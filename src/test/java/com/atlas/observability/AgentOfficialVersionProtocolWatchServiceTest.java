package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Official version/protocol watch contract tests.
 */
class AgentOfficialVersionProtocolWatchServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentOfficialVersionProtocolWatchService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentOfficialVersionProtocolWatchResponse.java"
    );

    @Test
    void watch_shouldPublishOfficialSourcesAndKeepRuntimeUnbound() {
        AgentOfficialVersionProtocolWatchService service = new AgentOfficialVersionProtocolWatchService(
            Clock.fixed(Instant.parse("2026-06-09T14:30:00Z"), ZoneOffset.UTC)
        );

        AgentOfficialVersionProtocolWatchResponse watch = service.watch();

        assertThat(watch.schemaVersion()).isEqualTo("agent-official-version-protocol-watch.v1");
        assertThat(watch.generatedAt()).isEqualTo(Instant.parse("2026-06-09T14:30:00Z"));
        assertThat(watch.watchStatus()).isEqualTo("OFFICIAL_WATCH_DEFINED_NOT_RUNTIME_BOUND");
        assertThat(watch.sourceReviewDate()).isEqualTo("2026-06-09");
        assertThat(watch.officialSourcesOnly()).isTrue();
        assertThat(watch.phase1TopTierGoalPreserved()).isTrue();
        assertThat(watch.javaSpringControlPlanePreserved()).isTrue();
        assertThat(watch.phase2NimHpcSlurmBcmPaused()).isTrue();
        assertThat(watch.runtimeUpgradePerformed()).isFalse();
        assertThat(watch.dependencyUpgradePerformed()).isFalse();
        assertThat(watch.externalCallsPerformed()).isFalse();
        assertThat(watch.officialSourceCount()).isEqualTo(8);
        assertThat(watch.technologyTrackCount()).isEqualTo(8);
        assertThat(watch.officialSources()).extracting(source -> source.get("id"))
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
        assertThat(watch.officialSources()).allSatisfy(source -> assertThat(source)
            .containsEntry("sourceReviewDate", "2026-06-09")
            .containsEntry("runtimeBound", false));
        assertThat(watch.technologyTracks()).extracting(track -> track.get("id"))
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
        assertThat(watch.adoptionGates()).extracting(gate -> gate.get("id"))
            .contains(
                "official-source-review",
                "compatibility-matrix-before-upgrade",
                "contract-before-runtime",
                "safe-authority-boundary",
                "phase2-domain-pause"
            );
        assertThat(watch.blockedRuntimeShortcuts()).extracting(shortcut -> shortcut.get("id"))
            .contains(
                "blind-latest-version-bump",
                "direct-mcp-tools-call",
                "direct-a2a-handoff-authority",
                "direct-retrieval-prompt-influence",
                "external-agent-runtime-as-control-plane"
            );
        assertThat(watch.recommendedBuildOrder()).containsExactly(
            "publish-official-version-protocol-watch",
            "bind-vue-official-watch-dashboard",
            "refresh-official-sources-through-git-review",
            "turn-watch-items-into-compatibility-matrix-tests-before-upgrades",
            "populate-reviewed-redacted-eval-and-memory-rag-traces",
            "only-then-prototype-mcp-tools-call-a2a-handoff-or-retrieval-runtime",
            "keep-nim-hpc-slurm-bcm-paused-until-phase2"
        );
        assertThat(watch.standardsAlignment())
            .containsEntry("springAiOfficialReferenceTracked", true)
            .containsEntry("openAiResponsesApiTracked", true)
            .containsEntry("openAiAgentsSdkTracked", true)
            .containsEntry("mcp20251125SpecTracked", true)
            .containsEntry("nsaMcpSecurityGuidanceTracked", true)
            .containsEntry("a2aLatestSpecTracked", true)
            .containsEntry("otelGenAiDevelopmentStatusRespected", true)
            .containsEntry("owaspLlmTop10MappedToSecurityGates", true)
            .containsEntry("runtimeBound", false);
        assertThat(watch.endpointMap())
            .containsEntry("officialVersionProtocolWatch",
                "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("officialVersionProtocolWatchDashboard",
                "/api/agent/observability/top-tier/official-version-protocol-watch/dashboard")
            .containsEntry("officialVersionProtocolWatchVueBindingSpec",
                "/api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec")
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec")
            .containsEntry("advancedTechnologyAdoptionContract",
                "/api/agent/observability/top-tier/advanced-technology-adoption-contract")
            .containsEntry("memoryRagReviewedTraceEvidenceManifest",
                "/api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest");
        assertThat(watch.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("watchOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("runtimeUpgradePerformed", false)
            .containsEntry("dependencyUpgradePerformed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("nimHpcSlurmBcmTouched", false);
        assertThat(watch.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false)
            .containsEntry("containsRuntimeSecrets", false);
        assertThat(watch.toString())
            .contains("mcp-2025-11-25", "otel-genai-semconv", "official-source-review")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayWatchOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("AgentOfficialVersionProtocolWatchResponse.of")
            .doesNotContain("ChatClient")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("official-version-protocol-watch")
            .contains("OFFICIAL_WATCH_DEFINED_NOT_RUNTIME_BOUND")
            .contains("https://modelcontextprotocol.io/specification/2025-11-25")
            .contains("https://a2a-protocol.org/latest/specification/")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }
}
