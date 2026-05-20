package com.atlas.graph.bridge;

import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolInputSchemaBuilder;
import com.atlas.tool.core.ToolParameterNormalizer;
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
 * <p><b>参数契约：</b>桥接层会根据 {@link BaseTool#getParameterSpecs()} 生成
 * ToolDefinition.inputSchema，让 LLM 优先输出 canonical 参数名；同时调用
 * {@link ToolParameterNormalizer} 兼容 LLM 偶尔输出的 alias 参数。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-P2
 */
public class AtlasToolCallback implements ToolCallback {

    private static final Logger log = LoggerFactory.getLogger(AtlasToolCallback.class);

    private final BaseTool baseTool;
    private final ObjectMapper objectMapper;
    private final ToolParameterNormalizer parameterNormalizer;

    public AtlasToolCallback(BaseTool baseTool,
                             ObjectMapper objectMapper,
                             ToolParameterNormalizer parameterNormalizer) {
        this.baseTool = baseTool;
        this.objectMapper = objectMapper;
        this.parameterNormalizer = parameterNormalizer != null ? parameterNormalizer : new ToolParameterNormalizer();
    }

    /**
     * 构建 Spring AI {@link ToolDefinition}（name + description + JSON Schema）。
     *
     * <p>如果 Tool 声明了 {@link com.atlas.tool.core.ToolParameterSpec}，则生成精确 properties；
     * 如果未声明，仍返回兼容任意 JSON 对象的 schema，保证旧 Tool 不受影响。</p>
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
                .name(baseTool.getToolName())
                .description(baseTool.getDescription())
                .inputSchema(ToolInputSchemaBuilder.build(baseTool.getParameterSpecs()))
                .build();
    }

    /**
     * Spring AI 调用入口：JSON 字符串 → Map → 参数归一化 → BaseTool.execute → Map → JSON 字符串。
     */
    @Override
    public String call(String toolInput) {
        try {
            log.debug("[AtlasToolCallback] 调用 {}，输入: {}", baseTool.getToolName(), toolInput);

            // 1. JSON → Map
            Map<String, Object> params = parseInput(toolInput);

            // 2. 统一参数归一化：只补齐 canonical 字段，不覆盖、不删除原始字段。
            Map<String, Object> normalizedParams = parameterNormalizer.normalize(baseTool.getToolName(), params);

            // 3. 执行业务 Tool
            Map<String, Object> result = baseTool.execute(normalizedParams);

            // 4. Map → JSON
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
