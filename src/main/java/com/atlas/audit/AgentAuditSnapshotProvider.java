package com.atlas.audit;

import java.util.Map;

/**
 * Agent 审计诊断快照提供者。
 *
 * <p>Controller 依赖该接口而不是内存实现，后续切换持久化审计存储时仍可暴露同一诊断契约。</p>
 */
public interface AgentAuditSnapshotProvider {

    Map<String, Object> snapshot();
}
