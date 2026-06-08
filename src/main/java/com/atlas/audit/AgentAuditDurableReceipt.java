package com.atlas.audit;

/**
 * Durable audit pre-write receipt.
 *
 * <p>High-risk Tool execution must not rely only on a stale "storage looks
 * ready" status check. The execution boundary asks for a concrete receipt for
 * this specific audit event before it calls the real Tool.</p>
 */
public record AgentAuditDurableReceipt(
    boolean accepted,
    String receiptId,
    String storageType,
    String reason,
    AgentAuditDurabilityStatus status
) {

    public static AgentAuditDurableReceipt accepted(String receiptId,
                                                    String storageType,
                                                    AgentAuditDurabilityStatus status) {
        return new AgentAuditDurableReceipt(
            true,
            safe(receiptId),
            safe(storageType),
            "",
            status != null ? status : AgentAuditDurabilityStatus.disabled()
        );
    }

    public static AgentAuditDurableReceipt rejected(String reason, AgentAuditDurabilityStatus status) {
        AgentAuditDurabilityStatus safeStatus = status != null ? status : AgentAuditDurabilityStatus.disabled();
        return new AgentAuditDurableReceipt(
            false,
            "",
            safeStatus.storageType(),
            safe(reason),
            safeStatus
        );
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }
}
