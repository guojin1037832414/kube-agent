package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vue binding specification for the advanced technology compatibility matrix.
 *
 * <p>中文说明：本响应把 M5.77 兼容矩阵翻译成 vue-kube-manager 可直接实现的前端规格，
 * 包括组件、字段路径、表格列、状态渲染、禁用动作和测试 fixture。它不创建真实前端按钮，
 * 不触发依赖升级，也不打开任何 Agent / Tool / MCP / RAG / kube-manager 运行时能力。</p>
 */
public record AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse(
    String schemaVersion,
    Instant generatedAt,
    String bindingStatus,
    String frontendTarget,
    boolean sourceMatrixEmbedded,
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
    AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix,
    Map<String, Object> endpointMap,
    Map<String, Object> bindingPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION =
        "agent-advanced-technology-compatibility-matrix-vue-binding-spec.v1";
    public static final String BINDING_SPEC_ENDPOINT =
        "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec";

    public static AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse of(
        Instant generatedAt,
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix
    ) {
        List<Map<String, Object>> components = buildComponentSpecs();
        List<Map<String, Object>> bindings = buildFieldBindings();
        List<Map<String, Object>> columnGroups = buildTableColumnGroups();
        List<Map<String, Object>> disabledBindings = buildDisabledActionBindings(sourceMatrix);
        List<Map<String, Object>> fixtures = buildTestFixtures(sourceMatrix);
        return new AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse(
            SCHEMA_VERSION,
            generatedAt,
            bindingStatus(sourceMatrix),
            "vue-kube-manager advanced Agent technology compatibility matrix binding",
            sourceMatrix != null,
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
            sourceMatrix,
            buildEndpointMap(),
            buildBindingPolicy(sourceMatrix, components, bindings, columnGroups, disabledBindings, fixtures),
            buildSafety(sourceMatrix),
            buildPrivacy(sourceMatrix)
        );
    }

    private static String bindingStatus(AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix) {
        if (sourceMatrix == null) {
            return "BINDING_SPEC_SOURCE_MATRIX_MISSING";
        }
        if (sourceMatrix.runtimeControlAllowed()
            || sourceMatrix.runtimeUpgradeAllowedNow()
            || sourceMatrix.dependencyUpgradeAllowedNow()
            || Boolean.TRUE.equals(sourceMatrix.safety().get("mcpToolsCall"))
            || Boolean.TRUE.equals(sourceMatrix.safety().get("toolExecution"))) {
            return "UNEXPECTED_RUNTIME_CONTROL_IN_SOURCE_MATRIX";
        }
        return "VUE_BINDING_SPEC_READY";
    }

    private static List<Map<String, Object>> buildComponentSpecs() {
        return List.of(
            component("AdvancedTechnologyMatrixSummaryStrip", "summary-strip",
                "Render matrix status, source count, matrix item count, gate count, shortcut count, and test lane count.",
                List.of("matrixStatus", "sourceBaselineCount", "matrixItemCount",
                    "migrationGateCount", "blockedShortcutCount", "testLaneCount")),
            component("SourceBaselineTable", "table",
                "Render reviewed official-source baselines as external navigation evidence only.",
                List.of("sourceBaselines[].sourceId", "sourceBaselines[].sourceType",
                    "sourceBaselines[].reviewDate", "sourceBaselines[].officialUrl")),
            component("CandidateUpgradeLaneMatrix", "status-matrix",
                "Render current baseline, candidate target, readiness, evidence, and adoption rule for each technology lane.",
                List.of("matrixItems[].id", "matrixItems[].currentBaseline",
                    "matrixItems[].candidateTarget", "matrixItems[].readiness",
                    "matrixItems[].requiredEvidence", "matrixItems[].adoptionRule")),
            component("MigrationGateChecklist", "checklist-table",
                "Render migration gates as required read-only checklist rows.",
                List.of("migrationGates[].id", "migrationGates[].summary",
                    "migrationGates[].required", "migrationGates[].runtimeBound")),
            component("BlockedUpgradeShortcutTable", "table",
                "Render blocked shortcuts and why they block a top-tier claim.",
                List.of("blockedUpgradeShortcuts[].id", "blockedUpgradeShortcuts[].summary",
                    "blockedUpgradeShortcuts[].allowed", "blockedUpgradeShortcuts[].blocksTopTierClaim")),
            component("CompatibilityTestLaneBoard", "lane-board",
                "Render current, planned, and release-gated test lanes without start buttons.",
                List.of("testLanes[].id", "testLanes[].target",
                    "testLanes[].status", "testLanes[].runtimeAuthorityOpened")),
            component("MatrixImplementationChecklistPanel", "read-only-list",
                "Render the backend-owned implementation checklist for future compatibility branches.",
                List.of("implementationChecklist[]")),
            component("CompatibilityMatrixSourceJsonPanel", "read-only-json",
                "Expose sourceMatrix and embedded sourceWatch for audit/debug drill-down without inline edits.",
                List.of("sourceMatrix.schemaVersion", "sourceMatrix.sourceWatch.schemaVersion",
                    "sourceMatrix.endpointMap", "sourceMatrix.safety"))
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
            field("matrix.status", "matrixStatus", "StatusBadge", "MATRIX_DEFINED_NOT_EXECUTED"),
            field("matrix.frontendTarget", "frontendTarget", "MutedText",
                "vue-kube-manager advanced Agent technology compatibility matrix binding"),
            field("matrix.sourceBaselineCount", "sourceBaselineCount", "MetricNumber", "8"),
            field("matrix.matrixItemCount", "matrixItemCount", "MetricNumber", "10"),
            field("matrix.migrationGateCount", "migrationGateCount", "MetricNumber", "8"),
            field("source.sourceId", "sourceBaselines[].sourceId", "CodeText", "spring-ai-reference"),
            field("source.officialUrl", "sourceBaselines[].officialUrl", "ExternalLink",
                "https://docs.spring.io/spring-ai/reference/"),
            field("item.readiness", "matrixItems[].readiness", "StatusBadge", "COMPATIBILITY_REQUIRED"),
            field("item.requiredEvidence", "matrixItems[].requiredEvidence", "EvidenceTagList",
                "mvn-validate-on-candidate-jdk"),
            field("item.mainlineAllowedNow", "matrixItems[].mainlineAllowedNow", "BooleanBadge", "false"),
            field("gate.required", "migrationGates[].required", "BooleanBadge", "true"),
            field("shortcut.allowed", "blockedUpgradeShortcuts[].allowed", "DangerBooleanBadge", "false"),
            field("lane.status", "testLanes[].status", "StatusBadge", "PLANNED"),
            field("safety.runtimeControlAllowed", "safety.runtimeControlAllowed", "HiddenActionGuard", "false")
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
            columns("sourceBaselines",
                List.of("sourceId", "sourceType", "reviewDate", "adoptionMode", "officialUrl"),
                "officialUrl must render as external navigation only."),
            columns("matrixItems",
                List.of("id", "currentBaseline", "candidateTarget", "readiness", "requiredEvidence",
                    "adoptionRule", "mainlineAllowedNow", "runtimeControlAllowed"),
                "mainlineAllowedNow=false and runtimeControlAllowed=false must be visually obvious."),
            columns("migrationGates",
                List.of("id", "summary", "required", "runtimeBound"),
                "required=true rows render as mandatory review gates."),
            columns("blockedUpgradeShortcuts",
                List.of("id", "summary", "allowed", "blocksTopTierClaim"),
                "allowed=false rows must not expose buttons or click handlers."),
            columns("testLanes",
                List.of("id", "target", "status", "runtimeAuthorityOpened", "requiresSeparateReviewedSlice"),
                "runtimeAuthorityOpened=false rows render as future evidence lanes.")
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
            stateRule("MATRIX_DEFINED_NOT_EXECUTED", "neutral", "Render as governance evidence, not as active runtime."),
            stateRule("COMPATIBILITY_REQUIRED", "warning", "Render as candidate lane requiring compatibility proof."),
            stateRule("CONTRACT_FIRST", "info", "Render as local contract mapping before runtime binding."),
            stateRule("RELEASE_GATED", "warning", "Render as blocked until release evidence exists."),
            stateRule("EVIDENCE_BLOCKED", "danger", "Render as blocked by missing reviewed traces/evals."),
            stateRule("WRITE_AUTHORITY_CLOSED", "danger", "Render as write path closed."),
            stateRule("QUALITY_GATE_REQUIRED", "warning", "Render as quality gate work before CI enforcement."),
            stateRule("BLOCKED_SHORTCUT", "danger", "Render as forbidden shortcut.")
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
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix
    ) {
        List<String> actionIds = sourceMatrix != null
            ? sourceMatrix.blockedUpgradeShortcuts().stream()
            .map(action -> String.valueOf(action.get("id")))
            .toList()
            : List.of(
                "upgrade-pom-from-readiness-page",
                "treat-rc-preview-as-mainline",
                "trust-mcp-tool-annotations",
                "delegate-authority-to-external-agent",
                "enable-retrieval-before-reviewed-traces",
                "use-otel-experimental-fields-as-contract",
                "enable-ci-blocking-with-empty-fixtures"
            );
        return actionIds.stream()
            .map(AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse::disabledActionBinding)
            .toList();
    }

    private static Map<String, Object> disabledActionBinding(String actionId) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("actionId", actionId);
        binding.put("renderAs", "disabled-row");
        binding.put("buttonVisible", false);
        binding.put("clickHandlerAllowed", false);
        binding.put("requiresSeparateReviewedSlice", true);
        binding.put("blocksTopTierClaim", true);
        return Map.copyOf(binding);
    }

    private static List<Map<String, Object>> buildTestFixtures(
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix
    ) {
        int matrixItemCount = sourceMatrix != null ? sourceMatrix.matrixItemCount() : 0;
        int testLaneCount = sourceMatrix != null ? sourceMatrix.testLaneCount() : 0;
        return List.of(
            fixture("happy-path-matrix", "Render full compatibility matrix with all candidate lanes.",
                Map.of("matrixItemCount", matrixItemCount, "testLaneCount", testLaneCount,
                    "runtimeControlAllowed", false)),
            fixture("major-upgrade-lanes-visible", "Render Java, Spring Boot, Spring AI, MCP, A2A, and RAG lanes.",
                Map.of("requiredLaneIds", List.of("java-runtime-toolchains", "spring-boot-framework",
                    "spring-ai-access-layer", "mcp-runtime-call-plane", "a2a-multi-agent-provenance",
                    "memory-rag-graphrag-reranker-vectorstore"))),
            fixture("runtime-buttons-absent", "Assert all runtime and dependency upgrade buttons are absent.",
                Map.of("buttonVisible", false, "clickHandlerAllowed", false)),
            fixture("blocked-shortcuts-visible", "Render blocked upgrade shortcuts as evidence rows.",
                Map.of("requiredShortcutId", "upgrade-pom-from-readiness-page", "blocksTopTierClaim", true)),
            fixture("source-watch-drilldown", "Render embedded sourceMatrix/sourceWatch read-only JSON drill-down.",
                Map.of("sourceMatrixEmbedded", sourceMatrix != null, "inlineEditAllowed", false))
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
            "create-route-advanced-technology-compatibility-matrix",
            "fetch-matrix-endpoint-with-admin-session",
            "render-summary-strip-before-technology-lanes",
            "render-source-baselines-with-external-link-only",
            "render-candidate-upgrade-lanes-with-evidence-tags",
            "render-migration-gates-and-blocked-shortcuts-as-read-only-tables",
            "render-test-lanes-without-start-buttons",
            "hide-all-runtime-and-dependency-upgrade-buttons",
            "add-fixtures-for-major-upgrade-lanes-and-disabled-shortcuts",
            "keep-nim-hpc-slurm-bcm-phase2-hidden-from-runtime-controls"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("advancedTechnologyCompatibilityMatrixVueBindingSpec", BINDING_SPEC_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrix",
            AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT);
        endpoints.put("advancedTechnologyAdoptionContract",
            "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        endpoints.put("officialVersionProtocolWatch", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchVueBindingSpec",
            AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildBindingPolicy(
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix,
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
        policy.put("sourceMatrixEmbedded", sourceMatrix != null);
        policy.put("componentSpecCount", componentSpecs.size());
        policy.put("fieldBindingCount", fieldBindings.size());
        policy.put("tableColumnGroupCount", tableColumnGroups.size());
        policy.put("disabledActionBindingCount", disabledActionBindings.size());
        policy.put("fixtureCount", testFixtures.size());
        policy.put("runtimeControlAllowed", false);
        policy.put("runtimeButtonsAllowed", false);
        policy.put("dependencyUpgradeButtonsAllowed", false);
        policy.put("inlineEditAllowed", false);
        policy.put("mockedHttpFixturesRequired", true);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety(AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix) {
        Map<String, Object> sourceSafety = sourceMatrix != null ? sourceMatrix.safety() : Map.of();
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("bindingSpecOnly", true);
        safety.put("vueWorkbenchOnly", true);
        safety.put("sourceMatrixReadOnly", bool(sourceSafety, "readOnly"));
        safety.put("runtimeMutationAllowed", false);
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

    private static Map<String, Object> buildPrivacy(AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix) {
        Map<String, Object> sourcePrivacy = sourceMatrix != null ? sourceMatrix.privacy() : Map.of();
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
