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
 * <p>中文说明：这个 Controller 是顶级 Agent 的 admin-only 控制面入口，负责把 Memory/RAG、
 * durable audit、replay、eval、kube-manager outlet 和前端 workbench 的只读证据组织成稳定 API。
 * 它本身不执行 Tool、不调用 MCP、不访问 kube-manager、不调用 LLM，也不改变 prompt。</p>
 *
 * <p>安全边界：这里暴露的是诊断/治理/学习材料，不是运行时授权面。所有入口都要经过管理员校验；
 * eval、replay、RAG contract 和 release gate contract 只说明证据状态，不授予 release authority，
 * 也不能绕过 SafeToolExecutor、HITL、审计预写或外部系统权限。</p>
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
    private final AgentAdvancedTechnologyAdoptionContractService advancedTechnologyAdoptionContractService;
    private final AgentAdvancedTechnologyCompatibilityMatrixService advancedTechnologyCompatibilityMatrixService;
    private final AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService advancedTechnologyCompatibilityMatrixVueBindingSpecService;
    private final AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService advancedTechnologyCompatibilityMatrixEvidenceReadinessService;
    private final AgentBackendTechnologyModernizationDecisionService backendTechnologyModernizationDecisionService;
    private final AgentTopTierTechnologyIntroductionPlaybookService topTierTechnologyIntroductionPlaybookService;
    private final AgentMultiAgentReviewService multiAgentReviewService;
    private final AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService;
    private final AgentOfficialVersionProtocolWatchDashboardService officialVersionProtocolWatchDashboardService;
    private final AgentOfficialVersionProtocolWatchVueBindingSpecService officialVersionProtocolWatchVueBindingSpecService;
    private final AgentTopTierVueWorkbenchImplementationPackageService topTierVueWorkbenchImplementationPackageService;
    private final AgentTopTierVueWorkbenchAcceptanceContractService topTierVueWorkbenchAcceptanceContractService;
    private final AgentTopTierVueWorkbenchMigrationPackageService topTierVueWorkbenchMigrationPackageService;
    private final AgentPhase1ExecutionRoadmapService phase1ExecutionRoadmapService;
    private final AgentVueReadinessControlPlaneService vueReadinessControlPlaneService;
    private final AgentMemoryRagReadinessService memoryRagReadinessService;
    private final AgentMemoryRagCitationSourceContractService memoryRagCitationSourceContractService;
    private final AgentMemoryRagSourceEvidenceDigestContractService memoryRagSourceEvidenceDigestContractService;
    private final AgentMemoryRagDurableMemoryLifecycleContractService memoryRagDurableMemoryLifecycleContractService;
    private final AgentMemoryRagEvalGateContractService memoryRagEvalGateContractService;
    private final AgentMemoryRagEvalSuiteBindingContractService memoryRagEvalSuiteBindingContractService;
    private final AgentMemoryRagTraceSetCurationContractService memoryRagTraceSetCurationContractService;
    private final AgentMemoryRagTraceSetCurationWorkbenchOverviewService memoryRagTraceSetCurationWorkbenchOverviewService;
    private final AgentMemoryRagReviewedTraceEvidenceManifestService memoryRagReviewedTraceEvidenceManifestService;
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
    private final AgentReviewedEvalTraceEvidenceService reviewedEvalTraceEvidenceService;
    private final AgentReviewedTraceFixtureIntakeContractService reviewedTraceFixtureIntakeContractService;
    private final AgentReviewedTraceFixtureManifestService reviewedTraceFixtureManifestService;
    private final AgentReviewedTraceFixtureTemplateService reviewedTraceFixtureTemplateService;
    private final AgentReviewedTraceFixtureCandidateService reviewedTraceFixtureCandidateService;
    private final AgentReleaseBlockingEvalGateContractService releaseBlockingEvalGateContractService;
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
                                   AgentAdvancedTechnologyAdoptionContractService advancedTechnologyAdoptionContractService,
                                   AgentAdvancedTechnologyCompatibilityMatrixService advancedTechnologyCompatibilityMatrixService,
                                   AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService advancedTechnologyCompatibilityMatrixVueBindingSpecService,
                                   AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessService advancedTechnologyCompatibilityMatrixEvidenceReadinessService,
                                   AgentBackendTechnologyModernizationDecisionService backendTechnologyModernizationDecisionService,
                                   AgentTopTierTechnologyIntroductionPlaybookService topTierTechnologyIntroductionPlaybookService,
                                   AgentMultiAgentReviewService multiAgentReviewService,
                                    AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService,
                                    AgentOfficialVersionProtocolWatchDashboardService officialVersionProtocolWatchDashboardService,
                                    AgentOfficialVersionProtocolWatchVueBindingSpecService officialVersionProtocolWatchVueBindingSpecService,
                                    AgentTopTierVueWorkbenchImplementationPackageService topTierVueWorkbenchImplementationPackageService,
                                    AgentTopTierVueWorkbenchAcceptanceContractService topTierVueWorkbenchAcceptanceContractService,
                                    AgentTopTierVueWorkbenchMigrationPackageService topTierVueWorkbenchMigrationPackageService,
                                    AgentPhase1ExecutionRoadmapService phase1ExecutionRoadmapService,
                                   AgentVueReadinessControlPlaneService vueReadinessControlPlaneService,
                                   AgentMemoryRagReadinessService memoryRagReadinessService,
                                   AgentMemoryRagCitationSourceContractService memoryRagCitationSourceContractService,
                                   AgentMemoryRagSourceEvidenceDigestContractService memoryRagSourceEvidenceDigestContractService,
                                   AgentMemoryRagDurableMemoryLifecycleContractService memoryRagDurableMemoryLifecycleContractService,
                                   AgentMemoryRagEvalGateContractService memoryRagEvalGateContractService,
                                   AgentMemoryRagEvalSuiteBindingContractService memoryRagEvalSuiteBindingContractService,
                                   AgentMemoryRagTraceSetCurationContractService memoryRagTraceSetCurationContractService,
                                   AgentMemoryRagTraceSetCurationWorkbenchOverviewService memoryRagTraceSetCurationWorkbenchOverviewService,
                                   AgentMemoryRagReviewedTraceEvidenceManifestService memoryRagReviewedTraceEvidenceManifestService,
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
                                   AgentReviewedEvalTraceEvidenceService reviewedEvalTraceEvidenceService,
                                   AgentReviewedTraceFixtureIntakeContractService reviewedTraceFixtureIntakeContractService,
                                   AgentReviewedTraceFixtureManifestService reviewedTraceFixtureManifestService,
                                   AgentReviewedTraceFixtureTemplateService reviewedTraceFixtureTemplateService,
                                   AgentReviewedTraceFixtureCandidateService reviewedTraceFixtureCandidateService,
                                   AgentReleaseBlockingEvalGateContractService releaseBlockingEvalGateContractService,
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
        this.advancedTechnologyAdoptionContractService = advancedTechnologyAdoptionContractService;
        this.advancedTechnologyCompatibilityMatrixService = advancedTechnologyCompatibilityMatrixService;
        this.advancedTechnologyCompatibilityMatrixVueBindingSpecService = advancedTechnologyCompatibilityMatrixVueBindingSpecService;
        this.advancedTechnologyCompatibilityMatrixEvidenceReadinessService = advancedTechnologyCompatibilityMatrixEvidenceReadinessService;
        this.backendTechnologyModernizationDecisionService = backendTechnologyModernizationDecisionService;
        this.topTierTechnologyIntroductionPlaybookService = topTierTechnologyIntroductionPlaybookService;
        this.multiAgentReviewService = multiAgentReviewService;
        this.officialVersionProtocolWatchService = officialVersionProtocolWatchService;
        this.officialVersionProtocolWatchDashboardService = officialVersionProtocolWatchDashboardService;
        this.officialVersionProtocolWatchVueBindingSpecService = officialVersionProtocolWatchVueBindingSpecService;
        this.topTierVueWorkbenchImplementationPackageService = topTierVueWorkbenchImplementationPackageService;
        this.topTierVueWorkbenchAcceptanceContractService = topTierVueWorkbenchAcceptanceContractService;
        this.topTierVueWorkbenchMigrationPackageService = topTierVueWorkbenchMigrationPackageService;
        this.phase1ExecutionRoadmapService = phase1ExecutionRoadmapService;
        this.vueReadinessControlPlaneService = vueReadinessControlPlaneService;
        this.memoryRagReadinessService = memoryRagReadinessService;
        this.memoryRagCitationSourceContractService = memoryRagCitationSourceContractService;
        this.memoryRagSourceEvidenceDigestContractService = memoryRagSourceEvidenceDigestContractService;
        this.memoryRagDurableMemoryLifecycleContractService = memoryRagDurableMemoryLifecycleContractService;
        this.memoryRagEvalGateContractService = memoryRagEvalGateContractService;
        this.memoryRagEvalSuiteBindingContractService = memoryRagEvalSuiteBindingContractService;
        this.memoryRagTraceSetCurationContractService = memoryRagTraceSetCurationContractService;
        this.memoryRagTraceSetCurationWorkbenchOverviewService = memoryRagTraceSetCurationWorkbenchOverviewService;
        this.memoryRagReviewedTraceEvidenceManifestService = memoryRagReviewedTraceEvidenceManifestService;
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
        this.reviewedEvalTraceEvidenceService = reviewedEvalTraceEvidenceService;
        this.reviewedTraceFixtureIntakeContractService = reviewedTraceFixtureIntakeContractService;
        this.reviewedTraceFixtureManifestService = reviewedTraceFixtureManifestService;
        this.reviewedTraceFixtureTemplateService = reviewedTraceFixtureTemplateService;
        this.reviewedTraceFixtureCandidateService = reviewedTraceFixtureCandidateService;
        this.releaseBlockingEvalGateContractService = releaseBlockingEvalGateContractService;
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

    /** Describe how Phase 1 adopts advanced Agent technologies without binding new runtimes. */
    @GetMapping("/top-tier/advanced-technology-adoption-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentAdvancedTechnologyAdoptionContractResponse>> advancedTechnologyAdoptionContract() {
        ResponseEntity<ApiResponse<AgentAdvancedTechnologyAdoptionContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(advancedTechnologyAdoptionContractService.contract()));
    }

    /** Publish the advanced technology compatibility matrix without changing dependencies or runtimes. */
    @GetMapping("/top-tier/advanced-technology-compatibility-matrix")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixResponse>> advancedTechnologyCompatibilityMatrix() {
        ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(advancedTechnologyCompatibilityMatrixService.matrix()));
    }

    /** Publish the Vue binding spec for the advanced technology compatibility matrix. */
    @GetMapping("/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse>> advancedTechnologyCompatibilityMatrixVueBindingSpec() {
        ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(advancedTechnologyCompatibilityMatrixVueBindingSpecService.spec()));
    }

    /** Publish evidence readiness for each advanced technology matrix lane without running gates or upgrades. */
    @GetMapping("/top-tier/advanced-technology-compatibility-matrix/evidence-readiness")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse>> advancedTechnologyCompatibilityMatrixEvidenceReadiness() {
        ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(advancedTechnologyCompatibilityMatrixEvidenceReadinessService.readiness()));
    }

    /** Publish the Java/Spring backend modernization decision without changing dependencies or runtimes. */
    @GetMapping("/top-tier/backend-technology-modernization-decision")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentBackendTechnologyModernizationDecisionResponse>> backendTechnologyModernizationDecision() {
        ResponseEntity<ApiResponse<AgentBackendTechnologyModernizationDecisionResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(backendTechnologyModernizationDecisionService.decision()));
    }

    /** Publish the top-tier latest-technology introduction playbook without runtime controls. */
    @GetMapping("/top-tier/technology-introduction-playbook")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentTopTierTechnologyIntroductionPlaybookResponse>> topTierTechnologyIntroductionPlaybook() {
        ResponseEntity<ApiResponse<AgentTopTierTechnologyIntroductionPlaybookResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(topTierTechnologyIntroductionPlaybookService.playbook()));
    }

    /**
     * 发布 Multi-Agent / Expert Review 聚合读模型。
     *
     * <p>中文说明：这个接口只给前端展示“多专家审阅证据”和“A2A/handoff 仍然关闭的原因”。
     * 它不提供执行按钮，不打开 A2A runtime handoff，不调用 MCP tools/call，也不触发 kube-manager 写操作。</p>
     */
    @GetMapping("/top-tier/multi-agent-review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMultiAgentReviewResponse>> multiAgentReview() {
        ResponseEntity<ApiResponse<AgentMultiAgentReviewResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(multiAgentReviewService.review()));
    }

    /** Publish the official version/protocol watch without external calls or runtime upgrades. */
    @GetMapping("/top-tier/official-version-protocol-watch")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchResponse>> officialVersionProtocolWatch() {
        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(officialVersionProtocolWatchService.watch()));
    }

    /** Build a Vue-ready dashboard for the official version/protocol watch without runtime controls. */
    @GetMapping("/top-tier/official-version-protocol-watch/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchDashboardResponse>> officialVersionProtocolWatchDashboard() {
        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchDashboardResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(officialVersionProtocolWatchDashboardService.dashboard()));
    }

    /** Publish the Vue component/field binding spec for the official watch dashboard. */
    @GetMapping("/top-tier/official-version-protocol-watch/vue-binding-spec")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchVueBindingSpecResponse>> officialVersionProtocolWatchVueBindingSpec() {
        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchVueBindingSpecResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(officialVersionProtocolWatchVueBindingSpecService.spec()));
    }

    /** Publish the Vue implementation package for the top-tier latest-technology workbench. */
    @GetMapping("/top-tier/vue-workbench-implementation-package")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentTopTierVueWorkbenchImplementationPackageResponse>> topTierVueWorkbenchImplementationPackage() {
        ResponseEntity<ApiResponse<AgentTopTierVueWorkbenchImplementationPackageResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(topTierVueWorkbenchImplementationPackageService.implementationPackage()));
    }

    /** Publish Vue 2 / Element UI acceptance fixtures and absence assertions for the top-tier workbench. */
    @GetMapping("/top-tier/vue-workbench-acceptance-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentTopTierVueWorkbenchAcceptanceContractResponse>> topTierVueWorkbenchAcceptanceContract() {
        ResponseEntity<ApiResponse<AgentTopTierVueWorkbenchAcceptanceContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(topTierVueWorkbenchAcceptanceContractService.contract()));
    }

    /** Publish the dry-run migration package for applying the workbench to vue-kube-manager. */
    @GetMapping("/top-tier/vue-workbench-migration-package")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentTopTierVueWorkbenchMigrationPackageResponse>> topTierVueWorkbenchMigrationPackage() {
        ResponseEntity<ApiResponse<AgentTopTierVueWorkbenchMigrationPackageResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(topTierVueWorkbenchMigrationPackageService.migrationPackage()));
    }

    /** Publish the Phase 1 execution order as a read-only roadmap contract. */
    @GetMapping("/top-tier/phase1-execution-roadmap")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentPhase1ExecutionRoadmapResponse>> phase1ExecutionRoadmap() {
        ResponseEntity<ApiResponse<AgentPhase1ExecutionRoadmapResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(phase1ExecutionRoadmapService.roadmap()));
    }

    /** Publish the Vue readiness control-plane binding contract without adding runtime controls. */
    @GetMapping("/top-tier/vue-readiness-control-plane")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentVueReadinessControlPlaneResponse>> vueReadinessControlPlane() {
        ResponseEntity<ApiResponse<AgentVueReadinessControlPlaneResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(vueReadinessControlPlaneService.controlPlane()));
    }

    /**
     * 构建 Memory/RAG 学习层就绪契约。
     *
     * <p>中文说明：admin-only，只读取摘要记忆 store 的有限统计和合同状态；不执行检索、
     * 不调用向量库、不调用 LLM/Tool/MCP/kube-manager，也不把摘要记忆变成 prompt 权威。</p>
     */
    @GetMapping("/memory-rag/readiness")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagReadinessResponse>> memoryRagReadiness() {
        ResponseEntity<ApiResponse<AgentMemoryRagReadinessResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagReadinessService.readiness()));
    }

    /**
     * 构建 Memory/RAG 引用与来源契约。
     *
     * <p>安全边界：这是只读合同视图，不执行检索、不改变 prompt、不写 memory，
     * 不调用向量库、LLM、Tool、MCP 或 kube-manager。</p>
     */
    @GetMapping("/memory-rag/citation-source-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagCitationSourceContractResponse>> memoryRagCitationSourceContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagCitationSourceContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagCitationSourceContractService.contract()));
    }

    /**
     * 构建 Memory/RAG 来源证据摘要合同。
     *
     * <p>中文说明：admin-only，只对 synthetic sample 做本地 SHA-256 digest，用来证明来源、
     * 片段、租户、ACL、脱敏和保留策略的证据形状稳定。它不执行摄取、不执行检索、
     * 不调用向量库、LLM、Tool、MCP 或 kube-manager，也不改变 prompt。</p>
     */
    @GetMapping("/memory-rag/source-evidence-digest-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagSourceEvidenceDigestContractResponse>> memoryRagSourceEvidenceDigestContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagSourceEvidenceDigestContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagSourceEvidenceDigestContractService.contract()));
    }

    /**
     * 描述 durable Memory/RAG 生命周期合同。
     *
     * <p>中文说明：这是未来持久记忆上线前的只读治理材料，不绑定持久化运行时，
     * 不写 memory、不导出原文、不授予跨租户复用能力。</p>
     */
    @GetMapping("/memory-rag/durable-memory-lifecycle-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagDurableMemoryLifecycleContractResponse>> memoryRagDurableMemoryLifecycleContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagDurableMemoryLifecycleContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagDurableMemoryLifecycleContractService.contract()));
    }

    /**
     * 描述 Memory/RAG eval gate 合同。
     *
     * <p>安全边界：当前只发布合同状态，不运行 eval、不执行检索、不允许检索证据影响 prompt；
     * 它也不是 release authority。</p>
     */
    @GetMapping("/memory-rag/eval-gate-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagEvalGateContractResponse>> memoryRagEvalGateContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagEvalGateContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagEvalGateContractService.contract()));
    }

    /**
     * 描述 Memory/RAG eval-suite 绑定合同。
     *
     * <p>中文说明：只说明未来 trace set、eval suite 与 RAG 证据之间如何绑定，
     * 不启动 eval runtime，也不绑定 retrieval runtime。</p>
     */
    @GetMapping("/memory-rag/eval-suite-binding-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagEvalSuiteBindingContractResponse>> memoryRagEvalSuiteBindingContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagEvalSuiteBindingContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagEvalSuiteBindingContractService.contract()));
    }

    /**
     * 描述 Memory/RAG trace-set 策展缺口。
     *
     * <p>安全边界：只输出 reviewed trace 晋升前的缺口，不接收任意 trace 作为可信 RAG 证据。</p>
     */
    @GetMapping("/memory-rag/trace-set-curation-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationContractResponse>> memoryRagTraceSetCurationContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagTraceSetCurationContractService.contract()));
    }

    /**
     * 构建 Vue 可渲染的 Memory/RAG trace-set 策展工作台总览。
     *
     * <p>中文说明：只读前端视图，不执行运行时动作，不修改 catalog，不写 memory。</p>
     */
    @GetMapping("/memory-rag/workbench/trace-set-curation/overview")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse>> memoryRagTraceSetCurationWorkbenchOverview() {
        ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagTraceSetCurationWorkbenchOverviewService.overview()));
    }

    /**
     * 发布 Memory/RAG reviewed trace-evidence 接入清单。
     *
     * <p>安全边界：该入口不接受调用方 traceId，不提升 trace set，也不把候选 evidence 写入 prompt。</p>
     */
    @GetMapping("/memory-rag/workbench/trace-set-curation/review-manifest")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentMemoryRagReviewedTraceEvidenceManifestResponse>> memoryRagReviewedTraceEvidenceManifest() {
        ResponseEntity<ApiResponse<AgentMemoryRagReviewedTraceEvidenceManifestResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(memoryRagReviewedTraceEvidenceManifestService.manifest()));
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

    /**
     * 查询 Agent 观测快照。
     *
     * <p>中文说明：admin-only，只聚合 metrics 和 audit 的脱敏摘要；不执行 Tool、不重放请求、
     * 不访问 kube-manager，也不暴露原始参数值。</p>
     */
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

    /**
     * 查询脱敏审计索引元信息。
     *
     * <p>中文说明：admin-only，只返回 durable audit/read model 的能力与限制，
     * 包括是否 redacted-only、扫描上限和 retention/export 元数据。</p>
     */
    @GetMapping("/audit/index")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> auditIndex() {
        ResponseEntity<ApiResponse<Map<String, Object>>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(auditQueryService.indexMetadata()));
    }

    /**
     * 按 auditId 查询单条脱敏审计事件。
     *
     * <p>安全边界：只读 redacted audit，不恢复 raw principal、raw reason、raw endpoint
     * 或原始参数值，也不触发重新执行。</p>
     */
    @GetMapping("/audit/id/{auditId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentAuditQueryResponse>> auditByAuditId(@PathVariable String auditId) {
        ResponseEntity<ApiResponse<AgentAuditQueryResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(auditQueryService.findByAuditId(auditId)));
    }

    /**
     * 按 traceId 查询脱敏审计时间线。
     *
     * <p>中文说明：这是 admin-only 诊断读接口，给 replay/eval 提供证据，不代表 Tool 执行授权。</p>
     */
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

    /**
     * 按 traceId 查询前端可回放的脱敏 Agent timeline。
     *
     * <p>安全边界：replay 只是只读证据时间线，不重新执行 Tool/MCP/kube-manager，
     * 不恢复原始 prompt、reason 或参数值，也不改变运行时状态。</p>
     */
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

    /**
     * 对单条 trace 的脱敏 replay evidence 做确定性评测。
     *
     * <p>中文说明：eval 只读 redacted replay evidence，不调用 LLM、Tool、MCP 或 kube-manager；
     * 结果用于学习、回归和治理，不参与 Tool 放行，也不授予 release authority。</p>
     */
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

    /**
     * 基于脱敏 replay evidence 评测一组 trace。
     *
     * <p>安全边界：这里的 release-gate style 只是确定性报告形状，不等于真实发布授权。
     * 它不执行外部调用、不修改 catalog、不写审计，也不授予 release authority。</p>
     */
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

    /**
     * 列出内置确定性 eval suite。
     *
     * <p>中文说明：这是只读目录视图，CI 或前端可以据此知道有哪些套件，但不会自动运行或放行发布。</p>
     */
    @GetMapping("/eval/suites")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalSuiteCatalogResponse>> evalSuites() {
        ResponseEntity<ApiResponse<AgentEvalSuiteCatalogResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(evalSuiteCatalogService.catalog()));
    }

    /**
     * 运行已开放的命名确定性 eval suite。
     *
     * <p>安全边界：仅允许 runtimeExecutionAllowed 的 suite 运行；未开放的 suite fail-closed。
     * 输入 trace 只是脱敏证据锚点，不是 prompt 权威，也不是 release authority。</p>
     */
    @PostMapping("/eval/suites/{suiteId}/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalSuiteRunResponse>> runEvalSuite(@PathVariable String suiteId,
                                                                               @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalSuiteRunResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        if (evalSuiteCatalogService.findDefinition(suiteId).isPresent()
            && !evalSuiteCatalogService.runtimeExecutionAllowed(suiteId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("Agent eval suite 当前仅用于目录/绑定契约，尚未开放运行: " + suiteId));
        }
        return evalSuiteCatalogService.run(suiteId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("未知的 Agent eval suite: " + suiteId)));
    }

    /**
     * 生成紧凑的 CI/release-gate artifact。
     *
     * <p>中文说明：artifact 只承载评测摘要，不内嵌 replay 原文或逐 trace 报告；它是治理证据，
     * 不是自动发布按钮。</p>
     */
    @PostMapping("/eval/suites/{suiteId}/gate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentEvalSuiteGateArtifact>> evalSuiteGate(@PathVariable String suiteId,
                                                                                 @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentEvalSuiteGateArtifact>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        if (evalSuiteCatalogService.findDefinition(suiteId).isPresent()
            && !evalSuiteCatalogService.runtimeExecutionAllowed(suiteId)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("Agent eval suite 当前仅用于目录/绑定契约，尚未开放 gate artifact: " + suiteId));
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

    /**
     * 发布 reviewed eval trace evidence 就绪状态。
     *
     * <p>安全边界：只读 catalog 派生视图，不修改 catalog、不运行 eval、不提升 trace set，
     * 也不授予 release authority。</p>
     */
    @GetMapping("/eval/reviewed-trace-evidence")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentReviewedEvalTraceEvidenceResponse>> reviewedEvalTraceEvidence() {
        ResponseEntity<ApiResponse<AgentReviewedEvalTraceEvidenceResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(reviewedEvalTraceEvidenceService.evidence()));
    }

    /**
     * 发布 reviewed redacted trace fixture 接入合同。
     *
     * <p>中文说明：该入口只告诉前端和学习者 fixture 在进入人审/Git review/目录晋升前
     * 必须携带哪些字段和证明；它不接收上传、不接收调用方 traceId，也不写 `eval-trace-sets.json`。</p>
     *
     * <p>安全边界：只读合同，不运行 eval/replay，不调用 Tool/MCP/LLM/RAG/kube-manager，
     * 不创建 HITL/audit/memory，也不打开 CI blocking 或 release authority。</p>
     */
    @GetMapping("/eval/reviewed-trace-fixture-intake-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentReviewedTraceFixtureIntakeContractResponse>> reviewedTraceFixtureIntakeContract() {
        ResponseEntity<ApiResponse<AgentReviewedTraceFixtureIntakeContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(reviewedTraceFixtureIntakeContractService.contract()));
    }

    /**
     * 发布 reviewed redacted trace fixture 仓库 manifest。
     *
     * <p>中文说明：该入口只扫描 classpath 中已经随 Git 提交的 fixture JSON 文件，
     * 汇总 trace set 覆盖缺口，帮助人审知道下一步该补哪些 repo-native fixture。</p>
     *
     * <p>安全边界：只读 manifest，不接收上传、不接收调用方 traceId、不写 `eval-trace-sets.json`，
     * 不运行 eval/replay，不调用 Tool/MCP/LLM/RAG/kube-manager，不创建 HITL/audit/memory，
     * 也不打开 CI blocking 或 release authority。</p>
     */
    @GetMapping("/eval/reviewed-trace-fixture-manifest")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentReviewedTraceFixtureManifestResponse>> reviewedTraceFixtureManifest() {
        ResponseEntity<ApiResponse<AgentReviewedTraceFixtureManifestResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(reviewedTraceFixtureManifestService.manifest()));
    }

    /**
     * 发布 reviewed redacted trace fixture 作者模板和 schema。
     *
     * <p>中文说明：该入口给前端和人审者展示真实 fixture 入仓前应填写的 JSON 结构、命名规则和
     * trace set 待补模板行；它只帮助准备人工 Git review，不创建 fixture 文件。</p>
     *
     * <p>安全边界：只读 template/schema，不接收上传、不接收调用方 traceId、不写 `eval-trace-sets.json`，
     * 不运行 eval/replay，不调用 Tool/MCP/LLM/RAG/kube-manager，不创建 HITL/audit/memory，
     * 也不打开 CI blocking 或 release authority。</p>
     */
    @GetMapping("/eval/reviewed-trace-fixture-template")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentReviewedTraceFixtureTemplateResponse>> reviewedTraceFixtureTemplate() {
        ResponseEntity<ApiResponse<AgentReviewedTraceFixtureTemplateResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(reviewedTraceFixtureTemplateService.template()));
    }

    /**
     * 发布 reviewed redacted trace fixture 候选证据预检包。
     *
     * <p>中文说明：该入口把管理员提交的候选 traceId 与 redacted replay / deterministic eval 读模型合并，
     * 生成“可以带去人审/Git review 的 fixture 草稿和缺口”。它不会创建 fixture 文件，也不会把 caller traceId
     * 直接登记为 reviewed evidence。</p>
     *
     * <p>安全边界：只读 preview，不上传 fixture、不写 `eval-trace-sets.json`，不运行 Tool/MCP/LLM/RAG/kube-manager，
     * 不写 HITL/audit/memory，也不打开 CI blocking 或 release authority。</p>
     */
    @PostMapping("/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentReviewedTraceFixtureCandidateResponse>> reviewedTraceFixtureCandidate(
        @PathVariable String traceSetId,
        @RequestBody(required = false) AgentEvalSuiteRequest request) {
        ResponseEntity<ApiResponse<AgentReviewedTraceFixtureCandidateResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return reviewedTraceFixtureCandidateService.candidate(traceSetId, request)
            .map(response -> ResponseEntity.ok(ApiResponse.ok(response)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("Unknown Agent eval trace set: " + traceSetId)));
    }

    /**
     * 发布 release-blocking eval gate 合同状态。
     *
     * <p>中文说明：这里只说明未来阻断式门禁需要哪些证据，不启用 CI blocking，
     * 不改变发布流水线，也不授予 release authority。</p>
     */
    @GetMapping("/eval/release-blocking-gate-contract")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYS_ADMIN')")
    public ResponseEntity<ApiResponse<AgentReleaseBlockingEvalGateContractResponse>> releaseBlockingEvalGateContract() {
        ResponseEntity<ApiResponse<AgentReleaseBlockingEvalGateContractResponse>> guard = requireAdmin();
        if (guard != null) {
            return guard;
        }
        return ResponseEntity.ok(ApiResponse.ok(releaseBlockingEvalGateContractService.contract()));
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

    /**
     * 统一管理员守卫。
     *
     * <p>中文说明：这些观测 API 都是 admin-only，因为它们可能暴露审计数量、工具名、
     * traceId、治理缺口和评测状态。守卫只读取服务端 Principal，不信任请求体里的身份字段。</p>
     */
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
