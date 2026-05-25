package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4.2 / M4-PX Plan-and-Execute 安全契约测试。
 *
 * <p>本测试只读取源码并验证关键结构，不启动 Spring，不调用 LLM，
 * 不访问 kube-manager，也不会执行任何真实 Tool。目标是把 Plan-and-Execute 的安全边界固化下来：
 * plan_node 只规划；execute_node 在 M4-PX.4 起只允许通过 SafeToolExecutor 执行单步 READ 候选，
 * 不得直接执行 Tool、不得创建服务端确认 marker、不得绕过 HITL / 多租户保护。</p>
 */
class M42PlanExecuteSafetyContractTest {

    private static final Path BRAIN_DECISION = Path.of("src/main/java/com/atlas/brain/BrainDecision.java");
    private static final Path GRAPH_CONFIG = Path.of("src/main/java/com/atlas/graph/config/AtlasGraphConfig.java");
    private static final Path PLAN_ENGINE = Path.of("src/main/java/com/atlas/plan/PlanEngine.java");
    private static final Path PLAN_RESULT = Path.of("src/main/java/com/atlas/plan/PlanResult.java");
    private static final Path PLAN_STEP = Path.of("src/main/java/com/atlas/plan/PlanStep.java");
    private static final Path SAFE_TOOL_EXECUTOR = Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutor.java");

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
            .contains("graph.addNode(\"execute_node\", buildExecuteNode(safeToolExecutor))")
            .contains("graph.addEdge(\"plan_node\", \"execute_node\")")
            .contains("graph.addEdge(\"execute_node\", END)")
            .contains("case DELEGATE_REACT -> \"react_node\"")
            .contains("case HITL_CONFIRM -> \"hitl_confirm");
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
            .contains("strategies.put(\"plan_steps\", new ReplaceStrategy())")
            .contains("strategies.put(\"execute_node_result\", new ReplaceStrategy())")
            .contains("strategies.put(\"execute_result\", new ReplaceStrategy())")
            .contains("strategies.put(\"execute_steps\", new ReplaceStrategy())");
    }

    /**
     * plan_node 只能写入 plan/answer 结果，不允许写入 hitl_confirmation 或 tool_result。
     */
    @Test
    void planNode_shouldOnlyPlanAndMustNotWriteConfirmationOrToolResult() throws IOException {
        String source = read(GRAPH_CONFIG);
        String planNode = substringBetween(source,
            "private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildPlanNode",
            "构建 Execute 节点异步动作");

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
            .contains("Map<String, Object> parameters")
            .contains("String riskLevel")
            .contains("boolean requiresConfirmation")
            .contains("不可信业务参数")
            .contains("不能作为执行层安全判定依据");
    }

    /**
     * M4-PX.4：execute_node 可以从 fail-closed 升级为“单步 READ 候选执行”，但只能委托 SafeToolExecutor。
     */
    @Test
    void executeNode_shouldOnlyDelegateSingleReadStepThroughSafeToolExecutor() throws IOException {
        String source = read(GRAPH_CONFIG);
        String executeNode = substringBetween(source,
            "private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildExecuteNode",
            "private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildReActNode");

        assertThat(executeNode)
            .contains("PlanResult planResult = state.value(\"plan_result\")")
            .contains("PLAN_RESULT_MISSING")
            .contains("PLAN_NOT_EXECUTABLE")
            .contains("EXECUTE_STEP_UNSUPPORTED")
            .contains("EXECUTE_STEP_NOT_READ_ONLY")
            .contains("EXECUTE_STEP_REQUIRES_CONFIRMATION")
            .contains("SafeToolExecutionSource.PLAN_EXECUTE_NODE")
            .contains("Map<String, Object> stepParameters = step.parameters()")
            .contains("containsProtectedContextParam(stepParameters)")
            .contains("PROTECTED_PLAN_PARAMETER")
            .contains("SafeToolExecutionRequest request = new SafeToolExecutionRequest")
            .contains("SafeToolExecutionResult result = safeToolExecutor.executeIntent(request)")
            .contains("result.toGraphUpdates()")
            .contains("state.value(\"user_id\")")
            .contains("state.value(\"token\")")
            .contains("state.value(\"orgId\")")
            .contains("state.value(\"conversation_id\")")
            .contains("updates.put(\"execute_node_result\", answer)")
            .contains("updates.put(\"execute_result\", executeResult)")
            .contains("updates.put(\"execute_steps\", planSteps)")
            .doesNotContain("new HitlConfirmation")
            .doesNotContain("HitlConfirmation.human")
            .doesNotContain("tool.execute")
            .doesNotContain("baseTool.execute")
            .doesNotContain("meta.instance().execute")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient");
    }

    /**
     * SafeToolExecutor 必须成为 execute_node 与 Graph tool_call 共享的唯一安全执行边界。
     */
    @Test
    void safeToolExecutor_shouldCentralizeProtectedParamHitlAndThreadLocalSafety() throws IOException {
        String source = read(SAFE_TOOL_EXECUTOR);

        assertThat(source)
            .contains("public class SafeToolExecutor")
            .contains("PROTECTED_CONTEXT_PARAMS")
            .contains("toolRegistry.findByIntentId(intentId)")
            .contains("toolRegistry.canExecuteIntent(intentId)")
            .contains("hitlGuard.verifyByIntentId(")
            .contains("request.source() != SafeToolExecutionSource.PLAN_EXECUTE_NODE")
            .contains("TOOL_PARAMETER_SPEC_MISSING")
            .contains("toolParameterNormalizer.normalize(tool.getToolName(), rawParams)")
            .contains("rejectUnknownPlanParameters(rawParams, allowedParamNames, declaredAliasNames)")
            .contains("TOOL_PARAMETER_UNKNOWN_FOR_PLAN_EXECUTE")
            .contains("declaredAliasNames")
            .contains("allowedParamNames.contains(key)")
            .contains("tool.execute(toolParams)")
            .contains("restoreThreadLocalContext(previousToken, previousOrgId)")
            .contains("系统上下文字段最后写入")
            .contains("缺失 orgId、未注册 Tool、权限不足、HITL 未确认均 fail-closed");
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
