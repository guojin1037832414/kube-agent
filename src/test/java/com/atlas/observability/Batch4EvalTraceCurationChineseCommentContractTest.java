package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eval trace evidence / curation 链路中文教学注释契约测试。
 *
 * <p>中文说明：本测试保护 Eval trace catalog、candidate discovery、curation review 和 patch proposal
 * 这些“发布门禁证据链”代码里的中文说明。它们是顶级 Agent 的学习重点：如何把一次运行轨迹变成
 * reviewed redacted evidence，而不是把运行时接口误用成自动发布开关。</p>
 *
 * <p>安全边界：本测试只读取源码 marker，不启动 Spring、不运行 eval、不调用 Tool/MCP/LLM/RAG、
 * 不访问 kube-manager、不写 audit/memory、不写 catalog，也不打开 retrieval/vector/CI blocking 或
 * Phase 2 NIM/HPC/Slurm/BCM 权力。</p>
 */
class Batch4EvalTraceCurationChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetCatalogService.java"),
            List.of("中文说明", "安全边界", "review-only", "不直接写 classpath catalog",
                "不执行 eval runtime", "traceIds 是 redacted replay", "evidence anchor")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetDefinition.java"),
            List.of("中文说明", "安全边界", "可版本化的 Eval trace set",
                "Tool 参数", "release authority")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetCandidateDiscoveryService.java"),
            List.of("中文说明", "安全边界", "redacted recentEvents", "recommendation-only",
                "不写 catalog", "人工 Git review")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetCandidate.java"),
            List.of("中文说明", "安全边界", "前端展示 DTO",
                "只是“建议审阅”", "不是目录提升")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetCandidateDiscoveryResponse.java"),
            List.of("中文说明", "安全边界", "admin-only read model",
                "candidateTraceIds 只是下一步", "不是已发布 trace set")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetCurationReviewArtifact.java"),
            List.of("中文说明", "安全边界", "审阅产物",
                "不修改 {@code eval-trace-sets.json}", "版本化 evidence anchor")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentEvalTraceSetCatalogPatchProposalArtifact.java"),
            List.of("中文说明", "安全边界", "RFC6902 JSON Patch",
                "只生成 artifact", "人工 Git review", "不等于 release authority")
        )
    );

    @Test
    void evalTraceCurationFiles_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep Eval trace curation teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
