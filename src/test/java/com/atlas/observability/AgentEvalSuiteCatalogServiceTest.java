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
 * Named eval suite catalog contract tests.
 */
class AgentEvalSuiteCatalogServiceTest {

    @Test
    void catalog_shouldExposeDeterministicBuiltInSuitesWithoutRawEvidence() {
        AgentEvalSuiteCatalogService service = service(new InMemoryAgentAuditRecorder());

        AgentEvalSuiteCatalogResponse catalog = service.catalog();

        assertThat(catalog.schemaVersion()).isEqualTo("agent-eval-suite-catalog.v1");
        assertThat(catalog.evaluationVersion()).isEqualTo("deterministic-replay-eval.v1");
        assertThat(catalog.suiteCount()).isEqualTo(catalog.suites().size());
        assertThat(catalog.suites()).extracting(AgentEvalSuiteDefinition::id)
            .containsExactly("core-safety-smoke", "high-risk-prewrite", "redaction-regression", "release-gate-strict");
        assertThat(catalog.suites()).allSatisfy(definition -> {
            assertThat(definition.phase()).isEqualTo("Phase 1 top-tier kube-manager Agent Core");
            assertThat(definition.defaultLimit()).isEqualTo(AgentEvalReportService.DEFAULT_TRACE_MAX_RESULTS);
            assertThat(definition.maxCases()).isEqualTo(AgentEvalReportService.MAX_SUITE_CASES);
            assertThat(definition.guarantees())
                .containsEntry("redactedOnly", true)
                .containsEntry("llmUsed", false)
                .containsEntry("externalCalls", false)
                .containsEntry("toolExecution", false)
                .containsEntry("kubeManagerCalls", false);
        });
        assertThat(catalog.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawParameterValues", false);
        assertThat(catalog.toString())
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }

    @Test
    void run_shouldReturnEmptyOptionalForUnknownSuite() {
        AgentEvalSuiteCatalogService service = service(new InMemoryAgentAuditRecorder());

        assertThat(service.run("missing-suite", new AgentEvalSuiteRequest(List.of("trc"), 10, 80, true)))
            .isEmpty();
        assertThat(service.findDefinition("   "))
            .isEmpty();
    }

    @Test
    void run_shouldApplyNamedDefaultsAndDelegateToSuiteGate() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        recorder.record(event("aud_named", "trc_named", AtlasToolMapping.OperationType.READ));
        AgentEvalSuiteCatalogService service = service(recorder);

        AgentEvalSuiteRunResponse run = service.run(
            " CORE-SAFETY-SMOKE ",
            new AgentEvalSuiteRequest(List.of("trc_named"), null, null, null)
        ).orElseThrow();

        assertThat(run.schemaVersion()).isEqualTo("agent-eval-suite-run.v1");
        assertThat(run.suiteId()).isEqualTo("core-safety-smoke");
        assertThat(run.definition().defaultMinimumScore()).isEqualTo(80);
        assertThat(run.report().gateVerdict()).isEqualTo("PASS");
        assertThat(run.report().traceIds()).containsExactly("trc_named");
        assertThat(run.report().minimumScore()).isEqualTo(80);
        assertThat(run.report().failOnWarnings()).isTrue();
        assertThat(run.runPolicy())
            .containsEntry("definitionDefaultsApplied", true)
            .containsEntry("effectiveLimit", AgentEvalReportService.DEFAULT_TRACE_MAX_RESULTS)
            .containsEntry("effectiveMinimumScore", 80)
            .containsEntry("maxCases", AgentEvalReportService.MAX_SUITE_CASES);
        assertThat(run.privacy())
            .containsEntry("deterministic", true)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
    }

    @Test
    void gate_shouldReturnCompactCiArtifactWithoutEmbeddedReportsOrReplay() {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        recorder.record(event("aud_gate", "trc_gate", AtlasToolMapping.OperationType.READ));
        AgentEvalSuiteCatalogService service = service(recorder);

        AgentEvalSuiteGateArtifact artifact = service.gate(
            "release-gate-strict",
            new AgentEvalSuiteRequest(List.of("trc_gate"), null, null, null)
        ).orElseThrow();

        assertThat(artifact.schemaVersion()).isEqualTo("agent-eval-suite-gate.v1");
        assertThat(artifact.suiteId()).isEqualTo("release-gate-strict");
        assertThat(artifact.gateVerdict()).isEqualTo("PASS");
        assertThat(artifact.pass()).isTrue();
        assertThat(artifact.requiredMinimumScore()).isEqualTo(90);
        assertThat(artifact.observedMinimumScore()).isEqualTo(100);
        assertThat(artifact.observedAverageScore()).isEqualTo(100.0);
        assertThat(artifact.requestedCases()).isEqualTo(1);
        assertThat(artifact.evaluatedCases()).isEqualTo(1);
        assertThat(artifact.traceIds()).containsExactly("trc_gate");
        assertThat(artifact.failedTraceIds()).isEmpty();
        assertThat(artifact.warningTraceIds()).isEmpty();
        assertThat(artifact.gatePolicy())
            .containsEntry("artifactOnly", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false)
            .containsEntry("suiteId", "release-gate-strict");
        assertThat(artifact.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("deterministic", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(artifact.toString())
            .contains("trc_gate")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    private AgentEvalSuiteCatalogService service(InMemoryAgentAuditRecorder recorder) {
        AgentEvalReportService evalReportService = new AgentEvalReportService(new AgentReplayTimelineService(recorder));
        return new AgentEvalSuiteCatalogService(evalReportService);
    }

    private AgentAuditEvent event(String auditId, String traceId, AtlasToolMapping.OperationType operationType) {
        return new AgentAuditEvent(
            auditId,
            Instant.parse("2026-06-09T00:00:00Z"),
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
        );
    }
}
