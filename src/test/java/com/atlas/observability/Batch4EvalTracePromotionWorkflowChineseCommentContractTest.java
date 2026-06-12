package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eval trace promotion workflow 中文教学注释契约测试。
 *
 * <p>中文说明：本测试专门保护 promotion workflow / workbench wrapper 这条链路里的中文教学注释，
 * 让读代码的人始终能看见“只读工作流、Git review、前端 read model、不是目录写权限”这些关键边界。</p>
 *
 * <p>安全边界：本测试只检查源码 marker，不启动 Spring，不运行 eval，不调用 Tool/MCP/LLM/RAG，
 * 不访问 kube-manager，不写 audit/memory，也不修改 trace set catalog。</p>
 */
class Batch4EvalTracePromotionWorkflowChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetPromotionWorkflowService.java"),
            List.of("中文说明", "安全边界", "read-only / workflow-only / proposal-only",
                "候选发现 -> 人工审阅 -> 补丁建议", "不修改", "selectedRecommendedTraceIds")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetPromotionWorkflowArtifact.java"),
            List.of("中文说明", "安全边界", "只生成 read model", "readyForGitReview", "不是 release authority")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetPromotionWorkflowRequest.java"),
            List.of("中文说明", "安全边界", "只影响 review artifact", "不授予 catalog write authority", "Tool")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalWorkbenchPromotionWorkflowService.java"),
            List.of("中文说明", "安全边界", "wrapper 层", "human Git review", "不修改目录")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalWorkbenchPromotionWorkflowResponse.java"),
            List.of("中文说明", "安全边界", "uiSteps", "patchSummary", "nextActions", "read model")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/observability/AgentEvalTraceSetPromotionWorkflowServiceTest.java"),
            List.of("中文说明", "安全边界", "只读工作流", "不是 runtime catalog write", "fail-closed")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/observability/AgentEvalWorkbenchPromotionWorkflowServiceTest.java"),
            List.of("中文说明", "安全边界", "wrapper/read model", "目录写权限", "不授予 release authority")
        )
    );

    @Test
    void promotionWorkflowFiles_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep promotion workflow teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
