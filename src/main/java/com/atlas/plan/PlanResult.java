package com.atlas.plan;

import java.util.List;

/**
 * PlanEngine 的结构化计划结果。
 *
 * <p>该结果会由 plan_node 写入 Graph State：answer、plan_node_result、
 * plan_result、plan_steps。这样后续可以被 SSE、审计、状态检查器或前端
 * Timeline 复用，而不是只依赖不可解析的自然语言文本。</p>
 *
 * @param summary 计划摘要
 * @param steps 步骤列表
 * @param executable 当前计划是否可直接进入执行；M4.2 POC 默认不自动执行
 * @param requiresConfirmation 是否包含需要 HITL 的步骤
 * @param nextActionHint 下一步建议
 * @param reflection 单次自检结果
 * @param finalAnswer 面向用户展示的最终文本
 */
public record PlanResult(
    String summary,
    List<PlanStep> steps,
    boolean executable,
    boolean requiresConfirmation,
    String nextActionHint,
    ReflectionResult reflection,
    String finalAnswer
) {
}
