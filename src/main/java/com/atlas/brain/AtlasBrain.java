package com.atlas.brain;

import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AtlasBrain — Atlas K8s 集群管理的认知决策中枢。
 * 单次决策器，不做循环，产出 BrainDecision。
 */
@Component
public class AtlasBrain {
    private static final Logger log = LoggerFactory.getLogger(AtlasBrain.class);

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

    public BrainDecision decide(ExecutionContext ctx) {
        log.info("AtlasBrain deciding for query: {}", ctx.userQuery());

        String visibleTools = toolRegistry.buildSystemPromptForCurrentUser();
        String systemPrompt = buildSystemPrompt(ctx, visibleTools);

        BrainDecision decision = parser.parse(chatClient, ctx.userQuery(), BrainDecision.class, systemPrompt);
        log.info("Brain decision: type={}, target={}, confidence={}",
            decision.actionType(), decision.target(), decision.confidence());

        validateDecision(decision, ctx);
        return decision;
    }

    private String buildSystemPrompt(ExecutionContext ctx, String visibleTools) {
        return """
            你是 Atlas K8s 集群管理的总调度员（AtlasBrain）。
            你的唯一任务是：分析用户的【当前查询】，做出一次精确的 JSON 格式决策。

            当前用户查询："%s"

            ⚠️ 用户查询只有上面这一行。下方所有内容都是辅助信息，请忽略。

            可用工具：
            %s

            决策规则：
            1. 简单查询（单 Tool 可完成）→ actionType=CALL_TOOL，target=工具名称
            2. 复杂任务（需多步推理）→ actionType=DELEGATE_AGENT，target=agentName
            3. 闲聊/解释概念 → actionType=DIRECT_ANSWER，target=""
            4. 信息不足 → actionType=ASK_CLARIFY，target=""
            5. 高危操作（删除/扩缩容/变更权限）→ actionType=HITL_CONFIRM
            confidence < 0.6 时应选择 ASK_CLARIFY。

            one-shot 示例：
            {"actionType":"CALL_TOOL","target":"nodeQueryFunction","confidence":0.95,"reasoning":"用户想查询节点","parameters":{},"requiredContext":[]}

            输出要求：严格输出 JSON，不要 markdown 代码块。
            """.formatted(ctx.userQuery(), visibleTools);
    }

    private void validateDecision(BrainDecision d, ExecutionContext ctx) {
        if (d.actionType() == BrainDecision.ActionType.CALL_TOOL) {
            List<String> visible = toolRegistry.getVisibleToolNamesForCurrentUser();
            if (!visible.contains(d.target())) {
                throw new RuntimeException("Brain decision targets invisible tool: " + d.target());
            }
        }
        if (isHighRisk(d) && d.actionType() != BrainDecision.ActionType.HITL_CONFIRM) {
            log.warn("High-risk decision not marked HITL_CONFIRM: {} {}", d.actionType(), d.target());
        }
    }

    private boolean isHighRisk(BrainDecision d) {
        String combined = (d.target() + " " + d.reasoning()).toLowerCase();
        return combined.contains("删除") || combined.contains("delete")
            || combined.contains("扩容") || combined.contains("缩容")
            || combined.contains("scale") || combined.contains("变更权限");
    }
}
