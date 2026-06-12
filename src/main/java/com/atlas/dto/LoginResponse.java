package com.atlas.dto;

/**
 * 登录成功响应。
 *
 * <p>扁平结构返回，前端直接读取根级字段即可识别 sessionId、username 等信息。
 * 兼容嵌套结构：前端代码 {@code res.data?.sessionId ?? res.sessionId} 同时处理两种格式。</p>
 *
 * <p>中文说明：这是登录完成后返回给前端的展示/会话契约。{@code sessionId} 是 kube-agent
 * 的会话句柄，用于后续请求的 {@code X-Session-Id}；它不是 kube-manager JWT，也不是权限本身。</p>
 *
 * <p>安全边界：响应里不返回 password 或 kube-manager token。{@code organizationId} 必须来自
 * 服务端可信解析后的 Session 创建上下文，不能简单回显 {@link LoginRequest#getOrganizationId()}。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
public class LoginResponse {

    /** 业务成功标志；只说明登录 API 成功，不代表后续 Tool/写操作授权。 */
    private boolean success = true;

    /** Session ID — 前端保存到 userStore，后续所有请求通过 X-Session-Id header 携带 */
    private String sessionId;

    /** 用户名；展示用，同时帮助前端渲染当前操作者，不替代服务端 Principal。 */
    private String username;

    /** 组织/租户 ID；必须是服务端可信解析结果，不是请求体原样回显。 */
    private String organizationId;

    /** 提示信息；人类可读，不作为机器授权证据。 */
    private String message;

    // ═══════════════════════════════════════════════════════════
    //  全参构造
    // ═══════════════════════════════════════════════════════════

    public LoginResponse(String sessionId, String username, String organizationId, String message) {
        this.sessionId = sessionId;
        this.username = username;
        this.organizationId = organizationId;
        this.message = message;
    }

    // ═══════════════════════════════════════════════════════════
    //  Getter / Setter
    // ═══════════════════════════════════════════════════════════

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
