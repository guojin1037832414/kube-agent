package com.atlas.graph;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 3 中文教学注释契约测试。
 *
 * <p>中文说明：本批覆盖 Orchestrator / StateGraph / ReAct / Plan 编排链路。
 * 这些文件决定一次用户请求如何从 SSE 入口进入 AtlasBrain，再经条件边进入 Tool、ReAct、
 * Plan、delegate 子图或 HITL 等路径，是学习 kube-agent 顶级 Agent Core 的主干。</p>
 *
 * <p>安全边界：本测试只读取源码，不启动 Spring、不调用 LLM、不执行 Tool、不访问 kube-manager，
 * 也不创建 HITL 或 audit 记录。它只锁定关键中文教学 marker，确保后续重构不会删掉
 * “状态机、SSE、Tool 调度、fail-closed、异步上下文传播”这些学习解释。</p>
 */
class Batch3ChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/orchestrator/AtlasOrchestrator.java"),
            List.of("中文说明", "安全边界", "Supervisor Graph", "SSE", "RuntimeIdentity",
                "HITL marker", "Graph State", "SafeToolExecutor")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/graph/config/AtlasGraphConfig.java"),
            List.of("中文说明", "安全边界", "StateGraph", "BrainDecision.ActionType",
                "条件边", "execute_node", "双层防线", "ThreadLocal")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/react/ReActEngine.java"),
            List.of("中文说明", "安全边界", "Thought → Action → Observation",
                "Action JSON", "候选业务参数", "SafeToolExecutor", "展示脱敏")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/plan/PlanEngine.java"),
            List.of("中文说明", "安全边界", "计划证据", "不是执行许可", "不调用 Tool",
                "不访问 kube-manager", "HITL marker")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/graph/bridge/AtlasToolCallback.java"),
            List.of("中文说明", "安全边界", "Spring AI ReactAgent", "ToolCallback",
                "不直接调用 BaseTool.execute", "SafeToolExecutor")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/react/ReActPromptBuilder.java"),
            List.of("中文说明", "安全边界", "可见工具列表", "提示词中的规则只是模型行为引导")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/react/ReActMemory.java"),
            List.of("中文说明", "安全边界", "短期记忆", "不是长期 Memory/RAG", "重复动作检测")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/graph/node/SseEmitNode.java"),
            List.of("中文说明", "安全边界", "SSE", "不代表 Tool 执行")
        )
    );

    @Test
    void batch3OrchestrationFiles_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey());

            assertThat(source)
                .as(entry.getKey() + " should keep Batch 3 Chinese teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
