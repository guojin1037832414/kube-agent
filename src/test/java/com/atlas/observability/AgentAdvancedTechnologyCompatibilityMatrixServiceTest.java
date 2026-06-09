package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Advanced technology compatibility matrix contract tests.
 */
class AgentAdvancedTechnologyCompatibilityMatrixServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentAdvancedTechnologyCompatibilityMatrixService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentAdvancedTechnologyCompatibilityMatrixResponse.java"
    );

    @Test
    void matrix_shouldPublishUpgradeEvidenceGatesWithoutChangingRuntimeOrDependencies() {
        AgentOfficialVersionProtocolWatchService watchService = new AgentOfficialVersionProtocolWatchService(
            Clock.fixed(Instant.parse("2026-06-10T01:00:00Z"), ZoneOffset.UTC)
        );
        AgentAdvancedTechnologyCompatibilityMatrixService service =
            new AgentAdvancedTechnologyCompatibilityMatrixService(
                watchService,
                Clock.fixed(Instant.parse("2026-06-10T02:00:00Z"), ZoneOffset.UTC)
            );

        AgentAdvancedTechnologyCompatibilityMatrixResponse matrix = service.matrix();

        assertThat(matrix.schemaVersion()).isEqualTo("agent-advanced-technology-compatibility-matrix.v1");
        assertThat(matrix.generatedAt()).isEqualTo(Instant.parse("2026-06-10T02:00:00Z"));
        assertThat(matrix.matrixStatus()).isEqualTo("MATRIX_DEFINED_NOT_EXECUTED");
        assertThat(matrix.sourceReviewDate()).isEqualTo("2026-06-10");
        assertThat(matrix.sourceWatchEmbedded()).isTrue();
        assertThat(matrix.phase1TopTierGoalPreserved()).isTrue();
        assertThat(matrix.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(matrix.runtimeUpgradeAllowedNow()).isFalse();
        assertThat(matrix.dependencyUpgradeAllowedNow()).isFalse();
        assertThat(matrix.runtimeControlAllowed()).isFalse();
        assertThat(matrix.sourceBaselineCount()).isEqualTo(8);
        assertThat(matrix.matrixItemCount()).isEqualTo(10);
        assertThat(matrix.migrationGateCount()).isEqualTo(8);
        assertThat(matrix.blockedShortcutCount()).isEqualTo(7);
        assertThat(matrix.testLaneCount()).isEqualTo(8);
        assertThat(matrix.sourceBaselines()).extracting(source -> source.get("sourceId"))
            .contains("spring-ai-reference", "openai-agents-sdk", "mcp-2025-11-25",
                "nsa-mcp-security-2026-06", "a2a-latest-spec", "otel-genai-semconv",
                "owasp-llm-top-10-2025");
        assertThat(matrix.matrixItems()).extracting(item -> item.get("id"))
            .containsExactly(
                "java-runtime-toolchains",
                "spring-boot-framework",
                "spring-ai-access-layer",
                "openai-responses-agents",
                "mcp-runtime-call-plane",
                "a2a-multi-agent-provenance",
                "otel-genai-mcp-semconv",
                "memory-rag-graphrag-reranker-vectorstore",
                "kubernetes-manager-control-plane",
                "supply-chain-ci-quality"
            );
        assertThat(matrix.matrixItems()).allSatisfy(item -> assertThat(item)
            .containsEntry("mainlineAllowedNow", false)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(matrix.migrationGates()).extracting(gate -> gate.get("id"))
            .contains("official-source-rechecked", "compatibility-branch-created",
                "build-and-focused-tests-green", "security-boundary-regression-green",
                "vue-readonly-evidence-updated", "git-reviewed-release-decision");
        assertThat(matrix.blockedUpgradeShortcuts()).extracting(shortcut -> shortcut.get("id"))
            .contains("upgrade-pom-from-readiness-page", "treat-rc-preview-as-mainline",
                "trust-mcp-tool-annotations", "enable-retrieval-before-reviewed-traces",
                "enable-ci-blocking-with-empty-fixtures");
        assertThat(matrix.testLanes()).extracting(lane -> lane.get("id"))
            .contains("current-mainline", "java-21-candidate", "boot-4-candidate",
                "spring-ai-2-candidate", "mcp-runtime-prototype", "memory-rag-runtime-prototype");
        assertThat(matrix.implementationChecklist()).contains(
            "keep-current-mainline-green-before-any-upgrade",
            "create-compatibility-branch-per-major-technology",
            "require-reviewed-trace-eval-audit-evidence-before-runtime",
            "commit-and-push-each-reviewed-slice"
        );
        assertThat(matrix.endpointMap())
            .containsEntry("advancedTechnologyCompatibilityMatrix",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix")
            .containsEntry("officialVersionProtocolWatch",
                "/api/agent/observability/top-tier/official-version-protocol-watch");
        assertThat(matrix.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("matrixOnly", true)
            .containsEntry("sourceWatchReadOnly", true)
            .containsEntry("runtimeUpgradeAllowedNow", false)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(matrix.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(matrix.sourceWatch().schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch.v1");
        assertThat(matrix.toString())
            .contains("spring-ai-access-layer", "mcp-runtime-call-plane", "upgrade-pom-from-readiness-page")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayMatrixOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("officialVersionProtocolWatchService.watch()")
            .doesNotContain("ChatClient")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("SafeToolExecutor.")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("advanced-technology-compatibility-matrix")
            .contains("matrixItems")
            .contains("migrationGates")
            .contains("blockedUpgradeShortcuts")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("SafeToolExecutor.")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }
}
