package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Advanced technology adoption contract tests.
 */
class AgentAdvancedTechnologyAdoptionContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentAdvancedTechnologyAdoptionContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentAdvancedTechnologyAdoptionContractResponse.java"
    );

    @Test
    void contract_shouldSeparateStableMainlineFromCompatibilityMatrix() {
        AgentAdvancedTechnologyAdoptionContractService service = new AgentAdvancedTechnologyAdoptionContractService(
            Clock.fixed(Instant.parse("2026-06-09T05:00:00Z"), ZoneOffset.UTC)
        );

        AgentAdvancedTechnologyAdoptionContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-advanced-technology-adoption-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T05:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.javaSpringControlPlanePreserved()).isTrue();
        assertThat(contract.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(contract.runtimeUpgradePerformed()).isFalse();
        assertThat(contract.dependencyUpgradePerformed()).isFalse();
        assertThat(contract.externalAgentRuntimeBound()).isFalse();
        assertThat(contract.mainlineTechnologies()).extracting(technology -> technology.get("id"))
            .containsExactly(
                "java-spring-control-plane",
                "spring-ai-1-1-access-layer",
                "official-version-protocol-watch",
                "safe-tool-executor-boundary",
                "deterministic-eval-workbench",
                "memory-rag-contract-stack",
                "memory-rag-eval-suite-binding",
                "mcp-manifest-governance",
                "trace-audit-replay-observability",
                "reviewed-eval-trace-evidence",
                "release-blocking-eval-gate-contract",
                "kube-manager-http-governance"
            );
        assertThat(contract.compatibilityMatrix()).extracting(technology -> technology.get("id"))
            .containsExactly(
                "java-21-25-26-toolchains",
                "spring-boot-4-framework-7",
                "spring-ai-2-line",
                "responses-agents-runtime",
                "mcp-runtime-server",
                "otel-genai-semconv-adapter",
                "a2a-agent-artifact-provenance",
                "hybrid-rag-graphrag-reranker-vector-stores"
            );
        assertThat(contract.adoptionGates()).extracting(gate -> gate.get("id"))
            .contains(
                "source-owned-contract",
                "build-test-recovery",
                "identity-tenant-privacy",
                "safe-execution-boundary",
                "trace-audit-replay",
                "eval-before-release",
                "phase2-domain-pause"
            );
        assertThat(contract.rejectedShortcuts()).extracting(shortcut -> shortcut.get("id"))
            .contains(
                "blind-major-version-upgrade",
                "prompt-only-security",
                "direct-protocol-authority",
                "vector-first-rag",
                "phase2-specialist-scope-creep"
            );
        assertThat(contract.recommendedBuildOrder()).containsExactly(
            "publish-advanced-technology-adoption-contract",
            "keep-java-spring-control-plane-as-phase1-mainline",
            "publish-official-version-protocol-watch",
            "add-official-version-and-protocol-watch-to-compatibility-matrix",
            "bind-memory-rag-eval-suite-before-retrieval-runtime",
            "wire-vue-top-tier-readiness-and-technology-adoption-workbench",
            "promote-reviewed-eval-and-security-gates-to-release-blocking",
            "prototype-mcp-runtime-and-agent-handoff-only-behind-safe-execution-boundary",
            "keep-nim-hpc-slurm-bcm-paused-until-phase2"
        );
        assertThat(contract.standardsAlignment())
            .containsEntry("openAiResponsesAndAgentsMappedToLocalContracts", true)
            .containsEntry("openAiAgentsSdkToolsHandoffsGuardrailsTracingTracked", true)
            .containsEntry("openAiTracingAndEvalEvidenceMappedToReviewedTraceContracts", true)
            .containsEntry("springAiChatMemoryVectorStoreMcpTracked", true)
            .containsEntry("springAiMainlineAndUpgradeMatrixSeparated", true)
            .containsEntry("mcpLatestSpecConsentAndToolBoundaryTracked", true)
            .containsEntry("mcpDiscoverySeparatedFromRuntimeAuthority", true)
            .containsEntry("otelGenAiMappedThroughStableInternalFields", true)
            .containsEntry("a2aAgentCardTaskArtifactProvenanceTracked", true)
            .containsEntry("owaspLlmSecurityThreatsMappedToGates", true)
            .containsEntry("officialVersionProtocolWatchTracked", true)
            .containsEntry("javaSpringStillPreferredBackendControlPlane", true)
            .containsEntry("runtimeBound", false);
        assertThat(contract.endpointMap())
            .containsEntry("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract")
            .containsEntry("officialVersionProtocolWatch", "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("officialVersionProtocolWatchDashboard",
                "/api/agent/observability/top-tier/official-version-protocol-watch/dashboard")
            .containsEntry("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap")
            .containsEntry("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane")
            .containsEntry("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview")
            .containsEntry("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence")
            .containsEntry("releaseBlockingEvalGateContract", "/api/agent/observability/eval/release-blocking-gate-contract")
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("memoryRagEvalSuiteBindingContract", "/api/agent/observability/memory-rag/eval-suite-binding-contract")
            .containsEntry("memoryRagReviewedTraceEvidenceManifest",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("dependencyUpgrade", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("agentHandoffRuntime", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false)
            .containsEntry("containsRuntimeSecrets", false);
        assertThat(contract.toString())
            .contains("java-spring-control-plane", "responses-agents-runtime", "source-owned-contract")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayContractOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("AgentAdvancedTechnologyAdoptionContractResponse.of")
            .doesNotContain("ChatClient")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("@PostMapping")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("advanced-technology-adoption-contract")
            .contains("COMPATIBILITY_MATRIX")
            .contains("MAINLINE_STABLE")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }
}
