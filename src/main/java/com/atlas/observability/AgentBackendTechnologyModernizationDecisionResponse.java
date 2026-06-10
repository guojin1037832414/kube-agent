package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backend technology modernization decision for the Phase 1 top-tier Agent.
 *
 * <p>Chinese teaching note: this response turns "use all advanced technologies" into an
 * engineering decision model. Java/Spring remains the typed control plane, while major
 * runtime upgrades and external Agent protocols move through compatibility and evidence gates.</p>
 */
public record AgentBackendTechnologyModernizationDecisionResponse(
    String schemaVersion,
    Instant generatedAt,
    String decisionStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean javaBackendStillPreferred,
    boolean javaSpringControlPlanePreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean mainlineRuntimeUpgradeAllowedNow,
    boolean dependencyUpgradeAllowedNow,
    boolean compatibilityBranchAllowed,
    boolean runtimeControlAllowed,
    boolean ciBlockingAllowedNow,
    int officialSourceCount,
    int mainlineDecisionCount,
    int compatibilityLaneCount,
    int blockedCompatibilityLaneCount,
    int modernizationGateCount,
    int blockedShortcutCount,
    int learningStepCount,
    List<Map<String, Object>> officialSourceBaselines,
    List<Map<String, Object>> mainlineDecisions,
    List<Map<String, Object>> compatibilityLaneDecisions,
    List<Map<String, Object>> modernizationGates,
    List<Map<String, Object>> blockedShortcuts,
    List<Map<String, Object>> learningPath,
    List<String> recommendedImplementationOrder,
    AgentOfficialVersionProtocolWatchResponse sourceWatch,
    AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
    Map<String, Object> endpointMap,
    Map<String, Object> decisionPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-backend-technology-modernization-decision.v1";
    public static final String DECISION_ENDPOINT =
        "/api/agent/observability/top-tier/backend-technology-modernization-decision";

    public static AgentBackendTechnologyModernizationDecisionResponse of(
        Instant generatedAt,
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness
    ) {
        List<Map<String, Object>> sourceBaselines = sourceBaselines(sourceWatch);
        List<Map<String, Object>> mainlineDecisions = buildMainlineDecisions();
        List<Map<String, Object>> compatibilityLaneDecisions = compatibilityLaneDecisions(evidenceReadiness);
        List<Map<String, Object>> modernizationGates = buildModernizationGates();
        List<Map<String, Object>> blockedShortcuts = buildBlockedShortcuts();
        List<Map<String, Object>> learningPath = buildLearningPath();
        int blockedCompatibilityLanes = countBlocked(compatibilityLaneDecisions);
        return new AgentBackendTechnologyModernizationDecisionResponse(
            SCHEMA_VERSION,
            generatedAt,
            decisionStatus(sourceWatch, evidenceReadiness, blockedCompatibilityLanes),
            "Phase 1 top-tier Agent backend technology modernization decision",
            true,
            true,
            true,
            true,
            false,
            false,
            true,
            false,
            false,
            sourceBaselines.size(),
            mainlineDecisions.size(),
            compatibilityLaneDecisions.size(),
            blockedCompatibilityLanes,
            modernizationGates.size(),
            blockedShortcuts.size(),
            learningPath.size(),
            sourceBaselines,
            mainlineDecisions,
            compatibilityLaneDecisions,
            modernizationGates,
            blockedShortcuts,
            learningPath,
            recommendedImplementationOrder(blockedCompatibilityLanes),
            sourceWatch,
            evidenceReadiness,
            buildEndpointMap(),
            decisionPolicy(sourceWatch, evidenceReadiness, mainlineDecisions, compatibilityLaneDecisions,
                modernizationGates, blockedShortcuts),
            safety(sourceWatch, evidenceReadiness),
            privacy(sourceWatch, evidenceReadiness)
        );
    }

    private static String decisionStatus(
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
        int blockedCompatibilityLanes
    ) {
        if (sourceWatch == null || evidenceReadiness == null) {
            return "MODERNIZATION_SOURCE_READ_MODEL_MISSING";
        }
        if (sourceWatch.runtimeUpgradePerformed()
            || sourceWatch.dependencyUpgradePerformed()
            || evidenceReadiness.runtimeControlAllowed()
            || evidenceReadiness.runtimeUpgradeAllowedNow()
            || evidenceReadiness.dependencyUpgradeAllowedNow()
            || evidenceReadiness.ciBlockingAllowedNow()) {
            return "UNEXPECTED_RUNTIME_OR_DEPENDENCY_AUTHORITY";
        }
        if (blockedCompatibilityLanes > 0) {
            return "JAVA_SPRING_MAINLINE_ADVANCED_COMPATIBILITY_LANES_BLOCKED_BY_EVIDENCE";
        }
        return "JAVA_SPRING_MAINLINE_READY_FOR_SEPARATE_MODERNIZATION_REVIEW";
    }

    private static List<Map<String, Object>> sourceBaselines(
        AgentOfficialVersionProtocolWatchResponse sourceWatch
    ) {
        if (sourceWatch == null) {
            return List.of();
        }
        return sourceWatch.officialSources().stream()
            .map(source -> {
                Map<String, Object> baseline = new LinkedHashMap<>();
                baseline.put("sourceId", string(source, "id"));
                baseline.put("title", string(source, "title"));
                baseline.put("officialUrl", string(source, "officialUrl"));
                baseline.put("sourceType", string(source, "sourceType"));
                baseline.put("sourceReviewDate", string(source, "sourceReviewDate"));
                baseline.put("adoptionMode", string(source, "adoptionMode"));
                baseline.put("runtimeBound", false);
                baseline.put("requiresGitReviewToChange", true);
                return Map.copyOf(baseline);
            })
            .toList();
    }

    private static List<Map<String, Object>> buildMainlineDecisions() {
        return List.of(
            mainline("java-17-build-baseline", "KEEP_MAINLINE",
                "Java 17 remains the recoverable build baseline while Java 21/25 run as compatibility lanes.",
                "typed runtime, stable tooling, rollback confidence"),
            mainline("spring-boot-3-5-control-plane", "KEEP_MAINLINE",
                "Spring Boot 3.5.x remains the production control-plane line until Boot 4 evidence is green.",
                "Spring Security, Web MVC contracts, Actuator, Micrometer"),
            mainline("spring-ai-1-1-access-layer", "KEEP_MAINLINE",
                "Spring AI 1.1.x remains the verified model access and ToolCallback layer.",
                "model/provider access without replacing local Tool authority"),
            mainline("safe-tool-executor-hitl-audit", "EXPAND_MAINLINE",
                "SafeToolExecutor, HITL, durable audit, idempotency, and release gates are the authority boundary.",
                "prevents external Agent protocols from becoming hidden execution paths"),
            mainline("deterministic-eval-replay", "EXPAND_MAINLINE",
                "Trace, redacted audit, replay, reviewed evidence, and deterministic evals stay on the release path.",
                "turns quality into evidence instead of opinion"),
            mainline("mcp-manifest-governance", "MAINLINE_CONTRACT_ONLY",
                "MCP is adopted first as read-only manifest and governance metadata.",
                "interoperability without tools/call authority"),
            mainline("memory-rag-contract-stack", "MAINLINE_CONTRACT_ONLY",
                "Memory/RAG advances through citation, source digest, lifecycle, and eval-suite contracts.",
                "retrieval safety before prompt influence"),
            mainline("vue-readonly-learning-workbench", "EXPAND_MAINLINE",
                "Vue should consume backend-owned read models before any runtime control is visible.",
                "operator UX plus learning UX without unsafe enable buttons")
        );
    }

    private static Map<String, Object> mainline(String id,
                                                String decision,
                                                String rationale,
                                                String phase1Value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("decision", decision);
        row.put("rationale", rationale);
        row.put("phase1Value", phase1Value);
        row.put("mainlineAllowedNow", true);
        row.put("requiresCompatibilityBranch", false);
        row.put("runtimeControlAllowed", false);
        row.put("dependencyUpgradeAllowedNow", false);
        return Map.copyOf(row);
    }

    private static List<Map<String, Object>> compatibilityLaneDecisions(
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness
    ) {
        if (evidenceReadiness == null) {
            return List.of();
        }
        return evidenceReadiness.matrixEvidenceRows().stream()
            .map(row -> compatibilityLane(row, evidenceReadiness))
            .toList();
    }

    private static Map<String, Object> compatibilityLane(
        Map<String, Object> evidenceRow,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness
    ) {
        String laneId = string(evidenceRow, "laneId");
        boolean blocked = bool(evidenceRow, "blocked");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("laneId", laneId);
        row.put("decision", laneDecision(laneId, blocked));
        row.put("currentBaseline", string(evidenceRow, "currentBaseline"));
        row.put("candidateTarget", string(evidenceRow, "candidateTarget"));
        row.put("evidenceReadiness", string(evidenceRow, "evidenceReadiness"));
        row.put("blocked", blocked);
        row.put("compatibilityBranchAllowed", true);
        row.put("mainlineUpgradeAllowedNow", false);
        row.put("dependencyUpgradeAllowedNow", false);
        row.put("runtimeControlAllowed", false);
        row.put("reviewedEvalTraceEvidenceReady", bool(evidenceRow, "reviewedEvalTraceEvidenceReady"));
        row.put("memoryRagReviewedTraceEvidenceReady", bool(evidenceRow, "memoryRagReviewedTraceEvidenceReady"));
        row.put("requiredEvidence", stringList(evidenceRow.get("requiredEvidence")));
        row.put("relatedEndpoints", evidenceRow.getOrDefault("relatedEndpoints", Map.of()));
        row.put("sourceEvidenceReadinessEndpoint",
            AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT);
        row.put("sourceReadinessStatus", evidenceReadiness.readinessStatus());
        return Map.copyOf(row);
    }

    private static String laneDecision(String laneId, boolean blocked) {
        if (!blocked) {
            return "READY_FOR_SEPARATE_RELEASE_REVIEW";
        }
        return switch (laneId) {
            case "java-runtime-toolchains", "spring-boot-framework", "spring-ai-access-layer" ->
                "COMPATIBILITY_BRANCH_REQUIRED_BEFORE_BASELINE_CHANGE";
            case "openai-responses-agents", "a2a-multi-agent-provenance" ->
                "LOCAL_CONTRACT_AND_PROVENANCE_FIRST";
            case "mcp-runtime-call-plane" ->
                "SAFE_TOOL_EXECUTOR_CONSENT_AUDIT_EVAL_FIRST";
            case "memory-rag-graphrag-reranker-vectorstore" ->
                "REVIEWED_MEMORY_RAG_TRACE_FIXTURES_FIRST";
            case "kubernetes-manager-control-plane" ->
                "WRITE_AUTHORITY_RELEASE_GATE_AND_READBACK_FIRST";
            case "supply-chain-ci-quality" ->
                "REAL_REVIEWED_TRACE_EVIDENCE_BEFORE_CI_BLOCKING";
            default -> "EVIDENCE_FIRST_COMPATIBILITY_LANE";
        };
    }

    private static List<Map<String, Object>> buildModernizationGates() {
        return List.of(
            gate("official-source-git-review", "Refresh official source URLs and dates through Git review."),
            gate("current-mainline-green", "Current Java 17 / Spring Boot 3.5.x mainline must stay green."),
            gate("compatibility-branch-before-major-upgrade", "Major Java, Boot, Spring AI, MCP, A2A, or RAG changes run on compatibility branches first."),
            gate("identity-tenant-privacy-regression", "Security, tenant, redaction, and secret-leak regressions must pass before runtime influence."),
            gate("trace-audit-replay-eval-evidence", "Reviewed trace, audit, replay, and eval evidence is required before promotion."),
            gate("vue-readonly-before-runtime-controls", "Vue renders evidence before any enable or upgrade button exists."),
            gate("human-release-decision", "Runtime authority and dependency upgrades need a separate reviewed release decision."),
            gate("phase2-domain-pause", "NIM, HPC, Slurm, and BCM stay paused for Phase 2.")
        );
    }

    private static Map<String, Object> gate(String id, String summary) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("id", id);
        gate.put("summary", summary);
        gate.put("required", true);
        gate.put("runtimeBound", false);
        return Map.copyOf(gate);
    }

    private static List<Map<String, Object>> buildBlockedShortcuts() {
        return List.of(
            shortcut("replace-java-control-plane-with-agent-runtime",
                "External Agent runtimes cannot own local RBAC, audit, HITL, or Tool authority."),
            shortcut("blind-java-25-baseline-bump",
                "Java 25 value must be proven through context propagation and audit regressions first."),
            shortcut("blind-spring-boot-4-mainline-bump",
                "Boot 4 / Framework 7 migration needs security and Web MVC contract evidence."),
            shortcut("blind-spring-ai-2-replacement",
                "Spring AI 2 preview APIs need Tool, advisor, memory, RAG, MCP, and observability compatibility proof."),
            shortcut("open-mcp-tools-call-directly",
                "MCP tools/call must be bound to SafeToolExecutor, consent, HITL, audit, rate limits, and eval gates."),
            shortcut("delegate-authority-through-a2a",
                "A2A handoffs may carry provenance, not local execution authority."),
            shortcut("enable-retrieval-before-reviewed-memory-rag-traces",
                "GraphRAG, rerankers, and vector stores cannot influence prompts before reviewed traces."),
            shortcut("treat-otel-genai-development-fields-as-contract",
                "Development semantic conventions stay behind an adapter."),
            shortcut("turn-ci-blocking-on-with-empty-fixtures",
                "CI cannot block releases on schema-only or unreviewed trace evidence.")
        );
    }

    private static Map<String, Object> shortcut(String id, String summary) {
        Map<String, Object> shortcut = new LinkedHashMap<>();
        shortcut.put("id", id);
        shortcut.put("summary", summary);
        shortcut.put("allowed", false);
        shortcut.put("blocksTopTierClaim", true);
        return Map.copyOf(shortcut);
    }

    private static List<Map<String, Object>> buildLearningPath() {
        return List.of(
            learning("control-plane-thinking", "Learn why a top-tier Agent still needs a typed Java/Spring control plane."),
            learning("tool-authority", "Map every model/protocol tool call back to SafeToolExecutor, HITL, and audit."),
            learning("official-source-watch", "Track official versions without turning documentation into runtime behavior."),
            learning("compatibility-matrix", "Practice major-version upgrades as compatibility branches, not dashboard buttons."),
            learning("trace-eval-evidence", "Use reviewed redacted traces as release evidence."),
            learning("memory-rag-governance", "Build citation, source custody, lifecycle, tenant, and eval gates before retrieval."),
            learning("protocol-provenance", "Treat MCP and A2A as protocol surfaces that need local authority mapping."),
            learning("operator-vue-workbench", "Render read-only governance state so operators can learn the system safely.")
        );
    }

    private static Map<String, Object> learning(String id, String objective) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("objective", objective);
        step.put("teachingMode", "contract-plus-tests-plus-docs");
        step.put("runtimeControlAllowed", false);
        return Map.copyOf(step);
    }

    private static List<String> recommendedImplementationOrder(int blockedCompatibilityLanes) {
        if (blockedCompatibilityLanes == 0) {
            return List.of(
                "prepare-separate-modernization-release-review",
                "keep-java-spring-control-plane-as-authority-boundary",
                "keep-vue-runtime-controls-hidden-until-release-decision"
            );
        }
        return List.of(
            "publish-backend-technology-modernization-decision",
            "wire-vue-backend-modernization-decision-page",
            "populate-reviewed-redacted-eval-trace-evidence",
            "complete-memory-rag-reviewed-trace-fixtures",
            "run-java-21-compatibility-branch-before-baseline-change",
            "run-java-25-and-spring-boot-4-compatibility-branches-after-current-mainline-green",
            "prototype-mcp-a2a-rag-only-behind-safe-tool-executor-and-release-gates",
            "keep-nim-hpc-slurm-bcm-paused-for-phase2"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("backendTechnologyModernizationDecision", DECISION_ENDPOINT);
        endpoints.put("officialVersionProtocolWatch", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrix",
            AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
            AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixVueBindingSpec",
            AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("advancedTechnologyAdoptionContract",
            "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        endpoints.put("topTierVueWorkbenchImplementationPackage",
            AgentTopTierVueWorkbenchImplementationPackageResponse.PACKAGE_ENDPOINT);
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("memoryRagReviewedTraceEvidenceManifest",
            AgentMemoryRagReviewedTraceEvidenceManifestResponse.MANIFEST_ENDPOINT);
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> decisionPolicy(
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
        List<Map<String, Object>> mainlineDecisions,
        List<Map<String, Object>> compatibilityLaneDecisions,
        List<Map<String, Object>> modernizationGates,
        List<Map<String, Object>> blockedShortcuts
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("decisionOnly", true);
        policy.put("sourceWatchEmbedded", sourceWatch != null);
        policy.put("evidenceReadinessEmbedded", evidenceReadiness != null);
        policy.put("mainlineDecisionCount", mainlineDecisions.size());
        policy.put("compatibilityLaneCount", compatibilityLaneDecisions.size());
        policy.put("blockedCompatibilityLaneCount", countBlocked(compatibilityLaneDecisions));
        policy.put("modernizationGateCount", modernizationGates.size());
        policy.put("blockedShortcutCount", blockedShortcuts.size());
        policy.put("javaBackendStillPreferred", true);
        policy.put("compatibilityBranchAllowed", true);
        policy.put("mainlineRuntimeUpgradeAllowedNow", false);
        policy.put("dependencyUpgradeAllowedNow", false);
        policy.put("runtimeControlAllowed", false);
        policy.put("ciBlockingAllowedNow", false);
        policy.put("requiresHumanGitReview", true);
        policy.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> safety(
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness
    ) {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("decisionOnly", true);
        safety.put("sourceWatchReadOnly", sourceWatch != null && bool(sourceWatch.safety(), "readOnly"));
        safety.put("evidenceReadinessReadOnly",
            evidenceReadiness != null && bool(evidenceReadiness.safety(), "readOnly"));
        safety.put("runtimeMutationAllowed", false);
        safety.put("runtimeControlAllowed", false);
        safety.put("runtimeUpgradeAllowedNow", false);
        safety.put("dependencyUpgradeAllowedNow", false);
        safety.put("compatibilityBranchCreationTriggered", false);
        safety.put("ciBlockingAllowedNow", false);
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
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> privacy(
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness
    ) {
        Map<String, Object> sourcePrivacy = sourceWatch != null ? sourceWatch.privacy() : Map.of();
        Map<String, Object> evidencePrivacy = evidenceReadiness != null ? evidenceReadiness.privacy() : Map.of();
        boolean containsRawPrincipal = bool(sourcePrivacy, "containsRawPrincipal")
            || bool(evidencePrivacy, "containsRawPrincipal");
        boolean containsRawPrompt = bool(sourcePrivacy, "containsRawPrompt")
            || bool(evidencePrivacy, "containsRawPrompt");
        boolean containsRawDocument = bool(sourcePrivacy, "containsRawDocument")
            || bool(evidencePrivacy, "containsRawDocument");
        boolean containsAuthorizationHeader = bool(sourcePrivacy, "containsAuthorizationHeader")
            || bool(evidencePrivacy, "containsAuthorizationHeader");
        boolean containsToken = bool(sourcePrivacy, "containsToken") || bool(evidencePrivacy, "containsToken");
        boolean containsPassword = bool(sourcePrivacy, "containsPassword") || bool(evidencePrivacy, "containsPassword");
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !containsRawPrincipal && !containsRawPrompt && !containsRawDocument
            && !containsAuthorizationHeader && !containsToken && !containsPassword);
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawPrompt", containsRawPrompt);
        privacy.put("containsRawDocument", containsRawDocument);
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsAuthorizationHeader", containsAuthorizationHeader);
        privacy.put("containsToken", containsToken);
        privacy.put("containsPassword", containsPassword);
        privacy.put("containsRuntimeSecrets", false);
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static int countBlocked(List<Map<String, Object>> rows) {
        return (int) rows.stream()
            .filter(row -> Boolean.TRUE.equals(row.get("blocked")))
            .count();
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static String string(Map<String, Object> data, String key) {
        Object value = data != null ? data.get(key) : null;
        return value != null ? String.valueOf(value) : "";
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
