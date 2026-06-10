package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Top-tier Vue workbench acceptance-contract tests.
 */
class AgentTopTierVueWorkbenchAcceptanceContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierVueWorkbenchAcceptanceContractService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierVueWorkbenchAcceptanceContractResponse.java"
    );

    @Test
    void contract_shouldPublishVue2ElementUiAcceptanceFixturesWithoutRuntimeAuthority() {
        AgentTopTierVueWorkbenchAcceptanceContractService service = newService();

        AgentTopTierVueWorkbenchAcceptanceContractResponse contract = service.contract();

        assertThat(contract.schemaVersion())
            .isEqualTo("agent-top-tier-vue-workbench-acceptance-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-10T03:00:00Z"));
        assertThat(contract.contractStatus())
            .isEqualTo("ACCEPTANCE_CONTRACT_READY_FOR_VUE2_ELEMENT_UI_IMPLEMENTATION");
        assertThat(contract.frontendTarget())
            .isEqualTo("vue-kube-manager Vue 2 / Element UI top-tier Agent technology workbench");
        assertThat(contract.sourceImplementationPackageEmbedded()).isTrue();
        assertThat(contract.vue2ElementUiProfile()).isTrue();
        assertThat(contract.fixtureOnly()).isTrue();
        assertThat(contract.runtimeControlAllowed()).isFalse();
        assertThat(contract.frontendStackFactCount()).isEqualTo(6);
        assertThat(contract.routeMountSpecCount()).isEqualTo(5);
        assertThat(contract.apiClientSpecCount()).isEqualTo(8);
        assertThat(contract.pageFixtureSpecCount()).isEqualTo(5);
        assertThat(contract.acceptanceScenarioCount()).isEqualTo(10);
        assertThat(contract.forbiddenRuntimeSelectorCount()).isEqualTo(12);
        assertThat(contract.implementationFileCount()).isEqualTo(10);
        assertThat(contract.testCommandCount()).isEqualTo(3);

        assertThat(contract.frontendStackFacts()).extracting(fact -> fact.get("id"))
            .containsExactly("vue-runtime", "router", "ui-kit", "http-client", "unit-test", "lint");
        assertThat(contract.frontendStackFacts()).extracting(fact -> fact.get("sourcePath"))
            .contains("F:/gitProject/vue-kube-manager/package.json",
                "F:/gitProject/vue-kube-manager/src/router/index.js",
                "F:/gitProject/vue-kube-manager/src/utils/request.js",
                "F:/gitProject/vue-kube-manager/jest.config.js");
        assertThat(contract.frontendStackFacts()).allSatisfy(fact -> assertThat(fact)
            .containsKey("exactObservedVersion")
            .containsEntry("packageJsonPath", "F:/gitProject/vue-kube-manager/package.json")
            .containsEntry("runtimeControlAllowed", false));
        assertThat(contract.frontendStackFacts().toString())
            .contains("^2.6.11", "3.0.6", "^2.15.14", "axios@0.18.1",
                "vue@3", "vue-router@4", "element-plus", "vite");
        assertThat(contract.routeMountSpecs()).extracting(route -> route.get("routePath"))
            .containsExactly(
                "/agent/top-tier/technology-introduction-playbook",
                "/agent/top-tier/official-version-protocol-watch",
                "/agent/top-tier/advanced-technology-compatibility-matrix",
                "/agent/top-tier/advanced-technology-evidence-readiness",
                "/agent/top-tier/backend-technology-modernization-decision"
            );
        assertThat(contract.routeMountSpecs()).allSatisfy(route -> assertThat(route)
            .containsEntry("routerFile", "src/router/index.js")
            .containsEntry("routeArray", "asyncRoutes")
            .containsEntry("parentRoutePath", "/agent")
            .containsEntry("layout", "BackendLayout")
            .containsEntry("routeNamePattern", "^[A-Za-z][A-Za-z0-9]*$")
            .containsEntry("hidden", false)
            .containsEntry("alwaysShow", false)
            .containsEntry("withPermission", true)
            .containsEntry("permissionMenuEndpoint", "/api/{organizationId}/permission/menu/my")
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(contract.routeMountSpecs()).extracting(route -> route.get("routeName"))
            .containsExactly(
                "TopTierTechnologyIntroductionPlaybook",
                "TopTierOfficialVersionProtocolWatch",
                "TopTierAdvancedTechnologyCompatibilityMatrix",
                "TopTierAdvancedTechnologyEvidenceReadiness",
                "TopTierBackendTechnologyModernizationDecision"
            )
            .allSatisfy(routeName -> assertThat(String.valueOf(routeName)).doesNotContain(" "));
        assertThat(contract.apiClientSpecs()).extracting(client -> client.get("functionName"))
            .containsExactly(
                "fetchTopTierVueWorkbenchAcceptanceContract",
                "fetchTopTierVueWorkbenchImplementationPackage",
                "fetchTechnologyIntroductionPlaybook",
                "fetchOfficialVersionProtocolWatchVueBindingSpec",
                "fetchAdvancedTechnologyCompatibilityMatrixVueBindingSpec",
                "fetchAdvancedTechnologyCompatibilityMatrixEvidenceReadiness",
                "fetchBackendTechnologyModernizationDecision",
                "fetchVueReadinessControlPlane"
            );
        assertThat(contract.apiClientSpecs()).allSatisfy(client -> assertThat(client)
            .containsEntry("apiFile", "src/api/agent-observability.js")
            .containsEntry("method", "get")
            .containsEntry("importPath", "@/utils/request")
            .containsEntry("viewUnwrapExpression", "response.data")
            .containsEntry("usesSharedRequestWrapper", true)
            .containsEntry("requiresAdminSession", true)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeBackendCallAllowed", false)
            .containsEntry("kubeManager8100Required", false));
        assertThat(contract.apiClientSpecs().toString())
            .contains("successField", "payloadField", "legacyResultFieldRequired", "axios.create",
                "post", "put", "patch", "delete");
        assertThat(contract.pageFixtureSpecs()).extracting(fixture -> fixture.get("id"))
            .containsExactly(
                "technology-introduction-playbook-fixture",
                "official-watch-fixture",
                "compatibility-matrix-fixture",
                "evidence-readiness-fixture",
                "backend-modernization-decision-fixture"
            );
        assertThat(contract.pageFixtureSpecs()).allSatisfy(fixture -> assertThat(fixture)
            .containsEntry("fixtureFile", "tests/unit/fixtures/agent-top-tier-workbench.js")
            .containsEntry("fixtureExportStyle", "named ES module fixture object")
            .containsEntry("mockStrategy", "jest.mock('@/api/agent-observability')")
            .containsEntry("mockedHttpOnly", true)
            .containsEntry("runtimeBackendCallAllowed", false)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(contract.pageFixtureSpecs().toString())
            .contains("responseEnvelopeExample", "httpStatusVariants", "401", "403");
        assertThat(contract.acceptanceScenarios()).extracting(scenario -> scenario.get("id"))
            .containsExactly(
                "router-mounts-five-workbench-pages",
                "api-client-uses-read-only-get-methods",
                "technology-playbook-renders-stages-lanes-and-gates",
                "official-watch-renders-backend-binding-spec",
                "compatibility-matrix-renders-backend-binding-spec",
                "evidence-readiness-renders-blockers",
                "backend-decision-renders-learning-path",
                "cross-page-navigation-keeps-read-only-state",
                "admin-auth-errors-render-readonly-empty-state",
                "source-json-panels-are-redacted-and-not-editable"
            );
        assertThat(contract.acceptanceScenarios()).allSatisfy(scenario -> assertThat(scenario)
            .containsEntry("requiresMockedHttp", true)
            .containsEntry("requiresRealBackend", false)
            .containsEntry("requiresKubeManager8100", false)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(contract.forbiddenRuntimeSelectors()).extracting(selector -> selector.get("id"))
            .contains(
                "mcp-tools-call-button",
                "a2a-runtime-handoff-button",
                "enable-rag-runtime-button",
                "enable-graphrag-button",
                "enable-ci-blocking-button",
                "kube-manager-write-button",
                "reopen-phase2-button"
            );
        assertThat(contract.forbiddenRuntimeSelectors()).allSatisfy(selector -> assertThat(selector)
            .containsEntry("mustBeAbsent", true)
            .containsEntry("clickHandlerAllowed", false)
            .containsEntry("apiExportAllowed", false)
            .containsEntry("requiresSeparateReviewedSlice", true));
        assertThat(contract.forbiddenRuntimeSelectors().toString())
            .contains("wrapper.find", "Object.keys(api)", "proves authority is absent");
        assertThat(contract.implementationFiles()).extracting(file -> file.get("path"))
            .contains(
                "src/api/agent-observability.js",
                "src/router/index.js",
                "src/views/agent/top-tier/components",
                "tests/unit/fixtures/agent-top-tier-workbench.js",
                "tests/unit/views/agent/top-tier"
            );
        assertThat(contract.implementationFiles()).allSatisfy(file -> assertThat(file)
            .containsEntry("ownerScope", "src/views/agent/top-tier")
            .containsEntry("readOnlyContract", true)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(contract.testCommands()).extracting(command -> command.get("command"))
            .containsExactly("npm run lint", "npm run test:unit", "npm run test:ci");
        assertThat(contract.teachingCheckpoints()).extracting(checkpoint -> checkpoint.get("id"))
            .contains("backend-owned-ui-contracts", "absence-as-a-test", "operator-learning-workbench");
        assertThat(contract.sourceImplementationPackage().schemaVersion())
            .isEqualTo("agent-top-tier-vue-workbench-implementation-package.v1");
        assertThat(contract.endpointMap())
            .containsEntry("topTierVueWorkbenchAcceptanceContract",
                "/api/agent/observability/top-tier/vue-workbench-acceptance-contract")
            .containsEntry("topTierVueWorkbenchImplementationPackage",
                "/api/agent/observability/top-tier/vue-workbench-implementation-package")
            .containsEntry("topTierTechnologyIntroductionPlaybook",
                "/api/agent/observability/top-tier/technology-introduction-playbook");
        assertThat(contract.contractPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("acceptanceContractOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("frontendStackLockedToObservedLegacyRuntime", true)
            .containsEntry("mockedHttpRequired", true)
            .containsEntry("realBackendRequiredForAcceptance", false)
            .containsEntry("kubeManager8100Required", false)
            .containsEntry("runtimeButtonsAllowed", false)
            .containsEntry("dependencyUpgradeButtonsAllowed", false);
        assertThat(contract.contractPolicy().toString())
            .contains("forbiddenFrontendAdditions", "vue@3", "pinia", "routeMountShape", "asyncRoutes",
                "permissionMenuFixture", "menus.some(menu => menu.path === route.path)",
                "requiredMenuPaths", "expectedElementUiSelectors", ".el-table", ".el-tabs",
                "forbiddenRequestUrls", "/api/agent/mcp/tools/call", "forbiddenTextPatterns");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("acceptanceContractOnly", true)
            .containsEntry("runtimeControlAllowed", false)
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
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(contract.toString())
            .contains("Vue 2", "Element UI", "mcp-tools-call-button",
                "source-json-panels-are-redacted-and-not-editable")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayAcceptanceContractOnlyAndAvoidRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("implementationPackageService.implementationPackage()")
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
            .contains("vue-workbench-acceptance-contract")
            .contains("frontendStackFacts")
            .contains("routeMountSpecs")
            .contains("acceptanceScenarios")
            .contains("forbiddenRuntimeSelectors")
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

    private static AgentTopTierVueWorkbenchAcceptanceContractService newService() {
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
        AgentTopTierVueWorkbenchImplementationPackageService packageService =
            new AgentTopTierVueWorkbenchImplementationPackageService(
                officialBindingService,
                matrixBindingService,
                Clock.fixed(Instant.parse("2026-06-10T02:00:00Z"), ZoneOffset.UTC)
            );
        return new AgentTopTierVueWorkbenchAcceptanceContractService(
            packageService,
            Clock.fixed(Instant.parse("2026-06-10T03:00:00Z"), ZoneOffset.UTC)
        );
    }
}
