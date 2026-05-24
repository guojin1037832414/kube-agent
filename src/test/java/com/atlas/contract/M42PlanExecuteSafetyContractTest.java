package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4.2 Plan-and-Execute 最小 POC 安全契约测试。
 *
 * <p>本测试只读取源码并验证关键结构，不启动 Spring，不调用 LLM，
 * 不访问 kube-manager，也不会执行任何真实 Tool。目标是把“plan_node 只规划、
 * 不执行、不确认、不绕过 HITL”的边界固化下来。</p>
 */
class M42PlanExecuteSafetyContractTest {

    private static final Path BRAIN_DECISION = Path.of("src/main/java/com/atlas/brain/BrainDecision.java");
    private static final Path GRAPH_CONFIG = Path.of("src/main/java/com/atlas/graph/config/AtlasGraphConfig.java");
    private static final Path PLAN_ENGINE = Path.of("src/main/java/com/atlas/plan/PlanEngine.java");
    private static final Path PLAN_RESULT = Path.of("src/main/java/com/atlas/plan/PlanResult.java");
    private static final Path PLAN_STEP = Path.of("src/main/java/com/atlas/plan/PlanStep.java");

    /**
     * BrainDecision 必须显式支持 PLAN，避免 LLM 输出 PLAN 时反序列化失败。
     */
    @Test
    void brainDecision_shouldDeclarePlanActionType() throws IOException {
        String source = read(BRAIN_DECISION);

        assertThat(source)
            .contains("PLAN")
            .contains("DELEGATE_REACT")
            .contains("HITL_CONFIRM");
    }

    /**
     * supervisorGraph 必须把 PLAN 路由到 plan_node，同时保留 ReAct/HITL 原有路由。
     */
    @Test
    void supervisorGraph_shouldRoutePlanToPlanNodeWithoutBreakingReactAndHitl() throws IOException {
        String source = read(GRAPH_CONFIG);

        assertThat(source)
            .contains("case PLAN -> \"plan_node\"")
            .contains("\"plan_node\", \"plan_node\"")
            .contains("graph.addNode(\"plan_node\", buildPlanNode(planEngine))")
            .contains("graph.addEdge(\"plan_node\", END)")
            .contains("case DELEGATE_REACT -> \"react_node\"")
            .contains("case HITL_CONFIRM -> \"hitl_confirm\"");
    }

    /**
     * Plan 相关 State key 必须显式声明，便于后续 SSE、审计和前端 Timeline 消费。
     */
    @Test
    void graphState_shouldDeclarePlanKeys() throws IOException {
        String source = read(GRAPH_CONFIG);

        assertThat(source)
            .contains("strategies.put(\"plan_node_result\", new ReplaceStrategy())")
            .contains("strategies.put(\"plan_result\", new ReplaceStrategy())")
            .contains("strategies.put(\"plan_steps\", new ReplaceStrategy())");
    }

    /**
     * plan_node 只能写入 plan/answer 结果，不允许写入 hitl_confirmation 或 tool_result。
     */
    @Test
    void planNode_shouldOnlyPlanAndMustNotWriteConfirmationOrToolResult() throws IOException {
        String source = read(GRAPH_CONFIG);
        String planNode = substringBetween(source,
            "private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildPlanNode",
            "private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildReActNode");

        assertThat(planNode)
            .contains("planEngine.plan(input, decision, context)")
            .contains("updates.put(\"answer\", result.finalAnswer())")
            .contains("updates.put(\"plan_node_result\", result.finalAnswer())")
            .contains("updates.put(\"plan_result\", result)")
            .contains("updates.put(\"plan_steps\", result.steps()")
            .doesNotContain("hitl_confirmation")
            .doesNotContain("tool_result")
            .doesNotContain("HitlConfirmation.human")
            .doesNotContain("tool.execute")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient");
    }

    /**
     * PlanEngine 作为最小 POC 不得直接调用 Tool 或 HTTP client，也不得创建服务端确认 marker。
     */
    @Test
    void planEngine_shouldNotExecuteToolsOrCreateHitlConfirmation() throws IOException {
        String source = read(PLAN_ENGINE);

        assertThat(source)
            .contains("public class PlanEngine")
            .contains("public PlanResult plan")
            .contains("当前 POC 未执行任何真实操作")
            .doesNotContain("BaseTool")
            .doesNotContain("ToolRegistry")
            .doesNotContain("HitlConfirmation.human")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain(".execute(");
    }

    /**
     * Plan DTO 必须是结构化输出，并明确风险字段只供展示，不能作为执行安全依据。
     */
    @Test
    void planDtos_shouldExposeStructuredStateForAudit() throws IOException {
        assertThat(read(PLAN_RESULT))
            .contains("public record PlanResult")
            .contains("List<PlanStep> steps")
            .contains("ReflectionResult reflection")
            .contains("String finalAnswer");
        assertThat(read(PLAN_STEP))
            .contains("public record PlanStep")
            .contains("String suggestedTool")
            .contains("String riskLevel")
            .contains("boolean requiresConfirmation")
            .contains("不能作为执行层安全判定依据");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String substringBetween(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end);
        assertThat(startIndex).as("start marker must exist").isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end marker must exist").isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
