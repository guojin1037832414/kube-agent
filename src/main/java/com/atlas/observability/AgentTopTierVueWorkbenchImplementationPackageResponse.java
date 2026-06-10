package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only implementation package for the Phase 1 top-tier Vue workbench.
 *
 * <p>It composes the official-source watch binding spec and the compatibility-matrix binding spec
 * into route, API-client, component, fixture, and acceptance-test guidance for vue-kube-manager.
 * The package is a frontend implementation contract only; it does not add runtime controls.</p>
 */
public record AgentTopTierVueWorkbenchImplementationPackageResponse(
    String schemaVersion,
    Instant generatedAt,
    String packageStatus,
    String frontendTarget,
    boolean sourceBindingSpecsEmbedded,
    boolean runtimeControlAllowed,
    int routeSpecCount,
    int apiClientBindingCount,
    int pageAssemblyCount,
    int sharedComponentCount,
    int acceptanceFixtureCount,
    List<Map<String, Object>> routeSpecs,
    List<Map<String, Object>> apiClientBindings,
    List<Map<String, Object>> pageAssemblies,
    List<Map<String, Object>> sharedComponentContracts,
    List<Map<String, Object>> acceptanceFixtures,
    List<Map<String, Object>> forbiddenRuntimeControls,
    List<String> implementationOrder,
    AgentOfficialVersionProtocolWatchVueBindingSpecResponse officialWatchBindingSpec,
    AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse compatibilityMatrixBindingSpec,
    Map<String, Object> endpointMap,
    Map<String, Object> packagePolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-top-tier-vue-workbench-implementation-package.v1";
    public static final String PACKAGE_ENDPOINT =
        "/api/agent/observability/top-tier/vue-workbench-implementation-package";

    public static AgentTopTierVueWorkbenchImplementationPackageResponse of(
        Instant generatedAt,
        AgentOfficialVersionProtocolWatchVueBindingSpecResponse officialWatchBindingSpec,
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse compatibilityMatrixBindingSpec
    ) {
        List<Map<String, Object>> routes = buildRouteSpecs();
        List<Map<String, Object>> apiClients = buildApiClientBindings();
        List<Map<String, Object>> assemblies = buildPageAssemblies();
        List<Map<String, Object>> sharedComponents = buildSharedComponentContracts();
        List<Map<String, Object>> fixtures = buildAcceptanceFixtures();
        List<Map<String, Object>> forbiddenControls =
            buildForbiddenRuntimeControls(officialWatchBindingSpec, compatibilityMatrixBindingSpec);
        return new AgentTopTierVueWorkbenchImplementationPackageResponse(
            SCHEMA_VERSION,
            generatedAt,
            packageStatus(officialWatchBindingSpec, compatibilityMatrixBindingSpec),
            "vue-kube-manager Phase 1 top-tier Agent latest-technology workbench",
            officialWatchBindingSpec != null && compatibilityMatrixBindingSpec != null,
            false,
            routes.size(),
            apiClients.size(),
            assemblies.size(),
            sharedComponents.size(),
            fixtures.size(),
            routes,
            apiClients,
            assemblies,
            sharedComponents,
            fixtures,
            forbiddenControls,
            buildImplementationOrder(),
            officialWatchBindingSpec,
            compatibilityMatrixBindingSpec,
            buildEndpointMap(),
            buildPackagePolicy(routes, apiClients, assemblies, sharedComponents, fixtures, forbiddenControls),
            buildSafety(officialWatchBindingSpec, compatibilityMatrixBindingSpec),
            buildPrivacy(officialWatchBindingSpec, compatibilityMatrixBindingSpec)
        );
    }

    private static String packageStatus(
        AgentOfficialVersionProtocolWatchVueBindingSpecResponse officialWatchBindingSpec,
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse compatibilityMatrixBindingSpec
    ) {
        if (officialWatchBindingSpec == null || compatibilityMatrixBindingSpec == null) {
            return "IMPLEMENTATION_PACKAGE_SOURCE_BINDING_MISSING";
        }
        if (!"VUE_BINDING_SPEC_READY".equals(officialWatchBindingSpec.bindingStatus())
            || !"VUE_BINDING_SPEC_READY".equals(compatibilityMatrixBindingSpec.bindingStatus())) {
            return "IMPLEMENTATION_PACKAGE_BLOCKED_BY_SOURCE_BINDING";
        }
        if (officialWatchBindingSpec.runtimeControlAllowed()
            || compatibilityMatrixBindingSpec.runtimeControlAllowed()
            || Boolean.TRUE.equals(officialWatchBindingSpec.safety().get("toolExecution"))
            || Boolean.TRUE.equals(compatibilityMatrixBindingSpec.safety().get("toolExecution"))) {
            return "UNEXPECTED_RUNTIME_CONTROL_IN_SOURCE_BINDING";
        }
        return "IMPLEMENTATION_PACKAGE_READY";
    }

    private static List<Map<String, Object>> buildRouteSpecs() {
        return List.of(
            routeSpec(
                "top-tier-technology-introduction-playbook",
                "/agent/top-tier/technology-introduction-playbook",
                "Top-tier technology introduction playbook",
                AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT,
                List.of("TechnologyPlaybookSummaryStrip", "TechnologyIntroductionStageTimeline",
                    "TechnologyLanePlaybookTable", "ReleaseGateChecklist",
                    "ExpertReviewRoundPanel", "TechnologyPlaybookSourceJsonPanel")
            ),
            routeSpec(
                "top-tier-official-version-protocol-watch",
                "/agent/top-tier/official-version-protocol-watch",
                "Official Agent technology watch",
                AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                List.of("OfficialWatchSummaryStrip", "OfficialSourceCardGrid",
                    "TechnologyTrackMatrix", "DisabledRuntimeActionList", "OfficialWatchSourceJsonPanel")
            ),
            routeSpec(
                "top-tier-advanced-technology-compatibility-matrix",
                "/agent/top-tier/advanced-technology-compatibility-matrix",
                "Advanced Agent technology compatibility matrix",
                AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                List.of("AdvancedTechnologyMatrixSummaryStrip", "CandidateUpgradeLaneMatrix",
                    "MigrationGateChecklist", "BlockedUpgradeShortcutTable",
                    "CompatibilityTestLaneBoard", "CompatibilityMatrixSourceJsonPanel")
            ),
            routeSpec(
                "top-tier-advanced-technology-evidence-readiness",
                "/agent/top-tier/advanced-technology-evidence-readiness",
                "Advanced Agent technology evidence readiness",
                AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT,
                List.of("EvidenceReadinessSummaryStrip", "TechnologyEvidenceGapTable",
                    "EvidenceGateChecklist", "DisabledRuntimeActionList", "EvidenceReadinessSourceJsonPanel")
            ),
            routeSpec(
                "top-tier-backend-technology-modernization-decision",
                "/agent/top-tier/backend-technology-modernization-decision",
                "Backend technology modernization decision",
                AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT,
                List.of("BackendDecisionSummaryStrip", "MainlineDecisionTable",
                    "CompatibilityLaneDecisionTable", "ModernizationGateChecklist",
                    "AgentLearningPathTimeline", "BackendDecisionSourceJsonPanel")
            )
        );
    }

    private static Map<String, Object> routeSpec(String id,
                                                 String routePath,
                                                 String title,
                                                 String sourceBindingSpecEndpoint,
                                                 List<String> requiredComponents) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("id", id);
        route.put("routePath", routePath);
        route.put("title", title);
        route.put("sourceBindingSpecEndpoint", sourceBindingSpecEndpoint);
        route.put("requiredComponents", List.copyOf(requiredComponents));
        route.put("requiresAdminSession", true);
        route.put("readOnly", true);
        route.put("runtimeControlAllowed", false);
        route.put("phase2ScopeVisibleAsPausedOnly", true);
        return Map.copyOf(route);
    }

    private static List<Map<String, Object>> buildApiClientBindings() {
        return List.of(
            apiClient("fetchOfficialWatchDashboard", "GET",
                AgentOfficialVersionProtocolWatchDashboardResponse.DASHBOARD_ENDPOINT,
                "sourceDashboard"),
            apiClient("fetchOfficialWatchBindingSpec", "GET",
                AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                "officialWatchBindingSpec"),
            apiClient("fetchTechnologyIntroductionPlaybook", "GET",
                AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT,
                "technologyIntroductionPlaybook"),
            apiClient("fetchCompatibilityMatrix", "GET",
                AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT,
                "sourceMatrix"),
            apiClient("fetchCompatibilityMatrixBindingSpec", "GET",
                AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                "compatibilityMatrixBindingSpec"),
            apiClient("fetchCompatibilityMatrixEvidenceReadiness", "GET",
                AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT,
                "compatibilityMatrixEvidenceReadiness"),
            apiClient("fetchBackendTechnologyModernizationDecision", "GET",
                AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT,
                "backendTechnologyModernizationDecision")
        );
    }

    private static Map<String, Object> apiClient(String name,
                                                 String method,
                                                 String endpoint,
                                                 String responseField) {
        Map<String, Object> client = new LinkedHashMap<>();
        client.put("name", name);
        client.put("method", method);
        client.put("endpoint", endpoint);
        client.put("responseField", responseField);
        client.put("requiresAdminSession", true);
        client.put("mockedFixtureAllowed", true);
        client.put("runtimeBackendCallAllowed", false);
        client.put("kubeManager8100Required", false);
        return Map.copyOf(client);
    }

    private static List<Map<String, Object>> buildPageAssemblies() {
        return List.of(
            pageAssembly("official-watch-page",
                AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                List.of("load-binding-spec", "render-source-dashboard", "render-disabled-actions",
                    "assert-no-runtime-buttons")),
            pageAssembly("technology-introduction-playbook-page",
                AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT,
                List.of("load-technology-introduction-playbook", "render-playbook-stages",
                    "render-lane-playbook-rows", "render-release-gates", "render-expert-review-rounds",
                    "assert-no-upgrade-buttons")),
            pageAssembly("compatibility-matrix-page",
                AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                List.of("load-binding-spec", "render-source-matrix", "render-candidate-lanes",
                    "render-test-lanes", "assert-no-upgrade-buttons")),
            pageAssembly("evidence-readiness-page",
                AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT,
                List.of("load-evidence-readiness", "render-evidence-gap-table", "render-blocking-gates",
                    "render-disabled-runtime-actions", "assert-no-enable-buttons")),
            pageAssembly("backend-technology-modernization-decision-page",
                AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT,
                List.of("load-backend-modernization-decision", "render-mainline-decisions",
                    "render-compatibility-lane-decisions", "render-learning-path",
                    "assert-no-upgrade-buttons"))
        );
    }

    private static Map<String, Object> pageAssembly(String pageId,
                                                    String bindingSpecEndpoint,
                                                    List<String> assemblySteps) {
        Map<String, Object> page = new LinkedHashMap<>();
        page.put("pageId", pageId);
        page.put("bindingSpecEndpoint", bindingSpecEndpoint);
        page.put("assemblySteps", List.copyOf(assemblySteps));
        page.put("ownsGovernanceLogic", false);
        page.put("usesBackendStateRules", true);
        page.put("runtimeControlAllowed", false);
        return Map.copyOf(page);
    }

    private static List<Map<String, Object>> buildSharedComponentContracts() {
        return List.of(
            sharedComponent("StatusBadge", "Render backend statuses using backend state rendering rules."),
            sharedComponent("MetricNumber", "Render backend counts without recomputing governance values."),
            sharedComponent("EvidenceTagList", "Render required evidence keys as read-only tags."),
            sharedComponent("ReadonlyTable", "Render source baselines, gates, lanes, and blocked shortcuts."),
            sharedComponent("DisabledActionList", "Render forbidden actions without buttons or click handlers."),
            sharedComponent("ExternalOfficialLink", "Open official URLs as navigation evidence only."),
            sharedComponent("ReadonlyJsonPanel", "Render embedded source read models without inline editing."),
            sharedComponent("EvidenceGapTable", "Render lane-to-evidence gaps without computing release authority."),
            sharedComponent("ExpertReviewRoundPanel", "Render required expert review rounds without workflow execution."),
            sharedComponent("LearningPathTimeline",
                "Render teaching steps without turning them into runtime workflow actions.")
        );
    }

    private static Map<String, Object> sharedComponent(String componentName, String rule) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("componentName", componentName);
        component.put("renderingRule", rule);
        component.put("readOnly", true);
        component.put("runtimeControlAllowed", false);
        component.put("inlineEditAllowed", false);
        return Map.copyOf(component);
    }

    private static List<Map<String, Object>> buildAcceptanceFixtures() {
        return List.of(
            fixture("official-watch-page-renders-with-mocked-binding-spec",
                "Render official sources, tracks, gates, disabled actions, and source JSON from mocked HTTP."),
            fixture("technology-playbook-page-renders-with-mocked-playbook",
                "Render introduction stages, lane playbook rows, release gates, expert reviews, and learning modules from mocked HTTP."),
            fixture("compatibility-matrix-page-renders-with-mocked-binding-spec",
                "Render baselines, candidate lanes, migration gates, blocked shortcuts, and test lanes from mocked HTTP."),
            fixture("evidence-readiness-page-renders-with-mocked-readiness",
                "Render advanced technology evidence gaps, blocking gates, next actions, and disabled runtime actions from mocked HTTP."),
            fixture("backend-modernization-decision-page-renders-with-mocked-decision",
                "Render Java/Spring mainline decisions, compatibility lanes, modernization gates, and learning path from mocked HTTP."),
            fixture("cross-page-navigation-keeps-read-only-state",
                "Switch between all top-tier technology pages without losing backend-owned read-only state."),
            fixture("runtime-buttons-absent-in-all-pages",
                "Assert upgrade, MCP tools/call, A2A, retrieval, CI blocking, HITL, and kube-manager write buttons are absent."),
            fixture("admin-auth-required-for-all-api-calls",
                "Mock anonymous/user/admin responses and require admin for all backend specs."),
            fixture("source-json-drilldown-redacted",
                "Render embedded source read models without raw prompt, token, password, or Authorization header.")
        );
    }

    private static Map<String, Object> fixture(String id, String purpose) {
        Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("id", id);
        fixture.put("purpose", purpose);
        fixture.put("requiresMockedHttp", true);
        fixture.put("requiresRuntimeBackendCalls", false);
        fixture.put("requiresKubeManager8100", false);
        fixture.put("runtimeControlAllowed", false);
        return Map.copyOf(fixture);
    }

    private static List<Map<String, Object>> buildForbiddenRuntimeControls(
        AgentOfficialVersionProtocolWatchVueBindingSpecResponse officialWatchBindingSpec,
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse compatibilityMatrixBindingSpec
    ) {
        List<String> officialActions = actionIds(officialWatchBindingSpec == null
            ? List.of()
            : officialWatchBindingSpec.disabledActionBindings());
        List<String> matrixActions = actionIds(compatibilityMatrixBindingSpec == null
            ? List.of()
            : compatibilityMatrixBindingSpec.disabledActionBindings());
        return List.of(
            forbiddenControl("official-watch-disabled-actions", officialActions),
            forbiddenControl("compatibility-matrix-disabled-actions", matrixActions),
            forbiddenControl("global-top-tier-runtime-controls", List.of(
                "upgrade-backend-dependencies-from-vue",
                "open-mcp-tools-call-from-vue",
                "trigger-a2a-runtime-handoff-from-vue",
                "enable-retrieval-vector-reranker-graphrag-from-vue",
                "enable-ci-blocking-from-empty-fixtures",
                "trigger-kube-manager-write-from-vue",
                "reopen-nim-hpc-slurm-bcm-phase2-from-vue"
            ))
        );
    }

    private static List<String> actionIds(List<Map<String, Object>> disabledActionBindings) {
        return disabledActionBindings.stream()
            .map(binding -> String.valueOf(binding.get("actionId")))
            .toList();
    }

    private static Map<String, Object> forbiddenControl(String groupId, List<String> actionIds) {
        Map<String, Object> control = new LinkedHashMap<>();
        control.put("groupId", groupId);
        control.put("actionIds", List.copyOf(actionIds));
        control.put("buttonVisible", false);
        control.put("clickHandlerAllowed", false);
        control.put("requiresSeparateReviewedSlice", true);
        return Map.copyOf(control);
    }

    private static List<String> buildImplementationOrder() {
        return List.of(
            "create-top-tier-agent-workbench-navigation",
            "add-api-client-methods-for-seven-read-only-endpoints",
            "implement-technology-introduction-playbook-page-from-read-model",
            "implement-shared-read-only-renderers",
            "implement-official-watch-page-from-binding-spec",
            "implement-compatibility-matrix-page-from-binding-spec",
            "implement-evidence-readiness-page-from-read-model",
            "implement-backend-modernization-decision-page-from-read-model",
            "add-mocked-fixture-tests-for-disabled-actions",
            "verify-runtime-control-buttons-are-absent"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("topTierVueWorkbenchImplementationPackage", PACKAGE_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchVueBindingSpec",
            AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchDashboard",
            AgentOfficialVersionProtocolWatchDashboardResponse.DASHBOARD_ENDPOINT);
        endpoints.put("topTierTechnologyIntroductionPlaybook",
            AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixVueBindingSpec",
            AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrix",
            AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
            AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT);
        endpoints.put("backendTechnologyModernizationDecision",
            AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT);
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildPackagePolicy(List<Map<String, Object>> routes,
                                                          List<Map<String, Object>> apiClients,
                                                          List<Map<String, Object>> assemblies,
                                                          List<Map<String, Object>> sharedComponents,
                                                          List<Map<String, Object>> fixtures,
                                                          List<Map<String, Object>> forbiddenControls) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("implementationPackageOnly", true);
        policy.put("vueWorkbenchOnly", true);
        policy.put("routeSpecCount", routes.size());
        policy.put("apiClientBindingCount", apiClients.size());
        policy.put("pageAssemblyCount", assemblies.size());
        policy.put("sharedComponentCount", sharedComponents.size());
        policy.put("acceptanceFixtureCount", fixtures.size());
        policy.put("forbiddenRuntimeControlGroupCount", forbiddenControls.size());
        policy.put("sourceBindingSpecsEmbedded", true);
        policy.put("runtimeControlAllowed", false);
        policy.put("runtimeButtonsAllowed", false);
        policy.put("dependencyUpgradeButtonsAllowed", false);
        policy.put("inlineEditAllowed", false);
        policy.put("mockedHttpFixturesRequired", true);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety(
        AgentOfficialVersionProtocolWatchVueBindingSpecResponse officialWatchBindingSpec,
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse compatibilityMatrixBindingSpec
    ) {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("implementationPackageOnly", true);
        safety.put("vueWorkbenchOnly", true);
        safety.put("sourceBindingSpecsReadOnly", officialWatchBindingSpec != null && compatibilityMatrixBindingSpec != null);
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
        AgentOfficialVersionProtocolWatchVueBindingSpecResponse officialWatchBindingSpec,
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse compatibilityMatrixBindingSpec
    ) {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", bool(officialWatchBindingSpec, "containsRawPrincipal")
            || bool(compatibilityMatrixBindingSpec, "containsRawPrincipal"));
        privacy.put("containsRawPrompt", bool(officialWatchBindingSpec, "containsRawPrompt")
            || bool(compatibilityMatrixBindingSpec, "containsRawPrompt"));
        privacy.put("containsRawDocument", bool(officialWatchBindingSpec, "containsRawDocument")
            || bool(compatibilityMatrixBindingSpec, "containsRawDocument"));
        privacy.put("containsAuthorizationHeader", bool(officialWatchBindingSpec, "containsAuthorizationHeader")
            || bool(compatibilityMatrixBindingSpec, "containsAuthorizationHeader"));
        privacy.put("containsToken", bool(officialWatchBindingSpec, "containsToken")
            || bool(compatibilityMatrixBindingSpec, "containsToken"));
        privacy.put("containsPassword", bool(officialWatchBindingSpec, "containsPassword")
            || bool(compatibilityMatrixBindingSpec, "containsPassword"));
        return Map.copyOf(privacy);
    }

    private static boolean bool(AgentOfficialVersionProtocolWatchVueBindingSpecResponse response, String key) {
        return response != null && Boolean.TRUE.equals(response.privacy().get(key));
    }

    private static boolean bool(AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse response, String key) {
        return response != null && Boolean.TRUE.equals(response.privacy().get(key));
    }
}
