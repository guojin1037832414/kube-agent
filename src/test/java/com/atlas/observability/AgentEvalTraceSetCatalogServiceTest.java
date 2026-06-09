package com.atlas.observability;

import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Versioned eval trace set catalog contract tests.
 */
class AgentEvalTraceSetCatalogServiceTest {

    @Test
    void catalog_shouldLoadVersionedTraceSetsWithoutRawEvidence() {
        AgentEvalTraceSetCatalogService service = service(new InMemoryAgentAuditRecorder());

        AgentEvalTraceSetCatalogResponse catalog = service.catalog();

        assertThat(catalog.schemaVersion()).isEqualTo("agent-eval-trace-set-catalog.v1");
        assertThat(catalog.evaluationVersion()).isEqualTo("deterministic-replay-eval.v1");
        assertThat(catalog.source()).isEqualTo("classpath:observability/eval-trace-sets.json");
        assertThat(catalog.traceSetCount()).isEqualTo(catalog.traceSets().size());
        assertThat(catalog.traceSets()).extracting(AgentEvalTraceSetDefinition::id)
            .containsExactly(
                "phase1-core-golden",
                "phase1-redaction-regression",
                "phase1-high-risk-prewrite",
                "phase1-red-team-safety"
            );
        assertThat(catalog.traceSets()).allSatisfy(definition -> {
            assertThat(definition.phase()).isEqualTo("Phase 1 top-tier kube-manager Agent Core");
            assertThat(definition.traceIds()).isEmpty();
            assertThat(definition.curationPolicy())
                .containsEntry("requiresRealAuditCapture", true)
                .containsEntry("placeholderTraceIds", false)
                .containsEntry("failClosedWhenEmpty", true)
                .containsEntry("requestTraceIdOverrideAllowed", false);
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
    void gate_shouldRejectUnknownTraceSetAndNormalizeIds() {
        AgentEvalTraceSetCatalogService service = service(new InMemoryAgentAuditRecorder());

        assertThat(service.findDefinition("  PHASE1-CORE-GOLDEN  "))
            .map(AgentEvalTraceSetDefinition::suiteId)
            .contains("release-gate-strict");
        assertThat(service.findDefinition("   "))
            .isEmpty();
        assertThat(service.gate("missing-trace-set", new AgentEvalSuiteRequest(List.of("ignored"), 10, 80, true)))
            .isEmpty();
    }

    @Test
    void gate_shouldFailClosedForEmptyTraceSetAndIgnoreRequestTraceIds() {
        AgentEvalTraceSetCatalogService service = service(new InMemoryAgentAuditRecorder());

        AgentEvalTraceSetGateArtifact artifact = service.gate(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of("trc_request_override_must_not_run"), null, null, null)
        ).orElseThrow();

        assertThat(artifact.schemaVersion()).isEqualTo("agent-eval-trace-set-gate.v1");
        assertThat(artifact.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(artifact.suiteId()).isEqualTo("release-gate-strict");
        assertThat(artifact.gateVerdict()).isEqualTo("FAIL");
        assertThat(artifact.pass()).isFalse();
        assertThat(artifact.emptyInput()).isTrue();
        assertThat(artifact.traceIds()).isEmpty();
        assertThat(artifact.suiteGate().schemaVersion()).isEqualTo("agent-eval-suite-gate.v1");
        assertThat(artifact.suiteGate().emptyInput()).isTrue();
        assertThat(artifact.suiteGate().requestedCases()).isZero();
        assertThat(artifact.gatePolicy())
            .containsEntry("artifactOnly", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false)
            .containsEntry("suiteGateEmbedded", true)
            .containsEntry("traceSetTraceIdsOverridden", false)
            .containsEntry("requestTraceIdsIgnored", true)
            .containsEntry("failClosedWhenEmpty", true);
        assertThat(artifact.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("deterministic", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(artifact.toString())
            .doesNotContain("trc_request_override_must_not_run")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    @Test
    void gateBundle_shouldProduceCompactCiArtifactForWholeCatalog() {
        AgentEvalTraceSetCatalogService service = service(new InMemoryAgentAuditRecorder());

        AgentEvalTraceSetGateBundleArtifact bundle = service.gateBundle(
            new AgentEvalSuiteRequest(List.of("trc_request_override_must_not_run"), null, null, null)
        );

        assertThat(bundle.schemaVersion()).isEqualTo("agent-eval-trace-set-gate-bundle.v1");
        assertThat(bundle.source()).isEqualTo("classpath:observability/eval-trace-sets.json");
        assertThat(bundle.gateVerdict()).isEqualTo("FAIL");
        assertThat(bundle.pass()).isFalse();
        assertThat(bundle.releaseEligible()).isFalse();
        assertThat(bundle.traceSetCount()).isEqualTo(4);
        assertThat(bundle.failedTraceSets()).isEqualTo(4);
        assertThat(bundle.emptyTraceSets()).isEqualTo(4);
        assertThat(bundle.traceSetIds())
            .containsExactly("phase1-core-golden", "phase1-redaction-regression", "phase1-high-risk-prewrite",
                "phase1-red-team-safety");
        assertThat(bundle.failedTraceSetIds()).containsExactlyElementsOf(bundle.traceSetIds());
        assertThat(bundle.emptyTraceSetIds()).containsExactlyElementsOf(bundle.traceSetIds());
        assertThat(bundle.traceSetGates()).hasSize(4);
        assertThat(bundle.traceSetGates()).allSatisfy(gate -> {
            assertThat(gate.suiteGate().schemaVersion()).isEqualTo("agent-eval-suite-gate.v1");
            assertThat(gate.emptyInput()).isTrue();
            assertThat(gate.traceIds()).isEmpty();
        });
        assertThat(bundle.bundlePolicy())
            .containsEntry("artifactOnly", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false)
            .containsEntry("ciArtifactPath", "target/agent-eval/trace-set-gate-bundle.json")
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("failClosedWhenEmpty", true)
            .containsEntry("requestTraceIdOverrideAllowed", false);
        assertThat(bundle.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("deterministic", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(bundle.toString())
            .doesNotContain("trc_request_override_must_not_run")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    private AgentEvalTraceSetCatalogService service(InMemoryAgentAuditRecorder recorder) {
        AgentEvalReportService evalReportService = new AgentEvalReportService(new AgentReplayTimelineService(recorder));
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        return new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
    }
}
