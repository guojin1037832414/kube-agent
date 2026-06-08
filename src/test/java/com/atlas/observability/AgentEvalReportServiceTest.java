package com.atlas.observability;

import com.atlas.audit.AgentAuditEvent;
import com.atlas.audit.AgentAuditOutcome;
import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic Agent eval report contract tests.
 */
class AgentEvalReportServiceTest {

    @Test
    void evaluateTrace_shouldPassForReadOnlySuccessReplay() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_read",
            "trc_read",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            true,
            true
        ));

        AgentEvalReportResponse report = service.evaluateTrace("trc_read", 10);

        assertThat(report.schemaVersion()).isEqualTo("agent-eval-report.v1");
        assertThat(report.evaluationVersion()).isEqualTo("deterministic-replay-eval.v1");
        assertThat(report.timelineSchemaVersion()).isEqualTo("agent-replay-timeline.v1");
        assertThat(report.verdict()).isEqualTo("PASS");
        assertThat(report.pass()).isTrue();
        assertThat(report.score()).isEqualTo(100);
        assertThat(report.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("deterministic", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false);
        assertThat(check(report, "HIGH_RISK_PREWRITE_EVIDENCE").status()).isEqualTo("PASS");
    }

    @Test
    void evaluateTrace_shouldFailWhenExecutedHighRiskFinalHasNoPreExecutionEvidence() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_create",
            "trc_create",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.CREATE,
            true,
            AgentAuditOutcome.SUCCESS,
            true,
            true
        ));

        AgentEvalReportResponse report = service.evaluateTrace("trc_create", 10);

        assertThat(report.verdict()).isEqualTo("FAIL");
        assertThat(report.pass()).isFalse();
        AgentEvalCheck check = check(report, "HIGH_RISK_PREWRITE_EVIDENCE");
        assertThat(check.status()).isEqualTo("FAIL");
        assertThat(check.evidence().get("missingPreExecutionAuditIds"))
            .isInstanceOfSatisfying(Iterable.class, missing -> assertThat(missing)
                .containsExactly("aud_create"));
    }

    @Test
    void evaluateTrace_shouldPassForHighRiskPreExecutionThenFinalEvidence() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_create",
            "trc_prewrite",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.CREATE,
            true,
            AgentAuditOutcome.PREPARED,
            false,
            false
        ));
        recorder.record(event(
            "aud_create",
            "trc_prewrite",
            Instant.parse("2026-06-09T00:00:05Z"),
            AtlasToolMapping.OperationType.CREATE,
            true,
            AgentAuditOutcome.SUCCESS,
            true,
            true
        ));

        AgentEvalReportResponse report = service.evaluateTrace("trc_prewrite", 10);

        assertThat(report.verdict()).isEqualTo("PASS");
        assertThat(check(report, "PHASE_SEQUENCE").status()).isEqualTo("PASS");
        assertThat(check(report, "HIGH_RISK_PREWRITE_EVIDENCE").status()).isEqualTo("PASS");
        assertThat(check(report, "HIGH_RISK_CONFIRMATION_MARKER").status()).isEqualTo("PASS");
    }

    @Test
    void evaluateTrace_shouldWarnForBlockedErrorBusinessFailureAndTruncatedReplay() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_blocked",
            "trc_warning",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.CREATE,
            true,
            AgentAuditOutcome.BLOCKED,
            false,
            false
        ));
        recorder.record(event(
            "aud_error",
            "trc_warning",
            Instant.parse("2026-06-09T00:00:01Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.ERROR,
            true,
            false
        ));
        recorder.record(event(
            "aud_business_failure",
            "trc_warning",
            Instant.parse("2026-06-09T00:00:02Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.BUSINESS_FAILURE,
            true,
            false
        ));

        AgentEvalReportResponse report = service.evaluateTrace("trc_warning", 2);

        assertThat(report.verdict()).isEqualTo("PASS_WITH_WARNINGS");
        assertThat(report.truncated()).isTrue();
        assertThat(check(report, "OUTCOME_HEALTH").status()).isEqualTo("WARN");
        assertThat(check(report, "REPLAY_NOT_TRUNCATED").status()).isEqualTo("WARN");
        assertThat(check(report, "HIGH_RISK_PREWRITE_EVIDENCE").status()).isEqualTo("PASS");
    }

    @Test
    void evaluateTrace_shouldFailImpossibleSuccessWithoutExecution() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_impossible",
            "trc_impossible",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            false,
            true
        ));

        AgentEvalReportResponse report = service.evaluateTrace("trc_impossible", 10);

        assertThat(report.verdict()).isEqualTo("FAIL");
        assertThat(check(report, "EXECUTION_SEMANTICS").status()).isEqualTo("FAIL");
    }

    @Test
    void evaluateTrace_shouldRemainRedactedEvenWhenAuditContainsSensitiveRawValues() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(new AgentAuditEvent(
            "aud_sensitive",
            Instant.parse("2026-06-09T00:00:00Z"),
            "trc_sensitive",
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            List.of("/api/org-sensitive/resource?token=secret-token-value"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            true,
            true,
            "reason token=secret-token-value",
            Map.of("count", 1, "keys", List.of(Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));

        AgentEvalReportResponse report = service.evaluateTrace("trc_sensitive", 10);

        assertThat(report.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawEndpoints", false)
            .containsEntry("containsRawParameterValues", false);
        assertThat(report.toString())
            .contains("aud_sensitive", "trc_sensitive", "<protected>")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void evaluateTrace_shouldBeDeterministicExceptGeneratedAt() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_read",
            "trc_deterministic",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            true,
            true
        ));

        AgentEvalReportResponse first = service.evaluateTrace("trc_deterministic", 10);
        AgentEvalReportResponse second = service.evaluateTrace("trc_deterministic", 10);

        assertThat(second.verdict()).isEqualTo(first.verdict());
        assertThat(second.score()).isEqualTo(first.score());
        assertThat(second.summary()).isEqualTo(first.summary());
        assertThat(second.privacy()).isEqualTo(first.privacy());
        assertThat(second.checks()).isEqualTo(first.checks());
    }

    @Test
    void evaluateSuite_shouldPassWhenAllReportsPassAndMeetMinimumScore() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_read_1",
            "trc_suite_one",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            true,
            true
        ));
        recorder.record(event(
            "aud_read_2",
            "trc_suite_two",
            Instant.parse("2026-06-09T00:00:01Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            true,
            true
        ));

        AgentEvalSuiteResponse suite = service.evaluateSuite(
            List.of("trc_suite_one", "trc_suite_two", "trc_suite_one"),
            10,
            90,
            true
        );

        assertThat(suite.schemaVersion()).isEqualTo("agent-eval-suite.v1");
        assertThat(suite.gateVerdict()).isEqualTo("PASS");
        assertThat(suite.pass()).isTrue();
        assertThat(suite.traceIds()).containsExactly("trc_suite_one", "trc_suite_two");
        assertThat(suite.summary())
            .containsEntry("requestedCases", 2)
            .containsEntry("caseCount", 2)
            .containsEntry("evaluatedCases", 2)
            .containsEntry("maxCases", AgentEvalReportService.MAX_SUITE_CASES)
            .containsEntry("caseLimitExceeded", false)
            .containsEntry("passedReports", 2)
            .containsEntry("failedReports", 0)
            .containsEntry("warningReports", 0)
            .containsEntry("minimumScore", 100);
        assertThat(suite.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("deterministic", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false);
    }

    @Test
    void evaluateSuite_shouldFailForFailedReportsWarningsWhenStrictAndEmptyInput() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_missing_prewrite",
            "trc_suite_fail",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.CREATE,
            true,
            AgentAuditOutcome.SUCCESS,
            true,
            true
        ));
        recorder.record(event(
            "aud_error",
            "trc_suite_warning",
            Instant.parse("2026-06-09T00:00:01Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.ERROR,
            true,
            false
        ));

        AgentEvalSuiteResponse suite = service.evaluateSuite(
            List.of("trc_suite_fail", "trc_suite_warning"),
            10,
            80,
            true
        );

        assertThat(suite.gateVerdict()).isEqualTo("FAIL");
        assertThat(suite.pass()).isFalse();
        assertThat(suite.summary())
            .containsEntry("caseCount", 2)
            .containsEntry("failedReports", 1)
            .containsEntry("warningReports", 1);
        assertThat(suite.summary().get("failedTraceIds"))
            .isInstanceOfSatisfying(Iterable.class, failedTraceIds -> assertThat(failedTraceIds)
                .containsExactly("trc_suite_fail"));
        assertThat(suite.summary().get("warningTraceIds"))
            .isInstanceOfSatisfying(Iterable.class, warningTraceIds -> assertThat(warningTraceIds)
                .containsExactly("trc_suite_warning"));

        AgentEvalSuiteResponse empty = service.evaluateSuite(List.of(" ", ""), 10, 80, true);

        assertThat(empty.gateVerdict()).isEqualTo("FAIL");
        assertThat(empty.pass()).isFalse();
        assertThat(empty.summary())
            .containsEntry("caseCount", 0)
            .containsEntry("emptyInput", true);
    }

    @Test
    void evaluateSuite_shouldAllowWarningReportsOnlyWhenPolicyIsLoose() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_warning",
            "trc_suite_warning_policy",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.ERROR,
            true,
            false
        ));

        AgentEvalSuiteResponse strict = service.evaluateSuite(
            List.of("trc_suite_warning_policy"),
            10,
            80,
            true
        );
        AgentEvalSuiteResponse loose = service.evaluateSuite(
            List.of("trc_suite_warning_policy"),
            10,
            80,
            false
        );

        assertThat(strict.pass()).isFalse();
        assertThat(strict.gateVerdict()).isEqualTo("FAIL");
        assertThat(strict.failOnWarnings()).isTrue();
        assertThat(loose.pass()).isTrue();
        assertThat(loose.gateVerdict()).isEqualTo("PASS");
        assertThat(loose.failOnWarnings()).isFalse();
        assertThat(loose.summary())
            .containsEntry("warningReports", 1)
            .containsEntry("failedReports", 0);
    }

    @Test
    void evaluateSuite_shouldFailWhenCaseLimitIsExceededAndSkipOverflowTraceIds() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        List<String> traceIds = java.util.stream.IntStream.rangeClosed(1, AgentEvalReportService.MAX_SUITE_CASES + 1)
            .mapToObj(index -> "trc_case_" + index)
            .toList();
        for (int i = 0; i < traceIds.size(); i++) {
            recorder.record(event(
                "aud_case_" + i,
                traceIds.get(i),
                Instant.parse("2026-06-09T00:00:00Z").plusSeconds(i),
                AtlasToolMapping.OperationType.READ,
                false,
                AgentAuditOutcome.SUCCESS,
                true,
                true
            ));
        }

        AgentEvalSuiteResponse suite = service.evaluateSuite(traceIds, 10, 80, false);

        assertThat(suite.pass()).isFalse();
        assertThat(suite.gateVerdict()).isEqualTo("FAIL");
        assertThat(suite.traceIds()).hasSize(AgentEvalReportService.MAX_SUITE_CASES);
        assertThat(suite.traceIds()).doesNotContain("trc_case_" + (AgentEvalReportService.MAX_SUITE_CASES + 1));
        assertThat(suite.reports()).hasSize(AgentEvalReportService.MAX_SUITE_CASES);
        assertThat(suite.summary())
            .containsEntry("requestedCases", AgentEvalReportService.MAX_SUITE_CASES + 1)
            .containsEntry("caseCount", AgentEvalReportService.MAX_SUITE_CASES)
            .containsEntry("evaluatedCases", AgentEvalReportService.MAX_SUITE_CASES)
            .containsEntry("maxCases", AgentEvalReportService.MAX_SUITE_CASES)
            .containsEntry("caseLimitExceeded", true)
            .containsEntry("failedReports", 0);
        assertThat(suite.summary().get("skippedTraceIds"))
            .isInstanceOfSatisfying(Iterable.class, skippedTraceIds -> assertThat(skippedTraceIds)
                .containsExactly("trc_case_" + (AgentEvalReportService.MAX_SUITE_CASES + 1)));
    }

    @Test
    void evaluateSuite_shouldBoundLimitAndMinimumScore() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService service = service(recorder);
        recorder.record(event(
            "aud_bounds",
            "trc_suite_bounds",
            Instant.parse("2026-06-09T00:00:00Z"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            true,
            true
        ));

        AgentEvalSuiteResponse negative = service.evaluateSuite(List.of("trc_suite_bounds"), -100, -10, true);
        AgentEvalSuiteResponse tooLarge = service.evaluateSuite(List.of("trc_suite_bounds"), 999, 999, true);

        assertThat(negative.maxResults()).isEqualTo(1);
        assertThat(negative.minimumScore()).isZero();
        assertThat(negative.reports()).extracting(AgentEvalReportResponse::maxResults)
            .containsExactly(1);
        assertThat(tooLarge.maxResults()).isEqualTo(AgentEvalReportService.MAX_TRACE_MAX_RESULTS);
        assertThat(tooLarge.minimumScore()).isEqualTo(100);
        assertThat(tooLarge.reports()).extracting(AgentEvalReportResponse::maxResults)
            .containsExactly(AgentEvalReportService.MAX_TRACE_MAX_RESULTS);
    }

    private AgentEvalReportService service(InMemoryAgentAuditRecorder recorder) {
        return new AgentEvalReportService(new AgentReplayTimelineService(recorder));
    }

    private AgentEvalCheck check(AgentEvalReportResponse report, String code) {
        return report.checks().stream()
            .filter(candidate -> code.equals(candidate.code()))
            .findFirst()
            .orElseThrow();
    }

    private AgentAuditEvent event(String auditId,
                                  String traceId,
                                  Instant occurredAt,
                                  AtlasToolMapping.OperationType operationType,
                                  boolean requiresConfirmation,
                                  AgentAuditOutcome outcome,
                                  boolean executed,
                                  boolean success) {
        return new AgentAuditEvent(
            auditId,
            occurredAt,
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            SafeToolExecutionSource.GRAPH_TOOL_CALL,
            operationType == AtlasToolMapping.OperationType.READ ? "GET" : "POST",
            List.of("/api/org-sensitive/tool?token=secret-token-value"),
            operationType,
            requiresConfirmation,
            outcome,
            executed,
            success,
            "reason token=secret-token-value",
            Map.of("count", 1, "keys", List.of(Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        );
    }
}
