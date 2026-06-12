package com.atlas.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 5 Tool core 测试中文教学注释契约。
 *
 * <p>中文说明：本测试保护 Tool core 相关测试文件本身的学习说明。项目是教学项目，
 * 所以测试不仅要证明代码行为，还要解释测试为什么存在、输入来自哪里、输出保护什么契约、
 * 以及测试不会触发哪些真实能力。</p>
 *
 * <p>安全边界：本测试只读取测试源码 marker，不启动 Spring、不执行 Tool、不调用 LLM/MCP、
 * 不访问 kube-manager、不写 audit/memory，也不打开 Phase 2 NIM/HPC/Slurm/BCM 权力。
 * 它保护测试注释，而不是生产运行时行为。</p>
 */
class Batch5ToolCoreTestChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/test/java/com/atlas/tool/core/ToolRegistryPromptContractTest.java"),
            List.of("中文说明", "安全边界", "Prompt 可见性契约", "不调用 LLM",
                "不访问 kube-manager", "LLM 只能看到经过权限过滤和脱敏的工具目录")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/tool/core/ToolRegistryPermissionTest.java"),
            List.of("中文说明", "安全边界", "可见工具目录", "不执行 Tool",
                "真实 Tool 调用仍必须经过 SafeToolExecutor")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/tool/core/ToolParameterNormalizerTest.java"),
            List.of("中文说明", "安全边界", "canonical 参数名", "只在内存中构造 ToolRegistry",
                "受保护字段仍必须由 ProtectedToolParameterFilter")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/tool/core/ToolInputSchemaBuilderTest.java"),
            List.of("中文说明", "安全边界", "inputSchema", "不是权限系统",
                "additionalProperties=true")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/tool/core/ProtectedToolParameterFilterTest.java"),
            List.of("中文说明", "安全边界", "共享黑名单", "只调用纯函数",
                "不替代 SafeToolExecutor")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/tool/core/ProtectedToolParameterFilterUsageContractTest.java"),
            List.of("中文说明", "安全边界", "只读源码", "统一过滤器",
                "架构不变量")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/tool/core/BaseToolOrganizationIdGovernanceTest.java"),
            List.of("中文说明", "安全边界", "服务端可信", "缺少可信 orgId 必须 fail-closed",
                "不触发真实 HTTP")
        ),
        Map.entry(
            Path.of("src/test/java/com/atlas/tool/core/AtlasToolCallbackSafeExecutorTest.java"),
            List.of("中文说明", "安全边界", "legacy callback", "委托 SafeToolExecutor",
                "不能绕过统一执行边界")
        )
    );

    @Test
    void toolCoreTests_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep Tool core test teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
