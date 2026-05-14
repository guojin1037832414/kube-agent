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

    public KubeManagerHttpClient(UserPermissionContext userPermissionContext) {
        this.userPermissionContext = userPermissionContext;
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

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromPath(path);
        if (queryParams != null) {
            queryParams.forEach((k, v) -> {
                if (v != null) {
                    uriBuilder.queryParam(k, v);
                }
            });
        }

        log.debug("[HTTP GET] {} 参数={}, tokenSource={}",
            path, queryParams, token.equals(fallbackAuthToken) ? "fallback_sysadmin" : "user_threadlocal");

        String responseBody = restClient.get()
            .uri(uriBuilder.toUriString())
            .header("Authorization", "Bearer " + token)
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
            .header("Authorization", "Bearer " + token)
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
            .header("Authorization", "Bearer " + token)
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
     */
    private void doFallbackLogin() {
        if (loginPassword == null || loginPassword.isBlank()) {
            log.warn("[Token] 未配置后端密码，跳过降级登录");
            return;
        }

        Map<String, Object> loginBody = new LinkedHashMap<>();
        loginBody.put("username", loginUsername);
        loginBody.put("password", loginPassword);

        try {
            String response = restClient.post()
                .uri("/api/login")
                .body(loginBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (req, res) -> {
                    String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeException("降级登录失败: " + res.getStatusCode() + " body=" + raw);
                })
                .body(String.class);

            Map<String, Object> result = parseJson(response);
            Object token = result.get("token");
            if (token == null) {
                token = result.get("data");
            }
            if (token instanceof Map<?, ?> dataMap) {
                token = dataMap.get("token");
            }

            if (token != null) {
                this.fallbackAuthToken = token.toString();
                this.fallbackTokenExpiry = System.currentTimeMillis() + TOKEN_TTL_MS;
                log.info("[Token] 降级登录成功（sysadmin），Token 有效期约25分钟");
            } else {
                log.error("[Token] 降级登录响应中未找到 token 字段: {}", result.keySet());
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
     */
    @Recover
    public Map<String, Object> recover(ResourceAccessException e, String path, Object extra) {
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
