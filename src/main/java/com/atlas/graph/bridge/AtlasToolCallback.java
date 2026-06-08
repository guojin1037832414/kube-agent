package com.atlas.graph.bridge;

import com.atlas.auth.UserPermissionContext;
import com.atlas.observability.AgentTraceContext;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolInputSchemaBuilder;
import com.atlas.tool.core.ToolParameterNormalizer;
import com.atlas.tool.execution.SafeToolExecutionRequest;
import com.atlas.tool.execution.SafeToolExecutionResult;
import com.atlas.tool.execution.SafeToolExecutionSource;
import com.atlas.tool.execution.SafeToolExecutor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Atlas BaseTool → Spring AI ToolCallback 桥接器。
 *
 * <p>将自有的 {@link BaseTool}（参数为 Map<String,Object>，返回 Map<String,Object>）
 * 桥接到 Spring AI 的 {@link ToolCallback}（参数为 JSON 字符串，返回 JSON 字符串），
 * 使 {@link com.alibaba.cloud.ai.graph.agent.ReactAgent} 能够直接调用 Atlas Tool 体系。</p>
 *
 * <p><b>执行边界：</b>桥接层只负责 JSON 解析、参数 alias 归一化和结果序列化；
 * 真正的 Tool 调用必须委托 {@link SafeToolExecutor}。这样 Spring AI / ReactAgent
 * 子图路径与主 Graph {@code tool_call}、Plan {@code execute_node} 共享同一套权限、
 * HITL、租户上下文、受保护参数过滤和 ThreadLocal 恢复语义。</p>
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
    private final SafeToolExecutor safeToolExecutor;
    private final UserPermissionContext userPermissionContext;
    private final com.atlas.tool.core.ToolRegistry.ToolMetadata atlasMetadata;

    public AtlasToolCallback(BaseTool baseTool,
                             ObjectMapper objectMapper,
                             ToolParameterNormalizer parameterNormalizer,
                             SafeToolExecutor safeToolExecutor,
                             UserPermissionContext userPermissionContext) {
        this(baseTool, objectMapper, parameterNormalizer, safeToolExecutor, userPermissionContext, null);
    }

    public AtlasToolCallback(BaseTool baseTool,
                             ObjectMapper objectMapper,
                             ToolParameterNormalizer parameterNormalizer,
                             SafeToolExecutor safeToolExecutor,
                             UserPermissionContext userPermissionContext,
                             com.atlas.tool.core.ToolRegistry.ToolMetadata atlasMetadata) {
        this.baseTool = Objects.requireNonNull(baseTool, "baseTool cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
        this.parameterNormalizer = parameterNormalizer != null ? parameterNormalizer : new ToolParameterNormalizer();
        this.safeToolExecutor = Objects.requireNonNull(safeToolExecutor, "safeToolExecutor cannot be null");
        this.userPermissionContext = Objects.requireNonNull(userPermissionContext, "userPermissionContext cannot be null");
        this.atlasMetadata = atlasMetadata;
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
     * Spring AI 调用入口：JSON 字符串 → Map → 参数归一化 → SafeToolExecutor → JSON 字符串。
     */
    @Override
    public String call(String toolInput) {
        try {
            log.debug("[AtlasToolCallback] 调用 {}，输入: {}", baseTool.getToolName(), toolInput);

            // 1. JSON → Map
            Map<String, Object> params = parseInput(toolInput);

            // 2. 统一参数归一化：只补齐 canonical 字段，不覆盖、不删除原始字段。
            Map<String, Object> normalizedParams = parameterNormalizer.normalize(baseTool.getToolName(), params);

            // 3. 委托统一安全执行器。ToolCallback 只能读取 delegate 节点提前绑定的 ThreadLocal，
            // 不能相信 LLM JSON 里的 token/orgId/userId/HITL/审计/发布/写入控制字段。
            SafeToolExecutionRequest request = new SafeToolExecutionRequest(
                resolveIntentId(),
                normalizedParams,
                resolveTrustedUserId(),
                UserPermissionContext.CURRENT_TOKEN.get(),
                UserPermissionContext.getCurrentOrgId(),
                "",
                AgentTraceContext.currentOrNew(""),
                null,
                SafeToolExecutionSource.TOOL_CALLBACK
            );
            SafeToolExecutionResult result = safeToolExecutor.executeIntent(request);

            // 4. Map → JSON
            String output = objectMapper.writeValueAsString(toCallbackPayload(result));
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

    private String resolveIntentId() {
        if (atlasMetadata != null && atlasMetadata.intentId() != null && !atlasMetadata.intentId().isBlank()) {
            return atlasMetadata.intentId();
        }
        return baseTool.getToolName();
    }

    private String resolveTrustedUserId() {
        return userPermissionContext.current()
            .map(UserPermissionContext.UserPermission::username)
            .filter(username -> !username.isBlank())
            .orElse("anonymous");
    }

    private Map<String, Object> toCallbackPayload(SafeToolExecutionResult result) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("source", SafeToolExecutionSource.TOOL_CALLBACK.name());
        if (result == null) {
            payload.put("success", false);
            payload.put("executed", false);
            payload.put("error", "SAFE_TOOL_EXECUTION_RESULT_MISSING");
            payload.put("message", "SafeToolExecutor 未返回执行结果");
            return payload;
        }
        payload.put("executed", result.executed());
        if (result.toolResult() != null) {
            payload.putAll(result.toolResult());
            payload.put("executed", result.executed());
            payload.put("source", SafeToolExecutionSource.TOOL_CALLBACK.name());
            return payload;
        }
        payload.put("success", false);
        payload.put("error", "SAFE_TOOL_EXECUTION_BLOCKED");
        payload.put("message", result.answer() != null ? result.answer() : "ToolCallback 已被安全执行器阻断");
        return payload;
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
