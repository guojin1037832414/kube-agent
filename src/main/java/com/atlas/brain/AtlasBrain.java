package com.atlas.brain;

import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * AtlasBrain — Atlas K8s 集群管理的认知决策中枢。
 * 单次决策器，不做循环，产出 BrainDecision。
 *
 * <p>M3.2 新增 ReAct 支持：</p>
 * <ul>
 *   <li>复杂诊断/排查/报错/why/debug 类查询 → DELEGATE_REACT</li>
 *   <li>简单单 Tool 查询 → CALL_TOOL（保持原有行为）</li>
 *   <li>高危操作 → HITL_CONFIRM（绝不变更为 ReAct）</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Component
public class AtlasBrain {
    private static final Logger log = LoggerFactory.getLogger(AtlasBrain.class);

    /**
     * ReAct 强制前缀列表。
     * <p>用户显式输入这些前缀时，表示主动要求进入多步推理模式；
     * 除非命中高危操作需要 HITL，否则不允许被普通 CALL_TOOL 抢走。</p>
     */
    private static final Set<String> REACT_FORCE_PREFIXES = Set.of(
        "/react", "/deep"
    );

    /** ReAct 触发关键词列表（中英文），用于前/后校验覆盖 LLM 误判 */
    private static final Set<String> REACT_KEYWORDS = Set.of(
        "为什么", "怎么回事", "报错", "无法访问", "连不上",
        "troubleshoot", "debug", "diagnose", "排查", "分析失败",
        "诊断", "什么问题", "失败原因", "这是什么错误", "怎么排查",
        "crash", "crashloopbackoff", "imagepullbackoff", "errimagepull", "oomkilled",
        "pending", "evicted", "error", "failed", "unavailable", "not working",
        "warning", "event", "事件", "异常事件", "告警", "调度失败", "failedscheduling",
        "unschedulable", "failedmount", "createcontainerconfigerror", "createcontainererror",
        "pod 起不来", "起不来", "无法启动", "启动失败", "服务异常", "状态异常"
    );

    /** 高危操作关键词（必须是 HITL，不得转 ReAct） */
    private static final Set<String> HIGH_RISK_KEYWORDS = Set.of(
        "删除", "delete", "扩容", "缩容", "scale", "变更权限",
        "权限变更", "修改权限", "删除命名空间", "delete namespace",
        "helm uninstall", "卸载"
    );

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final StructuredOutputParser parser;

