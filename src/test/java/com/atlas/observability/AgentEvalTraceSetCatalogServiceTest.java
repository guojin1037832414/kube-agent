package com.atlas.observability;

import com.atlas.audit.AgentAuditEvent;
import com.atlas.audit.AgentAuditOutcome;
import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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

    @Test
    void curationReview_shouldMarkPassingCandidatesReadyForCatalogReviewWithoutMutatingCatalog() {
        String traceId = "trc_11111111111111111111111111111111";
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        recordReadEvidence(recorder, traceId);
        AgentEvalTraceSetCatalogService service = service(recorder);

        AgentEvalTraceSetCurationReviewArtifact review = service.curationReview(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of(traceId), null, null, null)
        ).orElseThrow();

        assertThat(review.schemaVersion()).isEqualTo("agent-eval-trace-set-curation-review.v1");
        assertThat(review.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(review.suiteId()).isEqualTo("release-gate-strict");
        assertThat(review.reviewVerdict()).isEqualTo("READY_FOR_CATALOG_REVIEW");
        assertThat(review.readyForCatalogReview()).isTrue();
        assertThat(review.catalogMutated()).isFalse();
        assertThat(review.originalTraceSetTraceCount()).isZero();
        assertThat(review.candidateTraceIds()).containsExactly(traceId);
        assertThat(review.candidateGate().pass()).isTrue();
        assertThat(review.curationPolicy())
            .containsEntry("reviewOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("candidateTraceIdsPromotedToCatalog", false)
            .containsEntry("requiresHumanReview", true)
            .containsEntry("requiresGitReview", true)
            .containsEntry("requiresPersistedRedactedReplayEvidence", true)
            .containsEntry("readyForCatalogReview", true);
        assertThat(service.findDefinition("phase1-core-golden").orElseThrow().traceIds()).isEmpty();
        assertThat(review.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("deterministic", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(review.toString())
            .contains(traceId)
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
    }

    @Test
    void curationReview_shouldFilterNonW3cTraceCandidatesAndFailClosedWhenEmpty() {
        AgentEvalTraceSetCatalogService service = service(new InMemoryAgentAuditRecorder());

        AgentEvalTraceSetCurationReviewArtifact review = service.curationReview(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of(
                "trc_not_w3c",
                "secret-token-value",
                "trc_22222222222222222222222222222222",
                "trc_22222222222222222222222222222222"
            ), null, null, null)
        ).orElseThrow();

        assertThat(review.reviewVerdict()).isEqualTo("REJECT_EVAL_GATE_FAILED");
        assertThat(review.readyForCatalogReview()).isFalse();
        assertThat(review.catalogMutated()).isFalse();
        assertThat(review.candidateTraceIds()).containsExactly("trc_22222222222222222222222222222222");
        assertThat(review.candidateGate().pass()).isFalse();
        assertThat(review.candidateGate().warningReports()).isEqualTo(1);
        assertThat(review.curationPolicy())
            .containsEntry("candidateTraceIdsUsedForReview", true)
            .containsEntry("candidateTraceIdsPromotedToCatalog", false)
            .containsEntry("requestTraceIdOverrideAllowedForPublishedGate", false);
        assertThat(review.toString())
            .doesNotContain("trc_not_w3c", "secret-token-value");

        AgentEvalTraceSetCurationReviewArtifact emptyReview = service.curationReview(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of("trc_not_w3c", "secret-token-value"), null, null, null)
        ).orElseThrow();

        assertThat(emptyReview.reviewVerdict()).isEqualTo("REJECT_EMPTY_CANDIDATES");
        assertThat(emptyReview.emptyCandidates()).isTrue();
        assertThat(emptyReview.candidateTraceIds()).isEmpty();
    }

    @Test
    void catalogPatchProposal_shouldGenerateReviewOnlyJsonPatchWithoutMutatingCatalog() {
        String traceId = "trc_33333333333333333333333333333333";
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        recordReadEvidence(recorder, traceId);
        AgentEvalTraceSetCatalogService service = service(recorder);

        AgentEvalTraceSetCatalogPatchProposalArtifact proposal = service.catalogPatchProposal(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of(traceId, "secret-token-value", traceId), null, null, null)
        ).orElseThrow();

        assertThat(proposal.schemaVersion()).isEqualTo("agent-eval-trace-set-catalog-patch-proposal.v1");
        assertThat(proposal.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(proposal.proposalVerdict()).isEqualTo("READY_FOR_GIT_REVIEW");
        assertThat(proposal.readyForGitReview()).isTrue();
        assertThat(proposal.catalogMutated()).isFalse();
        assertThat(proposal.traceSetIndex()).isZero();
        assertThat(proposal.originalTraceIds()).isEmpty();
        assertThat(proposal.candidateTraceIds()).containsExactly(traceId);
        assertThat(proposal.addedTraceIds()).containsExactly(traceId);
        assertThat(proposal.proposedTraceIds()).containsExactly(traceId);
        assertThat(proposal.jsonPatch()).hasSize(1);
        assertThat(proposal.jsonPatch().get(0))
            .containsEntry("op", "replace")
            .containsEntry("path", "/0/traceIds")
            .containsEntry("value", List.of(traceId));
        assertThat(proposal.curationReview().readyForCatalogReview()).isTrue();
        assertThat(proposal.proposalPolicy())
            .containsEntry("reviewOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("requiresHumanReview", true)
            .containsEntry("requiresGitReview", true)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("requiresCiGateBundleRegeneration", true);
        assertThat(service.findDefinition("phase1-core-golden").orElseThrow().traceIds()).isEmpty();
        assertThat(proposal.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("deterministic", true)
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
    void catalogPatchProposal_shouldStayEmptyWhenCurationReviewFails() {
        AgentEvalTraceSetCatalogService service = service(new InMemoryAgentAuditRecorder());

        AgentEvalTraceSetCatalogPatchProposalArtifact proposal = service.catalogPatchProposal(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of("trc_44444444444444444444444444444444"), null, null, null)
        ).orElseThrow();

        assertThat(proposal.readyForGitReview()).isFalse();
        assertThat(proposal.proposalVerdict()).isEqualTo("REJECT_EVAL_GATE_FAILED");
        assertThat(proposal.jsonPatch()).isEmpty();
        assertThat(proposal.catalogMutated()).isFalse();
        assertThat(proposal.proposalPolicy())
            .containsEntry("readyForGitReview", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("requiresCiGateBundleRegeneration", false);
    }

    private AgentEvalTraceSetCatalogService service(InMemoryAgentAuditRecorder recorder) {
        AgentEvalReportService evalReportService = new AgentEvalReportService(new AgentReplayTimelineService(recorder));
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        return new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
    }

    private void recordReadEvidence(InMemoryAgentAuditRecorder recorder, String traceId) {
        recorder.record(new AgentAuditEvent(
            "aud_curation_review",
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
    }
}
