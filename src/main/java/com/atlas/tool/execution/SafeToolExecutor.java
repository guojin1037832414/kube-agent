package com.atlas.tool.execution;

import com.atlas.auth.UserPermissionContext;
import com.atlas.hitl.HitlGuard;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterNormalizer;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.core.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Atlas 统一安全 Tool 执行器。
 *
 * <p>M4-PX.3 引入该组件，用于把既有 Graph {@code tool_call} 节点中的安全执行链
 * 下沉到统一边界。后续 {@code execute_node}、ReAct、ToolCallback 等入口都应逐步复用
 * 本组件，避免每个入口各自复制权限、HITL、租户上下文和参数过滤逻辑。</p>
 *
 * <p><b>安全原则：</b></p>
 * <ol>
 *   <li>只通过 {@link ToolRegistry} 查找 Tool 和风险元数据；</li>
 *   <li>LLM/Plan 参数中的 token/orgId/userId 等受保护字段一律不可信；</li>
 *   <li>系统上下文字段最后写入，防止跨租户覆盖；</li>
 *   <li>真正调用 {@link BaseTool#execute(Map)} 前必须通过 {@link HitlGuard}；</li>
 *   <li>缺失 orgId、未注册 Tool、权限不足、HITL 未确认均 fail-closed；</li>
 *   <li>执行完成后恢复 ThreadLocal，防止线程池污染。</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-M4-PX.3
 */
@Component
public class SafeToolExecutor {

    /**
     * 会话/认证上下文字段白名单。
     *
     * <p>这些字段只能来自服务端可信上下文，不能由 LLM、Plan 或前端参数覆盖。</p>
     */
    private static final Set<String> PROTECTED_CONTEXT_PARAMS = Set.of(
        "token",
        "organizationId",
        "orgId",
        "conversationId",
        "conversation_id",
        "userId",
        "user_id"
    );

    private final ToolRegistry toolRegistry;
    private final HitlGuard hitlGuard;
    private final ToolParameterNormalizer toolParameterNormalizer;

    public SafeToolExecutor(ToolRegistry toolRegistry, HitlGuard hitlGuard) {
        this.toolRegistry = toolRegistry;
        this.hitlGuard = hitlGuard;
        this.toolParameterNormalizer = new ToolParameterNormalizer(toolRegistry);
    }

    /**
     * 按 intentId 安全执行 Tool。
     *
     * <p>方法内部严格保持“上下文绑定 → Tool/权限解析 → 参数净化 → HITL 校验 → execute → 恢复上下文”
     * 的顺序。任何失败都会返回结构化的未执行结果，而不是继续降级猜测。</p>
     *
     * @param request 安全执行请求
     * @return 安全执行结果
     */
    public SafeToolExecutionResult executeIntent(SafeToolExecutionRequest request) {
        if (request == null) {
            return SafeToolExecutionResult.notExecuted("[无执行请求] SafeToolExecutor 未收到有效请求");
        }
        String intentId = request.intentId();
        if (intentId == null || intentId.isBlank()) {
            return SafeToolExecutionResult.notExecuted("[无目标意图] 未指定要执行的 Tool 意图");
        }

        String orgId = resolveTrustedOrgId(request.orgId());
        if (orgId == null || orgId.isBlank()) {
            return SafeToolExecutionResult.notExecuted(
                "❌ 安全上下文缺失：无法确定当前用户所属组织，请重新登录后再试。");
        }

        String previousToken = UserPermissionContext.CURRENT_TOKEN.get();
        String previousOrgId = UserPermissionContext.CURRENT_ORG_ID.get();
        bindThreadLocalContext(request.token(), orgId);
        try {
            Optional<BaseTool> toolOpt = toolRegistry.findByIntentId(intentId);
            if (toolOpt.isEmpty()) {
                return SafeToolExecutionResult.notExecuted(
                    "⚠️ 意图 '" + intentId + "' 已识别，暂无对应 Tool 实现。");
            }

            if (!toolRegistry.canExecuteIntent(intentId)) {
                return SafeToolExecutionResult.notExecuted(
                    "❌ 权限不足：无权执行 '" + intentId + "'");
            }

            HitlGuard.Decision hitlDecision = hitlGuard.verifyByIntentId(
                toolRegistry, intentId, request.confirmation());
            if (!hitlDecision.allowed()) {
                return SafeToolExecutionResult.notExecuted(hitlDecision.message());
            }

            BaseTool tool = toolOpt.get();
            Map<String, Object> toolParams;
            try {
                toolParams = buildTrustedToolParams(request, orgId, tool);
            } catch (IllegalStateException ex) {
                return SafeToolExecutionResult.notExecuted("❌ " + ex.getMessage());
            }
            try {
                Map<String, Object> rawResult = tool.execute(toolParams);
                // BaseTool.wrapCall 将 doExecute 抛出的异常转为 errorCode=TOOL_EXECUTION_ERROR 的 Map，
                // 异常不应视为已执行，需返回 notExecuted 以符合 fail-closed 语义。
                Object errorCode = rawResult != null ? rawResult.get("errorCode") : null;
                if ("TOOL_EXECUTION_ERROR".equals(String.valueOf(errorCode))) {
                    String message = rawResult.get("message") != null
                        ? rawResult.get("message").toString() : "Tool 执行异常";
                    return SafeToolExecutionResult.notExecuted("❌ Tool 执行异常: " + message);
                }
                return toExecutionResult(intentId, rawResult);
            } catch (Exception ex) {
                return SafeToolExecutionResult.notExecuted("❌ Tool 执行异常: " + ex.getMessage());
            }
        } finally {
            restoreThreadLocalContext(previousToken, previousOrgId);
        }
    }

    private String resolveTrustedOrgId(String requestOrgId) {
        if (requestOrgId != null && !requestOrgId.isBlank()) {
            return requestOrgId;
        }
        return UserPermissionContext.getCurrentOrgId();
    }

    private void bindThreadLocalContext(String token, String orgId) {
        if (token != null && !token.isBlank()) {
            UserPermissionContext.CURRENT_TOKEN.set(token);
        } else {
            UserPermissionContext.CURRENT_TOKEN.remove();
        }
        if (orgId != null && !orgId.isBlank()) {
            UserPermissionContext.CURRENT_ORG_ID.set(orgId);
        } else {
            UserPermissionContext.CURRENT_ORG_ID.remove();
        }
    }

    private void restoreThreadLocalContext(String previousToken, String previousOrgId) {
        if (previousToken != null) {
            UserPermissionContext.CURRENT_TOKEN.set(previousToken);
        } else {
            UserPermissionContext.CURRENT_TOKEN.remove();
        }
        if (previousOrgId != null) {
            UserPermissionContext.CURRENT_ORG_ID.set(previousOrgId);
        } else {
            UserPermissionContext.CURRENT_ORG_ID.remove();
        }
    }

    private Map<String, Object> buildTrustedToolParams(SafeToolExecutionRequest request, String orgId, BaseTool tool) {
        Map<String, Object> toolParams = new HashMap<>();
        Map<String, Object> businessParams = sanitizeBusinessParams(request, tool);
        if (businessParams != null) {
            businessParams.forEach((key, value) -> {
                if (!isProtectedContextParam(key)) {
                    toolParams.put(key, value);
                }
            });
        }
        // 系统上下文字段最后写入，防止不可信参数覆盖租户与用户边界。
        toolParams.put("userId", request.userId() != null && !request.userId().isBlank()
            ? request.userId() : "anonymous");
        toolParams.put("organizationId", orgId);
        if (request.conversationId() != null && !request.conversationId().isBlank()) {
            toolParams.put("conversationId", request.conversationId());
        }
        return toolParams;
    }

    /**
     * 对业务参数执行来源感知的最小净化。
     *
     * <p>普通 Graph/ReAct/ToolCallback 兼容路径仍保持旧语义：只由
     * {@link #buildTrustedToolParams(SafeToolExecutionRequest, String, BaseTool)} 过滤 token/orgId 等受保护
     * 系统字段，不额外删除未知业务字段。M4-PX.4 第五小批只收紧 execute_node 的
     * {@link SafeToolExecutionSource#PLAN_EXECUTE_NODE} 来源，因为该来源代表 Plan 自动执行候选，
     * 必须以 Tool 自身声明的 {@link ToolParameterSpec} 作为唯一可信业务参数白名单。</p>
     */
    private Map<String, Object> sanitizeBusinessParams(SafeToolExecutionRequest request, BaseTool tool) {
        Map<String, Object> rawParams = request.parameters() != null ? request.parameters() : Map.of();
        if (request.source() != SafeToolExecutionSource.PLAN_EXECUTE_NODE) {
            return rawParams;
        }

        List<ToolParameterSpec> specs = tool.getParameterSpecs();
        if (specs == null || specs.isEmpty()) {
            throw new IllegalStateException("TOOL_PARAMETER_SPEC_MISSING: PLAN_EXECUTE_NODE 来源要求 Tool 显式声明 ToolParameterSpec");
        }

        Map<String, Object> normalized = toolParameterNormalizer.normalize(tool.getToolName(), rawParams);
        Set<String> allowedParamNames = specs.stream()
            .map(ToolParameterSpec::name)
            .collect(Collectors.toUnmodifiableSet());

        Map<String, Object> sanitized = new HashMap<>();
        normalized.forEach((key, value) -> {
            if (allowedParamNames.contains(key)) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    private boolean isProtectedContextParam(String key) {
        return key != null && PROTECTED_CONTEXT_PARAMS.contains(key);
    }

    private SafeToolExecutionResult toExecutionResult(String intentId, Map<String, Object> toolResult) {
        Map<String, Object> result = toolResult != null ? toolResult : Map.of();
        boolean success = Boolean.TRUE.equals(result.get("success"));
        String message = result.get("message") != null ? result.get("message").toString() : "";
        Object data = result.get("data");

        String summary = data instanceof java.util.List<?>
            ? String.format("✅ %s（共 %d 条数据）", message, ((java.util.List<?>) data).size())
            : "✅ " + message;

        Map<String, Object> structured = new HashMap<>();
        structured.put("success", success);
        structured.put("message", message);
        structured.put("tool", intentId);
        structured.put("data", data != null ? data : Map.of());
        return SafeToolExecutionResult.executed(success, summary, structured);
    }
}
