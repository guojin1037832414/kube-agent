package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 1 execution roadmap contract tests.
 */
class AgentPhase1ExecutionRoadmapServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentPhase1ExecutionRoadmapService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentPhase1ExecutionRoadmapResponse.java"
    );

    @Test
    void roadmap_shouldPublishOrderedPhase1PlanWithoutRuntimeMutation() {
        AgentPhase1ExecutionRoadmapService service = new AgentPhase1ExecutionRoadmapService(
            Clock.fixed(Instant.parse("2026-06-09T06:00:00Z"), ZoneOffset.UTC)
        );

        AgentPhase1ExecutionRoadmapResponse roadmap = service.roadmap();

        assertThat(roadmap.schemaVersion()).isEqualTo("agent-phase1-execution-roadmap.v1");
        assertThat(roadmap.generatedAt()).isEqualTo(Instant.parse("2026-06-09T06:00:00Z"));
        assertThat(roadmap.roadmapStatus()).isEqualTo("PHASE_1_TOP_TIER_ROADMAP_ACTIVE");
        assertThat(roadmap.phase1TopTierGoalPreserved()).isTrue();
        assertThat(roadmap.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(roadmap.roadmapOnly()).isTrue();
        assertThat(roadmap.runtimeMutationAllowed()).isFalse();
        assertThat(roadmap.stepCount()).isEqualTo(8);
        assertThat(roadmap.executionSteps()).extracting(step -> step.get("id"))
            .containsExactly(
                "vue-readiness-control-plane",
                "reviewed-eval-trace-evidence",
                "release-blocking-eval-gates",
                "memory-rag-eval-suite-binding",
                "durable-memory-store-binding",
                "retrieval-runtime-binding",
                "mcp-runtime-safe-call-plane",
                "agent-handoff-and-a2a-provenance"
            );
        assertThat(roadmap.dependencyGates()).extracting(gate -> gate.get("id"))
            .contains(
                "admin-auth-required",
                "safe-tool-executor-only",
                "trace-audit-replay-required",
                "eval-before-runtime",
                "vue-read-model-before-control",
                "kube-manager-write-authority-closed",
                "phase2-domain-pause"
            );
        assertThat(roadmap.vueWorkbenchTargets()).extracting(target -> target.get("id"))
            .contains(
                "top-tier-overview",
                "technology-adoption",
                "phase1-roadmap",
                "vue-readiness-control-plane",
                "kube-manager-governance",
                "memory-rag-readiness",
                "eval-workbench",
                "mcp-governance"
            );
        assertThat(roadmap.doNotStartYet())
            .contains(
                "nim-runtime-reopen",
                "hpc-slurm-bcm-domain-plugins",
                "kube-manager-state-changing-write-runtime",
                "mcp-tools-call-without-safe-tool-executor",
                "blind-spring-boot-4-or-spring-ai-2-mainline-upgrade"
            );
        assertThat(roadmap.endpointMap())
            .containsEntry("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap")
            .containsEntry("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane")
            .containsEntry("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview")
            .containsEntry("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract")
            .containsEntry("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence")
            .containsEntry("releaseBlockingEvalGateContract", "/api/agent/observability/eval/release-blocking-gate-contract")
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("memoryRagEvalSuiteBindingContract", "/api/agent/observability/memory-rag/eval-suite-binding-contract")
            .containsEntry("memoryRagTraceSetCurationContract",
                "/api/agent/observability/memory-rag/trace-set-curation-contract");
        assertThat(roadmap.executionSteps().get(3).get("requiredEvidence").toString())
            .contains("memory-rag-trace-set-curation-contract");
        assertThat(roadmap.executionSteps().get(1))
            .containsEntry("status", "BACKEND_CONTRACT_READY");
        assertThat(roadmap.executionSteps().get(2))
            .containsEntry("status", "BACKEND_CONTRACT_READY_BUT_BLOCKED");
        assertThat(roadmap.executionSteps().get(3))
            .containsEntry("status", "BACKEND_CONTRACT_READY_BUT_NOT_BOUND");
        assertThat(roadmap.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("roadmapOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
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
            .containsEntry("dependencyUpgrade", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
        assertThat(roadmap.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawEndpoint", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(roadmap.toString())
            .contains("vue-readiness-control-plane", "memory-rag-eval-suite-binding", "phase1-roadmap")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayRoadmapOnlyAndAvoidHiddenExecution() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("AgentPhase1ExecutionRoadmapResponse.of")
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
            .contains("phase1-execution-roadmap")
            .contains("runtimeMutationAllowed")
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
