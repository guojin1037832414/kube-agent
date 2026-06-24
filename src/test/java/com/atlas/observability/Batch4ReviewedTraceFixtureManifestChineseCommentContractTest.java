package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviewed trace fixture manifest 中文教学注释契约测试。
 *
 * <p>中文说明：fixture manifest 是 fixture intake 合同之后的 repo-native 证据层。
 * 这个测试确保新服务、响应、Controller 和测试文件都写清楚：manifest 只扫描已经随 Git 提交的
 * fixture 文件，不提供上传入口，不接受调用方 traceId，也不写 trace-set catalog。</p>
 *
 * <p>安全边界：本测试只读取源码文本，不启动 Spring，不访问网络，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不写 audit/memory，也不修改 fixture 或 catalog 文件。</p>
 */
class Batch4ReviewedTraceFixtureManifestChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentReviewedTraceFixtureManifestService.java"),
            List.of("中文说明", "安全边界", "manifest-only / read-only / classpath-scan-only",
                "不上传 fixture", "不接收 caller", "不修改")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentReviewedTraceFixtureManifestResponse.java"),
            List.of("中文说明", "安全边界", "repo-native / classpath-native read model",
                "不写", "不运行 eval/replay", "不启用 CI blocking")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/ObservabilityController.java"),
            List.of("reviewed redacted trace fixture 仓库 manifest", "不接收上传",
                "不接收调用方 traceId", "不写 `eval-trace-sets.json`", "不打开 CI blocking")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/observability/AgentReviewedTraceFixtureManifestServiceTest.java"),
            List.of("中文说明", "安全边界", "repo 内可审查文件", "不把 traceId 写回 catalog",
                "不修改 `eval-trace-sets.json`")
        )
    );

    @Test
    void fixtureManifestFiles_shouldKeepChineseTeachingMarkers() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep fixture manifest teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
