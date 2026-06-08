package com.atlas.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent audit durability status.
 *
 * <p>This small status object lets execution guards ask one question before a
 * high-risk Tool runs: can this process write the durable audit evidence it is
 * configured to require?</p>
 */
public record AgentAuditDurabilityStatus(
    boolean enabled,
    boolean ready,
    boolean durableRetention,
    boolean failClosedForHighRisk,
    String storageType,
    String location,
    long acceptedRecords,
    long failedRecords,
    String lastError
) {

    public static AgentAuditDurabilityStatus disabled() {
        return new AgentAuditDurabilityStatus(
            false,
            true,
            false,
            false,
            "none",
            "",
            0,
            0,
            ""
        );
    }

    public Map<String, Object> toDiagnosticMap() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", enabled);
        data.put("ready", ready);
        data.put("durableRetention", durableRetention);
        data.put("failClosedForHighRisk", failClosedForHighRisk);
        data.put("storageType", safe(storageType));
        data.put("location", safe(location));
        data.put("acceptedRecords", acceptedRecords);
        data.put("failedRecords", failedRecords);
        data.put("lastErrorPresent", lastError != null && !lastError.isBlank());
        return data;
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
