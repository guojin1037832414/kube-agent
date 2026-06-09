package com.atlas.audit;

import java.util.Map;

/**
 * Read-only audit query boundary.
 *
 * <p>Controllers depend on this interface instead of the in-memory recorder so
 * later JSONL, database, or search-backed indexes can replace the current
 * implementation without changing the admin API contract.</p>
 */
public interface AgentAuditQueryService {

    AgentAuditQueryResponse findByAuditId(String auditId);

    AgentAuditQueryResponse findByTraceId(String traceId, int maxResults);

    AgentAuditQueryResponse recentEvents(int maxResults);

    Map<String, Object> indexMetadata();
}
