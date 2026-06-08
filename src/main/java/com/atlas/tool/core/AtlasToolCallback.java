package com.atlas.tool.core;

import com.atlas.auth.UserPermissionContext;
import com.atlas.hitl.HitlGuard;
import com.atlas.observability.AgentTraceContext;
import com.atlas.tool.execution.SafeToolExecutionRequest;
import com.atlas.tool.execution.SafeToolExecutionResult;
import com.atlas.tool.execution.SafeToolExecutionSource;
import com.atlas.tool.execution.SafeToolExecutor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Atlas 专用 ToolCallback — 将 {@link BaseTool} 包装为 Spring AI 可识别的 ToolCallback。
 *
 * <p>这是早期 core 包中的兼容桥接类。新的 Graph Bridge 路径使用
 * {@code com.atlas.graph.bridge.AtlasToolCallback}，但这个类仍可能被历史 Spring AI
 * 注册链路引用，所以它也必须委托 {@link SafeToolExecutor}，不能直接调用 Tool。</p>
 *
 * <p>作用：</p>
 * <ol>
 *   <li>将 Atlas 的 {@code Map<String,Object> → Map<String,Object>} 统一接口
 *       适配到 Spring AI 的 {@code String call(String)} 契约</li>
 *   <li>JSON 反序列化：LLM 传入的 JSON 参数 → {@code Map<String,Object>}</li>
 *   <li>通过 {@link SafeToolExecutor} 完成权限、HITL、受保护参数过滤和 ThreadLocal 恢复</li>
 *   <li>JSON 序列化：安全执行结果 → JSON 字符串返回给 LLM</li>
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
    private final ToolRegistry.ToolMetadata atlasMetadata;
    private final ToolParameterNormalizer parameterNormalizer;
    private final SafeToolExecutor safeToolExecutor;
    private final UserPermissionContext userPermissionContext;
    private final ToolDefinition toolDefinition;
    private final ToolMetadata toolMetadata;

    public AtlasToolCallback(BaseTool tool) {
        this(tool, new HitlGuard(), null);
    }

    public AtlasToolCallback(BaseTool tool, HitlGuard hitlGuard) {
        this(tool, hitlGuard, null);
    }

    public AtlasToolCallback(BaseTool tool, HitlGuard hitlGuard, ToolRegistry.ToolMetadata atlasMetadata) {
        this(tool, legacyRuntime(tool, hitlGuard, atlasMetadata));
    }

    public AtlasToolCallback(BaseTool tool,
                             ToolParameterNormalizer parameterNormalizer,
                             SafeToolExecutor safeToolExecutor,
                             UserPermissionContext userPermissionContext,
                             ToolRegistry.ToolMetadata atlasMetadata) {
        this.tool = tool;
        this.parameterNormalizer = parameterNormalizer != null ? parameterNormalizer : new ToolParameterNormalizer();
        this.safeToolExecutor = safeToolExecutor;
        this.userPermissionContext = userPermissionContext != null ? userPermissionContext : new UserPermissionContext();
        this.atlasMetadata = atlasMetadata;
        this.toolDefinition = buildToolDefinition(tool);
        this.toolMetadata = ToolMetadata.builder().build();
    }

    private AtlasToolCallback(BaseTool tool, LegacyRuntime runtime) {
        this(tool, runtime.parameterNormalizer(), runtime.safeToolExecutor(),
            runtime.userPermissionContext(), runtime.atlasMetadata());
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
            // 2. 兼容旧入口的 alias 归一化；真正的受保护参数过滤由 SafeToolExecutor 再做一次。
            Map<String, Object> normalizedParams = parameterNormalizer.normalize(tool.getToolName(), params);
            // 3. 委托统一安全执行边界。ToolCallback 不能相信 LLM JSON 中的 token/orgId/userId/HITL 字段。
            SafeToolExecutionResult result = safeToolExecutor.executeIntent(new SafeToolExecutionRequest(
                resolveIntentId(),
                normalizedParams,
                resolveTrustedUserId(),
                UserPermissionContext.CURRENT_TOKEN.get(),
                UserPermissionContext.getCurrentOrgId(),
                "",
                AgentTraceContext.currentOrNew(""),
                null,
                SafeToolExecutionSource.TOOL_CALLBACK
            ));
            // 4. Map → JSON（返回给 LLM）
            return DEFAULT_CONVERTER.convert(toCallbackPayload(result), Map.class);
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

    private String resolveIntentId() {
        if (atlasMetadata != null && atlasMetadata.intentId() != null && !atlasMetadata.intentId().isBlank()) {
            return atlasMetadata.intentId();
        }
        return tool.getToolName();
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

    private static LegacyRuntime legacyRuntime(BaseTool tool,
                                               HitlGuard hitlGuard,
                                               ToolRegistry.ToolMetadata atlasMetadata) {
        UserPermissionContext userPermissionContext = new UserPermissionContext();
        ToolRegistry registry = new ToolRegistry(List.of(tool), userPermissionContext);
        registry.init();
        ToolRegistry.ToolMetadata metadata = atlasMetadata != null
            ? atlasMetadata
            : registry.resolve(tool.getToolName());
        HitlGuard guard = hitlGuard != null ? hitlGuard : new HitlGuard();
        return new LegacyRuntime(
            new ToolParameterNormalizer(registry),
            new SafeToolExecutor(registry, guard),
            userPermissionContext,
            metadata
        );
    }

    private record LegacyRuntime(ToolParameterNormalizer parameterNormalizer,
                                 SafeToolExecutor safeToolExecutor,
                                 UserPermissionContext userPermissionContext,
                                 ToolRegistry.ToolMetadata atlasMetadata) {
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
