package com.atlas.observability;

import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.audit.AgentAuditEvent;
import com.atlas.audit.AgentAuditOutcome;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eval workbench gate bundle summary contract tests.
 */
class AgentEvalWorkbenchGateBundleSummaryServiceTest {

    @Test
    void summary_shouldBuildFrontendReleaseGateSummaryWithoutExecutionAuthority() {
        AgentReplayTimelineService replayTimelineService =
            new AgentReplayTimelineService(new InMemoryAgentAuditRecorder());
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalWorkbenchGateBundleSummaryService service =
            new AgentEvalWorkbenchGateBundleSummaryService(traceSetCatalogService);

        AgentEvalWorkbenchGateBundleSummaryResponse summary = service.summary();

        assertThat(summary.schemaVersion()).isEqualTo("agent-eval-workbench-gate-bundle-summary.v1");
        assertThat(summary.evaluationVersion()).isEqualTo("deterministic-replay-eval.v1");
        assertThat(summary.gateVerdict()).isEqualTo("FAIL");
        assertThat(summary.pass()).isFalse();
        assertThat(summary.releaseEligible()).isFalse();
        assertThat(summary.traceSetCount()).isEqualTo(7);
        assertThat(summary.emptyTraceSets()).isEqualTo(7);
        assertThat(summary.readyForCiBlockingTraceSets()).isZero();
        assertThat(summary.traceSetIds()).containsExactly(
            "phase1-core-golden",
            "phase1-redaction-regression",
            "phase1-high-risk-prewrite",
            "phase1-red-team-safety",
            "memory-rag-citation-fidelity",
            "memory-rag-privacy-tenant",
            "memory-rag-lifecycle-policy"
        );
        assertThat(summary.traceSets()).allSatisfy(traceSet -> {
            assertThat(traceSet.readyForCiBlocking()).isFalse();
            assertThat(traceSet.policy())
                .containsEntry("runtimeCatalogWrite", false)
                .containsEntry("toolExecution", false)
                .containsEntry("kubeManagerCalls", false);
        });
        assertThat(summary.traceSets())
            .filteredOn(traceSet -> traceSet.id().startsWith("phase1-"))
            .allSatisfy(traceSet -> assertThat(traceSet.status()).isEqualTo("NEEDS_REDACTED_EVIDENCE"));
        assertThat(summary.traceSets())
            .filteredOn(traceSet -> traceSet.id().startsWith("memory-rag-"))
            .allSatisfy(traceSet -> {
                assertThat(traceSet.status()).isEqualTo("SUITE_RUNTIME_DISABLED_CATALOG_ONLY");
                assertThat(traceSet.nextAction()).isEqualTo("keep-catalog-only-until-reviewed-runtime-promotion");
                assertThat(traceSet.policy())
                    .containsEntry("suiteRuntimeDisabled", true)
                    .containsEntry("runtimeExecutionAllowed", false)
                    .containsEntry("retrievalRuntimeAllowed", false)
                    .containsEntry("traceSetGateRuntimeDisabled", true);
            });
        assertThat(summary.bundleSummary())
            .containsEntry("gateVerdict", "FAIL")
            .containsEntry("releaseEligible", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("requestTraceIdOverrideAllowed", false)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false);
        assertThat(summary.traceSetGateRows()).hasSize(7);
        assertThat(summary.traceSetGateRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("pass", false)
            .containsEntry("emptyInput", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false));
        assertThat(summary.traceSetGateRows())
            .filteredOn(row -> row.get("traceSetId").toString().startsWith("phase1-"))
            .allSatisfy(row -> assertThat(row).containsEntry("status", "NEEDS_REDACTED_EVIDENCE"));
        assertThat(summary.traceSetGateRows())
            .filteredOn(row -> row.get("traceSetId").toString().startsWith("memory-rag-"))
            .hasSize(3)
            .allSatisfy(row -> assertThat(row)
                .containsEntry("suiteId", "memory-rag-release-gate")
                .containsEntry("status", "SUITE_RUNTIME_DISABLED_CATALOG_ONLY")
                .containsEntry("gateVerdict", "SUITE_RUNTIME_DISABLED")
                .containsEntry("pass", false)
                .containsEntry("emptyInput", true));
        assertThat(summary.ciArtifact())
            .containsEntry("path", "target/agent-eval/trace-set-gate-bundle.json")
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requestTraceIdOverrideAllowed", false);
        assertThat(summary.blockerSummary())
            .containsEntry("hasBlockingIssues", true)
            .containsEntry("ciBlockingDisabled", true)
            .containsEntry("catalogMutationAllowed", false);
        assertThat(summary.nextActions())
            .contains(
                "discover-redacted-candidates",
                "open-catalog-patch-review",
                "regenerate-gate-bundle-after-reviewed-merge",
                "keep-ci-blocking-disabled-until-reviewed-real-evidence"
            );
        assertThat(summary.endpointTemplates())
            .containsEntry("gateBundleSummary",
                "/api/agent/observability/eval/workbench/gate-bundle-summary")
            .containsEntry("rawGateBundle",
                "/api/agent/observability/eval/trace-sets/gate-bundle");
        assertThat(summary.workbenchPolicy())
            .containsEntry("summaryOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requestTraceIdOverrideAllowed", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(summary.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawEndpoints", false)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(summary.toString())
            .contains("agent-eval-workbench-gate-bundle-summary.v1", "phase1-core-golden")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    @Test
    void summary_shouldExposeOnlyRedactedTraceAnchorsWhenCatalogHasRealTraceIds() {
        String traceId = "trc_99999999999999999999999999999999";
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        auditRecorder.record(new AgentAuditEvent(
            "aud_gate_bundle_summary_real_anchor",
            Instant.parse("2026-06-09T00:00:00Z"),
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            List.of("/api/org-sensitive/pod?token=secret-token-value"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            Map.of("count", 1, "keys", List.of(Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(auditRecorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalSuiteGateArtifact suiteGate = suiteCatalogService.gate(
            "release-gate-strict",
            new AgentEvalSuiteRequest(List.of(traceId), null, null, null)
        ).orElseThrow();
        AgentEvalTraceSetDefinition definition = AgentEvalTraceSetDefinition.of(
            "phase1-real-anchor",
            "Phase 1 Real Anchor",
            "A reviewed redacted trace anchor for gate bundle summary serialization.",
            "Phase 1 top-tier kube-manager Agent Core",
            "release-gate-strict",
            List.of(traceId),
            List.of("Persisted redacted replay evidence only."),
            List.of("phase1", "summary", "real-anchor"),
            Map.of("requestTraceIdOverrideAllowed", false),
            Map.ofEntries(
                Map.entry("redactedOnly", true),
                Map.entry("containsRawPrincipal", false),
                Map.entry("containsRawOrganization", false),
                Map.entry("containsRawConversation", false),
                Map.entry("containsRawEndpoints", false),
                Map.entry("containsRawReason", false),
                Map.entry("containsRawParameterValues", false),
                Map.entry("deterministic", true),
                Map.entry("llmUsed", false),
                Map.entry("externalCalls", false),
                Map.entry("toolExecution", false),
                Map.entry("kubeManagerCalls", false)
            )
        );
        AgentEvalTraceSetGateArtifact traceSetGate = AgentEvalTraceSetGateArtifact.from(
            definition,
            suiteGate,
            null,
            "test:real-redacted-anchor"
        );
        AgentEvalTraceSetGateBundleArtifact bundle = AgentEvalTraceSetGateBundleArtifact.of(
            "test:real-redacted-anchor",
            List.of(traceSetGate)
        );
        AgentEvalTraceSetCatalogResponse catalog = AgentEvalTraceSetCatalogResponse.of(
            "test:real-redacted-anchor",
            List.of(definition),
            definition.guarantees()
        );

        AgentEvalWorkbenchGateBundleSummaryResponse summary =
            AgentEvalWorkbenchGateBundleSummaryResponse.from(catalog, bundle);

        assertThat(summary.releaseEligible()).isTrue();
        assertThat(summary.traceSetIds()).containsExactly("phase1-real-anchor");
        assertThat(summary.gateBundle().traceSetGates().get(0).traceIds()).containsExactly(traceId);
        assertThat(summary.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawEndpoints", false)
            .containsEntry("containsRawParameterValues", false);
        assertThat(summary.toString())
            .contains(traceId, "phase1-real-anchor")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }
}
