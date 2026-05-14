package com.atlas.tool.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.Map;

/**
 * Atlas 专用 ToolCallback — 将 {@link BaseTool} 包装为 Spring AI 可识别的 ToolCallback。
 *
 * <p>这是 Atlas 的 <b>核心桥接类</b>，作用：</p>
 * <ol>
 *   <li>将 Atlas 的 {@code Map<String,Object> → Map<String,Object>} 统一接口
 *       适配到 Spring AI 的 {@code String call(String)} 契约</li>
 *   <li>JSON 反序列化：LLM 传入的 JSON 参数 → {@code Map<String,Object>}</li>
 *   <li>JSON 序列化：{@code AtlasToolResult(Map)} → JSON 字符串返回给 LLM</li>
 *   <li>动态生成 {@link ToolDefinition}（name / description / inputSchema）</li>
 * </ol>
 */
public class AtlasToolCallback implements ToolCallback {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE =
        new TypeReference<>() {};
    private static final ToolCallResultConverter DEFAULT_CONVERTER =
        new DefaultToolCallResultConverter();

    private final BaseTool tool;
    private final ToolDefinition toolDefinition;
    private final ToolMetadata toolMetadata;

    public AtlasToolCallback(BaseTool tool) {
        this.tool = tool;
        this.toolDefinition = buildToolDefinition(tool);
        this.toolMetadata = ToolMetadata.builder().build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return toolMetadata;
    }

    @Override
    public String call(String toolInput) {
        try {
            // 1. JSON → Map（LLM 传的参数）
            Map<String, Object> params = parseInput(toolInput);
            // 2. 调用 Atlas Tool
            Map<String, Object> result = tool.execute(params);
            // 3. Map → JSON（返回给 LLM）
            return DEFAULT_CONVERTER.convert(result, Map.class);
        } catch (Exception e) {
            // 任何序列化/调用异常都返回结构化错误，不抛出让 LLM 断线
            return fallbackError(e);
        }
    }

    // ═══════════════════════════════════════════
    // 内部辅助
    // ═══════════════════════════════════════════

    private Map<String, Object> parseInput(String toolInput) {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(toolInput, MAP_TYPE);
        } catch (Exception e) {
            // LLM 偶尔传的不是 JSON 对象，尝试包一层
            return Map.of("raw", toolInput);
        }
    }

    private String fallbackError(Exception e) {
        try {
            return OBJECT_MAPPER.writeValueAsString(
                AtlasToolResult.fail(
                    "参数解析失败: " + e.getMessage(),
                    "PARSE_ERROR",
                    java.util.List.of("请确认参数为有效的 JSON 对象")
                )
            );
        } catch (Exception ex) {
            return "{\"success\":false,\"message\":\"内部错误\"}";
        }
    }

    /**
     * 构建 ToolDefinition。inputSchema 对所有 Atlas Tool 统一为 "接受任意 JSON 对象的 Map"。
     *
     * <p>如果子类需要更精确的 Schema（让 LLM 知道具体参数），可扩展此方法
     * 读取子类的 {@code getRequiredParams()} / {@code getParamTypes()} 生成 JSON Schema。</p>
     */
    private static ToolDefinition buildToolDefinition(BaseTool tool) {
        // 基础 schema：接受任意 key-value 对象（Atlas 参数是动态的 Map）
        // 后续 P2 可以基于子类 getRequiredParams 生成 precise schema
        String schema = """
            {
              "type": "object",
              "properties": {},
              "additionalProperties": true,
              "description": "Atlas 工具参数映射（具体字段见工具说明）"
            }
            """;

        return DefaultToolDefinition.builder()
            .name(tool.getToolName())
            .description(tool.getDescription())
            .inputSchema(schema)
            .build();
    }
}
