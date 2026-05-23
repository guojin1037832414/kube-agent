package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.13 HITL fail-closed 执行层契约测试。
 *
 * <p>本测试只读取源码并验证关键安全结构，不启动 Spring，不调用 LLM，
 * 不访问 kube-manager，也不会执行任何真实 CREATE/UPDATE/DELETE/ACTION 操作。</p>
 *
 * <p>目标：把“高风险 Tool 无确认拒绝执行，有确认才放行”的安全边界固定在执行层，
 * 防止未来只改前端提示、只信 LLM 参数或绕过 {@code tool.execute(...)} 前置检查。</p>
 */
class M513HitlFailClosedContractTest {

    private static final Path GRAPH_CONFIG = Path.of("src/main/java/com/atlas/graph/config/AtlasGraphConfig.java");
    private static final Path HITL_CONTROLLER = Path.of("src/main/java/com/atlas/controller/HITLController.java");
    private static final Path HITL_CONFIRMATION = Path.of("src/main/java/com/atlas/hitl/HitlConfirmation.java");
    private static final Path HITL_GUARD = Path.of("src/main/java/com/atlas/hitl/HitlGuard.java");
    private static final Path REACT_ENGINE = Path.of("src/main/java/com/atlas/react/ReActEngine.java");
    private static final Path ORCHESTRATOR = Path.of("src/main/java/com/atlas/orchestrator/AtlasOrchestrator.java");
    private static final Path BRIDGE_CALLBACK = Path.of("src/main/java/com/atlas/graph/bridge/AtlasToolCallback.java");
    private static final Path BRIDGE_FACTORY = Path.of("src/main/java/com/atlas/graph/bridge/AtlasToolCallbackFactory.java");

    /**
     * 执行层必须通过统一 HitlGuard 依据 Tool 元数据做 fail-closed 风险判断。
     */
    @Test
    void hitlGuard_shouldFailClosedByToolMetadata() throws IOException {
        String source = read(HITL_GUARD);

        assertThat(source)
            .as("HitlGuard 必须作为统一执行层守卫存在")
            .contains("public class HitlGuard")
            .contains("HITL_CONFIRMATION_REQUIRED")
            .contains("requiresConfirmation(ToolRegistry.ToolMetadata metadata)")
            .contains("metadata.requiresConfirmation()")
            .contains("operationType != AtlasToolMapping.OperationType.READ")
            .contains("metadata == null")
            .contains("HitlConfirmation confirmation")
            .contains("confirmation.allows(target)");
    }

    /**
     * Graph tool_call 入口必须在 tool.execute 之前读取服务端确认 marker 并调用 HitlGuard。
     */
    @Test
    void graphToolCall_shouldCheckServerHitlConfirmationBeforeToolExecute() throws IOException {
        String source = read(GRAPH_CONFIG);

        int confirmationIndex = source.indexOf("state.value(\"hitl_confirmation\")");
        int guardIndex = source.indexOf("hitlGuard.verifyByIntentId(toolRegistry, intentId, confirmation)");
        int executeIndex = source.indexOf("tool.execute(toolParams)");

        assertThat(confirmationIndex)
            .as("Graph tool_call 必须读取服务端 hitl_confirmation marker")
            .isGreaterThanOrEqualTo(0);
        assertThat(guardIndex)
            .as("Graph tool_call 必须调用统一 HitlGuard")
            .isGreaterThanOrEqualTo(0);
        assertThat(executeIndex)
            .as("Graph tool_call 必须仍然存在真实 tool.execute 调用")
            .isGreaterThanOrEqualTo(0);
        assertThat(confirmationIndex).isLessThan(executeIndex);
        assertThat(guardIndex).isLessThan(executeIndex);
    }

    /**
     * confirm 恢复时 HITLController 会注入已确认的 CALL_TOOL 决策，supervisor 节点必须优先复用，
     * 否则 atlasBrain.decide 会重新决策并覆盖目标 Tool，导致确认后无法进入 tool_call 放行。
     */
    @Test
    void supervisorGraph_shouldReuseInjectedBrainDecisionOnResume() throws IOException {
        String source = read(GRAPH_CONFIG);

        int reuseIndex = source.indexOf("BrainDecision existingDecision = state.value(\"brain_decision\")");
        int decideIndex = source.indexOf("BrainDecision decision = atlasBrain.decide(ctx)");

        assertThat(reuseIndex)
            .as("supervisor 节点必须优先读取 resume 注入的 brain_decision")
            .isGreaterThanOrEqualTo(0);
        assertThat(decideIndex)
            .as("普通新请求仍然需要调用 AtlasBrain 决策")
            .isGreaterThanOrEqualTo(0);
        assertThat(reuseIndex)
            .as("复用注入决策必须发生在 atlasBrain.decide 之前")
            .isLessThan(decideIndex);
        assertThat(source)
            .contains("updates.put(\"brain_decision\", existingDecision)")
            .contains("确认后仍无法进入 tool_call");
    }

