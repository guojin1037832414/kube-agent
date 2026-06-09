package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend-owned read model for the Phase 1 vue-kube-manager readiness control plane.
 *
 * <p>中文说明：这个契约给前端一份可直接渲染的只读控制面清单。
 * 它只定义页面、接口、禁用动作和安全证明，不创建任何运行时控制按钮。</p>
 */
public record AgentVueReadinessControlPlaneResponse(
    String schemaVersion,
    Instant generatedAt,
    String controlPlaneStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean vueBindingReady,
    boolean runtimeControlAllowed,
    int dashboardCount,
    List<Map<String, Object>> dashboards,
    List<Map<String, Object>> requiredApiBindings,
    List<Map<String, Object>> operatorStates,
    List<String> forbiddenUiActions,
    List<String> recommendedBuildOrder,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-vue-readiness-control-plane.v1";

    public static AgentVueReadinessControlPlaneResponse of(Instant generatedAt) {
        List<Map<String, Object>> dashboards = buildDashboards();
        return new AgentVueReadinessControlPlaneResponse(
            SCHEMA_VERSION,
            generatedAt,
            "BACKEND_CONTRACT_READY_FOR_VUE_BINDING",
            "vue-kube-manager Phase 1 top-tier Agent readiness control plane",
            true,
            true,
            true,
            false,
            dashboards.size(),
            dashboards,
            buildRequiredApiBindings(),
            buildOperatorStates(),
            buildForbiddenUiActions(),
            buildRecommendedBuildOrder(),
            buildEndpointMap(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> buildDashboards() {
        return List.of(
            dashboard("top-tier-command-center", "READY_TO_BIND",
                "Render the overall Phase 1 readiness, gaps, and recommended build order.",
                "/api/agent/observability/top-tier/readiness-overview",
                List.of("capabilityCards", "topGaps", "recommendedBuildOrder", "endpointMap")),
            dashboard("advanced-technology-adoption", "READY_TO_BIND",
                "Show stable mainline technologies, compatibility matrix, gates, and rejected shortcuts.",
                "/api/agent/observability/top-tier/advanced-technology-adoption-contract",
                List.of("mainlineTechnologies", "compatibilityMatrix", "adoptionGates", "rejectedShortcuts")),
            dashboard("phase1-execution-roadmap", "READY_TO_BIND",
                "Render the ordered Phase 1 execution steps and do-not-start-yet boundaries.",
                "/api/agent/observability/top-tier/phase1-execution-roadmap",
                List.of("executionSteps", "dependencyGates", "vueWorkbenchTargets", "doNotStartYet")),
            dashboard("kube-manager-governance", "READY_TO_BIND",
                "Render read-side resilience and write-side blocked release evidence.",
                "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview",
                List.of("governanceCards", "recommendedWorkflow", "nextActions", "workbenchPolicy")),
            dashboard("memory-rag-readiness", "READY_TO_BIND",
                "Render Memory/RAG readiness, citation, source digest, lifecycle, and eval-gate blockers.",
                "/api/agent/observability/memory-rag/readiness",
                List.of("readinessCards", "requiredEvidence", "endpointMap", "safety")),
            dashboard("eval-workbench", "READY_TO_BIND",
                "Render eval workbench overview, trace-set rows, gate bundle state, and promotion entrypoints.",
                "/api/agent/observability/eval/workbench/overview",
                List.of("traceSetRows", "gateBundleSummary", "nextActions", "endpointTemplates")),
            dashboard("mcp-governance", "READY_TO_BIND",
                "Render MCP manifest/governance without exposing runtime tools/call authority.",
                "/api/agent/mcp/governance/overview",
                List.of("governanceCards", "exportPolicy", "runtimePolicy", "endpointMap"))
        );
    }

    private static List<Map<String, Object>> buildRequiredApiBindings() {
        return List.of(
            apiBinding("readiness-overview", "/api/agent/observability/top-tier/readiness-overview", "GET", true),
            apiBinding("advanced-technology-adoption", "/api/agent/observability/top-tier/advanced-technology-adoption-contract", "GET", true),
            apiBinding("phase1-roadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap", "GET", true),
            apiBinding("kube-manager-governance", "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview", "GET", true),
            apiBinding("memory-rag-readiness", "/api/agent/observability/memory-rag/readiness", "GET", true),
            apiBinding("memory-rag-eval-gate", "/api/agent/observability/memory-rag/eval-gate-contract", "GET", true),
            apiBinding("eval-workbench-overview", "/api/agent/observability/eval/workbench/overview", "GET", true),
            apiBinding("eval-gate-bundle-summary", "/api/agent/observability/eval/workbench/gate-bundle-summary", "GET", true),
            apiBinding("mcp-governance", "/api/agent/mcp/governance/overview", "GET", true),
            apiBinding("mcp-manifest", "/api/agent/mcp/manifest", "GET", true)
        );
    }

    private static List<Map<String, Object>> buildOperatorStates() {
        return List.of(
            operatorState("ready", "READY", "The backend read model is safe to render."),
            operatorState("partial", "PARTIAL", "Render the card with blockers and next evidence."),
            operatorState("blocked", "BLOCKED", "Render as blocked; do not show an enable action."),
            operatorState("contract-defined-not-bound", "CONTRACT_DEFINED_NOT_BOUND",
                "Render as a future capability contract, not as an active runtime feature."),
            operatorState("phase2-paused", "PHASE2_PAUSED",
                "Render as intentionally postponed Phase 2 scope.")
        );
    }

    private static List<String> buildForbiddenUiActions() {
        return List.of(
            "enable-kube-manager-write-retry",
            "trigger-kube-manager-state-changing-call",
            "run-mcp-tools-call",
            "run-retrieval-against-prompt",
            "mutate-eval-trace-set-catalog",
            "enable-ci-blocking-from-ui",
            "issue-durable-receipt",
            "invoke-hitl-from-readiness-page",
            "upgrade-backend-dependency-from-ui",
            "reopen-nim-hpc-slurm-bcm-phase2"
        );
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "create-vue-top-tier-agent-navigation",
            "bind-readiness-overview-card-grid",
            "bind-advanced-technology-adoption-matrix",
            "bind-phase1-execution-roadmap-timeline",
            "bind-kube-manager-governance-cards",
            "bind-memory-rag-readiness-and-contract-links",
            "bind-eval-workbench-summary-and-gate-bundle",
            "bind-mcp-governance-manifest-view",
            "keep-runtime-control-buttons-absent"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("kubeManagerGovernanceOverview", "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        endpoints.put("evalWorkbenchOverview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("evalWorkbenchGateBundleSummary", "/api/agent/observability/eval/workbench/gate-bundle-summary");
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        endpoints.put("mcpManifest", "/api/agent/mcp/manifest");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("vueContractOnly", true);
        safety.put("runtimeControlAllowed", false);
        safety.put("runtimeMutationAllowed", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("kubeManagerCalls", false);
        safety.put("mcpToolCall", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        safety.put("memoryWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("dependencyUpgrade", false);
        safety.put("nimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawConversation", false);
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawDocument", false);
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> dashboard(String id,
                                                 String status,
                                                 String summary,
                                                 String primaryEndpoint,
                                                 List<String> renderFields) {
        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("id", id);
        dashboard.put("status", status);
        dashboard.put("summary", summary);
        dashboard.put("primaryEndpoint", primaryEndpoint);
        dashboard.put("renderFields", List.copyOf(renderFields));
        dashboard.put("readOnly", true);
        dashboard.put("runtimeControlAllowed", false);
        return Map.copyOf(dashboard);
    }

    private static Map<String, Object> apiBinding(String id, String path, String method, boolean adminOnly) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("id", id);
        binding.put("path", path);
        binding.put("method", method);
        binding.put("adminOnly", adminOnly);
        binding.put("readOnly", true);
        binding.put("runtimeControlAllowed", false);
        return Map.copyOf(binding);
    }

    private static Map<String, Object> operatorState(String id, String status, String renderingRule) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("id", id);
        state.put("status", status);
        state.put("renderingRule", renderingRule);
        state.put("allowRuntimeAction", false);
        return Map.copyOf(state);
    }
}
