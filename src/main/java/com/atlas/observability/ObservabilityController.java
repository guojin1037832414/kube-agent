package com.atlas.observability;

import com.atlas.audit.AgentAuditQueryResponse;
import com.atlas.audit.AgentAuditQueryService;
import com.atlas.audit.AgentAuditSnapshotProvider;
import com.atlas.auth.AgentPrincipal;
import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Optional;

/**
 * Agent 可观测性控制器 — M5.20 最小诊断入口。
 *
 * <p>生产级指标仍通过 Spring Boot Actuator/Micrometer 暴露；本接口只返回 Atlas 维度的摘要快照，
 * 便于前端状态页或人工排障快速确认指标链路已经工作。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M5.20
 */
@RestController
@RequestMapping("/api/agent/observability")
public class ObservabilityController {

    private final AgentMetricsService metricsService;
    private final AgentAuditSnapshotProvider auditSnapshotProvider;
    private final AgentAuditQueryService auditQueryService;
    private final AgentPrincipalResolver principalResolver;

    public ObservabilityController(AgentMetricsService metricsService,
                                   AgentAuditSnapshotProvider auditSnapshotProvider,
                                   AgentAuditQueryService auditQueryService,
                                   AgentPrincipalResolver principalResolver) {
        this.metricsService = metricsService;
        this.auditSnapshotProvider = auditSnapshotProvider;
        this.auditQueryService = auditQueryService;
        this.principalResolver = principalResolver;
    }

    /** 查询 Agent 指标快照。 */
    @GetMapping("/snapshot")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> snapshot() {
        Optional<AgentPrincipal> currentUser = principalResolver.current();
        if (currentUser.isEmpty() || !currentUser.get().isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("未登录或会话已过期"));
        }
        if (!currentUser.get().isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("仅管理员可查看 Agent 观测与审计诊断快照"));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "metrics", metricsService.snapshot(),
            "audit", auditSnapshotProvider.snapshot()
        )));
    }

    /** 查询脱敏审计索引元信息。 */
    @GetMapping("/audit/index")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> auditIndex() {
        ResponseEntity<ApiResponse<Map<String, Object>>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(auditQueryService.indexMetadata()));
    }

    /** 按 auditId 查询单条脱敏审计事件。 */
    @GetMapping("/audit/id/{auditId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentAuditQueryResponse>> auditByAuditId(@PathVariable String auditId) {
        ResponseEntity<ApiResponse<AgentAuditQueryResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(auditQueryService.findByAuditId(auditId)));
    }

    /** 按 traceId 查询脱敏审计时间线。 */
    @GetMapping("/audit/trace/{traceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentAuditQueryResponse>> auditByTraceId(@PathVariable String traceId,
                                                                               @RequestParam(defaultValue = "50") int limit) {
        ResponseEntity<ApiResponse<AgentAuditQueryResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(auditQueryService.findByTraceId(traceId, limit)));
    }

    private <T> ResponseEntity<ApiResponse<T>> requireAdmin() {
        Optional<AgentPrincipal> currentUser = principalResolver.current();
        if (currentUser.isEmpty() || !currentUser.get().isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("未登录或会话已过期"));
        }
        if (!currentUser.get().isAdmin()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("仅管理员可查看 Agent 观测与审计诊断"));
        }
        return null;
    }
}
