package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Official version/protocol watch dashboard contract tests.
 */
class AgentOfficialVersionProtocolWatchDashboardServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentOfficialVersionProtocolWatchDashboardService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentOfficialVersionProtocolWatchDashboardResponse.java"
    );

    @Test
    void dashboard_shouldBuildVueReadModelWithoutRuntimeAuthority() {
        AgentOfficialVersionProtocolWatchDashboardService service =
            new AgentOfficialVersionProtocolWatchDashboardService(
                new AgentOfficialVersionProtocolWatchService(
                    Clock.fixed(Instant.parse("2026-06-09T15:00:00Z"), ZoneOffset.UTC)
                ),
                Clock.fixed(Instant.parse("2026-06-09T15:30:00Z"), ZoneOffset.UTC)
            );

        AgentOfficialVersionProtocolWatchDashboardResponse dashboard = service.dashboard();

        assertThat(dashboard.schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch-dashboard.v1");
        assertThat(dashboard.generatedAt()).isEqualTo(Instant.parse("2026-06-09T15:30:00Z"));
        assertThat(dashboard.dashboardStatus()).isEqualTo("DASHBOARD_READY_TO_RENDER_OFFICIAL_WATCH");
        assertThat(dashboard.frontendTarget())
            .isEqualTo("vue-kube-manager official Agent technology/protocol watch dashboard");
        assertThat(dashboard.phase1TopTierGoalPreserved()).isTrue();
        assertThat(dashboard.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(dashboard.sourceWatchEmbedded()).isTrue();
        assertThat(dashboard.runtimeControlAllowed()).isFalse();
        assertThat(dashboard.sourceCardCount()).isEqualTo(8);
        assertThat(dashboard.technologyTrackCardCount()).isEqualTo(8);
        assertThat(dashboard.adoptionGateCount()).isEqualTo(7);
        assertThat(dashboard.blockedRuntimeShortcutCount()).isEqualTo(6);
        assertThat(dashboard.sourceCards()).extracting(card -> card.get("id"))
            .containsExactly(
                "spring-ai-reference",
                "openai-responses-api",
                "openai-agents-sdk",
                "mcp-2025-11-25",
                "nsa-mcp-security-2026-06",
                "a2a-latest-spec",
                "otel-genai-semconv",
                "owasp-llm-top-10-2025"
            );
        assertThat(dashboard.sourceCards()).allSatisfy(card -> {
            assertThat(card)
                .containsEntry("status", "OFFICIAL_SOURCE_REVIEWED")
                .containsEntry("severity", "INFO")
                .containsEntry("sourceReviewDate", "2026-06-09")
                .containsEntry("runtimeBound", false)
                .containsEntry("readOnly", true)
                .containsEntry("externalNavigationOnly", true)
                .containsEntry("runtimeControlAllowed", false)
                .containsEntry("buttonVisibleNow", false);
            @SuppressWarnings("unchecked")
            Map<String, Object> renderHints = (Map<String, Object>) card.get("renderHints");
            assertThat(renderHints)
                .containsEntry("showRuntimeButton", false)
                .containsEntry("showExternalLink", true)
                .containsEntry("allowInlineEdit", false);
        });
        assertThat(dashboard.technologyTrackCards()).extracting(card -> card.get("id"))
            .containsExactly(
                "java-spring-governed-control-plane",
                "spring-ai-memory-rag-mcp",
                "openai-responses-agents-interop",
                "mcp-runtime-call-plane",
                "a2a-handoff-provenance",
                "otel-genai-observability-adapter",
                "owasp-llm-risk-controls",
                "advanced-rag-graphrag-rerankers-vector-stores"
            );
        assertThat(dashboard.technologyTrackCards()).allSatisfy(card -> assertThat(card)
            .containsEntry("phase1Scope", true)
            .containsEntry("runtimeBound", false)
            .containsEntry("requiresGitReview", true)
            .containsEntry("readOnly", true)
            .containsEntry("frontendNavigationOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("llmUsed", false));
        assertThat(dashboard.technologyTrackCards().stream()
            .filter(card -> "mcp-runtime-call-plane".equals(card.get("id")))
            .findFirst()
            .orElseThrow()
            .get("disabledRuntimeActions")
            .toString()).contains("run-mcp-tools-call", "enabledNow=false");
        assertThat(dashboard.adoptionGateRows()).extracting(row -> row.get("id"))
            .contains(
                "official-source-review",
                "compatibility-matrix-before-upgrade",
                "contract-before-runtime",
                "safe-authority-boundary",
                "vue-read-model-before-controls",
                "phase2-domain-pause"
            );
        assertThat(dashboard.adoptionGateRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("status", "REQUIRED_GATE")
            .containsEntry("required", true)
            .containsEntry("runtimeBound", false)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(dashboard.blockedRuntimeShortcutRows()).extracting(row -> row.get("id"))
            .contains(
                "blind-latest-version-bump",
                "direct-mcp-tools-call",
                "direct-a2a-handoff-authority",
                "direct-retrieval-prompt-influence",
                "external-agent-runtime-as-control-plane"
            );
        assertThat(dashboard.blockedRuntimeShortcutRows()).allSatisfy(row -> assertThat(row)
            .containsEntry("status", "BLOCKED_SHORTCUT")
            .containsEntry("allowed", false)
            .containsEntry("blocksTopTierClaim", true)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(dashboard.disabledRuntimeActions()).extracting(action -> action.get("id"))
            .containsExactly(
                "upgrade-dependencies-from-dashboard",
                "enable-mcp-tools-call",
                "enable-a2a-runtime-handoff",
                "enable-retrieval-runtime",
                "enable-ci-blocking",
                "reopen-phase2-domain-plugins"
            );
        assertThat(dashboard.disabledRuntimeActions()).allSatisfy(action -> assertThat(action)
            .containsEntry("enabledNow", false)
            .containsEntry("buttonVisibleNow", false)
            .containsEntry("requiresSeparateReviewedSlice", true));
        assertThat(dashboard.renderSections()).extracting(section -> section.get("id"))
            .containsExactly("official-sources", "technology-tracks", "adoption-gates", "blocked-shortcuts");
        assertThat(dashboard.recommendedWorkflow()).containsExactly(
            "official-version-protocol-watch-dashboard",
            "advanced-technology-adoption-contract",
            "phase1-execution-roadmap",
            "vue-readiness-control-plane",
            "review-official-source-update-through-git",
            "add-compatibility-matrix-tests-before-upgrades",
            "bind-runtime-only-after-reviewed-evidence-and-release-gates"
        );
        assertThat(dashboard.nextActions()).contains(
            "wire-vue-dashboard-to-render-official-source-cards",
            "wire-vue-dashboard-to-render-technology-track-cards",
            "hide-runtime-enable-buttons-for-all-watch-items",
            "continue-reviewed-eval-and-memory-rag-trace-evidence-curation",
            "keep-nim-hpc-slurm-bcm-paused-for-phase2"
        );
        assertThat(dashboard.endpointMap())
            .containsEntry("officialVersionProtocolWatchDashboard",
                "/api/agent/observability/top-tier/official-version-protocol-watch/dashboard")
            .containsEntry("officialVersionProtocolWatchVueBindingSpec",
                "/api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec")
            .containsEntry("officialVersionProtocolWatch",
                "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec")
            .containsEntry("advancedTechnologyAdoptionContract",
                "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        assertThat(dashboard.dashboardPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("dashboardOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("sourceWatchEmbedded", true)
            .containsEntry("sourceCardCount", 8)
            .containsEntry("technologyTrackCardCount", 8)
            .containsEntry("adoptionGateCount", 7)
            .containsEntry("blockedRuntimeShortcutCount", 6)
            .containsEntry("disabledRuntimeActionCount", 6)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("dependencyUpgradeAllowed", false)
            .containsEntry("runtimeUpgradeAllowed", false)
            .containsEntry("mcpToolsCallAllowed", false)
            .containsEntry("a2aRuntimeHandoffAllowed", false)
            .containsEntry("retrievalRuntimeAllowed", false)
            .containsEntry("ciBlockingAllowed", false)
            .containsEntry("requiresGitReview", true)
            .containsEntry("requiresCompatibilityMatrixBeforeUpgrade", true)
            .containsEntry("phase2NimHpcSlurmBcmPaused", true);
        assertThat(dashboard.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("dashboardOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("runtimeUpgradePerformed", false)
            .containsEntry("dependencyUpgradePerformed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(dashboard.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false)
            .containsEntry("containsRuntimeSecrets", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(dashboard.sourceWatch().schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch.v1");
        assertThat(dashboard.toString())
            .contains("official-version-protocol-watch-dashboard", "mcp-2025-11-25", "blocked-shortcuts")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayDashboardOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("sourceWatchService.watch()")
            .doesNotContain("ChatClient")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("new SafeToolExecutor")
            .doesNotContain("SafeToolExecutor.")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("official-version-protocol-watch-dashboard")
            .contains("disabledRuntimeActions")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("import com.atlas.tool.core.SafeToolExecutor")
            .doesNotContain("new SafeToolExecutor")
            .doesNotContain("SafeToolExecutor.")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }
}
