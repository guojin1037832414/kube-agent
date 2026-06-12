package com.atlas.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 5 支撑测试中文教学注释契约。
 *
 * <p>中文说明：本测试保护 config/store/intent 相关测试文件本身的教学价值。当前项目既是
 * 生产级 Agent，也是学习项目；测试文件不能只留下断言，还要解释断言保护的身份、会话、
 * 路由和降级安全边界。</p>
 *
 * <p>安全边界：本测试只读取测试源码 marker，不启动 Spring、不执行 Tool、不调用 LLM/MCP/RAG、
 * 不访问 kube-manager、不写 audit/memory，也不打开 Phase 2 NIM/HPC/Slurm/BCM 能力。
 * 它保护注释和学习说明，不改变生产运行时行为。</p>
 */
class Batch5SupportTestChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/test/java/com/atlas/config/TokenPropagatingTaskDecoratorTest.java"),
            List.of("中文说明", "安全边界", "异步线程", "服务端可信上下文",
                "任务结束后必须恢复旧 ThreadLocal", "不访问 kube-manager")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/store/ConversationStoreTest.java"),
            List.of("中文说明", "安全边界", "conversationId 不能作为授权凭证",
                "owner", "不产生长期记忆")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/intent/core/IntentArbiterTest.java"),
            List.of("中文说明", "安全边界", "候选路由证据", "crossBoost",
                "不是 Tool 授权", "SafeToolExecutor")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/intent/EmbeddingMatcherMockTest.java"),
            List.of("中文说明", "安全边界", "返回 null", "不加载真实向量模型",
                "Embedding 相似度只是意图候选证据")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/intent/rule/RuleMatcherTest.java"),
            List.of("中文说明", "安全边界", "关键词", "正则", "不是 Tool 授权",
                "HITL 确认")
        )
    );

    @Test
    void supportTests_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep Batch 5 support-test teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
