package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Official watch Vue binding-spec contract tests.
 */
class AgentOfficialVersionProtocolWatchVueBindingSpecServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentOfficialVersionProtocolWatchVueBindingSpecService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentOfficialVersionProtocolWatchVueBindingSpecResponse.java"
    );

    @Test
    void spec_shouldPublishVueComponentFieldFixtureContractWithoutRuntimeAuthority() {
        AgentOfficialVersionProtocolWatchService watchService = new AgentOfficialVersionProtocolWatchService(
            Clock.fixed(Instant.parse("2026-06-09T15:00:00Z"), ZoneOffset.UTC)
        );
        AgentOfficialVersionProtocolWatchDashboardService dashboardService =
            new AgentOfficialVersionProtocolWatchDashboardService(
                watchService,
                Clock.fixed(Instant.parse("2026-06-09T15:30:00Z"), ZoneOffset.UTC)
            );
        AgentOfficialVersionProtocolWatchVueBindingSpecService service =
            new AgentOfficialVersionProtocolWatchVueBindingSpecService(
                dashboardService,
                Clock.fixed(Instant.parse("2026-06-09T16:00:00Z"), ZoneOffset.UTC)
            );

        AgentOfficialVersionProtocolWatchVueBindingSpecResponse spec = service.spec();

        assertThat(spec.schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch-vue-binding-spec.v1");
        assertThat(spec.generatedAt()).isEqualTo(Instant.parse("2026-06-09T16:00:00Z"));
        assertThat(spec.bindingStatus()).isEqualTo("VUE_BINDING_SPEC_READY");
        assertThat(spec.frontendTarget())
            .isEqualTo("vue-kube-manager official Agent technology/protocol watch dashboard binding");
        assertThat(spec.sourceDashboardEmbedded()).isTrue();
        assertThat(spec.runtimeControlAllowed()).isFalse();
        assertThat(spec.componentSpecCount()).isEqualTo(7);
        assertThat(spec.fieldBindingCount()).isEqualTo(12);
        assertThat(spec.tableColumnGroupCount()).isEqualTo(4);
        assertThat(spec.disabledActionBindingCount()).isEqualTo(6);
        assertThat(spec.fixtureCount()).isEqualTo(4);
        assertThat(spec.componentSpecs()).extracting(component -> component.get("name"))
            .containsExactly(
                "OfficialWatchSummaryStrip",
                "OfficialSourceCardGrid",
                "TechnologyTrackMatrix",
                "AdoptionGateTable",
                "BlockedShortcutTable",
                "DisabledRuntimeActionList",
                "OfficialWatchSourceJsonPanel"
            );
        assertThat(spec.componentSpecs()).allSatisfy(component -> assertThat(component)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("inlineEditAllowed", false));
        assertThat(spec.fieldBindings()).extracting(binding -> binding.get("fieldPath"))
            .contains(
                "dashboardStatus",
                "sourceCards[].officialUrl",
                "technologyTrackCards[].disabledRuntimeActions",
                "dashboardPolicy.runtimeControlAllowed",
                "safety.mcpToolsCall"
            );
        assertThat(spec.tableColumnGroups()).extracting(group -> group.get("dataField"))
            .containsExactly(
                "sourceCards",
                "technologyTrackCards",
                "adoptionGateRows",
                "blockedRuntimeShortcutRows"
            );
        assertThat(spec.stateRenderingRules()).extracting(rule -> rule.get("status"))
            .contains("INFO", "BLOCKING", "REQUIRED_GATE", "BLOCKED_SHORTCUT", "OFFICIAL_SOURCE_REVIEWED");
        assertThat(spec.stateRenderingRules()).allSatisfy(rule -> assertThat(rule)
            .containsEntry("allowsRuntimeAction", false));
        assertThat(spec.disabledActionBindings()).extracting(binding -> binding.get("actionId"))
            .containsExactly(
                "upgrade-dependencies-from-dashboard",
                "enable-mcp-tools-call",
                "enable-a2a-runtime-handoff",
                "enable-retrieval-runtime",
                "enable-ci-blocking",
                "reopen-phase2-domain-plugins"
            );
        assertThat(spec.disabledActionBindings()).allSatisfy(binding -> assertThat(binding)
            .containsEntry("renderAs", "disabled-row")
            .containsEntry("buttonVisible", false)
            .containsEntry("clickHandlerAllowed", false)
            .containsEntry("requiresSeparateReviewedSlice", true));
        assertThat(spec.testFixtures()).extracting(fixture -> fixture.get("id"))
            .containsExactly(
                "happy-path-dashboard",
                "mcp-security-source-visible",
                "runtime-buttons-absent",
                "source-watch-drilldown"
            );
        assertThat(spec.testFixtures()).allSatisfy(fixture -> assertThat(fixture)
            .containsEntry("requiresMockedHttp", true)
            .containsEntry("requiresRuntimeBackendCalls", false)
            .containsEntry("requiresKubeManager8100", false));
        assertThat(spec.implementationChecklist()).contains(
            "fetch-dashboard-endpoint-with-admin-session",
            "render-official-source-cards-with-external-link-only",
            "hide-all-runtime-enable-buttons",
            "add-fixtures-for-nsa-mcp-security-source-and-disabled-actions"
        );
        assertThat(spec.endpointMap())
            .containsEntry("officialVersionProtocolWatchVueBindingSpec",
                "/api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec")
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec")
            .containsEntry("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness")
            .containsEntry("officialVersionProtocolWatchDashboard",
                "/api/agent/observability/top-tier/official-version-protocol-watch/dashboard");
        assertThat(spec.bindingPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("bindingSpecOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("sourceDashboardEmbedded", true)
            .containsEntry("componentSpecCount", 7)
            .containsEntry("fieldBindingCount", 12)
            .containsEntry("tableColumnGroupCount", 4)
            .containsEntry("disabledActionBindingCount", 6)
            .containsEntry("fixtureCount", 4)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("runtimeButtonsAllowed", false)
            .containsEntry("inlineEditAllowed", false)
            .containsEntry("mockedHttpFixturesRequired", true);
        assertThat(spec.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("bindingSpecOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("sourceDashboardReadOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
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
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(spec.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(spec.sourceDashboard().schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch-dashboard.v1");
        assertThat(spec.toString())
            .contains("OfficialSourceCardGrid", "nsa-mcp-security-2026-06", "runtime-buttons-absent")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayBindingSpecOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("dashboardService.dashboard()")
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
            .contains("official-version-protocol-watch-vue-binding-spec")
            .contains("componentSpecs")
            .contains("fieldBindings")
            .contains("testFixtures")
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
