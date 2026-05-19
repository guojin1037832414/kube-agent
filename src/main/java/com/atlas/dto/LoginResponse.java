package com.atlas.dto;

/**
 * 登录成功响应。
 *
 * <p>扁平结构返回，前端直接读取根级字段即可识别 sessionId、username 等信息。
 * 兼容嵌套结构：前端代码 {@code res.data?.sessionId ?? res.sessionId} 同时处理两种格式。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
public class LoginResponse {

    /** 业务成功标志 */
    private boolean success = true;

    /** Session ID — 前端保存到 userStore，后续所有请求通过 X-Session-Id header 携带 */
    private String sessionId;

    /** 用户名 */
    private String username;

    /** 组织/租户 ID */
    private String organizationId;

    /** 提示信息 */
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
