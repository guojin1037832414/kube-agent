package com.atlas.plan;

import com.atlas.brain.BrainDecision;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Plan-and-Execute + Reflection 最小 POC 引擎。
 *
 * <p>本类当前只做“规划 + 单次自检”，不直接执行任何 Tool，不访问 kube-manager，
 * 不创建或写入 HitlConfirmation。这样可以先把 PLAN 分支和 Graph State 闭环跑通，
 * 同时不破坏 M5 已完成的 HITL fail-closed 安全边界。</p>
 *
 * <p>后续若要加入 execute_node，必须把执行入口收敛到统一 Tool 执行服务，
 * 并在 tool.execute 前强制调用 HitlGuard。</p>
 */
@Component
public class PlanEngine {

    /** 高危意图关键词：只用于计划展示风险，不作为最终安全判定。 */
    private static final List<String> RISK_KEYWORDS = List.of(
        "删除", "delete", "扩容", "缩容", "scale", "重启", "restart",
        "停止", "stop", "修改", "变更", "权限", "创建", "create", "发布"
    );

    /** 诊断类关键词：用于生成诊断计划步骤，不替代 ReAct 路由。 */
    private static final List<String> DIAG_KEYWORDS = List.of(
        "为什么", "排查", "诊断", "debug", "troubleshoot", "crashloop", "pending", "异常", "报错"
    );

    /**
     * 生成最小计划结果。
     *
     * <p>注意：context 中可能包含 token/orgId 等敏感运行期信息，当前 POC 不把这些
     * 信息拼接进面向用户的 finalAnswer，避免泄露认证上下文。</p>
     *
     * @param userQuery 用户原始输入
     * @param decision AtlasBrain 的 PLAN 决策
     * @param context 运行期上下文，仅用于后续扩展；当前不外显敏感字段
     * @return 结构化计划结果
     */
    public PlanResult plan(String userQuery, BrainDecision decision, Map<String, Object> context) {
        String query = userQuery == null ? "" : userQuery.trim();
        boolean risky = containsAny(query, RISK_KEYWORDS) || isRiskyDecision(decision);
        boolean diagnostic = containsAny(query, DIAG_KEYWORDS);

        List<PlanStep> steps = new ArrayList<>();
        steps.add(new PlanStep(
            "step-1-understand",
            1,
            "确认目标与上下文",
            query.isBlank() ? "先补充用户目标、资源名称、命名空间和期望结果。" : "确认用户目标：" + query,
            "",
            "READ",
            false,
            PlanStepStatus.PENDING
        ));

        if (diagnostic) {
            steps.add(new PlanStep(
                "step-2-diagnose",
                2,
                "收集诊断信息",
                "后续可委派 ReAct 诊断链路逐步读取状态、事件和日志，当前 PLAN 阶段不直接执行工具。",
                "react_node",
                "READ",
                false,
                PlanStepStatus.PENDING
            ));
        } else {
            steps.add(new PlanStep(
                "step-2-read-state",
                2,
                "读取当前资源状态",
                "在真正变更前，应先通过只读 Tool 查询当前资源、配额、权限和运行状态。",
                "query/read-only-tool",
                "READ",
                false,
                PlanStepStatus.PENDING
            ));
        }

        steps.add(new PlanStep(
            risky ? "step-3-hitl-required" : "step-3-execute-or-answer",
            3,
            risky ? "等待人工确认后再执行" : "执行低风险步骤或输出结论",
            risky
                ? "该计划包含潜在变更/高危动作，必须通过 HITL 确认后才能进入真实执行路径。"
                : "若后续步骤仍为普通只读查询，可进入受保护的 Tool 执行路径；当前 PLAN 阶段尚未执行。",
            risky ? "hitl_confirm" : "tool_call/react_node",
            risky ? "ACTION" : "READ",
            risky,
            risky ? PlanStepStatus.WAITING_HITL : PlanStepStatus.PENDING
        ));

        ReflectionResult reflection = reflect(steps, risky);
        String summary = risky
            ? "已生成安全优先的执行计划：包含需要人工确认的风险步骤。"
            : "已生成最小执行计划：当前仅规划，不执行真实操作。";
        String nextActionHint = risky
            ? "如需继续执行，请通过 HITL 确认具体目标与参数；确认前不会调用任何高危工具。"
            : "如需继续执行，可由后续 execute_node 或 ReAct 在 HitlGuard 保护下处理。";
        String finalAnswer = renderFinalAnswer(summary, steps, nextActionHint, reflection);

        return new PlanResult(
            summary,
            List.copyOf(steps),
            false,
            risky,
            nextActionHint,
            reflection,
            finalAnswer
        );
    }

    /**
     * 单次 Reflection 自检：只验证计划是否具备步骤与 HITL 标记，不触发重试或执行。
     */
    private ReflectionResult reflect(List<PlanStep> steps, boolean risky) {
        List<String> issues = new ArrayList<>();
        if (steps == null || steps.isEmpty()) {
            issues.add("计划步骤为空");
        }
        if (risky && steps.stream().noneMatch(PlanStep::requiresConfirmation)) {
            issues.add("风险计划缺少人工确认步骤");
        }
        if (!issues.isEmpty()) {
            return new ReflectionResult(false, List.copyOf(issues), "请重新生成计划，并确保风险步骤显式标记 HITL。 ");
        }
        return ReflectionResult.passed("计划结构完整；当前 POC 未执行任何真实操作。 ");
    }

    /**
     * 渲染给用户看的最终文本，明确声明“尚未执行”。
     */
    private String renderFinalAnswer(String summary, List<PlanStep> steps,
                                     String nextActionHint, ReflectionResult reflection) {
        StringBuilder sb = new StringBuilder();
        sb.append("📋 ").append(summary).append("\n\n");
        sb.append("注意：以下只是计划，尚未执行任何真实操作。\n\n");
        for (PlanStep step : steps) {
            sb.append(step.index()).append(". ").append(step.title()).append("\n")
                .append("   - ").append(step.description()).append("\n")
                .append("   - 风险: ").append(step.riskLevel())
                .append("，需确认: ").append(step.requiresConfirmation() ? "是" : "否")
                .append("\n");
        }
        sb.append("\nReflection: ")
            .append(reflection.passed() ? "通过" : "未通过")
            .append("，").append(reflection.suggestion()).append("\n");
        sb.append("下一步：").append(nextActionHint);
        return sb.toString();
    }

    /**
     * 判断文本中是否包含任一关键词。
     */
    private boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(keyword -> lower.contains(keyword.toLowerCase(Locale.ROOT)));
    }

    /**
     * 对 LLM 决策做保守风险识别，仅影响计划展示，不影响执行安全边界。
     */
    private boolean isRiskyDecision(BrainDecision decision) {
        if (decision == null) {
            return false;
        }
        String combined = String.valueOf(decision.target()) + " " + String.valueOf(decision.reasoning());
        return containsAny(combined, RISK_KEYWORDS);
    }
}
