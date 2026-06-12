package com.atlas.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 5 意图路由链路中文教学注释契约测试。
 *
 * <p>中文说明：本测试保护 IntentRouter、IntentArbiter、RuleMatcher、EmbeddingMatcher、
 * IntentsLoader 和 L3IntentClassifier 这些“把自然语言变成候选 intent”的支撑代码。
 * 它们非常关键，但又很容易被误解为“命中 intent 就可以执行 Tool”。</p>
 *
 * <p>安全边界：本测试只读取源码 marker，不启动 Spring、不调用 LLM/Embedding、不访问
 * kube-manager、不执行 Tool、不调用 MCP、不写 audit/memory，也不打开 Phase 2 域能力。
 * 它只保护学习说明：意图命中、短路、分数、crossBoost、prompt 快照和目录加载都只是路由证据，
 * 不能绕过 SafeToolExecutor、HITL、audit、release 或 kube-manager 权限。</p>
 */
class Batch5IntentRoutingChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/IntentRouter.java"),
            List.of("中文说明", "安全边界", "候选意图收集器", "路由建议",
                "不是执行许可", "返回 unknown")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/core/IntentArbiter.java"),
            List.of("中文说明", "安全边界", "路由候选胜出", "不是 Tool 授权",
                "crossBoost", "release evidence")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/rule/RuleMatcher.java"),
            List.of("中文说明", "安全边界", "关键词或正则命中不是权限证据",
                "不能创建 HITL marker", "不能决定 orgId/token/userId")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/EmbeddingMatcher.java"),
            List.of("中文说明", "安全边界", "语义相近意图", "Embedding 相似度不是安全门禁",
                "不写 audit/memory", "返回 null")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/config/IntentsLoader.java"),
            List.of("中文说明", "安全边界", "路由目录", "不是 Tool 权限表",
                "不是 MCP manifest", "不能注册新 Tool")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/intent/llm/L3IntentClassifier.java"),
            List.of("中文说明", "安全边界", "语义兜底增强层", "LLM 输出不可信",
                "不能跳过 SafeToolExecutor", "不能动态注册能力")
        )
    );

    @Test
    void intentRoutingSupportFiles_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep Batch 5 intent-routing teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
