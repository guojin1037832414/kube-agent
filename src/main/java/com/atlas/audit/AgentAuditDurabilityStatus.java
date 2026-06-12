package com.atlas.audit;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent 审计持久化状态快照。
 *
 * <p>中文说明：SafeToolExecutor 在执行 CREATE / UPDATE / DELETE / ACTION 等高风险 Tool 前，
 * 只应该依赖这个服务端状态快照判断 durable audit 是否就绪。这里不能读取前端、LLM、Plan 传入的
 * “已审计”“可写入”声明，因为那些都只是候选输入，不是生产安全事实。</p>
 *
 * <p>安全边界：{@link #disabled()} 代表当前没有可用持久审计存储，但仍默认
 * {@code failClosedForHighRisk=true}。也就是说，Phase 1 顶级 Agent Core 的默认策略是：
 * durable audit 没准备好时，高风险写 Tool 不进入真实 kube-manager 出口。</p>
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
            true,
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