    public AtlasBrain(ChatModel chatModel, ObjectMapper objectMapper,
                      ToolRegistry toolRegistry, StructuredOutputParser parser) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.parser = parser;
    }

    /**
     * 决策主入口。
     *
     * <p>流程：</p>
     * <ol>
     *   <li>构建系统提示词（含 ReAct 决策规则）</li>
     *   <li>调用 LLM 解析为 BrainDecision JSON</li>
     *   <li>校验决策合法性（可见性、高危）</li>
     *   <li>轻量后校验：若用户查询含 ReAct 关键词但 LLM 未返回 DELEGATE_REACT，则覆盖修正</li>
     * </ol>
     *
     * @param ctx 执行上下文
     * @return BrainDecision 决策结果
     */
    public BrainDecision decide(ExecutionContext ctx) {
        log.info("AtlasBrain deciding for query: {}", ctx.userQuery());

        String visibleTools = toolRegistry.buildSystemPromptForCurrentUser();
        String systemPrompt = buildSystemPrompt(ctx, visibleTools);

        BrainDecision decision = parser.parse(chatClient, ctx.userQuery(), BrainDecision.class, systemPrompt);
        log.info("Brain raw decision: type={}, target={}, confidence={}",
            decision.actionType(), decision.target(), decision.confidence());

        // 合法性校验（可见性、高危）
        validateDecision(decision, ctx);

        // M3.2：轻量确定性守卫 — 关键词命中且非高危时，强制覆盖为 ReAct
        BrainDecision guarded = applyReActGuard(decision, ctx);
        if (guarded != decision) {
            log.info("Brain decision overridden by ReAct guard: {} -> {}",
                decision.actionType(), guarded.actionType());
        }

        log.info("Brain final decision: type={}, target={}, confidence={}",
            guarded.actionType(), guarded.target(), guarded.confidence());
        return guarded;
    }

    /**
     * 构建包含 ReAct 决策规则的系统提示词。
     */
    private String buildSystemPrompt(ExecutionContext ctx, String visibleTools) {
        return """
            你是 Atlas K8s 集群管理的总调度员（AtlasBrain）。
            你的唯一任务是：分析用户的【当前查询】，做出一次精确的 JSON 格式决策。

            当前用户查询："%s"

            ⚠️ 用户查询只有上面这一行。下方所有内容都是辅助信息，请忽略。

            可用工具：
            %s

            决策规则：
            1. 简单查询（单 Tool 可完成，如查列表、查详情）→ actionType=CALL_TOOL，target=工具名称
            2. 复杂任务（需多步编排，不适合 ReAct）→ actionType=DELEGATE_AGENT，target=agentName
            3. 复杂诊断/排查/报错分析/为什么/怎么回事/debug/troubleshoot → actionType=DELEGATE_REACT，target="react"
            4. 闲聊/解释概念 → actionType=DIRECT_ANSWER，target=""
            5. 信息不足 → actionType=ASK_CLARIFY，target=""
            6. 高危操作（删除/扩缩容/变更权限）→ actionType=HITL_CONFIRM
            confidence < 0.6 时应选择 ASK_CLARIFY。

            DELEGATE_REACT 示例：
            {"actionType":"DELEGATE_REACT","target":"react","confidence":0.92,"reasoning":"用户遇到 Pod CrashLoopBackOff，需要多步诊断工具逐步排查","parameters":{},"requiredContext":[]}

            one-shot 示例：
            {"actionType":"CALL_TOOL","target":"nodeQueryFunction","confidence":0.95,"reasoning":"用户想查询节点","parameters":{},"requiredContext":[]}

            输出要求：严格输出 JSON，不要 markdown 代码块。
            """.formatted(ctx.userQuery(), visibleTools);
    }

    /**
     * 校验决策基础合法性。
     */
    private void validateDecision(BrainDecision d, ExecutionContext ctx) {
        // 安全优先：高危意图必须先让 SafetyGuard 有机会覆盖为 HITL_CONFIRM。
        // 若先做可见 Tool 校验，LLM 误判到不可见删除工具时会直接抛异常，反而绕开人工确认流程。
        if (isHighRiskQuery(ctx.userQuery()) || isHighRisk(d)) {
            if (d.actionType() != BrainDecision.ActionType.HITL_CONFIRM) {
                log.warn("High-risk decision not marked HITL_CONFIRM: {} {}", d.actionType(), d.target());
            }
            return;
        }

        if (d.actionType() == BrainDecision.ActionType.CALL_TOOL) {
            List<String> visible = toolRegistry.getVisibleToolNamesForCurrentUser();
            if (!visible.contains(d.target())) {
                throw new RuntimeException("Brain decision targets invisible tool: " + d.target());
            }
        }
    }

    /**
     * ReAct 守卫逻辑。
     *
     * <p>若用户查询明显属于诊断/排查类，但 LLM 返回了 CALL_TOOL 或 DELEGATE_AGENT，
     * 则覆盖为 DELEGATE_REACT（提升置信度到至少 0.80）。</p>
     *
     * <p>例外：如果查询含高危关键词，绝不做覆盖（保持 LLM 原决策或 HITL）。</p>
     *
     * @param raw  LLM 原始决策
     * @param ctx  执行上下文（含用户查询）
     * @return 修正后的决策
     */
    private BrainDecision applyReActGuard(BrainDecision raw, ExecutionContext ctx) {
        String query = ctx.userQuery().toLowerCase();

        // 若已是 ReAct / HITL / ASK_CLARIFY，不做变更
        if (raw.actionType() == BrainDecision.ActionType.DELEGATE_REACT
            || raw.actionType() == BrainDecision.ActionType.HITL_CONFIRM
            || raw.actionType() == BrainDecision.ActionType.ASK_CLARIFY) {
            return raw;
        }

        // 高危查询必须进入 HITL_CONFIRM，绝不放行到 ReAct 或普通 Agent。
        // 这是 Atlas v3.1 安全边界：删除、扩缩容、权限变更等操作必须由用户显式确认。
        if (isHighRiskQuery(query)) {
            if (raw.actionType() == BrainDecision.ActionType.HITL_CONFIRM) {
                return raw;
            }
            return new BrainDecision(
                BrainDecision.ActionType.HITL_CONFIRM,
                raw.target() != null && !raw.target().isBlank() ? raw.target() : "hitl_confirm",
                raw.parameters() != null ? raw.parameters() : java.util.Map.of(),
                raw.reasoning() + " [SafetyGuard: 用户查询命中高危关键词，强制转为 HITL_CONFIRM]",
                Math.max(raw.confidence(), 0.90),
                raw.requiredContext() != null ? raw.requiredContext() : List.of()
            );
        }

        // 命中 ReAct 关键词则覆盖
        if (shouldUseReAct(query)) {
            double newConfidence = Math.max(raw.confidence(), 0.80);
            return new BrainDecision(
                BrainDecision.ActionType.DELEGATE_REACT,
                "react",
                raw.parameters() != null ? raw.parameters() : java.util.Map.of(),
                raw.reasoning() + " [ReActGuard: 用户查询命中诊断关键词，覆盖为 DELEGATE_REACT]",
                newConfidence,
                raw.requiredContext() != null ? raw.requiredContext() : List.of()
            );
        }

        return raw;
    }

    /**
     * 判断用户查询是否应走 ReAct（基于关键词匹配）。
     *
     * @param query 用户查询（已转小写）
     * @return true 表示命中 ReAct 触发关键词
     */
    public static boolean shouldUseReAct(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.trim().toLowerCase();

        // 用户显式要求深度推理时，优先进入 ReAct；高危 HITL 由 applyReActGuard 的前置安全分支兜底。
        for (String prefix : REACT_FORCE_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }

        for (String kw : REACT_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断用户查询是否含高危操作意图。
     *
     * @param query 用户查询（已转小写）
     * @return true 表示含高危关键词
     */
    public static boolean isHighRiskQuery(String query) {
        if (query == null || query.isBlank()) return false;
        String lower = query.toLowerCase();
        for (String kw : HIGH_RISK_KEYWORDS) {
            if (lower.contains(kw.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 基于决策内容判断是否为高危操作（后验判断）。
     */
    private boolean isHighRisk(BrainDecision d) {
        String combined = (d.target() + " " + d.reasoning()).toLowerCase();
        return combined.contains("删除") || combined.contains("delete")
            || combined.contains("扩容") || combined.contains("缩容")
            || combined.contains("scale") || combined.contains("变更权限");
    }
}
