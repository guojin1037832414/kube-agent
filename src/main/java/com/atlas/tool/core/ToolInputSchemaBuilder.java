package com.atlas.tool.core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 入参 JSON Schema 构建器。
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
     */
    private static String buildDescription(ToolParameterSpec spec) {
        String description = spec.description() == null ? "" : spec.description();
        if (spec.aliases() != null && !spec.aliases().isEmpty()) {
            description += "；aliases: " + String.join(", ", spec.aliases());
        }
        return description;
    }
}
