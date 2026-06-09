package com.atlas.observability;

import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.auth.AgentPrincipalResolver;
import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.ApiResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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
    private final ObservabilityController controller = new ObservabilityController(
        new AgentMetricsService(new SimpleMeterRegistry()),
        auditRecorder,
        auditRecorder,
        replayTimelineService,
        evalReportService,
        evalSuiteCatalogService,
        evalTraceSetCatalogService,
        traceSetCandidateDiscoveryService,
        new AgentPrincipalResolver(userPermissionContext)
    );

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
