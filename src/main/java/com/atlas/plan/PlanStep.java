package com.atlas.plan;

import java.util.Map;

/**
 * 单个计划步骤。
 *
 * <p>该 record 是 PlanEngine 的结构化输出之一。字段中的 suggestedTool、riskLevel
 * 仅用于展示和后续调度参考，不能作为执行层安全判定依据；真正执行前必须重新
 * 从 ToolRegistry 解析 ToolMetadata，并再次经过 HitlGuard。</p>
 *
 * <p>M4-PX.4 起，parameters 作为 Plan 输出的“不可信业务参数”槽位，用于把
 * 只读计划中的查询条件传递给 execute_node。该字段不能承载 token、orgId、userId、
 * conversationId 等系统上下文字段，也不能作为授权、HITL 或租户判定依据；执行层
 * 必须按 fail-closed 策略过滤受保护字段，并由服务端可信上下文覆盖真实身份信息。</p>
 *
 * @param id 步骤稳定标识，便于后续前端渲染和审计
 * @param index 步骤序号，从 1 开始
 * @param title 步骤标题
 * @param description 步骤说明
 * @param suggestedTool 建议工具名，仅供参考，不直接执行
 * @param parameters 不可信业务参数，只允许承载工具查询条件，不允许承载系统上下文
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
    Map<String, Object> parameters,
    String riskLevel,
    boolean requiresConfirmation,
    PlanStepStatus status
) {
}
