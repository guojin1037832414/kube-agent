package com.atlas.observability;

import com.atlas.auth.UserPermissionContext;
import com.atlas.mcp.McpGovernanceOverviewResponse;
import com.atlas.mcp.McpGovernanceOverviewService;
import com.atlas.mcp.McpToolManifestService;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.impl.DeployDeleteTool;
import com.atlas.tool.impl.MigConfigListTool;
import com.atlas.tool.impl.NodeQueryTool;
import com.atlas.tool.impl.UserQueryTool;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Top-tier Agent readiness overview contract tests.
 */
class AgentTopTierReadinessOverviewServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierReadinessOverviewService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierReadinessOverviewResponse.java"
    );

    @Test
    void overview_shouldSummarizePhase1TopTierAgentWithoutRuntimeAuthority() {
        AgentTopTierReadinessOverviewService service = newService();

        AgentTopTierReadinessOverviewResponse overview = service.overview();

        assertThat(overview.schemaVersion()).isEqualTo("agent-top-tier-readiness-overview.v1");
        assertThat(overview.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(overview.phase()).isEqualTo("PHASE_1_GENERIC_MANAGER_AGENT_CORE");
        assertThat(overview.target()).isEqualTo("top-tier kube-manager Agent core and learning platform");
        assertThat(overview.readinessVerdict()).isEqualTo("PHASE_1_TOP_TIER_CORE_IN_PROGRESS");
        assertThat(overview.phase1TopTierGoalPreserved()).isTrue();
        assertThat(overview.writeAuthorityClosed()).isTrue();
        assertThat(overview.toolExecutionTriggered()).isFalse();
        assertThat(overview.kubeManagerCalls()).isFalse();
        assertThat(overview.llmUsed()).isFalse();
        assertThat(overview.capabilityCardCount()).isEqualTo(10);
        assertThat(overview.readyCardCount()).isEqualTo(4);
        assertThat(overview.partialCardCount()).isEqualTo(4);
        assertThat(overview.blockedCardCount()).isEqualTo(1);
        assertThat(overview.phase2PausedCardCount()).isEqualTo(1);
        assertThat(overview.capabilityCards()).extracting(card -> card.get("id"))
            .containsExactly(
                "identity-security",
                "safe-tool-execution",
                "trace-audit-replay",
                "advanced-technology-adoption",
                "eval-release-gates",
                "kube-manager-http-governance",
                "mcp-interoperability",
                "memory-rag-learning",
                "vue-operator-workbench",
                "phase2-domain-plugins"
            );
        assertThat(overview.capabilityCards()).allSatisfy(card -> assertThat(card)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("llmUsed", false));
        assertThat(overview.topGaps()).containsExactly(
            "eval-release-gates",
            "kube-manager-http-governance",
            "mcp-interoperability",
            "memory-rag-learning",
            "vue-operator-workbench"
        );
        assertThat(overview.recommendedBuildOrder()).containsExactly(
            "wire-vue-top-tier-readiness-overview",
            "wire-vue-advanced-technology-adoption-contract",
            "wire-vue-official-version-protocol-watch",
            "wire-vue-phase1-execution-roadmap",
            "wire-vue-readiness-control-plane",
            "populate-reviewed-redacted-eval-trace-evidence",
            "promote-eval-gate-bundle-from-evidence-only-to-reviewed-blocking",
            "bind-memory-rag-eval-suite-before-retrieval-runtime",
            "bind-durable-memory-runtime-after-lifecycle-and-source-digest-contract",
            "add-mcp-tools-call-only-after-safe-tool-executor-consent-hitl-audit-eval-binding",
            "keep-nim-hpc-slurm-bcm-paused-until-phase-2"
        );
        assertThat(overview.endpointMap())
            .containsEntry("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview")
            .containsEntry("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract")
            .containsEntry("officialVersionProtocolWatch", "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap")
            .containsEntry("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane")
            .containsEntry("mcpGovernanceOverview", "/api/agent/mcp/governance/overview")
            .containsEntry("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence")
            .containsEntry("releaseBlockingEvalGateContract", "/api/agent/observability/eval/release-blocking-gate-contract")
            .containsEntry("memoryRagCitationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract")
            .containsEntry("memoryRagSourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract")
            .containsEntry("memoryRagDurableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract")
            .containsEntry("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract")
            .containsEntry("memoryRagEvalSuiteBindingContract", "/api/agent/observability/memory-rag/eval-suite-binding-contract")
            .containsEntry("kubeManagerGovernanceOverview", "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview");
        assertThat(overview.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("adminOnlyAppliesToThisEndpoint", true)
            .containsEntry("readOnly", true)
            .containsEntry("endpointMapNavigationOnly", true)
            .containsEntry("endpointMapDoesNotGrantAccess", true)
            .containsEntry("endpointAccessMayDiffer", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("kubeManagerHttpClientBinding", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("writeAuthorityClosed", true)
            .containsEntry("phase2DomainPluginsPaused", true)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(overview.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawEndpoint", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(overview.kubeManagerGovernance().workbenchStatus()).isEqualTo("WRITE_GOVERNANCE_NOT_READY");
        assertThat(overview.evalWorkbenchCapabilities().capabilityCount()).isGreaterThan(0);
        assertThat(overview.mcpGovernance().governanceStatus()).isEqualTo("MANIFEST_ONLY_NOT_CALLABLE");
        assertThat(overview.toString())
            .contains("PHASE_1_TOP_TIER_CORE_IN_PROGRESS", "memory-rag-learning", "mcp-interoperability")
            .doesNotContain("secret-password", "Bearer", "/api/login", "kube-manager.internal")
            .doesNotContain("secret-token-value", "user-sensitive", "org-sensitive", "conv-sensitive");
    }

    @Test
    void source_shouldStayReadOnlyAndAvoidHiddenExecution() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("AgentTopTierReadinessOverviewResponse.of(")
            .contains("kubeManagerGovernanceService.overview()")
            .contains("evalWorkbenchCapabilitiesService.capabilities()")
            .contains("mcpGovernanceOverviewService.overview()")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("@PostMapping");
        assertThat(responseSource)
            .contains("toolExecutionTriggered")
            .contains("writeAuthorityClosed")
            .contains("phase2DomainPluginsPaused")
            .contains("durableMemoryImplemented")
            .contains("durableMemoryLifecycleContractImplemented")
            .contains("memoryRagEvalGateContractImplemented")
            .contains("memoryRagEvalSuiteBindingContractImplemented")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("execute(")
            .doesNotContain("record(");
    }

    private AgentTopTierReadinessOverviewService newService() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC);
        return new AgentTopTierReadinessOverviewService(
            newKubeManagerGovernanceService(clock),
            new AgentEvalWorkbenchCapabilitiesService(),
            newMcpGovernanceService(clock),
            clock
        );
    }

    private AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService newKubeManagerGovernanceService(Clock clock) {
        RetryRegistry retryRegistry = RetryRegistry.of(java.util.Map.of(
            "kubeManagerRead", RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(500)).build(),
            "kubeManagerWrite", RetryConfig.custom().maxAttempts(1).build()
        ));
        retryRegistry.retry("kubeManagerRead", "kubeManagerRead");
        retryRegistry.retry("kubeManagerWrite", "kubeManagerWrite");
        CircuitBreakerRegistry circuitBreakerRegistry = CircuitBreakerRegistry.of(java.util.Map.of(
            "kubeManager", CircuitBreakerConfig.custom()
                .slidingWindowSize(50)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50)
                .build()
        ));
        circuitBreakerRegistry.circuitBreaker("kubeManager", "kubeManager");
        BulkheadRegistry bulkheadRegistry = BulkheadRegistry.of(java.util.Map.of(
            "kubeManager", BulkheadConfig.custom()
                .maxConcurrentCalls(32)
                .maxWaitDuration(Duration.ofMillis(100))
                .build()
        ));
        bulkheadRegistry.bulkhead("kubeManager", "kubeManager");
        return new AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewService(
            new AgentKubeManagerHttpOutletHealthSummaryService(
                retryRegistry,
                circuitBreakerRegistry,
                bulkheadRegistry,
                new MockEnvironment()
                    .withProperty("atlas.backend.base-url", "http://kube-manager.internal:8100")
                    .withProperty("atlas.backend.connect-timeout-seconds", "10")
                    .withProperty("atlas.backend.read-timeout-seconds", "30")
                    .withProperty("atlas.backend.login-password", "secret-password"),
                clock
            ),
            new AgentKubeManagerWriteRetryReadinessService(retryRegistry, clock),
            new AgentKubeManagerWriteIdempotencyContractService(
                new com.atlas.http.KubeManagerWriteIdempotencyKeyDeriver(),
                clock
            ),
            new AgentKubeManagerWriteOperationSafetyContractService(clock),
            new AgentKubeManagerWriteRetryGovernanceContractService(clock),
            new AgentKubeManagerWriteReleaseGateContractService(clock),
            clock
        );
    }

    private McpGovernanceOverviewService newMcpGovernanceService(Clock clock) {
        ToolRegistry registry = new ToolRegistry(List.of(
            new NodeQueryTool(null),
            new MigConfigListTool(null),
            new UserQueryTool(null),
            new DeployDeleteTool(null)
        ), new UserPermissionContext());
        registry.init();
        McpToolManifestService manifestService = new McpToolManifestService(registry);
        return new FixedClockMcpGovernanceOverviewService(manifestService, clock);
    }

    private static final class FixedClockMcpGovernanceOverviewService extends McpGovernanceOverviewService {

        private final McpToolManifestService manifestService;
        private final Clock clock;

        private FixedClockMcpGovernanceOverviewService(McpToolManifestService manifestService, Clock clock) {
            super(manifestService);
            this.manifestService = manifestService;
            this.clock = clock;
        }

        @Override
        public McpGovernanceOverviewResponse overview() {
            return McpGovernanceOverviewResponse.of(Instant.now(clock), manifestService.buildSafeManifest());
        }
    }
}
