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
 * Release-blocking eval gate contract tests.
 */
class AgentReleaseBlockingEvalGateContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReleaseBlockingEvalGateContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReleaseBlockingEvalGateContractResponse.java"
    );

    @Test
    void contract_shouldStayBlockedUntilReviewedEvidenceAndGateBundleAreReady() {
        AgentEvalReportService reportService = new AgentEvalReportService(
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder())
        );
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(reportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentReleaseBlockingEvalGateContractService service = new AgentReleaseBlockingEvalGateContractService(
            new AgentReviewedEvalTraceEvidenceService(traceSetCatalogService),
            new AgentEvalWorkbenchGateBundleSummaryService(traceSetCatalogService),
            Clock.fixed(Instant.parse("2026-06-09T10:00:00Z"), ZoneOffset.UTC)
        );

        AgentReleaseBlockingEvalGateContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-release-blocking-eval-gate-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T10:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("BLOCKED_BY_REVIEWED_TRACE_EVIDENCE");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.releaseBlockingGateDefined()).isTrue();
        assertThat(contract.releaseBlockingEnabled()).isFalse();
        assertThat(contract.ciBlockingEnabled()).isFalse();
        assertThat(contract.releaseGateCanOpenNow()).isFalse();
        assertThat(contract.runtimeMutationAllowed()).isFalse();
        assertThat(contract.reviewedEvidenceReady()).isFalse();
        assertThat(contract.gateBundleReleaseEligible()).isFalse();
        assertThat(contract.traceSetCount()).isEqualTo(7);
        assertThat(contract.reviewedTraceSetCount()).isZero();
        assertThat(contract.reviewedTraceAnchorCount()).isZero();
        assertThat(contract.emptyTraceSets()).isEqualTo(7);
        assertThat(contract.releaseGateChecks()).extracting(check -> check.get("id"))
            .containsExactly(
                "reviewed-trace-evidence",
                "gate-bundle-release-eligible",
                "no-empty-trace-sets",
                "human-git-review-complete",
                "ci-blocking-switch-absent",
                "runtime-authority-unchanged"
            );
        assertThat(contract.traceSetReleaseRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("reviewedEvidencePresent", false)
            .containsEntry("gatePass", false)
            .containsEntry("emptyInput", true)
            .containsEntry("releaseBlockingReady", false)
            .containsEntry("releaseBlockingAllowedNow", false));
        assertThat(contract.traceSetReleaseRows())
            .filteredOn(row -> row.get("traceSetId").toString().startsWith("memory-rag-"))
            .hasSize(3);
        assertThat(contract.blockedReasons()).contains(
            "reviewed-redacted-trace-evidence-missing",
            "gate-bundle-not-release-eligible",
            "empty-trace-sets-fail-closed",
            "human-release-review-not-bound",
            "ci-blocking-switch-intentionally-absent",
            "release-blocking-runtime-wire-not-implemented"
        );
        assertThat(contract.promotionPlan()).contains(
            "populate-reviewed-redacted-trace-anchors-through-git-review",
            "regenerate-gate-bundle-after-reviewed-catalog-merge",
            "wire-ci-to-consume-compact-gate-artifact-in-a-separate-release"
        );
        assertThat(contract.endpointMap())
            .containsEntry("releaseBlockingEvalGateContract", "/api/agent/observability/eval/release-blocking-gate-contract")
            .containsEntry("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence")
            .containsEntry("gateBundleSummary", "/api/agent/observability/eval/workbench/gate-bundle-summary");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("releaseGateCanOpenNow", false)
            .containsEntry("releaseBlockingEnabled", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("catalogMutationAllowed", false)
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
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawParameterValues", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(contract.toString())
            .contains("release-blocking-gate-contract", "BLOCKED_BY_REVIEWED_TRACE_EVIDENCE")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "/api/login");
    }

    @Test
    void response_shouldStillKeepCiBlockingDisabledWhenManualPromotionCouldBePrepared() {
        AgentReviewedEvalTraceEvidenceResponse reviewedEvidence = AgentReviewedEvalTraceEvidenceResponse.of(
            Instant.parse("2026-06-09T10:10:00Z"),
            AgentEvalTraceSetCatalogResponse.of(
                "test",
                List.of(traceSet("phase1-core-golden")),
                privacy()
            )
        );
        AgentEvalWorkbenchGateBundleSummaryResponse gateBundle = AgentEvalWorkbenchGateBundleSummaryResponse.from(
            AgentEvalTraceSetCatalogResponse.of("test", List.of(traceSet("phase1-core-golden")), privacy()),
            AgentEvalTraceSetGateBundleArtifact.of(
                "test",
                List.of(AgentEvalTraceSetGateArtifact.from(
                    traceSet("phase1-core-golden"),
                    passingSuiteGate(),
                    null,
                    "test"
                ))
            )
        );

        AgentReleaseBlockingEvalGateContractResponse contract =
            AgentReleaseBlockingEvalGateContractResponse.of(
                Instant.parse("2026-06-09T10:20:00Z"),
                reviewedEvidence,
                gateBundle
            );

        assertThat(contract.contractStatus()).isEqualTo("READY_FOR_MANUAL_RELEASE_GATE_PROMOTION");
        assertThat(contract.reviewedEvidenceReady()).isTrue();
        assertThat(contract.gateBundleReleaseEligible()).isTrue();
        assertThat(contract.releaseGateCanOpenNow()).isFalse();
        assertThat(contract.releaseBlockingEnabled()).isFalse();
        assertThat(contract.ciBlockingEnabled()).isFalse();
        assertThat(contract.safety())
            .containsEntry("releaseGateCanOpenNow", false)
            .containsEntry("releaseBlockingEnabled", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("runtimeMutationAllowed", false);
        assertThat(contract.blockedReasons()).contains(
            "human-release-review-not-bound",
            "ci-blocking-switch-intentionally-absent",
            "release-blocking-runtime-wire-not-implemented"
        );
        assertThat(contract.nextActions()).containsExactly(
            "prepare-human-release-review",
            "draft-ci-blocking-wiring-change",
            "keep-runtime-switch-disabled-until-ci-contract-lands"
        );
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("AgentReleaseBlockingEvalGateContractResponse.of")
            .contains("reviewedEvalTraceEvidenceService.evidence()")
            .contains("gateBundleSummaryService.summary()")
            .doesNotContain("ChatClient")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("Mcp")
            .doesNotContain("tools/call")
            .doesNotContain("ToolCallback")
            .doesNotContain("ToolRegistry")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("executeWrite")
            .doesNotContain("@PostMapping")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("release-blocking-gate-contract")
            .contains("releaseBlockingEnabled")
            .contains("ciBlockingEnabled")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("Mcp")
            .doesNotContain("tools/call")
            .doesNotContain("ToolCallback")
            .doesNotContain("executeWrite")
            .doesNotContain("@PostMapping")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }

    private static AgentEvalTraceSetDefinition traceSet(String id) {
        return AgentEvalTraceSetDefinition.of(
            id,
            id,
            "reviewed release gate trace set",
            "Phase 1 top-tier kube-manager Agent Core",
            "release-gate-strict",
            List.of("11111111111111111111111111111111"),
            List.of("Persisted redacted replay evidence."),
            List.of("phase1", "release"),
            Map.of("requestTraceIdOverrideAllowed", false),
            privacy()
        );
    }

    private static AgentEvalSuiteGateArtifact passingSuiteGate() {
        return new AgentEvalSuiteGateArtifact(
            "agent-eval-suite-gate.v1",
            Instant.parse("2026-06-09T10:15:00Z"),
            "deterministic-replay-eval.v1",
            "release-gate-strict",
            "Release Gate Strict",
            "PASS",
            true,
            90,
            100,
            100.0,
            false,
            1,
            1,
            1,
            50,
            false,
            false,
            0,
            0,
            0,
            0,
            List.of("11111111111111111111111111111111"),
            List.of(),
            List.of(),
            List.of(),
            Map.of("ciBlockingEnabled", false),
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
