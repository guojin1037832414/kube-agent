package com.atlas.http;

import com.atlas.auth.UserPermissionContext;
import com.atlas.observability.AgentTraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * kube-manager 后端 HTTP 客户端 — 所有 Tool 共用。
 *
 * <p><b>P1.4 重大变更：从 sysadmin 统一登录 → 每个用户使用自己的 Token。</b></p>
 * <ul>
 *   <li>旧方案：{@code ensureAuthenticated()} 使用配置的用户名密码调用 /api/login 获取统一 Token</li>
 *   <li>新方案：从当前请求 ThreadLocal 读取用户真实 Token，透传给 kube-manager</li>
 *   <li>好处：后端按用户身份鉴权，避免所有请求都用 sysadmin 权限</li>
 *   <li>回退机制：ThreadLocal 无 Token 时仍使用旧版 sysadmin 登录（兼容存量）</li>
 * </ul>
 *
 * <p><b>Token 透传机制：</b></p>
 * <ol>
 *   <li>{@link com.atlas.auth.AuthTokenFilter} 从 HTTP 请求头提取 Bearer Token</li>
 *   <li>写入 {@link UserPermissionContext} 的 ThreadLocal</li>
 *   <li>Tool 调用 → KubeManagerHttpClient 从 ThreadLocal 读取 → 写入 HTTP Authorization Header</li>
 *   <li>异步场景：使用 {@link com.atlas.auth.async.AsyncContextHolder} 保证 Token 不丢失</li>
 * </ol>
 *
 * <p><b>设计要点：</b></p>
 * <ul>
 *   <li><b>Spring Boot 3.2+ RestClient</b>： fluent API + 同步/异步统一，取代旧版 RestTemplate</li>
 *   <li><b>无状态 → 有状态（ThreadLocal）</b>：请求级别的 Token，不存储在实例变量</li>
 *   <li><b>超时/重试</b>：连接10s、读取30s，IO异常时最多重试2次</li>
 *   <li><b>所有请求/返回 Map&lt;String,Object&gt;</b>：给 LLM 的数据完整性优先，不定义强类型DTO</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-P1.4
 */
@Component
public class KubeManagerHttpClient {

