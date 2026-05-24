package com.atlas.plan;

/**
 * 单个计划步骤。
 *
 * <p>该 record 是 PlanEngine 的结构化输出之一。字段中的 suggestedTool、riskLevel
 * 仅用于展示和后续调度参考，不能作为执行层安全判定依据；真正执行前必须重新
 * 从 ToolRegistry 解析 ToolMetadata，并再次经过 HitlGuard。</p>
 *
 * @param id 步骤稳定标识，便于后续前端渲染和审计
 * @param index 步骤序号，从 1 开始
 * @param title 步骤标题
 * @param description 步骤说明
 * @param suggestedTool 建议工具名，仅供参考，不直接执行
 * @param riskLevel 风险等级展示值，如 READ / UPDATE / DELETE / ACTION / UNKNOWN
 * @param requiresConfirmation 是否需要人工确认
 * @param status 当前步骤状态
 */
public record PlanStep(
    String id,
    int index,
    String title,
    String description,
    String suggestedTool,
    String riskLevel,
    boolean requiresConfirmation,
    PlanStepStatus status
) {
}
