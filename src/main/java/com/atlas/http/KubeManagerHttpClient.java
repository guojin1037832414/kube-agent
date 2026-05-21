package com.atlas.http;

import com.atlas.auth.UserPermissionContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 旧版统一 Token 缓存（降级时使用） */
    private volatile String fallbackAuthToken = null;
    private volatile long fallbackTokenExpiry = 0L;
    private static final long TOKEN_TTL_MS = 25 * 60 * 1000; // 25分钟

    @Value("${atlas.backend.fallback-org-id:100001}")
    private String fallbackOrgId;

    public KubeManagerHttpClient(UserPermissionContext userPermissionContext) {
        this.userPermissionContext = userPermissionContext;
    }

    /**
     * 获取配置的 fallback orgId（P3.1 配置化修复）。
     * <p>当 ThreadLocal 无 orgId 且 Tool 参数未提供时，使用此默认值。</p>
     */
    public String getFallbackOrgId() {
        return fallbackOrgId;
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
    //  公共方法：GET / POST / DELETE 统一封装
    // ═══════════════════════════════════════════════════════════════

    /**
     * 通用 GET 请求 — 带查询参数。
     *
     * <p>P1.4：每次请求自动从 ThreadLocal 读取用户 Token，无需手动传入。</p>
     *
     * @param path       接口路径（如 /api/instances）
     * @param queryParams 查询参数 Map
     * @return JSON 解析后的 Map
     */
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public Map<String, Object> get(String path, Map<String, Object> queryParams) {
        String token = resolveToken();

        log.debug("[HTTP GET] {} 参数={}, tokenSource={}",
            path, queryParams, token.equals(fallbackAuthToken) ? "fallback_sysadmin" : "user_threadlocal");

        String responseBody = restClient.get()
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
            .header("X-Token", token)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                log.error("[HTTP GET 错误] {} {}: {}", res.getStatusCode(), path, body);
                throw new RestClientResponseException(
                    "GET " + path + " 失败: " + res.getStatusCode(),
                    res.getStatusCode().value(), res.getStatusCode().toString(),
                    res.getHeaders(), body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            })
            .body(String.class);

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
     *
     * @param path 接口路径（如 /api/instance/create）
     * @param body 请求体 Map（会被序列化为 JSON）
     * @return JSON 解析后的 Map
     */
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public Map<String, Object> post(String path, Map<String, Object> body) {
        String token = resolveToken();

        log.debug("[HTTP POST] {} body={}, tokenSource={}",
            path, body, token.equals(fallbackAuthToken) ? "fallback_sysadmin" : "user_threadlocal");

        String responseBody = restClient.post()
            .uri(path)
            .header("X-Token", token)
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
            .body(String.class);

        return parseJson(responseBody);
    }

    /**
     * DELETE 请求（部分旧接口用 POST 模拟 DELETE，这里提供原生 DELETE）。
     */
    @Retryable(
        retryFor = {ResourceAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 500, multiplier = 2)
    )
    public Map<String, Object> delete(String path, Map<String, Object> body) {
        String token = resolveToken();
        log.debug("[HTTP DELETE] {} body={}, tokenSource={}",
            path, body, token.equals(fallbackAuthToken) ? "fallback_sysadmin" : "user_threadlocal");

        String responseBody = restClient.delete()
            .uri(path)
            .header("X-Token", token)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (req, res) -> {
                String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                throw new RestClientResponseException(
                    "DELETE " + path + " 失败", res.getStatusCode().value(),
                    res.getStatusCode().toString(), res.getHeaders(),
                    raw.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
            })
            .body(String.class);

        return parseJson(responseBody);
    }

    // ═══════════════════════════════════════════════════════════════
    //  Token 解析 — P1.4 核心变更
    // ═══════════════════════════════════════════════════════════════

    /**
     * 解析当前请求应使用的 Token。
     *
     * <p><b>优先级：</b></p>
     * <ol>
     *   <li>ThreadLocal 中的用户 Token（从当前 HTTP 请求头提取）</li>
   *   <li>降级：使用 sysadmin 统一 Token（兼容存量 / 后台任务场景）</li>
     * </ol>
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
     * 用户名 → 组织ID 内存缓存（P1.4修复）。
     *
     * <p><b>为什么需要这个机制？</b></p>
     * <ul>
     *   <li>kube-manager 的 JWT Token payload 不包含 organizationId</li>
     *   <li>前端登录只传用户名密码，用户不知道自己的 orgId</li>
     *   <li>所有查询 API 路径都需要 orgId（如 /api/{orgId}/deployment）</li>
     * </ul>
     *
     * <p><b>实现方式：</b></p>
     * <ol>
     *   <li>懒加载：首次遇到该用户名时，调 kube-manager 查询</li>
     *   <li>缓存：查过一次后 5分钟内复用，避免重复请求</li>
     *   <li>回退：查不到时统一返回 100001（系统专用组织），确保功能不中断</li>
     *   <li>超管穿透：当 username=sysadmin 时，返回 "sysadmin" 标记，Tool 可据此选择无 orgId 过滤的全局模式</li>
     * </ol>
     *
     * <p><b>容器外调用：</b>仅 AtlasOrchestrator 在 SSE 流处理前调用一次。</p>
     *
     * @param username 前端传入的用户名（如 sysadmin、zhaotiandi）
     * @return 组织ID字符串（如 "100001"），超管返回 "sysadmin" 标记
     */
    static final class OrgIdEntry {
        final String orgId;
        final long expiry;
        OrgIdEntry(String orgId, long ttlMillis) {
            this.orgId = orgId;
            this.expiry = System.currentTimeMillis() + ttlMillis;
        }
        boolean isExpired() { return System.currentTimeMillis() > expiry; }
    }

    /** 用户名 → 组织ID 缓存，TTL=5分钟 */
    private final Map<String, OrgIdEntry> orgIdCache = new ConcurrentHashMap<>();

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
     *   <li>找到匹配用户后缓存 orgId</li>
     *   <li>超管直接返回 sysadmin 标记（绕过搜索）</li>
     * </ol>
     *
     * <p><b>未来替代方案：</b>kube-agent 自建 /api/v1/login，代理 kube-manager
     * 登录并显式返回 organizationId，前端传入 ChatRequest 中。</p>
     *
     * @param username  用户名（不可为空，空值回退 "100001"）
     * @param authToken 当前用户 Token（优先使用）
     */
    public String resolveOrgId(String username, String authToken) {
        if (username == null || username.isBlank()) {
            log.warn("[resolveOrgId] username为空，回退到默认组织 {}", fallbackOrgId);
            return fallbackOrgId;
        }

        // 超管标记穿透（sysadmin 用特殊标记，让 Tool 决定是否全局模式）
        if ("sysadmin".equals(username) || "sysadmin02".equals(username)) {
            return "sysadmin";
        }

        // 命中缓存？
        OrgIdEntry entry = orgIdCache.get(username);
        if (entry != null && !entry.isExpired()) {
            return entry.orgId;
        }

        // 懒加载：调 kube-manager 查询用户列表
        String effectiveToken = authToken;
        if (effectiveToken == null || effectiveToken.isBlank()) {
            // 使用降级 Token（sysadmin 预登录）代为查询用户-组织映射
            ensureFallbackAuthenticated();
            if (fallbackAuthToken != null && !fallbackAuthToken.isBlank()) {
                effectiveToken = fallbackAuthToken;
                log.debug("[resolveOrgId] 使用降级 Token 查询 username={}", username);
            } else {
                log.warn("[resolveOrgId] username={} 但无有效Token，无法查组织ID，回退{}", username, fallbackOrgId);
                return fallbackOrgId;
            }
        }

        // 桶式搜索：在已知组织列表中逐个查询，找到目标用户即停
        String found = null;
        for (String orgId : KNOWN_ORG_IDS) {
            try {
                String url = UriComponentsBuilder.fromUriString("/api/" + orgId + "/user")
                    .queryParam("pageNum", 1)
                    .queryParam("pageSize", 1000)
                    .toUriString();

                String response = restClient.get()
                    .uri(url)
                    .header("X-Token", effectiveToken)
                    .retrieve()
                    .body(String.class);

                Map<String, Object> result = parseJson(response);
                List<?> userList = extractUserList(result);

                for (Object u : userList) {
                    if (u instanceof Map<?, ?> um) {
                        String uname = String.valueOf(um.get("username"));
                        if (username.equals(uname)) {
                            found = String.valueOf(um.get("organizationId"));
                            break;
                        }
                    }
                }
                if (found != null) break; // 找到了，跳出外层循环

            } catch (Exception e) {
                // 该组织可能不存在或查询失败，继续下一个桶
                log.debug("[resolveOrgId] 组织 {} 查询失败，继续搜索: {}", orgId, e.getMessage());
            }
        }

        if (found != null && !found.isBlank()) {
            orgIdCache.put(username, new OrgIdEntry(found, 5L * 60 * 1000));
            log.info("[resolveOrgId] username={} → orgId={}", username, found);
            return found;
        }

        log.warn("[resolveOrgId] username={} 在所有已知组织中未找到，回退{}", username, fallbackOrgId);
        orgIdCache.put(username, new OrgIdEntry(fallbackOrgId, 60L * 1000)); // 短缓存避免反复查
        return fallbackOrgId;
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
     * 重试耗尽后的恢复方法 — 返回结构化错误给 LLM。
     * <p>签名使用 Exception（而非 ResourceAccessException）以兼容降级登录失败等更广泛的异常。</p>
     */
    @Recover
    public Map<String, Object> recover(Exception e, String path, Map<String, Object> params) {
        log.error("[HTTP] 重试耗尽，无法访问 {}: {}", path, e.getMessage());
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("success", false);
        error.put("errorType", "NETWORK_ERROR");
        error.put("message", "后端服务暂时不可用，请稍后重试");
        error.put("path", path);
        error.put("detail", e.getMessage());
        return error;
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
