package com.atlas.observability;

import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.ApiResponse;
import com.atlas.memory.ConversationSummaryMemoryStore;
import com.atlas.mcp.McpGovernanceOverviewService;
import com.atlas.mcp.McpToolManifestService;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.impl.DeployDeleteTool;
import com.atlas.tool.impl.MigConfigListTool;
import com.atlas.tool.impl.NodeQueryTool;
import com.atlas.tool.impl.UserQueryTool;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agent observability 诊断入口安全契约测试。
 */
class ObservabilityControllerTest {

    private final UserPermissionContext userPermissionContext = new UserPermissionContext();
    private final InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
    private final AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(auditRecorder);
    private final AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
    private final AgentEvalSuiteCatalogService evalSuiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
    private final AgentEvalTraceSetCatalogService evalTraceSetCatalogService =
        new AgentEvalTraceSetCatalogService(evalSuiteCatalogService, new com.fasterxml.jackson.databind.ObjectMapper());
    private final AgentEvalTraceSetCandidateDiscoveryService traceSetCandidateDiscoveryService =
        new AgentEvalTraceSetCandidateDiscoveryService(auditRecorder, evalTraceSetCatalogService);
    private final AgentEvalTraceSetPromotionWorkflowService traceSetPromotionWorkflowService =
        new AgentEvalTraceSetPromotionWorkflowService(traceSetCandidateDiscoveryService, evalTraceSetCatalogService);
    private final AgentEvalWorkbenchCapabilitiesService evalWorkbenchCapabilitiesService =
        new AgentEvalWorkbenchCapabilitiesService();
    private final AgentEvalWorkbenchOverviewService evalWorkbenchOverviewService =
        new AgentEvalWorkbenchOverviewService(evalWorkbenchCapabilitiesService, evalTraceSetCatalogService);
    private final AgentReviewedEvalTraceEvidenceService reviewedEvalTraceEvidenceService =
        new AgentReviewedEvalTraceEvidenceService(evalTraceSetCatalogService);
    private final AgentEvalWorkbenchTraceSetDetailService evalWorkbenchTraceSetDetailService =
        new AgentEvalWorkbenchTraceSetDetailService(evalTraceSetCatalogService);
    private final AgentEvalWorkbenchPromotionWorkflowService evalWorkbenchPromotionWorkflowService =
        new AgentEvalWorkbenchPromotionWorkflowService(evalTraceSetCatalogService, traceSetPromotionWorkflowService);
    private final AgentEvalWorkbenchCatalogPatchReviewService evalWorkbenchCatalogPatchReviewService =
        new AgentEvalWorkbenchCatalogPatchReviewService(evalTraceSetCatalogService);
    private final AgentEvalWorkbenchGateBundleSummaryService evalWorkbenchGateBundleSummaryService =
        new AgentEvalWorkbenchGateBundleSummaryService(evalTraceSetCatalogService);
    private final AgentReleaseBlockingEvalGateContractService releaseBlockingEvalGateContractService =
        new AgentReleaseBlockingEvalGateContractService(
            reviewedEvalTraceEvidenceService,
            evalWorkbenchGateBundleSummaryService
        );
    private final AgentKubeManagerHttpOutletHealthSummaryService kubeManagerHttpOutletHealthSummaryService =
        new AgentKubeManagerHttpOutletHealthSummaryService(
            retryRegistry(),
            circuitBreakerRegistry(),
            bulkheadRegistry(),
            new MockEnvironment()
                .withProperty("atlas.backend.base-url", "http://kube-manager.internal:8100")
                .withProperty("atlas.backend.connect-timeout-seconds", "10")
                .withProperty("atlas.backend.read-timeout-seconds", "30")
                .withProperty("atlas.backend.login-password", "secret-password")
        );
    private final AgentKubeManagerWriteRetryReadinessService kubeManagerWriteRetryReadinessService =
        new AgentKubeManagerWriteRetryReadinessService(retryRegistry());
    private final AgentKubeManagerWriteIdempotencyContractService kubeManagerWriteIdempotencyContractService =
        new AgentKubeManagerWriteIdempotencyContractService();
    private final AgentKubeManagerWriteOperationSafetyContractService kubeManagerWriteOperationSafetyContractService =
        new AgentKubeManagerWriteOperationSafetyContractService();
    private final AgentKubeManagerWriteRetryGovernanceContractService kubeManagerWriteRetryGovernanceContractService =
        new AgentKubeManagerWriteRetryGovernanceContractService();
    private final AgentKubeManagerWriteReleaseGateContractService kubeManagerWriteReleaseGateContractService =
        new AgentKubeManagerWriteReleaseGateContractService();
    private final AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService kubeManagerHttpOutletGovernanceWorkbenchOverviewService =
        new AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService(
            kubeManagerHttpOutletHealthSummaryService,
            kubeManagerWriteRetryReadinessService,
            kubeManagerWriteIdempotencyContractService,
            kubeManagerWriteOperationSafetyContractService,
            kubeManagerWriteRetryGovernanceContractService,
            kubeManagerWriteReleaseGateContractService
        );
    private final McpGovernanceOverviewService mcpGovernanceOverviewService =
        new McpGovernanceOverviewService(new McpToolManifestService(mcpToolRegistry()));
    private final ConversationSummaryMemoryStore memoryStore = new ConversationSummaryMemoryStore();
    private final AgentMemoryRagReadinessService memoryRagReadinessService =
        new AgentMemoryRagReadinessService(memoryStore);
    private final AgentMemoryRagCitationSourceContractService memoryRagCitationSourceContractService =
        new AgentMemoryRagCitationSourceContractService();
    private final AgentMemoryRagSourceEvidenceDigestContractService memoryRagSourceEvidenceDigestContractService =
        new AgentMemoryRagSourceEvidenceDigestContractService();
    private final AgentMemoryRagDurableMemoryLifecycleContractService memoryRagDurableMemoryLifecycleContractService =
        new AgentMemoryRagDurableMemoryLifecycleContractService();
    private final AgentMemoryRagEvalGateContractService memoryRagEvalGateContractService =
        new AgentMemoryRagEvalGateContractService();
    private final AgentMemoryRagEvalSuiteBindingContractService memoryRagEvalSuiteBindingContractService =
        new AgentMemoryRagEvalSuiteBindingContractService(
            memoryRagEvalGateContractService,
            evalSuiteCatalogService,
            evalTraceSetCatalogService
        );
    private final AgentMemoryRagTraceSetCurationContractService memoryRagTraceSetCurationContractService =
        new AgentMemoryRagTraceSetCurationContractService(evalTraceSetCatalogService, evalSuiteCatalogService);
    private final AgentMemoryRagTraceSetCurationWorkbenchOverviewService memoryRagTraceSetCurationWorkbenchOverviewService =
        new AgentMemoryRagTraceSetCurationWorkbenchOverviewService(
            memoryRagTraceSetCurationContractService,
            memoryRagEvalSuiteBindingContractService,
            memoryRagReadinessService
        );
    private final AgentMemoryRagReviewedTraceEvidenceManifestService memoryRagReviewedTraceEvidenceManifestService =
        new AgentMemoryRagReviewedTraceEvidenceManifestService(
            memoryRagTraceSetCurationContractService,
            memoryRagSourceEvidenceDigestContractService,
            memoryRagDurableMemoryLifecycleContractService,
            memoryRagEvalGateContractService,
            memoryRagEvalSuiteBindingContractService,
            memoryRagReadinessService
        );
    private final AgentTopTierReadinessOverviewService topTierReadinessOverviewService =
        new AgentTopTierReadinessOverviewService(
            kubeManagerHttpOutletGovernanceWorkbenchOverviewService,
            evalWorkbenchCapabilitiesService,
            mcpGovernanceOverviewService
        );
    private final AgentAdvancedTechnologyAdoptionContractService advancedTechnologyAdoptionContractService =
        new AgentAdvancedTechnologyAdoptionContractService();
    private final AgentOfficialVersionProtocolWatchService officialVersionProtocolWatchService =
        new AgentOfficialVersionProtocolWatchService();
    private final AgentAdvancedTechnologyCompatibilityMatrixService advancedTechnologyCompatibilityMatrixService =
        new AgentAdvancedTechnologyCompatibilityMatrixService(officialVersionProtocolWatchService);
    private final AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService advancedTechnologyCompatibilityMatrixVueBindingSpecService =
        new AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService(advancedTechnologyCompatibilityMatrixService);
    private final AgentOfficialVersionProtocolWatchDashboardService officialVersionProtocolWatchDashboardService =
        new AgentOfficialVersionProtocolWatchDashboardService(officialVersionProtocolWatchService);
    private final AgentOfficialVersionProtocolWatchVueBindingSpecService officialVersionProtocolWatchVueBindingSpecService =
        new AgentOfficialVersionProtocolWatchVueBindingSpecService(officialVersionProtocolWatchDashboardService);
    private final AgentTopTierVueWorkbenchImplementationPackageService topTierVueWorkbenchImplementationPackageService =
        new AgentTopTierVueWorkbenchImplementationPackageService(
            officialVersionProtocolWatchVueBindingSpecService,
            advancedTechnologyCompatibilityMatrixVueBindingSpecService
        );
    private final AgentPhase1ExecutionRoadmapService phase1ExecutionRoadmapService =
        new AgentPhase1ExecutionRoadmapService();
    private final AgentVueReadinessControlPlaneService vueReadinessControlPlaneService =
        new AgentVueReadinessControlPlaneService();
    private final ObservabilityController controller = new ObservabilityController(
        new AgentMetricsService(new SimpleMeterRegistry()),
        kubeManagerHttpOutletHealthSummaryService,
        kubeManagerWriteRetryReadinessService,
        kubeManagerWriteIdempotencyContractService,
        kubeManagerWriteOperationSafetyContractService,
        kubeManagerWriteRetryGovernanceContractService,
        kubeManagerWriteReleaseGateContractService,
        kubeManagerHttpOutletGovernanceWorkbenchOverviewService,
        topTierReadinessOverviewService,
        advancedTechnologyAdoptionContractService,
        advancedTechnologyCompatibilityMatrixService,
        advancedTechnologyCompatibilityMatrixVueBindingSpecService,
        officialVersionProtocolWatchService,
        officialVersionProtocolWatchDashboardService,
        officialVersionProtocolWatchVueBindingSpecService,
        topTierVueWorkbenchImplementationPackageService,
        phase1ExecutionRoadmapService,
        vueReadinessControlPlaneService,
        memoryRagReadinessService,
        memoryRagCitationSourceContractService,
        memoryRagSourceEvidenceDigestContractService,
        memoryRagDurableMemoryLifecycleContractService,
        memoryRagEvalGateContractService,
        memoryRagEvalSuiteBindingContractService,
        memoryRagTraceSetCurationContractService,
        memoryRagTraceSetCurationWorkbenchOverviewService,
        memoryRagReviewedTraceEvidenceManifestService,
        auditRecorder,
        auditRecorder,
        replayTimelineService,
        evalReportService,
        evalSuiteCatalogService,
        evalTraceSetCatalogService,
        traceSetCandidateDiscoveryService,
        traceSetPromotionWorkflowService,
        evalWorkbenchCapabilitiesService,
        evalWorkbenchOverviewService,
        reviewedEvalTraceEvidenceService,
        releaseBlockingEvalGateContractService,
        evalWorkbenchTraceSetDetailService,
        evalWorkbenchPromotionWorkflowService,
        evalWorkbenchCatalogPatchReviewService,
        evalWorkbenchGateBundleSummaryService,
        new AgentPrincipalResolver(userPermissionContext)
    );

