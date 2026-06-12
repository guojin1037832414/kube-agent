package com.atlas.audit;

/**
 * Agent 审计记录器。
 *
 * <p>接口先行是为了保持执行边界稳定：M5.25 使用内存实现便于测试和诊断；后续切换到
 * 数据库、Kafka、OpenTelemetry event 或安全日志时，不需要改动 SafeToolExecutor 的审计语义。</p>
 *
 * <p>安全边界：审计记录器只接收 SafeToolExecutor 已经构造好的脱敏审计事件，不重新授权 Tool，
 * 不读取原始参数值，也不把审计结果反向注入 prompt。高风险写操作的 durable prewrite 是执行前证据，
 * 不是执行许可；真正能放行写操作的仍然是策略、身份、HITL 与 Tool 安全执行链。</p>
 */
public interface AgentAuditRecorder {

    /**
     * 记录一次最终审计事件。
     *
     * <p>中文说明：实现可以写内存、JSONL、遥测或未来数据库，但都必须保持 redacted-only 语义。</p>
     */
    void record(AgentAuditEvent event);

    /**
     * 为高风险写操作预写 durable audit 证据。
     *
     * <p>安全边界：默认实现 fail-closed 地拒绝，避免没有持久审计能力时误以为写操作已经具备可追溯性。</p>
     */
    default AgentAuditDurableReceipt prewriteHighRisk(AgentAuditEvent event) {
        return AgentAuditDurableReceipt.rejected(
            "AGENT_AUDIT_DURABLE_PREWRITE_UNSUPPORTED",
            durabilityStatus()
        );
    }

    /**
     * 暴露当前 durable audit 能力状态，供 Observability 管理员只读查看。
     */
    default AgentAuditDurabilityStatus durabilityStatus() {
        return AgentAuditDurabilityStatus.disabled();
    }

    /**
     * 空实现仅用于测试或未启用审计的局部场景，生产写路径不应依赖它获得安全感。
     */
    static AgentAuditRecorder noop() {
        return event -> {
        };
    }
}
