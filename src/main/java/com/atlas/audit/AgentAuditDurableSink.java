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

    AgentAuditDurabilityStatus status();

    static AgentAuditDurableSink noop() {
        return new AgentAuditDurableSink() {
            @Override
            public void append(AgentAuditEvent event) {
            }

            @Override
            public AgentAuditDurabilityStatus status() {
                return AgentAuditDurabilityStatus.disabled();
            }
        };
    }
}
