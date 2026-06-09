package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Top-tier Vue workbench implementation-package contract tests.
 */
class AgentTopTierVueWorkbenchImplementationPackageServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierVueWorkbenchImplementationPackageService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierVueWorkbenchImplementationPackageResponse.java"
    );

    @Test
    void implementationPackage_shouldComposeBothVueBindingSpecsWithoutRuntimeAuthority() {
        AgentOfficialVersionProtocolWatchService watchService = new AgentOfficialVersionProtocolWatchService(
            Clock.fixed(Instant.parse("2026-06-10T01:00:00Z"), ZoneOffset.UTC)
        );
        AgentOfficialVersionProtocolWatchDashboardService dashboardService =
            new AgentOfficialVersionProtocolWatchDashboardService(
                watchService,
                Clock.fixed(Instant.parse("2026-06-10T01:10:00Z"), ZoneOffset.UTC)
            );
        AgentOfficialVersionProtocolWatchVueBindingSpecService officialBindingService =
            new AgentOfficialVersionProtocolWatchVueBindingSpecService(
                dashboardService,
                Clock.fixed(Instant.parse("2026-06-10T01:20:00Z"), ZoneOffset.UTC)
            );
        AgentAdvancedTechnologyCompatibilityMatrixService matrixService =
            new AgentAdvancedTechnologyCompatibilityMatrixService(
                watchService,
                Clock.fixed(Instant.parse("2026-06-10T01:30:00Z"), ZoneOffset.UTC)
            );
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService matrixBindingService =
            new AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService(
                matrixService,
                Clock.fixed(Instant.parse("2026-06-10T01:40:00Z"), ZoneOffset.UTC)
            );
        AgentTopTierVueWorkbenchImplementationPackageService service =
            new AgentTopTierVueWorkbenchImplementationPackageService(
                officialBindingService,
                matrixBindingService,
                Clock.fixed(Instant.parse("2026-06-10T02:00:00Z"), ZoneOffset.UTC)
            );

        AgentTopTierVueWorkbenchImplementationPackageResponse response = service.implementationPackage();

        assertThat(response.schemaVersion())
            .isEqualTo("agent-top-tier-vue-workbench-implementation-package.v1");
        assertThat(response.generatedAt()).isEqualTo(Instant.parse("2026-06-10T02:00:00Z"));
        assertThat(response.packageStatus()).isEqualTo("IMPLEMENTATION_PACKAGE_READY");
        assertThat(response.frontendTarget())
            .isEqualTo("vue-kube-manager Phase 1 top-tier Agent latest-technology workbench");
        assertThat(response.sourceBindingSpecsEmbedded()).isTrue();
        assertThat(response.runtimeControlAllowed()).isFalse();
        assertThat(response.routeSpecCount()).isEqualTo(2);
        assertThat(response.apiClientBindingCount()).isEqualTo(4);
        assertThat(response.pageAssemblyCount()).isEqualTo(2);
        assertThat(response.sharedComponentCount()).isEqualTo(7);
        assertThat(response.acceptanceFixtureCount()).isEqualTo(6);
        assertThat(response.routeSpecs()).extracting(route -> route.get("id"))
            .containsExactly(
                "top-tier-official-version-protocol-watch",
                "top-tier-advanced-technology-compatibility-matrix"
            );
        assertThat(response.routeSpecs()).allSatisfy(route -> assertThat(route)
            .containsEntry("requiresAdminSession", true)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("phase2ScopeVisibleAsPausedOnly", true));
        assertThat(response.apiClientBindings()).extracting(client -> client.get("name"))
            .containsExactly(
                "fetchOfficialWatchDashboard",
                "fetchOfficialWatchBindingSpec",
                "fetchCompatibilityMatrix",
                "fetchCompatibilityMatrixBindingSpec"
            );
        assertThat(response.apiClientBindings()).allSatisfy(client -> assertThat(client)
            .containsEntry("method", "GET")
            .containsEntry("requiresAdminSession", true)
            .containsEntry("mockedFixtureAllowed", true)
            .containsEntry("runtimeBackendCallAllowed", false)
            .containsEntry("kubeManager8100Required", false));
        assertThat(response.pageAssemblies()).extracting(page -> page.get("pageId"))
            .containsExactly("official-watch-page", "compatibility-matrix-page");
        assertThat(response.pageAssemblies()).allSatisfy(page -> assertThat(page)
            .containsEntry("ownsGovernanceLogic", false)
            .containsEntry("usesBackendStateRules", true)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(response.sharedComponentContracts()).extracting(component -> component.get("componentName"))
            .containsExactly("StatusBadge", "MetricNumber", "EvidenceTagList", "ReadonlyTable",
                "DisabledActionList", "ExternalOfficialLink", "ReadonlyJsonPanel");
        assertThat(response.sharedComponentContracts()).allSatisfy(component -> assertThat(component)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("inlineEditAllowed", false));
        assertThat(response.acceptanceFixtures()).extracting(fixture -> fixture.get("id"))
            .containsExactly(
                "official-watch-page-renders-with-mocked-binding-spec",
                "compatibility-matrix-page-renders-with-mocked-binding-spec",
                "cross-page-navigation-keeps-read-only-state",
                "runtime-buttons-absent-in-both-pages",
                "admin-auth-required-for-all-api-calls",
                "source-json-drilldown-redacted"
            );
        assertThat(response.acceptanceFixtures()).allSatisfy(fixture -> assertThat(fixture)
            .containsEntry("requiresMockedHttp", true)
            .containsEntry("requiresRuntimeBackendCalls", false)
            .containsEntry("requiresKubeManager8100", false)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(response.forbiddenRuntimeControls()).extracting(control -> control.get("groupId"))
            .containsExactly(
                "official-watch-disabled-actions",
                "compatibility-matrix-disabled-actions",
                "global-top-tier-runtime-controls"
            );
        assertThat(response.forbiddenRuntimeControls()).allSatisfy(control -> assertThat(control)
            .containsEntry("buttonVisible", false)
            .containsEntry("clickHandlerAllowed", false)
            .containsEntry("requiresSeparateReviewedSlice", true));
        assertThat(response.implementationOrder()).contains(
            "create-top-tier-agent-workbench-navigation",
            "implement-official-watch-page-from-binding-spec",
            "implement-compatibility-matrix-page-from-binding-spec",
            "verify-runtime-control-buttons-are-absent"
        );
        assertThat(response.officialWatchBindingSpec().schemaVersion())
            .isEqualTo("agent-official-version-protocol-watch-vue-binding-spec.v1");
        assertThat(response.compatibilityMatrixBindingSpec().schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix-vue-binding-spec.v1");
        assertThat(response.endpointMap())
            .containsEntry("topTierVueWorkbenchImplementationPackage",
                "/api/agent/observability/top-tier/vue-workbench-implementation-package")
            .containsEntry("officialVersionProtocolWatchVueBindingSpec",
                "/api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec")
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec");
        assertThat(response.packagePolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("implementationPackageOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("routeSpecCount", 2)
            .containsEntry("apiClientBindingCount", 4)
            .containsEntry("pageAssemblyCount", 2)
            .containsEntry("sharedComponentCount", 7)
            .containsEntry("acceptanceFixtureCount", 6)
            .containsEntry("runtimeButtonsAllowed", false)
            .containsEntry("dependencyUpgradeButtonsAllowed", false);
        assertThat(response.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("implementationPackageOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(response.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(response.toString())
            .contains("top-tier-official-version-protocol-watch",
                "top-tier-advanced-technology-compatibility-matrix",
                "runtime-buttons-absent-in-both-pages")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayImplementationPackageOnlyAndAvoidRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("officialWatchBindingSpecService.spec()")
            .contains("compatibilityMatrixBindingSpecService.spec()")
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
            .contains("vue-workbench-implementation-package")
            .contains("routeSpecs")
            .contains("apiClientBindings")
            .contains("acceptanceFixtures")
            .contains("forbiddenRuntimeControls")
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
