package com.atlas.tool.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.21-85 受保护 Tool 参数过滤共享使用契约。
 *
 * <p>中文说明：本测试通过读取源码，确认 ReActEngine、SafeToolExecutor 和 AtlasGraphConfig
 * 都引用同一个 {@link ProtectedToolParameterFilter}。这是为了防止不同入口复制不同黑名单，
 * 导致某个路径漏掉 token/orgId/HITL/audit/release/write 控制字段。</p>
 *
 * <p>安全边界：本测试只读源码，不启动 Spring、不执行 Tool、不调用 LLM/MCP/kube-manager、
 * 不写 audit/memory。源码契约不是运行时授权，它只是保护“统一过滤器必须被所有执行入口复用”的
 * 架构不变量。</p>
 *
 * <p>受保护字段名单必须集中在 {@link ProtectedToolParameterFilter}，执行入口只引用共享组件。
 * 这条源码契约用于防止 ReAct、SafeToolExecutor 后续重新复制私有黑名单。</p>
 */
class ProtectedToolParameterFilterUsageContractTest {

    private static final Path REACT_ENGINE = Path.of("src/main/java/com/atlas/react/ReActEngine.java");
    private static final Path SAFE_TOOL_EXECUTOR = Path.of("src/main/java/com/atlas/tool/execution/SafeToolExecutor.java");
    private static final Path ATLAS_GRAPH_CONFIG = Path.of("src/main/java/com/atlas/graph/config/AtlasGraphConfig.java");

    @Test
    void reactAndSafeToolExecutor_shouldUseSharedProtectedToolParameterFilterOnly() throws IOException {
        String react = read(REACT_ENGINE);
        String safeExecutor = read(SAFE_TOOL_EXECUTOR);

        assertThat(react)
            .contains("ProtectedToolParameterFilter.isProtected")
            .doesNotContain("PROTECTED_CONTEXT_PARAMS")
            .doesNotContain("PROTECTED_CONTEXT_PARAM_NORMALIZED_KEYS")
            .doesNotContain("normalizeProtectedParamKey");
        assertThat(safeExecutor)
            .contains("ProtectedToolParameterFilter.isProtected")
            .doesNotContain("PROTECTED_CONTEXT_PARAMS")
            .doesNotContain("isProtectedContextParam(");
    }

    @Test
    void executeNode_shouldFailClosedUsingSameProtectedFilterBeforeSafeToolExecutor() throws IOException {
        String graphConfig = read(ATLAS_GRAPH_CONFIG);

        assertThat(graphConfig)
            .contains("ProtectedToolParameterFilter.isProtected")
            .contains("PROTECTED_PLAN_PARAMETER")
            .contains("Plan 参数属于不可信输入")
            .doesNotContain("isProtectedContextKey(")
            .doesNotContain("PROTECTED_CONTEXT_PARAMS");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