    private static final Logger log = LoggerFactory.getLogger(KubeManagerHttpClient.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserPermissionContext userPermissionContext;
    private final KubeManagerHttpResiliencePolicy resiliencePolicy;
    private RestClient restClient;

    // ===== 配置项（从 application.yml 注入） =====
    @Value("${atlas.backend.base-url:http://localhost:8100}")
    private String backendBaseUrl;

    @Value("${atlas.backend.connect-timeout-seconds:10}")
    private int connectTimeoutSeconds;

    @Value("${atlas.backend.read-timeout-seconds:30}")
    private int readTimeoutSeconds;

    // ═══════ 旧版 sysadmin 登录配置（降级兼容用）════════
    @Value("${atlas.backend.login-username:sysadmin}")
    private String loginUsername;

    @Value("${atlas.backend.login-password:}")
    private String loginPassword;

    /** 旧版统一 Token 缓存（仅用于无用户上下文的兼容 HTTP 调用，不得用于 orgId 可信解析） */
    private volatile String fallbackAuthToken = null;
    private volatile long fallbackTokenExpiry = 0L;
    private static final long TOKEN_TTL_MS=25 * 60 * 1000; // 25分钟

    public KubeManagerHttpClient(UserPermissionContext userPermissionContext) {
        this(userPermissionContext, KubeManagerHttpResiliencePolicy.disabled());
    }

    @Autowired
    public KubeManagerHttpClient(UserPermissionContext userPermissionContext,
                                 KubeManagerHttpResiliencePolicy resiliencePolicy) {
        this.userPermissionContext = userPermissionContext;
        this.resiliencePolicy = resiliencePolicy != null
            ? resiliencePolicy
            : KubeManagerHttpResiliencePolicy.disabled();
    }

    /**
     * 初始化 RestClient（延迟到首次调用也行，但 PostConstruct 更可控）。
     */
    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        factory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));

        this.restClient = RestClient.builder()
            .requestFactory(factory)
            .baseUrl(backendBaseUrl)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .build();

        log.info("[KubeManagerHttpClient] 初始化完成, baseUrl={}, connect={}s, read={}s, mode=USER_TOKEN_PER_REQUEST",
            backendBaseUrl, connectTimeoutSeconds, readTimeoutSeconds);
    }

    // ═══════════════════════════════════════════════════════════════
    //  公共方法：GET / POST / PATCH / DELETE 统一封装
    // ═══════════════════════════════════════════════════════════════

    /**
     * 通用 GET 请求 — 带查询参数。
     *
     * <p>P1.4：每次请求自动从 ThreadLocal 读取用户 Token，无需手动传入。</p>
     * <p><b>M5.8 安全收口：</b>业务请求必须使用真实用户 Token，缺失用户上下文时
     * 直接 fail-closed，禁止透明降级到 sysadmin 统一 Token，避免后台兼容能力成为权限放大器。</p>
     *
     * @param path       接口路径（如 /api/instances）
     * @param queryParams 查询参数 Map
     * @return JSON 解析后的 Map
     */
    public Map<String, Object> get(String path, Map<String, Object> queryParams) {
        String token = resolveUserTokenRequired("GET", path);

        log.debug("[HTTP GET] {} 参数={}, tokenSource=user_threadlocal", path, queryParams);

        String responseBody = resiliencePolicy.executeRead(() -> restClient.get()
            .uri(builder -> {
                builder.path(path);
                if (queryParams != null) {
                    queryParams.forEach((k, v) -> {
                        if (v != null) {
                            builder.queryParam(k, v);
                        }
                    });
                }
                return builder.build();
            })
            .headers(headers -> applyUserAndTraceHeaders(headers, token))
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                log.error("[HTTP GET 错误] {} {}: {}", res.getStatusCode(), path, body);
                throw new RestClientResponseException(
                    "GET " + path + " 失败: " + res.getStatusCode(),
                    res.getStatusCode().value(), res.getStatusCode().toString(),
                    res.getHeaders(), body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            })
            .body(String.class)
        );

        return parseJson(responseBody);
    }

    /**
     * 无参 GET 便捷方法。
     */
    public Map<String, Object> get(String path) {
        return get(path, null);
    }

    /**
     * 通用 POST 请求 — 发送 JSON Body。
     *
     * <p>P1.4：每次请求自动从 ThreadLocal 读取用户 Token。</p>
     * <p><b>M5.8 安全收口：</b>POST 通常承载创建、提交、变更等业务动作，
     * 必须由当前用户 Token 发起；缺失用户 Token 时拒绝执行，不允许 sysadmin 降级代跑。</p>
     *
     * @param path 接口路径（如 /api/instance/create）
     * @param body 请求体 Map（会被序列化为 JSON）
     * @return JSON 解析后的 Map
     */
    public Map<String, Object> post(String path, Map<String, Object> body) {
        String token = resolveUserTokenRequired("POST", path);

        log.debug("[HTTP POST] {} body={}, tokenSource=user_threadlocal", path, body);

        String responseBody = resiliencePolicy.executeWrite(() -> restClient.post()
            .uri(path)
            .headers(headers -> applyUserAndTraceHeaders(headers, token))
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                log.error("[HTTP POST 错误] {} {}: {}", res.getStatusCode(), path, raw);
                throw new RestClientResponseException(
                    "POST " + path + " 失败: " + res.getStatusCode(),
                    res.getStatusCode().value(), res.getStatusCode().toString(),
                    res.getHeaders(), raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            })
            .body(String.class)
        );

        return parseJson(responseBody);
    }

    /**
     * 通用 PATCH 请求 — 发送 JSON Body。
     *
     * <p>kube-manager 的缩放等变更接口使用 PATCH。这里与 POST/DELETE 一样必须携带
     * 当前用户真实 Token，避免 Agent 在缺少用户上下文时替用户修改线上资源。</p>
     */
    public Map<String, Object> patch(String path, Map<String, Object> body) {
        String token = resolveUserTokenRequired("PATCH", path);

        log.debug("[HTTP PATCH] {} body={}, tokenSource=user_threadlocal", path, body);

        String responseBody = resiliencePolicy.executeWrite(() -> restClient.patch()
            .uri(path)
            .headers(headers -> applyUserAndTraceHeaders(headers, token))
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                log.error("[HTTP PATCH 错误] {} {}: {}", res.getStatusCode(), path, raw);
                throw new RestClientResponseException(
                    "PATCH " + path + " 失败: " + res.getStatusCode(),
                    res.getStatusCode().value(), res.getStatusCode().toString(),
                    res.getHeaders(), raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            })
            .body(String.class)
        );

        return parseJson(responseBody);
    }

    /**
     * 通用 POST 请求 - 同时发送 query 参数与 JSON Body。
     *
     * <p>Helm install 等成熟 kube-manager 接口使用 path variable + query + body 的组合。
     * 这里集中封装 URI 构造，避免 Tool 自己拼接查询串导致 chart 名称、版本号等参数转义不一致。</p>
     */
    public Map<String, Object> post(String path, Map<String, Object> queryParams, Map<String, Object> body) {
        String token = resolveUserTokenRequired("POST", path);

        log.debug("[HTTP POST] {} query={}, body={}, tokenSource=user_threadlocal", path, queryParams, body);

        String responseBody = resiliencePolicy.executeWrite(() -> restClient.post()
            .uri(builder -> {
                builder.path(path);
                if (queryParams != null) {
                    queryParams.forEach((k, v) -> {
                        if (v != null) {
                            builder.queryParam(k, v);
                        }
                    });
                }
                return builder.build();
            })
            .headers(headers -> applyUserAndTraceHeaders(headers, token))
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                log.error("[HTTP POST 错误] {} {}: {}", res.getStatusCode(), path, raw);
                throw new RestClientResponseException(
                    "POST " + path + " 失败: " + res.getStatusCode(),
                    res.getStatusCode().value(), res.getStatusCode().toString(),
                    res.getHeaders(), raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            })
            .body(String.class)
        );

        return parseJson(responseBody);
    }

    /**
     * 通用 PUT 请求 - 发送 JSON Body。
     *
     * <p>实验实例启动/关闭等动作在成熟前端中使用 PUT。这里与 POST/PATCH 一样强制使用
     * 当前登录用户 Token，避免高风险变更在缺少用户上下文时降级成系统账号代跑。</p>
     */
    /**
     * DELETE 请求（部分旧接口用 POST 模拟 DELETE，这里提供原生 DELETE）。
     *
     * <p><b>M5.8 安全收口：</b>删除类请求风险最高，必须绑定真实用户 Token；
     * 如果当前线程没有可信用户上下文，立即拒绝，禁止使用 sysadmin fallback token。</p>
     */
    public Map<String, Object> put(String path, Map<String, Object> body) {
        String token = resolveUserTokenRequired("PUT", path);

        log.debug("[HTTP PUT] {} body={}, tokenSource=user_threadlocal", path, body);

        String responseBody = resiliencePolicy.executeWrite(() -> restClient.put()
            .uri(path)
            .headers(headers -> applyUserAndTraceHeaders(headers, token))
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                log.error("[HTTP PUT 错误] {} {}: {}", res.getStatusCode(), path, raw);
                throw new RestClientResponseException(
                    "PUT " + path + " 失败: " + res.getStatusCode(),
                    res.getStatusCode().value(), res.getStatusCode().toString(),
                    res.getHeaders(), raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            })
            .body(String.class)
        );

        return parseJson(responseBody);
    }

    /**
     * 通用 PUT 请求 - 同时发送 query 参数与 JSON Body。
     *
     * <p>Helm upgrade 的成熟接口把 chart 放在 query 中，把升级参数放在 body 中。
     * 该重载让 Tool 只表达业务字段，不直接拼接 URL 查询串。</p>
     */
    public Map<String, Object> put(String path, Map<String, Object> queryParams, Map<String, Object> body) {
        String token = resolveUserTokenRequired("PUT", path);

        log.debug("[HTTP PUT] {} query={}, body={}, tokenSource=user_threadlocal", path, queryParams, body);

        String responseBody = resiliencePolicy.executeWrite(() -> restClient.put()
            .uri(builder -> {
                builder.path(path);
                if (queryParams != null) {
                    queryParams.forEach((k, v) -> {
                        if (v != null) {
                            builder.queryParam(k, v);
                        }
                    });
                }
                return builder.build();
            })
            .headers(headers -> applyUserAndTraceHeaders(headers, token))
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                log.error("[HTTP PUT 错误] {} {}: {}", res.getStatusCode(), path, raw);
                throw new RestClientResponseException(
                    "PUT " + path + " 失败: " + res.getStatusCode(),
                    res.getStatusCode().value(), res.getStatusCode().toString(),
                    res.getHeaders(), raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            })
            .body(String.class)
        );

        return parseJson(responseBody);
    }

    public Map<String, Object> delete(String path, Map<String, Object> queryParams) {
        String token = resolveUserTokenRequired("DELETE", path);
        log.debug("[HTTP DELETE] {} 参数={}, tokenSource=user_threadlocal", path, queryParams);

        String responseBody = resiliencePolicy.executeWrite(() -> restClient.delete()
            .uri(builder -> {
                builder.path(path);
                if (queryParams != null) {
                    queryParams.forEach((k, v) -> {
                        if (v != null) {
                            builder.queryParam(k, v);
                        }
                    });
                }
                return builder.build();
            })
            .headers(headers -> applyUserAndTraceHeaders(headers, token))
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                throw new RestClientResponseException(
                    "DELETE " + path + " 失败", res.getStatusCode().value(),
                    res.getStatusCode().toString(), res.getHeaders(),
                    raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            })
            .body(String.class)
        );

        return parseJson(responseBody);
    }

    /**
     * 统一写入 kube-manager 出口请求头。
     *
     * <p>M5.24 将 M5.23 的 Agent traceId 接到 HTTP outlet。这里刻意把 token、traceId 和
     * W3C traceparent 放在一个 helper 中，后续接入 auditId、idempotency key、tenant evidence、
     * OpenTelemetry baggage 时不需要在每个 GET/POST/PUT/DELETE 分支复制逻辑。</p>
     */
    private void applyUserAndTraceHeaders(HttpHeaders headers, String token) {
        headers.set("X-Token", token);
        String traceId = AgentTraceContext.currentOrNew("");
        headers.set("X-Trace-Id", traceId);
        String traceparent = AgentTraceContext.traceparentOrBlank(traceId);
        if (!traceparent.isBlank()) {
            headers.set("traceparent", traceparent);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  Token 解析 — P1.4 核心变更 / M5.8 安全收口
    // ═══════════════════════════════════════════════════════════════

    /**
     * 解析业务请求必须使用的真实用户 Token。
     *
     * <p><b>M5.8 安全边界：</b>{@link #get(String, Map)}、{@link #post(String, Map)}、
     * {@link #delete(String, Map)} 都是由 Agent Tool 发起的业务请求，必须继承当前登录用户的
     * ThreadLocal Token。若缺失用户 Token，说明请求链路没有可信用户上下文，此时必须 fail-closed，
     * 不能透明降级为 sysadmin fallback token。</p>
     *
     * <p>fallback sysadmin 登录能力仅保留给未来显式系统任务，例如健康探测、后台同步等，
     * 这些系统任务必须使用独立入口并经过审计，不能复用业务 Tool 默认路径。</p>
     *
     * @param operation HTTP 操作名，仅用于安全日志与异常排查
     * @param path      后端 API 路径，仅用于安全日志与异常排查
     * @return 当前用户真实 Token
     */
    private String resolveUserTokenRequired(String operation, String path) {
        String userToken = userPermissionContext.getCurrentToken();
        if (userToken != null && !userToken.isBlank()) {
            log.debug("[Token] {} {} 使用用户真实 Token（ThreadLocal）", operation, path);
            return userToken;
        }

        log.warn("[Token] 安全拒绝: {} {} 缺少用户 ThreadLocal Token，拒绝使用 sysadmin 降级 Token", operation, path);
        throw new IllegalStateException(
            "业务 kube-manager 请求缺少用户 Token，已拒绝使用 sysadmin 降级 Token。" +
            "请确认请求携带有效 X-Session-Id，且异步/Graph/ReAct 链路正确透传安全上下文。"
        );
    }

    /**
     * 解析系统任务可用的 Token。
     *
     * <p><b>重要：</b>该方法允许在没有用户 ThreadLocal Token 时执行 sysadmin 降级登录，
     * 因此禁止被业务 Tool 的 get/post/delete 默认路径调用。业务请求必须使用
     * {@link #resolveUserTokenRequired(String, String)}。</p>
     *
     * <p>当前保留该方法是为了兼容未来显式 SYSTEM_CONTEXT_ALLOWED 场景；新增调用方必须先完成
     * 风险审计、调用场景白名单和日志记录，避免 fallback token 成为权限放大器。</p>
     *
     * @return 可用的 Bearer Token 字符串
     */
    private String resolveToken() {
        // ① 优先从 ThreadLocal 读取用户真实 Token
        String userToken = userPermissionContext.getCurrentToken();
        if (userToken != null && !userToken.isBlank()) {
            log.debug("[Token] 使用用户真实 Token（ThreadLocal）");
            return userToken;
        }

        // ② 降级：使用 sysadmin 统一 Token（后台任务 / 未经过 HTTP 过滤器的场景）
        log.warn("[Token] ThreadLocal 无用户 Token，降级使用 sysadmin 统一登录");
        ensureFallbackAuthenticated();
        if (fallbackAuthToken != null && !fallbackAuthToken.isBlank()) {
            return fallbackAuthToken;
        }

        // ③ 无任何 Token 时抛出异常（避免无鉴权请求到达后端）
        throw new IllegalStateException(
            "无法获取有效 Token：ThreadLocal 无用户 Token，且 sysadmin 降级登录也失败。" +
            "请确保请求携带 Authorization: Bearer <token> 头，或配置有效的 atlas.backend.login-username/password"
        );
    }

    /**
     * 检查并刷新降级 Token（sysadmin 统一账号）。
     */
    private synchronized void ensureFallbackAuthenticated() {
        long now = System.currentTimeMillis();
        if (fallbackAuthToken != null && now < fallbackTokenExpiry) {
            return;
        }
        doFallbackLogin();
    }

    /**
     * 使用 sysadmin 账号执行降级登录。
     * <p>注意：必须传 organizationId + loginType，否则后端返回 TooManyResultsException。</p>
     */
    private void doFallbackLogin() {
        if (loginPassword == null || loginPassword.isBlank()) {
            log.warn("[Token] 未配置后端密码，跳过降级登录");
            return;
        }

        try {
            // kube-manager /api/login 要求 application/x-www-form-urlencoded，不能传 JSON
            String formBody = "username=" + java.net.URLEncoder.encode(loginUsername, StandardCharsets.UTF_8)
                + "&password=" + java.net.URLEncoder.encode(loginPassword, StandardCharsets.UTF_8)
                + "&organizationId=1"
                + "&loginType=local_login";

            String response = restClient.post()
                .uri("/api/login")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body(formBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeException("降级登录失败: " + res.getStatusCode() + " body=" + raw);
                })
                .body(String.class);

            Map<String, Object> result = parseJson(response);
            // kube-manager 返回: {"result":"jwt...","success":true} 或 {"code":..., "message":"..."}
            if (!Boolean.TRUE.equals(result.get("success"))) {
                log.error("[Token] 降级登录失败: {}", result);
                return;
            }

            Object token = result.get("result");  // kube-manager 用 'result' 承载 token
            if (token == null) token = result.get("token");  // 兼容其他字段
            if (token == null) token = result.get("data");
            if (token instanceof Map<?, ?> dataMap) {
                token = dataMap.get("token");
            }

            if (token != null) {
                this.fallbackAuthToken = token.toString();
                this.fallbackTokenExpiry = System.currentTimeMillis() + TOKEN_TTL_MS;
                log.info("[Token] 降级登录成功（sysadmin），Token 有效期约25分钟");
            } else {
                log.error("[Token] 降级登录响应中未找到 token 字段: keys={}", result.keySet());
            }
        } catch (Exception e) {
            log.error("[Token] 降级登录请求失败: {}", e.getMessage());
            throw new RuntimeException("无法获取 kube-manager 降级认证Token", e);
        }
    }

    /**
     * 手动刷新降级 Token（外部调用，如 Token 被踢出时）。
     */
    public synchronized void refreshFallbackToken() {
        this.fallbackAuthToken = null;
        this.fallbackTokenExpiry = 0;
        doFallbackLogin();
    }

    // ═══════════════════════════════════════════════════════════════
    //  P1.4-orgId 修复：按用户名解析组织ID
    // ═══════════════════════════════════════════════════════════════

    /**
     * 后端已知的可能组织ID列表（按频率排序）。
     * 用于 resolveOrgId 在首次查找用户时的桶式搜索。
     * 实际项目可通过配置动态注入更多 orgIds。
     */
    private static final List<String> KNOWN_ORG_IDS = List.of(
        "100001", "100002", "100003", "100050", "100051", "100057", "100061", "100062"
    );

    /**
     * 解析用户名对应的组织ID（P1.4 修复）。
     *
     * <p><b>核心挑战：</b></p>
     * <ul>
     *   <li>kube-manager JWT payload 不含 organizationId</li>
     *   <li>所有 /api/{orgId}/xxx API 需要已知 orgId 才能调用</li>
     *   <li>这是一个经典循环依赖：用 org-specific API 查 orgId</li>
     * </ul>
     *
     * <p><b>解决方案（桶式搜索）：</b></p>
     * <ol>
     *   <li>先用用户 Token 尝试查询各组织用户列表</li>
     *   <li>找到匹配用户后直接返回 kube-manager 响应中的可信 orgId</li>
     *   <li>超管直接返回 sysadmin 标记（绕过搜索）</li>
     * </ol>
     *
     * <p><b>未来替代方案：</b>kube-agent 自建 /api/v1/login，代理 kube-manager
     * 登录并显式返回 organizationId，前端传入 ChatRequest 中。</p>
     *
     * @param username  用户名（不可为空）
     * @param authToken 当前用户 Token（必须来自本次登录响应）
     * @throws OrgIdResolutionException 无法解析可信组织 ID 时抛出，调用方必须 fail-safe
     */
    public String resolveOrgId(String username, String authToken) {
        if (username == null || username.isBlank()) {
            throw new OrgIdResolutionException(
                OrgIdResolutionException.Reason.USERNAME_EMPTY,
                "username 为空，无法解析可信组织 ID"
            );
        }

        if (authToken == null || authToken.isBlank()) {
            throw new OrgIdResolutionException(
                OrgIdResolutionException.Reason.TOKEN_UNAVAILABLE,
                "缺少当前用户 Token，无法解析可信组织 ID"
            );
        }

        // 超管标记穿透（sysadmin 用特殊标记，让上层按全局身份单独授权，不能降级成普通租户 ID）
        if ("sysadmin".equals(username) || "sysadmin02".equals(username)) {
            return "sysadmin";
        }

        // M5.7：不再使用 username-only orgId 缓存，避免跨 session / 跨租户复用旧组织上下文。

        // 桶式搜索：仅使用当前用户 Token 查询，找到目标用户即停；禁止使用 sysadmin/降级 Token 洗白租户归属。
        String found = null;
        for (String orgId : KNOWN_ORG_IDS) {
            try {
                String url = UriComponentsBuilder.fromUriString("/api/" + orgId + "/user")
                    .queryParam("pageNum", 1)
                    .queryParam("pageSize", 1000)
                    .toUriString();

                String response = restClient.get()
                    .uri(url)
                    .headers(headers -> applyUserAndTraceHeaders(headers, authToken))
                    .retrieve()
                    .body(String.class);

                Map<String, Object> result = parseJson(response);
                List<?> userList = extractUserList(result);

                for (Object u : userList) {
                    if (u instanceof Map<?, ?> um) {
                        String uname = String.valueOf(um.get("username"));
                        if (username.equals(uname)) {
                            found = String.valueOf(um.get("organizationId"));
                            if (found == null || found.isBlank() || "null".equalsIgnoreCase(found) || "1".equals(found)) {
                                throw new OrgIdResolutionException(
                                    OrgIdResolutionException.Reason.INVALID_RESOLVED_ORG_ID,
                                    "kube-manager 返回的组织 ID 不可信: " + found
                                );
                            }
                            break;
                        }
                    }
                }
                if (found != null) break; // 找到了，跳出外层循环

            } catch (OrgIdResolutionException e) {
                // 已经找到目标用户但组织 ID 不可信，这是安全边界异常，必须立即终止，不能继续扫其他桶洗白。
                throw e;
            } catch (Exception e) {
                // 该组织可能不存在或查询失败，继续下一个桶
                log.debug("[resolveOrgId] 组织 {} 查询失败，继续搜索: {}", orgId, e.getMessage());
            }
        }

        if (found != null && !found.isBlank()) {
            log.info("[resolveOrgId] username={} → orgId={}", username, found);
            return found;
        }

        log.warn("[resolveOrgId] username={} 在所有已知组织中未找到，拒绝创建可信组织上下文", username);
        throw new OrgIdResolutionException(
            OrgIdResolutionException.Reason.USER_NOT_FOUND,
            "无法确认用户所属组织: " + username
        );
    }

    /**
     * 从 kube-manager /api/user 响应中提取用户列表。
     *
     * <p>兼容多种响应结构：</p>
     * <ul>
     *   <li>{"result":[...]} — 直接数组</li>
     *   <li>{"data":{"list":[...]}} — Spring Boot 分页包装</li>
     *   <li>{"data":[...]} — 无分页包装</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private List<?> extractUserList(Map<String, Object> result) {
        if (result == null) return List.of();
        // 格式A：{"result":[...]}
        Object raw = result.get("result");
        if (raw instanceof List<?> list) return list;
        // 格式B：{"data":{"list":[...]}}
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        if (data != null) {
            Object list = data.get("list");
            if (list instanceof List<?> l) return l;
            if (list == null && data.get("records") instanceof List<?> r) return r;
        }
        // 格式C：{"data":[...]}
        if (result.get("data") instanceof List<?> list) return list;
        return List.of();
    }

    // ═══════════════════════════════════════════════════════════════
    //  工具方法
    // ═══════════════════════════════════════════════════════════════

    /**
     * 解析 JSON 响应为 Map。空响应返回空 Map。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String body) {
        if (body == null || body.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Object parsed = objectMapper.readValue(body, Object.class);
            if (parsed instanceof Map) {
                return (Map<String, Object>) parsed;
            }
            // 非对象JSON（如数组），包装到 "data" 字段
            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("data", parsed);
            return wrapper;
        } catch (JsonProcessingException e) {
            log.error("[JSON解析失败] body={}", body.substring(0, Math.min(200, body.length())));
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", body);
            fallback.put("parseError", e.getMessage());
            return fallback;
        }
    }

    /**
     * 获取当前 baseUrl（调试用）。
     */
    public String getBaseUrl() {
        return backendBaseUrl;
    }

    /**
     * 获取当前 Token 来源信息（调试用，不暴露完整 Token）。
     */
    public String getTokenSourceDebug() {
        String userToken = userPermissionContext.getCurrentToken();
        if (userToken != null && !userToken.isBlank()) {
            return "user:" + userToken.substring(0, Math.min(8, userToken.length())) + "...";
        }
        if (fallbackAuthToken != null) {
            return "fallback_sysadmin:" + fallbackAuthToken.substring(0, Math.min(8, fallbackAuthToken.length())) + "...";
        }
        return "none";
    }
}
