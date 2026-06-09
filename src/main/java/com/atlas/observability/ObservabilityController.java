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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final AgentKubeManagerHttpOutletHealthSummaryService kubeManagerHttpOutletHealthSummaryService;
    private final AgentKubeManagerWriteRetryReadinessService kubeManagerWriteRetryReadinessService;
    private final AgentKubeManagerWriteIdempotencyContractService kubeManagerWriteIdempotencyContractService;
    private final AgentKubeManagerWriteOperationSafetyContractService kubeManagerWriteOperationSafetyContractService;
    private final AgentKubeManagerWriteRetryGovernanceContractService kubeManagerWriteRetryGovernanceContractService;
    private final AgentKubeManagerWriteReleaseGateContractService kubeManagerWriteReleaseGateContractService;
    private final AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService kubeManagerHttpOutletGovernanceWorkbenchOverviewService;
    private final AgentTopTierReadinessOverviewService topTierReadinessOverviewService;
    private final AgentMemoryRagReadinessService memoryRagReadinessService;
    private final AgentAuditSnapshotProvider auditSnapshotProvider;
    private final AgentAuditQueryService auditQueryService;
    private final AgentReplayTimelineService replayTimelineService;
    private final AgentEvalReportService evalReportService;
    private final AgentEvalSuiteCatalogService evalSuiteCatalogService;
    private final AgentEvalTraceSetCatalogService evalTraceSetCatalogService;
    private final AgentEvalTraceSetCandidateDiscoveryService traceSetCandidateDiscoveryService;
    private final AgentEvalTraceSetPromotionWorkflowService traceSetPromotionWorkflowService;
    private final AgentEvalWorkbenchCapabilitiesService evalWorkbenchCapabilitiesService;
    private final AgentEvalWorkbenchOverviewService evalWorkbenchOverviewService;
    private final AgentEvalWorkbenchTraceSetDetailService evalWorkbenchTraceSetDetailService;
    private final AgentEvalWorkbenchPromotionWorkflowService evalWorkbenchPromotionWorkflowService;
    private final AgentEvalWorkbenchCatalogPatchReviewService evalWorkbenchCatalogPatchReviewService;
    private final AgentEvalWorkbenchGateBundleSummaryService evalWorkbenchGateBundleSummaryService;
    private final AgentPrincipalResolver principalResolver;

    public ObservabilityController(AgentMetricsService metricsService,
                                   AgentKubeManagerHttpOutletHealthSummaryService kubeManagerHttpOutletHealthSummaryService,
                                   AgentKubeManagerWriteRetryReadinessService kubeManagerWriteRetryReadinessService,
                                   AgentKubeManagerWriteIdempotencyContractService kubeManagerWriteIdempotencyContractService,
                                   AgentKubeManagerWriteOperationSafetyContractService kubeManagerWriteOperationSafetyContractService,
                                   AgentKubeManagerWriteRetryGovernanceContractService kubeManagerWriteRetryGovernanceContractService,
                                   AgentKubeManagerWriteReleaseGateContractService kubeManagerWriteReleaseGateContractService,
                                   AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService kubeManagerHttpOutletGovernanceWorkbenchOverviewService,
                                   AgentTopTierReadinessOverviewService topTierReadinessOverviewService,
                                   AgentMemoryRagReadinessService memoryRagReadinessService,
                                   AgentAuditSnapshotProvider auditSnapshotProvider,
                                   AgentAuditQueryService auditQueryService,
                                   AgentReplayTimelineService replayTimelineService,
                                   AgentEvalReportService evalReportService,
                                   AgentEvalSuiteCatalogService evalSuiteCatalogService,
                                   AgentEvalTraceSetCatalogService evalTraceSetCatalogService,
                                   AgentEvalTraceSetCandidateDiscoveryService traceSetCandidateDiscoveryService,
                                   AgentEvalTraceSetPromotionWorkflowService traceSetPromotionWorkflowService,
                                   AgentEvalWorkbenchCapabilitiesService evalWorkbenchCapabilitiesService,
                                   AgentEvalWorkbenchOverviewService evalWorkbenchOverviewService,
                                   AgentEvalWorkbenchTraceSetDetailService evalWorkbenchTraceSetDetailService,
                                   AgentEvalWorkbenchPromotionWorkflowService evalWorkbenchPromotionWorkflowService,
                                   AgentEvalWorkbenchCatalogPatchReviewService evalWorkbenchCatalogPatchReviewService,
                                   AgentEvalWorkbenchGateBundleSummaryService evalWorkbenchGateBundleSummaryService,
                                   AgentPrincipalResolver principalResolver) {
        this.metricsService = metricsService;
        this.kubeManagerHttpOutletHealthSummaryService = kubeManagerHttpOutletHealthSummaryService;
        this.kubeManagerWriteRetryReadinessService = kubeManagerWriteRetryReadinessService;
        this.kubeManagerWriteIdempotencyContractService = kubeManagerWriteIdempotencyContractService;
        this.kubeManagerWriteOperationSafetyContractService = kubeManagerWriteOperationSafetyContractService;
        this.kubeManagerWriteRetryGovernanceContractService = kubeManagerWriteRetryGovernanceContractService;
        this.kubeManagerWriteReleaseGateContractService = kubeManagerWriteReleaseGateContractService;
        this.kubeManagerHttpOutletGovernanceWorkbenchOverviewService = kubeManagerHttpOutletGovernanceWorkbenchOverviewService;
        this.topTierReadinessOverviewService = topTierReadinessOverviewService;
        this.memoryRagReadinessService = memoryRagReadinessService;
        this.auditSnapshotProvider = auditSnapshotProvider;
        this.auditQueryService = auditQueryService;
        this.replayTimelineService = replayTimelineService;
        this.evalReportService = evalReportService;
        this.evalSuiteCatalogService = evalSuiteCatalogService;
        this.evalTraceSetCatalogService = evalTraceSetCatalogService;
        this.traceSetCandidateDiscoveryService = traceSetCandidateDiscoveryService;
        this.traceSetPromotionWorkflowService = traceSetPromotionWorkflowService;
        this.evalWorkbenchCapabilitiesService = evalWorkbenchCapabilitiesService;
        this.evalWorkbenchOverviewService = evalWorkbenchOverviewService;
        this.evalWorkbenchTraceSetDetailService = evalWorkbenchTraceSetDetailService;
        this.evalWorkbenchPromotionWorkflowService = evalWorkbenchPromotionWorkflowService;
        this.evalWorkbenchCatalogPatchReviewService = evalWorkbenchCatalogPatchReviewService;
        this.evalWorkbenchGateBundleSummaryService = evalWorkbenchGateBundleSummaryService;
        this.principalResolver = principalResolver;
    }

    /** 构建一期顶级 Agent 就绪总览，不触发任何运行时动作。 */
    @GetMapping("/top-tier/readiness-overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentTopTierReadinessOverviewResponse>> topTierReadinessOverview() {
        ResponseEntity<ApiResponse<AgentTopTierReadinessOverviewResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(topTierReadinessOverviewService.overview()));
    }

    /** 构建 Memory/RAG 学习层就绪契约，不执行检索、不调用向量库。 */
    @GetMapping("/memory-rag/readiness")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagReadinessResponse>> memoryRagReadiness() {
        ResponseEntity<ApiResponse<AgentMemoryRagReadinessResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagReadinessService.readiness()));
    }

    /** Describe kube-manager HTTP outlet resilience state without probing kube-manager. */
    @GetMapping("/kube-manager/http-outlet/health-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletHealthSummaryResponse>> kubeManagerHttpOutletHealthSummary() {
        ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletHealthSummaryResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(kubeManagerHttpOutletHealthSummaryService.summary()));
    }

    /** Describe why kube-manager write retries remain disabled and what evidence future releases must bind. */
    @GetMapping("/kube-manager/http-outlet/write-retry-readiness")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryReadinessResponse>> kubeManagerWriteRetryReadiness() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryReadinessResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(kubeManagerWriteRetryReadinessService.readiness()));
    }

    /** Describe the server-derived idempotency-key contract before it is bound to real writes. */
    @GetMapping("/kube-manager/http-outlet/write-idempotency-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentKubeManagerWriteIdempotencyContractResponse>> kubeManagerWriteIdempotencyContract() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteIdempotencyContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(kubeManagerWriteIdempotencyContractService.contract()));
    }

    /** Describe the write operation allowlist/RBAC/readback contract before binding runtime writes. */
    @GetMapping("/kube-manager/http-outlet/write-operation-safety-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentKubeManagerWriteOperationSafetyContractResponse>> kubeManagerWriteOperationSafetyContract() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteOperationSafetyContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(kubeManagerWriteOperationSafetyContractService.contract()));
    }

    /** Describe write retry failure classification and compensation contracts before runtime binding. */
    @GetMapping("/kube-manager/http-outlet/write-retry-governance-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryGovernanceContractResponse>> kubeManagerWriteRetryGovernanceContract() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryGovernanceContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(kubeManagerWriteRetryGovernanceContractService.contract()));
    }

    /** Describe durable receipt and HITL/release evidence contracts before runtime write binding. */
    @GetMapping("/kube-manager/http-outlet/write-release-gate-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentKubeManagerWriteReleaseGateContractResponse>> kubeManagerWriteReleaseGateContract() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteReleaseGateContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(kubeManagerWriteReleaseGateContractService.contract()));
    }

    /** Build a Vue-ready governance overview without opening kube-manager writes. */
    @GetMapping("/kube-manager/http-outlet/governance-workbench/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse>> kubeManagerHttpOutletGovernanceWorkbenchOverview() {
        ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(kubeManagerHttpOutletGovernanceWorkbenchOverviewService.overview()));
    }

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

    /** 鎸?traceId 鏌ヨ鍓嶇鍙洖鏀剧殑鑴辨晱 Agent timeline銆?*/
    @GetMapping("/replay/trace/{traceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentReplayTimelineResponse>> replayByTraceId(@PathVariable String traceId,
                                                                                    @RequestParam(defaultValue = "50") int limit) {
        ResponseEntity<ApiResponse<AgentReplayTimelineResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(replayTimelineService.traceTimeline(traceId, limit)));
    }

    /** Evaluate redacted replay evidence for a trace. */
    @GetMapping("/eval/trace/{traceId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalReportResponse>> evalByTraceId(@PathVariable String traceId,
                                                                              @RequestParam(defaultValue = "50") int limit) {
        ResponseEntity<ApiResponse<AgentEvalReportResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(evalReportService.evaluateTrace(traceId, limit)));
    }

    /** Evaluate a deterministic release-gate style suite from redacted replay evidence. */
    @PostMapping("/eval/suite")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalSuiteResponse>> evalSuite(@RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalSuiteResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        AgentEvalSuiteRequest safeRequest = request != null
            ? request
            : new AgentEvalSuiteRequest(
                java.util.List.of(),
                AgentEvalReportService.DEFAULT_TRACE_MAX_RESULTS,
                AgentEvalReportService.DEFAULT_SUITE_MINIMUM_SCORE,
                AgentEvalReportService.DEFAULT_SUITE_FAIL_ON_WARNINGS
            );
        int limit = safeRequest.limit() != null
            ? safeRequest.limit()
            : AgentEvalReportService.DEFAULT_TRACE_MAX_RESULTS;
        int minimumScore = safeRequest.minimumScore() != null
            ? safeRequest.minimumScore()
            : AgentEvalReportService.DEFAULT_SUITE_MINIMUM_SCORE;
        boolean failOnWarnings = safeRequest.failOnWarnings() != null
            ? safeRequest.failOnWarnings()
            : AgentEvalReportService.DEFAULT_SUITE_FAIL_ON_WARNINGS;
        return ResponseEntity.ok(ApiResponse.ok(evalReportService.evaluateSuite(
            safeRequest.traceIds(),
            limit,
            minimumScore,
            failOnWarnings
        )));
    }

    /** List built-in deterministic eval suites that CI or the frontend can run with trace evidence. */
    @GetMapping("/eval/suites")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalSuiteCatalogResponse>> evalSuites() {
        ResponseEntity<ApiResponse<AgentEvalSuiteCatalogResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(evalSuiteCatalogService.catalog()));
    }

    /** Run a named deterministic eval suite using caller-provided redacted trace anchors. */
    @PostMapping("/eval/suites/{suiteId}/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalSuiteRunResponse>> runEvalSuite(@PathVariable String suiteId,
                                                                               @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalSuiteRunResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return evalSuiteCatalogService.run(suiteId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("未知的 Agent eval suite: " + suiteId)));
    }

    /** Produce a compact CI/release-gate artifact without embedded replay or per-trace reports. */
    @PostMapping("/eval/suites/{suiteId}/gate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalSuiteGateArtifact>> evalSuiteGate(@PathVariable String suiteId,
                                                                                 @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalSuiteGateArtifact>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return evalSuiteCatalogService.gate(suiteId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("未知的 Agent eval suite: " + suiteId)));
    }

    /** Describe stable backend capabilities for the future Vue eval workbench. */
    @GetMapping("/eval/workbench/capabilities")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalWorkbenchCapabilitiesResponse>> evalWorkbenchCapabilities() {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchCapabilitiesResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(evalWorkbenchCapabilitiesService.capabilities()));
    }

    /** Build a frontend-ready eval workbench overview without executing Tools or mutating catalogs. */
    @GetMapping("/eval/workbench/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalWorkbenchOverviewResponse>> evalWorkbenchOverview() {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchOverviewResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(evalWorkbenchOverviewService.overview()));
    }

    /** Build a frontend-ready gate-bundle summary without accepting caller trace IDs. */
    @GetMapping("/eval/workbench/gate-bundle-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalWorkbenchGateBundleSummaryResponse>> evalWorkbenchGateBundleSummary() {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchGateBundleSummaryResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(evalWorkbenchGateBundleSummaryService.summary()));
    }

    /** Build a frontend-ready detail view for one trace set in the eval workbench. */
    @GetMapping("/eval/workbench/trace-sets/{traceSetId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalWorkbenchTraceSetDetailResponse>> evalWorkbenchTraceSetDetail(
        @PathVariable String traceSetId) {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchTraceSetDetailResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return evalWorkbenchTraceSetDetailService.detail(traceSetId)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Unknown Agent eval trace set: " + traceSetId)));
    }

    /** Compose a frontend-ready promotion workflow result for the eval workbench. */
    @PostMapping("/eval/workbench/trace-sets/{traceSetId}/promotion-workflow")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalWorkbenchPromotionWorkflowResponse>> evalWorkbenchPromotionWorkflow(
        @PathVariable String traceSetId,
        @RequestBody(required = false) AgentEvalTraceSetPromotionWorkflowRequest request) {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchPromotionWorkflowResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return evalWorkbenchPromotionWorkflowService.workflow(traceSetId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Unknown Agent eval trace set: " + traceSetId)));
    }

    /** Build a frontend-ready Git review model for a trace-set catalog patch proposal. */
    @PostMapping("/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalWorkbenchCatalogPatchReviewResponse>> evalWorkbenchCatalogPatchReview(
        @PathVariable String traceSetId,
        @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchCatalogPatchReviewResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return evalWorkbenchCatalogPatchReviewService.review(traceSetId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Unknown Agent eval trace set: " + traceSetId)));
    }

    /** List versioned golden/red-team trace sets that bind curated evidence anchors to eval suites. */
    @GetMapping("/eval/trace-sets")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogResponse>> evalTraceSets() {
        ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(evalTraceSetCatalogService.catalog()));
    }

    /** Discover redacted recent trace candidates before running curation review. */
    @GetMapping("/eval/trace-sets/{traceSetId}/candidates")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalTraceSetCandidateDiscoveryResponse>> evalTraceSetCandidates(
        @PathVariable String traceSetId,
        @RequestParam(defaultValue = "50") int limit) {
        ResponseEntity<ApiResponse<AgentEvalTraceSetCandidateDiscoveryResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return traceSetCandidateDiscoveryService.discover(traceSetId, limit)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Unknown Agent eval trace set: " + traceSetId)));
    }

    /** Run the suite attached to a trace set; empty trace sets fail closed until real captures are curated. */
    @PostMapping("/eval/trace-sets/{traceSetId}/gate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalTraceSetGateArtifact>> evalTraceSetGate(@PathVariable String traceSetId,
                                                                                       @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalTraceSetGateArtifact>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return evalTraceSetCatalogService.gate(traceSetId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Unknown Agent eval trace set: " + traceSetId)));
    }

    /** Review candidate trace IDs before a human/git catalog patch promotes them into a trace set. */
    @PostMapping("/eval/trace-sets/{traceSetId}/curation-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalTraceSetCurationReviewArtifact>> evalTraceSetCurationReview(
        @PathVariable String traceSetId,
        @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalTraceSetCurationReviewArtifact>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return evalTraceSetCatalogService.curationReview(traceSetId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Unknown Agent eval trace set: " + traceSetId)));
    }

    /** Produce a review-only JSON Patch proposal for a Git catalog update. */
    @PostMapping("/eval/trace-sets/{traceSetId}/catalog-patch-proposal")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogPatchProposalArtifact>> evalTraceSetCatalogPatchProposal(
        @PathVariable String traceSetId,
        @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogPatchProposalArtifact>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return evalTraceSetCatalogService.catalogPatchProposal(traceSetId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Unknown Agent eval trace set: " + traceSetId)));
    }

    /** Compose discovery, curation review, and patch proposal for a future eval workbench. */
    @PostMapping("/eval/trace-sets/{traceSetId}/promotion-workflow")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalTraceSetPromotionWorkflowArtifact>> evalTraceSetPromotionWorkflow(
        @PathVariable String traceSetId,
        @RequestBody(required = false) AgentEvalTraceSetPromotionWorkflowRequest request) {
        ResponseEntity<ApiResponse<AgentEvalTraceSetPromotionWorkflowArtifact>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return traceSetPromotionWorkflowService.workflow(traceSetId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Unknown Agent eval trace set: " + traceSetId)));
    }

    /** Produce a compact CI/release-gate bundle for every versioned trace set. */
    @PostMapping("/eval/trace-sets/gate-bundle")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalTraceSetGateBundleArtifact>> evalTraceSetGateBundle(
        @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalTraceSetGateBundleArtifact>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(evalTraceSetCatalogService.gateBundle(request)));
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
