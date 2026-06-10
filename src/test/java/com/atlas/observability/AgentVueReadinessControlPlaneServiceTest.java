package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vue readiness control-plane contract tests.
 */
class AgentVueReadinessControlPlaneServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentVueReadinessControlPlaneService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentVueReadinessControlPlaneResponse.java"
    );

    @Test
    void controlPlane_shouldPublishFrontendBindingContractWithoutRuntimeControls() {
        AgentVueReadinessControlPlaneService service = new AgentVueReadinessControlPlaneService(
            Clock.fixed(Instant.parse("2026-06-09T07:00:00Z"), ZoneOffset.UTC)
        );

        AgentVueReadinessControlPlaneResponse controlPlane = service.controlPlane();

        assertThat(controlPlane.schemaVersion()).isEqualTo("agent-vue-readiness-control-plane.v1");
        assertThat(controlPlane.generatedAt()).isEqualTo(Instant.parse("2026-06-09T07:00:00Z"));
        assertThat(controlPlane.controlPlaneStatus()).isEqualTo("BACKEND_CONTRACT_READY_FOR_VUE_BINDING");
        assertThat(controlPlane.phase1TopTierGoalPreserved()).isTrue();
        assertThat(controlPlane.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(controlPlane.vueBindingReady()).isTrue();
        assertThat(controlPlane.runtimeControlAllowed()).isFalse();
        assertThat(controlPlane.dashboardCount()).isEqualTo(17);
        assertThat(controlPlane.dashboards()).extracting(dashboard -> dashboard.get("id"))
            .containsExactly(
                "top-tier-command-center",
                "advanced-technology-adoption",
                "advanced-technology-compatibility-matrix",
                "advanced-technology-compatibility-matrix-binding-spec",
                "advanced-technology-compatibility-matrix-evidence-readiness",
                "backend-technology-modernization-decision",
                "official-version-protocol-watch",
                "official-version-protocol-watch-dashboard",
                "official-version-protocol-watch-binding-spec",
                "top-tier-vue-workbench-implementation-package",
                "phase1-execution-roadmap",
                "kube-manager-governance",
                "memory-rag-readiness",
                "memory-rag-trace-set-curation-workbench",
                "memory-rag-reviewed-trace-evidence-manifest",
                "eval-workbench",
                "mcp-governance"
            );
        assertThat(controlPlane.requiredApiBindings()).extracting(binding -> binding.get("id"))
            .contains(
                "readiness-overview",
                "advanced-technology-adoption",
                "advanced-technology-compatibility-matrix",
                "advanced-technology-compatibility-matrix-binding-spec",
                "advanced-technology-compatibility-matrix-evidence-readiness",
                "backend-technology-modernization-decision",
                "official-version-protocol-watch",
                "official-version-protocol-watch-dashboard",
                "official-version-protocol-watch-binding-spec",
                "top-tier-vue-workbench-implementation-package",
                "phase1-roadmap",
                "reviewed-trace-evidence",
                "release-blocking-gate-contract",
                "memory-rag-trace-set-curation-workbench",
                "memory-rag-reviewed-trace-evidence-manifest",
                "memory-rag-eval-gate",
                "memory-rag-eval-suite-binding",
                "eval-gate-bundle-summary",
                "mcp-manifest"
            );
        assertThat(controlPlane.operatorStates()).extracting(state -> state.get("status"))
            .contains("READY", "PARTIAL", "BLOCKED", "CONTRACT_DEFINED_NOT_BOUND", "PHASE2_PAUSED");
        assertThat(controlPlane.forbiddenUiActions())
            .contains(
                "enable-kube-manager-write-retry",
                "trigger-kube-manager-state-changing-call",
                "run-mcp-tools-call",
                "run-retrieval-against-prompt",
                "run-memory-rag-trace-set-curation-workbench-action",
                "enable-ci-blocking-from-ui",
                "reopen-nim-hpc-slurm-bcm-phase2"
            );
        assertThat(controlPlane.recommendedBuildOrder()).containsExactly(
            "create-vue-top-tier-agent-navigation",
            "bind-readiness-overview-card-grid",
            "bind-advanced-technology-adoption-matrix",
            "bind-advanced-technology-compatibility-matrix",
            "bind-advanced-technology-compatibility-matrix-binding-spec",
            "bind-advanced-technology-compatibility-matrix-evidence-readiness",
            "bind-backend-technology-modernization-decision",
            "bind-official-version-protocol-watch",
            "bind-official-version-protocol-watch-dashboard",
            "bind-official-version-protocol-watch-binding-spec",
            "bind-top-tier-vue-workbench-implementation-package",
            "bind-phase1-execution-roadmap-timeline",
            "bind-kube-manager-governance-cards",
            "bind-memory-rag-readiness-and-contract-links",
            "bind-memory-rag-trace-set-curation-workbench",
            "bind-memory-rag-reviewed-trace-evidence-manifest",
            "bind-eval-workbench-summary-and-gate-bundle",
            "bind-mcp-governance-manifest-view",
            "keep-runtime-control-buttons-absent"
        );
        assertThat(controlPlane.endpointMap())
            .containsEntry("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane")
            .containsEntry("advancedTechnologyCompatibilityMatrix",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix")
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec")
            .containsEntry("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness")
            .containsEntry("backendTechnologyModernizationDecision",
                "/api/agent/observability/top-tier/backend-technology-modernization-decision")
            .containsEntry("officialVersionProtocolWatch", "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("officialVersionProtocolWatchDashboard",
                "/api/agent/observability/top-tier/official-version-protocol-watch/dashboard")
            .containsEntry("officialVersionProtocolWatchVueBindingSpec",
                "/api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec")
            .containsEntry("topTierVueWorkbenchImplementationPackage",
                "/api/agent/observability/top-tier/vue-workbench-implementation-package")
            .containsEntry("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap")
            .containsEntry("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence")
            .containsEntry("releaseBlockingEvalGateContract", "/api/agent/observability/eval/release-blocking-gate-contract")
            .containsEntry("memoryRagTraceSetCurationWorkbenchOverview",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/overview")
            .containsEntry("memoryRagReviewedTraceEvidenceManifest",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest")
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("memoryRagEvalSuiteBindingContract", "/api/agent/observability/memory-rag/eval-suite-binding-contract")
            .containsEntry("mcpManifest", "/api/agent/mcp/manifest");
        assertThat(controlPlane.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("vueContractOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("runtimeMutationAllowed", false)
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
            .containsEntry("dependencyUpgrade", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
        assertThat(controlPlane.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawEndpoint", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(controlPlane.toString())
            .contains("top-tier-command-center", "phase1-execution-roadmap", "keep-runtime-control-buttons-absent")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidHiddenRuntimeControl() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("AgentVueReadinessControlPlaneResponse.of")
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
            .contains("vue-readiness-control-plane")
            .contains("runtimeControlAllowed")
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
