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
 * <p>中文说明：这是 legacy core 路径的 Spring AI 适配器，输入来自模型生成的
 * {@code toolInput} JSON 字符串，输出给 Spring AI / ReAct 继续消费。它的职责是做协议转换：
 * JSON → Map、参数别名归一化、委托统一执行器、再把执行回执转成 JSON；它不拥有任何
 * kube-manager 权限，也不决定用户到底能否调用某个 Tool。</p>
 *
 * <p>安全边界：LLM 传进来的 JSON 永远是不可信候选业务输入，里面的 {@code token}、
 * {@code orgId}、{@code userId}、{@code confirmation}、{@code writeAllowed} 等字段不能作为
 * 服务端可信上下文。本类必须通过 {@link SafeToolExecutor} 执行，不能直接调用
 * {@link BaseTool#execute(Map)}；返回给 LLM 的 JSON 也只是展示/观察材料，不是 HITL 确认、
 * audit prewrite、release gate 或 kube-manager 写入成功证明。</p>
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
            // 中文说明：这里解析的是模型生成的 Tool JSON，只能当作候选业务参数，
            // 不能把其中任何 token/orgId/userId/HITL 字段提升为服务端可信事实。
            Map<String, Object> params = parseInput(toolInput);
            // 中文说明：兼容旧入口的 alias 归一化只补齐普通业务字段；
            // 真正的受保护参数过滤、权限和 HITL 门禁必须由 SafeToolExecutor 再做一次。
            Map<String, Object> normalizedParams = parameterNormalizer.normalize(tool.getToolName(), params);
            // 安全边界：ToolCallback 是适配器，不是执行边界。这里必须委托统一安全执行器，
            // 并从服务端 ThreadLocal/Principal 取可信上下文，而不是信任 LLM JSON。
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
            // 中文说明：返回给 LLM 的 JSON 是观察结果/阻断原因，不代表前端可以据此放行写操作。
            return DEFAULT_CONVERTER.convert(toCallbackPayload(result), Map.class);
        } catch (Exception e) {
            // 安全边界：任何序列化/调用异常都返回结构化错误，不把异常栈、token 或内部 endpoint 暴露给 LLM。
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
            // 中文说明：LLM 偶尔会传非 JSON 对象；包成 raw 只用于后续澄清/失败解释，
            // 不能把 raw 文本拼接进 URL path、query、body 或审计原文。
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
        // 中文说明：未执行或被阻断时只给出可解释的展示字段，不伪造 tool_result、
        // HITL marker、audit receipt 或 trace 成功证据。
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
