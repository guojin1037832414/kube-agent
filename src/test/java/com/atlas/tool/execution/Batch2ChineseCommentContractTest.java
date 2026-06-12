package com.atlas.tool.execution;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Batch 2 中文教学注释契约测试。
 *
 * <p>中文说明：这一批覆盖 Tool 执行器、Tool 注册中心、MCP Manifest 和 kube-manager HTTP 出口。
 * 它们是 Agent 从“会说话”进入“能调用外部能力”的核心边界，因此源码里必须长期保留中文教学注释，
 * 解释哪些输入来自 LLM/Plan/前端、哪些上下文必须由服务端覆盖、哪些能力只是只读清单、哪些能力会触达外部网络。</p>
 *
 * <p>安全边界：本测试不运行 Tool、不访问 kube-manager、不打开 MCP runtime，也不检查注释数量。
 * 它只锁定关键语义 marker，避免后续清理代码时把教学项目最重要的安全解释删掉。</p>
 */
class Batch2ChineseCommentContractTest {

    private static final Map<Path, List<String>> REQUIRED_MARKERS = Map.of(
        Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutor.java"),
        List.of("中文说明", "安全边界", "LLM/Plan/前端", "服务端可信上下文", "durable audit"),
        Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutionRequest.java"),
        List.of("中文说明", "安全边界", "不可信业务输入", "HITL", "traceId"),
        Path.of("src/main/java/com/atlas/tool/core/ToolRegistry.java"),
        List.of("中文说明", "安全边界", "Prompt 可见性", "系统审计视角", "不要把内部 endpoint"),
        Path.of("src/main/java/com/atlas/tool/core/ProtectedToolParameterFilter.java"),
        List.of("中文说明", "安全边界", "控制平面字段", "不能由 LLM", "服务端可信链路"),
        Path.of("src/main/java/com/atlas/mcp/McpToolManifestService.java"),
        List.of("中文说明", "安全边界", "Manifest 不是执行授权", "只读", "不泄露内部 endpoint"),
        Path.of("src/main/java/com/atlas/mcp/McpManifestController.java"),
        List.of("中文说明", "安全边界", "只读查询", "不会执行 Tool", "不会打开 MCP 执行面"),
        Path.of("src/main/java/com/atlas/http/KubeManagerHttpClient.java"),
        List.of("中文说明", "安全边界", "外部网络出口", "真实用户 Token", "禁止透明降级"),
        Path.of("src/main/java/com/atlas/http/KubeManagerHttpResiliencePolicy.java"),
        List.of("中文说明", "安全边界", "读请求可以自动重试", "写请求默认不自动重试", "幂等证据")
    );

    @Test
    void batch2ExecutionBoundaryFiles_shouldKeepChineseTeachingComments() throws Exception {
        for (Map.Entry<Path, List<String>> entry : REQUIRED_MARKERS.entrySet()) {
            String source = Files.readString(entry.getKey());

            assertThat(source)
                .as(entry.getKey() + " should keep Batch 2 Chinese teaching markers")
                .contains(entry.getValue().toArray(String[]::new));
        }
    }
}
