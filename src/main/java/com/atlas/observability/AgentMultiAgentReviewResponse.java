package com.atlas.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregated read model for Phase 1 multi-agent/expert review.
 *
 * <p>This contract makes expert review visible to Vue without enabling A2A handoff,
 * MCP tools/call, retrieval, kube-manager mutation, eval execution, or dependency upgrades.</p>
 */
public record AgentMultiAgentReviewResponse(
    String schemaVersion,
    Instant generatedAt,
    String reviewStatus,
    String frontendTarget,
    boolean phase1TopTierGoalPreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean playbookEmbedded,
    boolean phase1RoadmapEmbedded,
    boolean compatibilityEvidenceEmbedded,
    boolean officialWatchDashboardEmbedded,
    boolean backendDecisionEmbedded,
    boolean runtimeControlAllowed,
    boolean a2aRuntimeHandoffAllowed,
    boolean mcpToolsCallAllowed,
    boolean toolExecutionAllowed,
    int expertReviewRoundCount,
    int roadmapStepCount,
    int a2aEvidenceRowCount,
    int blockedRuntimeShortcutCount,
    int reviewGateCount,
    int disabledRuntimeActionCount,
    List<Map<String, Object>> expertReviewRounds,
    List<Map<String, Object>> orchestrationReviewRows,
    List<Map<String, Object>> a2aProvenanceRows,
    List<Map<String, Object>> reviewGateRows,
    List<Map<String, Object>> blockedRuntimeShortcuts,
    List<Map<String, Object>> disabledRuntimeActions,
    List<String> recommendedImplementationOrder,
    List<String> learningNotes,
    List<String> blockedActions,
    AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
    AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
    AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
    AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
    AgentBackendTechnologyModernizationDecisionResponse backendDecision,
    Map<String, Object> endpointMap,
    Map<String, Object> reviewPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-multi-agent-review.v1";
    public static final String REVIEW_ENDPOINT =
        "/api/agent/observability/top-tier/multi-agent-review";

    public static AgentMultiAgentReviewResponse of(
        Instant generatedAt,
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        List<Map<String, Object>> expertRounds = buildExpertReviewRounds(playbook);
        List<Map<String, Object>> orchestrationRows = buildOrchestrationReviewRows(phase1Roadmap);
        List<Map<String, Object>> a2aRows = buildA2aProvenanceRows(
            playbook,
            phase1Roadmap,
            compatibilityEvidence,
            officialWatchDashboard,
            backendDecision
        );
        List<Map<String, Object>> reviewGates = buildReviewGateRows(
            playbook,
            phase1Roadmap,
            compatibilityEvidence,
            officialWatchDashboard,
            backendDecision
        );
        List<Map<String, Object>> blockedShortcuts = buildBlockedRuntimeShortcuts(
            playbook,
            officialWatchDashboard,
            backendDecision
        );
        List<Map<String, Object>> disabledActions = buildDisabledRuntimeActions(
            compatibilityEvidence,
            officialWatchDashboard
        );
        List<String> blockedActionIds = blockedActions(disabledActions);
        return new AgentMultiAgentReviewResponse(
            SCHEMA_VERSION,
            generatedAt,
            reviewStatus(playbook, phase1Roadmap, compatibilityEvidence, officialWatchDashboard, backendDecision),
            "vue-kube-manager multi-agent expert review board",
            phase1TopTierGoalPreserved(playbook, phase1Roadmap, compatibilityEvidence, officialWatchDashboard,
                backendDecision),
            true,
            playbook != null,
            phase1Roadmap != null,
            compatibilityEvidence != null,
            officialWatchDashboard != null,
            backendDecision != null,
            false,
            false,
            false,
            false,
            expertRounds.size(),
            phase1Roadmap != null ? phase1Roadmap.stepCount() : 0,
            a2aRows.size(),
            blockedShortcuts.size(),
            reviewGates.size(),
            disabledActions.size(),
            expertRounds,
            orchestrationRows,
            a2aRows,
            reviewGates,
            blockedShortcuts,
            disabledActions,
            buildRecommendedImplementationOrder(),
            buildLearningNotes(),
            blockedActionIds,
            playbook,
            phase1Roadmap,
            compatibilityEvidence,
            officialWatchDashboard,
            backendDecision,
            buildEndpointMap(),
            buildReviewPolicy(playbook, phase1Roadmap, compatibilityEvidence, officialWatchDashboard, backendDecision,
                expertRounds, orchestrationRows, a2aRows, reviewGates, blockedShortcuts, disabledActions),
            buildSafety(playbook, phase1Roadmap, compatibilityEvidence, officialWatchDashboard, backendDecision),
            buildPrivacy(playbook, phase1Roadmap, compatibilityEvidence, officialWatchDashboard, backendDecision)
        );
    }

    private static String reviewStatus(
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        if (playbook == null || phase1Roadmap == null || compatibilityEvidence == null
            || officialWatchDashboard == null || backendDecision == null) {
            return "MULTI_AGENT_REVIEW_SOURCE_READ_MODEL_MISSING";
        }
        if (playbook.runtimeControlAllowed()
            || playbook.runtimeUpgradeAllowedNow()
            || playbook.dependencyUpgradeAllowedNow()
            || playbook.ciBlockingAllowedNow()
            || phase1Roadmap.runtimeMutationAllowed()
            || compatibilityEvidence.runtimeControlAllowed()
            || compatibilityEvidence.runtimeUpgradeAllowedNow()
            || compatibilityEvidence.dependencyUpgradeAllowedNow()
            || compatibilityEvidence.ciBlockingAllowedNow()
            || compatibilityEvidence.catalogMutationAllowed()
            || officialWatchDashboard.runtimeControlAllowed()
            || backendDecision.runtimeControlAllowed()
            || backendDecision.mainlineRuntimeUpgradeAllowedNow()
            || backendDecision.dependencyUpgradeAllowedNow()
            || backendDecision.ciBlockingAllowedNow()) {
            return "UNEXPECTED_RUNTIME_AUTHORITY_IN_MULTI_AGENT_REVIEW_SOURCE";
        }
        return "MULTI_AGENT_REVIEW_READY_RUNTIME_HANDOFF_CLOSED";
    }

    private static boolean phase1TopTierGoalPreserved(
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        return (playbook == null || playbook.phase1TopTierGoalPreserved())
            && (phase1Roadmap == null || phase1Roadmap.phase1TopTierGoalPreserved())
            && (compatibilityEvidence == null || compatibilityEvidence.phase1TopTierGoalPreserved())
            && (officialWatchDashboard == null || officialWatchDashboard.phase1TopTierGoalPreserved())
            && (backendDecision == null || backendDecision.phase1TopTierGoalPreserved());
    }

    private static List<Map<String, Object>> buildExpertReviewRounds(
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook
    ) {
        if (playbook == null) {
            return List.of();
        }
        return playbook.expertReviewRounds().stream()
            .map(round -> {
                String id = string(round, "id");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", id);
                row.put("objective", string(round, "objective"));
                row.put("reviewRole", reviewRole(id));
                row.put("status", "REQUIRED_BEFORE_RUNTIME_BINDING");
                row.put("sourceReadModel", "technology-introduction-playbook");
                row.put("requiredBeforeRuntimeBinding", bool(round, "requiredBeforeRuntimeBinding"));
                row.put("canBeParallelized", bool(round, "canBeParallelized"));
                row.put("runtimeControlAllowed", false);
                row.put("a2aRuntimeHandoffAllowed", false);
                row.put("toolExecutionAllowed", false);
                row.put("readOnly", true);
                return Map.copyOf(row);
            })
            .toList();
    }

    private static String reviewRole(String id) {
        return switch (id) {
            case "architecture-review" -> "architecture";
            case "security-review" -> "security";
            case "frontend-vue-review" -> "frontend";
            case "eval-quality-review" -> "eval-quality";
            case "memory-rag-review" -> "memory-rag";
            case "release-manager-review" -> "release-management";
            default -> "expert-review";
        };
    }

    private static List<Map<String, Object>> buildOrchestrationReviewRows(
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap
    ) {
        if (phase1Roadmap == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> step : phase1Roadmap.executionSteps()) {
            String stepId = string(step, "id");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("order", integer(step, "order"));
            row.put("stepId", stepId);
            row.put("summary", string(step, "summary"));
            row.put("status", string(step, "status"));
            row.put("reviewRoundIds", reviewRoundIdsForStep(stepId));
            row.put("sourceReadModel", "phase1-execution-roadmap");
            row.put("dependencies", stringList(step.get("requiredEvidence")));
            row.put("vueTargets", stringList(step.get("vueTargets")));
            row.put("runtimeControlAllowed", false);
            row.put("a2aRuntimeHandoffAllowed", false);
            row.put("mcpToolsCallAllowed", false);
            row.put("toolExecutionAllowed", false);
            row.put("readOnly", true);
            rows.add(Map.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static List<String> reviewRoundIdsForStep(String stepId) {
        return switch (stepId) {
            case "vue-readiness-control-plane" -> List.of("frontend-vue-review", "architecture-review");
            case "reviewed-eval-trace-evidence", "release-blocking-eval-gates" ->
                List.of("eval-quality-review", "security-review", "release-manager-review");
            case "memory-rag-eval-suite-binding", "durable-memory-store-binding", "retrieval-runtime-binding" ->
                List.of("memory-rag-review", "eval-quality-review", "security-review");
            case "mcp-runtime-safe-call-plane" ->
                List.of("security-review", "architecture-review", "eval-quality-review");
            case "agent-handoff-and-a2a-provenance" ->
                List.of("architecture-review", "security-review", "eval-quality-review", "release-manager-review");
            default -> List.of("architecture-review");
        };
    }

    private static List<Map<String, Object>> buildA2aProvenanceRows(
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        addA2aLaneRows(rows, playbook);
        addA2aRoadmapRows(rows, phase1Roadmap);
        addA2aEvidenceRows(rows, compatibilityEvidence);
        addA2aDashboardRows(rows, officialWatchDashboard);
        addA2aDecisionRows(rows, backendDecision);
        return List.copyOf(rows);
    }

    private static void addA2aLaneRows(List<Map<String, Object>> rows,
                                       AgentTopTierTechnologyIntroductionPlaybookResponse playbook) {
        if (playbook == null) {
            return;
        }
        for (Map<String, Object> lane : playbook.technologyLanePlaybookRows()) {
            String laneId = string(lane, "laneId");
            if (containsA2aOrHandoff(laneId)) {
                rows.add(a2aRow("technology-introduction-playbook", laneId, "lane",
                    string(lane, "evidenceReadiness"), bool(lane, "blocked"), stringList(lane.get("requiredProofs"))));
            }
        }
    }

    private static void addA2aRoadmapRows(List<Map<String, Object>> rows,
                                          AgentPhase1ExecutionRoadmapResponse phase1Roadmap) {
        if (phase1Roadmap == null) {
            return;
        }
        for (Map<String, Object> step : phase1Roadmap.executionSteps()) {
            String stepId = string(step, "id");
            String summary = string(step, "summary");
            if (containsA2aOrHandoff(stepId) || containsA2aOrHandoff(summary)) {
                rows.add(a2aRow("phase1-execution-roadmap", stepId, "roadmap-step",
                    string(step, "status"), true, stringList(step.get("requiredEvidence"))));
            }
        }
    }

    private static void addA2aEvidenceRows(List<Map<String, Object>> rows,
                                           AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse evidence) {
        if (evidence == null) {
            return;
        }
        for (Map<String, Object> evidenceRow : evidence.matrixEvidenceRows()) {
            String laneId = string(evidenceRow, "laneId");
            if (containsA2aOrHandoff(laneId)) {
                rows.add(a2aRow("advanced-technology-evidence-readiness", laneId, "evidence-row",
                    string(evidenceRow, "evidenceReadiness"), bool(evidenceRow, "blocked"),
                    stringList(evidenceRow.get("requiredEvidence"))));
            }
        }
    }

    private static void addA2aDashboardRows(List<Map<String, Object>> rows,
                                            AgentOfficialVersionProtocolWatchDashboardResponse dashboard) {
        if (dashboard == null) {
            return;
        }
        for (Map<String, Object> track : dashboard.technologyTrackCards()) {
            String trackId = string(track, "id");
            if (containsA2aOrHandoff(trackId)) {
                rows.add(a2aRow("official-version-protocol-watch-dashboard", trackId, "technology-track",
                    string(track, "status"), true, stringList(track.get("beforeRuntimeEvidence"))));
            }
        }
    }

    private static void addA2aDecisionRows(List<Map<String, Object>> rows,
                                           AgentBackendTechnologyModernizationDecisionResponse backendDecision) {
        if (backendDecision == null) {
            return;
        }
        for (Map<String, Object> lane : backendDecision.compatibilityLaneDecisions()) {
            String laneId = string(lane, "laneId");
            if (containsA2aOrHandoff(laneId)) {
                rows.add(a2aRow("backend-technology-modernization-decision", laneId, "decision-row",
                    string(lane, "decision"), bool(lane, "blocked"), stringList(lane.get("requiredEvidence"))));
            }
        }
    }

    private static Map<String, Object> a2aRow(String source,
                                              String sourceId,
                                              String rowType,
                                              String status,
                                              boolean blocked,
                                              List<String> requiredEvidence) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", source);
        row.put("sourceId", sourceId);
        row.put("rowType", rowType);
        row.put("status", status);
        row.put("blocked", blocked);
        row.put("requiredEvidence", List.copyOf(requiredEvidence));
        row.put("agentCardRuntimeExportAllowed", false);
        row.put("taskRuntimeHandoffAllowed", false);
        row.put("artifactDigestRequired", true);
        row.put("localAuthorityRequired", true);
        row.put("traceAuditReplayRequired", true);
        row.put("evalCoverageRequired", true);
        row.put("a2aRuntimeHandoffAllowed", false);
        row.put("runtimeControlAllowed", false);
        row.put("readOnly", true);
        return Map.copyOf(row);
    }

    private static boolean containsA2aOrHandoff(String value) {
        String normalized = value != null ? value.toLowerCase() : "";
        return normalized.contains("a2a") || normalized.contains("handoff");
    }

    private static List<Map<String, Object>> buildReviewGateRows(
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        addGateRows(rows, "technology-introduction-playbook", playbook != null ? playbook.releaseGateRows() : List.of());
        addGateRows(rows, "phase1-execution-roadmap", phase1Roadmap != null ? phase1Roadmap.dependencyGates() : List.of());
        addGateRows(rows, "advanced-technology-evidence-readiness",
            compatibilityEvidence != null ? compatibilityEvidence.blockingGateRows() : List.of());
        addGateRows(rows, "official-version-protocol-watch-dashboard",
            officialWatchDashboard != null ? officialWatchDashboard.adoptionGateRows() : List.of());
        addGateRows(rows, "backend-technology-modernization-decision",
            backendDecision != null ? backendDecision.modernizationGates() : List.of());
        return List.copyOf(rows);
    }

    private static void addGateRows(List<Map<String, Object>> rows,
                                    String source,
                                    List<Map<String, Object>> sourceRows) {
        for (Map<String, Object> gate : sourceRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", source);
            row.put("id", string(gate, "id"));
            row.put("requirement", firstNonBlank(string(gate, "requirement"), string(gate, "summary")));
            row.put("status", firstNonBlank(string(gate, "status"), bool(gate, "satisfiedNow")
                || bool(gate, "satisfied") ? "READY" : "REVIEW_REQUIRED"));
            row.put("required", gate.getOrDefault("required", true));
            row.put("runtimeBound", bool(gate, "runtimeBound"));
            row.put("runtimeControlAllowed", false);
            row.put("a2aRuntimeHandoffAllowed", false);
            row.put("toolExecutionAllowed", false);
            row.put("readOnly", true);
            rows.add(Map.copyOf(row));
        }
    }

    private static List<Map<String, Object>> buildBlockedRuntimeShortcuts(
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        addShortcutRows(rows, "technology-introduction-playbook",
            playbook != null ? playbook.forbiddenShortcuts() : List.of());
        addShortcutRows(rows, "official-version-protocol-watch-dashboard",
            officialWatchDashboard != null ? officialWatchDashboard.blockedRuntimeShortcutRows() : List.of());
        addShortcutRows(rows, "backend-technology-modernization-decision",
            backendDecision != null ? backendDecision.blockedShortcuts() : List.of());
        return List.copyOf(rows);
    }

    private static void addShortcutRows(List<Map<String, Object>> rows,
                                        String source,
                                        List<Map<String, Object>> sourceRows) {
        for (Map<String, Object> shortcut : sourceRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", source);
            row.put("id", string(shortcut, "id"));
            row.put("reason", firstNonBlank(string(shortcut, "reason"), string(shortcut, "summary")));
            row.put("allowedNow", false);
            row.put("blocksTopTierClaim", shortcut.getOrDefault("blocksTopTierClaim", true));
            row.put("runtimeControlAllowed", false);
            row.put("buttonVisibleNow", false);
            row.put("readOnly", true);
            rows.add(Map.copyOf(row));
        }
    }

    private static List<Map<String, Object>> buildDisabledRuntimeActions(
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(disabledRuntimeAction("run-a2a-runtime-handoff", "A2A handoff needs artifact provenance, local authority, replay, and eval evidence."));
        rows.add(disabledRuntimeAction("call-mcp-tools", "MCP tools/call must pass consent, SafeToolExecutor, HITL, audit, and release gates."));
        rows.add(disabledRuntimeAction("execute-agent-tool", "Tool execution is outside this read-model aggregate."));
        rows.add(disabledRuntimeAction("run-retrieval-runtime", "Retrieval needs citation, source digest, privacy, lifecycle, and eval evidence."));
        rows.add(disabledRuntimeAction("upgrade-dependencies", "Dependency upgrades require compatibility branches and Git review."));
        rows.add(disabledRuntimeAction("mutate-kube-manager", "kube-manager writes need idempotency, release evidence, and readback."));
        rows.add(disabledRuntimeAction("run-eval-runtime", "Eval execution is separate from this review projection."));
        rows.add(disabledRuntimeAction("enable-ci-blocking", "CI blocking needs reviewed trace evidence and a release decision."));
        rows.add(disabledRuntimeAction("write-durable-memory", "Memory writes require lifecycle, privacy, and recovery contracts."));
        rows.add(disabledRuntimeAction("reopen-phase2-domain-plugins", "NIM, HPC, Slurm, and BCM remain Phase 2."));
        addDisabledRows(rows, "advanced-technology-evidence-readiness",
            compatibilityEvidence != null ? compatibilityEvidence.disabledRuntimeActions() : List.of());
        addDisabledRows(rows, "official-version-protocol-watch-dashboard",
            officialWatchDashboard != null ? officialWatchDashboard.disabledRuntimeActions() : List.of());
        return List.copyOf(rows);
    }

    private static Map<String, Object> disabledRuntimeAction(String actionId, String reason) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("source", "multi-agent-review");
        action.put("actionId", actionId);
        action.put("reason", reason);
        action.put("enabledNow", false);
        action.put("buttonVisibleNow", false);
        action.put("clickHandlerAllowed", false);
        action.put("requiresSeparateReviewedSlice", true);
        return Map.copyOf(action);
    }

    private static void addDisabledRows(List<Map<String, Object>> rows,
                                        String source,
                                        List<Map<String, Object>> sourceRows) {
        for (Map<String, Object> action : sourceRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("source", source);
            row.put("actionId", firstNonBlank(string(action, "actionId"), string(action, "id")));
            row.put("reason", firstNonBlank(
                firstNonBlank(string(action, "reason"), string(action, "disabledReason")),
                string(action, "summary")
            ));
            row.put("enabledNow", false);
            row.put("buttonVisibleNow", false);
            row.put("clickHandlerAllowed", false);
            row.put("requiresSeparateReviewedSlice", true);
            rows.add(Map.copyOf(row));
        }
    }

    private static List<String> blockedActions(List<Map<String, Object>> disabledActions) {
        List<String> actions = new ArrayList<>();
        for (Map<String, Object> action : disabledActions) {
            actions.add(string(action, "actionId"));
        }
        return List.copyOf(actions);
    }

    private static List<String> buildRecommendedImplementationOrder() {
        return List.of(
            "publish-multi-agent-review-read-model",
            "bind-vue-multi-agent-review-page",
            "assign-parallel-expert-review-rounds",
            "keep-a2a-runtime-handoff-closed-until-provenance-evidence",
            "populate-reviewed-redacted-eval-trace-evidence",
            "complete-memory-rag-reviewed-trace-fixtures",
            "run-compatibility-branches-after-mainline-green",
            "prepare-separate-release-decision-before-runtime-binding",
            "keep-nim-hpc-slurm-bcm-paused-for-phase2"
        );
    }

    private static List<String> buildLearningNotes() {
        return List.of(
            "Multi-agent review is an evidence workflow before it is a runtime handoff protocol.",
            "A top-tier Agent keeps Java/Spring as local authority while learning MCP, A2A, RAG, eval, and observability as governed contracts.",
            "Expert rounds can run in parallel, but runtime authority still needs reviewed traces, release gates, audit evidence, and Git review.",
            "A2A provenance starts with Agent Card/task/artifact custody, not with delegating local RBAC or Tool authority.",
            "The frontend should render blocked shortcuts and disabled actions as learning evidence, not as missing features."
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("multiAgentReview", REVIEW_ENDPOINT);
        endpoints.put("topTierTechnologyIntroductionPlaybook",
            AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT);
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
            AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchDashboard",
            AgentOfficialVersionProtocolWatchDashboardResponse.DASHBOARD_ENDPOINT);
        endpoints.put("backendTechnologyModernizationDecision",
            AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrix",
            AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT);
        endpoints.put("officialVersionProtocolWatch", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT);
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("memoryRagReviewedTraceEvidenceManifest",
            AgentMemoryRagReviewedTraceEvidenceManifestResponse.MANIFEST_ENDPOINT);
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        endpoints.put("kubeManagerGovernanceOverview",
            "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildReviewPolicy(
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision,
        List<Map<String, Object>> expertRounds,
        List<Map<String, Object>> orchestrationRows,
        List<Map<String, Object>> a2aRows,
        List<Map<String, Object>> reviewGates,
        List<Map<String, Object>> blockedShortcuts,
        List<Map<String, Object>> disabledActions
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("aggregateReadModelOnly", true);
        policy.put("playbookEmbedded", playbook != null);
        policy.put("phase1RoadmapEmbedded", phase1Roadmap != null);
        policy.put("compatibilityEvidenceEmbedded", compatibilityEvidence != null);
        policy.put("officialWatchDashboardEmbedded", officialWatchDashboard != null);
        policy.put("backendDecisionEmbedded", backendDecision != null);
        policy.put("expertReviewRoundCount", expertRounds.size());
        policy.put("orchestrationReviewRowCount", orchestrationRows.size());
        policy.put("a2aEvidenceRowCount", a2aRows.size());
        policy.put("reviewGateCount", reviewGates.size());
        policy.put("blockedRuntimeShortcutCount", blockedShortcuts.size());
        policy.put("disabledRuntimeActionCount", disabledActions.size());
        policy.put("multiExpertReviewVisible", true);
        policy.put("officialSourceWinsOverConversationMemory", true);
        policy.put("requiresHumanGitReview", true);
        policy.put("runtimeControlAllowed", false);
        policy.put("a2aRuntimeHandoffAllowed", false);
        policy.put("mcpToolsCallAllowed", false);
        policy.put("toolExecutionAllowed", false);
        policy.put("dependencyUpgradeAllowedNow", false);
        policy.put("ciBlockingAllowedNow", false);
        policy.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety(
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("aggregateReadModelOnly", true);
        safety.put("playbookReadOnly", playbook != null && bool(playbook.safety(), "readOnly"));
        safety.put("phase1RoadmapReadOnly", phase1Roadmap != null && bool(phase1Roadmap.safety(), "readOnly"));
        safety.put("compatibilityEvidenceReadOnly",
            compatibilityEvidence != null && bool(compatibilityEvidence.safety(), "readOnly"));
        safety.put("officialWatchDashboardReadOnly",
            officialWatchDashboard != null && bool(officialWatchDashboard.safety(), "readOnly"));
        safety.put("backendDecisionReadOnly", backendDecision != null && bool(backendDecision.safety(), "readOnly"));
        safety.put("runtimeMutationAllowed", false);
        safety.put("runtimeControlAllowed", false);
        safety.put("runtimeUpgradeAllowedNow", false);
        safety.put("dependencyUpgradeAllowedNow", false);
        safety.put("compatibilityBranchCreationTriggered", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("kubeManagerCalls", false);
        safety.put("mcpToolCall", false);
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
        safety.put("evalRuntimeExecuted", false);
        safety.put("ciBlockingChanged", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        Map<String, Object> playbookPrivacy = playbook != null ? playbook.privacy() : Map.of();
        Map<String, Object> roadmapPrivacy = phase1Roadmap != null ? phase1Roadmap.privacy() : Map.of();
        Map<String, Object> evidencePrivacy = compatibilityEvidence != null ? compatibilityEvidence.privacy() : Map.of();
        Map<String, Object> dashboardPrivacy = officialWatchDashboard != null ? officialWatchDashboard.privacy() : Map.of();
        Map<String, Object> decisionPrivacy = backendDecision != null ? backendDecision.privacy() : Map.of();
        boolean containsRawPrincipal = truthyAny("containsRawPrincipal", playbookPrivacy, roadmapPrivacy,
            evidencePrivacy, dashboardPrivacy, decisionPrivacy);
        boolean containsRawOrganization = truthyAny("containsRawOrganization", playbookPrivacy, roadmapPrivacy,
            evidencePrivacy, dashboardPrivacy, decisionPrivacy);
        boolean containsRawConversation = truthyAny("containsRawConversation", playbookPrivacy, roadmapPrivacy,
            evidencePrivacy, dashboardPrivacy, decisionPrivacy);
        boolean containsRawPrompt = truthyAny("containsRawPrompt", playbookPrivacy, roadmapPrivacy, evidencePrivacy,
            dashboardPrivacy, decisionPrivacy);
        boolean containsRawDocument = truthyAny("containsRawDocument", playbookPrivacy, roadmapPrivacy,
            evidencePrivacy, dashboardPrivacy, decisionPrivacy);
        boolean containsRawEndpoint = truthyAny("containsRawEndpoint", playbookPrivacy, roadmapPrivacy,
            evidencePrivacy, dashboardPrivacy, decisionPrivacy);
        boolean containsAuthorizationHeader = truthyAny("containsAuthorizationHeader", playbookPrivacy,
            roadmapPrivacy, evidencePrivacy, dashboardPrivacy, decisionPrivacy);
        boolean containsToken = truthyAny("containsToken", playbookPrivacy, roadmapPrivacy, evidencePrivacy,
            dashboardPrivacy, decisionPrivacy);
        boolean containsPassword = truthyAny("containsPassword", playbookPrivacy, roadmapPrivacy, evidencePrivacy,
            dashboardPrivacy, decisionPrivacy);
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !(containsRawPrincipal
            || containsRawOrganization
            || containsRawConversation
            || containsRawPrompt
            || containsRawDocument
            || containsRawEndpoint
            || containsAuthorizationHeader
            || containsToken
            || containsPassword));
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", containsRawOrganization);
        privacy.put("containsRawConversation", containsRawConversation);
        privacy.put("containsRawPrompt", containsRawPrompt);
        privacy.put("containsRawDocument", containsRawDocument);
        privacy.put("containsRawEndpoint", containsRawEndpoint);
        privacy.put("containsAuthorizationHeader", containsAuthorizationHeader);
        privacy.put("containsToken", containsToken);
        privacy.put("containsPassword", containsPassword);
        privacy.put("containsRuntimeSecrets", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        privacy.put("mcpToolsCall", false);
        privacy.put("a2aRuntimeHandoff", false);
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        return Map.copyOf(privacy);
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

    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static String string(Map<String, Object> data, String key) {
        Object value = data != null ? data.get(key) : null;
        return value != null ? String.valueOf(value) : "";
    }

    private static int integer(Map<String, Object> data, String key) {
        Object value = data != null ? data.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream()
                .map(String::valueOf)
                .toList();
        }
        return List.of();
    }
}