    private RetryRegistry retryRegistry() {
        RetryRegistry registry = RetryRegistry.of(java.util.Map.of(
            "kubeManagerRead", RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(500)).build(),
            "kubeManagerWrite", RetryConfig.custom().maxAttempts(1).build()
        ));
        registry.retry("kubeManagerRead", "kubeManagerRead");
        registry.retry("kubeManagerWrite", "kubeManagerWrite");
        return registry;
    }

    private ToolRegistry mcpToolRegistry() {
        ToolRegistry registry = new ToolRegistry(java.util.List.of(
            new NodeQueryTool(null),
            new MigConfigListTool(null),
            new UserQueryTool(null),
            new DeployDeleteTool(null)
        ), new UserPermissionContext());
        registry.init();
        return registry;
    }

    private CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(java.util.Map.of(
            "kubeManager", CircuitBreakerConfig.custom()
                .slidingWindowSize(50)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .build()
        ));
        registry.circuitBreaker("kubeManager", "kubeManager");
        return registry;
    }

    private BulkheadRegistry bulkheadRegistry() {
        BulkheadRegistry registry = BulkheadRegistry.of(java.util.Map.of(
            "kubeManager", BulkheadConfig.custom()
                .maxConcurrentCalls(32)
                .maxWaitDuration(Duration.ofMillis(100))
                .build()
        ));
        registry.bulkhead("kubeManager", "kubeManager");
        return registry;
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        userPermissionContext.unbind();
    }

    @Test
    void snapshot_shouldRejectAnonymousUser() {
        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.snapshot();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void snapshot_shouldRejectNonAdminUser() {
        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.snapshot();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    @Test
    void snapshot_shouldAllowAdminUserFromSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.snapshot();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).containsKeys("metrics", "audit");
    }

    @Test
    void snapshot_shouldAllowLegacyAdminFallback() {
        userPermissionContext.onLogin("admin-token", "boss", "sys_admin", Set.of());
        userPermissionContext.bind("admin-token", "100002");

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.snapshot();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).containsKeys("metrics", "audit");
    }

    @Test
    void auditQuery_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<com.atlas.audit.AgentAuditQueryResponse>> anonymous =
            controller.auditByAuditId("aud_missing");

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<com.atlas.audit.AgentAuditQueryResponse>> user =
            controller.auditByTraceId("trc_missing", 10);

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void auditQuery_shouldReturnRedactedAdminResults() {
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_admin_query",
            java.time.Instant.EPOCH,
            "trc_admin_query",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.GRAPH_TOOL_CALL,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            null,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<com.atlas.audit.AgentAuditQueryResponse>> response =
            controller.auditByTraceId("trc_admin_query", 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        String bodyText = response.getBody().getData().toString();
        assertThat(bodyText)
            .contains("aud_admin_query", "trc_admin_query", "<protected>")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void auditIndex_shouldReturnMetadataForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<Map<String, Object>>> response = controller.auditIndex();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData())
            .containsEntry("backend", "in-memory-ring-buffer")
            .containsEntry("containsRawEndpoints", false);
    }

    @Test
    void kubeManagerHttpOutletHealthSummary_shouldRequireAdminAndReturnRedactedLocalPolicy() {
        ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletHealthSummaryResponse>> anonymous =
            controller.kubeManagerHttpOutletHealthSummary();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletHealthSummaryResponse>> user =
            controller.kubeManagerHttpOutletHealthSummary();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletHealthSummaryResponse>> admin =
            controller.kubeManagerHttpOutletHealthSummary();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentKubeManagerHttpOutletHealthSummaryResponse summary = admin.getBody().getData();
        assertThat(summary.schemaVersion()).isEqualTo("agent-kube-manager-http-outlet-health-summary.v1");
        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.readPolicy())
            .containsEntry("automaticRetryEnabled", true)
            .containsEntry("maxAttempts", 3);
        assertThat(summary.writePolicy())
            .containsEntry("automaticRetryEnabled", false)
            .containsEntry("configuredButInactive", true)
            .containsEntry("configuredMaxAttempts", 1);
        assertThat(summary.safety())
            .containsEntry("localProcessOnly", true)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("remoteProbeExecuted", false)
            .containsEntry("toolExecution", false)
            .containsEntry("fallbackLogin", false);
        assertThat(summary.privacy())
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawEndpoint", false);
        assertThat(summary.toString())
            .doesNotContain("kube-manager.internal", "secret-password", "Bearer", "user-token", "/api/login");
    }

    @Test
    void kubeManagerWriteRetryReadiness_shouldRequireAdminAndReturnFailClosedReadinessContract() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryReadinessResponse>> anonymous =
            controller.kubeManagerWriteRetryReadiness();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryReadinessResponse>> user =
            controller.kubeManagerWriteRetryReadiness();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryReadinessResponse>> admin =
            controller.kubeManagerWriteRetryReadiness();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentKubeManagerWriteRetryReadinessResponse readiness = admin.getBody().getData();
        assertThat(readiness.schemaVersion()).isEqualTo("agent-kube-manager-write-retry-readiness.v1");
        assertThat(readiness.readinessVerdict()).isEqualTo("NOT_READY");
        assertThat(readiness.readyForControlledWriteRetry()).isFalse();
        assertThat(readiness.writeRetryEnabled()).isFalse();
        assertThat(readiness.automaticWriteRetryAllowed()).isFalse();
        assertThat(readiness.effectivePolicy())
            .containsEntry("configuredButInactive", true)
            .containsEntry("automaticRetryEnabled", false)
            .containsEntry("runtimeEnableEndpointPresent", false);
        assertThat(readiness.currentEvidence())
            .containsEntry("highRiskDurablePrewriteGateExists", true)
            .containsEntry("genericDurableReceiptContractExists", true)
            .containsEntry("genericDurableReceiptContractBoundToHttpOutlet", false)
            .containsEntry("genericDurableReceiptIssuerExists", false)
            .containsEntry("genericDurableReceiptIssuedByReadinessEndpoint", false)
            .containsEntry("genericDurableReceiptCanOpenReleaseGate", false)
            .containsEntry("genericReleaseEvidenceContractExists", true)
            .containsEntry("genericReleaseEvidenceContractBoundToHttpOutlet", false)
            .containsEntry("serverHitlConfirmationBoundToHttpOutlet", false)
            .containsEntry("callerProvidedReleaseEvidenceAccepted", false)
            .containsEntry("runtimeReleaseGateSwitchExists", false)
            .containsEntry("runtimeReleaseGateOpenCount", 0)
            .containsEntry("genericKubeManagerIdempotencyBoundaryExists", true)
            .containsEntry("genericKubeManagerIdempotencyBoundaryBoundToHttpOutlet", false)
            .containsEntry("callerProvidedIdempotencyKeyAccepted", false)
            .containsEntry("genericWriteOperationAllowlistExists", true)
            .containsEntry("genericWriteOperationAllowlistBoundToHttpOutlet", false)
            .containsEntry("genericWriteOperationAllowlistEnforcedByHttpOutlet", false)
            .containsEntry("retryFailureClassificationContractExists", true)
            .containsEntry("retryPredicateContractExists", true)
            .containsEntry("retryPredicateBoundToHttpOutlet", false)
            .containsEntry("runtimeRetryableFailureClassCount", 0)
            .containsEntry("callerProvidedRetryPredicateAccepted", false)
            .containsEntry("postWriteReadbackContractExists", true)
            .containsEntry("postWriteReadbackBoundToHttpOutlet", false)
            .containsEntry("runtimeRetryEligibleWriteOperationCount", 0)
            .containsEntry("postWriteReadbackExecutorExists", false)
            .containsEntry("runtimeWriteRetryEnablementSwitchExists", false)
            .containsEntry("compensationPolicyContractExists", true)
            .containsEntry("compensationPolicyBoundToHttpOutlet", false)
            .containsEntry("compensationExecutorExists", false)
            .containsEntry("automaticCompensationPolicyCount", 0)
            .containsEntry("compensationCanOpenReleaseSwitch", false)
            .containsEntry("nimHpcSlurmBcmPhase2Paused", true);
        assertThat(readiness.blockedReasons()).contains(
            "generic-kube-manager-idempotency-boundary-not-bound-to-http-outlet",
            "generic-durable-receipt-contract-not-bound-to-http-outlet",
            "generic-durable-receipt-issuer-missing",
            "generic-release-evidence-contract-not-bound-to-http-outlet",
            "server-hitl-confirmation-not-bound-to-http-outlet",
            "write-operation-allowlist-contract-not-bound-to-http-outlet",
            "write-retry-predicate-contract-not-bound-to-http-outlet",
            "no-runtime-retryable-failure-class",
            "post-write-readback-contract-not-bound-to-http-outlet",
            "runtime-release-gate-switch-intentionally-absent",
            "compensation-policy-contract-not-bound-to-http-outlet",
            "compensation-executor-missing"
        );
        assertThat(readiness.endpointTemplates())
            .containsEntry("writeRetryGovernanceContract", "/api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract")
            .containsEntry("writeReleaseGateContract", "/api/agent/observability/kube-manager/http-outlet/write-release-gate-contract");
        assertThat(readiness.safety())
            .containsEntry("localProcessOnly", true)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("remoteProbeExecuted", false)
            .containsEntry("toolExecution", false)
            .containsEntry("writeRetryEnablement", false)
            .containsEntry("callerInputAccepted", false);
        assertThat(readiness.privacy())
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawEndpoint", false);
        assertThat(readiness.toString())
            .doesNotContain("kube-manager.internal", "secret-password", "Bearer", "user-token", "/api/login");
    }

    @Test
    void kubeManagerWriteIdempotencyContract_shouldRequireAdminAndReturnUnboundServerDerivedContract() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteIdempotencyContractResponse>> anonymous =
            controller.kubeManagerWriteIdempotencyContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentKubeManagerWriteIdempotencyContractResponse>> user =
            controller.kubeManagerWriteIdempotencyContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentKubeManagerWriteIdempotencyContractResponse>> admin =
            controller.kubeManagerWriteIdempotencyContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentKubeManagerWriteIdempotencyContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-kube-manager-write-idempotency-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.serverDerivedKeyContractExists()).isTrue();
        assertThat(contract.boundToHttpOutlet()).isFalse();
        assertThat(contract.callerProvidedIdempotencyKeyAccepted()).isFalse();
        assertThat(contract.writeRetryEnabled()).isFalse();
        assertThat(contract.keyContract())
            .containsEntry("keySource", "server-derived-sha256-bound-evidence.v1")
            .containsEntry("actualKeyExposed", false)
            .containsEntry("retryAllowedByThisContract", false);
        assertThat(contract.bindingStatus())
            .containsEntry("boundToKubeManagerHttpClient", false)
            .containsEntry("httpHeaderInjectionEnabled", false)
            .containsEntry("writeRetryEnabled", false)
            .containsEntry("callerOverrideAllowed", false);
        assertThat(contract.safety())
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("auditWrite", false)
            .containsEntry("httpHeaderInjection", false)
            .containsEntry("writeRetryEnablement", false);
        assertThat(contract.privacy())
            .containsEntry("actualKeyExposed", false)
            .containsEntry("rawEvidenceExposed", false)
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false);
        assertThat(contract.toString())
            .doesNotContain("secret-password", "Bearer", "user-token", "/api/login", "/api/100002");
    }

    @Test
    void kubeManagerWriteOperationSafetyContract_shouldRequireAdminAndReturnUnboundSafetyContract() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteOperationSafetyContractResponse>> anonymous =
            controller.kubeManagerWriteOperationSafetyContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentKubeManagerWriteOperationSafetyContractResponse>> user =
            controller.kubeManagerWriteOperationSafetyContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentKubeManagerWriteOperationSafetyContractResponse>> admin =
            controller.kubeManagerWriteOperationSafetyContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentKubeManagerWriteOperationSafetyContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-kube-manager-write-operation-safety-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.operationAllowlistContractExists()).isTrue();
        assertThat(contract.postWriteReadbackContractExists()).isTrue();
        assertThat(contract.boundToHttpOutlet()).isFalse();
        assertThat(contract.writeRetryEnabled()).isFalse();
        assertThat(contract.allowedOperationClasses())
            .extracting(operation -> operation.get("id"))
            .containsExactly(
                "generic-tenant-create",
                "generic-tenant-update-patch",
                "generic-tenant-update-put",
                "generic-tenant-delete",
                "generic-tenant-action"
            );
        assertThat(contract.requiredRbacEvidence())
            .extracting(evidence -> evidence.get("field"))
            .contains("principalFingerprint", "organizationFingerprint", "rolePermissionDigest", "releaseEvidenceDigest");
        assertThat(contract.readbackContract())
            .containsEntry("boundToHttpOutlet", false)
            .containsEntry("readMethod", "GET")
            .containsEntry("successClaimRequiresReadback", true)
            .containsEntry("acceptsCallerSuccessClaim", false);
        assertThat(contract.retryEligibilityGates())
            .containsEntry("runtimeRetryEligibleWriteOperationCount", 0L)
            .containsEntry("runtimeBindingAllowedNow", false)
            .containsEntry("callerOverrideAllowed", false)
            .containsEntry("defaultIfAnyGateMissing", "fail-closed-no-write-retry");
        assertThat(contract.blockedRuntimeBindings())
            .containsEntry("realWriteExecution", "blocked-by-contract")
            .containsEntry("runtimeEnableSwitch", "not-exposed");
        assertThat(contract.safety())
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("auditWrite", false)
            .containsEntry("httpHeaderInjection", false)
            .containsEntry("writeRetryEnablement", false)
            .containsEntry("allowlistMutation", false)
            .containsEntry("readbackExecuted", false);
        assertThat(contract.privacy())
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawRequestBody", false);
        assertThat(contract.toString())
            .doesNotContain("secret-password", "Bearer", "user-token", "/api/login", "/api/100002");
    }

    @Test
    void kubeManagerWriteRetryGovernanceContract_shouldRequireAdminAndReturnUnboundGovernanceContract() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryGovernanceContractResponse>> anonymous =
            controller.kubeManagerWriteRetryGovernanceContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryGovernanceContractResponse>> user =
            controller.kubeManagerWriteRetryGovernanceContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentKubeManagerWriteRetryGovernanceContractResponse>> admin =
            controller.kubeManagerWriteRetryGovernanceContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentKubeManagerWriteRetryGovernanceContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-kube-manager-write-retry-governance-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.retryPredicateContractExists()).isTrue();
        assertThat(contract.compensationPolicyContractExists()).isTrue();
        assertThat(contract.boundToHttpOutlet()).isFalse();
        assertThat(contract.writeRetryEnabled()).isFalse();
        assertThat(contract.runtimeRetryableFailureClassCount()).isZero();
        assertThat(contract.automaticCompensationPolicyCount()).isZero();
        assertThat(contract.predicateContract())
            .containsEntry("boundToHttpOutlet", false)
            .containsEntry("runtimePredicateExists", false)
            .containsEntry("callerOverrideAccepted", false)
            .containsEntry("sameIdempotencyKeyRequired", true)
            .containsEntry("postWriteReadbackRequiredBeforeSuccess", true);
        assertThat(contract.failureClasses())
            .allSatisfy(failureClass -> assertThat(failureClass).containsEntry("runtimeRetryableNow", false));
        assertThat(contract.compensationPolicies())
            .allSatisfy(policy -> assertThat(policy)
                .containsEntry("automaticCompensationAllowed", false)
                .containsEntry("operatorReviewRequired", true)
                .containsEntry("runtimeBound", false)
                .containsEntry("canOpenReleaseSwitch", false));
        assertThat(contract.bindingStatus())
            .containsEntry("retryPredicateBoundToResilience4j", false)
            .containsEntry("retryPredicateBoundToHttpOutlet", false)
            .containsEntry("failureClassifierRuntimeBound", false)
            .containsEntry("compensationExecutorExists", false)
            .containsEntry("writeRetryEnabled", false)
            .containsEntry("runtimeEnableSwitchPresent", false);
        assertThat(contract.safety())
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("retryRegistryMutation", false)
            .containsEntry("toolExecution", false)
            .containsEntry("auditWrite", false)
            .containsEntry("readbackExecuted", false)
            .containsEntry("compensationExecuted", false)
            .containsEntry("writeRetryEnablement", false);
        assertThat(contract.privacy())
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawRequestBody", false);
        assertThat(contract.toString())
            .contains("agent-kube-manager-write-retry-governance-contract.v1", "CONTRACT_DEFINED_NOT_BOUND")
            .doesNotContain("secret-password", "Bearer", "user-token", "/api/login", "/api/100002");
    }

    @Test
    void kubeManagerWriteReleaseGateContract_shouldRequireAdminAndReturnUnboundReleaseGateContract() {
        ResponseEntity<ApiResponse<AgentKubeManagerWriteReleaseGateContractResponse>> anonymous =
            controller.kubeManagerWriteReleaseGateContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentKubeManagerWriteReleaseGateContractResponse>> user =
            controller.kubeManagerWriteReleaseGateContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentKubeManagerWriteReleaseGateContractResponse>> admin =
            controller.kubeManagerWriteReleaseGateContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentKubeManagerWriteReleaseGateContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-kube-manager-write-release-gate-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.durableReceiptContractExists()).isTrue();
        assertThat(contract.releaseEvidenceContractExists()).isTrue();
        assertThat(contract.boundToHttpOutlet()).isFalse();
        assertThat(contract.releaseGateOpen()).isFalse();
        assertThat(contract.writeRetryEnabled()).isFalse();
        assertThat(contract.runtimeReleaseGateOpenCount()).isZero();
        assertThat(contract.durableReceiptContract())
            .containsEntry("boundToHttpOutlet", false)
            .containsEntry("issuerExists", false)
            .containsEntry("issuedByReadinessEndpoint", false)
            .containsEntry("durableStorageMutationAllowed", false);
        assertThat(contract.releaseEvidenceContract())
            .containsEntry("boundToHttpOutlet", false)
            .containsEntry("hitlEvidenceRequired", true)
            .containsEntry("releaseReviewRequired", true)
            .containsEntry("callerProvidedReleaseEvidenceAccepted", false)
            .containsEntry("canOpenReleaseSwitch", false);
        assertThat(contract.bindingStatus())
            .containsEntry("durableReceiptIssuerExists", false)
            .containsEntry("serverHitlConfirmationBound", false)
            .containsEntry("runtimeReleaseSwitchPresent", false)
            .containsEntry("releaseGateOpen", false)
            .containsEntry("writeRetryEnabled", false);
        assertThat(contract.safety())
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("releaseDecisionSigned", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("durableStorageMutation", false)
            .containsEntry("writeRetryEnablement", false)
            .containsEntry("releaseGateOpen", false);
        assertThat(contract.privacy())
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawReleaseEvidence", false)
            .containsEntry("containsRawReceipt", false);
        assertThat(contract.toString())
            .contains("agent-kube-manager-write-release-gate-contract.v1", "CONTRACT_DEFINED_NOT_BOUND")
            .doesNotContain("secret-password", "Bearer", "user-token", "/api/login", "/api/100002");
    }

    @Test
    void kubeManagerHttpOutletGovernanceWorkbenchOverview_shouldRequireAdminAndReturnVueReadModel() {
        ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse>> anonymous =
            controller.kubeManagerHttpOutletGovernanceWorkbenchOverview();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse>> user =
            controller.kubeManagerHttpOutletGovernanceWorkbenchOverview();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse>> admin =
            controller.kubeManagerHttpOutletGovernanceWorkbenchOverview();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse overview = admin.getBody().getData();
        assertThat(overview.schemaVersion())
            .isEqualTo("agent-kube-manager-http-outlet-governance-workbench-overview.v1");
        assertThat(overview.workbenchStatus()).isEqualTo("WRITE_GOVERNANCE_NOT_READY");
        assertThat(overview.frontendTarget())
            .isEqualTo("vue-kube-manager kube-manager HTTP outlet governance workbench");
        assertThat(overview.httpOutletStatus()).isEqualTo("READY");
        assertThat(overview.writeReadinessVerdict()).isEqualTo("NOT_READY");
        assertThat(overview.releaseGateOpen()).isFalse();
        assertThat(overview.writeRetryEnabled()).isFalse();
        assertThat(overview.automaticWriteRetryAllowed()).isFalse();
        assertThat(overview.governanceCardCount()).isEqualTo(6);
        assertThat(overview.blockingCardCount()).isEqualTo(5);
        assertThat(overview.boundRuntimeContractCount()).isZero();
        assertThat(overview.governanceCards()).extracting(card -> card.get("id"))
            .containsExactly(
                "http-outlet-health",
                "write-retry-readiness",
                "write-idempotency-contract",
                "write-operation-safety-contract",
                "write-retry-governance-contract",
                "write-release-gate-contract"
            );
        assertThat(overview.recommendedWorkflow())
            .contains("governance-workbench-overview", "human-release-review-before-runtime-binding");
        assertThat(overview.nextActions())
            .contains("keep-kube-manager-write-retry-disabled", "keep-nim-hpc-slurm-bcm-paused-for-phase2");
        assertThat(overview.workbenchPolicy())
            .containsEntry("runtimeWriteBindingAllowed", false)
            .containsEntry("runtimeReleaseGateSwitchPresent", false)
            .containsEntry("writeRetryEnablementAllowed", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("remoteProbeExecuted", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(overview.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawEndpoint", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(overview.toString())
            .contains("governance-workbench-overview", "write-release-gate-contract")
            .doesNotContain("kube-manager.internal", "secret-password", "Bearer", "user-token", "/api/login", "/api/100002");
    }

    @Test
    void topTierReadinessOverview_shouldRequireAdminAndReturnMasterReadModel() {
        ResponseEntity<ApiResponse<AgentTopTierReadinessOverviewResponse>> anonymous =
            controller.topTierReadinessOverview();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentTopTierReadinessOverviewResponse>> user =
            controller.topTierReadinessOverview();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentTopTierReadinessOverviewResponse>> admin =
            controller.topTierReadinessOverview();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentTopTierReadinessOverviewResponse overview = admin.getBody().getData();
        assertThat(overview.schemaVersion()).isEqualTo("agent-top-tier-readiness-overview.v1");
        assertThat(overview.readinessVerdict()).isEqualTo("PHASE_1_TOP_TIER_CORE_IN_PROGRESS");
        assertThat(overview.phase1TopTierGoalPreserved()).isTrue();
        assertThat(overview.writeAuthorityClosed()).isTrue();
        assertThat(overview.toolExecutionTriggered()).isFalse();
        assertThat(overview.capabilityCardCount()).isEqualTo(10);
        assertThat(overview.topGaps())
            .contains("eval-release-gates", "memory-rag-learning", "vue-operator-workbench");
        assertThat(overview.endpointMap())
            .containsEntry("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview")
            .containsEntry("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract")
            .containsEntry("officialVersionProtocolWatch", "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap")
            .containsEntry("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane")
            .containsEntry("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness")
            .containsEntry("memoryRagDurableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        assertThat(overview.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("adminOnlyAppliesToThisEndpoint", true)
            .containsEntry("readOnly", true)
            .containsEntry("endpointMapNavigationOnly", true)
            .containsEntry("endpointMapDoesNotGrantAccess", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("writeAuthorityClosed", true)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(overview.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false);
        assertThat(overview.kubeManagerGovernance().workbenchStatus()).isEqualTo("WRITE_GOVERNANCE_NOT_READY");
        assertThat(overview.mcpGovernance().governanceStatus()).isEqualTo("MANIFEST_ONLY_NOT_CALLABLE");
        assertThat(overview.toString())
            .contains("top-tier", "memory-rag-learning", "mcp-interoperability")
            .doesNotContain("kube-manager.internal", "secret-password", "Bearer", "user-token", "/api/login", "/api/100002");
    }

    @Test
    void advancedTechnologyAdoptionContract_shouldRequireAdminAndReturnUnboundContract() {
        ResponseEntity<ApiResponse<AgentAdvancedTechnologyAdoptionContractResponse>> anonymous =
            controller.advancedTechnologyAdoptionContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentAdvancedTechnologyAdoptionContractResponse>> user =
            controller.advancedTechnologyAdoptionContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentAdvancedTechnologyAdoptionContractResponse>> admin =
            controller.advancedTechnologyAdoptionContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentAdvancedTechnologyAdoptionContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-advanced-technology-adoption-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.javaSpringControlPlanePreserved()).isTrue();
        assertThat(contract.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(contract.runtimeUpgradePerformed()).isFalse();
        assertThat(contract.dependencyUpgradePerformed()).isFalse();
        assertThat(contract.externalAgentRuntimeBound()).isFalse();
        assertThat(contract.mainlineTechnologies()).extracting(technology -> technology.get("id"))
            .contains("java-spring-control-plane", "safe-tool-executor-boundary", "memory-rag-contract-stack");
        assertThat(contract.compatibilityMatrix()).extracting(technology -> technology.get("id"))
            .contains("spring-boot-4-framework-7", "responses-agents-runtime", "mcp-runtime-server");
        assertThat(contract.adoptionGates()).extracting(gate -> gate.get("id"))
            .contains("source-owned-contract", "eval-before-release", "phase2-domain-pause");
        assertThat(contract.endpointMap())
            .containsEntry("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract")
            .containsEntry("advancedTechnologyCompatibilityMatrix",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix")
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec")
            .containsEntry("officialVersionProtocolWatch", "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap")
            .containsEntry("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("llmUsed", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(contract.toString())
            .contains("java-spring-control-plane", "COMPATIBILITY_MATRIX", "source-owned-contract")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void advancedTechnologyCompatibilityMatrix_shouldRequireAdminAndReturnMatrixOnlyContract() {
        ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixResponse>> anonymous =
            controller.advancedTechnologyCompatibilityMatrix();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixResponse>> user =
            controller.advancedTechnologyCompatibilityMatrix();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixResponse>> admin =
            controller.advancedTechnologyCompatibilityMatrix();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentAdvancedTechnologyCompatibilityMatrixResponse matrix = admin.getBody().getData();
        assertThat(matrix.schemaVersion()).isEqualTo("agent-advanced-technology-compatibility-matrix.v1");
        assertThat(matrix.matrixStatus()).isEqualTo("MATRIX_DEFINED_NOT_EXECUTED");
        assertThat(matrix.sourceBaselineCount()).isEqualTo(8);
        assertThat(matrix.matrixItemCount()).isEqualTo(10);
        assertThat(matrix.migrationGateCount()).isEqualTo(8);
        assertThat(matrix.blockedShortcutCount()).isEqualTo(7);
        assertThat(matrix.testLaneCount()).isEqualTo(8);
        assertThat(matrix.runtimeUpgradeAllowedNow()).isFalse();
        assertThat(matrix.dependencyUpgradeAllowedNow()).isFalse();
        assertThat(matrix.matrixItems()).extracting(item -> item.get("id"))
            .contains("java-runtime-toolchains", "spring-boot-framework", "spring-ai-access-layer",
                "mcp-runtime-call-plane", "memory-rag-graphrag-reranker-vectorstore");
        assertThat(matrix.blockedUpgradeShortcuts()).extracting(shortcut -> shortcut.get("id"))
            .contains("upgrade-pom-from-readiness-page", "trust-mcp-tool-annotations",
                "enable-ci-blocking-with-empty-fixtures");
        assertThat(matrix.endpointMap())
            .containsEntry("advancedTechnologyCompatibilityMatrix",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix")
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec")
            .containsEntry("officialVersionProtocolWatch",
                "/api/agent/observability/top-tier/official-version-protocol-watch");
        assertThat(matrix.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("matrixOnly", true)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("toolExecution", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(matrix.sourceWatch().schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch.v1");
        assertThat(matrix.toString())
            .contains("spring-ai-access-layer", "mcp-runtime-call-plane", "upgrade-pom-from-readiness-page")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void advancedTechnologyCompatibilityMatrixVueBindingSpec_shouldRequireAdminAndReturnBindingSpec() {
        ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse>> anonymous =
            controller.advancedTechnologyCompatibilityMatrixVueBindingSpec();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse>> user =
            controller.advancedTechnologyCompatibilityMatrixVueBindingSpec();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse>> admin =
            controller.advancedTechnologyCompatibilityMatrixVueBindingSpec();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse spec = admin.getBody().getData();
        assertThat(spec.schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix-vue-binding-spec.v1");
        assertThat(spec.bindingStatus()).isEqualTo("VUE_BINDING_SPEC_READY");
        assertThat(spec.componentSpecCount()).isEqualTo(8);
        assertThat(spec.fieldBindingCount()).isEqualTo(14);
        assertThat(spec.disabledActionBindingCount()).isEqualTo(7);
        assertThat(spec.fixtureCount()).isEqualTo(5);
        assertThat(spec.runtimeControlAllowed()).isFalse();
        assertThat(spec.componentSpecs()).extracting(component -> component.get("name"))
            .contains("CandidateUpgradeLaneMatrix", "CompatibilityTestLaneBoard",
                "CompatibilityMatrixSourceJsonPanel");
        assertThat(spec.fieldBindings()).extracting(binding -> binding.get("fieldPath"))
            .contains("matrixItems[].readiness", "blockedUpgradeShortcuts[].allowed",
                "safety.runtimeControlAllowed");
        assertThat(spec.disabledActionBindings()).allSatisfy(binding -> assertThat(binding)
            .containsEntry("buttonVisible", false)
            .containsEntry("clickHandlerAllowed", false)
            .containsEntry("blocksTopTierClaim", true));
        assertThat(spec.testFixtures()).extracting(fixture -> fixture.get("id"))
            .contains("major-upgrade-lanes-visible", "runtime-buttons-absent");
        assertThat(spec.endpointMap())
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec")
            .containsEntry("advancedTechnologyCompatibilityMatrix",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix");
        assertThat(spec.bindingPolicy())
            .containsEntry("bindingSpecOnly", true)
            .containsEntry("runtimeButtonsAllowed", false)
            .containsEntry("dependencyUpgradeButtonsAllowed", false)
            .containsEntry("mockedHttpFixturesRequired", true);
        assertThat(spec.safety())
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(spec.sourceMatrix().schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix.v1");
        assertThat(spec.toString())
            .contains("CandidateUpgradeLaneMatrix", "runtime-buttons-absent")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void officialVersionProtocolWatch_shouldRequireAdminAndReturnOfficialSourceWatch() {
        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchResponse>> anonymous =
            controller.officialVersionProtocolWatch();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchResponse>> user =
            controller.officialVersionProtocolWatch();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchResponse>> admin =
            controller.officialVersionProtocolWatch();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentOfficialVersionProtocolWatchResponse watch = admin.getBody().getData();
        assertThat(watch.schemaVersion()).isEqualTo("agent-official-version-protocol-watch.v1");
        assertThat(watch.watchStatus()).isEqualTo("OFFICIAL_WATCH_DEFINED_NOT_RUNTIME_BOUND");
        assertThat(watch.officialSourcesOnly()).isTrue();
        assertThat(watch.runtimeUpgradePerformed()).isFalse();
        assertThat(watch.dependencyUpgradePerformed()).isFalse();
        assertThat(watch.externalCallsPerformed()).isFalse();
        assertThat(watch.officialSources()).extracting(source -> source.get("id"))
            .contains("spring-ai-reference", "openai-agents-sdk", "mcp-2025-11-25",
                "nsa-mcp-security-2026-06",
                "a2a-latest-spec", "otel-genai-semconv", "owasp-llm-top-10-2025");
        assertThat(watch.technologyTracks()).extracting(track -> track.get("id"))
            .contains("java-spring-governed-control-plane", "mcp-runtime-call-plane",
                "a2a-handoff-provenance", "advanced-rag-graphrag-rerankers-vector-stores");
        assertThat(watch.endpointMap())
            .containsEntry("officialVersionProtocolWatch",
                "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("advancedTechnologyAdoptionContract",
                "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        assertThat(watch.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("watchOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
        assertThat(watch.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(watch.toString())
            .contains("official-source-review", "mcp-2025-11-25", "otel-genai-semconv")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void officialVersionProtocolWatchDashboard_shouldRequireAdminAndReturnVueDashboard() {
        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchDashboardResponse>> anonymous =
            controller.officialVersionProtocolWatchDashboard();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchDashboardResponse>> user =
            controller.officialVersionProtocolWatchDashboard();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchDashboardResponse>> admin =
            controller.officialVersionProtocolWatchDashboard();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentOfficialVersionProtocolWatchDashboardResponse dashboard = admin.getBody().getData();
        assertThat(dashboard.schemaVersion()).isEqualTo("agent-official-version-protocol-watch-dashboard.v1");
        assertThat(dashboard.dashboardStatus()).isEqualTo("DASHBOARD_READY_TO_RENDER_OFFICIAL_WATCH");
        assertThat(dashboard.sourceCardCount()).isEqualTo(8);
        assertThat(dashboard.technologyTrackCardCount()).isEqualTo(8);
        assertThat(dashboard.runtimeControlAllowed()).isFalse();
        assertThat(dashboard.sourceCards()).extracting(card -> card.get("id"))
            .contains("spring-ai-reference", "openai-agents-sdk", "mcp-2025-11-25",
                "nsa-mcp-security-2026-06");
        assertThat(dashboard.disabledRuntimeActions()).extracting(action -> action.get("id"))
            .contains("upgrade-dependencies-from-dashboard", "enable-mcp-tools-call",
                "enable-a2a-runtime-handoff", "enable-retrieval-runtime");
        assertThat(dashboard.endpointMap())
            .containsEntry("officialVersionProtocolWatchDashboard",
                "/api/agent/observability/top-tier/official-version-protocol-watch/dashboard")
            .containsEntry("officialVersionProtocolWatch",
                "/api/agent/observability/top-tier/official-version-protocol-watch");
        assertThat(dashboard.dashboardPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("dashboardOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("dependencyUpgradeAllowed", false)
            .containsEntry("mcpToolsCallAllowed", false)
            .containsEntry("a2aRuntimeHandoffAllowed", false)
            .containsEntry("retrievalRuntimeAllowed", false)
            .containsEntry("phase2NimHpcSlurmBcmPaused", true);
        assertThat(dashboard.safety())
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(dashboard.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(dashboard.toString())
            .contains("official-version-protocol-watch-dashboard", "blocked-shortcuts")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void officialVersionProtocolWatchVueBindingSpec_shouldRequireAdminAndReturnBindingSpec() {
        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchVueBindingSpecResponse>> anonymous =
            controller.officialVersionProtocolWatchVueBindingSpec();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchVueBindingSpecResponse>> user =
            controller.officialVersionProtocolWatchVueBindingSpec();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentOfficialVersionProtocolWatchVueBindingSpecResponse>> admin =
            controller.officialVersionProtocolWatchVueBindingSpec();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentOfficialVersionProtocolWatchVueBindingSpecResponse spec = admin.getBody().getData();
        assertThat(spec.schemaVersion()).isEqualTo("agent-official-version-protocol-watch-vue-binding-spec.v1");
        assertThat(spec.bindingStatus()).isEqualTo("VUE_BINDING_SPEC_READY");
        assertThat(spec.componentSpecCount()).isEqualTo(7);
        assertThat(spec.fieldBindingCount()).isEqualTo(12);
        assertThat(spec.disabledActionBindingCount()).isEqualTo(6);
        assertThat(spec.runtimeControlAllowed()).isFalse();
        assertThat(spec.componentSpecs()).extracting(component -> component.get("name"))
            .contains("OfficialSourceCardGrid", "TechnologyTrackMatrix", "DisabledRuntimeActionList");
        assertThat(spec.fieldBindings()).extracting(binding -> binding.get("fieldPath"))
            .contains("sourceCards[].officialUrl", "technologyTrackCards[].disabledRuntimeActions",
                "dashboardPolicy.runtimeControlAllowed");
        assertThat(spec.disabledActionBindings()).allSatisfy(binding -> assertThat(binding)
            .containsEntry("buttonVisible", false)
            .containsEntry("clickHandlerAllowed", false));
        assertThat(spec.testFixtures()).extracting(fixture -> fixture.get("id"))
            .contains("mcp-security-source-visible", "runtime-buttons-absent");
        assertThat(spec.endpointMap())
            .containsEntry("officialVersionProtocolWatchVueBindingSpec",
                "/api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec")
            .containsEntry("officialVersionProtocolWatchDashboard",
                "/api/agent/observability/top-tier/official-version-protocol-watch/dashboard");
        assertThat(spec.bindingPolicy())
            .containsEntry("bindingSpecOnly", true)
            .containsEntry("runtimeButtonsAllowed", false)
            .containsEntry("mockedHttpFixturesRequired", true);
        assertThat(spec.safety())
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(spec.sourceDashboard().schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch-dashboard.v1");
        assertThat(spec.toString())
            .contains("OfficialSourceCardGrid", "runtime-buttons-absent")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void topTierVueWorkbenchImplementationPackage_shouldRequireAdminAndReturnFrontendImplementationPackage() {
        ResponseEntity<ApiResponse<AgentTopTierVueWorkbenchImplementationPackageResponse>> anonymous =
            controller.topTierVueWorkbenchImplementationPackage();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentTopTierVueWorkbenchImplementationPackageResponse>> user =
            controller.topTierVueWorkbenchImplementationPackage();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentTopTierVueWorkbenchImplementationPackageResponse>> admin =
            controller.topTierVueWorkbenchImplementationPackage();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentTopTierVueWorkbenchImplementationPackageResponse implementationPackage = admin.getBody().getData();
        assertThat(implementationPackage.schemaVersion())
            .isEqualTo("agent-top-tier-vue-workbench-implementation-package.v1");
        assertThat(implementationPackage.packageStatus()).isEqualTo("IMPLEMENTATION_PACKAGE_READY");
        assertThat(implementationPackage.routeSpecCount()).isEqualTo(2);
        assertThat(implementationPackage.apiClientBindingCount()).isEqualTo(4);
        assertThat(implementationPackage.pageAssemblyCount()).isEqualTo(2);
        assertThat(implementationPackage.sharedComponentCount()).isEqualTo(7);
        assertThat(implementationPackage.acceptanceFixtureCount()).isEqualTo(6);
        assertThat(implementationPackage.runtimeControlAllowed()).isFalse();
        assertThat(implementationPackage.routeSpecs()).extracting(route -> route.get("id"))
            .contains("top-tier-official-version-protocol-watch",
                "top-tier-advanced-technology-compatibility-matrix");
        assertThat(implementationPackage.apiClientBindings()).extracting(client -> client.get("name"))
            .contains("fetchOfficialWatchBindingSpec", "fetchCompatibilityMatrixBindingSpec");
        assertThat(implementationPackage.forbiddenRuntimeControls()).allSatisfy(control -> assertThat(control)
            .containsEntry("buttonVisible", false)
            .containsEntry("clickHandlerAllowed", false)
            .containsEntry("requiresSeparateReviewedSlice", true));
        assertThat(implementationPackage.packagePolicy())
            .containsEntry("implementationPackageOnly", true)
            .containsEntry("runtimeButtonsAllowed", false)
            .containsEntry("dependencyUpgradeButtonsAllowed", false);
        assertThat(implementationPackage.safety())
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(implementationPackage.endpointMap())
            .containsEntry("topTierVueWorkbenchImplementationPackage",
                "/api/agent/observability/top-tier/vue-workbench-implementation-package")
            .containsEntry("officialVersionProtocolWatchVueBindingSpec",
                "/api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec")
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec");
        assertThat(implementationPackage.officialWatchBindingSpec().schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch-vue-binding-spec.v1");
        assertThat(implementationPackage.compatibilityMatrixBindingSpec().schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix-vue-binding-spec.v1");
        assertThat(implementationPackage.toString())
            .contains("runtime-buttons-absent-in-both-pages", "DisabledActionList")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void phase1ExecutionRoadmap_shouldRequireAdminAndReturnReadOnlyRoadmap() {
        ResponseEntity<ApiResponse<AgentPhase1ExecutionRoadmapResponse>> anonymous =
            controller.phase1ExecutionRoadmap();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentPhase1ExecutionRoadmapResponse>> user =
            controller.phase1ExecutionRoadmap();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentPhase1ExecutionRoadmapResponse>> admin =
            controller.phase1ExecutionRoadmap();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentPhase1ExecutionRoadmapResponse roadmap = admin.getBody().getData();
        assertThat(roadmap.schemaVersion()).isEqualTo("agent-phase1-execution-roadmap.v1");
        assertThat(roadmap.roadmapStatus()).isEqualTo("PHASE_1_TOP_TIER_ROADMAP_ACTIVE");
        assertThat(roadmap.phase1TopTierGoalPreserved()).isTrue();
        assertThat(roadmap.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(roadmap.roadmapOnly()).isTrue();
        assertThat(roadmap.runtimeMutationAllowed()).isFalse();
        assertThat(roadmap.executionSteps()).extracting(step -> step.get("id"))
            .containsExactly(
                "vue-readiness-control-plane",
                "reviewed-eval-trace-evidence",
                "release-blocking-eval-gates",
                "memory-rag-eval-suite-binding",
                "durable-memory-store-binding",
                "retrieval-runtime-binding",
                "mcp-runtime-safe-call-plane",
                "agent-handoff-and-a2a-provenance"
            );
        assertThat(roadmap.endpointMap())
            .containsEntry("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap")
            .containsEntry("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane")
            .containsEntry("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract")
            .containsEntry("officialVersionProtocolWatch", "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("memoryRagTraceSetCurationContract",
                "/api/agent/observability/memory-rag/trace-set-curation-contract")
            .containsEntry("memoryRagTraceSetCurationWorkbenchOverview",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/overview");
        assertThat(roadmap.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("roadmapOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("llmUsed", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
        assertThat(roadmap.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(roadmap.toString())
            .contains("vue-readiness-control-plane", "memory-rag-eval-suite-binding", "mcp-runtime-safe-call-plane")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void vueReadinessControlPlane_shouldRequireAdminAndReturnReadOnlyBindingContract() {
        ResponseEntity<ApiResponse<AgentVueReadinessControlPlaneResponse>> anonymous =
            controller.vueReadinessControlPlane();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentVueReadinessControlPlaneResponse>> user =
            controller.vueReadinessControlPlane();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentVueReadinessControlPlaneResponse>> admin =
            controller.vueReadinessControlPlane();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentVueReadinessControlPlaneResponse controlPlane = admin.getBody().getData();
        assertThat(controlPlane.schemaVersion()).isEqualTo("agent-vue-readiness-control-plane.v1");
        assertThat(controlPlane.controlPlaneStatus()).isEqualTo("BACKEND_CONTRACT_READY_FOR_VUE_BINDING");
        assertThat(controlPlane.phase1TopTierGoalPreserved()).isTrue();
        assertThat(controlPlane.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(controlPlane.vueBindingReady()).isTrue();
        assertThat(controlPlane.runtimeControlAllowed()).isFalse();
        assertThat(controlPlane.dashboards()).extracting(dashboard -> dashboard.get("id"))
            .contains(
                "top-tier-command-center",
                "advanced-technology-adoption",
                "official-version-protocol-watch",
                "phase1-execution-roadmap",
                "kube-manager-governance",
                "memory-rag-readiness",
                "memory-rag-trace-set-curation-workbench",
                "eval-workbench",
                "mcp-governance"
            );
        assertThat(controlPlane.forbiddenUiActions())
            .contains("enable-kube-manager-write-retry", "run-mcp-tools-call", "run-retrieval-against-prompt",
                "run-memory-rag-trace-set-curation-workbench-action");
        assertThat(controlPlane.endpointMap())
            .containsEntry("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane")
            .containsEntry("officialVersionProtocolWatch", "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap")
            .containsEntry("memoryRagTraceSetCurationWorkbenchOverview",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/overview")
            .containsEntry("mcpManifest", "/api/agent/mcp/manifest");
        assertThat(controlPlane.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("vueContractOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("llmUsed", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
        assertThat(controlPlane.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(controlPlane.toString())
            .contains("top-tier-command-center", "phase1-execution-roadmap", "keep-runtime-control-buttons-absent")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void memoryRagReadiness_shouldRequireAdminAndReturnFailClosedContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagReadinessResponse>> anonymous =
            controller.memoryRagReadiness();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentMemoryRagReadinessResponse>> user =
            controller.memoryRagReadiness();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));
        memoryStore.append("boss", "conv-1", "安全摘要 token=secret-value");

        ResponseEntity<ApiResponse<AgentMemoryRagReadinessResponse>> admin =
            controller.memoryRagReadiness();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentMemoryRagReadinessResponse readiness = admin.getBody().getData();
        assertThat(readiness.schemaVersion()).isEqualTo("agent-memory-rag-readiness.v1");
        assertThat(readiness.readinessVerdict()).isEqualTo("MEMORY_RAG_CONTRACT_DEFINED_NOT_READY");
        assertThat(readiness.currentSafeSummaryMemoryEnabled()).isTrue();
        assertThat(readiness.ragReady()).isFalse();
        assertThat(readiness.blockingGaps()).contains(
            "durable-memory-store",
            "rag-retrieval-layer",
            "citation-and-source-contract",
            "eval-and-observability"
        );
        assertThat(readiness.currentEvidence())
            .containsEntry("trustedPrincipalOwner", true)
            .containsEntry("durableStoreBound", false)
            .containsEntry("durableMemoryLifecycleContractDefined", true)
            .containsEntry("durableMemoryLifecycleContractBound", false)
            .containsEntry("vectorStoreBound", false)
            .containsEntry("citationSourceContractDefined", true)
            .containsEntry("sourceEvidenceDigestContractDefined", true)
            .containsEntry("sourceEvidenceDigestContractBound", false);
        assertThat(readiness.currentEvidence())
            .containsEntry("memoryRagEvalGateContractDefined", true)
            .containsEntry("memoryRagEvalGateContractBound", false);
        assertThat(readiness.endpointMap())
            .containsEntry("durableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        assertThat(readiness.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(readiness.privacy())
            .containsEntry("containsRawConversation", false)
            .containsEntry("containsToken", false);
        assertThat(readiness.toString())
            .contains("safe-summary-memory", "citation-and-source-contract")
            .doesNotContain("secret-value", "Bearer", "user-token");
    }

    @Test
    void memoryRagCitationSourceContract_shouldRequireAdminAndReturnUnboundContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagCitationSourceContractResponse>> anonymous =
            controller.memoryRagCitationSourceContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentMemoryRagCitationSourceContractResponse>> user =
            controller.memoryRagCitationSourceContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentMemoryRagCitationSourceContractResponse>> admin =
            controller.memoryRagCitationSourceContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentMemoryRagCitationSourceContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-citation-source-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.contractDefined()).isTrue();
        assertThat(contract.boundToRetrievalRuntime()).isFalse();
        assertThat(contract.promptEvidenceAllowedNow()).isFalse();
        assertThat(contract.sourceEvidenceFields()).extracting(field -> field.get("id"))
            .contains("sourceDigest", "tenantScope", "redactionStatus");
        assertThat(contract.citationFields()).extracting(field -> field.get("id"))
            .contains("citationId", "sourceDigest", "chunkDigest");
        assertThat(contract.promptEvidenceRules()).extracting(rule -> rule.get("id"))
            .contains("source-evidence-digest-required");
        assertThat(contract.endpointMap())
            .containsEntry("sourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(contract.privacy())
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsSourceBody", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("citation-required-for-rag-answer", "sourceDigest")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void memoryRagSourceEvidenceDigestContract_shouldRequireAdminAndReturnUnboundContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagSourceEvidenceDigestContractResponse>> anonymous =
            controller.memoryRagSourceEvidenceDigestContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentMemoryRagSourceEvidenceDigestContractResponse>> user =
            controller.memoryRagSourceEvidenceDigestContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentMemoryRagSourceEvidenceDigestContractResponse>> admin =
            controller.memoryRagSourceEvidenceDigestContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentMemoryRagSourceEvidenceDigestContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-source-evidence-digest-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.sourceEvidenceDigestDeriverDefined()).isTrue();
        assertThat(contract.boundToIngestionRuntime()).isFalse();
        assertThat(contract.boundToRetrievalRuntime()).isFalse();
        assertThat(contract.sampleDigest().evidenceDigest()).matches("sha256:[a-f0-9]{64}");
        assertThat(contract.digestInputs()).extracting(input -> input.get("id"))
            .contains("tenantScopeDigest", "sourceAclDigest", "chunkContentDigest", "retrievalPolicyDigest");
        assertThat(contract.enforcementRules()).extracting(rule -> rule.get("id"))
            .contains("redacted-or-summary-only", "tenant-scope-bound", "runtime-binding-gated");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("sampleDerivationLocalOnly", true)
            .containsEntry("ingestionExecuted", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(contract.privacy())
            .containsEntry("sampleSyntheticOnly", true)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("sourceDigest", "citationSeed")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw document");
    }

    @Test
    void memoryRagDurableMemoryLifecycleContract_shouldRequireAdminAndReturnUnboundContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagDurableMemoryLifecycleContractResponse>> anonymous =
            controller.memoryRagDurableMemoryLifecycleContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentMemoryRagDurableMemoryLifecycleContractResponse>> user =
            controller.memoryRagDurableMemoryLifecycleContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentMemoryRagDurableMemoryLifecycleContractResponse>> admin =
            controller.memoryRagDurableMemoryLifecycleContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentMemoryRagDurableMemoryLifecycleContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-durable-memory-lifecycle-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.lifecycleContractDefined()).isTrue();
        assertThat(contract.boundToDurableStoreRuntime()).isFalse();
        assertThat(contract.retentionEnforcedNow()).isFalse();
        assertThat(contract.deleteEndpointImplemented()).isFalse();
        assertThat(contract.exportEndpointImplemented()).isFalse();
        assertThat(contract.recoveryCheckpointBound()).isFalse();
        assertThat(contract.promptEvidenceAllowedNow()).isFalse();
        assertThat(contract.lifecycleFields()).extracting(field -> field.get("id"))
            .contains("memoryRecordId", "tenantPartitionDigest", "sourceEvidenceDigest", "deleteProofDigest");
        assertThat(contract.deletionProofRules()).extracting(rule -> rule.get("id"))
            .contains("delete-tombstone-required", "delete-runtime-disabled-now");
        assertThat(contract.exportProofRules()).extracting(rule -> rule.get("id"))
            .contains("redacted-export-only", "export-runtime-disabled-now");
        assertThat(contract.recoveryRules()).extracting(rule -> rule.get("id"))
            .contains("recovery-manifest-required", "recovery-runtime-disabled-now");
        assertThat(contract.endpointMap())
            .containsEntry("durableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
            .containsEntry("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("memoryWrite", false)
            .containsEntry("durableStoreCalls", false)
            .containsEntry("deleteExecuted", false)
            .containsEntry("exportExecuted", false)
            .containsEntry("recoveryExecuted", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawConversation", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("tenantPartitionDigest", "deleteProofDigest", "exportProofDigest")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw document");
    }

    @Test
    void memoryRagEvalGateContract_shouldRequireAdminAndReturnUnboundContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagEvalGateContractResponse>> anonymous =
            controller.memoryRagEvalGateContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentMemoryRagEvalGateContractResponse>> user =
            controller.memoryRagEvalGateContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentMemoryRagEvalGateContractResponse>> admin =
            controller.memoryRagEvalGateContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentMemoryRagEvalGateContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-eval-gate-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.evalGateContractDefined()).isTrue();
        assertThat(contract.boundToEvalRuntime()).isFalse();
        assertThat(contract.ciBlockingEnabled()).isFalse();
        assertThat(contract.traceEvidenceCurated()).isFalse();
        assertThat(contract.promptEvidenceAllowedNow()).isFalse();
        assertThat(contract.retrievalRuntimeAllowedNow()).isFalse();
        assertThat(contract.gateInputs()).extracting(input -> input.get("id"))
            .contains("traceSetId", "sourceEvidenceDigest", "durableLifecycleDigest", "tenantPartitionDigest");
        assertThat(contract.gateChecks()).extracting(check -> check.get("id"))
            .contains("citation-fidelity", "privacy-leakage", "tenant-isolation", "retention-staleness");
        assertThat(contract.failureClasses()).extracting(failure -> failure.get("id"))
            .contains("MISSING_CITATION", "SOURCE_DIGEST_MISMATCH", "RAW_SECRET_OR_PROMPT_LEAK");
        assertThat(contract.endpointMap())
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawConversation", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsEvalTracePayload", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("citation-fidelity", "tenant-isolation", "MISSING_CITATION")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw document");
    }

    @Test
    void memoryRagEvalSuiteBindingContract_shouldRequireAdminAndReturnUnboundContract() {
        ResponseEntity<ApiResponse<AgentMemoryRagEvalSuiteBindingContractResponse>> anonymous =
            controller.memoryRagEvalSuiteBindingContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentMemoryRagEvalSuiteBindingContractResponse>> user =
            controller.memoryRagEvalSuiteBindingContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentMemoryRagEvalSuiteBindingContractResponse>> admin =
            controller.memoryRagEvalSuiteBindingContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentMemoryRagEvalSuiteBindingContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-eval-suite-binding-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("TRACE_SETS_DEFINED_REVIEWED_EVIDENCE_NOT_CURATED");
        assertThat(contract.memoryRagEvalSuiteBound()).isTrue();
        assertThat(contract.memoryRagTraceSetBound()).isFalse();
        assertThat(contract.evalRuntimeExecuted()).isFalse();
        assertThat(contract.ciBlockingEnabled()).isFalse();
        assertThat(contract.retrievalRuntimeAllowedNow()).isFalse();
        assertThat(contract.bindingRows()).extracting(row -> row.get("gateCheckId"))
            .contains("citation-fidelity", "source-digest-integrity", "privacy-leakage", "tenant-isolation");
        assertThat(contract.bindingRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("suiteCheckCodePresent", true)
            .containsEntry("bindingStatus", "MAPPED")
            .containsEntry("runtimeBound", false));
        assertThat(contract.requiredTraceSets()).extracting(row -> row.get("traceSetId"))
            .contains("memory-rag-citation-fidelity", "memory-rag-privacy-tenant", "memory-rag-lifecycle-policy");
        assertThat(contract.requiredTraceSets()).allSatisfy(row -> assertThat(row)
            .containsEntry("definedInCatalog", true)
            .containsEntry("reviewedTraceIdsPresent", false)
            .containsEntry("catalogOnlyUntilReviewed", true)
            .containsEntry("suiteRuntimeExecutionAllowed", false)
            .containsEntry("retrievalRuntimeAllowed", false)
            .containsEntry("ciBlockingAllowed", false));
        assertThat(contract.endpointMap())
            .containsEntry("memoryRagEvalSuiteBindingContract", "/api/agent/observability/memory-rag/eval-suite-binding-contract")
            .containsEntry("evalSuiteCatalog", "/api/agent/observability/eval/suites")
            .containsEntry("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("evalSuiteRunInvoked", false)
            .containsEntry("traceSetGateInvoked", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("memory-rag-eval-suite-binding-contract", "MEMORY_RAG_CITATION_FIDELITY")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "raw document");
    }

    @Test
    void memoryRagTraceSetCurationContract_shouldRequireAdminAndReturnReviewedTraceGaps() {
        ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationContractResponse>> anonymous =
            controller.memoryRagTraceSetCurationContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationContractResponse>> user =
            controller.memoryRagTraceSetCurationContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationContractResponse>> admin =
            controller.memoryRagTraceSetCurationContract();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentMemoryRagTraceSetCurationContractResponse contract = admin.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-memory-rag-trace-set-curation-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("TRACE_SETS_DEFINED_REVIEWED_EVIDENCE_NOT_CURATED");
        assertThat(contract.reviewedTraceEvidenceCurated()).isFalse();
        assertThat(contract.allRequiredTraceSetsDefined()).isTrue();
        assertThat(contract.allRequiredTraceSetsPolicyClosed()).isTrue();
        assertThat(contract.suiteRuntimePolicyClosed()).isTrue();
        assertThat(contract.evalRuntimeAllowedNow()).isFalse();
        assertThat(contract.retrievalRuntimeAllowedNow()).isFalse();
        assertThat(contract.ciBlockingAllowedNow()).isFalse();
        assertThat(contract.requiredTraceSetCount()).isEqualTo(3);
        assertThat(contract.definedTraceSetCount()).isEqualTo(3);
        assertThat(contract.reviewedTraceSetCount()).isZero();
        assertThat(contract.traceSetRows()).extracting(row -> row.get("traceSetId"))
            .containsExactly(
                "memory-rag-citation-fidelity",
                "memory-rag-privacy-tenant",
                "memory-rag-lifecycle-policy"
            );
        assertThat(contract.suiteRuntimeLatch())
            .containsEntry("suiteId", "memory-rag-release-gate")
            .containsEntry("definedInCatalog", true)
            .containsEntry("policyLatchDeclaredClosed", true)
            .containsEntry("runtimeExecutionAllowedNow", false);
        assertThat(contract.traceSetRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("definedInCatalog", true)
            .containsEntry("rowStatus", "REVIEWED_EVIDENCE_MISSING")
            .containsEntry("reviewedTraceIdsPresent", false)
            .containsEntry("traceIdsVisibleInContract", false)
            .containsEntry("policyKeysPresent", true)
            .containsEntry("policyLatchDeclaredClosed", true)
            .containsEntry("catalogOnlyUntilReviewed", true)
            .containsEntry("suiteRuntimeExecutionAllowed", false)
            .containsEntry("retrievalRuntimeAllowed", false)
            .containsEntry("ciBlockingAllowed", false)
            .containsEntry("requiresRealAuditCapture", true)
            .containsEntry("placeholderTraceIds", false)
            .containsEntry("failClosedWhenEmpty", true)
            .containsEntry("requestTraceIdOverrideAllowed", false)
            .containsEntry("runtimeCatalogMutationAllowed", false));
        assertThat(contract.traceSetRows()).allSatisfy(row ->
            assertThat(row.get("blockedReasons").toString()).contains("reviewed-redacted-trace-ids-missing"));
        assertThat(contract.blockedReasons()).contains(
            "reviewed-redacted-memory-rag-trace-ids-missing",
            "memory-rag-advisory-gate-bundle-not-generated",
            "memory-rag-eval-runtime-not-promoted",
            "retrieval-runtime-intentionally-closed",
            "ci-blocking-switch-intentionally-absent"
        );
        assertThat(contract.endpointMap())
            .containsEntry("memoryRagTraceSetCurationContract",
                "/api/agent/observability/memory-rag/trace-set-curation-contract")
            .containsEntry("memoryRagTraceSetCurationWorkbenchOverview",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/overview")
            .containsEntry("memoryRagEvalSuiteBindingContract",
                "/api/agent/observability/memory-rag/eval-suite-binding-contract")
            .containsEntry("traceSetCatalog", "/api/agent/observability/eval/trace-sets")
            .containsKey("traceSetGateBundle");
        assertThat(contract.evidencePolicy())
            .containsEntry("traceIdsAcceptedFromCaller", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("requiresGitReview", true)
            .containsEntry("emptyTraceIdsFailClosed", true)
            .containsEntry("missingPolicyKeyOutcome", "fail-closed-visible-blocker");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("traceSetGateInvoked", false)
            .containsEntry("curationReviewInvoked", false)
            .containsEntry("candidateDiscoveryInvoked", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("traceIdsVisibleInContract", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsToken", false);
        assertThat(contract.toString())
            .contains("memory-rag-trace-set-curation-contract", "memory-rag-citation-fidelity")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void memoryRagTraceSetCurationWorkbenchOverview_shouldRequireAdminAndReturnVueReadModel() {
        ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse>> anonymous =
            controller.memoryRagTraceSetCurationWorkbenchOverview();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse>> user =
            controller.memoryRagTraceSetCurationWorkbenchOverview();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse>> admin =
            controller.memoryRagTraceSetCurationWorkbenchOverview();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse overview = admin.getBody().getData();
        assertThat(overview.schemaVersion())
            .isEqualTo("agent-memory-rag-trace-set-curation-workbench-overview.v1");
        assertThat(overview.workbenchStatus()).isEqualTo("WORKBENCH_READY_TO_RENDER_REVIEWED_EVIDENCE_GAPS");
        assertThat(overview.phase1TopTierGoalPreserved()).isTrue();
        assertThat(overview.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(overview.sourceReadModelsEmbedded()).isTrue();
        assertThat(overview.runtimeControlAllowed()).isFalse();
        assertThat(overview.curationCardCount()).isEqualTo(3);
        assertThat(overview.blockingCardCount()).isEqualTo(3);
        assertThat(overview.curationCards()).extracting(card -> card.get("id"))
            .containsExactly(
                "memory-rag-citation-fidelity",
                "memory-rag-privacy-tenant",
                "memory-rag-lifecycle-policy"
            );
        assertThat(overview.curationCards()).allSatisfy(card -> {
            assertThat(card)
                .containsEntry("severity", "BLOCKING")
                .containsEntry("traceIdsVisibleInWorkbench", false)
                .containsEntry("policyLatchDeclaredClosed", true)
                .containsEntry("runtimeControlAllowed", false)
                .containsEntry("runtimeCatalogMutationAllowed", false)
                .containsEntry("toolExecution", false)
                .containsEntry("kubeManagerCalls", false);
            @SuppressWarnings("unchecked")
            java.util.List<java.util.Map<String, Object>> actions =
                (java.util.List<java.util.Map<String, Object>>) card.get("disabledRuntimeActions");
            assertThat(actions).allSatisfy(action -> assertThat(action)
                .containsEntry("enabledNow", false)
                .containsEntry("buttonVisibleNow", false));
        });
        assertThat(overview.suiteLatchCard())
            .containsEntry("status", "RUNTIME_LATCH_CLOSED")
            .containsEntry("runtimeExecutionAllowedNow", false)
            .containsEntry("runtimeControlAllowed", false);
        assertThat(overview.endpointMap())
            .containsEntry("memoryRagTraceSetCurationWorkbenchOverview",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/overview")
            .containsEntry("memoryRagReviewedTraceEvidenceManifest",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest")
            .containsEntry("memoryRagTraceSetCurationContract",
                "/api/agent/observability/memory-rag/trace-set-curation-contract")
            .containsEntry("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        assertThat(overview.workbenchPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("overviewOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("traceIdsAcceptedFromCaller", false)
            .containsEntry("traceIdsVisibleInWorkbench", false)
            .containsEntry("candidateDiscoveryAllowedNow", false)
            .containsEntry("curationReviewAllowedNow", false)
            .containsEntry("traceSetGateAllowedNow", false)
            .containsEntry("gateBundleButtonEnabledNow", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(overview.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("traceSetGateInvoked", false)
            .containsEntry("curationReviewInvoked", false)
            .containsEntry("candidateDiscoveryInvoked", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("auditWrite", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(overview.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("traceIdsVisibleInWorkbench", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawRetrievedChunk", false)
            .containsEntry("containsToken", false);
        assertThat(overview.curationContract().schemaVersion())
            .isEqualTo("agent-memory-rag-trace-set-curation-contract.v1");
        assertThat(overview.suiteBindingContract().schemaVersion())
            .isEqualTo("agent-memory-rag-eval-suite-binding-contract.v1");
        assertThat(overview.memoryRagReadiness().schemaVersion()).isEqualTo("agent-memory-rag-readiness.v1");
        assertThat(overview.toString())
            .contains("trace-set-curation-workbench", "memory-rag-citation-fidelity")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void memoryRagReviewedTraceEvidenceManifest_shouldRequireAdminAndReturnIntakeManifest() {
        ResponseEntity<ApiResponse<AgentMemoryRagReviewedTraceEvidenceManifestResponse>> anonymous =
            controller.memoryRagReviewedTraceEvidenceManifest();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentMemoryRagReviewedTraceEvidenceManifestResponse>> user =
            controller.memoryRagReviewedTraceEvidenceManifest();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        userPermissionContext.unbind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentMemoryRagReviewedTraceEvidenceManifestResponse>> admin =
            controller.memoryRagReviewedTraceEvidenceManifest();

        assertThat(admin.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.getBody()).isNotNull();
        AgentMemoryRagReviewedTraceEvidenceManifestResponse manifest = admin.getBody().getData();
        assertThat(manifest.schemaVersion())
            .isEqualTo("agent-memory-rag-reviewed-trace-evidence-manifest.v1");
        assertThat(manifest.manifestStatus()).isEqualTo("WAITING_FOR_REVIEWED_REDACTED_TRACE_FIXTURES");
        assertThat(manifest.requiredTraceSetCount()).isEqualTo(3);
        assertThat(manifest.reviewedTraceSetCount()).isZero();
        assertThat(manifest.authoritativeFixtureCount()).isZero();
        assertThat(manifest.runtimeControlAllowed()).isFalse();
        assertThat(manifest.requiredTraceSets()).extracting(row -> row.get("traceSetId"))
            .containsExactly(
                "memory-rag-citation-fidelity",
                "memory-rag-privacy-tenant",
                "memory-rag-lifecycle-policy"
            );
        assertThat(manifest.requiredTraceSets()).allSatisfy(row -> assertThat(row)
            .containsEntry("catalogPatchTarget", "src/main/resources/observability/eval-trace-sets.json")
            .containsEntry("traceIdsVisibleInManifest", false)
            .containsEntry("authoritativeFixturePresent", false)
            .containsEntry("safeToPromoteNow", false)
            .containsEntry("safeToRunEvalNow", false)
            .containsEntry("safeToEnableRetrievalNow", false)
            .containsEntry("safeToEnableCiBlockingNow", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false));
        assertThat(manifest.advancedTechnologyMappings()).extracting(mapping -> mapping.get("id"))
            .contains(
                "spring-ai-memory-rag-vectorstore",
                "openai-agents-tracing-guardrails-evals",
                "mcp-2025-11-25-tools-resources-prompts",
                "otel-genai-semantic-conventions",
                "a2a-agent-card-task-artifact-provenance"
            );
        assertThat(manifest.endpointMap())
            .containsEntry("memoryRagReviewedTraceEvidenceManifest",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest")
            .containsEntry("memoryRagTraceSetCurationWorkbenchOverview",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/overview");
        assertThat(manifest.manifestPolicy())
            .containsEntry("readOnly", true)
            .containsEntry("traceIdsAcceptedFromCaller", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requiresHumanGitReview", true)
            .containsEntry("evalRuntimeAllowedNow", false)
            .containsEntry("retrievalRuntimeAllowedNow", false)
            .containsEntry("ciBlockingAllowedNow", false);
        assertThat(manifest.safety())
            .containsEntry("readOnly", true)
            .containsEntry("candidateDiscoveryInvoked", false)
            .containsEntry("curationReviewInvoked", false)
            .containsEntry("traceSetGateInvoked", false)
            .containsEntry("evalRuntimeExecuted", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false);
        assertThat(manifest.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("traceIdsVisibleInManifest", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsRawRetrievedChunk", false);
        assertThat(manifest.toString())
            .contains("review-manifest", "memory-rag-citation-fidelity")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void replayTimeline_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentReplayTimelineResponse>> anonymous =
            controller.replayByTraceId("trc_missing", 10);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentReplayTimelineResponse>> user =
            controller.replayByTraceId("trc_missing", 10);

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void replayTimeline_shouldReturnRedactedChronologicalAdminSteps() {
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_replay",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            "trc_replay",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "POST",
            java.util.List.of("/api/org-sensitive/deployment?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.CREATE,
            true,
            com.atlas.audit.AgentAuditOutcome.PREPARED,
            false,
            false,
            "prepared token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_replay",
            java.time.Instant.parse("2026-06-09T00:00:05Z"),
            "trc_replay",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "POST",
            java.util.List.of("/api/org-sensitive/deployment?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.CREATE,
            true,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentReplayTimelineResponse>> response =
            controller.replayByTraceId("trc_replay", 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentReplayTimelineResponse timeline = response.getBody().getData();
        assertThat(timeline.schemaVersion()).isEqualTo("agent-replay-timeline.v1");
        assertThat(timeline.order()).isEqualTo("oldest-first");
        assertThat(timeline.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawEndpoints", false)
            .containsEntry("containsRawParameterValues", false);
        assertThat(timeline.steps()).hasSize(2);
        assertThat(timeline.steps()).extracting(AgentReplayTimelineStep::phase)
            .containsExactly("PRE_EXECUTION", "FINAL");
        assertThat(timeline.steps()).extracting(AgentReplayTimelineStep::recordPhase)
            .containsExactly("PRE_EXECUTION", "FINAL");
        assertThat(timeline.steps()).extracting(AgentReplayTimelineStep::kind)
            .containsExactly("TOOL_PREPARED", "TOOL_RESULT");
        assertThat(timeline.steps()).extracting(AgentReplayTimelineStep::status)
            .containsExactly("prepared", "success");
        String bodyText = timeline.toString();
        assertThat(bodyText)
            .contains("aud_replay", "trc_replay", "<protected>", "confirmation:required")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void evalReport_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentEvalReportResponse>> anonymous =
            controller.evalByTraceId("trc_missing", 10);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalReportResponse>> user =
            controller.evalByTraceId("trc_missing", 10);

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evalReport_shouldReturnRedactedDeterministicAdminEvidence() {
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            "trc_eval",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "POST",
            java.util.List.of("/api/org-sensitive/deployment?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.CREATE,
            true,
            com.atlas.audit.AgentAuditOutcome.PREPARED,
            false,
            false,
            "prepared token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval",
            java.time.Instant.parse("2026-06-09T00:00:05Z"),
            "trc_eval",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "POST",
            java.util.List.of("/api/org-sensitive/deployment?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.CREATE,
            true,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalReportResponse>> response =
            controller.evalByTraceId("trc_eval", 10);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalReportResponse report = response.getBody().getData();
        assertThat(report.schemaVersion()).isEqualTo("agent-eval-report.v1");
        assertThat(report.evaluationVersion()).isEqualTo("deterministic-replay-eval.v1");
        assertThat(report.timelineSchemaVersion()).isEqualTo("agent-replay-timeline.v1");
        assertThat(report.verdict()).isEqualTo("PASS");
        assertThat(report.pass()).isTrue();
        assertThat(report.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("deterministic", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false);
        assertThat(report.checks()).extracting(AgentEvalCheck::code)
            .contains("HIGH_RISK_PREWRITE_EVIDENCE", "HIGH_RISK_CONFIRMATION_MARKER", "EXECUTION_SEMANTICS");
        String bodyText = report.toString();
        assertThat(bodyText)
            .contains("aud_eval", "trc_eval", "<protected>", "confirmation:required")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void evalSuite_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentEvalSuiteResponse>> anonymous =
            controller.evalSuite(new AgentEvalSuiteRequest(java.util.List.of("trc_missing"), 10, 80, true));

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalSuiteResponse>> user =
            controller.evalSuite(new AgentEvalSuiteRequest(java.util.List.of("trc_missing"), 10, 80, true));

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evalSuite_shouldReturnRedactedGateReportForAdminUser() {
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_suite",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            "trc_eval_suite",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalSuiteResponse>> response =
            controller.evalSuite(new AgentEvalSuiteRequest(java.util.List.of("trc_eval_suite"), 10, 80, true));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalSuiteResponse suite = response.getBody().getData();
        assertThat(suite.schemaVersion()).isEqualTo("agent-eval-suite.v1");
        assertThat(suite.gateVerdict()).isEqualTo("PASS");
        assertThat(suite.pass()).isTrue();
        assertThat(suite.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("deterministic", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false);
        assertThat(suite.reports()).hasSize(1);
        String bodyText = suite.toString();
        assertThat(bodyText)
            .contains("aud_eval_suite", "trc_eval_suite", "<protected>")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void evalSuite_shouldUseDocumentedDefaultsForNullRequestAndNullFields() {
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_suite_default",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            "trc_eval_suite_default",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalSuiteResponse>> nullRequest = controller.evalSuite(null);
        ResponseEntity<ApiResponse<AgentEvalSuiteResponse>> nullFields = controller.evalSuite(
            new AgentEvalSuiteRequest(java.util.List.of("trc_eval_suite_default"), null, null, null)
        );

        assertThat(nullRequest.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nullRequest.getBody()).isNotNull();
        AgentEvalSuiteResponse emptySuite = nullRequest.getBody().getData();
        assertThat(emptySuite.maxResults()).isEqualTo(AgentEvalReportService.DEFAULT_TRACE_MAX_RESULTS);
        assertThat(emptySuite.minimumScore()).isEqualTo(AgentEvalReportService.DEFAULT_SUITE_MINIMUM_SCORE);
        assertThat(emptySuite.failOnWarnings()).isEqualTo(AgentEvalReportService.DEFAULT_SUITE_FAIL_ON_WARNINGS);
        assertThat(emptySuite.pass()).isFalse();
        assertThat(emptySuite.summary()).containsEntry("emptyInput", true);

        assertThat(nullFields.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nullFields.getBody()).isNotNull();
        AgentEvalSuiteResponse defaultedSuite = nullFields.getBody().getData();
        assertThat(defaultedSuite.pass()).isTrue();
        assertThat(defaultedSuite.maxResults()).isEqualTo(AgentEvalReportService.DEFAULT_TRACE_MAX_RESULTS);
        assertThat(defaultedSuite.minimumScore()).isEqualTo(AgentEvalReportService.DEFAULT_SUITE_MINIMUM_SCORE);
        assertThat(defaultedSuite.failOnWarnings()).isEqualTo(AgentEvalReportService.DEFAULT_SUITE_FAIL_ON_WARNINGS);
        assertThat(defaultedSuite.summary())
            .containsEntry("requestedCases", 1)
            .containsEntry("caseLimitExceeded", false);
    }

    @Test
    void evalSuites_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentEvalSuiteCatalogResponse>> anonymous = controller.evalSuites();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalSuiteCatalogResponse>> user = controller.evalSuites();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evalSuites_shouldReturnCatalogForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalSuiteCatalogResponse>> response = controller.evalSuites();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalSuiteCatalogResponse catalog = response.getBody().getData();
        assertThat(catalog.schemaVersion()).isEqualTo("agent-eval-suite-catalog.v1");
        assertThat(catalog.suites()).extracting(AgentEvalSuiteDefinition::id)
            .contains("core-safety-smoke", "high-risk-prewrite", "redaction-regression", "release-gate-strict",
                "memory-rag-release-gate");
        assertThat(catalog.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(catalog.toString())
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void runEvalSuite_shouldRequireAdminUserAndRejectUnknownSuite() {
        AgentEvalSuiteRequest request = new AgentEvalSuiteRequest(java.util.List.of("trc_missing"), 10, 80, true);
        ResponseEntity<ApiResponse<AgentEvalSuiteRunResponse>> anonymous =
            controller.runEvalSuite("core-safety-smoke", request);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalSuiteRunResponse>> missing =
            controller.runEvalSuite("missing-suite", request);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().isSuccess()).isFalse();
    }

    @Test
    void runEvalSuite_shouldApplyNamedDefaultsAndReturnRedactedReport() {
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_named_suite",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            "trc_eval_named_suite",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalSuiteRunResponse>> response = controller.runEvalSuite(
            "core-safety-smoke",
            new AgentEvalSuiteRequest(java.util.List.of("trc_eval_named_suite"), null, null, null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalSuiteRunResponse run = response.getBody().getData();
        assertThat(run.schemaVersion()).isEqualTo("agent-eval-suite-run.v1");
        assertThat(run.suiteId()).isEqualTo("core-safety-smoke");
        assertThat(run.definition().defaultMinimumScore()).isEqualTo(80);
        assertThat(run.runPolicy())
            .containsEntry("definitionDefaultsApplied", true)
            .containsEntry("effectiveMinimumScore", 80)
            .containsEntry("failOnWarnings", true);
        assertThat(run.report().pass()).isTrue();
        assertThat(run.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false);
        String bodyText = run.toString();
        assertThat(bodyText)
            .contains("aud_eval_named_suite", "trc_eval_named_suite", "<protected>")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void memoryRagReleaseGateSuite_shouldRemainCatalogOnlyForRuntimeEndpoints() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));
        AgentEvalSuiteRequest request = new AgentEvalSuiteRequest(
            java.util.List.of("trc_memory_rag_catalog_only"),
            null,
            null,
            null
        );

        ResponseEntity<ApiResponse<AgentEvalSuiteRunResponse>> run =
            controller.runEvalSuite("memory-rag-release-gate", request);
        ResponseEntity<ApiResponse<AgentEvalSuiteGateArtifact>> gate =
            controller.evalSuiteGate("memory-rag-release-gate", request);

        assertThat(run.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(run.getBody()).isNotNull();
        assertThat(run.getBody().isSuccess()).isFalse();
        assertThat(run.getBody().getMessage()).contains("仅用于目录/绑定契约");
        assertThat(gate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(gate.getBody()).isNotNull();
        assertThat(gate.getBody().isSuccess()).isFalse();
        assertThat(gate.getBody().getMessage()).contains("尚未开放 gate artifact");
    }

    @Test
    void evalSuiteGate_shouldRequireAdminUserAndRejectUnknownSuite() {
        AgentEvalSuiteRequest request = new AgentEvalSuiteRequest(java.util.List.of("trc_missing"), 10, 80, true);
        ResponseEntity<ApiResponse<AgentEvalSuiteGateArtifact>> anonymous =
            controller.evalSuiteGate("release-gate-strict", request);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalSuiteGateArtifact>> missing =
            controller.evalSuiteGate("missing-suite", request);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().isSuccess()).isFalse();
    }

    @Test
    void evalSuiteGate_shouldReturnCompactRedactedCiArtifact() {
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_gate",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            "trc_eval_gate",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalSuiteGateArtifact>> response = controller.evalSuiteGate(
            "release-gate-strict",
            new AgentEvalSuiteRequest(java.util.List.of("trc_eval_gate"), null, null, null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalSuiteGateArtifact artifact = response.getBody().getData();
        assertThat(artifact.schemaVersion()).isEqualTo("agent-eval-suite-gate.v1");
        assertThat(artifact.suiteId()).isEqualTo("release-gate-strict");
        assertThat(artifact.pass()).isTrue();
        assertThat(artifact.requiredMinimumScore()).isEqualTo(90);
        assertThat(artifact.gatePolicy())
            .containsEntry("artifactOnly", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false);
        assertThat(artifact.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        String bodyText = artifact.toString();
        assertThat(bodyText)
            .contains("trc_eval_gate")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    @Test
    void evalWorkbenchCapabilities_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchCapabilitiesResponse>> anonymous =
            controller.evalWorkbenchCapabilities();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalWorkbenchCapabilitiesResponse>> user =
            controller.evalWorkbenchCapabilities();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evalWorkbenchCapabilities_shouldReturnFrontendManifestForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalWorkbenchCapabilitiesResponse>> response =
            controller.evalWorkbenchCapabilities();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalWorkbenchCapabilitiesResponse capabilities = response.getBody().getData();
        assertThat(capabilities.schemaVersion()).isEqualTo("agent-eval-workbench-capabilities.v1");
        assertThat(capabilities.capabilities()).extracting(AgentEvalWorkbenchCapability::id)
            .contains(
                "workbench-overview",
                "workbench-trace-set-detail",
                "workbench-promotion-workflow",
                "trace-set-promotion-workflow",
                "trace-set-gate-bundle",
                "trace-replay-timeline"
            );
        assertThat(capabilities.recommendedWorkflow())
            .contains("workbench-overview", "workbench-trace-set-detail", "workbench-promotion-workflow", "trace-set-gate-bundle");
        assertThat(capabilities.workbenchPolicy())
            .containsEntry("frontendTarget", "vue-kube-manager eval workbench")
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(capabilities.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(capabilities.toString())
            .contains(
                "workbench-trace-set-detail",
                "agent-eval-workbench-promotion-workflow.v1",
                "agent-eval-workbench-gate-bundle-summary.v1"
            )
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void evalWorkbenchOverview_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchOverviewResponse>> anonymous =
            controller.evalWorkbenchOverview();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalWorkbenchOverviewResponse>> user =
            controller.evalWorkbenchOverview();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evalWorkbenchOverview_shouldReturnFrontendReadModelForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalWorkbenchOverviewResponse>> response =
            controller.evalWorkbenchOverview();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalWorkbenchOverviewResponse overview = response.getBody().getData();
        assertThat(overview.schemaVersion()).isEqualTo("agent-eval-workbench-overview.v1");
        assertThat(overview.traceSetCount()).isEqualTo(7);
        assertThat(overview.traceSetNeedsEvidenceCount()).isEqualTo(7);
        assertThat(overview.traceSets())
            .filteredOn(traceSet -> traceSet.id().startsWith("phase1-"))
            .extracting(AgentEvalWorkbenchTraceSetView::status)
            .containsOnly("NEEDS_REDACTED_EVIDENCE");
        assertThat(overview.traceSets())
            .filteredOn(traceSet -> traceSet.id().startsWith("memory-rag-"))
            .hasSize(3)
            .allSatisfy(traceSet -> {
                assertThat(traceSet.suiteId()).isEqualTo("memory-rag-release-gate");
                assertThat(traceSet.gateVerdict()).isEqualTo("SUITE_RUNTIME_DISABLED");
                assertThat(traceSet.status()).isEqualTo("SUITE_RUNTIME_DISABLED_CATALOG_ONLY");
                assertThat(traceSet.nextAction()).isEqualTo("keep-catalog-only-until-reviewed-runtime-promotion");
                assertThat(traceSet.policy())
                    .containsEntry("suiteRuntimeDisabled", true)
                    .containsEntry("runtimeExecutionAllowed", false)
                    .containsEntry("retrievalRuntimeAllowed", false)
                    .containsEntry("traceSetGateRuntimeDisabled", true);
            });
        assertThat(overview.recommendedWorkflow()).startsWith(
            "workbench-overview",
            "trace-set-catalog",
            "workbench-trace-set-detail"
        );
        assertThat(overview.workbenchPolicy())
            .containsEntry("frontendTarget", "vue-kube-manager eval workbench")
            .containsEntry("overviewOnly", true)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(overview.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(overview.toString())
            .contains("workbench-overview", "phase1-core-golden")
            .doesNotContain("reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void reviewedEvalTraceEvidence_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentReviewedEvalTraceEvidenceResponse>> anonymous =
            controller.reviewedEvalTraceEvidence();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentReviewedEvalTraceEvidenceResponse>> user =
            controller.reviewedEvalTraceEvidence();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void reviewedEvalTraceEvidence_shouldReturnReadOnlyEvidenceContractForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentReviewedEvalTraceEvidenceResponse>> response =
            controller.reviewedEvalTraceEvidence();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentReviewedEvalTraceEvidenceResponse evidence = response.getBody().getData();
        assertThat(evidence.schemaVersion()).isEqualTo("agent-reviewed-eval-trace-evidence.v1");
        assertThat(evidence.evidenceStatus()).isEqualTo("NEEDS_REVIEWED_REDACTED_TRACE_EVIDENCE");
        assertThat(evidence.traceSetCount()).isEqualTo(7);
        assertThat(evidence.reviewedTraceSetCount()).isZero();
        assertThat(evidence.reviewedTraceAnchorCount()).isZero();
        assertThat(evidence.reviewPipeline()).extracting(stage -> stage.get("id"))
            .contains("redacted-candidate-discovery", "curation-review", "human-git-review", "release-blocking-promotion");
        assertThat(evidence.standardsAlignment()).extracting(standard -> standard.get("id"))
            .contains("openai-agents-tracing", "mcp-tools-governance", "otel-genai-semconv", "owasp-llm-top-10");
        assertThat(evidence.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("releaseBlockingAllowedNow", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(evidence.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawParameterValues", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false);
        assertThat(evidence.toString())
            .contains("reviewed-trace-evidence", "phase1-core-golden")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void releaseBlockingEvalGateContract_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentReleaseBlockingEvalGateContractResponse>> anonymous =
            controller.releaseBlockingEvalGateContract();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentReleaseBlockingEvalGateContractResponse>> user =
            controller.releaseBlockingEvalGateContract();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void releaseBlockingEvalGateContract_shouldReturnFailClosedContractForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentReleaseBlockingEvalGateContractResponse>> response =
            controller.releaseBlockingEvalGateContract();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentReleaseBlockingEvalGateContractResponse contract = response.getBody().getData();
        assertThat(contract.schemaVersion()).isEqualTo("agent-release-blocking-eval-gate-contract.v1");
        assertThat(contract.contractStatus()).isEqualTo("BLOCKED_BY_REVIEWED_TRACE_EVIDENCE");
        assertThat(contract.releaseBlockingGateDefined()).isTrue();
        assertThat(contract.releaseBlockingEnabled()).isFalse();
        assertThat(contract.ciBlockingEnabled()).isFalse();
        assertThat(contract.releaseGateCanOpenNow()).isFalse();
        assertThat(contract.reviewedEvidenceReady()).isFalse();
        assertThat(contract.gateBundleReleaseEligible()).isFalse();
        assertThat(contract.emptyTraceSets()).isEqualTo(7);
        assertThat(contract.blockedReasons())
            .contains("reviewed-redacted-trace-evidence-missing", "ci-blocking-switch-intentionally-absent");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("releaseBlockingEnabled", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawParameterValues", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false);
        assertThat(contract.toString())
            .contains("release-blocking-gate-contract", "phase1-core-golden")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void evalWorkbenchGateBundleSummary_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchGateBundleSummaryResponse>> anonymous =
            controller.evalWorkbenchGateBundleSummary();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalWorkbenchGateBundleSummaryResponse>> user =
            controller.evalWorkbenchGateBundleSummary();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evalWorkbenchGateBundleSummary_shouldReturnPageReadyCiEvidenceSummaryForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalWorkbenchGateBundleSummaryResponse>> response =
            controller.evalWorkbenchGateBundleSummary();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalWorkbenchGateBundleSummaryResponse summary = response.getBody().getData();
        assertThat(summary.schemaVersion()).isEqualTo("agent-eval-workbench-gate-bundle-summary.v1");
        assertThat(summary.gateVerdict()).isEqualTo("FAIL");
        assertThat(summary.releaseEligible()).isFalse();
        assertThat(summary.traceSetCount()).isEqualTo(7);
        assertThat(summary.traceSetGateRows())
            .filteredOn(row -> row.get("traceSetId").toString().startsWith("memory-rag-"))
            .hasSize(3)
            .allSatisfy(row -> assertThat(row)
                .containsEntry("suiteId", "memory-rag-release-gate")
                .containsEntry("status", "SUITE_RUNTIME_DISABLED_CATALOG_ONLY")
                .containsEntry("gateVerdict", "SUITE_RUNTIME_DISABLED"));
        assertThat(summary.emptyTraceSetIds()).containsExactlyElementsOf(summary.traceSetIds());
        assertThat(summary.bundleSummary())
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("requestTraceIdOverrideAllowed", false)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false);
        assertThat(summary.ciArtifact())
            .containsEntry("path", "target/agent-eval/trace-set-gate-bundle.json")
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(summary.blockerSummary())
            .containsEntry("hasBlockingIssues", true)
            .containsEntry("catalogMutationAllowed", false);
        assertThat(summary.workbenchPolicy())
            .containsEntry("summaryOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requestTraceIdOverrideAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(summary.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(summary.toString())
            .contains("gate-bundle-summary", "phase1-core-golden")
            .doesNotContain("reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void evalWorkbenchTraceSetDetail_shouldRequireAdminUserAndRejectUnknownTraceSet() {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchTraceSetDetailResponse>> anonymous =
            controller.evalWorkbenchTraceSetDetail("phase1-core-golden");

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalWorkbenchTraceSetDetailResponse>> user =
            controller.evalWorkbenchTraceSetDetail("phase1-core-golden");

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalWorkbenchTraceSetDetailResponse>> missing =
            controller.evalWorkbenchTraceSetDetail("missing-trace-set");

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void evalWorkbenchTraceSetDetail_shouldReturnTraceSetDetailForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalWorkbenchTraceSetDetailResponse>> response =
            controller.evalWorkbenchTraceSetDetail("phase1-core-golden");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalWorkbenchTraceSetDetailResponse detail = response.getBody().getData();
        assertThat(detail.schemaVersion()).isEqualTo("agent-eval-workbench-trace-set-detail.v1");
        assertThat(detail.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(detail.status()).isEqualTo("NEEDS_REDACTED_EVIDENCE");
        assertThat(detail.curatedTraceCount()).isZero();
        assertThat(detail.endpointTemplates())
            .containsEntry("workbenchPromotionWorkflow",
                "/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/promotion-workflow")
            .containsEntry("workbenchCatalogPatchReview",
                "/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/catalog-patch-review")
            .containsEntry("workbenchGateBundleSummary",
                "/api/agent/observability/eval/workbench/gate-bundle-summary")
            .containsEntry("promotionWorkflow",
                "/api/agent/observability/eval/trace-sets/phase1-core-golden/promotion-workflow");
        assertThat(detail.detailPolicy())
            .containsEntry("detailOnly", true)
            .containsEntry("candidateDiscoveryExecuted", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(detail.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(detail.toString())
            .contains("phase1-core-golden", "agent-eval-workbench-trace-set-detail.v1")
            .doesNotContain("reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void evalWorkbenchPromotionWorkflow_shouldRequireAdminUserAndRejectUnknownTraceSet() {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchPromotionWorkflowResponse>> anonymous =
            controller.evalWorkbenchPromotionWorkflow("phase1-core-golden", null);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalWorkbenchPromotionWorkflowResponse>> user =
            controller.evalWorkbenchPromotionWorkflow("phase1-core-golden", null);

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalWorkbenchPromotionWorkflowResponse>> missing =
            controller.evalWorkbenchPromotionWorkflow("missing-trace-set", null);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void evalWorkbenchPromotionWorkflow_shouldReturnUiWorkflowForAdminUser() {
        String traceId = "trc_99999999999999999999999999999999";
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_workbench_workflow",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalWorkbenchPromotionWorkflowResponse>> response =
            controller.evalWorkbenchPromotionWorkflow(
                "phase1-core-golden",
                new AgentEvalTraceSetPromotionWorkflowRequest(50, null, null, null, 5)
            );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalWorkbenchPromotionWorkflowResponse workflow = response.getBody().getData();
        assertThat(workflow.schemaVersion()).isEqualTo("agent-eval-workbench-promotion-workflow.v1");
        assertThat(workflow.workflowVerdict()).isEqualTo("READY_FOR_GIT_REVIEW");
        assertThat(workflow.readyForGitReview()).isTrue();
        assertThat(workflow.selectedCandidateTraceIds()).containsExactly(traceId);
        assertThat(workflow.uiSteps()).extracting(step -> step.get("id"))
            .containsExactly(
                "candidate-discovery",
                "curation-review",
                "catalog-patch-proposal",
                "gate-bundle-regeneration"
            );
        assertThat(workflow.patchSummary())
            .containsEntry("readyForGitReview", true)
            .containsEntry("addedTraceCount", 1)
            .containsEntry("catalogMutated", false)
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(workflow.workbenchPolicy())
            .containsEntry("workbenchWrapperOnly", true)
            .containsEntry("candidateDiscoveryExecuted", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(workflow.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(workflow.toString())
            .contains(traceId, "workbench-promotion")
            .doesNotContain("reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void evalWorkbenchCatalogPatchReview_shouldRequireAdminUserAndRejectUnknownTraceSet() {
        ResponseEntity<ApiResponse<AgentEvalWorkbenchCatalogPatchReviewResponse>> anonymous =
            controller.evalWorkbenchCatalogPatchReview("phase1-core-golden", null);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalWorkbenchCatalogPatchReviewResponse>> user =
            controller.evalWorkbenchCatalogPatchReview("phase1-core-golden", null);

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalWorkbenchCatalogPatchReviewResponse>> missing =
            controller.evalWorkbenchCatalogPatchReview("missing-trace-set", null);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void evalWorkbenchCatalogPatchReview_shouldReturnGitReviewModelForAdminUser() {
        String traceId = "trc_bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_workbench_patch_review",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalWorkbenchCatalogPatchReviewResponse>> response =
            controller.evalWorkbenchCatalogPatchReview(
                "phase1-core-golden",
                new AgentEvalSuiteRequest(java.util.List.of(traceId, "secret-token-value"), null, null, null)
            );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalWorkbenchCatalogPatchReviewResponse review = response.getBody().getData();
        assertThat(review.schemaVersion()).isEqualTo("agent-eval-workbench-catalog-patch-review.v1");
        assertThat(review.proposalVerdict()).isEqualTo("READY_FOR_GIT_REVIEW");
        assertThat(review.readyForGitReview()).isTrue();
        assertThat(review.addedTraceIds()).containsExactly(traceId);
        assertThat(review.patchOperations()).hasSize(1);
        assertThat(review.patchOperations().get(0))
            .containsEntry("op", "replace")
            .containsEntry("path", "/0/traceIds")
            .containsEntry("applied", false)
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(review.traceDelta())
            .containsEntry("addedTraceCount", 1)
            .containsEntry("catalogMutated", false)
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(review.workbenchPolicy())
            .containsEntry("catalogPatchReviewOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(review.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawEndpoints", false)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(review.toString())
            .contains(traceId, "catalog-patch-review")
            .doesNotContain("reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void evalTraceSets_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogResponse>> anonymous = controller.evalTraceSets();

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogResponse>> user = controller.evalTraceSets();

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evalTraceSets_shouldReturnCatalogForAdminUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogResponse>> response = controller.evalTraceSets();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalTraceSetCatalogResponse catalog = response.getBody().getData();
        assertThat(catalog.schemaVersion()).isEqualTo("agent-eval-trace-set-catalog.v1");
        assertThat(catalog.traceSets()).extracting(AgentEvalTraceSetDefinition::id)
            .contains("phase1-core-golden", "phase1-redaction-regression", "phase1-high-risk-prewrite");
        assertThat(catalog.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(catalog.toString())
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void evalTraceSetCandidates_shouldRequireAdminUserAndRejectUnknownTraceSet() {
        ResponseEntity<ApiResponse<AgentEvalTraceSetCandidateDiscoveryResponse>> anonymous =
            controller.evalTraceSetCandidates("phase1-core-golden", 50);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetCandidateDiscoveryResponse>> missing =
            controller.evalTraceSetCandidates("missing-trace-set", 50);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().isSuccess()).isFalse();
    }

    @Test
    void evalTraceSetCandidates_shouldReturnRedactedRecommendedCandidatesForAdminUser() {
        String traceId = "trc_44444444444444444444444444444444";
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_trace_set_candidate",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetCandidateDiscoveryResponse>> response =
            controller.evalTraceSetCandidates("phase1-core-golden", 50);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalTraceSetCandidateDiscoveryResponse candidates = response.getBody().getData();
        assertThat(candidates.schemaVersion()).isEqualTo("agent-eval-trace-set-candidates.v1");
        assertThat(candidates.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(candidates.candidateTraceIds()).contains(traceId);
        assertThat(candidates.candidates()).filteredOn(AgentEvalTraceSetCandidate::recommendedForCurationReview)
            .extracting(AgentEvalTraceSetCandidate::traceId)
            .contains(traceId);
        assertThat(candidates.discoveryPolicy())
            .containsEntry("sourceRedactedOnly", true)
            .containsEntry("requiresCurationReview", true)
            .containsEntry("catalogMutationAllowed", false);
        assertThat(candidates.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(candidates.toString())
            .contains(traceId)
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void evalTraceSetGate_shouldRequireAdminUserAndRejectUnknownTraceSet() {
        ResponseEntity<ApiResponse<AgentEvalTraceSetGateArtifact>> anonymous =
            controller.evalTraceSetGate("phase1-core-golden", null);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetGateArtifact>> missing =
            controller.evalTraceSetGate("missing-trace-set", null);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().isSuccess()).isFalse();
    }

    @Test
    void evalTraceSetGate_shouldReturnFailClosedArtifactForEmptyTraceSet() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetGateArtifact>> response = controller.evalTraceSetGate(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(java.util.List.of("trc_request_override_must_not_run"), null, null, null)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalTraceSetGateArtifact artifact = response.getBody().getData();
        assertThat(artifact.schemaVersion()).isEqualTo("agent-eval-trace-set-gate.v1");
        assertThat(artifact.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(artifact.suiteId()).isEqualTo("release-gate-strict");
        assertThat(artifact.pass()).isFalse();
        assertThat(artifact.emptyInput()).isTrue();
        assertThat(artifact.traceIds()).isEmpty();
        assertThat(artifact.gatePolicy())
            .containsEntry("artifactOnly", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false)
            .containsEntry("requestTraceIdsIgnored", true);
        assertThat(artifact.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        String bodyText = artifact.toString();
        assertThat(bodyText)
            .doesNotContain("trc_request_override_must_not_run")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    @Test
    void evalTraceSetCurationReview_shouldRequireAdminUserAndRejectUnknownTraceSet() {
        ResponseEntity<ApiResponse<AgentEvalTraceSetCurationReviewArtifact>> anonymous =
            controller.evalTraceSetCurationReview("phase1-core-golden", null);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetCurationReviewArtifact>> missing =
            controller.evalTraceSetCurationReview("missing-trace-set", null);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().isSuccess()).isFalse();
    }

    @Test
    void evalTraceSetCurationReview_shouldReturnReviewOnlyArtifactForAdminCandidate() {
        String traceId = "trc_33333333333333333333333333333333";
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_trace_set_review",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetCurationReviewArtifact>> response =
            controller.evalTraceSetCurationReview(
                "phase1-core-golden",
                new AgentEvalSuiteRequest(java.util.List.of(traceId, "secret-token-value"), null, null, null)
            );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalTraceSetCurationReviewArtifact review = response.getBody().getData();
        assertThat(review.schemaVersion()).isEqualTo("agent-eval-trace-set-curation-review.v1");
        assertThat(review.reviewVerdict()).isEqualTo("READY_FOR_CATALOG_REVIEW");
        assertThat(review.readyForCatalogReview()).isTrue();
        assertThat(review.catalogMutated()).isFalse();
        assertThat(review.candidateTraceIds()).containsExactly(traceId);
        assertThat(review.candidateGate().pass()).isTrue();
        assertThat(review.curationPolicy())
            .containsEntry("reviewOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("candidateTraceIdsPromotedToCatalog", false)
            .containsEntry("requiresGitReview", true);
        assertThat(review.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(review.toString())
            .contains(traceId)
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    @Test
    void evalTraceSetCatalogPatchProposal_shouldRequireAdminUserAndRejectUnknownTraceSet() {
        ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogPatchProposalArtifact>> anonymous =
            controller.evalTraceSetCatalogPatchProposal("phase1-core-golden", null);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogPatchProposalArtifact>> missing =
            controller.evalTraceSetCatalogPatchProposal("missing-trace-set", null);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().isSuccess()).isFalse();
    }

    @Test
    void evalTraceSetCatalogPatchProposal_shouldReturnReviewOnlyPatchForAdminCandidate() {
        String traceId = "trc_55555555555555555555555555555555";
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_trace_set_patch",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetCatalogPatchProposalArtifact>> response =
            controller.evalTraceSetCatalogPatchProposal(
                "phase1-core-golden",
                new AgentEvalSuiteRequest(java.util.List.of(traceId, "secret-token-value"), null, null, null)
            );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal = response.getBody().getData();
        assertThat(proposal.schemaVersion()).isEqualTo("agent-eval-trace-set-catalog-patch-proposal.v1");
        assertThat(proposal.proposalVerdict()).isEqualTo("READY_FOR_GIT_REVIEW");
        assertThat(proposal.readyForGitReview()).isTrue();
        assertThat(proposal.catalogMutated()).isFalse();
        assertThat(proposal.addedTraceIds()).containsExactly(traceId);
        assertThat(proposal.jsonPatch()).hasSize(1);
        assertThat(proposal.jsonPatch().get(0))
            .containsEntry("op", "replace")
            .containsEntry("path", "/0/traceIds")
            .containsEntry("value", java.util.List.of(traceId));
        assertThat(proposal.proposalPolicy())
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("requiresGitReview", true)
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(proposal.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(proposal.toString())
            .contains(traceId, "observability/eval-trace-sets.json")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    @Test
    void evalTraceSetPromotionWorkflow_shouldRequireAdminUserAndRejectUnknownTraceSet() {
        ResponseEntity<ApiResponse<AgentEvalTraceSetPromotionWorkflowArtifact>> anonymous =
            controller.evalTraceSetPromotionWorkflow("phase1-core-golden", null);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetPromotionWorkflowArtifact>> missing =
            controller.evalTraceSetPromotionWorkflow("missing-trace-set", null);

        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(missing.getBody()).isNotNull();
        assertThat(missing.getBody().isSuccess()).isFalse();
    }

    @Test
    void evalTraceSetPromotionWorkflow_shouldComposeRedactedPromotionArtifactsForAdminUser() {
        String traceId = "trc_77777777777777777777777777777777";
        auditRecorder.record(new com.atlas.audit.AgentAuditEvent(
            "aud_eval_trace_set_workflow",
            java.time.Instant.parse("2026-06-09T00:00:00Z"),
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            com.atlas.tool.execution.SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            java.util.List.of("/api/org-sensitive/pod?token=secret-token-value"),
            com.atlas.tool.annotation.AtlasToolMapping.OperationType.READ,
            false,
            com.atlas.audit.AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            java.util.Map.of("count", 1, "keys", java.util.List.of(java.util.Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetPromotionWorkflowArtifact>> response =
            controller.evalTraceSetPromotionWorkflow(
                "phase1-core-golden",
                new AgentEvalTraceSetPromotionWorkflowRequest(50, null, null, null, 5)
            );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalTraceSetPromotionWorkflowArtifact workflow = response.getBody().getData();
        assertThat(workflow.schemaVersion()).isEqualTo("agent-eval-trace-set-promotion-workflow.v1");
        assertThat(workflow.workflowVerdict()).isEqualTo("READY_FOR_GIT_REVIEW");
        assertThat(workflow.readyForGitReview()).isTrue();
        assertThat(workflow.catalogMutated()).isFalse();
        assertThat(workflow.selectedCandidateTraceIds()).containsExactly(traceId);
        assertThat(workflow.candidateDiscovery().candidateTraceIds()).contains(traceId);
        assertThat(workflow.catalogPatchProposal().readyForGitReview()).isTrue();
        assertThat(workflow.workflowPolicy())
            .containsEntry("workflowOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requiresGitReview", true)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(workflow.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(workflow.toString())
            .contains(traceId, "catalog-patch-proposal", "gate-bundle")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    @Test
    void evalTraceSetGateBundle_shouldRequireAdminUser() {
        ResponseEntity<ApiResponse<AgentEvalTraceSetGateBundleArtifact>> anonymous =
            controller.evalTraceSetGateBundle(null);

        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        userPermissionContext.onLogin("user-token", "alice", "user", Set.of());
        userPermissionContext.bind("user-token", "100002");

        ResponseEntity<ApiResponse<AgentEvalTraceSetGateBundleArtifact>> user =
            controller.evalTraceSetGateBundle(null);

        assertThat(user.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void evalTraceSetGateBundle_shouldReturnCompactFailClosedCiArtifact() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
            "boss", null, "ROLE_SYS_ADMIN", "agent:observe"));

        ResponseEntity<ApiResponse<AgentEvalTraceSetGateBundleArtifact>> response =
            controller.evalTraceSetGateBundle(new AgentEvalSuiteRequest(
                java.util.List.of("trc_request_override_must_not_run"), null, null, null
            ));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        AgentEvalTraceSetGateBundleArtifact bundle = response.getBody().getData();
        assertThat(bundle.schemaVersion()).isEqualTo("agent-eval-trace-set-gate-bundle.v1");
        assertThat(bundle.pass()).isFalse();
        assertThat(bundle.releaseEligible()).isFalse();
        assertThat(bundle.traceSetCount()).isEqualTo(7);
        assertThat(bundle.failedTraceSetIds()).contains("phase1-core-golden", "phase1-redaction-regression");
        assertThat(bundle.traceSetGates())
            .filteredOn(gate -> gate.traceSetId().startsWith("memory-rag-"))
            .hasSize(3)
            .allSatisfy(gate -> {
                assertThat(gate.gateVerdict()).isEqualTo("SUITE_RUNTIME_DISABLED");
                assertThat(gate.suiteGate()).isNull();
            });
        assertThat(bundle.emptyTraceSetIds()).containsExactlyElementsOf(bundle.traceSetIds());
        assertThat(bundle.bundlePolicy())
            .containsEntry("artifactOnly", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false)
            .containsEntry("ciBlockingEnabled", false);
        assertThat(bundle.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(bundle.toString())
            .doesNotContain("trc_request_override_must_not_run")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }
}