    /**
     * Review 发现的直接执行入口必须全部接入 HitlGuard，避免 ReAct/legacy/ToolCallback 绕过 Graph 拦截。
     */
    @Test
    void allKnownToolExecuteEntrances_shouldUseHitlGuardBeforeExecute() throws IOException {
        assertGuardBeforeExecute(read(REACT_ENGINE),
            "hitlGuard.verify(toolName, meta, null)",
            "meta.instance().execute(params)",
            "ReActEngine.runWithEvents");

        assertGuardBeforeExecute(read(ORCHESTRATOR),
            "hitlGuard.verifyByIntentId(toolRegistry, result.intentId(), null)",
            "tool.execute(toolParams)",
            "AtlasOrchestrator legacy fallback");

        assertGuardBeforeExecute(read(BRIDGE_CALLBACK),
            "hitlGuard.verify(baseTool.getToolName(), atlasMetadata, null)",
            "baseTool.execute(normalizedParams)",
            "Spring AI AtlasToolCallback");

        assertThat(read(BRIDGE_FACTORY))
            .as("ToolCallbackFactory 必须向 callback 传入真实 ToolMetadata，避免 READ 查询被 metadata=null 误拦截")
            .contains("HitlGuard hitlGuard")
            .contains("new AtlasToolCallback((BaseTool) meta.instance(), objectMapper, parameterNormalizer, hitlGuard, meta)")
            .contains("toolRegistry.resolve(tool.getToolName())");
    }

    /**
     * confirm 接口校验 token 后必须注入服务端 marker，clarify 路径必须显式清空旧 marker。
     */
    @Test
    void hitlController_shouldInjectConfirmationOnlyForConfirmedResumeAndClearOnClarify() throws IOException {
        String source = read(HITL_CONTROLLER);

        assertThat(source)
            .as("HITLController 必须创建服务端 HitlConfirmation，而不是信任前端参数")
            .contains("@Qualifier(\"supervisorGraph\") CompiledGraph compiledGraph")
            .contains("HitlConfirmation.human(threadId, original.target())")
            .contains("inputs.put(\"hitl_confirmation\", confirmation)");
        assertThat(source)
            .as("clarify 恢复不是人工确认，不能携带旧 HitlConfirmation")
            .contains("runResumeWithCheckpointContext(threadId, clarified, null, emitter)")
            .contains("inputs.put(\"hitl_confirmation\", null)");
    }

    /**
     * 普通新会话必须显式清空确认 marker，只有 HITLController.confirmAndResume 能写入可信 marker。
     */
    @Test
    void normalGraphEntrypoints_shouldClearConfirmationMarker() throws IOException {
        String source = read(ORCHESTRATOR);

        assertThat(source)
            .as("普通 Graph/Supervisor 新会话必须显式清空 hitl_confirmation，避免 checkpoint 或线程状态继承旧确认")
            .contains("inputs.put(\"hitl_confirmation\", null);")
            .contains("普通新请求永远不携带服务端确认 marker")
            .contains("只有 HITLController.confirmAndResume 可注入");
    }

    /**
     * HitlConfirmation 自身必须是后端可信 marker，并且只允许确认目标精确匹配的 Tool。
     */
    @Test
    void hitlConfirmation_shouldBeServerSideExactTargetMarker() throws IOException {
        String source = read(HITL_CONFIRMATION);

        assertThat(source)
            .contains("public record HitlConfirmation")
            .contains("CONFIRMED_BY_HUMAN")
            .contains("target.equals(expectedTarget)")
            .contains("System.currentTimeMillis()");
    }

    private void assertGuardBeforeExecute(String source,
                                          String guardCall,
                                          String executeCall,
                                          String entranceName) {
        int guardIndex = source.indexOf(guardCall);
        int executeIndex = source.indexOf(executeCall);
        assertThat(guardIndex)
            .as(entranceName + " 必须调用 HitlGuard")
            .isGreaterThanOrEqualTo(0);
        assertThat(executeIndex)
            .as(entranceName + " 必须仍然存在真实 execute 调用")
            .isGreaterThanOrEqualTo(0);
        assertThat(guardIndex)
            .as(entranceName + " 的 HitlGuard 必须位于 execute 之前")
            .isLessThan(executeIndex);
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
