package com.atlas.audit;

/**
 * Durable audit sink boundary.
 *
 * <p>The recorder owns the diagnostic in-memory snapshot. This sink owns the
 * optional append-only evidence channel that can later be replaced by JDBC,
 * Elasticsearch, Kafka, or a security log service without changing
 * SafeToolExecutor semantics.</p>
 */
public interface AgentAuditDurableSink {

    void append(AgentAuditEvent event);

    /**
     * Writes durable pre-execution evidence for a high-risk Tool call.
     *
     * <p>The default implementation deliberately performs a real append and
     * returns a receipt only after the write succeeds. Implementations can later
     * override this with a reservation/outbox transaction, but callers keep the
     * same fail-closed contract.</p>
     */
    default AgentAuditDurableReceipt prewriteHighRisk(AgentAuditEvent event) {
        AgentAuditDurabilityStatus currentStatus = status();
        if (!currentStatus.enabled() || !currentStatus.ready() || !currentStatus.durableRetention()) {
            return AgentAuditDurableReceipt.rejected(
                "AGENT_AUDIT_DURABLE_NOT_READY",
                currentStatus
            );
        }
        append(event);
        return AgentAuditDurableReceipt.accepted(
            event != null ? event.auditId() : "",
            currentStatus.storageType(),
            status()
        );
    }

    AgentAuditDurabilityStatus status();

    static AgentAuditDurableSink noop() {
        return new AgentAuditDurableSink() {
            @Override
            public void append(AgentAuditEvent event) {
            }

            @Override
            public AgentAuditDurableReceipt prewriteHighRisk(AgentAuditEvent event) {
                return AgentAuditDurableReceipt.rejected(
                    "AGENT_AUDIT_DURABLE_DISABLED",
                    AgentAuditDurabilityStatus.disabled()
                );
            }

            @Override
            public AgentAuditDurabilityStatus status() {
                return AgentAuditDurabilityStatus.disabled();
            }
        };
    }
}
