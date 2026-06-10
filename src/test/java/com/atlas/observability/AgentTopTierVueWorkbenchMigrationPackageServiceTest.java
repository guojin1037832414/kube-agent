package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Top-tier Vue workbench migration-package tests.
 */
class AgentTopTierVueWorkbenchMigrationPackageServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierVueWorkbenchMigrationPackageService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentTopTierVueWorkbenchMigrationPackageResponse.java"
    );

    @Test
    void migrationPackage_shouldPublishDryRunFrontendMigrationWithoutRuntimeAuthority() {
        AgentTopTierVueWorkbenchMigrationPackageService service = newService();

        AgentTopTierVueWorkbenchMigrationPackageResponse migrationPackage = service.migrationPackage();

        assertThat(migrationPackage.schemaVersion())
            .isEqualTo("agent-top-tier-vue-workbench-migration-package.v1");
        assertThat(migrationPackage.generatedAt()).isEqualTo(Instant.parse("2026-06-10T04:00:00Z"));
        assertThat(migrationPackage.migrationStatus())
            .isEqualTo("MIGRATION_PACKAGE_READY_TO_APPLY_TO_VUE_KUBE_MANAGER");
        assertThat(migrationPackage.frontendTarget())
            .isEqualTo("vue-kube-manager Vue 2 / Element UI top-tier Agent workbench migration");
        assertThat(migrationPackage.directFrontendWritePerformed()).isFalse();
        assertThat(migrationPackage.frontendRepositoryWritableInCurrentWorkspace()).isFalse();
        assertThat(migrationPackage.gitSafeDirectoryRequired()).isTrue();
        assertThat(migrationPackage.acceptanceContractEmbedded()).isTrue();
        assertThat(migrationPackage.readOnlyMigrationOnly()).isTrue();
        assertThat(migrationPackage.runtimeControlAllowed()).isFalse();
        assertThat(migrationPackage.repositoryFactCount()).isEqualTo(5);
        assertThat(migrationPackage.routePatchCount()).isEqualTo(5);
        assertThat(migrationPackage.fileBlueprintCount()).isEqualTo(10);
        assertThat(migrationPackage.apiExportCount()).isEqualTo(8);
        assertThat(migrationPackage.testBlueprintCount()).isEqualTo(9);
        assertThat(migrationPackage.validationCheckCount()).isEqualTo(8);
        assertThat(migrationPackage.forbiddenRuntimeAssertionCount()).isEqualTo(12);

        assertThat(migrationPackage.repositoryFacts()).extracting(fact -> fact.get("id"))
            .containsExactly(
                "frontend-repository-path",
                "frontend-git-safe-directory",
                "current-kube-agent-writable-root",
                "phase2-domain-scope",
                "permission-menu-exact-path-match"
            );
        assertThat(migrationPackage.repositoryFacts().toString())
            .contains("F:/gitProject/vue-kube-manager", "dubious-ownership",
                "git config --global --add safe.directory", "menus.some(menu => menu.path === route.path)");

        assertThat(migrationPackage.routePatches()).extracting(route -> route.get("id"))
            .containsExactly(
                "top-tier-technology-introduction-playbook",
                "top-tier-official-version-protocol-watch",
                "top-tier-advanced-technology-compatibility-matrix",
                "top-tier-advanced-technology-evidence-readiness",
                "top-tier-backend-technology-modernization-decision"
            );
        assertThat(migrationPackage.routePatches()).allSatisfy(route -> assertThat(route)
            .containsEntry("targetFile", "src/router/index.js")
            .containsEntry("operation", "insert-child-route-under-agent-parent-in-asyncRoutes")
            .containsEntry("parentRoutePath", "/agent")
            .containsEntry("parentWithPermission", false)
            .containsEntry("parentMenuPathRequired", false)
            .containsEntry("childPathMustBeAbsolute", true)
            .containsEntry("layout", "BackendLayout")
            .containsEntry("routeArray", "asyncRoutes")
            .containsEntry("menuMatchingRule", "menus.some(menu => menu.path === route.path)")
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(migrationPackage.routePatches().toString())
            .contains("/agent/top-tier/technology-introduction-playbook",
                "withPermission: true",
                "TopTierBackendTechnologyModernizationDecision");

        assertThat(migrationPackage.fileBlueprints()).extracting(file -> file.get("targetPath"))
            .containsExactly(
                "src/api/agent-observability.js",
                "src/router/index.js",
                "src/views/agent/top-tier/components",
                "src/views/agent/top-tier/technology-introduction-playbook/index.vue",
                "src/views/agent/top-tier/official-version-protocol-watch/index.vue",
                "src/views/agent/top-tier/advanced-technology-compatibility-matrix/index.vue",
                "src/views/agent/top-tier/advanced-technology-evidence-readiness/index.vue",
                "src/views/agent/top-tier/backend-technology-modernization-decision/index.vue",
                "tests/unit/fixtures/agent-top-tier-workbench.js",
                "tests/unit/views/agent/top-tier"
            );
        assertThat(migrationPackage.fileBlueprints()).allSatisfy(file -> assertThat(file)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false));
        String apiClientBlueprint = String.valueOf(migrationPackage.fileBlueprints().get(0).get("templateOrRule"));
        assertThat(apiClientBlueprint)
            .contains("import request from '@/utils/request'",
                "export function fetchTopTierVueWorkbenchAcceptanceContract",
                "method: 'get'",
                "params: query")
            .doesNotContain("axios.create", "fetch(", "method: 'post'", "method: 'put'",
                "method: 'patch'", "method: 'delete'");

        assertThat(migrationPackage.apiClientExports()).extracting(export -> export.get("functionName"))
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
        assertThat(migrationPackage.apiClientExports()).allSatisfy(export -> assertThat(export)
            .containsEntry("method", "get")
            .containsEntry("apiFile", "src/api/agent-observability.js")
            .containsEntry("unwrapExpression", "response.data")
            .containsEntry("mutatingMethodAllowed", false)
            .containsEntry("runtimeBackendCallAllowed", false));

        assertThat(migrationPackage.testBlueprints()).extracting(test -> test.get("id"))
            .contains("router-mounts", "source-json-xss", "auth-empty-states");
        assertThat(migrationPackage.testBlueprints()).allSatisfy(test -> assertThat(test)
            .containsEntry("requiresMockedHttp", true)
            .containsEntry("requiresRealBackend", false)
            .containsEntry("requiresKubeManager8100", false)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(migrationPackage.testBlueprints().toString())
            .contains(".el-table", ".el-tag", ".el-alert", "v-html", "401", "403");

        assertThat(migrationPackage.validationChecks()).extracting(check -> check.get("id"))
            .containsExactly(
                "frontend-lint",
                "frontend-unit",
                "frontend-ci",
                "route-scan",
                "api-mutation-scan",
                "runtime-selector-scan",
                "git-whitespace",
                "backend-contract"
            );
        assertThat(migrationPackage.validationChecks().toString())
            .contains("npm run lint", "npm run test:unit", "git diff --check");

        assertThat(migrationPackage.forbiddenRuntimeAssertions()).extracting(assertion -> assertion.get("id"))
            .contains("mcp-tools-call-button", "enable-rag-runtime-button",
                "kube-manager-write-button", "reopen-phase2-button");
        assertThat(migrationPackage.forbiddenRuntimeAssertions()).allSatisfy(assertion -> assertThat(assertion)
            .containsEntry("mustBeAbsent", true)
            .containsEntry("apiExportAllowed", false)
            .containsEntry("clickHandlerAllowed", false)
            .containsEntry("runtimeControlAllowed", false));

        assertThat(migrationPackage.applyOrder()).containsExactly(
            "trust-and-add-vue-kube-manager-as-writable-root",
            "confirm-vue-kube-manager-git-status-is-clean",
            "create-agent-observability-get-only-api-client",
            "mount-agent-top-tier-routes-under-asyncRoutes-BackendLayout",
            "create-shared-read-only-Element-UI-components",
            "create-five-top-tier-agent-workbench-pages",
            "add-mocked-fixtures-and-permission-menu-fixture",
            "add-Jest-acceptance-and-absence-tests",
            "run-lint-unit-ci-and-forbidden-runtime-scans",
            "commit-and-push-frontend-and-backend-recovery-memory"
        );
        assertThat(migrationPackage.acceptanceContract().schemaVersion())
            .isEqualTo("agent-top-tier-vue-workbench-acceptance-contract.v1");
        assertThat(migrationPackage.endpointMap())
            .containsEntry("topTierVueWorkbenchMigrationPackage",
                "/api/agent/observability/top-tier/vue-workbench-migration-package")
            .containsEntry("topTierVueWorkbenchAcceptanceContract",
                "/api/agent/observability/top-tier/vue-workbench-acceptance-contract");
        assertThat(migrationPackage.packagePolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("migrationPackageOnly", true)
            .containsEntry("directFrontendWritePerformed", false)
            .containsEntry("frontendRepositoryWritableInCurrentWorkspace", false)
            .containsEntry("frontendRepositoryGitStatusBlockedByDubiousOwnership", true)
            .containsEntry("mockedHttpRequired", true)
            .containsEntry("realBackendRequiredForAcceptance", false)
            .containsEntry("kubeManager8100Required", false)
            .containsEntry("runtimeButtonsAllowed", false)
            .containsEntry("dependencyUpgradeButtonsAllowed", false)
            .containsEntry("mutatingApiMethodsAllowed", false)
            .containsEntry("phase2NimHpcSlurmBcmPaused", true);
        assertThat(migrationPackage.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("migrationPackageOnly", true)
            .containsEntry("directFrontendWritePerformed", false)
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
            .containsEntry("phase2NimHpcSlurmBcmTouched", false)
            .containsEntry("acceptanceContractRuntimeControlAllowed", false);
        assertThat(migrationPackage.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(migrationPackage.toString())
            .contains("MIGRATION_PACKAGE_READY_TO_APPLY_TO_VUE_KUBE_MANAGER",
                "permission-menu-exact-path-match", "source-json-xss")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayMigrationPackageOnlyAndAvoidRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("acceptanceContractService.contract()")
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
            .contains("vue-workbench-migration-package")
            .contains("directFrontendWritePerformed")
            .contains("frontendRepositoryWritableInCurrentWorkspace")
            .contains("permission-menu-exact-path-match")
            .contains("forbiddenRuntimeAssertions")
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

    private static AgentTopTierVueWorkbenchMigrationPackageService newService() {
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
        AgentTopTierVueWorkbenchAcceptanceContractService acceptanceContractService =
            new AgentTopTierVueWorkbenchAcceptanceContractService(
                packageService,
                Clock.fixed(Instant.parse("2026-06-10T03:00:00Z"), ZoneOffset.UTC)
            );
        return new AgentTopTierVueWorkbenchMigrationPackageService(
            acceptanceContractService,
            Clock.fixed(Instant.parse("2026-06-10T04:00:00Z"), ZoneOffset.UTC)
        );
    }
}
