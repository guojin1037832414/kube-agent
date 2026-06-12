package com.atlas.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviewed trace fixture intake 合同测试。
 *
 * <p>中文说明：这些测试保护的是“fixture 接入前置规范”，不是运行时上传功能。
 * 它们帮助学习者理解：脱敏 trace fixture 要先具备字段、隐私证明、确定性 eval 证明和人审证据，
 * 才能进入 Git review；即使合同已定义，也不能让调用方提交 traceId 直接写 catalog。</p>
 *
 * <p>安全边界：测试不启动 Spring，不调用 Tool/MCP/LLM/RAG/kube-manager，不写 audit/memory，
 * 不运行 eval/replay，也不修改 `eval-trace-sets.json`。</p>
 */
class AgentReviewedTraceFixtureIntakeContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedTraceFixtureIntakeContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedTraceFixtureIntakeContractResponse.java"
    );

    @Test
    void contract_shouldPublishFixtureIntakeSpecWithoutOpeningRuntimeIntake() {
        AgentEvalReportService reportService = new AgentEvalReportService(
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder())
        );
        AgentReviewedTraceFixtureIntakeContractService service =
            new AgentReviewedTraceFixtureIntakeContractService(
                new AgentEvalTraceSetCatalogService(
                    new AgentEvalSuiteCatalogService(reportService),
                    new ObjectMapper()
                ),
                Clock.fixed(Instant.parse("2026-06-13T08:00:00Z"), ZoneOffset.UTC)
            );

        AgentReviewedTraceFixtureIntakeContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-reviewed-trace-fixture-intake-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-13T08:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("FIXTURE_INTAKE_CONTRACT_DEFINED_NOT_RUNTIME_BOUND");
        assertThat(contract.phase1TopTierGoalPreserved()).isTrue();
        assertThat(contract.runtimeIntakeAllowedNow()).isFalse();
        assertThat(contract.fixtureUploadAccepted()).isFalse();
        assertThat(contract.callerTraceIdsAccepted()).isFalse();
        assertThat(contract.runtimeCatalogWrite()).isFalse();
        assertThat(contract.catalogMutationAllowed()).isFalse();
        assertThat(contract.releaseBlockingAllowedNow()).isFalse();
        assertThat(contract.ciBlockingEnabled()).isFalse();
        assertThat(contract.runtimeEvalAllowed()).isFalse();
        assertThat(contract.traceSetCount()).isEqualTo(7);
        assertThat(contract.reviewedTraceSetCount()).isZero();
        assertThat(contract.missingFixtureTraceSetCount()).isEqualTo(7);
        assertThat(contract.requiredFixtureFields()).extracting(field -> field.get("name"))
            .contains(
                "traceId",
                "traceSetId",
                "suiteId",
                "replaySource",
                "redactionProof",
                "deterministicEvalProof",
                "privacyProof",
                "sourceCommitSha",
                "reviewer",
                "reviewTimestamp",
                "evidenceDigest"
            );
        assertThat(contract.requiredFixtureFields()).allSatisfy(field -> assertThat(field)
            .containsEntry("required", true)
            .containsEntry("callerSuppliedRuntimeAuthority", false));
        assertThat(contract.reviewWorkflow()).extracting(stage -> stage.get("id"))
            .containsExactly(
                "candidate-discovery",
                "curation-review",
                "fixture-intake-contract",
                "catalog-patch-proposal",
                "human-git-review",
                "gate-bundle-regeneration"
            );
        assertThat(contract.qualityGates()).extracting(gate -> gate.get("id"))
            .contains(
                "w3c-trace-anchor",
                "redacted-replay-only",
                "deterministic-eval-proof",
                "privacy-proof",
                "human-git-review",
                "no-runtime-authority"
            );
        assertThat(contract.traceSetReadiness()).allSatisfy(row -> assertThat(row)
            .containsEntry("status", "NEEDS_REVIEWED_FIXTURE_INTAKE")
            .containsEntry("reviewedTraceIdsPresent", false)
            .containsEntry("fixtureIntakeRequired", true)
            .containsEntry("callerTraceIdOverrideAllowed", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeEvalAllowed", false));
        assertThat(contract.forbiddenShortcuts())
            .contains(
                "caller-submitted-trace-ids",
                "raw-audit-export",
                "runtime-catalog-mutation",
                "ci-blocking-switch",
                "llm-as-judge-runtime-shortcut",
                "retrieval-prompt-influence",
                "mcp-tools-call",
                "kube-manager-read-or-write",
                "nim-hpc-slurm-bcm-phase2-authority"
            );
        assertThat(contract.endpointMap())
            .containsEntry("fixtureIntakeContract",
                "/api/agent/observability/eval/reviewed-trace-fixture-intake-contract")
            .containsEntry("catalogPatchProposal",
                "/api/agent/observability/eval/trace-sets/{traceSetId}/catalog-patch-proposal")
            .containsEntry("catalogPatchReview",
                "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("contractOnly", true)
            .containsEntry("intakeSpecOnly", true)
            .containsEntry("runtimeIntakeAllowedNow", false)
            .containsEntry("fixtureUploadAccepted", false)
            .containsEntry("callerTraceIdsAccepted", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("evalTraceSetsJsonWrite", false)
            .containsEntry("releaseBlockingAllowedNow", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("runtimeEvalAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("rawAuditExportAllowed", false)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawParameterValues", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(contract.toString())
            .contains("reviewed-trace-fixture-intake-contract", "sourceCommitSha", "human-git-review")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret", "/api/login");
    }

    @Test
    void source_shouldKeepContractOnlyRuntimeClosedMarkers() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("intake-spec-only / read-only / contract-only")
            .contains("不接受调用方 traceId")
            .contains("不修改")
            .contains("不运行 eval/replay")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("ChatClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("fixtureUploadAccepted")
            .contains("callerTraceIdsAccepted")
            .contains("runtimeCatalogWrite")
            .contains("forbiddenRuntimeClaims")
            .contains("nim-hpc-slurm-bcm-phase2-authority")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }
}
