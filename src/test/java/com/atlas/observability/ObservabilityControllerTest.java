package com.atlas.observability;

import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.ApiResponse;
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
    private final AgentEvalWorkbenchTraceSetDetailService evalWorkbenchTraceSetDetailService =
        new AgentEvalWorkbenchTraceSetDetailService(evalTraceSetCatalogService);
    private final AgentEvalWorkbenchPromotionWorkflowService evalWorkbenchPromotionWorkflowService =
        new AgentEvalWorkbenchPromotionWorkflowService(evalTraceSetCatalogService, traceSetPromotionWorkflowService);
    private final AgentEvalWorkbenchCatalogPatchReviewService evalWorkbenchCatalogPatchReviewService =
        new AgentEvalWorkbenchCatalogPatchReviewService(evalTraceSetCatalogService);
    private final AgentEvalWorkbenchGateBundleSummaryService evalWorkbenchGateBundleSummaryService =
        new AgentEvalWorkbenchGateBundleSummaryService(evalTraceSetCatalogService);
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
    private final ObservabilityController controller = new ObservabilityController(
        new AgentMetricsService(new SimpleMeterRegistry()),
        kubeManagerHttpOutletHealthSummaryService,
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
            .contains("core-safety-smoke", "high-risk-prewrite", "redaction-regression", "release-gate-strict");
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
        assertThat(overview.traceSetCount()).isEqualTo(4);
        assertThat(overview.traceSetNeedsEvidenceCount()).isEqualTo(4);
        assertThat(overview.traceSets()).extracting(AgentEvalWorkbenchTraceSetView::status)
            .containsOnly("NEEDS_REDACTED_EVIDENCE");
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
        assertThat(summary.traceSetCount()).isEqualTo(4);
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
        assertThat(bundle.traceSetCount()).isEqualTo(4);
        assertThat(bundle.failedTraceSetIds()).contains("phase1-core-golden", "phase1-redaction-regression");
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
