package com.atlas.audit;

import com.atlas.auth.AgentPrincipal;
import com.atlas.tool.core.ProtectedToolParameterFilter;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.execution.SafeToolExecutionRequest;
import com.atlas.tool.execution.SafeToolExecutionSource;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 审计事件工厂。
 *
 * <p>集中生成 auditId、风险元数据和参数摘要，避免不同执行入口复制审计字段。
 * 参数摘要只记录键、类型和受保护状态，不保存 token/password/secret 等真实值。</p>
 *
 * <p>中文说明：SafeToolExecutor、Graph ToolCallback、ReAct 或未来 MCP/A2A 出口都应该通过这里生成
 * 同一形状的 audit event。这样 replay、eval、durable audit 和前端观测可以消费同一份脱敏证据，
 * 不需要知道每个入口的内部实现。</p>
 *
 * <p>安全边界：本工厂不执行 Tool、不调用 kube-manager、不发网络请求，也不授予写权限。
 * 它只把服务端可信主体、工具元数据和参数“存在性摘要”固化为证据；原始 reason、endpoint 与参数值
 * 后续仍会被读模型进一步压缩或隐藏。</p>
 */
public final class AgentAuditEventFactory {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int MAX_SUMMARY_KEYS = 64;

    private AgentAuditEventFactory() {
    }

    public static AgentAuditEvent fromExecution(
        SafeToolExecutionRequest request,
        ToolRegistry.ToolMetadata metadata,
        String traceId,
        String organizationId,
        AgentAuditOutcome outcome,
        boolean executed,
        boolean success,
        String reason
    ) {
        return fromExecution(
            request, metadata, traceId, organizationId, null, outcome, executed, success, reason);
    }

    public static AgentAuditEvent fromExecution(
        SafeToolExecutionRequest request,
        ToolRegistry.ToolMetadata metadata,
        String traceId,
        String organizationId,
        AgentPrincipal principal,
        AgentAuditOutcome outcome,
        boolean executed,
        boolean success,
        String reason
    ) {
        SafeToolExecutionRequest safeRequest = request != null
            ? request
            : new SafeToolExecutionRequest("", Map.of(), "", "", "", "", "", null, null);
        return new AgentAuditEvent(
            newAuditId(),
            Instant.now(Clock.systemUTC()),
            traceId != null ? traceId : "",
            safeText(safeRequest.conversationId()),
            safeText(trustedUserId(principal, safeRequest.userId())),
            safeText(trustedOrganizationId(principal, organizationId)),
            safeText(safeRequest.intentId()),
            metadata != null ? safeText(metadata.name()) : "",
            safeRequest.source() != null ? safeRequest.source() : SafeToolExecutionSource.GRAPH_TOOL_CALL,
            metadata != null ? safeText(metadata.httpMethod()) : "",
            metadata != null ? List.copyOf(metadata.apiEndpoints()) : List.of(),
            metadata != null ? metadata.operationType() : null,
            metadata != null && metadata.requiresConfirmation(),
            outcome,
            executed,
            success,
            safeText(reason),
            summarizeParameters(safeRequest.parameters())
        );
    }

    /**
     * 把业务参数转换成可审计但不可还原的摘要。
     *
     * <p>中文说明：这里只保留参数名、类型、是否受保护和是否出现。前端和评测能据此判断
     * “是否有高风险参数参与”，但不能从审计记录中恢复 secret、token 或 kube-manager 请求体。</p>
     */
    static Map<String, Object> summarizeParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of("count", 0, "keys", List.of());
        }
        List<Map<String, Object>> keys = parameters.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .limit(MAX_SUMMARY_KEYS)
            .map(entry -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getKey());
                item.put("protected", ProtectedToolParameterFilter.isProtected(entry.getKey()));
                item.put("type", valueType(entry.getValue()));
                item.put("present", entry.getValue() != null);
                return item;
            })
            .toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("count", parameters.size());
        summary.put("truncated", parameters.size() > MAX_SUMMARY_KEYS);
        summary.put("keys", keys);
        return summary;
    }

    private static String newAuditId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return "aud_" + HexFormat.of().formatHex(bytes);
    }

    private static String valueType(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String) {
            return "string";
        }
        if (value instanceof Number) {
            return "number";
        }
        if (value instanceof Boolean) {
            return "boolean";
        }
        if (value instanceof Map<?, ?>) {
            return "object";
        }
        if (value instanceof Iterable<?>) {
            return "array";
        }
        return value.getClass().getSimpleName();
    }

    /**
     * 优先使用服务端认证主体，而不是调用方请求体中的 userId。
     *
     * <p>安全边界：fallback 只兼容旧入口或测试；生产链路要让 Principal 成为审计身份来源。</p>
     */
    private static String trustedUserId(AgentPrincipal principal, String fallback) {
        if (principal != null && principal.isAuthenticated()) {
            return principal.username();
        }
        return fallback;
    }

    /**
     * 优先使用服务端认证主体中的组织上下文，避免前端伪造组织边界。
     */
    private static String trustedOrganizationId(AgentPrincipal principal, String fallback) {
        if (principal != null && principal.organizationId() != null && !principal.organizationId().isBlank()) {
            return principal.organizationId();
        }
        return fallback;
    }

    private static String safeText(String value) {
        return value != null ? value : "";
    }
}
