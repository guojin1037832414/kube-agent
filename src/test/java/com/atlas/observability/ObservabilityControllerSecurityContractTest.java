package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Observability 诊断入口的源码级安全契约。
 *
 * <p>FilterChain 是第一道 URL 级防线，方法级授权是第二道防线。这个测试防止未来重构
 * Controller 或 Security matcher 时，审计快照只剩路由层保护。</p>
 */
class ObservabilityControllerSecurityContractTest {

    private static final Path SOURCE = Path.of(
        "src/main/java/com/atlas/observability/ObservabilityController.java"
    );

    @Test
    void snapshotShouldKeepMethodLevelAdminGuard() throws Exception {
        String source = Files.readString(SOURCE);

        assertThat(source).contains("import org.springframework.security.access.prepost.PreAuthorize;");
        assertThat(source).contains("@PreAuthorize(\"hasAnyRole('ADMIN', 'SYS_ADMIN')\")");
        assertThat(source).contains("public ResponseEntity<ApiResponse<Map<String, Object>>> snapshot()");
    }

    @Test
    void auditQueryMethodsShouldKeepMethodLevelAdminGuard() throws Exception {
        String source = Files.readString(SOURCE);

        assertThat(source).contains("@GetMapping(\"/audit/index\")");
        assertThat(source).contains("@GetMapping(\"/audit/id/{auditId}\")");
        assertThat(source).contains("@GetMapping(\"/audit/trace/{traceId}\")");
        assertThat(source).contains("@GetMapping(\"/kube-manager/http-outlet/health-summary\")");
        assertThat(source).contains("@GetMapping(\"/kube-manager/http-outlet/write-retry-readiness\")");
        assertThat(source).contains("@GetMapping(\"/kube-manager/http-outlet/write-idempotency-contract\")");
        assertThat(source).contains("@GetMapping(\"/kube-manager/http-outlet/write-operation-safety-contract\")");
        assertThat(source).contains("@GetMapping(\"/kube-manager/http-outlet/write-retry-governance-contract\")");
        assertThat(source).contains("@GetMapping(\"/kube-manager/http-outlet/write-release-gate-contract\")");
        assertThat(source).contains("@GetMapping(\"/kube-manager/http-outlet/governance-workbench/overview\")");
        assertThat(source).contains("@GetMapping(\"/top-tier/readiness-overview\")");
        assertThat(source).contains("@GetMapping(\"/replay/trace/{traceId}\")");
        assertThat(source).contains("@GetMapping(\"/eval/trace/{traceId}\")");
        assertThat(source).contains("@PostMapping(\"/eval/suite\")");
        assertThat(source).contains("@GetMapping(\"/eval/suites\")");
        assertThat(source).contains("@PostMapping(\"/eval/suites/{suiteId}/run\")");
        assertThat(source).contains("@PostMapping(\"/eval/suites/{suiteId}/gate\")");
        assertThat(source).contains("@GetMapping(\"/eval/workbench/capabilities\")");
        assertThat(source).contains("@GetMapping(\"/eval/workbench/overview\")");
        assertThat(source).contains("@GetMapping(\"/eval/workbench/gate-bundle-summary\")");
        assertThat(source).contains("@GetMapping(\"/eval/workbench/trace-sets/{traceSetId}\")");
        assertThat(source).contains("@PostMapping(\"/eval/workbench/trace-sets/{traceSetId}/promotion-workflow\")");
        assertThat(source).contains("@PostMapping(\"/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review\")");
        assertThat(source).contains("@GetMapping(\"/eval/trace-sets\")");
        assertThat(source).contains("@GetMapping(\"/eval/trace-sets/{traceSetId}/candidates\")");
        assertThat(source).contains("@PostMapping(\"/eval/trace-sets/{traceSetId}/gate\")");
        assertThat(source).contains("@PostMapping(\"/eval/trace-sets/{traceSetId}/curation-review\")");
        assertThat(source).contains("@PostMapping(\"/eval/trace-sets/{traceSetId}/catalog-patch-proposal\")");
        assertThat(source).contains("@PostMapping(\"/eval/trace-sets/{traceSetId}/promotion-workflow\")");
        assertThat(source).contains("@PostMapping(\"/eval/trace-sets/gate-bundle\")");
        assertThat(source).contains("public ResponseEntity<ApiResponse<Map<String, Object>>> auditIndex()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentAuditQueryResponse>> auditByAuditId(@PathVariable String auditId)");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentAuditQueryResponse>> auditByTraceId(@PathVariable String traceId,");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletHealthSummaryResponse>> kubeManagerHttpOutletHealthSummary()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryReadinessResponse>> kubeManagerWriteRetryReadiness()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentKubeManagerWriteIdempotencyContractResponse>> kubeManagerWriteIdempotencyContract()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentKubeManagerWriteOperationSafetyContractResponse>> kubeManagerWriteOperationSafetyContract()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryGovernanceContractResponse>> kubeManagerWriteRetryGovernanceContract()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentKubeManagerWriteReleaseGateContractResponse>> kubeManagerWriteReleaseGateContract()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse>> kubeManagerHttpOutletGovernanceWorkbenchOverview()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentTopTierReadinessOverviewResponse>> topTierReadinessOverview()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentReplayTimelineResponse>> replayByTraceId(@PathVariable String traceId,");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalReportResponse>> evalByTraceId(@PathVariable String traceId,");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalSuiteResponse>> evalSuite(@RequestBody(required = false) AgentEvalSuiteRequest request)");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalSuiteCatalogResponse>> evalSuites()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalSuiteRunResponse>> runEvalSuite(@PathVariable String suiteId,");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalSuiteGateArtifact>> evalSuiteGate(@PathVariable String suiteId,");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalWorkbenchCapabilitiesResponse>> evalWorkbenchCapabilities()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalWorkbenchOverviewResponse>> evalWorkbenchOverview()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalWorkbenchGateBundleSummaryResponse>> evalWorkbenchGateBundleSummary()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalWorkbenchTraceSetDetailResponse>> evalWorkbenchTraceSetDetail(");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalWorkbenchPromotionWorkflowResponse>> evalWorkbenchPromotionWorkflow(");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalWorkbenchCatalogPatchReviewResponse>> evalWorkbenchCatalogPatchReview(");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogResponse>> evalTraceSets()");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalTraceSetCandidateDiscoveryResponse>> evalTraceSetCandidates(");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalTraceSetGateArtifact>> evalTraceSetGate(@PathVariable String traceSetId,");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalTraceSetCurationReviewArtifact>> evalTraceSetCurationReview(");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogPatchProposalArtifact>> evalTraceSetCatalogPatchProposal(");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalTraceSetPromotionWorkflowArtifact>> evalTraceSetPromotionWorkflow(");
        assertThat(source).contains("public ResponseEntity<ApiResponse<AgentEvalTraceSetGateBundleArtifact>> evalTraceSetGateBundle(");
        assertThat(source.split("@PreAuthorize\\(\"hasAnyRole\\('ADMIN', 'SYS_ADMIN'\\)\"\\)", -1).length - 1)
            .isGreaterThanOrEqualTo(31);
    }
}
