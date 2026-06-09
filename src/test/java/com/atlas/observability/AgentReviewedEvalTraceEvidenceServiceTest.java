package com.atlas.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviewed eval trace evidence contract tests.
 */
class AgentReviewedEvalTraceEvidenceServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedEvalTraceEvidenceService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedEvalTraceEvidenceResponse.java"
    );

    @Test
    void evidence_shouldPublishNotReadyReadModelUntilAllTraceSetsHaveReviewedAnchors() {
        AgentEvalReportService reportService = new AgentEvalReportService(
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder())
        );
        AgentReviewedEvalTraceEvidenceService service = new AgentReviewedEvalTraceEvidenceService(
            new AgentEvalTraceSetCatalogService(
                new AgentEvalSuiteCatalogService(reportService),
                new ObjectMapper()
            ),
            Clock.fixed(Instant.parse("2026-06-09T08:00:00Z"), ZoneOffset.UTC)
        );

        AgentReviewedEvalTraceEvidenceResponse evidence = service.evidence();

        assertThat(evidence.schemaVersion()).isEqualTo("agent-reviewed-eval-trace-evidence.v1");
        assertThat(evidence.generatedAt()).isEqualTo(Instant.parse("2026-06-09T08:00:00Z"));
        assertThat(evidence.evidenceStatus()).isEqualTo("NEEDS_REVIEWED_REDACTED_TRACE_EVIDENCE");
        assertThat(evidence.phase1TopTierGoalPreserved()).isTrue();
        assertThat(evidence.reviewedEvidenceReady()).isFalse();
        assertThat(evidence.releaseBlockingAllowedNow()).isFalse();
        assertThat(evidence.ciBlockingEnabled()).isFalse();
        assertThat(evidence.runtimeMutationAllowed()).isFalse();
        assertThat(evidence.traceSetCount()).isEqualTo(4);
        assertThat(evidence.reviewedTraceSetCount()).isZero();
        assertThat(evidence.reviewedTraceAnchorCount()).isZero();
        assertThat(evidence.traceSetEvidence()).allSatisfy(traceSet -> assertThat(traceSet)
            .containsEntry("status", "NEEDS_REDACTED_REVIEWED_TRACE_EVIDENCE")
            .containsEntry("reviewedEvidencePresent", false)
            .containsEntry("requiresPersistedRedactedReplayEvidence", true)
            .containsEntry("requiresHumanGitReview", true)
            .containsEntry("requestTraceIdOverrideAllowed", false)
            .containsEntry("placeholderTraceIdsAllowed", false)
            .containsEntry("releaseBlockingAllowedNow", false)
            .containsEntry("phase2Scope", false));
        assertThat(evidence.reviewPipeline()).extracting(stage -> stage.get("id"))
            .containsExactly(
                "redacted-candidate-discovery",
                "curation-review",
                "workbench-catalog-patch-review",
                "human-git-review",
                "gate-bundle-regeneration",
                "release-blocking-promotion"
            );
        assertThat(evidence.qualityGates()).extracting(gate -> gate.get("id"))
            .contains(
                "w3c-trace-anchor",
                "redacted-replay-only",
                "deterministic-eval",
                "human-git-review",
                "no-runtime-authority",
                "phase1-core-only"
            );
        assertThat(evidence.standardsAlignment()).extracting(standard -> standard.get("id"))
            .contains(
                "openai-agents-tracing",
                "mcp-tools-governance",
                "otel-genai-semconv",
                "owasp-llm-top-10",
                "w3c-trace-context"
            );
        assertThat(evidence.nextActions()).contains(
            "discover-redacted-trace-candidates",
            "review-candidates-through-curation-review",
            "keep-ci-blocking-disabled-until-reviewed-evidence-is-present"
        );
        assertThat(evidence.endpointMap())
            .containsEntry("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence")
            .containsEntry("traceSetCurationReview", "/api/agent/observability/eval/trace-sets/{traceSetId}/curation-review")
            .containsEntry("catalogPatchReview", "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review");
        assertThat(evidence.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("releaseBlockingAllowedNow", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(evidence.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(evidence.toString())
            .contains("reviewed-trace-evidence", "redacted-candidate-discovery", "human-git-review")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "/api/login");
    }

    @Test
    void response_shouldStillKeepReleaseBlockingClosedWhenReviewedAnchorsExist() {
        AgentEvalTraceSetCatalogResponse catalog = AgentEvalTraceSetCatalogResponse.of(
            "test",
            List.of(
                traceSet("phase1-core-golden", "release-gate-strict"),
                traceSet("phase1-red-team-safety", "redaction-regression")
            ),
            privacy()
        );

        AgentReviewedEvalTraceEvidenceResponse evidence = AgentReviewedEvalTraceEvidenceResponse.of(
            Instant.parse("2026-06-09T09:00:00Z"),
            catalog
        );

        assertThat(evidence.evidenceStatus()).isEqualTo("REVIEWED_TRACE_EVIDENCE_READY_FOR_RELEASE_GATE_PROMOTION");
        assertThat(evidence.reviewedEvidenceReady()).isTrue();
        assertThat(evidence.reviewedTraceSetCount()).isEqualTo(2);
        assertThat(evidence.reviewedTraceAnchorCount()).isEqualTo(2);
        assertThat(evidence.releaseBlockingAllowedNow()).isFalse();
        assertThat(evidence.ciBlockingEnabled()).isFalse();
        assertThat(evidence.nextActions()).containsExactly(
            "inspect-reviewed-trace-evidence-before-release-gate-promotion",
            "regenerate-gate-bundle-after-human-git-review",
            "prepare-release-blocking-eval-gate-contract"
        );
        assertThat(evidence.traceSetEvidence()).allSatisfy(traceSet -> assertThat(traceSet)
            .containsEntry("status", "HAS_REVIEWED_TRACE_ANCHORS")
            .containsEntry("reviewedEvidencePresent", true)
            .containsEntry("releaseBlockingAllowedNow", false));
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("AgentReviewedEvalTraceEvidenceResponse.of")
            .contains("traceSetCatalogService.catalog()")
            .doesNotContain("ChatClient")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("reviewed-trace-evidence")
            .contains("releaseBlockingAllowedNow")
            .contains("ciBlockingEnabled")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }

    private static AgentEvalTraceSetDefinition traceSet(String id, String suiteId) {
        return AgentEvalTraceSetDefinition.of(
            id,
            id,
            "reviewed test trace set",
            "Phase 1 top-tier kube-manager Agent Core",
            suiteId,
            List.of("11111111111111111111111111111111"),
            List.of("Persisted redacted replay evidence."),
            List.of("phase1", "reviewed"),
            Map.of(
                "requiresRealAuditCapture", true,
                "placeholderTraceIds", false,
                "requestTraceIdOverrideAllowed", false
            ),
            privacy()
        );
    }

    private static Map<String, Object> privacy() {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", true);
        proof.put("containsRawPrincipal", false);
        proof.put("containsRawOrganization", false);
        proof.put("containsRawConversation", false);
        proof.put("containsRawEndpoints", false);
        proof.put("containsRawReason", false);
        proof.put("containsRawParameterValues", false);
        proof.put("deterministic", true);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        proof.put("toolExecution", false);
        proof.put("kubeManagerCalls", false);
        return proof;
    }
}
