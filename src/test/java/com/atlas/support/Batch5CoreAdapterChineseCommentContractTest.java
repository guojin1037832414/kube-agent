package com.atlas.support;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 5 Tool core adapter 中文教学注释契约测试。
 *
 * <p>中文说明：本测试覆盖 AtlasTool、legacy ToolCallback、ToolContext、inputSchema、
 * 默认值切面和结果转换这些“看起来像胶水代码”的支撑层。它们连接 LLM、Spring AI、Graph、
 * SafeToolExecutor 和前端展示，如果缺少中文教学注释，学习者很容易把 schema、context、
 * result 或 defaults 误解成权限事实。</p>
 *
 * <p>安全边界：本测试只读取源码 marker，不启动 Spring、不执行 Tool、不访问 kube-manager、
 * 不调用 LLM/MCP/RAG，也不写 audit 或 memory。它只锁定教学说明：这些 adapter 只能做协议转换、
 * 展示投影或普通业务参数补齐，不能绕过 SafeToolExecutor，也不能打开二期 NIM/HPC/Slurm/BCM
 * 运行时权力。</p>
 */
class Batch5CoreAdapterChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.ofEntries(
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/core/AtlasTool.java"),
            List.of("中文说明", "安全边界", "最小的 Tool 形状", "不是执行授权",
                "不可信候选业务输入", "SafeToolExecutor")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/core/AtlasToolCallback.java"),
            List.of("中文说明", "安全边界", "legacy core 路径", "LLM 传进来的 JSON 永远是不可信候选业务输入",
                "不能直接调用", "audit prewrite")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/core/ToolInputSchemaBuilder.java"),
            List.of("中文说明", "安全边界", "inputSchema", "不是权限系统",
                "additionalProperties=true", "ProtectedToolParameterFilter")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/core/AtlasToolResultConverter.java"),
            List.of("中文说明", "安全边界", "最后一层转换器", "不是新的安全闸门",
                "不能把失败结果改成成功", "redacted-only")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/core/AtlasToolResult.java"),
            List.of("中文说明", "安全边界", "统一数据容器", "success=true",
                "不反向证明 HITL", "不能包含 token")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/core/AtlasToolContext.java"),
            List.of("中文说明", "安全边界", "早期 Spring AI", "上下文对象",
                "不能从前端", "不能写审计、记忆或 RAG")
        ),
        Map.entry(
            Path.of("src/main/java/com/atlas/tool/core/DefaultValueAspect.java"),
            List.of("中文说明", "安全边界", "表单草稿字段", "不是权限系统",
                "不能生成、覆盖或信任", "SafeToolExecutor")
        )
    );

    @Test
    void coreAdapterSupportFiles_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey(), StandardCharsets.UTF_8);

            assertThat(source)
                .as(entry.getKey() + " should keep Batch 5 core adapter teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
