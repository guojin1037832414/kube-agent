package com.atlas.graph.bridge;

import com.atlas.tool.core.BaseTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Map;

/**
 * Atlas BaseTool → Spring AI ToolCallback 桥接器。
 *
 * <p>将自有的 {@link BaseTool}（参数为 Map<String,Object>，返回 Map<String,Object>）
 * 桥接到 Spring AI 的 {@link ToolCallback}（参数为 JSON 字符串，返回 JSON 字符串），
 * 使 {@link com.alibaba.cloud.ai.graph.agent.ReactAgent} 能够直接调用 Atlas Tool 体系。</p>
 *
 * <p><b>权限感知：</b>桥接层内部调用 {@link BaseTool#execute(Map)}，而该方法内部
 * 已包含参数校验和异常兜底，权限校验由 {@link com.atlas.tool.core.ToolRegistry}
 * 在构建 AtlasToolCallback 时通过过滤可见 Tool 来保证。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-P2
 */
public class AtlasToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(AtlasToolCallback.class);

    private final BaseTool baseTool;
    private final ObjectMapper objectMapper;

    public AtlasToolCallback(BaseTool baseTool, ObjectMapper objectMapper) {
        this.baseTool = baseTool;
        this.objectMapper = objectMapper;
    }

    /**
     * 构建 Spring AI {@link ToolDefinition}（name + description + JSON Schema）。
     *
     * <p>由于 BaseTool 当前没有显式声明 JSON Schema，此处仅注册 name + description。
     * Spring AI OpenAI starter 在发送 function definition 时会生成基础 schema（object type）。
     * 如需精细的 parameter schema，可在 BaseTool 中扩展 {@code getParameterSchema()} 方法。</p>
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(baseTool.getToolName())
                .description(baseTool.getDescription())
                .inputSchema("{\"type\":\"object\",\"properties\":{},\"required\":[]}")
                .build();
    }

    /**
     * Spring AI 调用入口：JSON 字符串 → Map → BaseTool.execute → Map → JSON 字符串。
     */
    @Override
    public String call(String toolInput) {
        try {
            log.debug("[AtlasToolCallback] 调用 {}，输入: {}", baseTool.getToolName(), toolInput);

            // 1. JSON → Map
            Map<String, Object> params = parseInput(toolInput);

            // 2. 执行业务 Tool
            Map<String, Object> result = baseTool.execute(params);

            // 3. Map → JSON
            String output = objectMapper.writeValueAsString(result);
            log.debug("[AtlasToolCallback] {} 结果: {}", baseTool.getToolName(), output);
            return output;

        } catch (JsonProcessingException e) {
            log.warn("[AtlasToolCallback] {} 参数解析失败: {}", baseTool.getToolName(), e.getMessage());
            return jsonError("PARAM_PARSE_ERROR", "参数解析失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[AtlasToolCallback] {} 执行异常", baseTool.getToolName(), e);
            return jsonError("TOOL_EXECUTION_ERROR", "工具执行异常: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseInput(String toolInput) throws JsonProcessingException {
        if (toolInput == null || toolInput.isBlank()) {
            return Map.of();
        }
        return objectMapper.readValue(toolInput, new TypeReference<>() {});
    }

    private String jsonError(String code, String message) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "success", false,
                    "error", code,
                    "message", message
            ));
        } catch (JsonProcessingException e) {
            return "{\"success\":false,\"error\":\"SERIALIZATION_ERROR\"}";
        }
    }
}
