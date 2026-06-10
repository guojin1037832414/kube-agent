package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Backend-owned migration package for applying the top-tier Agent workbench to vue-kube-manager.
 *
 * <p>The current Codex workspace can read the frontend repository, but cannot safely write it in
 * this slice. This package keeps the next frontend implementation executable and auditable by
 * publishing the exact target routes, file blueprints, test blueprints, validation checks, and
 * absent-runtime-control assertions derived from the M5.83 acceptance contract.</p>
 */
public record AgentTopTierVueWorkbenchMigrationPackageResponse(
    String schemaVersion,
    Instant generatedAt,
    String migrationStatus,
    String frontendTarget,
    boolean directFrontendWritePerformed,
    boolean frontendRepositoryWritableInCurrentWorkspace,
    boolean gitSafeDirectoryRequired,
    boolean acceptanceContractEmbedded,
    boolean readOnlyMigrationOnly,
    boolean runtimeControlAllowed,
    int repositoryFactCount,
    int routePatchCount,
    int fileBlueprintCount,
    int apiExportCount,
    int testBlueprintCount,
    int validationCheckCount,
    int forbiddenRuntimeAssertionCount,
    List<Map<String, Object>> repositoryFacts,
    List<Map<String, Object>> routePatches,
    List<Map<String, Object>> fileBlueprints,
    List<Map<String, Object>> apiClientExports,
    List<Map<String, Object>> testBlueprints,
    List<Map<String, Object>> validationChecks,
    List<Map<String, Object>> forbiddenRuntimeAssertions,
    List<String> applyOrder,
    AgentTopTierVueWorkbenchAcceptanceContractResponse acceptanceContract,
    Map<String, Object> endpointMap,
    Map<String, Object> packagePolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-top-tier-vue-workbench-migration-package.v1";
    public static final String MIGRATION_PACKAGE_ENDPOINT =
        "/api/agent/observability/top-tier/vue-workbench-migration-package";

    public static AgentTopTierVueWorkbenchMigrationPackageResponse of(
        Instant generatedAt,
        AgentTopTierVueWorkbenchAcceptanceContractResponse acceptanceContract
    ) {
        List<Map<String, Object>> repositoryFacts = buildRepositoryFacts();
        List<Map<String, Object>> routePatches = buildRoutePatches(acceptanceContract);
        List<Map<String, Object>> fileBlueprints = buildFileBlueprints(acceptanceContract);
        List<Map<String, Object>> apiClientExports = buildApiClientExports(acceptanceContract);
        List<Map<String, Object>> testBlueprints = buildTestBlueprints(acceptanceContract);
        List<Map<String, Object>> validationChecks = buildValidationChecks(acceptanceContract);
        List<Map<String, Object>> forbiddenRuntimeAssertions =
            buildForbiddenRuntimeAssertions(acceptanceContract);
        return new AgentTopTierVueWorkbenchMigrationPackageResponse(
            SCHEMA_VERSION,
            generatedAt,
            migrationStatus(acceptanceContract),
            "vue-kube-manager Vue 2 / Element UI top-tier Agent workbench migration",
            false,
            false,
            true,
            acceptanceContract != null,
            true,
            false,
            repositoryFacts.size(),
            routePatches.size(),
            fileBlueprints.size(),
            apiClientExports.size(),
            testBlueprints.size(),
            validationChecks.size(),
            forbiddenRuntimeAssertions.size(),
            repositoryFacts,
            routePatches,
            fileBlueprints,
            apiClientExports,
            testBlueprints,
            validationChecks,
            forbiddenRuntimeAssertions,
            buildApplyOrder(),
            acceptanceContract,
            buildEndpointMap(),
            buildPackagePolicy(acceptanceContract, routePatches, fileBlueprints, apiClientExports,
                testBlueprints, validationChecks, forbiddenRuntimeAssertions),
            buildSafety(acceptanceContract),
            buildPrivacy(acceptanceContract)
        );
    }

    private static String migrationStatus(AgentTopTierVueWorkbenchAcceptanceContractResponse contract) {
        if (contract == null) {
            return "MIGRATION_PACKAGE_BLOCKED_BY_MISSING_ACCEPTANCE_CONTRACT";
        }
        if (!"ACCEPTANCE_CONTRACT_READY_FOR_VUE2_ELEMENT_UI_IMPLEMENTATION".equals(contract.contractStatus())) {
            return "MIGRATION_PACKAGE_BLOCKED_BY_ACCEPTANCE_CONTRACT";
        }
        if (contract.runtimeControlAllowed()
            || Boolean.TRUE.equals(contract.safety().get("runtimeControlAllowed"))
            || Boolean.TRUE.equals(contract.safety().get("toolExecution"))
            || Boolean.TRUE.equals(contract.safety().get("kubeManagerCalls"))
            || Boolean.TRUE.equals(contract.safety().get("mcpToolsCall"))
            || Boolean.TRUE.equals(contract.safety().get("phase2NimHpcSlurmBcmTouched"))) {
            return "MIGRATION_PACKAGE_BLOCKED_BY_UNEXPECTED_RUNTIME_AUTHORITY";
        }
        return "MIGRATION_PACKAGE_READY_TO_APPLY_TO_VUE_KUBE_MANAGER";
    }

    private static List<Map<String, Object>> buildRepositoryFacts() {
        return List.of(
            repositoryFact(
                "frontend-repository-path",
                "F:/gitProject/vue-kube-manager",
                "Readable from this workspace; direct edits are outside the current writable root.",
                "Add the repository as a writable workspace root before applying generated files.",
                false
            ),
            repositoryFact(
                "frontend-git-safe-directory",
                "F:/gitProject/vue-kube-manager/.git",
                "git status is blocked by Git dubious-ownership protection.",
                "Run git config --global --add safe.directory F:/gitProject/vue-kube-manager when the frontend repo is intentionally trusted.",
                true
            ),
            repositoryFact(
                "current-kube-agent-writable-root",
                "F:/gitProject/kube-agent",
                "This migration package is stored in the writable backend repository.",
                "Keep package, tests, docs, and recovery memory here until the frontend repo is writable.",
                false
            ),
            repositoryFact(
                "phase2-domain-scope",
                "NIM/HPC/Slurm/BCM",
                "Phase 2 domain plugins stay paused in this migration.",
                "Do not add Phase 2 routes or buttons while applying the Phase 1 workbench.",
                false
            ),
            repositoryFact(
                "permission-menu-exact-path-match",
                "src/store/modules/permission.js",
                "filterAsyncRoutesByMenu compares menus.some(menu => menu.path === route.path).",
                "Use absolute child paths and do not set parent /agent withPermission unless the menu API also returns /agent.",
                false
            )
        );
    }

    private static Map<String, Object> repositoryFact(String id,
                                                      String path,
                                                      String observedState,
                                                      String nextAction,
                                                      boolean requiresGitSafeDirectory) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", id);
        fact.put("path", path);
        fact.put("observedState", observedState);
        fact.put("nextAction", nextAction);
        fact.put("requiresGitSafeDirectory", requiresGitSafeDirectory);
        fact.put("runtimeControlAllowed", false);
        return Map.copyOf(fact);
    }

    private static List<Map<String, Object>> buildRoutePatches(
        AgentTopTierVueWorkbenchAcceptanceContractResponse contract
    ) {
        if (contract == null) {
            return List.of();
        }
        return contract.routeMountSpecs().stream()
            .map(AgentTopTierVueWorkbenchMigrationPackageResponse::routePatch)
            .toList();
    }

    private static Map<String, Object> routePatch(Map<String, Object> route) {
        String routePath = String.valueOf(route.get("routePath"));
        String componentPath = String.valueOf(route.get("componentPath"));
        String routeName = String.valueOf(route.get("routeName"));
        String metaTitle = String.valueOf(route.get("metaTitle"));
        Map<String, Object> patch = new LinkedHashMap<>();
        patch.put("id", route.get("routeId"));
        patch.put("targetFile", "src/router/index.js");
        patch.put("operation", "insert-child-route-under-agent-parent-in-asyncRoutes");
        patch.put("parentRoutePath", "/agent");
        patch.put("layout", "BackendLayout");
        patch.put("routeArray", "asyncRoutes");
        patch.put("permissionMenuPath", routePath);
        patch.put("parentWithPermission", false);
        patch.put("parentMenuPathRequired", false);
        patch.put("childPathMustBeAbsolute", true);
        patch.put("menuMatchingRule", "menus.some(menu => menu.path === route.path)");
        patch.put("routeName", routeName);
        patch.put("componentPath", componentPath);
        patch.put("routeSnippet", routeSnippet(routePath, componentPath, routeName, metaTitle));
        patch.put("readOnly", true);
        patch.put("runtimeControlAllowed", false);
        return Map.copyOf(patch);
    }

    private static String routeSnippet(String routePath,
                                       String componentPath,
                                       String routeName,
                                       String metaTitle) {
        return "{ path: '" + routePath + "', name: '" + routeName
            + "', component: () => import('" + componentPath
            + "'), meta: { title: '" + metaTitle
            + "', icon: 'el-icon-data-analysis', withPermission: true } }";
    }

    private static List<Map<String, Object>> buildFileBlueprints(
        AgentTopTierVueWorkbenchAcceptanceContractResponse contract
    ) {
        if (contract == null) {
            return List.of();
        }
        return List.of(
            fileBlueprint("api-client", "src/api/agent-observability.js", "create",
                "Export GET-only wrappers through @/utils/request.",
                apiModuleTemplate(contract)),
            fileBlueprint("router", "src/router/index.js", "update",
                "Add parent /agent if missing and mount all top-tier children in asyncRoutes.",
                "Use routePatches[*].routeSnippet under BackendLayout. Keep parent /agent without withPermission unless /agent is returned by /api/{organizationId}/permission/menu/my."),
            fileBlueprint("shared-components", "src/views/agent/top-tier/components", "create",
                "Create read-only Element UI renderers for status tags, tables, disabled actions, and source JSON.",
                "StatusTag.vue, ReadonlyMetricGrid.vue, ReadonlyTable.vue, DisabledActionList.vue, ReadonlyJsonPanel.vue"),
            fileBlueprint("technology-playbook-page",
                "src/views/agent/top-tier/technology-introduction-playbook/index.vue", "create",
                "Render technology introduction stages, lanes, gates, expert reviews, and learning modules.",
                pageBlueprint("fetchTechnologyIntroductionPlaybook", "technologyIntroductionPlaybook")),
            fileBlueprint("official-watch-page",
                "src/views/agent/top-tier/official-version-protocol-watch/index.vue", "create",
                "Render official watch binding spec, disabled actions, source cards, and safe source JSON.",
                pageBlueprint("fetchOfficialVersionProtocolWatchVueBindingSpec", "officialWatchBindingSpec")),
            fileBlueprint("compatibility-matrix-page",
                "src/views/agent/top-tier/advanced-technology-compatibility-matrix/index.vue", "create",
                "Render candidate lanes, migration gates, blocked shortcuts, and compatibility test lanes.",
                pageBlueprint("fetchAdvancedTechnologyCompatibilityMatrixVueBindingSpec", "compatibilityMatrixBindingSpec")),
            fileBlueprint("evidence-readiness-page",
                "src/views/agent/top-tier/advanced-technology-evidence-readiness/index.vue", "create",
                "Render reviewed-trace, Memory/RAG fixture, release gate, Vue, and Git review gaps.",
                pageBlueprint("fetchAdvancedTechnologyCompatibilityMatrixEvidenceReadiness", "evidenceReadiness")),
            fileBlueprint("backend-modernization-page",
                "src/views/agent/top-tier/backend-technology-modernization-decision/index.vue", "create",
                "Render Java/Spring mainline decision, compatibility lanes, gates, and learning path.",
                pageBlueprint("fetchBackendTechnologyModernizationDecision", "backendTechnologyModernizationDecision")),
            fileBlueprint("fixtures", "tests/unit/fixtures/agent-top-tier-workbench.js", "create",
                "Export mocked ApiResponse envelopes with success=true and data payloads for all pages.",
                "Include html-like strings as escaped data and assert no v-html rendering is needed."),
            fileBlueprint("tests", "tests/unit/views/agent/top-tier", "create",
                "Add Jest + Vue Test Utils specs for routing, API exports, pages, 401/403, and absent runtime controls.",
                "Use jest.mock('@/api/agent-observability') and Element UI selector assertions.")
        );
    }

    private static Map<String, Object> fileBlueprint(String id,
                                                     String targetPath,
                                                     String operation,
                                                     String purpose,
                                                     String templateOrRule) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("id", id);
        file.put("targetPath", targetPath);
        file.put("operation", operation);
        file.put("purpose", purpose);
        file.put("templateOrRule", templateOrRule);
        file.put("readOnly", true);
        file.put("runtimeControlAllowed", false);
        return Map.copyOf(file);
    }

    private static String pageBlueprint(String apiFunction, String fixtureKey) {
        return "created() loads " + apiFunction + "(), unwraps response.data, renders " + fixtureKey
            + " with Element UI tables/tags/alerts/empty states, and never uses v-html or editable textareas.";
    }

    private static String apiModuleTemplate(AgentTopTierVueWorkbenchAcceptanceContractResponse contract) {
        return "import request from '@/utils/request'\n\n" + contract.apiClientSpecs().stream()
            .map(client -> "export function " + client.get("functionName")
                + "(query) {\n  return request({ url: '" + client.get("endpoint")
                + "', method: 'get', params: query })\n}")
            .collect(Collectors.joining("\n\n"));
    }

    private static List<Map<String, Object>> buildApiClientExports(
        AgentTopTierVueWorkbenchAcceptanceContractResponse contract
    ) {
        if (contract == null) {
            return List.of();
        }
        return contract.apiClientSpecs().stream()
            .map(AgentTopTierVueWorkbenchMigrationPackageResponse::apiExport)
            .toList();
    }

    private static Map<String, Object> apiExport(Map<String, Object> client) {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("functionName", client.get("functionName"));
        export.put("endpoint", client.get("endpoint"));
        export.put("method", "get");
        export.put("apiFile", "src/api/agent-observability.js");
        export.put("unwrapExpression", "response.data");
        export.put("mockFixtureKey", client.get("fixtureKey"));
        export.put("mutatingMethodAllowed", false);
        export.put("runtimeBackendCallAllowed", false);
        return Map.copyOf(export);
    }

    private static List<Map<String, Object>> buildTestBlueprints(
        AgentTopTierVueWorkbenchAcceptanceContractResponse contract
    ) {
        if (contract == null) {
            return List.of();
        }
        return List.of(
            testBlueprint("api-client-get-only",
                "tests/unit/api/agent-observability.spec.js",
                "Assert all exported API functions use method=get and no mutating method exports exist."),
            testBlueprint("router-mounts",
                "tests/unit/router/agent-top-tier-routes.spec.js",
                "Assert asyncRoutes contains the /agent parent and all five child routes with stable PascalCase names."),
            testBlueprint("technology-playbook-page",
                "tests/unit/views/agent/top-tier/technology-introduction-playbook.spec.js",
                "Mount page with mocked playbook fixture and assert .el-table, .el-tag, .el-alert, and no forbidden selectors."),
            testBlueprint("official-watch-page",
                "tests/unit/views/agent/top-tier/official-version-protocol-watch.spec.js",
                "Mount page with mocked binding spec and assert disabled runtime actions render as text, never buttons."),
            testBlueprint("compatibility-matrix-page",
                "tests/unit/views/agent/top-tier/advanced-technology-compatibility-matrix.spec.js",
                "Mount page and assert candidate lanes, blocked shortcuts, and test lanes render from fixture."),
            testBlueprint("evidence-readiness-page",
                "tests/unit/views/agent/top-tier/advanced-technology-evidence-readiness.spec.js",
                "Mount page and assert reviewed-trace and Memory/RAG fixture blockers are visible."),
            testBlueprint("backend-decision-page",
                "tests/unit/views/agent/top-tier/backend-technology-modernization-decision.spec.js",
                "Mount page and assert Java/Spring mainline, compatibility lanes, and learning path render."),
            testBlueprint("auth-empty-states",
                "tests/unit/views/agent/top-tier/auth-empty-states.spec.js",
                "Mock 401 and 403 responses and assert el-alert or el-empty states without retry buttons."),
            testBlueprint("source-json-xss",
                "tests/unit/views/agent/top-tier/source-json-xss.spec.js",
                "Assert html-like fixture data is escaped, v-html is absent, and source JSON panels are read-only.")
        );
    }

    private static Map<String, Object> testBlueprint(String id, String targetPath, String assertion) {
        Map<String, Object> test = new LinkedHashMap<>();
        test.put("id", id);
        test.put("targetPath", targetPath);
        test.put("assertion", assertion);
        test.put("requiresMockedHttp", true);
        test.put("requiresRealBackend", false);
        test.put("requiresKubeManager8100", false);
        test.put("runtimeControlAllowed", false);
        return Map.copyOf(test);
    }

    private static List<Map<String, Object>> buildValidationChecks(
        AgentTopTierVueWorkbenchAcceptanceContractResponse contract
    ) {
        if (contract == null) {
            return List.of();
        }
        return List.of(
            validation("frontend-lint", "npm run lint",
                "Vue and JS lint after applying the migration package."),
            validation("frontend-unit", "npm run test:unit",
                "Jest coverage for API client, routes, pages, auth states, XSS, and absent controls."),
            validation("frontend-ci", "npm run test:ci",
                "Frontend lint plus unit tests."),
            validation("route-scan", "rg \"/agent/top-tier\" src/router/index.js",
                "Confirm the expected route paths are mounted."),
            validation("api-mutation-scan", "rg \"method: '(post|put|patch|delete)'\" src/api/agent-observability.js",
                "This command must return no matches for the Agent observability API client."),
            validation("runtime-selector-scan", "rg \"data-test='(mcp-tools-call|kube-manager-write|enable-rag-runtime)'\" src tests",
                "This command must return no matches except explicit negative-test fixture strings."),
            validation("git-whitespace", "git diff --check",
                "Confirm the applied frontend patch has no whitespace errors."),
            validation("backend-contract", "mvn -q -Dtest=AgentTopTierVueWorkbenchMigrationPackageServiceTest test",
                "Confirm the backend migration package remains coherent with the acceptance contract.")
        );
    }

    private static Map<String, Object> validation(String id, String command, String purpose) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("id", id);
        check.put("command", command);
        check.put("purpose", purpose);
        check.put("runtimeControlAllowed", false);
        return Map.copyOf(check);
    }

    private static List<Map<String, Object>> buildForbiddenRuntimeAssertions(
        AgentTopTierVueWorkbenchAcceptanceContractResponse contract
    ) {
        if (contract == null) {
            return List.of();
        }
        return contract.forbiddenRuntimeSelectors().stream()
            .map(AgentTopTierVueWorkbenchMigrationPackageResponse::forbiddenAssertion)
            .toList();
    }

    private static Map<String, Object> forbiddenAssertion(Map<String, Object> selector) {
        Map<String, Object> assertion = new LinkedHashMap<>();
        assertion.put("id", selector.get("id"));
        assertion.put("selector", selector.get("selector"));
        assertion.put("mustBeAbsent", true);
        assertion.put("apiExportAllowed", false);
        assertion.put("clickHandlerAllowed", false);
        assertion.put("testRule", "wrapper.find(\"" + selector.get("selector") + "\").exists() === false");
        assertion.put("runtimeControlAllowed", false);
        return Map.copyOf(assertion);
    }

    private static List<String> buildApplyOrder() {
        return List.of(
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
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("topTierVueWorkbenchMigrationPackage", MIGRATION_PACKAGE_ENDPOINT);
        endpoints.put("topTierVueWorkbenchAcceptanceContract",
            AgentTopTierVueWorkbenchAcceptanceContractResponse.ACCEPTANCE_CONTRACT_ENDPOINT);
        endpoints.put("topTierVueWorkbenchImplementationPackage",
            AgentTopTierVueWorkbenchImplementationPackageResponse.PACKAGE_ENDPOINT);
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildPackagePolicy(
        AgentTopTierVueWorkbenchAcceptanceContractResponse contract,
        List<Map<String, Object>> routes,
        List<Map<String, Object>> files,
        List<Map<String, Object>> apiExports,
        List<Map<String, Object>> tests,
        List<Map<String, Object>> checks,
        List<Map<String, Object>> forbiddenAssertions
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("migrationPackageOnly", true);
        policy.put("directFrontendWritePerformed", false);
        policy.put("frontendRepositoryWritableInCurrentWorkspace", false);
        policy.put("frontendRepositoryGitStatusBlockedByDubiousOwnership", true);
        policy.put("acceptanceContractStatus", contract != null ? contract.contractStatus() : "MISSING");
        policy.put("routePatchCount", routes.size());
        policy.put("fileBlueprintCount", files.size());
        policy.put("apiExportCount", apiExports.size());
        policy.put("testBlueprintCount", tests.size());
        policy.put("validationCheckCount", checks.size());
        policy.put("forbiddenRuntimeAssertionCount", forbiddenAssertions.size());
        policy.put("vue2ElementUiProfile", contract != null && contract.vue2ElementUiProfile());
        policy.put("mockedHttpRequired", true);
        policy.put("realBackendRequiredForAcceptance", false);
        policy.put("kubeManager8100Required", false);
        policy.put("runtimeButtonsAllowed", false);
        policy.put("dependencyUpgradeButtonsAllowed", false);
        policy.put("mutatingApiMethodsAllowed", false);
        policy.put("phase2NimHpcSlurmBcmPaused", true);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety(
        AgentTopTierVueWorkbenchAcceptanceContractResponse contract
    ) {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("migrationPackageOnly", true);
        safety.put("directFrontendWritePerformed", false);
        safety.put("runtimeMutationAllowed", false);
        safety.put("runtimeControlAllowed", false);
        safety.put("runtimeUpgradeAllowedNow", false);
        safety.put("dependencyUpgradeAllowedNow", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("kubeManagerCalls", false);
        safety.put("mcpToolsCall", false);
        safety.put("a2aRuntimeHandoff", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("memoryWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("vectorStoreCalls", false);
        safety.put("embeddingModelCalls", false);
        safety.put("rerankerCalls", false);
        safety.put("ciBlockingChanged", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        safety.put("acceptanceContractRuntimeControlAllowed", contract != null && contract.runtimeControlAllowed());
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(
        AgentTopTierVueWorkbenchAcceptanceContractResponse contract
    ) {
        Map<String, Object> contractPrivacy = contract != null ? contract.privacy() : Map.of();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", bool(contractPrivacy, "containsRawPrincipal"));
        privacy.put("containsRawPrompt", bool(contractPrivacy, "containsRawPrompt"));
        privacy.put("containsRawDocument", bool(contractPrivacy, "containsRawDocument"));
        privacy.put("containsAuthorizationHeader", bool(contractPrivacy, "containsAuthorizationHeader"));
        privacy.put("containsToken", bool(contractPrivacy, "containsToken"));
        privacy.put("containsPassword", bool(contractPrivacy, "containsPassword"));
        privacy.put("containsRuntimeSecrets", false);
        return Map.copyOf(privacy);
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
