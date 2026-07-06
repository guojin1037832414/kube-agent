package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * reviewed fixture 工作流的 Vue 绑定规格响应。
 *
 * <p>中文说明：这个响应不是前端页面本身，而是后端给 `vue-kube-manager` 的实现契约。它把 reviewed fixture
 * 的自动候选、candidate preview、人审包、人审 gate、manifest readiness 和 catalog patch review 串成一个可渲染的
 * 只读工作流，前端照此实现即可，不需要自行推断哪些按钮该显示、哪些状态能代表 release 或写权限。</p>
 *
 * <p>安全边界：本响应可以嵌入 capability/overview 这类 redacted 读模型，但不嵌入 raw audit、replay steps、
 * eval reports 或 fixtureRows；所有动作绑定都必须是禁用/缺席的 runtime action。它不创建 fixture，不写 catalog，
 * 不执行 Tool/MCP/LLM/RAG/kube-manager，也不打开 CI blocking、release authority 或 Phase 2 能力。</p>
 */
public record AgentReviewedTraceFixtureVueBindingSpecResponse(
    String schemaVersion,
    Instant generatedAt,
    String bindingStatus,
    String frontendTarget,
    boolean sourceCapabilitiesEmbedded,
    boolean sourceOverviewEmbedded,
    boolean runtimeControlAllowed,
    int componentSpecCount,
    int fieldBindingCount,
    int workflowStageCount,
    int disabledActionBindingCount,
    int fixtureCount,
    List<Map<String, Object>> componentSpecs,
    List<Map<String, Object>> fieldBindings,
    List<Map<String, Object>> workflowStages,
    List<Map<String, Object>> stateRenderingRules,
    List<Map<String, Object>> disabledActionBindings,
    List<Map<String, Object>> testFixtures,
    List<String> implementationChecklist,
    AgentEvalWorkbenchCapabilitiesResponse sourceCapabilities,
    AgentEvalWorkbenchOverviewResponse sourceOverview,
    Map<String, Object> endpointMap,
    Map<String, Object> bindingPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION =
        "agent-reviewed-trace-fixture-vue-binding-spec.v1";
    public static final String ENDPOINT =
        "/api/agent/observability/eval/workbench/reviewed-fixture-vue-binding-spec";

    public static AgentReviewedTraceFixtureVueBindingSpecResponse of(
        Instant generatedAt,
        AgentEvalWorkbenchCapabilitiesResponse capabilities,
        AgentEvalWorkbenchOverviewResponse overview) {
        List<Map<String, Object>> components = buildComponentSpecs();
        List<Map<String, Object>> fields = buildFieldBindings();
        List<Map<String, Object>> stages = buildWorkflowStages();
        List<Map<String, Object>> disabledActions = buildDisabledActionBindings();
        List<Map<String, Object>> fixtures = buildTestFixtures();
        return new AgentReviewedTraceFixtureVueBindingSpecResponse(
            SCHEMA_VERSION,
            generatedAt,
            bindingStatus(capabilities, overview),
            "vue-kube-manager reviewed fixture eval workbench binding",
            capabilities != null,
            overview != null,
            false,
            components.size(),
            fields.size(),
            stages.size(),
            disabledActions.size(),
            fixtures.size(),
            components,
            fields,
            stages,
            buildStateRenderingRules(),
            disabledActions,
            fixtures,
            buildImplementationChecklist(),
            capabilities,
            overview,
            buildEndpointMap(),
            bindingPolicy(capabilities, overview, components, fields, stages, disabledActions, fixtures),
            buildSafety(capabilities, overview),
            buildPrivacy(capabilities, overview)
        );
    }

    private static String bindingStatus(AgentEvalWorkbenchCapabilitiesResponse capabilities,
                                        AgentEvalWorkbenchOverviewResponse overview) {
        if (capabilities == null || overview == null) {
            return "BINDING_SPEC_SOURCE_READ_MODELS_MISSING";
        }
        boolean hasGateCapability = capabilities.capabilities().stream()
            .anyMatch(capability -> "workbench-reviewed-fixture-human-review-gate".equals(capability.id()));
        boolean hasPackageCapability = capabilities.capabilities().stream()
            .anyMatch(capability -> "workbench-reviewed-fixture-human-review-package".equals(capability.id()));
        boolean hasCandidateCapability = capabilities.capabilities().stream()
            .anyMatch(capability -> "workbench-reviewed-fixture-candidate-autopreview".equals(capability.id()));
        boolean overviewMentionsGate = overview.nextActions().contains("validate-reviewed-fixture-human-review-gate");
        if (hasGateCapability && hasPackageCapability && hasCandidateCapability && overviewMentionsGate) {
            return "VUE_BINDING_SPEC_READY";
        }
        return "BINDING_SPEC_REVIEWED_FIXTURE_FLOW_INCOMPLETE";
    }

    private static List<Map<String, Object>> buildComponentSpecs() {
        return List.of(
            component("ReviewedFixtureWorkflowSummary", "summary-strip",
                "展示 trace set 数量、需补证据数量、工作流能力数和 release 关闭状态。",
                List.of("sourceOverview.traceSetCount", "sourceOverview.traceSetNeedsEvidenceCount",
                    "sourceCapabilities.capabilityCount", "sourceOverview.releaseEligible")),
            component("ReviewedFixtureTraceSetTable", "table",
                "按 trace set 行展示自动候选、人审包、人审 gate 和 manifest/catalog review 路径。",
                List.of("sourceOverview.traceSets[].id", "sourceOverview.traceSets[].status",
                    "sourceOverview.traceSets[].reviewedFixtureCandidateWorkbenchPath",
                    "sourceOverview.traceSets[].reviewedFixtureHumanReviewPackagePath",
                    "sourceOverview.traceSets[].reviewedFixtureHumanReviewGatePath")),
            component("CandidateWorkbenchPanel", "read-only-json-panel",
                "展示自动候选预检入口、candidateDiscoverySummary 和 selectedCandidateTraceId。",
                List.of("candidateWorkbench.workbenchStatus", "candidateWorkbench.candidateDiscoverySummary",
                    "candidateWorkbench.selectedCandidateTraceId")),
            component("HumanReviewPackagePanel", "review-checklist-panel",
                "展示 candidateFixtureDraft、manualReviewFields、reviewChecklist 和 manifestQualityGatePreview。",
                List.of("humanReviewPackage.candidateFixtureDraft", "humanReviewPackage.manualReviewFields",
                    "humanReviewPackage.reviewChecklist", "humanReviewPackage.manifestQualityGatePreview")),
            component("HumanReviewGatePanel", "validate-only-form",
                "渲染人工字段输入和 expectedEvidenceDigest，但提交后只能得到 validate-only 结果。",
                List.of("humanReviewGate.fieldResults", "humanReviewGate.expectedEvidenceDigest",
                    "humanReviewGate.readyForFixtureCommit", "humanReviewGate.runtimeFixtureCommitAllowed")),
            component("ReviewedFixtureReadinessPanel", "readiness-table",
                "展示 catalog patch review 中的 reviewedFixtureReadiness 和 failedQualityGates。",
                List.of("catalogPatchReview.reviewedFixtureReadiness",
                    "catalogPatchReview.reviewedFixtureReadiness.currentTraceSetFailedQualityGates")),
            component("DisabledRuntimeActionPanel", "disabled-action-list",
                "集中展示所有必须缺席或禁用的 runtime action，防止前端误做写按钮。",
                List.of("disabledActionBindings[].actionId", "disabledActionBindings[].buttonVisible",
                    "disabledActionBindings[].clickHandlerAllowed")),
            component("ReviewedFixtureRawReadModelPanel", "read-only-json-panel",
                "仅用于调试展开后端只读模型，不允许 inline edit 或 raw evidence 展示。",
                List.of("sourceCapabilities.schemaVersion", "sourceOverview.schemaVersion",
                    "bindingPolicy", "safety", "privacy"))
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
        component.put("writesBackendState", false);
        return Map.copyOf(component);
    }

    private static List<Map<String, Object>> buildFieldBindings() {
        return List.of(
            field("summary.capabilityCount", "sourceCapabilities.capabilityCount", "MetricNumber", "21"),
            field("summary.traceSetNeedsEvidenceCount", "sourceOverview.traceSetNeedsEvidenceCount",
                "MetricNumber", "7"),
            field("summary.releaseEligible", "sourceOverview.releaseEligible", "FalseBadge", "false"),
            field("traceSet.id", "sourceOverview.traceSets[].id", "CodeText", "phase1-core-golden"),
            field("traceSet.status", "sourceOverview.traceSets[].status", "StatusBadge", "NEEDS_REDACTED_EVIDENCE"),
            field("traceSet.candidateWorkbenchPath",
                "sourceOverview.traceSets[].reviewedFixtureCandidateWorkbenchPath", "EndpointLink",
                "/reviewed-fixture-candidate-workbench"),
            field("traceSet.humanReviewPackagePath",
                "sourceOverview.traceSets[].reviewedFixtureHumanReviewPackagePath", "EndpointLink",
                "/reviewed-fixture-human-review-package"),
            field("traceSet.humanReviewGatePath",
                "sourceOverview.traceSets[].reviewedFixtureHumanReviewGatePath", "EndpointLink",
                "/reviewed-fixture-human-review-gate"),
            field("package.manualReviewFields", "humanReviewPackage.manualReviewFields[].name",
                "ChecklistItem", "sourceCommitSha"),
            field("package.readyForFixtureCommit", "humanReviewPackage.readyForFixtureCommit",
                "FalseBadge", "false"),
            field("gate.expectedEvidenceDigest", "humanReviewGate.expectedEvidenceDigest",
                "CodeText", "sha256:<computed>"),
            field("gate.readyForFixtureCommit", "humanReviewGate.readyForFixtureCommit",
                "ConditionalReadOnlyBadge", "true-only-means-manual-git-commit-can-continue"),
            field("gate.runtimeFixtureCommitAllowed", "humanReviewGate.runtimeFixtureCommitAllowed",
                "DangerFalseBadge", "false"),
            field("gate.qualityGateStatusGrantedNow",
                "humanReviewGate.manifestQualityGatePreview.qualityGateStatusGrantedNow",
                "DangerFalseBadge", "false"),
            field("readiness.failedQualityGates",
                "catalogPatchReview.reviewedFixtureReadiness.currentTraceSetFailedQualityGates",
                "TagList", "[]"),
            field("policy.createsFixtureFile", "humanReviewGate.gatePolicy.createsFixtureFile",
                "DangerFalseBadge", "false"),
            field("policy.runtimeCatalogWrite", "humanReviewGate.gatePolicy.runtimeCatalogWrite",
                "DangerFalseBadge", "false"),
            field("privacy.redactedOnly", "privacy.redactedOnly", "EvidenceBadge", "true")
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

    private static List<Map<String, Object>> buildWorkflowStages() {
        return List.of(
            stage("capability-discovery", "GET capabilities and overview read models.", false),
            stage("candidate-workbench", "Open auto candidate workbench for one trace set.", false),
            stage("human-review-package", "Render candidate draft and manual review checklist.", false),
            stage("human-review-gate", "POST human fields for validate-only digest check.", false),
            stage("manual-git-fixture-commit", "Human commits reviewed fixture JSON outside runtime.", true),
            stage("manifest-rescan", "Rerun reviewed fixture manifest after Git commit.", false),
            stage("catalog-patch-review", "Review catalog patch only after manifest passes.", false),
            stage("release-review", "Keep CI/release disabled until separate reviewed release gate.", true)
        );
    }

    private static Map<String, Object> stage(String id, String description, boolean outsideRuntimeOnly) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("id", id);
        stage.put("description", description);
        stage.put("outsideRuntimeOnly", outsideRuntimeOnly);
        stage.put("runtimeControlAllowed", false);
        stage.put("writesBackendState", false);
        return Map.copyOf(stage);
    }

    private static List<Map<String, Object>> buildStateRenderingRules() {
        return List.of(
            stateRule("VUE_BINDING_SPEC_READY", "success", "规格可用于前端实现，但不代表 runtime action 可用。"),
            stateRule("NEEDS_REDACTED_EVIDENCE", "warning", "需要真实 redacted audit 证据。"),
            stateRule("READY_FOR_HUMAN_GIT_REVIEW_PACKAGE", "info", "只表示可进入人工 Git review 包。"),
            stateRule("READY_FOR_MANUAL_GIT_FIXTURE_COMMIT", "success",
                "只表示人工 Git 提交可继续，运行时写入仍关闭。"),
            stateRule("HUMAN_REVIEW_GATE_REWORK_REQUIRED", "warning", "人审字段或摘要需要返工。"),
            stateRule("FIXTURE_NEEDS_REVIEW_REWORK", "danger", "manifest 质量门未过。"),
            stateRule("QUALITY_GATE_STATUS_GRANTED_NOW_FALSE", "danger", "前端必须显示未授予质量门。")
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

    private static List<Map<String, Object>> buildDisabledActionBindings() {
        return List.of(
            disabledAction("create-fixture-json-from-browser"),
            disabledAction("upload-reviewed-fixture"),
            disabledAction("write-eval-trace-sets-json"),
            disabledAction("grant-quality-gate-status-now"),
            disabledAction("enable-ci-blocking"),
            disabledAction("approve-release-from-workbench"),
            disabledAction("call-mcp-tools-call"),
            disabledAction("call-kube-manager-write"),
            disabledAction("invoke-hitl-from-review-gate"),
            disabledAction("run-llm-eval"),
            disabledAction("execute-retrieval-or-vector-runtime")
        );
    }

    private static Map<String, Object> disabledAction(String actionId) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("actionId", actionId);
        action.put("renderAs", "absent-or-disabled-row");
        action.put("buttonVisible", false);
        action.put("clickHandlerAllowed", false);
        action.put("runtimeControlAllowed", false);
        action.put("requiresSeparateReviewedSlice", true);
        return Map.copyOf(action);
    }

    private static List<Map<String, Object>> buildTestFixtures() {
        return List.of(
            fixture("overview-renders-gate-path", "Trace set rows render candidate/package/gate paths.",
                Map.of("requiredField", "reviewedFixtureHumanReviewGatePath")),
            fixture("package-renders-manual-fields", "Human review package renders required manual fields.",
                Map.of("requiredFields", List.of("sourceCommitSha", "reviewer", "reviewTimestamp",
                    "evidenceDigest"))),
            fixture("gate-success-does-not-enable-runtime-write",
                "Gate success still renders runtimeFixtureCommitAllowed=false.",
                Map.of("readyForFixtureCommit", true, "runtimeFixtureCommitAllowed", false)),
            fixture("failed-gate-redacts-caller-input",
                "Mismatched caller trace or unsafe reviewer must not leak token/password-like text.",
                Map.of("containsToken", false, "containsPassword", false)),
            fixture("runtime-actions-absent", "All dangerous runtime actions are absent or disabled.",
                Map.of("buttonVisible", false, "clickHandlerAllowed", false)),
            fixture("raw-read-model-json-is-read-only", "JSON drill-down is read-only and omits raw replay/report rows.",
                Map.of("inlineEditAllowed", false, "containsFixtureRows", false))
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
            "fetch-reviewed-fixture-vue-binding-spec-with-admin-session",
            "render-workflow-summary-before-trace-set-table",
            "render-candidate-workbench-package-and-gate-as-read-only-sections",
            "render-gate-success-as-manual-git-signal-not-runtime-write",
            "render-manifest-readiness-and-failed-quality-gates",
            "hide-fixture-upload-catalog-write-ci-release-and-mcp-tools-call-buttons",
            "add-mocked-fixtures-for-gate-success-gate-rework-and-no-candidate",
            "assert-no-token-password-raw-replay-report-fixtureRows-in-rendered-output"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("reviewedFixtureVueBindingSpec", ENDPOINT);
        endpoints.put("workbenchCapabilities", "/api/agent/observability/eval/workbench/capabilities");
        endpoints.put("workbenchOverview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("candidateWorkbench",
            AgentReviewedTraceFixtureCandidateWorkbenchResponse.ENDPOINT_TEMPLATE + "?limit={limit}");
        endpoints.put("humanReviewPackage",
            AgentReviewedTraceFixtureHumanReviewPackageResponse.ENDPOINT_TEMPLATE + "?limit={limit}");
        endpoints.put("humanReviewGate", AgentReviewedTraceFixtureHumanReviewGateResponse.ENDPOINT_TEMPLATE);
        endpoints.put("fixtureManifest", AgentReviewedTraceFixtureManifestResponse.ENDPOINT);
        endpoints.put("catalogPatchReview",
            "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> bindingPolicy(AgentEvalWorkbenchCapabilitiesResponse capabilities,
                                                     AgentEvalWorkbenchOverviewResponse overview,
                                                     List<Map<String, Object>> components,
                                                     List<Map<String, Object>> fields,
                                                     List<Map<String, Object>> stages,
                                                     List<Map<String, Object>> disabledActions,
                                                     List<Map<String, Object>> fixtures) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("bindingSpecOnly", true);
        policy.put("vueWorkbenchOnly", true);
        policy.put("sourceCapabilitiesEmbedded", capabilities != null);
        policy.put("sourceOverviewEmbedded", overview != null);
        policy.put("componentSpecCount", components.size());
        policy.put("fieldBindingCount", fields.size());
        policy.put("workflowStageCount", stages.size());
        policy.put("disabledActionBindingCount", disabledActions.size());
        policy.put("fixtureCount", fixtures.size());
        policy.put("runtimeControlAllowed", false);
        policy.put("runtimeButtonsAllowed", false);
        policy.put("fixtureUploadAccepted", false);
        policy.put("catalogMutationAllowed", false);
        // 该绑定规范只告诉 Vue 如何渲染 reviewed fixture 工作台，不能被误用为运行时 catalog 写入口。
        policy.put("runtimeCatalogWrite", false);
        policy.put("ciBlockingEnabled", false);
        policy.put("releaseAuthority", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety(AgentEvalWorkbenchCapabilitiesResponse capabilities,
                                                   AgentEvalWorkbenchOverviewResponse overview) {
        Map<String, Object> capabilityPolicy = capabilities != null ? capabilities.workbenchPolicy() : Map.of();
        Map<String, Object> overviewPolicy = overview != null ? overview.workbenchPolicy() : Map.of();
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("bindingSpecOnly", true);
        safety.put("sourceCapabilitiesReadOnly", bool(capabilityPolicy, "runtimeCatalogWrite") == false);
        safety.put("sourceOverviewReadOnly", bool(overviewPolicy, "runtimeCatalogWrite") == false);
        safety.put("callerTraceIdsAccepted", false);
        safety.put("fixtureUploadAccepted", false);
        safety.put("createsFixtureFile", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("mcpToolCall", false);
        safety.put("kubeManagerCalls", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("memoryWrite", false);
        safety.put("hitlInvocation", false);
        safety.put("ciBlockingEnabled", false);
        safety.put("releaseAuthority", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(AgentEvalWorkbenchCapabilitiesResponse capabilities,
                                                    AgentEvalWorkbenchOverviewResponse overview) {
        Map<String, Object> capabilityPrivacy = capabilities != null ? capabilities.privacy() : Map.of();
        Map<String, Object> overviewPrivacy = overview != null ? overview.privacy() : Map.of();
        boolean containsRawPrincipal = bool(capabilityPrivacy, "containsRawPrincipal")
            || bool(overviewPrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = bool(capabilityPrivacy, "containsRawOrganization")
            || bool(overviewPrivacy, "containsRawOrganization");
        boolean containsRawConversation = bool(capabilityPrivacy, "containsRawConversation")
            || bool(overviewPrivacy, "containsRawConversation");
        boolean containsRawEndpoints = bool(capabilityPrivacy, "containsRawEndpoints")
            || bool(overviewPrivacy, "containsRawEndpoints");
        boolean containsRawParameterValues = bool(capabilityPrivacy, "containsRawParameterValues")
            || bool(overviewPrivacy, "containsRawParameterValues");
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !containsRawPrincipal
            && !containsRawOrganization
            && !containsRawConversation
            && !containsRawEndpoints
            && !containsRawParameterValues);
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", containsRawOrganization);
        privacy.put("containsRawConversation", containsRawConversation);
        privacy.put("containsRawEndpoints", containsRawEndpoints);
        privacy.put("containsRawParameterValues", containsRawParameterValues);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawDocument", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
