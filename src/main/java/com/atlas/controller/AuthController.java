package com.atlas.controller;

import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.*;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.store.SessionStore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 认证控制器 — 登录代理、登出、当前用户信息查询。
 *
 * <p>核心职责：</p>
 * <ol>
 *   <li><b>登录代理</b>：接收前端 JSON → 转 form-urlencoded → 代理到 kube-manager → 返回 Session ID</li>
 *   <li><b>登出</b>：从 X-Session-Id 读取会话 → 清理缓存</li>
 *   <li><b>当前用户</b>：从 X-Session-Id 反查会话信息</li>
 * </ol>
 *
 * <p><b>安全设计：</b></p>
 * <ul>
 *   <li>密码不落地日志（{@link LoginRequest#toString()} 已脱敏）</li>
 *   <li>Session ID 用 SecureRandom 生成，不可预测</li>
 *   <li>JWT Token 仅存于 Caffeine 内存缓存，TTL=30min</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
@RestController
@RequestMapping("/api/agent")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final RestClient restClient;
    private final UserPermissionContext userPermissionContext;
    private final SessionStore sessionStore;
    private final ObjectMapper objectMapper;

    public AuthController(
            @Value("${kube.manager.base-url:http://localhost:8100}") String baseUrl,
            RestClient.Builder restClientBuilder,
            UserPermissionContext userPermissionContext,
            SessionStore sessionStore,
            ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.userPermissionContext = userPermissionContext;
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════════════
    //  ① 登录代理
    // ═══════════════════════════════════════════════════════════

    /**
     * 用户登录 — 代理到 kube-manager /api/login。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>参数校验</li>
     *   <li>JSON → form-urlencoded 转换</li>
     *   <li>POST 代理到 kube-manager</li>
     *   <li>解析 JWT token + 用户信息</li>
     *   <li>生成 Session ID 并缓存</li>
     *   <li>扁平结构返回</li>
     * </ol>
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // ── 参数校验 ──
        if (request.getUsername() == null || request.getUsername().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()) {
            log.warn("[Auth] 登录请求参数缺失: username={}, password=***",
                    request.getUsername());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.fail("用户名和密码不能为空"));
        }

        String orgId = (request.getOrganizationId() != null && !request.getOrganizationId().isBlank())
                ? request.getOrganizationId() : "1";

        try {
            // ── 转 form-urlencoded ──
            String formBody = "username=" + URLEncoder.encode(request.getUsername(), StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(request.getPassword(), StandardCharsets.UTF_8)
                    + "&organizationId=" + URLEncoder.encode(orgId, StandardCharsets.UTF_8)
                    + "&loginType=local_login";

            // ── 代理到 kube-manager ──
            String responseBody = restClient.post()
                    .uri("/api/login")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .body(formBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String raw = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                        log.warn("[Auth] kube-manager 登录失败: status={}, body={}", res.getStatusCode(), raw);
                        throw new RuntimeException("登录服务暂时不可用");
                    })
                    .body(String.class);

            // ── 解析响应 ──
            JsonNode root = objectMapper.readTree(responseBody);
            boolean success = root.path("success").asBoolean(false);
            if (!success) {
                String msg = root.path("message").asText("用户名或密码错误");
                log.warn("[Auth] kube-manager 返回登录失败: user={}, msg={}", request.getUsername(), msg);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.fail(msg));
            }

            // 提取 token — 兼容多种字段名（result/token/data）
            String token = extractToken(root);
            if (token == null || token.isBlank()) {
                log.error("[Auth] kube-manager 响应中未找到 token: keys={}", root.fieldNames());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.fail("登录服务响应异常"));
            }

            // 提取用户信息
            String username = request.getUsername();
            String resolvedOrgId = orgId;
            String role = "user";
            Set<String> permissions = Set.of();

            // 尝试从响应中解析更详细的用户信息
            JsonNode userNode = root.path("user");
            if (userNode.isObject()) {
                resolvedOrgId = userNode.path("organizationId").asText(resolvedOrgId);
                role = userNode.path("role").asText(role);
            }

            // ── 缓存到 UserPermissionContext ──
            userPermissionContext.onLogin(token, username, role, permissions);

            // ── 生成 Session ID 并缓存到 SessionStore ──
            String sessionId = sessionStore.createSession(token, username, resolvedOrgId, role, permissions);

            // ── 返回扁平结构 ──
            LoginResponse loginResp = new LoginResponse(sessionId, username, resolvedOrgId, "登录成功");
            return ResponseEntity.ok(loginResp);

        } catch (Exception e) {
            log.error("[Auth] 登录处理异常: user={}, error={}", request.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.fail("登录服务暂时不可用：" + e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ② 登出
    // ═══════════════════════════════════════════════════════════

    /**
     * 用户登出 — 幂等设计，即使 sessionId 已失效也返回成功。
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            // 查询 Session 中的 JWT token，一并清理 UserPermissionContext
            sessionStore.findById(sessionId).ifPresent(data -> {
                userPermissionContext.onLogout(data.token());
            });
            sessionStore.remove(sessionId);
            log.info("[Auth] 用户登出: sessionId={}", mask(sessionId));
        }
        return ResponseEntity.ok(ApiResponse.ok("登出成功"));
    }

    // ═══════════════════════════════════════════════════════════
    //  ③ 当前用户信息
    // ═══════════════════════════════════════════════════════════

    /**
     * 获取当前登录用户信息。
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail("未登录或会话已过期"));
        }

        Optional<SessionData> opt = sessionStore.findById(sessionId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.fail("会话已过期，请重新登录"));
        }

        SessionData data = opt.get();
        Map<String, Object> userInfo = new LinkedHashMap<>();
        userInfo.put("username", data.username());
        userInfo.put("organizationId", data.organizationId());
        userInfo.put("role", data.role());

        return ResponseEntity.ok(ApiResponse.ok(userInfo));
    }

    // ═══════════════════════════════════════════════════════════
    //  内部辅助
    // ═══════════════════════════════════════════════════════════

    /**
     * 从 kube-manager 响应中递归提取 token。
     * <p>兼容字段名：result → token → data.token</p>
     */
    private String extractToken(JsonNode root) {
        // 1. 直接字段
        if (root.hasNonNull("result")) return root.path("result").asText();
        if (root.hasNonNull("token")) return root.path("token").asText();

        // 2. 嵌套 data 对象
        JsonNode dataNode = root.path("data");
        if (dataNode.isObject()) {
            if (dataNode.hasNonNull("token")) return dataNode.path("token").asText();
            if (dataNode.hasNonNull("result")) return dataNode.path("result").asText();
        }

        return null;
    }

    private String mask(String sessionId) {
        if (sessionId == null || sessionId.length() < 8) return "***";
        return sessionId.substring(0, 8) + "...";
    }
}
