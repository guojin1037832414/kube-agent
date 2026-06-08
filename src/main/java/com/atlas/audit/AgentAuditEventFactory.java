package com.atlas.audit;

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
        SafeToolExecutionRequest safeRequest = request != null
            ? request
            : new SafeToolExecutionRequest("", Map.of(), "", "", "", "", "", null, null);
        return new AgentAuditEvent(
            newAuditId(),
            Instant.now(Clock.systemUTC()),
            traceId != null ? traceId : "",
            safeText(safeRequest.conversationId()),
            safeText(safeRequest.userId()),
            safeText(organizationId),
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

    private static String safeText(String value) {
        return value != null ? value : "";
    }
}
