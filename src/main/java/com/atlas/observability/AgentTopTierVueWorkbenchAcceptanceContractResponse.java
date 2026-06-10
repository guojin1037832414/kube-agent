package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Acceptance contract for implementing the top-tier Agent technology workbench in vue-kube-manager.
 *
 * <p>This contract is intentionally stricter than a route list. It describes the Vue 2 / Element UI
 * files, mocked fixtures, Jest assertions, and forbidden runtime controls that the frontend must
 * satisfy before the workbench can be treated as implemented.</p>
 */
public record AgentTopTierVueWorkbenchAcceptanceContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String frontendTarget,
    boolean sourceImplementationPackageEmbedded,
    boolean vue2ElementUiProfile,
    boolean fixtureOnly,
    boolean runtimeControlAllowed,
    int frontendStackFactCount,
    int routeMountSpecCount,
    int apiClientSpecCount,
    int pageFixtureSpecCount,
    int acceptanceScenarioCount,
    int forbiddenRuntimeSelectorCount,
    int implementationFileCount,
    int testCommandCount,
    List<Map<String, Object>> frontendStackFacts,
    List<Map<String, Object>> routeMountSpecs,
    List<Map<String, Object>> apiClientSpecs,
    List<Map<String, Object>> pageFixtureSpecs,
    List<Map<String, Object>> acceptanceScenarios,
    List<Map<String, Object>> forbiddenRuntimeSelectors,
    List<Map<String, Object>> implementationFiles,
    List<Map<String, Object>> testCommands,
    List<Map<String, Object>> teachingCheckpoints,
    AgentTopTierVueWorkbenchImplementationPackageResponse sourceImplementationPackage,
    Map<String, Object> endpointMap,
    Map<String, Object> contractPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-top-tier-vue-workbench-acceptance-contract.v1";
    public static final String ACCEPTANCE_CONTRACT_ENDPOINT =
        "/api/agent/observability/top-tier/vue-workbench-acceptance-contract";

    public static AgentTopTierVueWorkbenchAcceptanceContractResponse of(
        Instant generatedAt,
        AgentTopTierVueWorkbenchImplementationPackageResponse sourceImplementationPackage
    ) {
        List<Map<String, Object>> stackFacts = buildFrontendStackFacts();
        List<Map<String, Object>> routes = buildRouteMountSpecs();
        List<Map<String, Object>> apiClients = buildApiClientSpecs();
        List<Map<String, Object>> fixtures = buildPageFixtureSpecs();
        List<Map<String, Object>> scenarios = buildAcceptanceScenarios();
        List<Map<String, Object>> forbiddenSelectors = buildForbiddenRuntimeSelectors();
        List<Map<String, Object>> files = buildImplementationFiles();
        List<Map<String, Object>> commands = buildTestCommands();
        List<Map<String, Object>> teaching = buildTeachingCheckpoints();
        return new AgentTopTierVueWorkbenchAcceptanceContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            contractStatus(sourceImplementationPackage),
            "vue-kube-manager Vue 2 / Element UI top-tier Agent technology workbench",
            sourceImplementationPackage != null,
            true,
            true,
            false,
            stackFacts.size(),
            routes.size(),
            apiClients.size(),
            fixtures.size(),
            scenarios.size(),
            forbiddenSelectors.size(),
            files.size(),
            commands.size(),
            stackFacts,
            routes,
            apiClients,
            fixtures,
            scenarios,
            forbiddenSelectors,
            files,
            commands,
            teaching,
            sourceImplementationPackage,
            buildEndpointMap(),
            buildContractPolicy(routes, apiClients, fixtures, scenarios, forbiddenSelectors, files, commands),
            buildSafety(sourceImplementationPackage),
            buildPrivacy(sourceImplementationPackage)
        );
    }

    private static String contractStatus(
        AgentTopTierVueWorkbenchImplementationPackageResponse sourceImplementationPackage
    ) {
        if (sourceImplementationPackage == null) {
            return "ACCEPTANCE_CONTRACT_SOURCE_PACKAGE_MISSING";
        }
        if (!"IMPLEMENTATION_PACKAGE_READY".equals(sourceImplementationPackage.packageStatus())) {
            return "ACCEPTANCE_CONTRACT_BLOCKED_BY_SOURCE_PACKAGE";
        }
        if (sourceImplementationPackage.runtimeControlAllowed()
            || Boolean.TRUE.equals(sourceImplementationPackage.safety().get("runtimeControlAllowed"))
            || unsafeSourceCapabilityEnabled(sourceImplementationPackage.safety())) {
            return "UNEXPECTED_RUNTIME_CONTROL_IN_SOURCE_PACKAGE";
        }
        return "ACCEPTANCE_CONTRACT_READY_FOR_VUE2_ELEMENT_UI_IMPLEMENTATION";
    }

    private static boolean unsafeSourceCapabilityEnabled(Map<String, Object> safety) {
        return List.of(
            "toolExecution",
            "safeToolExecutorInvocation",
            "hitlInvocation",
            "kubeManagerCalls",
            "mcpToolsCall",
            "a2aRuntimeHandoff",
            "externalCalls",
            "auditWrite",
            "memoryWrite",
            "retrievalExecuted",
            "vectorStoreCalls",
            "embeddingModelCalls",
            "rerankerCalls",
            "ciBlockingChanged",
            "phase2NimHpcSlurmBcmTouched"
        ).stream().anyMatch(key -> Boolean.TRUE.equals(safety.get(key)));
    }

    private static List<Map<String, Object>> buildFrontendStackFacts() {
        return List.of(
            stackFact("vue-runtime", "vue", "2.6.x",
                "^2.6.11",
                "Use options-api single-file components under src/views; do not introduce Vue 3 composition-only code.",
                List.of("vue@3", "composition-only pages")),
            stackFact("router", "vue-router", "3.0.x",
                "3.0.6",
                "Mount routes in src/router/index.js using asyncRoutes, BackendLayout, and history mode.",
                List.of("vue-router@4")),
            stackFact("ui-kit", "element-ui", "2.15.x",
                "^2.15.14",
                "Use el-card, el-table, el-tabs, el-tag, el-alert, el-timeline, and el-empty for dense operator views.",
                List.of("element-plus")),
            stackFact("http-client", "axios via src/utils/request.js", "0.18.x wrapper",
                "axios@0.18.1",
                "Add read-only functions in src/api/agent-observability.js; preserve X-Token and Accept-Language interceptors.",
                List.of("fetch", "axios.create", "@/utils/request-nim")),
            stackFact("unit-test", "vue-cli-service test:unit + Jest", "Vue CLI 4",
                "vue-cli-service test:unit with tests/unit/**/*.spec.js",
                "Add tests under tests/unit for mocked API clients and absent runtime controls.",
                List.of("vitest", "cypress-only acceptance")),
            stackFact("lint", "eslint --ext .js,.vue src", "project script",
                "npm run lint",
                "Keep the workbench in the existing lint path; do not add a new frontend toolchain.",
                List.of("vite migration", "eslint flat-config migration"))
        );
    }

    private static Map<String, Object> stackFact(String id,
                                                 String technology,
                                                 String observedVersion,
                                                 String exactObservedVersion,
                                                 String rule,
                                                 List<String> forbiddenAdditions) {
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("id", id);
        fact.put("technology", technology);
        fact.put("observedVersion", observedVersion);
        fact.put("requiredVersionRange", observedVersion);
        fact.put("exactObservedVersion", exactObservedVersion);
        fact.put("evidenceSource", sourcePathForFact(id));
        fact.put("verifiedAt", "2026-06-10");
        fact.put("packageJsonPath", "F:/gitProject/vue-kube-manager/package.json");
        fact.put("sourcePath", sourcePathForFact(id));
        fact.put("implementationRule", rule);
        fact.put("forbiddenAdditions", List.copyOf(forbiddenAdditions));
        fact.put("runtimeControlAllowed", false);
        return Map.copyOf(fact);
    }

    private static String sourcePathForFact(String id) {
        return switch (id) {
            case "router" -> "F:/gitProject/vue-kube-manager/src/router/index.js";
            case "http-client" -> "F:/gitProject/vue-kube-manager/src/utils/request.js";
            case "unit-test" -> "F:/gitProject/vue-kube-manager/jest.config.js";
            default -> "F:/gitProject/vue-kube-manager/package.json";
        };
    }

    private static List<Map<String, Object>> buildRouteMountSpecs() {
        return List.of(
            route("top-tier-technology-introduction-playbook",
                "/agent/top-tier/technology-introduction-playbook",
                "@/views/agent/top-tier/technology-introduction-playbook/index.vue",
                "Top-tier Agent Playbook",
                AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT,
                1),
            route("top-tier-official-version-protocol-watch",
                "/agent/top-tier/official-version-protocol-watch",
                "@/views/agent/top-tier/official-version-protocol-watch/index.vue",
                "Official Technology Watch",
                AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                2),
            route("top-tier-advanced-technology-compatibility-matrix",
                "/agent/top-tier/advanced-technology-compatibility-matrix",
                "@/views/agent/top-tier/advanced-technology-compatibility-matrix/index.vue",
                "Compatibility Matrix",
                AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                3),
            route("top-tier-advanced-technology-evidence-readiness",
                "/agent/top-tier/advanced-technology-evidence-readiness",
                "@/views/agent/top-tier/advanced-technology-evidence-readiness/index.vue",
                "Evidence Readiness",
                AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT,
                4),
            route("top-tier-backend-technology-modernization-decision",
                "/agent/top-tier/backend-technology-modernization-decision",
                "@/views/agent/top-tier/backend-technology-modernization-decision/index.vue",
                "Backend Modernization Decision",
                AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT,
                5)
        );
    }

    private static Map<String, Object> route(String routeId,
                                             String routePath,
                                             String componentPath,
                                             String title,
                                             String sourceEndpoint,
                                             int sidebarOrder) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("routeId", routeId);
        route.put("routePath", routePath);
        route.put("componentPath", componentPath);
        route.put("routerFile", "src/router/index.js");
        route.put("routeArray", "asyncRoutes");
        route.put("parentRoutePath", "/agent");
        route.put("parentRedirect", "/agent/top-tier/technology-introduction-playbook");
        route.put("layout", "BackendLayout");
        route.put("routeName", pascalCase(routeId));
        route.put("routeNamePattern", "^[A-Za-z][A-Za-z0-9]*$");
        route.put("componentImport", componentPath);
        route.put("hidden", false);
        route.put("alwaysShow", false);
        route.put("sidebarOrder", sidebarOrder);
        route.put("metaTitle", title);
        route.put("metaIcon", "el-icon-data-analysis");
        route.put("withPermission", true);
        route.put("permissionMenuPath", routePath);
        route.put("permissionMenuEndpoint", "/api/{organizationId}/permission/menu/my");
        route.put("sourceEndpoint", sourceEndpoint);
        route.put("readOnly", true);
        route.put("runtimeControlAllowed", false);
        return Map.copyOf(route);
    }

    private static String pascalCase(String routeId) {
        return java.util.Arrays.stream(routeId.split("-"))
            .filter(part -> !part.isBlank())
            .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
            .reduce("", String::concat);
    }

    private static List<Map<String, Object>> buildApiClientSpecs() {
        return List.of(
            apiClient("fetchTopTierVueWorkbenchAcceptanceContract", ACCEPTANCE_CONTRACT_ENDPOINT,
                "acceptanceContract"),
            apiClient("fetchTopTierVueWorkbenchImplementationPackage",
                AgentTopTierVueWorkbenchImplementationPackageResponse.PACKAGE_ENDPOINT,
                "implementationPackage"),
            apiClient("fetchTechnologyIntroductionPlaybook",
                AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT,
                "technologyIntroductionPlaybook"),
            apiClient("fetchOfficialVersionProtocolWatchVueBindingSpec",
                AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                "officialWatchBindingSpec"),
            apiClient("fetchAdvancedTechnologyCompatibilityMatrixVueBindingSpec",
                AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                "compatibilityMatrixBindingSpec"),
            apiClient("fetchAdvancedTechnologyCompatibilityMatrixEvidenceReadiness",
                AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT,
                "evidenceReadiness"),
            apiClient("fetchBackendTechnologyModernizationDecision",
                AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT,
                "backendTechnologyModernizationDecision"),
            apiClient("fetchVueReadinessControlPlane",
                "/api/agent/observability/top-tier/vue-readiness-control-plane",
                "vueReadinessControlPlane")
        );
    }

    private static Map<String, Object> apiClient(String functionName, String endpoint, String fixtureKey) {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("apiFile", "src/api/agent-observability.js");
        client.put("functionName", functionName);
        client.put("method", "get");
        client.put("endpoint", endpoint);
        client.put("fixtureKey", fixtureKey);
        client.put("importPath", "@/utils/request");
        client.put("requestObjectShape", Map.of(
            "url", endpoint,
            "method", "get",
            "params", "query"
        ));
        client.put("responseEnvelope", Map.of(
            "successField", "success",
            "payloadField", "data",
            "legacyResultFieldRequired", false
        ));
        client.put("viewUnwrapExpression", "response.data");
        client.put("forbiddenClients", List.of("fetch", "axios.create", "@/utils/request-nim"));
        client.put("forbiddenApiMethods", List.of("post", "put", "patch", "delete"));
        client.put("usesSharedRequestWrapper", true);
        client.put("requiresAdminSession", true);
        client.put("acceptanceRequiresMockedHttp", true);
        client.put("productionReadModelCallAllowed", true);
        client.put("mutatingBackendCallAllowed", false);
        client.put("readOnly", true);
        client.put("runtimeBackendCallAllowed", false);
        client.put("kubeManager8100Required", false);
        return Map.copyOf(client);
    }

    private static List<Map<String, Object>> buildPageFixtureSpecs() {
        return List.of(
            fixture("technology-introduction-playbook-fixture", "technologyIntroductionPlaybook",
                AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT,
                List.of("schemaVersion", "playbookStatus", "technologyLanePlaybookRows", "releaseGateRows")),
            fixture("official-watch-fixture", "officialWatchBindingSpec",
                AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                List.of("schemaVersion", "componentSpecs", "fieldBindings", "disabledActionBindings")),
            fixture("compatibility-matrix-fixture", "compatibilityMatrixBindingSpec",
                AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                List.of("schemaVersion", "componentSpecs", "tableColumnGroups", "disabledActionBindings")),
            fixture("evidence-readiness-fixture", "evidenceReadiness",
                AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT,
                List.of("schemaVersion", "matrixEvidenceRows", "blockingGateRows", "disabledRuntimeActions")),
            fixture("backend-modernization-decision-fixture", "backendTechnologyModernizationDecision",
                AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT,
                List.of("schemaVersion", "mainlineDecisions", "compatibilityLaneDecisions", "learningPath"))
        );
    }

    private static Map<String, Object> fixture(String id,
                                               String fixtureKey,
                                               String sourceEndpoint,
                                               List<String> requiredFields) {
        Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("id", id);
        fixture.put("fixtureFile", "tests/unit/fixtures/agent-top-tier-workbench.js");
        fixture.put("fixtureKey", fixtureKey);
        fixture.put("sourceEndpoint", sourceEndpoint);
        fixture.put("requiredFields", List.copyOf(requiredFields));
        fixture.put("fixtureExportStyle", "named ES module fixture object");
        fixture.put("mockStrategy", "jest.mock('@/api/agent-observability')");
        fixture.put("responseEnvelopeExample", Map.of(
            "success", true,
            "data", fixtureKey
        ));
        fixture.put("httpStatusVariants", List.of(200, 401, 403));
        fixture.put("mockedHttpOnly", true);
        fixture.put("runtimeBackendCallAllowed", false);
        fixture.put("runtimeControlAllowed", false);
        return Map.copyOf(fixture);
    }

    private static List<Map<String, Object>> buildAcceptanceScenarios() {
        return List.of(
            scenario("router-mounts-five-workbench-pages",
                "Assert src/router/index.js exposes all five /agent/top-tier routes under asyncRoutes, BackendLayout, and permission menu paths."),
            scenario("api-client-uses-read-only-get-methods",
                "Assert src/api/agent-observability.js exports GET functions only, uses @/utils/request, and unwraps ApiResponse.data."),
            scenario("technology-playbook-renders-stages-lanes-and-gates",
                "Mount the playbook page with mocked fixture and assert stage, lane, release gate, expert review, and learning sections."),
            scenario("official-watch-renders-backend-binding-spec",
                "Mount the official watch page and assert component specs, disabled actions, and source JSON render from fixture."),
            scenario("compatibility-matrix-renders-backend-binding-spec",
                "Mount the compatibility page and assert candidate lanes, migration gates, blocked shortcuts, and test lanes."),
            scenario("evidence-readiness-renders-blockers",
                "Mount the evidence page and assert reviewed trace and Memory/RAG fixture gaps are visible."),
            scenario("backend-decision-renders-learning-path",
                "Mount the backend decision page and assert Java/Spring mainline, compatibility lanes, and learning path are visible."),
            scenario("cross-page-navigation-keeps-read-only-state",
                "Switch between routes while proving dangerous DOM selectors and API exports are absent."),
            scenario("admin-auth-errors-render-readonly-empty-state",
                "Mock 401/403 responses and assert Element UI empty or alert states without retrying privileged actions."),
            scenario("source-json-panels-are-redacted-and-not-editable",
                "Assert source JSON drilldowns do not expose token, password, Authorization, raw prompt, or editable textarea.")
        );
    }

    private static Map<String, Object> scenario(String id, String assertion) {
        Map<String, Object> scenario = new LinkedHashMap<>();
        scenario.put("id", id);
        scenario.put("assertion", assertion);
        scenario.put("testLocation", "tests/unit/views/agent/top-tier");
        scenario.put("requiresMockedHttp", true);
        scenario.put("requiresRealBackend", false);
        scenario.put("requiresKubeManager8100", false);
        scenario.put("forbiddenSelectorMatrixRequired", true);
        scenario.put("apiMutationScanRequired", true);
        scenario.put("runtimeControlAllowed", false);
        return Map.copyOf(scenario);
    }

    private static List<Map<String, Object>> buildForbiddenRuntimeSelectors() {
        return List.of(
            forbiddenSelector("upgrade-backend-dependencies-button", "[data-test='upgrade-backend-dependencies']"),
            forbiddenSelector("spring-boot-4-upgrade-button", "[data-test='upgrade-spring-boot-4']"),
            forbiddenSelector("spring-ai-2-upgrade-button", "[data-test='upgrade-spring-ai-2']"),
            forbiddenSelector("mcp-tools-call-button", "[data-test='mcp-tools-call']"),
            forbiddenSelector("a2a-runtime-handoff-button", "[data-test='a2a-runtime-handoff']"),
            forbiddenSelector("enable-rag-runtime-button", "[data-test='enable-rag-runtime']"),
            forbiddenSelector("enable-graphrag-button", "[data-test='enable-graphrag']"),
            forbiddenSelector("enable-ci-blocking-button", "[data-test='enable-ci-blocking']"),
            forbiddenSelector("kube-manager-write-button", "[data-test='kube-manager-write']"),
            forbiddenSelector("issue-durable-receipt-button", "[data-test='issue-durable-receipt']"),
            forbiddenSelector("invoke-hitl-button", "[data-test='invoke-hitl']"),
            forbiddenSelector("reopen-phase2-button", "[data-test='reopen-nim-hpc-slurm-bcm']")
        );
    }

    private static Map<String, Object> forbiddenSelector(String id, String selector) {
        Map<String, Object> forbidden = new LinkedHashMap<>();
        forbidden.put("id", id);
        forbidden.put("selector", selector);
        forbidden.put("mustBeAbsent", true);
        forbidden.put("clickHandlerAllowed", false);
        forbidden.put("apiExportAllowed", false);
        forbidden.put("assertions", List.of(
            "wrapper.find(\"" + selector + "\").exists() === false",
            "Object.keys(api).every(name => !name.toLowerCase().includes('post'))"
        ));
        forbidden.put("teachingReason",
            "A top-tier Agent workbench proves authority is absent before it later proves authority is safe.");
        forbidden.put("requiresSeparateReviewedSlice", true);
        return Map.copyOf(forbidden);
    }

    private static List<Map<String, Object>> buildImplementationFiles() {
        return List.of(
            implementationFile("api-client", "src/api/agent-observability.js", "read-only GET API functions"),
            implementationFile("router", "src/router/index.js", "five BackendLayout child routes"),
            implementationFile("shared-components", "src/views/agent/top-tier/components", "read-only cards, tables, JSON panel, and disabled-action list"),
            implementationFile("playbook-page", "src/views/agent/top-tier/technology-introduction-playbook/index.vue", "playbook page"),
            implementationFile("official-watch-page", "src/views/agent/top-tier/official-version-protocol-watch/index.vue", "official watch page"),
            implementationFile("compatibility-page", "src/views/agent/top-tier/advanced-technology-compatibility-matrix/index.vue", "compatibility matrix page"),
            implementationFile("evidence-page", "src/views/agent/top-tier/advanced-technology-evidence-readiness/index.vue", "evidence readiness page"),
            implementationFile("backend-decision-page", "src/views/agent/top-tier/backend-technology-modernization-decision/index.vue", "backend modernization decision page"),
            implementationFile("fixture", "tests/unit/fixtures/agent-top-tier-workbench.js", "mocked backend responses"),
            implementationFile("view-tests", "tests/unit/views/agent/top-tier", "Jest + Vue Test Utils acceptance tests")
        );
    }

    private static Map<String, Object> implementationFile(String id, String path, String purpose) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("id", id);
        file.put("path", path);
        file.put("purpose", purpose);
        file.put("writeScope", "vue-kube-manager");
        file.put("ownerScope", "src/views/agent/top-tier");
        file.put("registeredInPages", List.of(
            "technology-introduction-playbook",
            "official-version-protocol-watch",
            "advanced-technology-compatibility-matrix",
            "advanced-technology-evidence-readiness",
            "backend-technology-modernization-decision"
        ));
        file.put("readOnlyContract", true);
        file.put("runtimeControlAllowed", false);
        return Map.copyOf(file);
    }

    private static List<Map<String, Object>> buildTestCommands() {
        return List.of(
            command("lint", "npm run lint", "Static Vue and JS linting."),
            command("unit", "npm run test:unit", "Jest unit tests for API clients and page rendering."),
            command("ci", "npm run test:ci", "Frontend lint plus unit tests.")
        );
    }

    private static Map<String, Object> command(String id, String command, String purpose) {
        Map<String, Object> testCommand = new LinkedHashMap<>();
        testCommand.put("id", id);
        testCommand.put("command", command);
        testCommand.put("purpose", purpose);
        testCommand.put("requiresRealBackend", false);
        testCommand.put("requiresKubeManager8100", false);
        testCommand.put("runtimeControlAllowed", false);
        return Map.copyOf(testCommand);
    }

    private static List<Map<String, Object>> buildTeachingCheckpoints() {
        return List.of(
            teaching("backend-owned-ui-contracts", "Learn why the backend publishes route, API, fixture, and acceptance rules."),
            teaching("vue2-element-ui-implementation", "Learn how older Vue 2 admin systems can still host top-tier Agent governance views."),
            teaching("mock-first-frontends", "Learn why mocked fixtures are required before runtime buttons or live writes exist."),
            teaching("absence-as-a-test", "Learn to test that dangerous controls are absent, not merely disabled."),
            teaching("operator-learning-workbench", "Learn how frontend pages can teach source evidence, gates, and blockers.")
        );
    }

    private static Map<String, Object> teaching(String id, String lesson) {
        Map<String, Object> checkpoint = new LinkedHashMap<>();
        checkpoint.put("id", id);
        checkpoint.put("lesson", lesson);
        checkpoint.put("artifact", "contract-plus-fixture-plus-jest-assertion");
        checkpoint.put("runtimeControlAllowed", false);
        return Map.copyOf(checkpoint);
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("topTierVueWorkbenchAcceptanceContract", ACCEPTANCE_CONTRACT_ENDPOINT);
        endpoints.put("topTierVueWorkbenchImplementationPackage",
            AgentTopTierVueWorkbenchImplementationPackageResponse.PACKAGE_ENDPOINT);
        endpoints.put("topTierTechnologyIntroductionPlaybook",
            AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchVueBindingSpec",
            AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixVueBindingSpec",
            AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
            AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT);
        endpoints.put("backendTechnologyModernizationDecision",
            AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT);
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildContractPolicy(List<Map<String, Object>> routes,
                                                           List<Map<String, Object>> apiClients,
                                                           List<Map<String, Object>> fixtures,
                                                           List<Map<String, Object>> scenarios,
                                                           List<Map<String, Object>> forbiddenSelectors,
                                                           List<Map<String, Object>> files,
                                                           List<Map<String, Object>> commands) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("acceptanceContractOnly", true);
        policy.put("vueWorkbenchOnly", true);
        policy.put("frontendRepository", "F:/gitProject/vue-kube-manager");
        policy.put("frontendStackLockedToObservedLegacyRuntime", true);
        policy.put("governanceAlignment", Map.of(
            "openAiAgentsPrimitivesRenderedAsEvidenceOnly", true,
            "mcpAuthorizationTokenFlowNotStarted", true,
            "otelGenAiSpansNotEmittedByThisEndpoint", true,
            "owaspLlmTop10MappedToFrontendLearningChecks", true,
            "agentLoopNotStarted", true
        ));
        policy.put("xssAndEvidenceRenderingRules", Map.of(
            "vHtmlAllowed", false,
            "jsonPanelReadonly", true,
            "backendStringsEscapedByVue", true,
            "fixtureIncludesHtmlLikePayload", true,
            "editableTextareaAllowed", false
        ));
        policy.put("forbiddenFrontendAdditions", List.of(
            "vue@3",
            "vue-router@4",
            "element-plus",
            "vite",
            "pinia",
            "axios.create"
        ));
        policy.put("routeMountShape", Map.of(
            "routeArray", "asyncRoutes",
            "parentRoutePath", "/agent",
            "layout", "BackendLayout",
            "mode", "history"
        ));
        policy.put("permissionMenuFixture", Map.of(
            "endpoint", "/api/{organizationId}/permission/menu/my",
            "matchingRule", "menus.some(menu => menu.path === route.path)",
            "requiredMenuPaths", routes.stream().map(route -> route.get("routePath")).toList()
        ));
        policy.put("responseEnvelope", Map.of(
            "successField", "success",
            "payloadField", "data",
            "viewUnwrapExpression", "response.data"
        ));
        policy.put("expectedElementUiSelectors", List.of(
            ".el-table",
            ".el-tag",
            ".el-alert",
            ".el-empty",
            ".el-tabs"
        ));
        policy.put("forbiddenApiMethods", List.of("post", "put", "patch", "delete"));
        policy.put("forbiddenRequestUrls", List.of(
            "/tools/call",
            "/api/agent/mcp/tools/call",
            "/api/agent/a2a/handoff",
            "/api/agent/rag/runtime",
            "/api/agent/graphrag/runtime",
            "/api/agent/ci/blocking",
            "/api/agent/hitl/invoke",
            "/api/kube-manager/write"
        ));
        policy.put("forbiddenTextPatterns", List.of("Upgrade", "Enable", "Run", "Write", "tools/call"));
        policy.put("routeMountSpecCount", routes.size());
        policy.put("apiClientSpecCount", apiClients.size());
        policy.put("pageFixtureSpecCount", fixtures.size());
        policy.put("acceptanceScenarioCount", scenarios.size());
        policy.put("forbiddenRuntimeSelectorCount", forbiddenSelectors.size());
        policy.put("implementationFileCount", files.size());
        policy.put("testCommandCount", commands.size());
        policy.put("mockedHttpRequired", true);
        policy.put("realBackendRequiredForAcceptance", false);
        policy.put("kubeManager8100Required", false);
        policy.put("runtimeButtonsAllowed", false);
        policy.put("dependencyUpgradeButtonsAllowed", false);
        policy.put("inlineEditAllowed", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety(
        AgentTopTierVueWorkbenchImplementationPackageResponse sourceImplementationPackage
    ) {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("acceptanceContractOnly", true);
        safety.put("sourceImplementationPackageReadOnly", sourceImplementationPackage != null
            && Boolean.TRUE.equals(sourceImplementationPackage.safety().get("readOnly")));
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
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(
        AgentTopTierVueWorkbenchImplementationPackageResponse sourceImplementationPackage
    ) {
        Map<String, Object> sourcePrivacy = sourceImplementationPackage != null
            ? sourceImplementationPackage.privacy()
            : Map.of();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", bool(sourcePrivacy, "containsRawPrincipal"));
        privacy.put("containsRawPrompt", bool(sourcePrivacy, "containsRawPrompt"));
        privacy.put("containsRawDocument", bool(sourcePrivacy, "containsRawDocument"));
        privacy.put("containsAuthorizationHeader", bool(sourcePrivacy, "containsAuthorizationHeader"));
        privacy.put("containsToken", bool(sourcePrivacy, "containsToken"));
        privacy.put("containsPassword", bool(sourcePrivacy, "containsPassword"));
        privacy.put("containsRuntimeSecrets", false);
        return Map.copyOf(privacy);
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
