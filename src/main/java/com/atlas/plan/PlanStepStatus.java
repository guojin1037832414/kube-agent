package com.atlas.plan;

/**
 * Plan 步骤状态。
 *
 * <p>M4 Plan-and-Execute 最小 POC 中，计划节点只负责生成与展示步骤，
 * 不直接驱动真实 Tool 执行。因此这里的状态主要用于表达计划阶段的
 * 预期执行顺序和风险标记，为后续 execute_node 扩展预留稳定枚举。</p>
 */
public enum PlanStepStatus {
    /** 步骤已生成，等待后续执行或用户确认。 */
    PENDING,

    /** 步骤已完成。当前 POC 中仅用于只读/说明类伪执行结果。 */
    SUCCEEDED,

    /** 步骤需要人工确认后才能执行。 */
    WAITING_HITL,

    /** 步骤失败。 */
    FAILED,

    /** 步骤被跳过。 */
    SKIPPED
}
