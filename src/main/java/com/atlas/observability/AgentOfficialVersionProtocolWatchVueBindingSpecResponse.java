package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vue binding specification for the official version/protocol watch dashboard.
 *
 * <p>中文说明：本响应把 M5.75 Dashboard 翻译成前端实现规格，包括组件、字段路径、表格列、
 * 禁用动作和测试 fixture。它不接触真实前端仓库，也不创建任何运行时按钮。</p>
 */
public record AgentOfficialVersionProtocolWatchVueBindingSpecResponse(
    String schemaVersion,
    Instant generatedAt,
    String bindingStatus,
    String frontendTarget,
    boolean sourceDashboardEmbedded,
    boolean runtimeControlAllowed,
    int componentSpecCount,
    int fieldBindingCount,
    int tableColumnGroupCount,
    int disabledActionBindingCount,
    int fixtureCount,
    List<Map<String, Object>> componentSpecs,
    List<Map<String, Object>> fieldBindings,
    List<Map<String, Object>> tableColumnGroups,
    List<Map<String, Object>> stateRenderingRules,
    List<Map<String, Object>> disabledActionBindings,
    List<Map<String, Object>> testFixtures,
    List<String> implementationChecklist,
    AgentOfficialVersionProtocolWatchDashboardResponse sourceDashboard,
    Map<String, Object> endpointMap,
    Map<String, Object> bindingPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION =
        "agent-official-version-protocol-watch-vue-binding-spec.v1";
    public static final String BINDING_SPEC_ENDPOINT =
        "/api/agent/observability/top-tier/official-version-protocol-watch/vue-binding-spec";

    public static AgentOfficialVersionProtocolWatchVueBindingSpecResponse of(
        Instant generatedAt,
        AgentOfficialVersionProtocolWatchDashboardResponse sourceDashboard
    ) {
        List<Map<String, Object>> components = buildComponentSpecs();
        List<Map<String, Object>> bindings = buildFieldBindings();
        List<Map<String, Object>> columnGroups = buildTableColumnGroups();
        List<Map<String, Object>> disabledBindings = buildDisabledActionBindings(sourceDashboard);
        List<Map<String, Object>> fixtures = buildTestFixtures(sourceDashboard);
        return new AgentOfficialVersionProtocolWatchVueBindingSpecResponse(
            SCHEMA_VERSION,
            generatedAt,
            bindingStatus(sourceDashboard),
            "vue-kube-manager official Agent technology/protocol watch dashboard binding",
            sourceDashboard != null,
            false,
            components.size(),
            bindings.size(),
            columnGroups.size(),
            disabledBindings.size(),
            fixtures.size(),
            components,
            bindings,
            columnGroups,
            buildStateRenderingRules(),
            disabledBindings,
            fixtures,
            buildImplementationChecklist(),
            sourceDashboard,
            buildEndpointMap(),
            buildBindingPolicy(sourceDashboard, components, bindings, columnGroups, disabledBindings, fixtures),
            buildSafety(sourceDashboard),
            buildPrivacy(sourceDashboard)
        );
    }

    private static String bindingStatus(AgentOfficialVersionProtocolWatchDashboardResponse sourceDashboard) {
        if (sourceDashboard == null) {
            return "BINDING_SPEC_SOURCE_DASHBOARD_MISSING";
        }
        if (sourceDashboard.runtimeControlAllowed()
            || Boolean.TRUE.equals(sourceDashboard.safety().get("toolExecution"))
            || Boolean.TRUE.equals(sourceDashboard.safety().get("mcpToolsCall"))) {
            return "UNEXPECTED_RUNTIME_CONTROL_IN_SOURCE_DASHBOARD";
        }
        return "VUE_BINDING_SPEC_READY";
    }

    private static List<Map<String, Object>> buildComponentSpecs() {
        return List.of(
            component("OfficialWatchSummaryStrip", "summary-strip",
                "Render source/track/gate/shortcut counts and dashboard status.",
                List.of("dashboardStatus", "sourceCardCount", "technologyTrackCardCount",
                    "adoptionGateCount", "blockedRuntimeShortcutCount")),
            component("OfficialSourceCardGrid", "card-grid",
                "Render official source cards with external links only.",
                List.of("sourceCards[].title", "sourceCards[].sourceType",
                    "sourceCards[].sourceReviewDate", "sourceCards[].officialUrl")),
            component("TechnologyTrackMatrix", "status-matrix",
                "Render technology adoption decisions, evidence requirements, and disabled runtime actions.",
                List.of("technologyTrackCards[].status", "technologyTrackCards[].beforeRuntimeEvidence",
                    "technologyTrackCards[].localAnchors", "technologyTrackCards[].disabledRuntimeActions")),
            component("AdoptionGateTable", "table",
                "Render required gates as read-only rows.",
                List.of("adoptionGateRows[].id", "adoptionGateRows[].summary",
                    "adoptionGateRows[].required", "adoptionGateRows[].runtimeBound")),
            component("BlockedShortcutTable", "table",
                "Render blocked shortcuts and why they block top-tier claims.",
                List.of("blockedRuntimeShortcutRows[].id", "blockedRuntimeShortcutRows[].summary",
                    "blockedRuntimeShortcutRows[].blocksTopTierClaim")),
            component("DisabledRuntimeActionList", "disabled-action-list",
                "Render disabled runtime actions as non-clickable evidence rows.",
                List.of("disabledRuntimeActions[].id", "disabledRuntimeActions[].disabledReason",
                    "disabledRuntimeActions[].requiresSeparateReviewedSlice")),
            component("OfficialWatchSourceJsonPanel", "read-only-json",
                "Expose the embedded source watch for audit/debug drill-down without inline edits.",
                List.of("sourceWatch.schemaVersion", "sourceWatch.officialSources",
                    "sourceWatch.technologyTracks", "sourceWatch.standardsAlignment"))
        );
    }

    private static Map<String, Object> component(String name,
                                                 String componentType,
                                                 String purpose,
                                                 List<String> requiredFields) {
        Map<String, Object> component = new LinkedHashMap<>();
        component.put("name", name);
        component.put("componentType", componentType);
        component.put("purpose", purpose);
        component.put("requiredFields", List.copyOf(requiredFields));
        component.put("readOnly", true);
        component.put("runtimeControlAllowed", false);
        component.put("inlineEditAllowed", false);
        component.put("emptyStateAllowed", false);
        return Map.copyOf(component);
    }

    private static List<Map<String, Object>> buildFieldBindings() {
        return List.of(
            field("dashboard.status", "dashboardStatus", "StatusBadge", "DASHBOARD_READY_TO_RENDER_OFFICIAL_WATCH"),
            field("dashboard.frontendTarget", "frontendTarget", "MutedText", "vue-kube-manager official Agent technology/protocol watch dashboard"),
            field("dashboard.sourceCount", "sourceCardCount", "MetricNumber", "8"),
            field("dashboard.trackCount", "technologyTrackCardCount", "MetricNumber", "8"),
            field("source.title", "sourceCards[].title", "ExternalLinkLabel", "Spring AI reference documentation"),
            field("source.officialUrl", "sourceCards[].officialUrl", "ExternalLink", "https://docs.spring.io/spring-ai/reference/"),
            field("track.status", "technologyTrackCards[].status", "StatusBadge", "MANIFEST_FIRST"),
            field("track.disabledActions", "technologyTrackCards[].disabledRuntimeActions", "DisabledActionList", "run-mcp-tools-call"),
            field("gate.summary", "adoptionGateRows[].summary", "ReadOnlyText", "A human-reviewed official source URL and review date must exist before changing the watch."),
            field("shortcut.blocksTopTierClaim", "blockedRuntimeShortcutRows[].blocksTopTierClaim", "BooleanBadge", "true"),
            field("policy.runtimeControlAllowed", "dashboardPolicy.runtimeControlAllowed", "HiddenActionGuard", "false"),
            field("safety.mcpToolsCall", "safety.mcpToolsCall", "HiddenActionGuard", "false")
        );
    }

    private static Map<String, Object> field(String id,
                                             String fieldPath,
                                             String renderer,
                                             String exampleValue) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("id", id);
        binding.put("fieldPath", fieldPath);
        binding.put("renderer", renderer);
        binding.put("exampleValue", exampleValue);
        binding.put("required", true);
        binding.put("readOnly", true);
        binding.put("runtimeControlAllowed", false);
        return Map.copyOf(binding);
    }

    private static List<Map<String, Object>> buildTableColumnGroups() {
        return List.of(
            columns("sourceCards", List.of("title", "sourceType", "sourceReviewDate", "adoptionMode", "officialUrl"),
                "officialUrl must render as external navigation only."),
            columns("technologyTrackCards", List.of("title", "status", "phase1Interpretation", "beforeRuntimeEvidence", "disabledRuntimeActions"),
                "disabledRuntimeActions must render as disabled rows, not buttons."),
            columns("adoptionGateRows", List.of("id", "status", "summary", "required", "runtimeControlAllowed"),
                "runtimeControlAllowed=false must be visually obvious."),
            columns("blockedRuntimeShortcutRows", List.of("id", "status", "summary", "allowed", "blocksTopTierClaim"),
                "allowed=false rows should not expose quick actions.")
        );
    }

    private static Map<String, Object> columns(String dataField,
                                               List<String> columns,
                                               String renderingRule) {
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("dataField", dataField);
        group.put("columns", List.copyOf(columns));
        group.put("renderingRule", renderingRule);
        group.put("readOnly", true);
        group.put("runtimeControlAllowed", false);
        return Map.copyOf(group);
    }

    private static List<Map<String, Object>> buildStateRenderingRules() {
        return List.of(
            stateRule("INFO", "neutral", "Render as informational evidence."),
            stateRule("BLOCKING", "danger", "Render as blocking evidence without action buttons."),
            stateRule("REQUIRED_GATE", "warning", "Render as mandatory review gate."),
            stateRule("BLOCKED_SHORTCUT", "danger", "Render as forbidden shortcut."),
            stateRule("OFFICIAL_SOURCE_REVIEWED", "success", "Render as reviewed official source.")
        );
    }

    private static Map<String, Object> stateRule(String status, String tone, String renderingRule) {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("status", status);
        rule.put("tone", tone);
        rule.put("renderingRule", renderingRule);
        rule.put("allowsRuntimeAction", false);
        return Map.copyOf(rule);
    }

    private static List<Map<String, Object>> buildDisabledActionBindings(
        AgentOfficialVersionProtocolWatchDashboardResponse sourceDashboard
    ) {
        List<String> actionIds = sourceDashboard != null
            ? sourceDashboard.disabledRuntimeActions().stream()
            .map(action -> String.valueOf(action.get("id")))
            .toList()
            : List.of(
                "upgrade-dependencies-from-dashboard",
                "enable-mcp-tools-call",
                "enable-a2a-runtime-handoff",
                "enable-retrieval-runtime",
                "enable-ci-blocking",
                "reopen-phase2-domain-plugins"
            );
        return actionIds.stream()
            .map(AgentOfficialVersionProtocolWatchVueBindingSpecResponse::disabledActionBinding)
            .toList();
    }

    private static Map<String, Object> disabledActionBinding(String actionId) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("actionId", actionId);
        binding.put("renderAs", "disabled-row");
        binding.put("buttonVisible", false);
        binding.put("clickHandlerAllowed", false);
        binding.put("requiresSeparateReviewedSlice", true);
        return Map.copyOf(binding);
    }

    private static List<Map<String, Object>> buildTestFixtures(
        AgentOfficialVersionProtocolWatchDashboardResponse sourceDashboard
    ) {
        int sourceCardCount = sourceDashboard != null ? sourceDashboard.sourceCardCount() : 0;
        int trackCardCount = sourceDashboard != null ? sourceDashboard.technologyTrackCardCount() : 0;
        return List.of(
            fixture("happy-path-dashboard", "Render full dashboard with reviewed sources and disabled actions.",
                Map.of("sourceCardCount", sourceCardCount, "technologyTrackCardCount", trackCardCount,
                    "runtimeControlAllowed", false)),
            fixture("mcp-security-source-visible", "Render NSA MCP security source as official reviewed evidence.",
                Map.of("requiredSourceId", "nsa-mcp-security-2026-06", "externalNavigationOnly", true)),
            fixture("runtime-buttons-absent", "Assert all runtime action buttons are absent.",
                Map.of("buttonVisible", false, "clickHandlerAllowed", false)),
            fixture("source-watch-drilldown", "Render embedded sourceWatch read-only JSON drill-down.",
                Map.of("sourceWatchEmbedded", sourceDashboard != null, "inlineEditAllowed", false))
        );
    }

    private static Map<String, Object> fixture(String id,
                                               String scenario,
                                               Map<String, Object> assertions) {
        Map<String, Object> fixture = new LinkedHashMap<>();
        fixture.put("id", id);
        fixture.put("scenario", scenario);
        fixture.put("assertions", Map.copyOf(assertions));
        fixture.put("requiresMockedHttp", true);
        fixture.put("requiresRuntimeBackendCalls", false);
        fixture.put("requiresKubeManager8100", false);
        return Map.copyOf(fixture);
    }

    private static List<String> buildImplementationChecklist() {
        return List.of(
            "create-route-official-version-protocol-watch-dashboard",
            "fetch-dashboard-endpoint-with-admin-session",
            "render-summary-strip-before-card-grids",
            "render-official-source-cards-with-external-link-only",
            "render-technology-track-matrix-with-disabled-actions",
            "render-adoption-gates-and-blocked-shortcuts-as-read-only-tables",
            "hide-all-runtime-enable-buttons",
            "add-fixtures-for-nsa-mcp-security-source-and-disabled-actions",
            "keep-nim-hpc-slurm-bcm-phase2-hidden-from-runtime-controls"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("officialVersionProtocolWatchVueBindingSpec", BINDING_SPEC_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchDashboard",
            AgentOfficialVersionProtocolWatchDashboardResponse.DASHBOARD_ENDPOINT);
        endpoints.put("officialVersionProtocolWatch", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT);
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildBindingPolicy(
        AgentOfficialVersionProtocolWatchDashboardResponse sourceDashboard,
        List<Map<String, Object>> componentSpecs,
        List<Map<String, Object>> fieldBindings,
        List<Map<String, Object>> tableColumnGroups,
        List<Map<String, Object>> disabledActionBindings,
        List<Map<String, Object>> testFixtures
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("bindingSpecOnly", true);
        policy.put("vueWorkbenchOnly", true);
        policy.put("sourceDashboardEmbedded", sourceDashboard != null);
        policy.put("componentSpecCount", componentSpecs.size());
        policy.put("fieldBindingCount", fieldBindings.size());
        policy.put("tableColumnGroupCount", tableColumnGroups.size());
        policy.put("disabledActionBindingCount", disabledActionBindings.size());
        policy.put("fixtureCount", testFixtures.size());
        policy.put("runtimeControlAllowed", false);
        policy.put("runtimeButtonsAllowed", false);
        policy.put("inlineEditAllowed", false);
        policy.put("mockedHttpFixturesRequired", true);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety(AgentOfficialVersionProtocolWatchDashboardResponse sourceDashboard) {
        Map<String, Object> sourceSafety = sourceDashboard != null ? sourceDashboard.safety() : Map.of();
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("bindingSpecOnly", true);
        safety.put("vueWorkbenchOnly", true);
        safety.put("sourceDashboardReadOnly", bool(sourceSafety, "readOnly"));
        safety.put("runtimeMutationAllowed", false);
        safety.put("runtimeUpgradePerformed", false);
        safety.put("dependencyUpgradePerformed", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("kubeManagerCalls", false);
        safety.put("mcpToolsCall", false);
        safety.put("a2aRuntimeHandoff", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        safety.put("memoryWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("vectorStoreCalls", false);
        safety.put("embeddingModelCalls", false);
        safety.put("rerankerCalls", false);
        safety.put("ciBlockingChanged", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(AgentOfficialVersionProtocolWatchDashboardResponse sourceDashboard) {
        Map<String, Object> sourcePrivacy = sourceDashboard != null ? sourceDashboard.privacy() : Map.of();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", bool(sourcePrivacy, "containsRawPrincipal"));
        privacy.put("containsRawPrompt", bool(sourcePrivacy, "containsRawPrompt"));
        privacy.put("containsRawDocument", bool(sourcePrivacy, "containsRawDocument"));
        privacy.put("containsAuthorizationHeader", bool(sourcePrivacy, "containsAuthorizationHeader"));
        privacy.put("containsToken", bool(sourcePrivacy, "containsToken"));
        privacy.put("containsPassword", bool(sourcePrivacy, "containsPassword"));
        privacy.put("containsRuntimeSecrets", bool(sourcePrivacy, "containsRuntimeSecrets"));
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
