package com.atlas.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 审计事件的遥测投影。
 *
 * <p>M5.26 先定义稳定的内部投影契约，再逐步接入 OpenTelemetry Span/Event、前端回放和持久化审计。
 * 这样可以吸收 OTel GenAI 语义约定的最新能力，同时避免把仍在演进的实验字段直接固化成数据库契约。</p>
 */
public record AgentAuditTelemetryProjection(
    String schemaVersion,
    String eventName,
    String spanName,
    String spanKind,
    String spanStatus,
    Map<String, Object> stableAttributes,
    Map<String, Object> experimentalOtelAttributes
) {

    public Map<String, Object> toDiagnosticMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", schemaVersion);
        data.put("eventName", eventName);
        data.put("spanName", spanName);
        data.put("spanKind", spanKind);
        data.put("spanStatus", spanStatus);
        data.put("stableAttributes", stableAttributes);
        data.put("experimentalOtelAttributes", experimentalOtelAttributes);
        return data;
    }
}
