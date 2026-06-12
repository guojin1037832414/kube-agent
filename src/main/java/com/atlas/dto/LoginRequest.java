package com.atlas.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求参数。
 *
 * <p>接收前端 POST /api/agent/login 的 JSON 请求体，包含用户名、密码和可选的组织 ID。</p>
 * <p><b>安全注意：</b>{@code toString()} 已脱敏处理，日志中不会打印明文密码。</p>
 *
 * <p>中文说明：这是前端提交给 kube-agent 的登录参数 DTO。{@code organizationId} 只是传给
 * kube-manager 登录接口的候选参数，不能被 kube-agent 本地 SessionStore 直接当作可信租户事实。
 * 可信 orgId 必须来自 kube-manager 响应或基于 token 的服务端反查。</p>
 *
 * <p>安全边界：password 只允许用于本次登录转发，不应进入日志、审计、Memory、RAG、eval、
 * prompt、SSE 或前端持久化。任何后续身份/租户/权限事实都不能从这个请求体直接推导。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
public class LoginRequest {

    /** 用户名（必填） */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 密码（必填） */
    @NotBlank(message = "密码不能为空")
    private String password;

    /** 组织/租户 ID（可选；仅是 kube-manager 登录参数，不是本地可信租户事实） */
    private String organizationId;

    // ═══════════════════════════════════════════════════════════
    //  Getter / Setter
    // ═══════════════════════════════════════════════════════════

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(String organizationId) {
        this.organizationId = organizationId;
    }

    /**
     * 脱敏 toString — 日志中打印此对象时不会泄露密码。
     *
     * <p>中文说明：这里保留 username/organizationId 便于排障，但密码永远只显示占位符。</p>
     */
    @Override
    public String toString() {
        return "LoginRequest{username='" + username + "', password=***, organizationId='" + organizationId + "'}";
    }
}
