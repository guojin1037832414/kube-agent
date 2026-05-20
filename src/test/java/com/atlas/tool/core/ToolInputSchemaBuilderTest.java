package com.atlas.tool.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolInputSchemaBuilder 单元测试。
 *
 * <p>用于锁定 ToolParameterSpec → Spring AI ToolDefinition.inputSchema 的转换契约，
 * 防止后续扩展 Tool Schema 时破坏 LLM 工具调用参数描述。</p>
 */
class ToolInputSchemaBuilderTest {

    @Test
    void build_shouldReturnLooseObjectSchemaWhenSpecsEmpty() {
        String schema = ToolInputSchemaBuilder.build(List.of());

        assertTrue(schema.contains("\"type\":\"object\""));
        assertTrue(schema.contains("\"additionalProperties\":true"));
    }

    @Test
    void build_shouldRenderPropertiesRequiredAndAliases() {
        String schema = ToolInputSchemaBuilder.build(List.of(
            ToolParameterSpec.stringParam("podName", "Pod名称", true, List.of("pod_name", "name")),
            ToolParameterSpec.stringParam("namespace", "命名空间", false, List.of("ns"))
        ));

        assertTrue(schema.contains("\"podName\""));
        assertTrue(schema.contains("\"namespace\""));
        assertTrue(schema.contains("\"required\":[\"podName\"]"));
        assertTrue(schema.contains("aliases: pod_name, name"), "schema 描述中应包含 alias 提示，帮助 LLM 输出 canonical 字段");
        assertTrue(schema.contains("\"additionalProperties\":true"));
    }
}
