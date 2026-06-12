package com.atlas.tool.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolInputSchemaBuilder 单元测试。
 *
 * <p>中文说明：本测试保护 {@link ToolParameterSpec} 到 Spring AI inputSchema 的转换形状。
 * inputSchema 会影响 LLM 生成 Action.params 的字段，但它只是提示/约束材料，不是权限系统。</p>
 *
 * <p>安全边界：测试不启动 Spring、不调用 LLM、不执行 Tool、不访问 kube-manager、
 * 不写 audit/memory。`additionalProperties=true` 只服务历史兼容，不代表 token/orgId/userId、
 * HITL、audit、release 或 writeAllowed 等控制平面字段可以被透传。</p>
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
