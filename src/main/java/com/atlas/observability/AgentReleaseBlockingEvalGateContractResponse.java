package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only release-blocking eval gate contract for Phase 1.
 */
public record AgentReleaseBlockingEvalGateContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean releaseBlockingGateDefined,
    boolean releaseBlockingEnabled,
    boolean ciBlockingEnabled,
    boolean releaseGateCanOpenNow,
    boolean runtimeMutationAllowed,
    boolean reviewedEvidenceReady,
    boolean gateBundleReleaseEligible,
    int traceSetCount,
    int reviewedTraceSetCount,
    int reviewedTraceAnchorCount,
    int emptyTraceSets,
    List<Map<String, Object>> releaseGateChecks,
    List<Map<String, Object>> traceSetReleaseRows,
    List<String> blockedReasons,
    List<String> promotionPlan,
    List<String> nextActions,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-release-blocking-eval-gate-contract.v1";

    public static AgentReleaseBlockingEvalGateContractResponse of(
        Instant generatedAt,
        AgentReviewedEvalTraceEvidenceResponse reviewedEvidence,
        AgentEvalWorkbenchGateBundleSummaryResponse gateBundleSummary
    ) {
        boolean reviewedReady = reviewedEvidence != null && reviewedEvidence.reviewedEvidenceReady();
        boolean bundleEligible = gateBundleSummary != null && gateBundleSummary.releaseEligible();
        int emptyTraceSets = gateBundleSummary != null ? gateBundleSummary.emptyTraceSets() : 0;
        boolean canOpen = reviewedReady && bundleEligible && emptyTraceSets == 0;
        return new AgentReleaseBlockingEvalGateContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            canOpen ? "READY_FOR_MANUAL_RELEASE_GATE_PROMOTION" : "BLOCKED_BY_REVIEWED_TRACE_EVIDENCE",
            "Phase 1 release-blocking eval gate contract",
            true,
            true,
            false,
            false,
            false,
            false,
            reviewedReady,
            bundleEligible,
            reviewedEvidence != null ? reviewedEvidence.traceSetCount() : 0,
            reviewedEvidence != null ? reviewedEvidence.reviewedTraceSetCount() : 0,
            reviewedEvidence != null ? reviewedEvidence.reviewedTraceAnchorCount() : 0,
            emptyTraceSets,
            releaseGateChecks(reviewedReady, bundleEligible, emptyTraceSets),
            traceSetReleaseRows(reviewedEvidence, gateBundleSummary),
            blockedReasons(reviewedReady, bundleEligible, emptyTraceSets),
            buildPromotionPlan(),
            nextActions(canOpen),
            buildEndpointMap(),
            buildSafety(),
            privacy(reviewedEvidence, gateBundleSummary)
        );
    }

    private static List<Map<String, Object>> releaseGateChecks(boolean reviewedReady,
                                                               boolean bundleEligible,
                                                               int emptyTraceSets) {
        return List.of(
            check("reviewed-trace-evidence", reviewedReady, "All Phase 1 trace sets must contain reviewed redacted anchors."),
            check("gate-bundle-release-eligible", bundleEligible, "The compact gate bundle must pass with no failed trace sets."),
            check("no-empty-trace-sets", emptyTraceSets == 0, "Release gates fail closed while any curated trace set is empty."),
            check("human-git-review-complete", false, "Trace-set changes and release gate promotion require human source review."),
            check("ci-blocking-switch-absent", false, "CI blocking remains disabled until a later explicit wiring slice."),
            check("runtime-authority-unchanged", true, "This contract cannot grant Tool, MCP, kube-manager, retrieval, or memory authority.")
        );
    }

    private static List<Map<String, Object>> traceSetReleaseRows(
        AgentReviewedEvalTraceEvidenceResponse reviewedEvidence,
        AgentEvalWorkbenchGateBundleSummaryResponse gateBundleSummary
    ) {
        Map<String, Map<String, Object>> reviewedRows = new LinkedHashMap<>();
        if (reviewedEvidence != null) {
            for (Map<String, Object> row : reviewedEvidence.traceSetEvidence()) {
                reviewedRows.put(safeText(row.get("traceSetId")), row);
            }
        }
        Map<String, Map<String, Object>> gateRows = new LinkedHashMap<>();
        if (gateBundleSummary != null) {
            for (Map<String, Object> row : gateBundleSummary.traceSetGateRows()) {
                gateRows.put(safeText(row.get("traceSetId")), row);
            }
        }
        return reviewedRows.keySet().stream()
            .map(traceSetId -> traceSetReleaseRow(traceSetId, reviewedRows.get(traceSetId), gateRows.get(traceSetId)))
            .toList();
    }

    private static Map<String, Object> traceSetReleaseRow(String traceSetId,
                                                          Map<String, Object> reviewedRow,
                                                          Map<String, Object> gateRow) {
        boolean reviewed = truthy(reviewedRow, "reviewedEvidencePresent");
        boolean pass = truthy(gateRow, "pass");
        boolean empty = truthy(gateRow, "emptyInput");
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("traceSetId", traceSetId);
        row.put("reviewedEvidencePresent", reviewed);
        row.put("gatePass", pass);
        row.put("emptyInput", empty);
        row.put("releaseBlockingReady", reviewed && pass && !empty);
        row.put("releaseBlockingAllowedNow", false);
        row.put("reviewStatus", reviewedRow != null ? safeText(reviewedRow.get("status")) : "MISSING_REVIEW_ROW");
        row.put("gateStatus", gateRow != null ? safeText(gateRow.get("status")) : "MISSING_GATE_ROW");
        row.put("reviewedTraceAnchorCount", reviewedRow != null ? safeNumber(reviewedRow.get("reviewedTraceAnchorCount")) : 0);
        row.put("evaluatedCases", gateRow != null ? safeNumber(gateRow.get("evaluatedCases")) : 0);
        row.put("nextAction", reviewed && pass && !empty ? "prepare-manual-release-gate-promotion" : "complete-reviewed-evidence-and-gate-bundle");
        return Map.copyOf(row);
    }

    private static List<String> blockedReasons(boolean reviewedReady,
                                               boolean bundleEligible,
                                               int emptyTraceSets) {
        java.util.ArrayList<String> reasons = new java.util.ArrayList<>();
        if (!reviewedReady) {
            reasons.add("reviewed-redacted-trace-evidence-missing");
        }
        if (!bundleEligible) {
            reasons.add("gate-bundle-not-release-eligible");
        }
        if (emptyTraceSets > 0) {
            reasons.add("empty-trace-sets-fail-closed");
        }
        reasons.add("human-release-review-not-bound");
        reasons.add("ci-blocking-switch-intentionally-absent");
        reasons.add("release-blocking-runtime-wire-not-implemented");
        return List.copyOf(reasons);
    }

    private static List<String> buildPromotionPlan() {
        return List.of(
            "populate-reviewed-redacted-trace-anchors-through-git-review",
            "regenerate-gate-bundle-after-reviewed-catalog-merge",
            "confirm-all-trace-set-gates-pass-without-empty-input",
            "add-human-release-approval-evidence",
            "wire-ci-to-consume-compact-gate-artifact-in-a-separate-release",
            "keep-runtime-authority-unchanged-until-gate-contract-is-proven"
        );
    }

    private static List<String> nextActions(boolean canOpen) {
        if (canOpen) {
            return List.of(
                "prepare-human-release-review",
                "draft-ci-blocking-wiring-change",
                "keep-runtime-switch-disabled-until-ci-contract-lands"
            );
        }
        return List.of(
            "complete-reviewed-trace-evidence",
            "regenerate-gate-bundle-after-reviewed-evidence",
            "inspect-release-blocking-contract-before-ci-wiring",
            "keep-ci-blocking-disabled"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("releaseBlockingEvalGateContract", "/api/agent/observability/eval/release-blocking-gate-contract");
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("gateBundleSummary", "/api/agent/observability/eval/workbench/gate-bundle-summary");
        endpoints.put("rawGateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        endpoints.put("workbenchOverview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("traceReplayTimeline", "/api/agent/observability/replay/trace/{traceId}");
        endpoints.put("traceEvalReport", "/api/agent/observability/eval/trace/{traceId}");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("contractOnly", true);
        safety.put("summaryOnly", true);
        safety.put("releaseGateCanOpenNow", false);
        safety.put("releaseBlockingEnabled", false);
        safety.put("ciBlockingEnabled", false);
        safety.put("runtimeMutationAllowed", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
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
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> privacy(AgentReviewedEvalTraceEvidenceResponse reviewedEvidence,
                                               AgentEvalWorkbenchGateBundleSummaryResponse gateBundleSummary) {
        Map<String, Object> reviewedPrivacy = reviewedEvidence != null ? reviewedEvidence.privacy() : Map.of();
        Map<String, Object> bundlePrivacy = gateBundleSummary != null ? gateBundleSummary.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(reviewedPrivacy, "containsRawPrincipal") || truthy(bundlePrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(reviewedPrivacy, "containsRawOrganization") || truthy(bundlePrivacy, "containsRawOrganization");
        boolean containsRawConversation = truthy(reviewedPrivacy, "containsRawConversation") || truthy(bundlePrivacy, "containsRawConversation");
        boolean containsRawEndpoints = truthy(reviewedPrivacy, "containsRawEndpoints") || truthy(bundlePrivacy, "containsRawEndpoints");
        boolean containsRawReason = truthy(reviewedPrivacy, "containsRawReason") || truthy(bundlePrivacy, "containsRawReason");
        boolean containsRawParameterValues = truthy(reviewedPrivacy, "containsRawParameterValues") || truthy(bundlePrivacy, "containsRawParameterValues");
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !(containsRawPrincipal
            || containsRawOrganization
            || containsRawConversation
            || containsRawEndpoints
            || containsRawReason
            || containsRawParameterValues));
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", containsRawOrganization);
        privacy.put("containsRawConversation", containsRawConversation);
        privacy.put("containsRawEndpoints", containsRawEndpoints);
        privacy.put("containsRawReason", containsRawReason);
        privacy.put("containsRawParameterValues", containsRawParameterValues);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("deterministic", true);
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> check(String id, boolean pass, String requirement) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("id", id);
        check.put("pass", pass);
        check.put("required", true);
        check.put("requirement", requirement);
        check.put("runtimeBound", false);
        return Map.copyOf(check);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static String safeText(Object value) {
        return value != null ? value.toString() : "";
    }

    private static int safeNumber(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }
}
