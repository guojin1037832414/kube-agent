package com.atlas.controller;

import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.*;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.http.OrgIdResolutionException;
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
 * <p>中文说明：AuthController 是 kube-agent 与成熟 kube-manager 账号体系之间的桥。
 * 它不保存密码、不自建用户表、不绕过 kube-manager 登录结果；它只把前端登录请求代理出去，
 * 再把服务端确认过的 token、用户名、角色和组织上下文写入本地会话缓存。</p>
 *
 * <p>安全边界：登录成功但无法确认组织上下文时必须 fail-safe，不创建 Session。
 * 因为后续 Tool、Graph、kube-manager HTTP 出口都会把 SessionStore 里的 organizationId 当成租户边界。</p>
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
    // 用于登录后反查组织上下文；这个依赖只在登录边界使用，不允许变成通用 Tool 执行入口。
    private final KubeManagerHttpClient kubeManagerClient;

    public AuthController(
            @Value("${kube.manager.base-url:http://localhost:8100}") String baseUrl,
            RestClient.Builder restClientBuilder,
            UserPermissionContext userPermissionContext,
            SessionStore sessionStore,
            ObjectMapper objectMapper,
            KubeManagerHttpClient kubeManagerClient) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.userPermissionContext = userPermissionContext;
        this.sessionStore = sessionStore;
        this.objectMapper = objectMapper;
        this.kubeManagerClient = kubeManagerClient;
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
     *
     * <p>中文说明：这个接口是认证入口，不是 Agent 推理入口。
     * 它不会调用 LLM、不会执行 Tool、不会触发 HITL，也不会写业务资源；它只创建后续请求需要的服务端会话。</p>
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
            // kube-manager 登录接口使用 form-urlencoded；前端仍可向 kube-agent 发送 JSON，由这里做协议转换。
            String formBody = "username=" + URLEncoder.encode(request.getUsername(), StandardCharsets.UTF_8)
                    + "&password=" + URLEncoder.encode(request.getPassword(), StandardCharsets.UTF_8)
                    + "&organizationId=" + URLEncoder.encode(orgId, StandardCharsets.UTF_8)
                    + "&loginType=local_login";

            // ── 代理到 kube-manager ──
            // 密码只进入本次 HTTP 请求体，不写入 SessionStore，不写入 UserPermissionContext，也不进入返回体。
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
            // token 缺失时不能继续创建本地 Session，否则后续请求无法代表真实 kube-manager 登录态。
            String token = extractToken(root);
            if (token == null || token.isBlank()) {
                log.error("[Auth] kube-manager 响应中未找到 token: keys={}", root.fieldNames());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ApiResponse.fail("登录服务响应异常"));
            }

            // 尝试从响应中解析更详细的用户信息
            // kube-manager 返回格式可能为 {"result": {"orgId":"100002", "token":"jwt..."}, "success": true}
            // 或 {"user": {"organizationId":"100002"}, ...}
            String username = request.getUsername();
            // 请求体里的 organizationId 只作为 kube-manager 登录参数，不能作为本地 Session 的可信租户事实。
            // 只有 kube-manager 响应字段或使用本次 token 反查得到的结果，才允许写入 SessionStore。
            String resolvedOrgId = "";
            String role = "user";
            Set<String> permissions = Set.of();

            // 1. 尝试 result 对象（kube-manager /api/login 的实际格式）
            // 这里读取的是 kube-manager 返回的服务端事实，不读取前端请求体里的角色或权限声明。
            JsonNode resultNode = root.path("result");
            if (resultNode.isObject()) {
                resolvedOrgId = firstNonBlank(
                    resultNode.path("orgId").asText(""),
                    resultNode.path("organizationId").asText(""),
                    resolvedOrgId
                );
            }
            // 2. 尝试 user 节点
            JsonNode userNode = root.path("user");
            if (userNode.isObject()) {
                resolvedOrgId = firstNonBlank(userNode.path("organizationId").asText(""), resolvedOrgId);
                role = userNode.path("role").asText(role);
            }
            // 3. 根级别 fallback
            resolvedOrgId = firstNonBlank(root.path("organizationId").asText(""), resolvedOrgId);

            // M5.7: 如果响应未包含可信 orgId，用本次登录 token 反查；反查失败必须 fail-safe，不创建 Session。
            // 组织上下文是后续所有 kube-manager 调用的边界；不能仅凭登录请求里的 organizationId 当作可信结果。
            if (isUntrustedOrgId(resolvedOrgId)) {
                try {
                    String fetchedOrgId = kubeManagerClient.resolveOrgId(username, token);
                    if ("sysadmin".equals(fetchedOrgId)) {
                        resolvedOrgId = fetchedOrgId;
                        log.info("[Auth] 登录后确认超管身份: user={}", username);
                    } else if (!isUntrustedOrgId(fetchedOrgId)) {
                        resolvedOrgId = fetchedOrgId;
                        log.info("[Auth] 登录后反查可信 orgId: user={}, orgId={}", username, resolvedOrgId);
                    } else {
                        throw new OrgIdResolutionException(
                            OrgIdResolutionException.Reason.INVALID_RESOLVED_ORG_ID,
                            "反查结果不是可信组织 ID: " + fetchedOrgId
                        );
                    }
                } catch (Exception e) {
                    log.warn("[Auth] 登录成功但无法解析可信组织上下文，拒绝创建Session: user={}, error={}",
                        username, e.getMessage());
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body(ApiResponse.fail("登录成功但无法确认所属组织，请稍后重试或联系管理员"));
                }
            }

            if (!"sysadmin".equals(resolvedOrgId) && isUntrustedOrgId(resolvedOrgId)) {
                log.warn("[Auth] 组织上下文不可信，拒绝创建Session: user={}, orgId={}", username, resolvedOrgId);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ApiResponse.fail("登录成功但无法确认所属组织，请稍后重试或联系管理员"));
            }

            // ── 缓存到 UserPermissionContext ──
            // 只有 token 与组织上下文都通过服务端确认后，才把登录事实写入本地权限缓存。
            userPermissionContext.onLogin(token, username, role, permissions, resolvedOrgId);

            // ── 生成 Session ID 并缓存到 SessionStore ──
            // 前端后续使用 Session ID；真实 kube-manager token 保留在服务端，减少浏览器侧敏感信息暴露面。
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
     *
     * <p>中文说明：登出接口只做本地会话和权限缓存清理，不假装已经撤销 kube-manager 侧 token。
     * 后续如需远端 token revoke，应作为单独的受控集成切片实现。</p>
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
     *
     * <p>中文说明：这个接口只返回 SessionStore 中的非敏感用户摘要。
     * 不返回真实 token，不返回密码，也不把该响应当成 Tool 执行授权。</p>
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
     *
     * <p>中文说明：兼容多种响应形状是为了适配成熟基座项目的历史格式；
     * 兼容只限 token 所在位置，不代表接受任意登录成功语义。</p>
     */
    private String extractToken(JsonNode root) {
        // 1. result 可能是字符串 token，也可能是包含 token/orgId 的对象；对象型 result 不能直接 asText。
        if (root.hasNonNull("result")) {
            JsonNode result = root.path("result");
            if (result.isTextual()) {
                return result.asText();
            }
            if (result.isObject()) {
                if (result.hasNonNull("token")) return result.path("token").asText();
                if (result.hasNonNull("accessToken")) return result.path("accessToken").asText();
                if (result.hasNonNull("jwt")) return result.path("jwt").asText();
            }
        }
        if (root.hasNonNull("token")) return root.path("token").asText();

        // 2. 嵌套 data 对象
        JsonNode dataNode = root.path("data");
        if (dataNode.isObject()) {
            if (dataNode.hasNonNull("token")) return dataNode.path("token").asText();
            if (dataNode.hasNonNull("result")) return dataNode.path("result").asText();
        }

        return null;
    }

    /**
     * 判断组织 ID 是否不可信。
     *
     * <p>M5.7：普通用户的组织 ID 不允许为空或 kube-manager 登录占位值 {@code "1"}。
     * 真实组织即使等于某个历史默认配置值，也必须来自 kube-manager 响应或本次 token 反查成功，
     * 不能由本服务配置 fallback 生成。</p>
     *
     * <p>中文说明：返回 true 时上层必须继续反查或拒绝创建 Session，不能静默继续。</p>
     */
    private boolean isUntrustedOrgId(String orgId) {
        return orgId == null || orgId.isBlank() || "1".equals(orgId);
    }

    /**
     * 返回第一个非空白字符串。
     *
     * <p>中文说明：登录响应可能来自不同版本 kube-manager，字段位置不完全一致。
     * 这里做的是“服务端响应字段优先”的兼容解析，不会把请求体里的 organizationId 混进可信结果。</p>
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String mask(String sessionId) {
        if (sessionId == null || sessionId.length() < 8) return "***";
        return sessionId.substring(0, 8) + "...";
    }
}
