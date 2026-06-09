package com.atlas.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only evidence readiness projection for the advanced technology matrix.
 *
 * <p>Chinese teaching note: this response turns each "latest technology" lane into a reviewed-evidence
 * checklist. It is intentionally a control-plane mirror only: no runtime switch, no dependency upgrade,
 * no eval run, no trace discovery, no retrieval, and no kube-manager call happens here.</p>
 */
public record AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse(
    String schemaVersion,
    Instant generatedAt,
    String readinessStatus,
    String frontendTarget,
    boolean phase1TopTierGoalPreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean sourceMatrixEmbedded,
    boolean reviewedEvalEvidenceEmbedded,
    boolean memoryRagManifestEmbedded,
    boolean runtimeControlAllowed,
    boolean runtimeUpgradeAllowedNow,
    boolean dependencyUpgradeAllowedNow,
    boolean ciBlockingAllowedNow,
    boolean catalogMutationAllowed,
    int matrixItemCount,
    int evidenceRowCount,
    int blockedEvidenceRowCount,
    int reviewedTraceSetCount,
    int reviewedTraceAnchorCount,
    int memoryRagRequiredTraceSetCount,
    int memoryRagReviewedTraceSetCount,
    int blockingGateRowCount,
    int disabledRuntimeActionCount,
    List<Map<String, Object>> matrixEvidenceRows,
    List<Map<String, Object>> blockingGateRows,
    List<Map<String, Object>> disabledRuntimeActions,
    List<String> nextActions,
    AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix,
    AgentReviewedEvalTraceEvidenceResponse reviewedEvalTraceEvidence,
    AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryRagReviewedTraceEvidenceManifest,
    Map<String, Object> endpointMap,
    Map<String, Object> readinessPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION =
        "agent-advanced-technology-compatibility-matrix-evidence-readiness.v1";
    public static final String EVIDENCE_READINESS_ENDPOINT =
        "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness";

    public static AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse of(
        Instant generatedAt,
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix,
        AgentReviewedEvalTraceEvidenceResponse reviewedEvalTraceEvidence,
        AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryRagReviewedTraceEvidenceManifest
    ) {
        List<Map<String, Object>> evidenceRows = buildMatrixEvidenceRows(
            sourceMatrix,
            reviewedEvalTraceEvidence,
            memoryRagReviewedTraceEvidenceManifest
        );
        int blockedRows = countBlockedEvidenceRows(evidenceRows);
        List<Map<String, Object>> blockingGates = buildBlockingGateRows(
            reviewedEvalTraceEvidence,
            memoryRagReviewedTraceEvidenceManifest,
            blockedRows
        );
        List<Map<String, Object>> disabledActions = buildDisabledRuntimeActions(sourceMatrix);
        int reviewedTraceSets = reviewedEvalTraceEvidence != null
            ? reviewedEvalTraceEvidence.reviewedTraceSetCount()
            : 0;
        int reviewedTraceAnchors = reviewedEvalTraceEvidence != null
            ? reviewedEvalTraceEvidence.reviewedTraceAnchorCount()
            : 0;
        int memoryRequiredTraceSets = memoryRagReviewedTraceEvidenceManifest != null
            ? memoryRagReviewedTraceEvidenceManifest.requiredTraceSetCount()
            : 0;
        int memoryReviewedTraceSets = memoryRagReviewedTraceEvidenceManifest != null
            ? memoryRagReviewedTraceEvidenceManifest.reviewedTraceSetCount()
            : 0;
        return new AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse(
            SCHEMA_VERSION,
            generatedAt,
            readinessStatus(sourceMatrix, reviewedEvalTraceEvidence, memoryRagReviewedTraceEvidenceManifest,
                blockedRows),
            "vue-kube-manager advanced technology evidence readiness board",
            true,
            true,
            sourceMatrix != null,
            reviewedEvalTraceEvidence != null,
            memoryRagReviewedTraceEvidenceManifest != null,
            false,
            false,
            false,
            false,
            false,
            sourceMatrix != null ? sourceMatrix.matrixItemCount() : 0,
            evidenceRows.size(),
            blockedRows,
            reviewedTraceSets,
            reviewedTraceAnchors,
            memoryRequiredTraceSets,
            memoryReviewedTraceSets,
            blockingGates.size(),
            disabledActions.size(),
            evidenceRows,
            blockingGates,
            disabledActions,
            nextActions(blockedRows),
            sourceMatrix,
            reviewedEvalTraceEvidence,
            memoryRagReviewedTraceEvidenceManifest,
            buildEndpointMap(),
            buildReadinessPolicy(sourceMatrix, reviewedEvalTraceEvidence, memoryRagReviewedTraceEvidenceManifest,
                evidenceRows, blockingGates),
            buildSafety(sourceMatrix, reviewedEvalTraceEvidence, memoryRagReviewedTraceEvidenceManifest),
            buildPrivacy(sourceMatrix, reviewedEvalTraceEvidence, memoryRagReviewedTraceEvidenceManifest)
        );
    }

    private static String readinessStatus(
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix,
        AgentReviewedEvalTraceEvidenceResponse reviewedEvalTraceEvidence,
        AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryRagReviewedTraceEvidenceManifest,
        int blockedRows
    ) {
        if (sourceMatrix == null || reviewedEvalTraceEvidence == null
            || memoryRagReviewedTraceEvidenceManifest == null) {
            return "EVIDENCE_READINESS_SOURCE_MISSING";
        }
        if (sourceMatrix.runtimeControlAllowed()
            || sourceMatrix.runtimeUpgradeAllowedNow()
            || sourceMatrix.dependencyUpgradeAllowedNow()
            || reviewedEvalTraceEvidence.runtimeMutationAllowed()
            || reviewedEvalTraceEvidence.ciBlockingEnabled()
            || memoryRagReviewedTraceEvidenceManifest.runtimeControlAllowed()) {
            return "UNEXPECTED_RUNTIME_CONTROL_IN_EVIDENCE_SOURCE";
        }
        if (blockedRows == 0
            && reviewedEvalTraceEvidence.reviewedEvidenceReady()
            && memoryRagReviewedTraceEvidenceManifest.requiredTraceSetCount() > 0
            && memoryRagReviewedTraceEvidenceManifest.reviewedTraceSetCount()
            == memoryRagReviewedTraceEvidenceManifest.requiredTraceSetCount()) {
            return "EVIDENCE_READY_FOR_SEPARATE_RELEASE_REVIEW";
        }
        return "EVIDENCE_READINESS_BLOCKED_BY_REVIEWED_TRACE_GAPS";
    }

    private static List<Map<String, Object>> buildMatrixEvidenceRows(
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix,
        AgentReviewedEvalTraceEvidenceResponse reviewedEvalTraceEvidence,
        AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryRagManifest
    ) {
        if (sourceMatrix == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> matrixItem : sourceMatrix.matrixItems()) {
            rows.add(matrixEvidenceRow(matrixItem, reviewedEvalTraceEvidence, memoryRagManifest));
        }
        return List.copyOf(rows);
    }

    private static Map<String, Object> matrixEvidenceRow(
        Map<String, Object> matrixItem,
        AgentReviewedEvalTraceEvidenceResponse reviewedEvalTraceEvidence,
        AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryRagManifest
    ) {
        String laneId = string(matrixItem, "id");
        boolean reviewedEvalReady = reviewedEvalTraceEvidence != null
            && reviewedEvalTraceEvidence.reviewedEvidenceReady();
        boolean memoryLane = "memory-rag-graphrag-reranker-vectorstore".equals(laneId);
        boolean memoryEvidenceReady = !memoryLane || (memoryRagManifest != null
            && memoryRagManifest.requiredTraceSetCount() > 0
            && memoryRagManifest.reviewedTraceSetCount() == memoryRagManifest.requiredTraceSetCount());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("laneId", laneId);
        row.put("currentBaseline", string(matrixItem, "currentBaseline"));
        row.put("candidateTarget", string(matrixItem, "candidateTarget"));
        row.put("sourceReadiness", string(matrixItem, "readiness"));
        row.put("evidenceReadiness", evidenceReadiness(laneId, reviewedEvalReady, memoryEvidenceReady));
        row.put("blocked", !(reviewedEvalReady && memoryEvidenceReady));
        row.put("reviewedEvalTraceEvidenceRequired", true);
        row.put("reviewedEvalTraceEvidenceReady", reviewedEvalReady);
        row.put("memoryRagManifestRequired", memoryLane);
        row.put("memoryRagReviewedTraceEvidenceReady", memoryEvidenceReady);
        row.put("officialSourceReviewRequired", true);
        row.put("focusedCompatibilityTestsRequired", true);
        row.put("vueVisibilityRequired", true);
        row.put("humanGitReviewRequired", true);
        row.put("ciBlockingAllowedNow", false);
        row.put("runtimeControlAllowed", false);
        row.put("runtimeUpgradeAllowedNow", false);
        row.put("dependencyUpgradeAllowedNow", false);
        row.put("requiredEvidence", requiredEvidence(laneId));
        row.put("sourceMatrixRequiredEvidence", stringList(matrixItem.get("requiredEvidence")));
        row.put("adoptionRule", string(matrixItem, "adoptionRule"));
        row.put("relatedEndpoints", relatedEndpoints(laneId));
        return Map.copyOf(row);
    }

    private static String evidenceReadiness(String laneId,
                                            boolean reviewedEvalReady,
                                            boolean memoryEvidenceReady) {
        if (reviewedEvalReady && memoryEvidenceReady) {
            return "REVIEWED_EVIDENCE_PRESENT_RELEASE_STILL_SEPARATE";
        }
        return switch (laneId) {
            case "mcp-runtime-call-plane" -> "BLOCKED_BY_SAFE_TOOL_EXECUTOR_CONSENT_AUDIT_EVAL_EVIDENCE";
            case "a2a-multi-agent-provenance" -> "BLOCKED_BY_HANDOFF_PROVENANCE_AND_REVIEWED_TRACE_EVIDENCE";
            case "otel-genai-mcp-semconv" -> "BLOCKED_BY_REDACTION_CARDINALITY_AND_SEMCONV_STATUS_REVIEW";
            case "memory-rag-graphrag-reranker-vectorstore" ->
                "BLOCKED_BY_MEMORY_RAG_REVIEWED_TRACE_FIXTURES";
            case "kubernetes-manager-control-plane" -> "BLOCKED_BY_WRITE_RELEASE_GATE_AND_READBACK_EVIDENCE";
            case "supply-chain-ci-quality" -> "BLOCKED_BY_REAL_REVIEWED_TRACE_EVIDENCE_BEFORE_CI";
            default -> "BLOCKED_BY_COMPATIBILITY_AND_REVIEWED_TRACE_EVIDENCE";
        };
    }

    private static List<String> requiredEvidence(String laneId) {
        List<String> evidence = new ArrayList<>();
        evidence.add("reviewed-redacted-eval-trace-evidence");
        evidence.add("official-source-review-date-and-url");
        evidence.add("focused-security-observability-tests");
        evidence.add("vue-readonly-visibility");
        evidence.add("workspace-recovery-memory");
        evidence.add("human-git-review");
        switch (laneId) {
            case "mcp-runtime-call-plane" -> evidence.addAll(List.of(
                "explicit-consent-ui",
                "safe-tool-executor-binding-proof",
                "tenant-tool-policy",
                "durable-audit-prewrite-proof",
                "release-gate-eval-evidence"
            ));
            case "memory-rag-graphrag-reranker-vectorstore" -> evidence.addAll(List.of(
                "memory-rag-reviewed-trace-fixtures",
                "citation-source-digest-evidence",
                "tenant-privacy-negative-retrieval-proof",
                "durable-memory-lifecycle-proof"
            ));
            case "kubernetes-manager-control-plane" -> evidence.addAll(List.of(
                "idempotency-contract-proof",
                "operation-safety-allowlist-proof",
                "readback-contract-proof",
                "release-gate-receipt-proof"
            ));
            case "supply-chain-ci-quality" -> evidence.addAll(List.of(
                "sbom-artifact-review",
                "dependency-diff-review",
                "ci-blocking-release-decision"
            ));
            default -> evidence.add("compatibility-branch-green-evidence");
        }
        return List.copyOf(evidence);
    }

    private static Map<String, Object> relatedEndpoints(String laneId) {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("evidenceReadiness", EVIDENCE_READINESS_ENDPOINT);
        endpoints.put("sourceMatrix", AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT);
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        if ("memory-rag-graphrag-reranker-vectorstore".equals(laneId)) {
            endpoints.put("memoryRagReviewedTraceEvidenceManifest",
                AgentMemoryRagReviewedTraceEvidenceManifestResponse.MANIFEST_ENDPOINT);
            endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        }
        if ("mcp-runtime-call-plane".equals(laneId)) {
            endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        }
        if ("kubernetes-manager-control-plane".equals(laneId)) {
            endpoints.put("kubeManagerGovernanceOverview",
                "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview");
        }
        if ("supply-chain-ci-quality".equals(laneId)) {
            endpoints.put("releaseBlockingEvalGateContract",
                "/api/agent/observability/eval/release-blocking-gate-contract");
        }
        return Map.copyOf(endpoints);
    }

    private static int countBlockedEvidenceRows(List<Map<String, Object>> rows) {
        return (int) rows.stream()
            .filter(row -> Boolean.TRUE.equals(row.get("blocked")))
            .count();
    }

    private static List<Map<String, Object>> buildBlockingGateRows(
        AgentReviewedEvalTraceEvidenceResponse reviewedEvalTraceEvidence,
        AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryRagManifest,
        int blockedRows
    ) {
        return List.of(
            gate("source-matrix-present", "Compatibility matrix must be generated from official-source watch.",
                true, "READ_MODEL_REQUIRED"),
            gate("reviewed-eval-trace-evidence", "Every advanced technology lane needs reviewed redacted traces.",
                reviewedEvalTraceEvidence != null && reviewedEvalTraceEvidence.reviewedEvidenceReady(),
                status(reviewedEvalTraceEvidence != null && reviewedEvalTraceEvidence.reviewedEvidenceReady())),
            gate("memory-rag-reviewed-fixtures", "RAG, GraphRAG, reranker, and vector-store lanes need Memory/RAG fixture evidence.",
                memoryRagManifest != null
                    && memoryRagManifest.requiredTraceSetCount() > 0
                    && memoryRagManifest.reviewedTraceSetCount() == memoryRagManifest.requiredTraceSetCount(),
                status(memoryRagManifest != null
                    && memoryRagManifest.requiredTraceSetCount() > 0
                    && memoryRagManifest.reviewedTraceSetCount() == memoryRagManifest.requiredTraceSetCount())),
            gate("runtime-authority-closed", "Runtime control remains closed while evidence rows are blocked.",
                blockedRows == 0, blockedRows == 0 ? "READY_FOR_SEPARATE_REVIEW" : "BLOCKED"),
            gate("ci-blocking-release-decision", "CI blocking needs a separate reviewed release gate after real evidence exists.",
                false, "BLOCKED_NOW"),
            gate("vue-readonly-binding", "Vue may render the evidence board but must not expose enable buttons.",
                true, "READY_TO_BIND"),
            gate("human-git-review", "Promotion of trace anchors or dependency changes stays in source control review.",
                false, "HUMAN_REQUIRED")
        );
    }

    private static Map<String, Object> gate(String id, String requirement, boolean satisfied, String status) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("id", id);
        gate.put("requirement", requirement);
        gate.put("satisfied", satisfied);
        gate.put("status", status);
        gate.put("required", true);
        gate.put("runtimeBound", false);
        gate.put("runtimeControlAllowed", false);
        return Map.copyOf(gate);
    }

    private static String status(boolean ready) {
        return ready ? "READY" : "BLOCKED_BY_MISSING_REVIEWED_EVIDENCE";
    }

    private static List<Map<String, Object>> buildDisabledRuntimeActions(
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix
    ) {
        List<Map<String, Object>> actions = new ArrayList<>();
        if (sourceMatrix != null) {
            for (Map<String, Object> shortcut : sourceMatrix.blockedUpgradeShortcuts()) {
                actions.add(disabledAction(
                    string(shortcut, "id"),
                    "source-matrix-shortcut",
                    "The source compatibility matrix blocks this shortcut."
                ));
            }
        }
        actions.add(disabledAction("run-compatibility-branch-from-ui", "evidence-readiness",
            "Compatibility branches are developer/Git operations, not dashboard actions."));
        actions.add(disabledAction("run-candidate-discovery-from-this-endpoint", "evidence-readiness",
            "This endpoint does not query audit events or discover trace candidates."));
        actions.add(disabledAction("run-curation-review-from-this-endpoint", "evidence-readiness",
            "Curation review is a separate explicit endpoint and remains review-only."));
        actions.add(disabledAction("enable-mcp-tools-call", "evidence-readiness",
            "MCP tools/call requires a future SafeToolExecutor-bound release slice."));
        actions.add(disabledAction("enable-rag-runtime", "evidence-readiness",
            "Retrieval and prompt influence need reviewed Memory/RAG evidence first."));
        actions.add(disabledAction("enable-kube-manager-write-runtime", "evidence-readiness",
            "State-changing kube-manager writes remain behind release-gate evidence."));
        actions.add(disabledAction("enable-ci-blocking", "evidence-readiness",
            "CI blocking needs real reviewed trace anchors and a separate release decision."));
        return List.copyOf(actions);
    }

    private static Map<String, Object> disabledAction(String actionId, String source, String reason) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("actionId", actionId);
        action.put("source", source);
        action.put("reason", reason);
        action.put("enabledNow", false);
        action.put("buttonVisibleNow", false);
        action.put("clickHandlerAllowed", false);
        action.put("requiresSeparateReviewedSlice", true);
        return Map.copyOf(action);
    }

    private static List<String> nextActions(int blockedRows) {
        if (blockedRows == 0) {
            return List.of(
                "review-evidence-readiness-before-release-branch",
                "prepare-separate-release-gate-promotion",
                "keep-runtime-controls-hidden-until-release-review"
            );
        }
        return List.of(
            "capture-reviewed-redacted-eval-trace-evidence",
            "complete-memory-rag-reviewed-trace-fixtures",
            "map-each-advanced-technology-lane-to-focused-tests",
            "render-evidence-readiness-in-vue-without-enable-buttons",
            "prepare-human-git-review-before-any-dependency-or-runtime-change",
            "keep-nim-hpc-slurm-bcm-paused-for-phase2"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("advancedTechnologyCompatibilityMatrixEvidenceReadiness", EVIDENCE_READINESS_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrix",
            AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixVueBindingSpec",
            AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("memoryRagReviewedTraceEvidenceManifest",
            AgentMemoryRagReviewedTraceEvidenceManifestResponse.MANIFEST_ENDPOINT);
        endpoints.put("releaseBlockingEvalGateContract",
            "/api/agent/observability/eval/release-blocking-gate-contract");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("topTierVueWorkbenchImplementationPackage",
            AgentTopTierVueWorkbenchImplementationPackageResponse.PACKAGE_ENDPOINT);
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        endpoints.put("kubeManagerGovernanceOverview",
            "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildReadinessPolicy(
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix,
        AgentReviewedEvalTraceEvidenceResponse reviewedEvalTraceEvidence,
        AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryRagManifest,
        List<Map<String, Object>> evidenceRows,
        List<Map<String, Object>> blockingGates
    ) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("evidenceReadinessOnly", true);
        policy.put("sourceMatrixEmbedded", sourceMatrix != null);
        policy.put("reviewedEvalEvidenceEmbedded", reviewedEvalTraceEvidence != null);
        policy.put("memoryRagManifestEmbedded", memoryRagManifest != null);
        policy.put("matrixItemCount", sourceMatrix != null ? sourceMatrix.matrixItemCount() : 0);
        policy.put("evidenceRowCount", evidenceRows.size());
        policy.put("blockedEvidenceRowCount", countBlockedEvidenceRows(evidenceRows));
        policy.put("blockingGateRowCount", blockingGates.size());
        policy.put("callerTraceIdsAccepted", false);
        policy.put("runtimeUpgradeAllowedNow", false);
        policy.put("dependencyUpgradeAllowedNow", false);
        policy.put("runtimeControlAllowed", false);
        policy.put("ciBlockingAllowedNow", false);
        policy.put("catalogMutationAllowed", false);
        policy.put("requiresHumanGitReview", true);
        policy.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> buildSafety(
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix,
        AgentReviewedEvalTraceEvidenceResponse reviewedEvalTraceEvidence,
        AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryRagManifest
    ) {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("evidenceReadinessOnly", true);
        safety.put("sourceMatrixReadOnly", sourceMatrix != null && bool(sourceMatrix.safety(), "readOnly"));
        safety.put("reviewedEvalEvidenceReadOnly",
            reviewedEvalTraceEvidence != null && bool(reviewedEvalTraceEvidence.safety(), "readOnly"));
        safety.put("memoryRagManifestReadOnly",
            memoryRagManifest != null && bool(memoryRagManifest.safety(), "readOnly"));
        safety.put("runtimeMutationAllowed", false);
        safety.put("runtimeControlAllowed", false);
        safety.put("runtimeUpgradeAllowedNow", false);
        safety.put("dependencyUpgradeAllowedNow", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("ciBlockingAllowedNow", false);
        safety.put("candidateDiscoveryInvoked", false);
        safety.put("curationReviewInvoked", false);
        safety.put("evalRuntimeExecuted", false);
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

    private static Map<String, Object> buildPrivacy(
        AgentAdvancedTechnologyCompatibilityMatrixResponse sourceMatrix,
        AgentReviewedEvalTraceEvidenceResponse reviewedEvalTraceEvidence,
        AgentMemoryRagReviewedTraceEvidenceManifestResponse memoryRagManifest
    ) {
        Map<String, Object> matrixPrivacy = sourceMatrix != null ? sourceMatrix.privacy() : Map.of();
        Map<String, Object> evalPrivacy = reviewedEvalTraceEvidence != null
            ? reviewedEvalTraceEvidence.privacy()
            : Map.of();
        Map<String, Object> manifestPrivacy = memoryRagManifest != null ? memoryRagManifest.privacy() : Map.of();
        boolean containsRawPrincipal = truthyAny("containsRawPrincipal", matrixPrivacy, evalPrivacy, manifestPrivacy);
        boolean containsRawOrganization = truthyAny("containsRawOrganization", matrixPrivacy, evalPrivacy,
            manifestPrivacy);
        boolean containsRawPrompt = truthyAny("containsRawPrompt", matrixPrivacy, evalPrivacy, manifestPrivacy);
        boolean containsRawDocument = truthyAny("containsRawDocument", matrixPrivacy, evalPrivacy, manifestPrivacy);
        boolean containsAuthorizationHeader = truthyAny("containsAuthorizationHeader", matrixPrivacy, evalPrivacy,
            manifestPrivacy);
        boolean containsToken = truthyAny("containsToken", matrixPrivacy, evalPrivacy, manifestPrivacy);
        boolean containsPassword = truthyAny("containsPassword", matrixPrivacy, evalPrivacy, manifestPrivacy);
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !containsRawPrincipal
            && !containsRawOrganization
            && !containsRawPrompt
            && !containsRawDocument
            && !containsAuthorizationHeader
            && !containsToken
            && !containsPassword);
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", containsRawOrganization);
        privacy.put("containsRawPrompt", containsRawPrompt);
        privacy.put("containsRawDocument", containsRawDocument);
        privacy.put("containsAuthorizationHeader", containsAuthorizationHeader);
        privacy.put("containsToken", containsToken);
        privacy.put("containsPassword", containsPassword);
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsRuntimeSecrets", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
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

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
