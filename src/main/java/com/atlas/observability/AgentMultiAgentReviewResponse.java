package com.atlas.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 Multi-Agent / Expert Review 聚合读模型。
 *
 * <p>中文说明：这个 record 是前端“多 Agent / 多专家审阅页面”的后端契约。
 * 它把多个已经存在的 top-tier 读模型合并成一个稳定响应，帮助学习者看到：
 * 哪些专家轮次必须参与、哪些 A2A/handoff 证据仍然缺失、哪些 release gate 还没满足、
 * 哪些 runtime shortcut 被明确阻断、哪些按钮即使前端想做也必须保持不可见。</p>
 *
 * <p>设计原则：这里的 Multi-Agent 先是审阅证据与 provenance，不是 runtime 编排。
 * 所有字段都必须保持只读；如果任一源读模型意外暴露运行时权限，本响应要进入 fail-closed 状态，
 * 让前端显示异常，而不是继续渲染成“可执行”。</p>
 *
 * <p>安全边界：本响应不会执行 A2A handoff、MCP tools/call、Tool、RAG、eval、kube-manager 写操作，
 * 也不会写 audit/memory、创建兼容分支、升级依赖或触碰 Phase 2 NIM/HPC/Slurm/BCM。</p>
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

    /**
     * 从多个后端拥有的只读证据源生成聚合响应。
     *
     * <p>中文说明：这个工厂方法只做结构化投影，不调用任何运行时执行器。
     * 参数允许为 null，是为了让缺失源读模型时响应能够 fail-closed，而不是抛出空指针后让前端失去恢复信息。</p>
     */
    public static AgentMultiAgentReviewResponse of(
        Instant generatedAt,
        AgentTopTierTechnologyIntroductionPlaybookResponse playbook,
        AgentPhase1ExecutionRoadmapResponse phase1Roadmap,
        AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse compatibilityEvidence,
        AgentOfficialVersionProtocolWatchDashboardResponse officialWatchDashboard,
        AgentBackendTechnologyModernizationDecisionResponse backendDecision
    ) {
        // 专家审阅轮次来自技术引入 playbook；这里补上前端需要的角色、状态和禁用运行时字段。
        List<Map<String, Object>> expertRounds = buildExpertReviewRounds(playbook);
        // 编排审阅行来自 Phase 1 roadmap；它说明每个里程碑应由哪些专家并行审阅。
        List<Map<String, Object>> orchestrationRows = buildOrchestrationReviewRows(phase1Roadmap);
        // A2A/handoff 行是 provenance 证据，不是 handoff 执行计划；所有 runtime 字段必须为 false。
        List<Map<String, Object>> a2aRows = buildA2aProvenanceRows(
            playbook,
            phase1Roadmap,
            compatibilityEvidence,
            officialWatchDashboard,
            backendDecision
        );
        // Review gate 行把 release gate、dependency gate、blocking gate、modernization gate 放到同一张表里。
        List<Map<String, Object>> reviewGates = buildReviewGateRows(
            playbook,
            phase1Roadmap,
            compatibilityEvidence,
            officialWatchDashboard,
            backendDecision
        );
        // blocked shortcut 行用于教学：解释为什么“直接升级、直接 handoff、直接 tools/call”会破坏顶级 Agent 目标。
        List<Map<String, Object>> blockedShortcuts = buildBlockedRuntimeShortcuts(
            playbook,
            officialWatchDashboard,
            backendDecision
        );
        // disabled action 行给前端提供明确的不可见/不可点击动作清单，防止 UI 自己发明 runtime 按钮。
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

    /**
     * 计算聚合响应状态。
     *
     * <p>中文说明：顶级 Agent 的治理接口必须 fail-closed。
     * 只要任何源读模型缺失，就返回 source missing；只要任何源读模型意外打开 runtime/control/upgrade/CI/catalog 权限，
     * 就返回 unexpected runtime authority。只有所有源都存在且保持只读时，才声明 review ready 但 handoff closed。</p>
     */
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

    /**
     * 汇总 Phase 1 顶级目标是否仍被所有源读模型保留。
     *
     * <p>中文说明：这里采用“缺失源不直接否定目标”的策略，因为缺失源已经由 reviewStatus fail-closed 表达；
     * 这个布尔值只回答“已存在的源是否都没有降低一期顶级 Agent 目标”。</p>
     */
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

    /**
     * 把 playbook 中的专家审阅轮次转换成前端可渲染行。
     *
     * <p>中文说明：专家审阅行是“审阅工作流证据”，不是角色授权。
     * 即使 canBeParallelized=true，也只表示这些审阅可以并行推进，不表示可以跳过 release gate 或打开 runtime。</p>
     */
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

    /** 将 playbook 的 review id 映射为更适合前端展示和学习理解的专家角色名。 */
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

    /**
     * 根据 Phase 1 roadmap 生成编排审阅行。
     *
     * <p>中文说明：每个 roadmap step 都绑定一个或多个专家审阅轮次。
     * 这里故意把 runtimeControlAllowed、a2aRuntimeHandoffAllowed、mcpToolsCallAllowed、toolExecutionAllowed 全部写死为 false，
     * 因为 roadmap 只能教“下一步该审什么”，不能成为“下一步可执行什么”的权限来源。</p>
     */
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

    /**
     * 为每个路线图步骤选择需要参与的专家轮次。
     *
     * <p>中文说明：这不是调度系统，只是后端给前端的审阅建议。
     * 真正的责任分配仍需要 Git/人工审阅流程确认。</p>
     */
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

    /**
     * 从多个来源收集 A2A / handoff 相关证据。
     *
     * <p>中文说明：A2A provenance 必须跨 playbook、roadmap、evidence readiness、official watch、backend decision 对齐。
     * 只有多个源都能解释“为什么现在不能 handoff”，前端才不会把 A2A 当成一个简单开关。</p>
     */
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

    /** 从技术引入 playbook 中提取 A2A lane，表达“这是技术引入路线，不是 runtime handoff”。 */
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

    /** 从 Phase 1 roadmap 中提取 handoff 相关步骤，表达“这是未来路线图步骤，不是当前执行入口”。 */
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

    /** 从 evidence readiness 中提取 A2A lane，表达 reviewed trace / provenance 证据仍是 runtime 前置条件。 */
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

    /** 从官方 watch dashboard 中提取 A2A track，表达官方协议被跟踪但没有授予本地 runtime authority。 */
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

    /** 从后端现代化决策中提取 A2A lane，表达 Java/Spring 控制平面仍然保留本地权限边界。 */
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

    /**
     * 构造单条 A2A provenance 行。
     *
     * <p>中文说明：这行的重点不是“可以 handoff”，而是列出 handoff 之前必须存在的证据：
     * Agent Card 导出策略、task/artifact custody、本地权限证明、trace/audit/replay、eval coverage。
     * 因此 runtime handoff 和 runtime control 都必须为 false。</p>
     */
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

    /** 统一判断文本是否属于 A2A 或 handoff 相关证据，避免每个来源各写一套字符串判断。 */
    private static boolean containsA2aOrHandoff(String value) {
        String normalized = value != null ? value.toLowerCase() : "";
        return normalized.contains("a2a") || normalized.contains("handoff");
    }

    /**
     * 聚合所有与发布/证据/依赖/现代化相关的 review gate。
     *
     * <p>中文说明：前端需要一张统一的 gate 表来教学，但每类 gate 的来源不同：
     * playbook 给 release gate，roadmap 给 dependency gate，evidence readiness 给 blocking gate，
     * official dashboard 给 adoption gate，backend decision 给 modernization gate。
     * 这里统一格式，不改变任何 gate 的真实权限状态。</p>
     */
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

    /**
     * 将不同来源的 gate 行统一为前端表格行。
     *
     * <p>中文说明：源模型字段不完全一致，有的叫 requirement，有的叫 summary，有的有 status，
     * 有的只有 satisfied/satisfiedNow。这里做兼容投影，但 runtimeControlAllowed 仍固定为 false，
     * 防止“gate ready”被误解成“runtime 可执行”。</p>
     */
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

    /**
     * 聚合所有被阻断的 runtime shortcut。
     *
     * <p>中文说明：blocked shortcut 是学习面板里非常重要的“负空间”。
     * 顶级 Agent 不只是展示能做什么，也必须展示为什么不能直接升级依赖、直接 A2A handoff、
     * 直接 MCP tools/call、直接打开检索或 kube-manager 写操作。</p>
     */
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

    /**
     * 将不同来源的 shortcut 统一成前端可渲染行。
     *
     * <p>中文说明：无论源模型字段叫 reason 还是 summary，这里都统一成 reason。
     * allowedNow 和 buttonVisibleNow 必须为 false，用来约束前端不要生成隐藏执行按钮。</p>
     */
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

    /**
     * 构建 disabled runtime action 清单。
     *
     * <p>中文说明：这里同时包含本聚合模型自己的禁用动作，以及 evidence/dashboard 源模型已经声明的禁用动作。
     * 这样前端只消费一个接口，也能知道哪些按钮绝对不应该出现：A2A handoff、MCP tools/call、
     * Tool 执行、RAG 检索、依赖升级、kube-manager 写、eval runtime、CI blocking、memory write、Phase 2 域重开。</p>
     */
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

    /** 创建一条由本聚合模型声明的禁用动作，所有可点击/可见/可执行标志都必须关闭。 */
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

    /**
     * 兼容源模型中的 disabled action 字段差异。
     *
     * <p>中文说明：有的源模型字段叫 actionId，有的叫 id；有的说明叫 reason，有的叫 disabledReason。
     * 这里统一成 actionId/reason，同时重新写入 enabledNow=false、buttonVisibleNow=false、clickHandlerAllowed=false，
     * 避免源模型将来字段变化时前端误认为按钮可以打开。</p>
     */
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

    /** 提取禁用动作 id 列表，给前端做摘要卡片或搜索过滤使用，不承载任何权限含义。 */
    private static List<String> blockedActions(List<Map<String, Object>> disabledActions) {
        List<String> actions = new ArrayList<>();
        for (Map<String, Object> action : disabledActions) {
            actions.add(string(action, "actionId"));
        }
        return List.copyOf(actions);
    }

    /**
     * 推荐实现顺序。
     *
     * <p>中文说明：顺序表是教学路线，不是自动执行计划。
     * 后续每一步仍需要测试、文档、review、commit/push 和恢复记忆。</p>
     */
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

    /** 学习笔记直接进入响应，让前端可以把架构原则展示给用户，而不是藏在代码或聊天记录里。 */
    private static List<String> buildLearningNotes() {
        return List.of(
            "Multi-agent review is an evidence workflow before it is a runtime handoff protocol.",
            "A top-tier Agent keeps Java/Spring as local authority while learning MCP, A2A, RAG, eval, and observability as governed contracts.",
            "Expert rounds can run in parallel, but runtime authority still needs reviewed traces, release gates, audit evidence, and Git review.",
            "A2A provenance starts with Agent Card/task/artifact custody, not with delegating local RBAC or Tool authority.",
            "The frontend should render blocked shortcuts and disabled actions as learning evidence, not as missing features."
        );
    }

    /**
     * 统一端点索引。
     *
     * <p>中文说明：endpointMap 用来帮助前端和学习者追溯每一块证据的来源。
     * 它只暴露 GET/read-model 路径，不包含 POST、tools/call、handoff 或运行时控制端点。</p>
     */
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

    /**
     * 构建 reviewPolicy。
     *
     * <p>中文说明：policy 是给前端渲染和测试用的“契约摘要”。
     * 它把本响应的只读性质、嵌入源、行数、专家审阅可见性和禁止运行时动作集中表达，
     * 防止前端从某个单独字段推断出错误权限。</p>
     */
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

    /**
     * 构建安全边界 map。
     *
     * <p>中文说明：这里采用显式 false 列表，而不是省略字段。
     * 对 Agent 项目来说，“没写”容易被前端或后续开发者误解；显式 false 可以让测试、文档和 UI
     * 同时看到 runtime、Tool、MCP、A2A、RAG、kube-manager、eval、CI、memory/audit 都没有被打开。</p>
     */
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

    /**
     * 构建隐私边界 map。
     *
     * <p>中文说明：隐私字段通过所有源模型做 OR 聚合；只要任一源暴露 raw principal、prompt、document、
     * endpoint、token 或 password，本聚合模型就不能声称 redactedOnly=true。
     * 这种保守聚合能避免“某个下游源泄露了敏感信息，但聚合层仍显示安全”的错误。</p>
     */
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

    /** 多个源 map 的布尔字段只要任意为 true，就按 true 处理，用于隐私泄漏风险的保守聚合。 */
    @SafeVarargs
    private static boolean truthyAny(String key, Map<String, Object>... maps) {
        for (Map<String, Object> map : maps) {
            if (bool(map, key)) {
                return true;
            }
        }
        return false;
    }

    /** 安全读取布尔字段；只有显式 Boolean.TRUE 才视为 true，缺失、null、字符串都不自动放大权限。 */
    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    /** 安全读取字符串字段；缺失时返回空字符串，避免响应组装时出现 NPE。 */
    private static String string(Map<String, Object> data, String key) {
        Object value = data != null ? data.get(key) : null;
        return value != null ? String.valueOf(value) : "";
    }

    /** 安全读取整数字段；只有 Number 才转换，避免把字符串误判为可信数值。 */
    private static int integer(Map<String, Object> data, String key) {
        Object value = data != null ? data.get(key) : null;
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    /** 选择第一个非空文本，用于兼容不同源模型中的字段命名差异。 */
    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    /** 安全读取字符串列表；非 List 值直接返回空列表，避免把异常源字段传给前端。 */
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
