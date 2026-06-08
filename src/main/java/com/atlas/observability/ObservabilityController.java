package com.atlas.observability;

import com.atlas.audit.AgentAuditSnapshotProvider;
import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
    private final UserPermissionContext userPermissionContext;

    public ObservabilityController(AgentMetricsService metricsService,
                                   AgentAuditSnapshotProvider auditSnapshotProvider,
                                   UserPermissionContext userPermissionContext) {
        this.metricsService = metricsService;
        this.auditSnapshotProvider = auditSnapshotProvider;
        this.userPermissionContext = userPermissionContext;
    }

    /** 查询 Agent 指标快照。 */
    @GetMapping("/snapshot")
    public ResponseEntity<ApiResponse<Map<String, Object>>> snapshot() {
        Optional<UserPermissionContext.UserPermission> currentUser = userPermissionContext.current();
        if (currentUser.isEmpty()) {
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
}
