package com.atlas.audit;

/**
 * Agent 审计记录器。
 *
 * <p>接口先行是为了保持执行边界稳定：M5.25 使用内存实现便于测试和诊断；后续切换到
 * 数据库、Kafka、OpenTelemetry event 或安全日志时，不需要改动 SafeToolExecutor 的审计语义。</p>
 */
public interface AgentAuditRecorder {

    void record(AgentAuditEvent event);

    default AgentAuditDurabilityStatus durabilityStatus() {
        return AgentAuditDurabilityStatus.disabled();
    }

    static AgentAuditRecorder noop() {
        return event -> {
        };
    }
}
