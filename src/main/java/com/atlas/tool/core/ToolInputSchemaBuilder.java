package com.atlas.tool.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 入参 JSON Schema 构建器。
 *
 * <p>中文说明：该构建器把 {@link ToolParameterSpec} 这种 Java 侧参数契约，转换成
 * Spring AI {@code ToolDefinition.inputSchema} 可消费的 JSON Schema。它的输出主要给
 * LLM 看，用来降低模型写错字段名、漏掉必填业务参数或混淆别名的概率。</p>
 *
 * <p>安全边界：inputSchema 只是提示词/函数调用 schema，不是权限系统、不是参数校验器，
 * 也不是 kube-manager API 白名单。即使 schema 中出现 required 或 aliases，真实执行仍必须
 * 经过 {@link com.atlas.tool.execution.SafeToolExecutor}、{@link ProtectedToolParameterFilter}
 * 和具体 Tool 的业务校验；{@code additionalProperties=true} 只用于兼容历史参数，不代表
 * {@code token/orgId/userId/HITL/audit/release} 等控制平面字段可以被透传。</p>
 *
 * <p>将 {@link ToolParameterSpec} 列表转换为 Spring AI {@code ToolDefinition.inputSchema}
 * 所需的 JSON 字符串。第一阶段保持兼容优先：即使声明了 properties，也保留
 * {@code additionalProperties=true}，避免历史额外参数或上下文参数被 LLM/框架拒绝。</p>
 */
public final class ToolInputSchemaBuilder {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ToolInputSchemaBuilder() {
    }

    /**
     * 构建 inputSchema。
     *
     * <p>中文说明：返回值会进入 ToolDefinition，影响 LLM 生成 Action.params 的形状。
     * 因此这里保持 deterministic 的字段顺序，便于测试、review 和学习者对照 prompt。</p>
     *
     * <p>安全边界：构建失败时回退到“任意对象 schema”只是为了保持对话链可用；
     * 不能因为 schema 宽松就跳过执行层的受保护字段过滤、必填校验或权限判断。</p>
     *
     * @param specs Tool 参数声明；为空时返回兼容任意对象的基础 schema
     * @return 单行 JSON schema 字符串
     */
    public static String build(List<ToolParameterSpec> specs) {
        try {
            Map<String, Object> schema = new LinkedHashMap<>();
            schema.put("type", "object");

            Map<String, Object> properties = new LinkedHashMap<>();
            List<String> required = specs == null
                ? List.of()
                : specs.stream()
                    .filter(ToolParameterSpec::required)
                    .map(ToolParameterSpec::name)
                    .toList();

            if (specs != null) {
                for (ToolParameterSpec spec : specs) {
                    Map<String, Object> property = new LinkedHashMap<>();
                    property.put("type", spec.type());
                    property.put("description", buildDescription(spec));
                    properties.put(spec.name(), property);
                }
            }

            schema.put("properties", properties);
            schema.put("required", required);
            schema.put("additionalProperties", true);
            return OBJECT_MAPPER.writeValueAsString(schema);
        } catch (Exception e) {
            return "{\"type\":\"object\",\"properties\":{},\"required\":[],\"additionalProperties\":true}";
        }
    }

    /**
     * 构建给 LLM 看的参数描述。
     *
     * <p>aliases 只写入描述，不作为 JSON Schema properties 暴露，目的是引导 LLM 优先生成
     * canonical 参数，同时让模型知道历史别名也可被系统兼容。</p>
     *
     * <p>安全边界：aliases 只能描述普通业务字段的兼容名称，不能新增控制平面字段别名；
     * 如果未来 ToolParameterSpec 中误写了 {@code token/orgId/userId} 等名称，也必须由
     * ProtectedToolParameterFilter 和 SafeToolExecutor 在执行前 fail-closed。</p>
     */
    private static String buildDescription(ToolParameterSpec spec) {
        String description = spec.description() == null ? "" : spec.description();
        if (spec.aliases() != null && !spec.aliases().isEmpty()) {
            description += "；aliases: " + String.join(", ", spec.aliases());
        }
        return description;
    }
}
