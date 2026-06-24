package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviewed trace fixture template 中文教学注释契约测试。
 *
 * <p>中文说明：fixture template 是把 manifest 缺口推进成真实文件准备工作的功能面。
 * 本测试确保源码和 README 写清楚：模板只指导人工编写 reviewed fixture，不能提交占位 traceId，
 * 不能写 catalog，也不能打开运行时上传、eval/replay、CI 或 release 权力。</p>
 *
 * <p>安全边界：本测试只读取源码和 README 文本，不启动 Spring，不访问网络，不调用
 * Tool/MCP/LLM/RAG/kube-manager，不创建 fixture JSON，不写 audit/memory。</p>
 */
class Batch4ReviewedTraceFixtureTemplateChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentReviewedTraceFixtureTemplateService.java"),
            List.of("中文说明", "安全边界", "template-only / read-only / schema-only",
                "不创建 fixture 文件", "不写", "不接收 caller traceId", "不运行 eval/replay")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/AgentReviewedTraceFixtureTemplateResponse.java"),
            List.of("中文说明", "安全边界", "repo-native authoring guide",
                "不创建文件", "不写 catalog", "不授予 CI blocking")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/observability/ObservabilityController.java"),
            List.of("reviewed redacted trace fixture 作者模板和 schema", "不创建 fixture 文件",
                "不接收上传", "不接收调用方 traceId", "不写 `eval-trace-sets.json`")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/observability/AgentReviewedTraceFixtureTemplateServiceTest.java"),
            List.of("中文说明", "安全边界", "不能制造 fixture", "不能提交占位 traceId",
                "不创建真实 reviewed fixture JSON")
        ),
        Map.entry(
            Path.of("src/main/resources/observability/reviewed-trace-fixtures/README.md"),
            List.of("不要把模板或占位 JSON 提交到本目录", "不要提交 fake traceId",
                "不授予 CI blocking", "reviewed-trace-fixture-template")
        )
    );

    @Test
    void fixtureTemplateFiles_shouldKeepChineseTeachingMarkers() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep fixture template teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
