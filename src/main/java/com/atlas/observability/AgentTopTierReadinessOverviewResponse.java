package com.atlas.observability;

import com.atlas.mcp.McpGovernanceOverviewResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 top-tier Agent readiness overview.
 *
 * <p>中文说明：这是一期顶级 Agent 的总控面契约。它把安全执行、身份、审计、
 * 评测、MCP、kube-manager 治理和学习文档等能力放到同一个只读视图里，帮助开发者
 * 判断“离顶级 Agent 还差什么”。它不是运行时开关，也不会执行 Tool。</p>
 */
public record AgentTopTierReadinessOverviewResponse(
    String schemaVersion,
    Instant generatedAt,
    String phase,
    String readinessVerdict,
    String target,
    int capabilityCardCount,
    int readyCardCount,
    int partialCardCount,
    int blockedCardCount,
    int phase2PausedCardCount,
    boolean phase1TopTierGoalPreserved,
    boolean writeAuthorityClosed,
    boolean toolExecutionTriggered,
    boolean kubeManagerCalls,
    boolean llmUsed,
    List<Map<String, Object>> capabilityCards,
    List<String> recommendedBuildOrder,
    List<String> topGaps,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy,
    AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse kubeManagerGovernance,
    AgentEvalWorkbenchCapabilitiesResponse evalWorkbenchCapabilities,
    McpGovernanceOverviewResponse mcpGovernance
) {

    public static final String SCHEMA_VERSION = "agent-top-tier-readiness-overview.v1";

    public static AgentTopTierReadinessOverviewResponse of(
        Instant generatedAt,
        AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse kubeManagerGovernance,
        AgentEvalWorkbenchCapabilitiesResponse evalWorkbenchCapabilities,
        McpGovernanceOverviewResponse mcpGovernance
    ) {
        List<Map<String, Object>> cards = capabilityCards(
            kubeManagerGovernance,
            evalWorkbenchCapabilities,
            mcpGovernance
        );
        int ready = countByStatus(cards, "READY");
        int partial = countByStatus(cards, "PARTIAL");
        int blocked = countByStatus(cards, "BLOCKED");
        int phase2Paused = countByStatus(cards, "PHASE2_PAUSED");
        boolean writeClosed = kubeManagerGovernance == null
            || (!kubeManagerGovernance.releaseGateOpen()
            && !kubeManagerGovernance.writeRetryEnabled()
            && !kubeManagerGovernance.automaticWriteRetryAllowed());
        return new AgentTopTierReadinessOverviewResponse(
            SCHEMA_VERSION,
            generatedAt,
            "PHASE_1_GENERIC_MANAGER_AGENT_CORE",
            readinessVerdict(blocked, partial, phase2Paused, writeClosed),
            "top-tier kube-manager Agent core and learning platform",
            cards.size(),
            ready,
            partial,
            blocked,
            phase2Paused,
            true,
            writeClosed,
            false,
            false,
            false,
            cards,
            buildRecommendedBuildOrder(),
            topGaps(cards),
            buildEndpointMap(),
            safetyProof(writeClosed),
            privacyProof(),
            kubeManagerGovernance,
            evalWorkbenchCapabilities,
            mcpGovernance
        );
    }

    private static List<Map<String, Object>> capabilityCards(
        AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse kubeManagerGovernance,
        AgentEvalWorkbenchCapabilitiesResponse evalWorkbenchCapabilities,
        McpGovernanceOverviewResponse mcpGovernance
    ) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card(
            "identity-security",
            "Trusted identity and endpoint security",
            "READY",
            "Spring Security principal bridge protects Agent APIs; observability remains admin-only.",
            "/api/agent/observability/snapshot",
            List.of("Spring Security", "AgentPrincipalResolver", "method-level admin guard"),
            Map.of(
                "authenticatedAgentApiDefault", true,
                "adminOnlyObservability", true,
                "rawAuthTokenInCredentials", false
            )
        ));
        cards.add(card(
            "safe-tool-execution",
            "SafeToolExecutor-only execution boundary",
            "READY",
            "Tool execution authority is centralized behind SafeToolExecutor, HITL, protected params, and durable audit readiness.",
            "/api/agent/observability/snapshot",
            List.of("SafeToolExecutor", "HITL guard", "protected parameter filter", "durable prewrite gate"),
            Map.of(
                "directBaseToolExecuteAllowed", false,
                "highRiskDurablePrewriteRequired", true,
                "toolExecutionTriggeredByOverview", false
            )
        ));
        cards.add(card(
            "trace-audit-replay",
            "Trace, audit, and replay evidence",
            "READY",
            "Trace-aware audit, durable JSONL option, redacted query, replay timeline, and telemetry projection are present.",
            "/api/agent/observability/replay/trace/{traceId}",
            List.of("AgentTraceContext", "AgentAuditRecorder", "JsonlAgentAuditQueryService", "AgentReplayTimelineService"),
            Map.of(
                "redactedAuditQuery", true,
                "replayTimeline", true,
                "rawPrincipalExposed", false
            )
        ));
        cards.add(card(
            "advanced-technology-adoption",
            "Advanced Agent technology adoption gate",
            "READY",
            "Phase 1 keeps the Java/Spring control plane stable while tracking official Responses/Agents, Spring AI, MCP, OTel GenAI, A2A, OWASP LLM, and advanced RAG sources in a compatibility matrix.",
            "/api/agent/observability/top-tier/advanced-technology-adoption-contract",
            List.of("Java/Spring control plane", "official source watch", "compatibility matrix",
                "Responses/Agents mapping", "MCP runtime matrix", "OTel GenAI adapter", "A2A artifacts"),
            Map.of(
                "adoptionContractExists", true,
                "compatibilityMatrixEndpoint", AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT,
                "officialVersionProtocolWatchExists", true,
                "officialVersionProtocolWatchEndpoint", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT,
                "officialVersionProtocolWatchDashboardEndpoint",
                AgentOfficialVersionProtocolWatchDashboardResponse.DASHBOARD_ENDPOINT,
                "officialVersionProtocolWatchVueBindingSpecEndpoint",
                AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT,
                "javaSpringControlPlanePreserved", true,
                "runtimeUpgradePerformed", false,
                "dependencyUpgradePerformed", false,
                "phase2DomainPluginsPaused", true
            )
        ));
        cards.add(card(
            "eval-release-gates",
            "Deterministic eval and release-gate evidence",
            evalWorkbenchCapabilities != null && evalWorkbenchCapabilities.capabilityCount() > 0 ? "PARTIAL" : "BLOCKED",
            "Eval suites, trace sets, gate artifacts, and Vue-ready workbench contracts exist; real curated trace evidence is still required before CI can become blocking.",
            "/api/agent/observability/eval/workbench/capabilities",
            List.of("deterministic eval", "trace-set catalog", "gate bundle", "catalog patch review"),
            Map.of(
                "capabilityCount", evalWorkbenchCapabilities != null ? evalWorkbenchCapabilities.capabilityCount() : 0,
                "reviewedTraceEvidenceContractExists", true,
                "releaseBlockingEvalGateContractExists", true,
                "ciBlockingEnabled", false,
                "needsReviewedCuratedTraceIds", true
            )
        ));
        cards.add(card(
            "kube-manager-http-governance",
            "Kube-manager HTTP outlet governance",
            kubeManagerStatus(kubeManagerGovernance),
            "Read-side resilience exists and write authority stays closed behind idempotency, safety, retry governance, and release gate contracts.",
            "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview",
            List.of("Resilience4j", "idempotency contract", "operation safety contract", "release gate contract"),
            Map.of(
                "workbenchStatus",
                kubeManagerGovernance != null ? kubeManagerGovernance.workbenchStatus() : "MISSING",
                "releaseGateOpen",
                kubeManagerGovernance != null && kubeManagerGovernance.releaseGateOpen(),
                "writeRetryEnabled",
                kubeManagerGovernance != null && kubeManagerGovernance.writeRetryEnabled(),
                "blockingCardCount",
                kubeManagerGovernance != null ? kubeManagerGovernance.blockingCardCount() : 0
            )
        ));
        cards.add(card(
            "mcp-interoperability",
            "MCP interoperability governance",
            mcpGovernance != null && "MANIFEST_ONLY_NOT_CALLABLE".equals(mcpGovernance.governanceStatus())
                ? "PARTIAL"
                : "BLOCKED",
            "Safe manifest and governance overview exist; runtime MCP server and tools/call remain blocked until a reviewed release binds safety evidence.",
            "/api/agent/mcp/governance/overview",
            List.of("safe manifest", "MCP governance overview", "future SafeToolExecutor binding"),
            Map.of(
                "governanceStatus", mcpGovernance != null ? mcpGovernance.governanceStatus() : "MISSING",
                "exportedToolCount", mcpGovernance != null ? mcpGovernance.exportedToolCount() : 0,
                "toolsCallEnabled", mcpGovernance != null && mcpGovernance.toolsCallEnabled(),
                "mcpServerRuntimeEnabled", mcpGovernance != null && mcpGovernance.mcpServerRuntimeEnabled()
            )
        ));
        cards.add(card(
            "memory-rag-learning",
            "Persistent Memory and RAG learning layer",
            "BLOCKED",
            "Safe summary memory, readiness, citation/source, source evidence digest, durable lifecycle, eval gate, and eval-suite binding contracts exist, but Phase 1 still needs runtime durable store binding, retrieval binding, and curated eval coverage.",
            "/api/agent/observability/memory-rag/readiness",
            List.of("conversation summary memory", "Memory/RAG readiness contract", "source evidence digest contract", "durable lifecycle contract", "eval gate contract", "eval-suite binding contract", "future vector store"),
            Map.ofEntries(
                Map.entry("readinessContractExists", true),
                Map.entry("durableMemoryImplemented", false),
                Map.entry("durableMemoryLifecycleContractImplemented", true),
                Map.entry("durableMemoryLifecycleContractBound", false),
                Map.entry("memoryRagEvalGateContractImplemented", true),
                Map.entry("memoryRagEvalGateContractBound", false),
                Map.entry("memoryRagEvalSuiteBindingContractImplemented", true),
                Map.entry("memoryRagEvalSuiteBindingContractBound", false),
                Map.entry("vectorRetrievalImplemented", false),
                Map.entry("citationContractImplemented", true),
                Map.entry("sourceEvidenceDigestContractImplemented", true),
                Map.entry("sourceEvidenceDigestBoundToRuntime", false)
            )
        ));
        cards.add(card(
            "vue-operator-workbench",
            "Vue operator workbench integration",
            "PARTIAL",
            "Backend page contracts exist for eval and kube-manager governance; frontend implementation still needs to consume the full readiness/control-plane map.",
            "/api/agent/observability/top-tier/readiness-overview",
            List.of("eval workbench contracts", "kube-manager governance workbench", "top-tier readiness overview"),
            Map.of(
                "backendContractsExist", true,
                "frontendConsumptionVerified", false,
                "runtimeControlButtonsAllowed", false
            )
        ));
        cards.add(card(
            "phase2-domain-plugins",
            "NIM, HPC, Slurm, and BCM specialist plugins",
            "PHASE2_PAUSED",
            "Specialist domain plugins are intentionally paused for Phase 2; Phase 1 top-tier standards remain unchanged.",
            "",
            List.of("NIM", "HPC", "Slurm", "BCM"),
            Map.of(
                "phase2Paused", true,
                "reducesPhase1QualityBar", false,
                "realStateChangingCallsAllowedNow", false
            )
        ));
        return List.copyOf(cards);
    }

    private static Map<String, Object> card(String id,
                                            String title,
                                            String status,
                                            String summary,
                                            String endpoint,
                                            List<String> technologyPoints,
                                            Map<String, Object> evidence) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("title", title);
        card.put("status", status);
        card.put("summary", summary);
        card.put("endpoint", endpoint);
        card.put("technologyPoints", List.copyOf(technologyPoints));
        card.put("evidence", Map.copyOf(evidence));
        card.put("readOnly", true);
        card.put("runtimeMutationAllowed", false);
        card.put("toolExecution", false);
        card.put("kubeManagerCalls", false);
        card.put("llmUsed", false);
        return Map.copyOf(card);
    }

    private static String kubeManagerStatus(AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse overview) {
        if (overview == null) {
            return "BLOCKED";
        }
        if (overview.releaseGateOpen() || overview.writeRetryEnabled() || overview.automaticWriteRetryAllowed()) {
            return "BLOCKED";
        }
        return overview.blockingCardCount() > 0 ? "PARTIAL" : "READY";
    }

    private static int countByStatus(List<Map<String, Object>> cards, String status) {
        return (int) cards.stream()
            .filter(card -> status.equals(card.get("status")))
            .count();
    }

    private static String readinessVerdict(int blocked, int partial, int phase2Paused, boolean writeClosed) {
        if (!writeClosed) {
            return "UNEXPECTED_RUNTIME_WRITE_AUTHORITY";
        }
        if (blocked > 0) {
            return "PHASE_1_TOP_TIER_CORE_IN_PROGRESS";
        }
        if (partial > 0 || phase2Paused > 0) {
            return "PHASE_1_CORE_READY_FOR_FRONTEND_AND_EVIDENCE_HARDENING";
        }
        return "PHASE_1_TOP_TIER_CORE_READY_FOR_RELEASE_REVIEW";
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "wire-vue-top-tier-readiness-overview",
            "wire-vue-advanced-technology-adoption-contract",
            "wire-vue-advanced-technology-compatibility-matrix",
            "wire-vue-official-version-protocol-watch",
            "wire-vue-official-version-protocol-watch-dashboard",
            "wire-vue-official-version-protocol-watch-binding-spec",
            "wire-vue-phase1-execution-roadmap",
            "wire-vue-readiness-control-plane",
            "populate-reviewed-redacted-eval-trace-evidence",
            "promote-eval-gate-bundle-from-evidence-only-to-reviewed-blocking",
            "bind-memory-rag-eval-suite-before-retrieval-runtime",
            "bind-durable-memory-runtime-after-lifecycle-and-source-digest-contract",
            "add-mcp-tools-call-only-after-safe-tool-executor-consent-hitl-audit-eval-binding",
            "keep-nim-hpc-slurm-bcm-paused-until-phase-2"
        );
    }

    private static List<String> topGaps(List<Map<String, Object>> cards) {
        List<String> gaps = new ArrayList<>();
        for (Map<String, Object> card : cards) {
            Object status = card.get("status");
            if ("BLOCKED".equals(status) || "PARTIAL".equals(status)) {
                gaps.add(String.valueOf(card.get("id")));
            }
        }
        return List.copyOf(gaps);
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        endpoints.put("advancedTechnologyCompatibilityMatrix",
            AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT);
        endpoints.put("officialVersionProtocolWatch", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchDashboard",
            AgentOfficialVersionProtocolWatchDashboardResponse.DASHBOARD_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchVueBindingSpec",
            AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("kubeManagerGovernanceOverview", "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview");
        endpoints.put("evalWorkbenchCapabilities", "/api/agent/observability/eval/workbench/capabilities");
        endpoints.put("evalWorkbenchOverview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("releaseBlockingEvalGateContract", "/api/agent/observability/eval/release-blocking-gate-contract");
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        endpoints.put("mcpManifest", "/api/agent/mcp/manifest");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("memoryRagCitationSourceContract", "/api/agent/observability/memory-rag/citation-source-contract");
        endpoints.put("memoryRagSourceEvidenceDigestContract", "/api/agent/observability/memory-rag/source-evidence-digest-contract");
        endpoints.put("memoryRagDurableMemoryLifecycleContract", "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract");
        endpoints.put("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        endpoints.put("memoryRagEvalSuiteBindingContract", "/api/agent/observability/memory-rag/eval-suite-binding-contract");
        endpoints.put("replayTimeline", "/api/agent/observability/replay/trace/{traceId}");
        endpoints.put("memorySummaries", "/api/agent/memory/summaries");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> safetyProof(boolean writeClosed) {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("adminOnlyAppliesToThisEndpoint", true);
        safety.put("readOnly", true);
        safety.put("summaryOnly", true);
        safety.put("endpointMapNavigationOnly", true);
        safety.put("endpointMapDoesNotGrantAccess", true);
        safety.put("endpointAccessMayDiffer", true);
        safety.put("runtimeMutationAllowed", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("kubeManagerCalls", false);
        safety.put("kubeManagerHttpClientBinding", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        safety.put("mcpToolsCall", false);
        safety.put("releaseGateOpen", !writeClosed);
        safety.put("writeAuthorityClosed", writeClosed);
        safety.put("phase2DomainPluginsPaused", true);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> privacyProof() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawConversation", false);
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsRawBackendPath", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("containsRawRequestBody", false);
        privacy.put("containsRawResponseBody", false);
        return Map.copyOf(privacy);
    }
}
