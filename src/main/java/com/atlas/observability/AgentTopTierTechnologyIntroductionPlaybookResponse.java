package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Phase 1 playbook for introducing the newest Agent technologies safely.
 *
 * <p>Chinese teaching note: this is the "how to introduce everything advanced" contract.
 * It keeps the project ambitious, but forces every latest technology through official sources,
 * compatibility lanes, reviewed evidence, Vue visibility, release review, and then runtime binding.</p>
 */
public record AgentTopTierTechnologyIntroductionPlaybookResponse(
    String schemaVersion,
    Instant generatedAt,
    String playbookStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean javaSpringControlPlanePreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean sourceWatchEmbedded,
    boolean compatibilityMatrixEmbedded,
    boolean evidenceReadinessEmbedded,
    boolean backendDecisionEmbedded,
    boolean runtimeControlAllowed,
    boolean runtimeUpgradeAllowedNow,
    boolean dependencyUpgradeAllowedNow,
    boolean ciBlockingAllowedNow,
    int officialSourceCount,
    int technologyLaneCount,
    int playbookStageCount,
    int releaseGateCount,
    int expertReviewRoundCount,
    int learningModuleCount,
    int forbiddenShortcutCount,
    int vueRouteCount,
    List<Map<String, Object>> officialSourceSnapshot,
    List<Map<String, Object>> technologyIntroductionStages,
    List<Map<String, Object>> technologyLanePlaybookRows,
    List<Map<String, Object>> releaseGateRows,
    List<Map<String, Object>> expertReviewRounds,
    List<Map<String, Object>> learningModules,
    List<Map<String, Object>> forbiddenShortcuts,
    List<Map<String, Object>> vueWorkbenchRequirements,
    List<String> recommendedImplementationOrder,
    AgentOfficialVersionProtocolWatchResponse sourceWatch,
    AgentAdvancedTechnologyCompatibilityMatrixResponse compatibilityMatrix,
    AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
    AgentBackendTechnologyModernizationDecisionResponse backendDecision,
    Map<String, Object> endpointMap,
    Map<String, Object> playbookPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-top-tier-technology-introduction-playbook.v1";
    public static final String PLAYBOOK_ENDPOINT =
        "/api/agent/observability/top-tier/technology-introduction-playbook";

    public static AgentTopTierTechnologyIntroductionPlaybookResponse of(
        Instant generatedAt,
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        AgentAdvancedTechnologyCompatibilityMatrixResponse compatibilityMatrix,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        List<Map<String, Object>> sourceSnapshot = officialSourceSnapshot(sourceWatch);
        List<Map<String, Object>> stages = buildTechnologyIntroductionStages();
        List<Map<String, Object>> laneRows = technologyLanePlaybookRows(
            compatibilityMatrix,
            evidenceReadiness,
            backendDecision
        );
        List<Map<String, Object>> releaseGates = buildReleaseGateRows();
        List<Map<String, Object>> reviewRounds = buildExpertReviewRounds();
        List<Map<String, Object>> learningModules = buildLearningModules();
        List<Map<String, Object>> forbiddenShortcuts = buildForbiddenShortcuts();
        List<Map<String, Object>> vueRequirements = buildVueWorkbenchRequirements();
        int blockedLaneCount = countBlockedLanes(laneRows);
        return new AgentTopTierTechnologyIntroductionPlaybookResponse(
            SCHEMA_VERSION,
            generatedAt,
            playbookStatus(sourceWatch, compatibilityMatrix, evidenceReadiness, backendDecision, blockedLaneCount),
            "Phase 1 top-tier Agent latest-technology introduction playbook",
            true,
            true,
            true,
            sourceWatch != null,
            compatibilityMatrix != null,
            evidenceReadiness != null,
            backendDecision != null,
            false,
            false,
            false,
            false,
            sourceSnapshot.size(),
            laneRows.size(),
            stages.size(),
            releaseGates.size(),
            reviewRounds.size(),
            learningModules.size(),
            forbiddenShortcuts.size(),
            vueRequirements.size(),
            sourceSnapshot,
            stages,
            laneRows,
            releaseGates,
            reviewRounds,
            learningModules,
            forbiddenShortcuts,
            vueRequirements,
            recommendedImplementationOrder(blockedLaneCount),
            sourceWatch,
            compatibilityMatrix,
            evidenceReadiness,
            backendDecision,
            buildEndpointMap(),
            playbookPolicy(sourceWatch, compatibilityMatrix, evidenceReadiness, backendDecision, laneRows,
                releaseGates, reviewRounds, learningModules, forbiddenShortcuts, vueRequirements),
            safety(sourceWatch, compatibilityMatrix, evidenceReadiness, backendDecision),
            privacy(sourceWatch, compatibilityMatrix, evidenceReadiness, backendDecision)
        );
    }

    private static String playbookStatus(
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        AgentAdvancedTechnologyCompatibilityMatrixResponse compatibilityMatrix,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision,
        int blockedLaneCount
    ) {
        if (sourceWatch == null || compatibilityMatrix == null || evidenceReadiness == null
            || backendDecision == null) {
            return "PLAYBOOK_SOURCE_READ_MODEL_MISSING";
        }
        if (sourceWatch.runtimeUpgradePerformed()
            || sourceWatch.dependencyUpgradePerformed()
            || compatibilityMatrix.runtimeControlAllowed()
            || compatibilityMatrix.runtimeUpgradeAllowedNow()
            || compatibilityMatrix.dependencyUpgradeAllowedNow()
            || evidenceReadiness.runtimeControlAllowed()
            || evidenceReadiness.runtimeUpgradeAllowedNow()
            || evidenceReadiness.dependencyUpgradeAllowedNow()
            || backendDecision.runtimeControlAllowed()
            || backendDecision.mainlineRuntimeUpgradeAllowedNow()
            || backendDecision.dependencyUpgradeAllowedNow()) {
            return "UNEXPECTED_RUNTIME_OR_DEPENDENCY_AUTHORITY";
        }
        if (blockedLaneCount > 0) {
            return "PLAYBOOK_READY_EVIDENCE_GAPS_BLOCK_RUNTIME";
        }
        return "PLAYBOOK_READY_FOR_SEPARATE_RELEASE_REVIEW";
    }

    private static List<Map<String, Object>> officialSourceSnapshot(
        AgentOfficialVersionProtocolWatchResponse sourceWatch
    ) {
        if (sourceWatch == null) {
            return List.of();
        }
        return sourceWatch.officialSources().stream()
            .map(source -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("sourceId", string(source, "id"));
                row.put("title", string(source, "title"));
                row.put("officialUrl", string(source, "officialUrl"));
                row.put("sourceType", string(source, "sourceType"));
                row.put("sourceReviewDate", string(source, "sourceReviewDate"));
                row.put("adoptionMode", string(source, "adoptionMode"));
                row.put("officialSourceWinsOverConversationMemory", true);
                row.put("runtimeBound", false);
                return Map.copyOf(row);
            })
            .toList();
    }

    private static List<Map<String, Object>> buildTechnologyIntroductionStages() {
        return List.of(
            stage(1, "official-source-watch", "Recheck official sources and record source URLs, dates, and adoption mode."),
            stage(2, "compatibility-matrix", "Map every advanced technology to a lane, candidate target, tests, and blockers."),
            stage(3, "evidence-readiness", "Compare each lane with reviewed traces, Memory/RAG fixtures, Vue visibility, and Git evidence."),
            stage(4, "compatibility-branch", "Run major Java, Spring, Spring AI, MCP, A2A, RAG, or CI changes outside mainline first."),
            stage(5, "focused-regression-tests", "Verify identity, tenant, audit, HITL, Tool boundary, redaction, replay, and eval contracts."),
            stage(6, "vue-readonly-workbench", "Expose source evidence and blockers in Vue without enable or upgrade buttons."),
            stage(7, "multi-expert-release-review", "Collect architecture, security, frontend, eval, memory, and release reviews."),
            stage(8, "runtime-binding-slice", "Bind runtime authority only in a separate reviewed slice after gates are green.")
        );
    }

    private static Map<String, Object> stage(int order, String id, String summary) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("order", order);
        stage.put("id", id);
        stage.put("summary", summary);
        stage.put("required", true);
        stage.put("runtimeBound", false);
        stage.put("runtimeControlAllowed", false);
        return Map.copyOf(stage);
    }

    private static List<Map<String, Object>> technologyLanePlaybookRows(
        AgentAdvancedTechnologyCompatibilityMatrixResponse compatibilityMatrix,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        if (compatibilityMatrix == null) {
            return List.of();
        }
        Map<String, Map<String, Object>> evidenceRows = indexBy(
            evidenceReadiness != null ? evidenceReadiness.matrixEvidenceRows() : List.of(),
            "laneId"
        );
        Map<String, Map<String, Object>> decisionRows = indexBy(
            backendDecision != null ? backendDecision.compatibilityLaneDecisions() : List.of(),
            "laneId"
        );
        return compatibilityMatrix.matrixItems().stream()
            .map(item -> technologyLanePlaybookRow(
                item,
                evidenceRows.get(string(item, "id")),
                decisionRows.get(string(item, "id"))
            ))
            .toList();
    }

    private static Map<String, Object> technologyLanePlaybookRow(
        Map<String, Object> matrixItem,
        Map<String, Object> evidenceRow,
        Map<String, Object> decisionRow
    ) {
        String laneId = string(matrixItem, "id");
        boolean blocked = evidenceRow == null || bool(evidenceRow, "blocked");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("laneId", laneId);
        row.put("currentBaseline", string(matrixItem, "currentBaseline"));
        row.put("candidateTarget", string(matrixItem, "candidateTarget"));
        row.put("introductionMode", introductionMode(laneId, string(matrixItem, "readiness")));
        row.put("sourceReadiness", string(matrixItem, "readiness"));
        row.put("evidenceReadiness", evidenceRow != null ? string(evidenceRow, "evidenceReadiness") : "MISSING");
        row.put("backendDecision", decisionRow != null ? string(decisionRow, "decision") : "MISSING");
        row.put("blocked", blocked);
        row.put("compatibilityBranchRequired", true);
        row.put("mainlineUpgradeAllowedNow", false);
        row.put("dependencyUpgradeAllowedNow", false);
        row.put("runtimeControlAllowed", false);
        row.put("ciBlockingAllowedNow", false);
        row.put("teachingFocus", teachingFocus(laneId));
        row.put("requiredProofs", evidenceRow != null
            ? stringList(evidenceRow.get("requiredEvidence"))
            : List.of("reviewed-redacted-eval-trace-evidence", "official-source-review-date-and-url"));
        row.put("sourceMatrixRequiredEvidence", stringList(matrixItem.get("requiredEvidence")));
        row.put("relatedEndpoints", evidenceRow != null
            ? evidenceRow.getOrDefault("relatedEndpoints", Map.of())
            : Map.of("playbook", PLAYBOOK_ENDPOINT));
        return Map.copyOf(row);
    }

    private static String introductionMode(String laneId, String sourceReadiness) {
        return switch (laneId) {
            case "java-runtime-toolchains", "spring-boot-framework", "spring-ai-access-layer" ->
                "COMPATIBILITY_BRANCH_FIRST";
            case "openai-responses-agents" -> "LOCAL_CONTRACTS_BEFORE_SDK_OR_RUNTIME_ADAPTER";
            case "mcp-runtime-call-plane" -> "MANIFEST_AND_GOVERNANCE_BEFORE_TOOLS_CALL";
            case "a2a-multi-agent-provenance" -> "PROVENANCE_AND_ARTIFACT_EVIDENCE_BEFORE_HANDOFF";
            case "otel-genai-mcp-semconv" -> "ADAPTER_BEHIND_STABLE_INTERNAL_TELEMETRY";
            case "memory-rag-graphrag-reranker-vectorstore" -> "MEMORY_RAG_EVAL_FIXTURES_BEFORE_RETRIEVAL";
            case "kubernetes-manager-control-plane" -> "WRITE_RELEASE_GATE_AND_READBACK_BEFORE_MUTATION";
            case "supply-chain-ci-quality" -> "SBOM_DEPENDENCY_DIFF_AND_REVIEWED_TRACES_BEFORE_BLOCKING";
            default -> sourceReadiness.isBlank() ? "EVIDENCE_FIRST" : sourceReadiness;
        };
    }

    private static String teachingFocus(String laneId) {
        return switch (laneId) {
            case "java-runtime-toolchains" -> "Learn toolchain upgrades without breaking trace and audit context.";
            case "spring-boot-framework" -> "Learn major Spring migrations through security and controller contracts.";
            case "spring-ai-access-layer" -> "Learn model access abstraction without surrendering Tool authority.";
            case "openai-responses-agents" -> "Learn Responses and Agents concepts as local contracts first.";
            case "mcp-runtime-call-plane" -> "Learn MCP as protocol interoperability, not a shortcut around execution safety.";
            case "a2a-multi-agent-provenance" -> "Learn multi-Agent handoff through artifact provenance and local authority.";
            case "otel-genai-mcp-semconv" -> "Learn observability portability through adapter design and redaction.";
            case "memory-rag-graphrag-reranker-vectorstore" -> "Learn advanced RAG through source custody, tenant privacy, and evals.";
            case "kubernetes-manager-control-plane" -> "Learn manager writes through idempotency, release evidence, and readback.";
            case "supply-chain-ci-quality" -> "Learn supply-chain and CI gates from real reviewed evidence.";
            default -> "Learn this lane with official sources, contracts, tests, docs, and review.";
        };
    }

    private static List<Map<String, Object>> buildReleaseGateRows() {
        return List.of(
            releaseGate("official-source-reviewed", "Official source URLs and dates are refreshed through Git review."),
            releaseGate("current-mainline-green", "Current mainline validate and focused tests pass before any branch promotion."),
            releaseGate("compatibility-branch-green", "Candidate technology has a green compatibility branch."),
            releaseGate("identity-tenant-redaction-green", "Identity, tenant, privacy, and redaction regressions pass."),
            releaseGate("safe-tool-authority-proven", "Tool, MCP, A2A, RAG, and kube-manager paths cannot bypass local authority."),
            releaseGate("reviewed-trace-evidence-present", "Reviewed redacted traces exist for the affected capability."),
            releaseGate("deterministic-eval-green", "Deterministic eval and replay gates pass on reviewed evidence."),
            releaseGate("vue-readonly-evidence-visible", "Vue shows evidence and blockers before any runtime control exists."),
            releaseGate("recovery-memory-updated", "Workspace recovery memory and SHA manifests are updated."),
            releaseGate("human-release-decision", "A separate human/Git release decision authorizes runtime binding.")
        );
    }

    private static Map<String, Object> releaseGate(String id, String requirement) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("id", id);
        gate.put("requirement", requirement);
        gate.put("required", true);
        gate.put("satisfiedNow", false);
        gate.put("runtimeBound", false);
        return Map.copyOf(gate);
    }

    private static List<Map<String, Object>> buildExpertReviewRounds() {
        return List.of(
            expertRound("architecture-review", "Architecture reviewer validates control-plane fit and migration shape."),
            expertRound("security-review", "Security reviewer validates RBAC, Tool authority, privacy, and release gates."),
            expertRound("frontend-vue-review", "Frontend reviewer validates read-only workbench routes, states, and absent buttons."),
            expertRound("eval-quality-review", "Eval reviewer validates trace coverage, scoring, replay, and CI readiness."),
            expertRound("memory-rag-review", "Memory/RAG reviewer validates citation, source digest, lifecycle, tenant, and retrieval gates."),
            expertRound("release-manager-review", "Release reviewer validates recovery memory, Git evidence, rollback, and rollout order.")
        );
    }

    private static Map<String, Object> expertRound(String id, String objective) {
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("id", id);
        review.put("objective", objective);
        review.put("requiredBeforeRuntimeBinding", true);
        review.put("canBeParallelized", true);
        review.put("runtimeControlAllowed", false);
        return Map.copyOf(review);
    }

    private static List<Map<String, Object>> buildLearningModules() {
        return List.of(
            learningModule("control-plane-mindset", "Why top-tier Agents still need typed backend authority."),
            learningModule("official-source-literacy", "How to read current official docs without blind upgrades."),
            learningModule("compatibility-matrix-practice", "How to turn latest technology into migration lanes."),
            learningModule("tool-authority-and-hitl", "How Tool authority, HITL, durable audit, and release receipts fit together."),
            learningModule("trace-replay-eval-loop", "How replay and deterministic eval become release evidence."),
            learningModule("mcp-a2a-protocol-governance", "How MCP and A2A add interoperability without bypassing local safety."),
            learningModule("advanced-memory-rag", "How GraphRAG, rerankers, and vector stores need source and privacy gates."),
            learningModule("vue-operator-learning-workbench", "How operators learn the system from read-only backend read models.")
        );
    }

    private static Map<String, Object> learningModule(String id, String outcome) {
        Map<String, Object> module = new LinkedHashMap<>();
        module.put("id", id);
        module.put("outcome", outcome);
        module.put("teachingArtifact", "contract-plus-tests-plus-docs");
        module.put("runtimeControlAllowed", false);
        return Map.copyOf(module);
    }

    private static List<Map<String, Object>> buildForbiddenShortcuts() {
        return List.of(
            shortcut("treat-latest-as-safe", "Latest version labels do not prove production fitness."),
            shortcut("replace-java-spring-control-plane", "External Agent runtimes cannot own local identity, audit, or release gates."),
            shortcut("upgrade-pom-from-ui", "Dependency changes need branch, tests, review, and rollback evidence."),
            shortcut("open-mcp-tools-call-before-consent", "MCP tools/call needs consent, SafeToolExecutor, HITL, audit, and eval gates."),
            shortcut("run-a2a-handoff-before-provenance", "A2A needs Agent Card/task/artifact provenance before runtime handoff."),
            shortcut("enable-rag-before-reviewed-fixtures", "Retrieval cannot influence prompts before Memory/RAG evidence exists."),
            shortcut("use-otel-development-fields-as-primary-contract", "Development semconv fields stay behind adapters."),
            shortcut("enable-ci-blocking-on-empty-catalogs", "CI blocking needs real reviewed trace anchors."),
            shortcut("open-kube-manager-write-before-release-gate", "Manager writes need idempotency, release evidence, and readback."),
            shortcut("reopen-phase2-domain-plugins", "NIM, HPC, Slurm, and BCM stay paused until Phase 2.")
        );
    }

    private static Map<String, Object> shortcut(String id, String reason) {
        Map<String, Object> shortcut = new LinkedHashMap<>();
        shortcut.put("id", id);
        shortcut.put("reason", reason);
        shortcut.put("allowedNow", false);
        shortcut.put("blocksTopTierClaim", true);
        return Map.copyOf(shortcut);
    }

    private static List<Map<String, Object>> buildVueWorkbenchRequirements() {
        return List.of(
            vueRoute("technology-introduction-playbook", PLAYBOOK_ENDPOINT),
            vueRoute("official-version-protocol-watch", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT),
            vueRoute("advanced-technology-compatibility-matrix",
                AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT),
            vueRoute("advanced-technology-evidence-readiness",
                AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT),
            vueRoute("backend-technology-modernization-decision",
                AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT)
        );
    }

    private static Map<String, Object> vueRoute(String id, String endpoint) {
        Map<String, Object> route = new LinkedHashMap<>();
        route.put("id", id);
        route.put("endpoint", endpoint);
        route.put("method", "GET");
        route.put("requiresAdminSession", true);
        route.put("readOnly", true);
        route.put("runtimeControlAllowed", false);
        return Map.copyOf(route);
    }

    private static List<String> recommendedImplementationOrder(int blockedLaneCount) {
        if (blockedLaneCount == 0) {
            return List.of(
                "review-playbook-before-release-branch",
                "prepare-separate-runtime-binding-release-decision",
                "keep-vue-runtime-controls-hidden-until-release-approval"
            );
        }
        return List.of(
            "publish-top-tier-technology-introduction-playbook",
            "wire-vue-playbook-page",
            "keep-java-spring-control-plane-as-phase1-mainline",
            "populate-reviewed-redacted-eval-trace-evidence",
            "complete-memory-rag-reviewed-trace-fixtures",
            "run-java-21-and-java-25-compatibility-branches-after-mainline-green",
            "run-spring-boot-4-and-spring-ai-2-compatibility-branches-after-source-review",
            "prototype-mcp-a2a-rag-only-after-safe-tool-executor-release-gates",
            "keep-nim-hpc-slurm-bcm-paused-for-phase2"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("topTierTechnologyIntroductionPlaybook", PLAYBOOK_ENDPOINT);
        endpoints.put("officialVersionProtocolWatch", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrix",
            AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
            AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT);
        endpoints.put("backendTechnologyModernizationDecision",
            AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT);
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
        endpoints.put("kubeManagerGovernanceOverview",
            "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> playbookPolicy(
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        AgentAdvancedTechnologyCompatibilityMatrixResponse compatibilityMatrix,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision,
        List<Map<String, Object>> laneRows,
        List<Map<String, Object>> releaseGates,
        List<Map<String, Object>> reviewRounds,
        List<Map<String, Object>> learningModules,
        List<Map<String, Object>> forbiddenShortcuts,
        List<Map<String, Object>> vueRequirements
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("playbookOnly", true);
        policy.put("sourceWatchEmbedded", sourceWatch != null);
        policy.put("compatibilityMatrixEmbedded", compatibilityMatrix != null);
        policy.put("evidenceReadinessEmbedded", evidenceReadiness != null);
        policy.put("backendDecisionEmbedded", backendDecision != null);
        policy.put("technologyLaneCount", laneRows.size());
        policy.put("blockedTechnologyLaneCount", countBlockedLanes(laneRows));
        policy.put("releaseGateCount", releaseGates.size());
        policy.put("expertReviewRoundCount", reviewRounds.size());
        policy.put("learningModuleCount", learningModules.size());
        policy.put("forbiddenShortcutCount", forbiddenShortcuts.size());
        policy.put("vueRouteCount", vueRequirements.size());
        policy.put("officialSourceWinsOverConversationMemory", true);
        policy.put("compatibilityBranchRequiredBeforeMajorUpgrade", true);
        policy.put("requiresHumanGitReview", true);
        policy.put("runtimeControlAllowed", false);
        policy.put("runtimeUpgradeAllowedNow", false);
        policy.put("dependencyUpgradeAllowedNow", false);
        policy.put("ciBlockingAllowedNow", false);
        policy.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> safety(
        AgentOfficialVersionProtocolWatchResponse sourceWatch,
        AgentAdvancedTechnologyCompatibilityMatrixResponse compatibilityMatrix,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("playbookOnly", true);
        safety.put("sourceWatchReadOnly", sourceWatch != null && bool(sourceWatch.safety(), "readOnly"));
        safety.put("compatibilityMatrixReadOnly", compatibilityMatrix != null
            && bool(compatibilityMatrix.safety(), "readOnly"));
        safety.put("evidenceReadinessReadOnly", evidenceReadiness != null
            && bool(evidenceReadiness.safety(), "readOnly"));
        safety.put("backendDecisionReadOnly", backendDecision != null && bool(backendDecision.safety(), "readOnly"));
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
        AgentAdvancedTechnologyCompatibilityMatrixResponse compatibilityMatrix,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidenceReadiness,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        boolean containsRawPrincipal = truthyAny("containsRawPrincipal", privacyOf(sourceWatch),
            privacyOf(compatibilityMatrix), privacyOf(evidenceReadiness), privacyOf(backendDecision));
        boolean containsRawPrompt = truthyAny("containsRawPrompt", privacyOf(sourceWatch),
            privacyOf(compatibilityMatrix), privacyOf(evidenceReadiness), privacyOf(backendDecision));
        boolean containsRawDocument = truthyAny("containsRawDocument", privacyOf(sourceWatch),
            privacyOf(compatibilityMatrix), privacyOf(evidenceReadiness), privacyOf(backendDecision));
        boolean containsAuthorizationHeader = truthyAny("containsAuthorizationHeader", privacyOf(sourceWatch),
            privacyOf(compatibilityMatrix), privacyOf(evidenceReadiness), privacyOf(backendDecision));
        boolean containsToken = truthyAny("containsToken", privacyOf(sourceWatch),
            privacyOf(compatibilityMatrix), privacyOf(evidenceReadiness), privacyOf(backendDecision));
        boolean containsPassword = truthyAny("containsPassword", privacyOf(sourceWatch),
            privacyOf(compatibilityMatrix), privacyOf(evidenceReadiness), privacyOf(backendDecision));
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !containsRawPrincipal && !containsRawPrompt && !containsRawDocument
            && !containsAuthorizationHeader && !containsToken && !containsPassword);
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawConversation", false);
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

    private static Map<String, Map<String, Object>> indexBy(List<Map<String, Object>> rows, String key) {
        return rows.stream()
            .collect(Collectors.toMap(row -> string(row, key), Function.identity(), (left, right) -> left));
    }

    private static int countBlockedLanes(List<Map<String, Object>> laneRows) {
        return (int) laneRows.stream()
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

    private static Map<String, Object> privacyOf(AgentOfficialVersionProtocolWatchResponse response) {
        return response != null ? response.privacy() : Map.of();
    }

    private static Map<String, Object> privacyOf(AgentAdvancedTechnologyCompatibilityMatrixResponse response) {
        return response != null ? response.privacy() : Map.of();
    }

    private static Map<String, Object> privacyOf(
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse response
    ) {
        return response != null ? response.privacy() : Map.of();
    }

    private static Map<String, Object> privacyOf(AgentBackendTechnologyModernizationDecisionResponse response) {
        return response != null ? response.privacy() : Map.of();
    }

    @SafeVarargs
    private static boolean truthyAny(String key, Map<String, Object>... maps) {
        for (Map<String, Object> map : maps) {
            if (bool(map, key)) {
                return true;
            }
        }
        return false;
    }
}
