package com.atlas.audit;

/**
 * Agent 审计事件结果。
 *
 * <p>M5.25 先建立统一词表，后续持久化、前端回放、红队评测和 OpenTelemetry span
 * 都应复用这些枚举，而不是各层自由拼字符串。</p>
 */
public enum AgentAuditOutcome {

    /** Tool 已被允许并返回业务成功。 */
    SUCCESS,

    /** Tool 已被允许调用，但业务结果为失败或需要澄清。 */
    BUSINESS_FAILURE,

    /** 执行边界在调用 Tool 前阻断。 */
    BLOCKED,

    /** Tool 调用过程中出现异常或 BaseTool 包装出的执行异常。 */
    ERROR
}
