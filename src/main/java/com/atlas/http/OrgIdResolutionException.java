package com.atlas.http;

/**
 * 组织 ID 可信解析异常。
 *
 * <p>M5.7 安全治理后，orgId 是认证/授权边界，不能在解析失败时回退到任何默认组织。
 * 登录链路、Session 创建链路必须把该异常视为 fail-safe 信号：拒绝创建会话，要求用户重新登录或联系管理员。</p>
 */
public class OrgIdResolutionException extends RuntimeException {

    /** 解析失败原因，便于日志、测试和后续监控聚合。 */
    private final Reason reason;

    public OrgIdResolutionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public OrgIdResolutionException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    /**
     * 组织解析失败分类。
     */
    public enum Reason {
        USERNAME_EMPTY,
        TOKEN_UNAVAILABLE,
        USER_NOT_FOUND,
        INVALID_RESOLVED_ORG_ID
    }
}
