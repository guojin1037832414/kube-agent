package com.atlas.tool.execution;

import com.atlas.audit.AgentAuditEvent;
import com.atlas.audit.AgentAuditDurableReceipt;
import com.atlas.audit.AgentAuditDurabilityStatus;
import com.atlas.audit.AgentAuditEventFactory;
import com.atlas.audit.AgentAuditOutcome;
import com.atlas.audit.AgentAuditRecorder;
import com.atlas.auth.AgentPrincipal;
import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.auth.UserPermissionContext;
import com.atlas.hitl.HitlGuard;
import com.atlas.observability.AgentTraceContext;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ProtectedToolParameterFilter;
import com.atlas.tool.core.ToolParameterNormalizer;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.core.ToolRegistry.ToolMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
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

    private static final Logger log = LoggerFactory.getLogger(SafeToolExecutor.class);

    private final ToolRegistry toolRegistry;
    private final HitlGuard hitlGuard;
    private final ToolParameterNormalizer toolParameterNormalizer;
    private final AgentAuditRecorder auditRecorder;
    private final AgentPrincipalResolver principalResolver;

    public SafeToolExecutor(ToolRegistry toolRegistry, HitlGuard hitlGuard) {
        this(toolRegistry, hitlGuard, AgentAuditRecorder.noop());
    }

    public SafeToolExecutor(ToolRegistry toolRegistry, HitlGuard hitlGuard, AgentAuditRecorder auditRecorder) {
        this(toolRegistry, hitlGuard, auditRecorder, null);
    }

    @Autowired
    public SafeToolExecutor(ToolRegistry toolRegistry,
                            HitlGuard hitlGuard,
                            AgentAuditRecorder auditRecorder,
                            AgentPrincipalResolver principalResolver) {
        this.toolRegistry = toolRegistry;
        this.hitlGuard = hitlGuard;
        this.auditRecorder = auditRecorder != null ? auditRecorder : AgentAuditRecorder.noop();
        this.principalResolver = principalResolver;
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
        String traceId = request != null
            ? AgentTraceContext.currentOrNew(request.traceId())
            : AgentTraceContext.currentOrNew("");
        try (AgentTraceContext.Scope ignored = AgentTraceContext.bind(traceId)) {
            return executeIntentWithTrace(request, traceId);
        }
    }

    private SafeToolExecutionResult executeIntentWithTrace(SafeToolExecutionRequest request, String traceId) {
        AgentPrincipal auditPrincipal = currentAuditPrincipal();
        if (request == null) {
            SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted(
                "[无执行请求] SafeToolExecutor 未收到有效请求", traceId);
            recordAudit(null, null, traceId, "", auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
            return result;
        }
        String intentId = request.intentId();
        if (intentId == null || intentId.isBlank()) {
            SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted(
                "[无目标意图] 未指定要执行的 Tool 意图", traceId);
            recordAudit(request, null, traceId, resolveTrustedOrgId(request.orgId()), auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
            return result;
        }

        String orgId = resolveTrustedOrgId(request.orgId());
        if (orgId == null || orgId.isBlank()) {
            SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted(
                "❌ 安全上下文缺失：无法确定当前用户所属组织，请重新登录后再试。", traceId);
            recordAudit(request, null, traceId, "", auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
            return result;
        }

        String previousToken = UserPermissionContext.CURRENT_TOKEN.get();
        String previousOrgId = UserPermissionContext.CURRENT_ORG_ID.get();
        bindThreadLocalContext(request.token(), orgId);
        try {
            Optional<BaseTool> toolOpt = toolRegistry.findByIntentId(intentId);
            if (toolOpt.isEmpty()) {
                SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted(
                    "⚠️ 意图 '" + intentId + "' 已识别，暂无对应 Tool 实现。", traceId);
                recordAudit(request, null, traceId, orgId, auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
                return result;
            }

            ToolMetadata metadata = resolveMetadata(intentId, toolOpt.get());
            if (!toolRegistry.canExecuteIntent(intentId)) {
                SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted(
                    "❌ 权限不足：无权执行 '" + intentId + "'", traceId);
                recordAudit(request, metadata, traceId, orgId, auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
                return result;
            }

            HitlGuard.Decision hitlDecision = hitlGuard.verifyByIntentId(
                toolRegistry, intentId, request.confirmation());
            if (!hitlDecision.allowed()) {
                SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted(hitlDecision.message(), traceId);
                recordAudit(request, metadata, traceId, orgId, auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
                return result;
            }

            SafeToolExecutionResult durableGate = verifyDurableAuditGate(request, metadata, traceId, orgId, auditPrincipal);
            if (durableGate != null) {
                return durableGate;
            }

            BaseTool tool = toolOpt.get();
            Map<String, Object> toolParams;
            try {
                toolParams = buildTrustedToolParams(request, orgId, tool);
            } catch (IllegalStateException ex) {
                SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted("❌ " + ex.getMessage(), traceId);
                recordAudit(request, metadata, traceId, orgId, auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
                return result;
            }
            try {
                Map<String, Object> rawResult = tool.execute(toolParams);
                // BaseTool.wrapCall 将 doExecute 抛出的异常转为 errorCode=TOOL_EXECUTION_ERROR 的 Map，
                // 异常不应视为已执行，需返回 notExecuted 以符合 fail-closed 语义。
                Object errorCode = rawResult != null ? rawResult.get("errorCode") : null;
                if ("TOOL_EXECUTION_ERROR".equals(String.valueOf(errorCode))) {
                    String message = rawResult.get("message") != null
                        ? rawResult.get("message").toString() : "Tool 执行异常";
                    SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted("❌ Tool 执行异常: " + message, traceId);
                    recordAudit(request, metadata, traceId, orgId, auditPrincipal, AgentAuditOutcome.ERROR, true, false, result.answer());
                    return result;
                }
                SafeToolExecutionResult result = toExecutionResult(intentId, rawResult, traceId);
                AgentAuditOutcome outcome = result.success()
                    ? AgentAuditOutcome.SUCCESS
                    : AgentAuditOutcome.BUSINESS_FAILURE;
                recordAudit(request, metadata, traceId, orgId, auditPrincipal, outcome, true, result.success(), result.answer());
                return result;
            } catch (Exception ex) {
                SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted("❌ Tool 执行异常: " + ex.getMessage(), traceId);
                recordAudit(request, metadata, traceId, orgId, auditPrincipal, AgentAuditOutcome.ERROR, false, false, result.answer());
                return result;
            }
        } finally {
            restoreThreadLocalContext(previousToken, previousOrgId);
        }
    }

    private ToolMetadata resolveMetadata(String intentId, BaseTool tool) {
        ToolMetadata systemMetadata = toolRegistry.listAllMetadataForSystemAudit().stream()
            .filter(meta -> intentId.equals(meta.intentId()) || tool.getToolName().equals(meta.name()))
            .findFirst()
            .orElse(null);
        if (systemMetadata != null) {
            return systemMetadata;
        }
        try {
            return toolRegistry.resolveByIntentId(intentId).orElseGet(() -> toolRegistry.resolve(tool.getToolName()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private void recordAudit(SafeToolExecutionRequest request,
                             ToolMetadata metadata,
                             String traceId,
                             String orgId,
                             AgentPrincipal auditPrincipal,
                             AgentAuditOutcome outcome,
                             boolean executed,
                             boolean success,
                             String reason) {
        try {
            AgentAuditEvent event = AgentAuditEventFactory.fromExecution(
                request, metadata, traceId, orgId, auditPrincipal, outcome, executed, success, reason);
            auditRecorder.record(event);
        } catch (RuntimeException ex) {
            // 当前 M5.25 recorder 是诊断型证据内核，不能因记录失败篡改真实 Tool 执行结果。
            // 后续持久化审计若要成为写操作前置门禁，应在 Tool 调用前单独 fail-closed。
            log.warn("[AgentAudit] 诊断审计记录失败: traceId={}, intentId={}",
                traceId, request != null ? request.intentId() : "", ex);
        }
    }

    private SafeToolExecutionResult verifyDurableAuditGate(SafeToolExecutionRequest request,
                                                           ToolMetadata metadata,
                                                           String traceId,
                                                           String orgId,
                                                           AgentPrincipal auditPrincipal) {
        AgentAuditDurabilityStatus status = auditRecorder.durabilityStatus();
        if (!status.failClosedForHighRisk()) {
            return null;
        }
        if (metadata == null || metadata.operationType() == null
            || metadata.operationType() == com.atlas.tool.annotation.AtlasToolMapping.OperationType.UNKNOWN) {
            String message = "AGENT_AUDIT_METADATA_REQUIRED: fail-closed high-risk mode requires explicit Tool risk metadata";
            SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted(message, traceId);
            recordAudit(request, metadata, traceId, orgId, auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
            return result;
        }
        if (!isHighRiskOperation(metadata)) {
            return null;
        }
        if (!status.enabled() || !status.ready() || !status.durableRetention()) {
            String message = "AGENT_AUDIT_DURABLE_REQUIRED: high-risk Tool execution requires ready durable audit storage";
            SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted(message, traceId);
            recordAudit(request, metadata, traceId, orgId, auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
            return result;
        }
        AgentAuditEvent preparedEvent = AgentAuditEventFactory.fromExecution(
            request,
            metadata,
            traceId,
            orgId,
            auditPrincipal,
            AgentAuditOutcome.PREPARED,
            false,
            false,
            "High-risk Tool durable pre-execution evidence"
        );
        AgentAuditDurableReceipt receipt;
        try {
            receipt = auditRecorder.prewriteHighRisk(preparedEvent);
        } catch (RuntimeException ex) {
            receipt = AgentAuditDurableReceipt.rejected(
                "AGENT_AUDIT_DURABLE_PREWRITE_EXCEPTION",
                auditRecorder.durabilityStatus()
            );
        }
        if (receipt == null || !receipt.accepted()) {
            String message = "AGENT_AUDIT_DURABLE_PREWRITE_REQUIRED: high-risk Tool execution requires durable pre-execution evidence";
            SafeToolExecutionResult result = SafeToolExecutionResult.notExecuted(message, traceId);
            recordAudit(request, metadata, traceId, orgId, auditPrincipal, AgentAuditOutcome.BLOCKED, false, false, result.answer());
            return result;
        }
        return null;
    }

    private boolean isHighRiskOperation(ToolMetadata metadata) {
        if (metadata.operationType() == null) {
            return true;
        }
        return switch (metadata.operationType()) {
            case CREATE, UPDATE, DELETE, ACTION, PLACEHOLDER, UNKNOWN -> true;
            case READ, SENSITIVE_READ -> false;
        };
    }

    private AgentPrincipal currentAuditPrincipal() {
        if (principalResolver == null) {
            return null;
        }
        try {
            return principalResolver.current().orElse(null);
        } catch (RuntimeException ex) {
            log.warn("[AgentAudit] 当前安全主体解析失败，审计 actor 将回落到兼容字段", ex);
            return null;
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
                if (!ProtectedToolParameterFilter.isProtected(key)) {
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
     * {@link #buildTrustedToolParams(SafeToolExecutionRequest, String, BaseTool)} 过滤 token/orgId 以及
     * HITL/审计/发布/写入控制字段，不额外删除未知业务字段。M4-PX.4 第五小批只收紧 execute_node 的
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
        Set<String> declaredAliasNames = specs.stream()
            .flatMap(spec -> spec.aliases() == null ? java.util.stream.Stream.<String>empty() : spec.aliases().stream())
            .collect(Collectors.toUnmodifiableSet());

        rejectUnknownPlanParameters(rawParams, allowedParamNames, declaredAliasNames);

        Map<String, Object> sanitized = new HashMap<>();
        normalized.forEach((key, value) -> {
            if (allowedParamNames.contains(key)) {
                sanitized.put(key, value);
            }
        });
        return sanitized;
    }

    /**
     * 拒绝 PLAN_EXECUTE_NODE 来源的未知业务字段。
     *
     * <p>这里故意检查原始 Plan 参数，而不是检查 normalizer 产物：normalizer 会保留 alias 原字段并补齐
     * canonical 字段，已声明的 alias（如 q/ns）允许作为输入兼容存在，但最终不会透传给 Tool；真正需要
     * 拒绝的是既不是 canonical、也不是当前 Tool 声明 alias、也不是受保护控制平面字段的未知业务参数。
     * 这样可以把 planner/schema 漂移显式暴露为结构化 fail-closed，而不是静默扩大查询范围。</p>
     */
    private void rejectUnknownPlanParameters(Map<String, Object> rawParams,
                                             Set<String> allowedParamNames,
                                             Set<String> declaredAliasNames) {
        Set<String> unknownPlanParams = rawParams.keySet().stream()
            .filter(key -> !allowedParamNames.contains(key))
            .filter(key -> !declaredAliasNames.contains(key))
            .filter(key -> !ProtectedToolParameterFilter.isProtected(key))
            .collect(Collectors.toCollection(TreeSet::new));
        if (!unknownPlanParams.isEmpty()) {
            throw new IllegalStateException(
                "TOOL_PARAMETER_UNKNOWN_FOR_PLAN_EXECUTE: PLAN_EXECUTE_NODE 来源包含 ToolParameterSpec 未声明的参数: "
                    + unknownPlanParams);
        }
    }

    private SafeToolExecutionResult toExecutionResult(String intentId, Map<String, Object> toolResult, String traceId) {
        Map<String, Object> result = toolResult != null ? toolResult : Map.of();
        boolean success = Boolean.TRUE.equals(result.get("success"));
        String message = result.get("message") != null ? result.get("message").toString() : "";
        Object data = result.get("data");

        String prefix = success ? "✅ " : "❌ ";
        String summary = success && data instanceof java.util.List<?>
            ? String.format("%s%s（共 %d 条数据）", prefix, message, ((java.util.List<?>) data).size())
            : prefix + message;

        Map<String, Object> structured = new HashMap<>();
        structured.put("success", success);
        structured.put("message", message);
        structured.put("tool", intentId);
        structured.put("traceId", traceId);
        structured.put("data", data != null ? data : Map.of());
        if (result.get("errorCode") != null) {
            structured.put("errorCode", result.get("errorCode"));
        }
        if (result.get("suggestions") != null) {
            structured.put("suggestions", result.get("suggestions"));
        }
        if (!success && (result.get("errorCode") != null || result.get("suggestions") != null)) {
            // 给 Graph / SSE / 前端一个稳定布尔位：这是可被继续澄清的问题，而不是普通异常。
            structured.put("requiresClarification", true);
        }
        return SafeToolExecutionResult.executed(success, summary, structured, traceId);
    }
}
