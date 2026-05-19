package com.atlas.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求参数。
 *
 * <p>接收前端 POST /api/agent/login 的 JSON 请求体，包含用户名、密码和可选的组织 ID。</p>
 * <p><b>安全注意：</b>{@code toString()} 已脱敏处理，日志中不会打印明文密码。</p>
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

    /** 组织/租户 ID（可选，不传时后端自动解析） */
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
     */
    @Override
    public String toString() {
        return "LoginRequest{username='" + username + "', password=***, organizationId='" + organizationId + "'}";
    }
}
