package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviewed trace fixture intake 中文教学注释契约测试。
 *
 * <p>中文说明：fixture intake 是学习顶级 Agent eval/release gate 的关键步骤。
 * 这个测试确保服务、响应、Controller 和测试文件都保留中文教学 marker，说明它只是接入规范，
 * 不上传 fixture、不接受 caller traceId、不写 catalog、不运行 eval，也不打开 release/CI 权力。</p>
 *
 * <p>安全边界：本测试只读取源码文本，不启动 Spring，不访问网络，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 audit/memory，也不修改 trace set catalog。</p>
 */
class Batch4ReviewedTraceFixtureIntakeChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentReviewedTraceFixtureIntakeContractService.java"),
            List.of("中文说明", "安全边界", "intake-spec-only / read-only / contract-only",
                "不接受调用方 traceId", "不修改", "不运行 eval/replay")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentReviewedTraceFixtureIntakeContractResponse.java"),
            List.of("中文说明", "安全边界", "只表达合同状态", "不上传 fixture",
                "不接收 caller traceIds", "不授予 release authority")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/ObservabilityController.java"),
            List.of("reviewed redacted trace fixture 接入合同", "不接收上传", "不接收调用方 traceId",
                "不写 `eval-trace-sets.json`", "不打开 CI blocking")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/observability/AgentReviewedTraceFixtureIntakeContractServiceTest.java"),
            List.of("中文说明", "安全边界", "fixture 接入前置规范", "不是运行时上传功能",
                "不修改 `eval-trace-sets.json`")
        )
    );

    @Test
    void fixtureIntakeContractFiles_shouldKeepChineseTeachingMarkers() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep fixture intake teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
