package com.atlas.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vue-ready dashboard for the official Agent technology/protocol watch.
 *
 * <p>中文说明：这个响应给 `vue-kube-manager` 直接渲染官方来源、技术轨道、门禁和阻断项。
 * 它只包装 M5.74 的只读 Watch，不联网、不升级依赖、不打开运行时按钮。</p>
 */
public record AgentOfficialVersionProtocolWatchDashboardResponse(
    String schemaVersion,
    Instant generatedAt,
    String dashboardStatus,
    String frontendTarget,
    boolean phase1TopTierGoalPreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean sourceWatchEmbedded,
    boolean runtimeControlAllowed,
    int sourceCardCount,
    int technologyTrackCardCount,
    int adoptionGateCount,
    int blockedRuntimeShortcutCount,
    List<Map<String, Object>> sourceCards,
    List<Map<String, Object>> technologyTrackCards,
    List<Map<String, Object>> adoptionGateRows,
    List<Map<String, Object>> blockedRuntimeShortcutRows,
    List<Map<String, Object>> disabledRuntimeActions,
    List<Map<String, Object>> renderSections,
    List<String> recommendedWorkflow,
    List<String> nextActions,
    AgentOfficialVersionProtocolWatchResponse sourceWatch,
    Map<String, Object> endpointMap,
    Map<String, Object> dashboardPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION =
        "agent-official-version-protocol-watch-dashboard.v1";
    public static final String DASHBOARD_ENDPOINT =
        "/api/agent/observability/top-tier/official-version-protocol-watch/dashboard";

    public static AgentOfficialVersionProtocolWatchDashboardResponse of(
        Instant generatedAt,
        AgentOfficialVersionProtocolWatchResponse sourceWatch
    ) {
        List<Map<String, Object>> sources = sourceCards(sourceWatch);
        List<Map<String, Object>> tracks = technologyTrackCards(sourceWatch);
        List<Map<String, Object>> gates = adoptionGateRows(sourceWatch);
        List<Map<String, Object>> shortcuts = blockedRuntimeShortcutRows(sourceWatch);
        List<Map<String, Object>> disabledActions = buildDisabledRuntimeActions();
        return new AgentOfficialVersionProtocolWatchDashboardResponse(
            SCHEMA_VERSION,
            generatedAt,
            dashboardStatus(sourceWatch),
            "vue-kube-manager official Agent technology/protocol watch dashboard",
            sourceWatch != null && sourceWatch.phase1TopTierGoalPreserved(),
            true,
            sourceWatch != null,
            false,
            sources.size(),
            tracks.size(),
            gates.size(),
            shortcuts.size(),
            sources,
            tracks,
            gates,
            shortcuts,
            disabledActions,
            buildRenderSections(),
            buildRecommendedWorkflow(),
            nextActions(sourceWatch),
            sourceWatch,
            buildEndpointMap(),
            dashboardPolicy(sourceWatch, sources, tracks, gates, shortcuts, disabledActions),
            safety(sourceWatch),
            privacy(sourceWatch)
        );
    }

    private static String dashboardStatus(AgentOfficialVersionProtocolWatchResponse sourceWatch) {
        if (sourceWatch == null) {
            return "DASHBOARD_SOURCE_WATCH_MISSING";
        }
        if (sourceWatch.runtimeUpgradePerformed()
            || sourceWatch.dependencyUpgradePerformed()
            || sourceWatch.externalCallsPerformed()) {
            return "UNEXPECTED_RUNTIME_TECHNOLOGY_AUTHORITY";
        }
        return "DASHBOARD_READY_TO_RENDER_OFFICIAL_WATCH";
    }

    private static List<Map<String, Object>> sourceCards(
        AgentOfficialVersionProtocolWatchResponse sourceWatch
    ) {
        if (sourceWatch == null) {
            return List.of();
        }
        return sourceWatch.officialSources().stream()
            .map(AgentOfficialVersionProtocolWatchDashboardResponse::sourceCard)
            .toList();
    }

    private static Map<String, Object> sourceCard(Map<String, Object> source) {
        String id = string(source, "id");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("title", string(source, "title"));
        card.put("status", "OFFICIAL_SOURCE_REVIEWED");
        card.put("severity", "INFO");
        card.put("sourceType", string(source, "sourceType"));
        card.put("officialUrl", string(source, "officialUrl"));
        card.put("sourceReviewDate", string(source, "sourceReviewDate"));
        card.put("currentFinding", string(source, "currentFinding"));
        card.put("adoptionMode", string(source, "adoptionMode"));
        card.put("runtimeBound", bool(source, "runtimeBound"));
        card.put("renderHints", renderHints("book-open", "official-source", false));
        card.put("evidence", Map.of(
            "officialSourceOnly", true,
            "requiresGitReviewToChange", true,
            "runtimeAuthorityGranted", false
        ));
        card.put("readOnly", true);
        card.put("externalNavigationOnly", true);
        card.put("runtimeControlAllowed", false);
        card.put("buttonVisibleNow", false);
        return Map.copyOf(card);
    }

    private static List<Map<String, Object>> technologyTrackCards(
        AgentOfficialVersionProtocolWatchResponse sourceWatch
    ) {
        if (sourceWatch == null) {
            return List.of();
        }
        return sourceWatch.technologyTracks().stream()
            .map(AgentOfficialVersionProtocolWatchDashboardResponse::technologyTrackCard)
            .toList();
    }

    private static Map<String, Object> technologyTrackCard(Map<String, Object> track) {
        String adoptionDecision = string(track, "adoptionDecision");
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", string(track, "id"));
        card.put("title", title(string(track, "id")));
        card.put("status", adoptionDecision);
        card.put("severity", "RUNTIME_BLOCKED_UNTIL_EVIDENCE".equals(adoptionDecision) ? "BLOCKING" : "INFO");
        card.put("phase1Interpretation", string(track, "phase1Interpretation"));
        card.put("beforeRuntimeEvidence", stringList(track.get("beforeRuntimeEvidence")));
        card.put("localAnchors", stringList(track.get("localAnchors")));
        card.put("phase1Scope", bool(track, "phase1Scope"));
        card.put("runtimeBound", bool(track, "runtimeBound"));
        card.put("requiresGitReview", bool(track, "requiresGitReview"));
        card.put("renderHints", renderHints("route", "technology-track", false));
        card.put("disabledRuntimeActions", disabledActionsForTrack(string(track, "id")));
        card.put("readOnly", true);
        card.put("frontendNavigationOnly", true);
        card.put("runtimeControlAllowed", false);
        card.put("toolExecution", false);
        card.put("kubeManagerCalls", false);
        card.put("llmUsed", false);
        return Map.copyOf(card);
    }

    private static List<Map<String, Object>> adoptionGateRows(
        AgentOfficialVersionProtocolWatchResponse sourceWatch
    ) {
        if (sourceWatch == null) {
            return List.of();
        }
        return sourceWatch.adoptionGates().stream()
            .map(gate -> gateRow(gate, "REQUIRED_GATE"))
            .toList();
    }

    private static Map<String, Object> gateRow(Map<String, Object> gate, String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", string(gate, "id"));
        row.put("status", status);
        row.put("summary", string(gate, "summary"));
        row.put("required", bool(gate, "required"));
        row.put("runtimeBound", bool(gate, "runtimeBound"));
        row.put("readOnly", true);
        row.put("runtimeControlAllowed", false);
        row.put("renderHints", renderHints("shield-check", "adoption-gate", false));
        return Map.copyOf(row);
    }

    private static List<Map<String, Object>> blockedRuntimeShortcutRows(
        AgentOfficialVersionProtocolWatchResponse sourceWatch
    ) {
        if (sourceWatch == null) {
            return List.of();
        }
        return sourceWatch.blockedRuntimeShortcuts().stream()
            .map(AgentOfficialVersionProtocolWatchDashboardResponse::blockedShortcutRow)
            .toList();
    }

    private static Map<String, Object> blockedShortcutRow(Map<String, Object> shortcut) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", string(shortcut, "id"));
        row.put("status", "BLOCKED_SHORTCUT");
        row.put("summary", string(shortcut, "summary"));
        row.put("allowed", bool(shortcut, "allowed"));
        row.put("blocksTopTierClaim", bool(shortcut, "blocksTopTierClaim"));
        row.put("readOnly", true);
        row.put("runtimeControlAllowed", false);
        row.put("renderHints", renderHints("ban", "blocked-shortcut", false));
        return Map.copyOf(row);
    }

    private static List<Map<String, Object>> buildDisabledRuntimeActions() {
        return List.of(
            disabledAction("upgrade-dependencies-from-dashboard", "Dependency upgrades require compatibility matrix tests and Git review."),
            disabledAction("enable-mcp-tools-call", "MCP tools/call requires SafeToolExecutor, identity, consent, HITL, audit, eval, and release gates."),
            disabledAction("enable-a2a-runtime-handoff", "A2A handoff requires artifact provenance, local authority proof, trace/audit bridge, and eval coverage."),
            disabledAction("enable-retrieval-runtime", "Retrieval requires reviewed Memory/RAG trace evidence, source custody, tenant isolation, citation, and lifecycle gates."),
            disabledAction("enable-ci-blocking", "CI blocking requires reviewed real trace evidence and a separate release-gate slice."),
            disabledAction("reopen-phase2-domain-plugins", "NIM, HPC, Slurm, and BCM remain Phase 2 scope.")
        );
    }

    private static List<Map<String, Object>> disabledActionsForTrack(String trackId) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (trackId.contains("mcp")) {
            actions.add(disabledAction("run-mcp-tools-call", "The dashboard cannot execute MCP tools/call."));
        }
        if (trackId.contains("a2a")) {
            actions.add(disabledAction("run-a2a-handoff", "The dashboard cannot perform Agent-to-Agent runtime handoff."));
        }
        if (trackId.contains("rag") || trackId.contains("vector")) {
            actions.add(disabledAction("run-retrieval", "The dashboard cannot execute retrieval, vector search, reranking, or GraphRAG."));
        }
        if (trackId.contains("openai")) {
            actions.add(disabledAction("call-external-agent-runtime", "The dashboard cannot call external Agent runtimes or LLM APIs."));
        }
        if (actions.isEmpty()) {
            actions.add(disabledAction("open-runtime-control", "The dashboard is read-only and cannot grant runtime authority."));
        }
        return List.copyOf(actions);
    }

    private static Map<String, Object> disabledAction(String id, String disabledReason) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", id);
        action.put("enabledNow", false);
        action.put("buttonVisibleNow", false);
        action.put("disabledReason", disabledReason);
        action.put("requiresSeparateReviewedSlice", true);
        return Map.copyOf(action);
    }

    private static List<Map<String, Object>> buildRenderSections() {
        return List.of(
            section("official-sources", "Official sources", "sourceCards", "book-open"),
            section("technology-tracks", "Technology tracks", "technologyTrackCards", "route"),
            section("adoption-gates", "Adoption gates", "adoptionGateRows", "shield-check"),
            section("blocked-shortcuts", "Blocked shortcuts", "blockedRuntimeShortcutRows", "ban")
        );
    }

    private static Map<String, Object> section(String id,
                                               String title,
                                               String dataField,
                                               String icon) {
        Map<String, Object> section = new LinkedHashMap<>();
        section.put("id", id);
        section.put("title", title);
        section.put("dataField", dataField);
        section.put("icon", icon);
        section.put("emptyStateAllowed", false);
        section.put("runtimeControlAllowed", false);
        return Map.copyOf(section);
    }

    private static List<String> buildRecommendedWorkflow() {
        return List.of(
            "official-version-protocol-watch-dashboard",
            "advanced-technology-adoption-contract",
            "phase1-execution-roadmap",
            "vue-readiness-control-plane",
            "review-official-source-update-through-git",
            "add-compatibility-matrix-tests-before-upgrades",
            "bind-runtime-only-after-reviewed-evidence-and-release-gates"
        );
    }

    private static List<String> nextActions(AgentOfficialVersionProtocolWatchResponse sourceWatch) {
        List<String> actions = new ArrayList<>();
        if (sourceWatch == null) {
            actions.add("restore-official-version-protocol-watch");
            return List.copyOf(actions);
        }
        actions.add("wire-vue-dashboard-to-render-official-source-cards");
        actions.add("wire-vue-dashboard-to-render-technology-track-cards");
        actions.add("hide-runtime-enable-buttons-for-all-watch-items");
        actions.add("continue-reviewed-eval-and-memory-rag-trace-evidence-curation");
        actions.add("keep-nim-hpc-slurm-bcm-paused-for-phase2");
        return List.copyOf(actions);
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("officialVersionProtocolWatchDashboard", DASHBOARD_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchVueBindingSpec",
            AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("officialVersionProtocolWatch", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT);
        endpoints.put("advancedTechnologyAdoptionContract",
            "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("memoryRagReviewedTraceEvidenceManifest",
            AgentMemoryRagReviewedTraceEvidenceManifestResponse.MANIFEST_ENDPOINT);
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> dashboardPolicy(
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        List<Map<String, Object>> sourceCards,
        List<Map<String, Object>> technologyTrackCards,
        List<Map<String, Object>> adoptionGateRows,
        List<Map<String, Object>> blockedRuntimeShortcutRows,
        List<Map<String, Object>> disabledRuntimeActions
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager official Agent technology/protocol watch dashboard");
        policy.put("dashboardOnly", true);
        policy.put("readOnly", true);
        policy.put("vueWorkbenchOnly", true);
        policy.put("sourceWatchEmbedded", sourceWatch != null);
        policy.put("sourceCardCount", sourceCards.size());
        policy.put("technologyTrackCardCount", technologyTrackCards.size());
        policy.put("adoptionGateCount", adoptionGateRows.size());
        policy.put("blockedRuntimeShortcutCount", blockedRuntimeShortcutRows.size());
        policy.put("disabledRuntimeActionCount", disabledRuntimeActions.size());
        policy.put("runtimeControlAllowed", false);
        policy.put("dependencyUpgradeAllowed", false);
        policy.put("runtimeUpgradeAllowed", false);
        policy.put("mcpToolsCallAllowed", false);
        policy.put("a2aRuntimeHandoffAllowed", false);
        policy.put("retrievalRuntimeAllowed", false);
        policy.put("ciBlockingAllowed", false);
        policy.put("requiresGitReview", true);
        policy.put("requiresCompatibilityMatrixBeforeUpgrade", true);
        policy.put("phase2NimHpcSlurmBcmPaused", true);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> safety(AgentOfficialVersionProtocolWatchResponse sourceWatch) {
        Map<String, Object> sourceSafety = sourceWatch != null ? sourceWatch.safety() : Map.of();
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("dashboardOnly", true);
        safety.put("vueWorkbenchOnly", true);
        safety.put("sourceWatchReadOnly", bool(sourceSafety, "readOnly"));
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

    private static Map<String, Object> privacy(AgentOfficialVersionProtocolWatchResponse sourceWatch) {
        Map<String, Object> sourcePrivacy = sourceWatch != null ? sourceWatch.privacy() : Map.of();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", bool(sourcePrivacy, "containsRawPrincipal"));
        privacy.put("containsRawOrganization", bool(sourcePrivacy, "containsRawOrganization"));
        privacy.put("containsRawConversation", bool(sourcePrivacy, "containsRawConversation"));
        privacy.put("containsRawPrompt", bool(sourcePrivacy, "containsRawPrompt"));
        privacy.put("containsRawDocument", bool(sourcePrivacy, "containsRawDocument"));
        privacy.put("containsAuthorizationHeader", bool(sourcePrivacy, "containsAuthorizationHeader"));
        privacy.put("containsToken", bool(sourcePrivacy, "containsToken"));
        privacy.put("containsPassword", bool(sourcePrivacy, "containsPassword"));
        privacy.put("containsRuntimeSecrets", bool(sourcePrivacy, "containsRuntimeSecrets"));
        privacy.put("containsInternalEndpointSecrets", bool(sourcePrivacy, "containsInternalEndpointSecrets"));
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> renderHints(String icon, String category, boolean showRuntimeButton) {
        Map<String, Object> hints = new LinkedHashMap<>();
        hints.put("icon", icon);
        hints.put("category", category);
        hints.put("showRuntimeButton", showRuntimeButton);
        hints.put("showExternalLink", "official-source".equals(category));
        hints.put("showEvidenceList", true);
        hints.put("allowInlineEdit", false);
        return Map.copyOf(hints);
    }

    private static String title(String id) {
        return switch (id) {
            case "java-spring-governed-control-plane" -> "Java/Spring governed control plane";
            case "spring-ai-memory-rag-mcp" -> "Spring AI Memory/RAG/MCP adoption";
            case "openai-responses-agents-interop" -> "OpenAI Responses/Agents interop";
            case "mcp-runtime-call-plane" -> "MCP runtime call plane";
            case "a2a-handoff-provenance" -> "A2A handoff provenance";
            case "otel-genai-observability-adapter" -> "OpenTelemetry GenAI adapter";
            case "owasp-llm-risk-controls" -> "OWASP LLM risk controls";
            case "advanced-rag-graphrag-rerankers-vector-stores" -> "Advanced RAG, GraphRAG, rerankers, and vector stores";
            default -> id;
        };
    }

    private static String string(Map<String, Object> data, String key) {
        Object value = data != null ? data.get(key) : null;
        return value != null ? String.valueOf(value) : "";
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
