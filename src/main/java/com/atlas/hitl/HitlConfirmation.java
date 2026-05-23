package com.atlas.hitl;

/**
 * HITL 服务端确认凭证。
 *
 * <p>该对象只允许由后端 {@code HITLController} 在 confirmToken 校验成功后写入 Graph State，
 * 执行层 {@code tool_call} 只信任这个服务端 marker，不信任 LLM、前端或 Tool 参数中任何
 * 形如 confirmed / hitlConfirmed 的字段。这样可以保证高风险 Tool 在无人工确认时 fail-closed，
 * 即使 LLM 直接产出 CALL_TOOL 或用户伪造参数，也不能绕过执行层强拦截。</p>
 *
 * @param threadId 原 Graph 会话线程 ID
 * @param target 已确认执行的 Tool/intent 目标
 * @param confirmedBy 确认来源，当前固定为 human
 * @param confirmedAtEpochMs 服务端确认时间戳
 */
public record HitlConfirmation(
    String threadId,
    String target,
    String confirmedBy,
    long confirmedAtEpochMs
) {

    /** 服务端人工确认来源标识。 */
    public static final String CONFIRMED_BY_HUMAN = "human";

    /**
     * 创建人工确认凭证。
     *
     * @param threadId 原 Graph 会话线程 ID
     * @param target 已确认执行的 Tool/intent 目标
     * @return 不可变确认凭证
     */
    public static HitlConfirmation human(String threadId, String target) {
        return new HitlConfirmation(threadId, target, CONFIRMED_BY_HUMAN, System.currentTimeMillis());
    }

    /**
     * 判断当前凭证是否能授权指定目标执行。
     *
     * <p>目标必须完全一致，避免用户确认 A Tool 后被恢复链路或 LLM 决策污染为 B Tool。</p>
     *
     * @param expectedTarget 待执行 Tool/intent 目标
     * @return true 表示该目标已由人工确认
     */
    public boolean allows(String expectedTarget) {
        return CONFIRMED_BY_HUMAN.equals(confirmedBy)
            && target != null
            && expectedTarget != null
            && target.equals(expectedTarget);
    }
}
