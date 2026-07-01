package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frontend-ready review model for a trace-set catalog patch proposal.
 *
 * <p>This is a Git-review helper for the future vue-kube-manager eval
 * workbench. It explains the proposed patch and review checklist, but it never
 * applies the patch or writes the trace-set catalog at runtime.</p>
 */
public record AgentEvalWorkbenchCatalogPatchReviewResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String proposalVerdict,
    boolean readyForGitReview,
    int originalTraceSetTraceCount,
    int candidateTraceCount,
    int addedTraceCount,
    int proposedTraceSetTraceCount,
    List<String> candidateTraceIds,
    List<String> addedTraceIds,
    List<String> proposedTraceIds,
    AgentEvalWorkbenchTraceSetView traceSetView,
    AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
    List<Map<String, Object>> patchOperations,
    Map<String, Object> traceDelta,
    Map<String, Object> candidateGateSummary,
    Map<String, Object> reviewedFixtureReadiness,
    List<String> reviewChecklist,
    List<String> nextActions,
    Map<String, Object> endpointTemplates,
    Map<String, Object> workbenchPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-workbench-catalog-patch-review.v1";

    public static AgentEvalWorkbenchCatalogPatchReviewResponse from(
        AgentEvalTraceSetDefinition definition,
        AgentEvalTraceSetGateArtifact traceSetGate,
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        return from(definition, traceSetGate, proposal, null);
    }

    public static AgentEvalWorkbenchCatalogPatchReviewResponse from(
        AgentEvalTraceSetDefinition definition,
        AgentEvalTraceSetGateArtifact traceSetGate,
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
        AgentReviewedTraceFixtureManifestResponse reviewedFixtureManifest) {
        AgentEvalWorkbenchTraceSetView view = AgentEvalWorkbenchTraceSetView.from(definition, traceSetGate);
        List<String> candidateTraceIds = proposal != null ? List.copyOf(proposal.candidateTraceIds()) : List.of();
        List<String> addedTraceIds = proposal != null ? List.copyOf(proposal.addedTraceIds()) : List.of();
        List<String> proposedTraceIds = proposal != null ? List.copyOf(proposal.proposedTraceIds()) : List.of();
        Map<String, Object> reviewedFixtureReadiness = buildReviewedFixtureReadiness(
            proposal,
            view,
            reviewedFixtureManifest
        );
        return new AgentEvalWorkbenchCatalogPatchReviewResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            proposal != null ? proposal.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION,
            proposal != null ? proposal.traceSetId() : safeText(definition != null ? definition.id() : ""),
            proposal != null ? proposal.traceSetTitle() : safeText(definition != null ? definition.title() : ""),
            proposal != null ? proposal.suiteId() : safeText(definition != null ? definition.suiteId() : ""),
            proposal != null ? proposal.proposalVerdict() : "PROPOSAL_UNAVAILABLE",
            proposal != null && proposal.readyForGitReview(),
            proposal != null ? proposal.originalTraceSetTraceCount() : 0,
            proposal != null ? proposal.candidateTraceCount() : 0,
            proposal != null ? proposal.addedTraceCount() : 0,
            proposedTraceIds.size(),
            candidateTraceIds,
            addedTraceIds,
            proposedTraceIds,
            view,
            proposal,
            buildPatchOperations(proposal),
            buildTraceDelta(proposal),
            buildCandidateGateSummary(proposal),
            reviewedFixtureReadiness,
            buildReviewChecklist(proposal, reviewedFixtureReadiness),
            buildNextActions(proposal, reviewedFixtureReadiness),
            buildEndpointTemplates(view.id()),
            buildWorkbenchPolicy(proposal, view),
            privacyProof(proposal, view)
        );
    }

    /**
     * 汇总 reviewed fixture 的准备度。
     *
     * <p>中文说明：这里故意只嵌入 manifest 的摘要和当前 trace set 覆盖状态，不把 fixtureRows 原样塞进
     * catalog patch review。前端需要知道“是否具备 merge 前证据”，但不能获得运行时上传、写 catalog、
     * 执行 eval/replay 或调用外部系统的权力。</p>
     */
    private static Map<String, Object> buildReviewedFixtureReadiness(
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
        AgentEvalWorkbenchTraceSetView view,
        AgentReviewedTraceFixtureManifestResponse manifest) {
        String traceSetId = proposal != null ? proposal.traceSetId() : safeText(view != null ? view.id() : "");
        Map<String, Object> coverage = currentTraceSetCoverage(traceSetId, manifest);
        boolean manifestAvailable = manifest != null;
        boolean fixturePresent = Boolean.TRUE.equals(coverage.get("reviewedFixtureFilePresent"));
        boolean catalogTraceIdsPresent = Boolean.TRUE.equals(coverage.get("catalogTraceIdsPresent"));
        boolean patchAddsTraceIds = proposal != null && proposal.addedTraceCount() > 0;
        Map<String, Object> readiness = new LinkedHashMap<>();
        readiness.put("schemaVersion", manifestAvailable ? manifest.schemaVersion() : "");
        readiness.put("manifestStatus", manifestAvailable ? manifest.manifestStatus() : "MANIFEST_UNAVAILABLE");
        readiness.put("traceSetCount", manifestAvailable ? manifest.traceSetCount() : 0);
        readiness.put("fixtureFileCount", manifestAvailable ? manifest.fixtureFileCount() : 0);
        readiness.put("matchedFixtureTraceSetCount", manifestAvailable ? manifest.matchedFixtureTraceSetCount() : 0);
        readiness.put("missingFixtureTraceSetCount", manifestAvailable ? manifest.missingFixtureTraceSetCount() : 0);
        readiness.put("currentTraceSetId", traceSetId);
        readiness.put("currentTraceSetFixtureStatus", coverageStatus(coverage, manifestAvailable));
        readiness.put("currentTraceSetFixturePresent", fixturePresent);
        readiness.put("catalogTraceIdsPresent", catalogTraceIdsPresent);
        readiness.put("requiredBeforeCatalogPatchMerge", manifestAvailable && patchAddsTraceIds && !fixturePresent);
        readiness.put("runtimeCatalogWrite", false);
        readiness.put("catalogMutationAllowed", false);
        readiness.put("fixtureUploadAccepted", false);
        readiness.put("callerTraceIdsAccepted", false);
        readiness.put("runtimeEvalAllowed", false);
        readiness.put("releaseBlockingAllowedNow", false);
        readiness.put("endpointMap", reviewedFixtureEndpointMap(manifest));
        return Map.copyOf(readiness);
    }

    private static Map<String, Object> currentTraceSetCoverage(
        String traceSetId,
        AgentReviewedTraceFixtureManifestResponse manifest) {
        if (manifest == null || traceSetId.isBlank()) {
            return Map.of();
        }
        return manifest.traceSetCoverage().stream()
            .filter(row -> traceSetId.equals(safeText(row.get("traceSetId"))))
            .findFirst()
            .map(Map::copyOf)
            .orElseGet(Map::of);
    }

    private static String coverageStatus(Map<String, Object> coverage, boolean manifestAvailable) {
        if (!manifestAvailable) {
            return "MANIFEST_UNAVAILABLE";
        }
        String status = safeText(coverage.get("status"));
        return status.isBlank() ? "TRACE_SET_NOT_LISTED_IN_MANIFEST" : status;
    }

    private static Map<String, Object> reviewedFixtureEndpointMap(
        AgentReviewedTraceFixtureManifestResponse manifest) {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        Map<String, Object> source = manifest != null ? manifest.endpointMap() : Map.of();
        copyEndpoint(source, endpoints, "fixtureManifest");
        copyEndpoint(source, endpoints, "fixtureTemplate");
        copyEndpoint(source, endpoints, "fixtureIntakeContract");
        copyEndpoint(source, endpoints, "reviewedEvalTraceEvidence");
        return Map.copyOf(endpoints);
    }

    private static void copyEndpoint(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source != null ? source.get(key) : null;
        if (value != null) {
            target.put(key, safeText(value));
        }
    }

    private static List<Map<String, Object>> buildPatchOperations(
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        if (proposal == null || proposal.jsonPatch().isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> operations = new ArrayList<>();
        int index = 0;
        for (Map<String, Object> operation : proposal.jsonPatch()) {
            Object value = operation.get("value");
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("index", index++);
            item.put("op", safeText(operation.get("op")));
            item.put("path", safeText(operation.get("path")));
            item.put("valueKind", value instanceof List<?> ? "trace-id-list" : "scalar");
            item.put("valueCount", value instanceof List<?> list ? list.size() : 0);
            item.put("reviewState", proposal.readyForGitReview() ? "READY_FOR_GIT_REVIEW" : "NOT_READY");
            item.put("applied", false);
            item.put("runtimeCatalogWrite", false);
            operations.add(Map.copyOf(item));
        }
        return List.copyOf(operations);
    }

    private static Map<String, Object> buildTraceDelta(AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        Map<String, Object> delta = new LinkedHashMap<>();
        int candidateCount = proposal != null ? proposal.candidateTraceCount() : 0;
        int addedCount = proposal != null ? proposal.addedTraceCount() : 0;
        delta.put("originalTraceSetTraceCount", proposal != null ? proposal.originalTraceSetTraceCount() : 0);
        delta.put("candidateTraceCount", candidateCount);
        delta.put("addedTraceCount", addedCount);
        delta.put("duplicateOrAlreadyCuratedCandidateCount", Math.max(0, candidateCount - addedCount));
        delta.put("proposedTraceSetTraceCount", proposal != null ? proposal.proposedTraceIds().size() : 0);
        delta.put("hasNewTraceIds", addedCount > 0);
        delta.put("emptyPatch", proposal == null || proposal.jsonPatch().isEmpty());
        delta.put("catalogMutated", false);
        delta.put("runtimeCatalogWrite", false);
        return Map.copyOf(delta);
    }

    private static Map<String, Object> buildCandidateGateSummary(
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        AgentEvalTraceSetCurationReviewArtifact review = proposal != null ? proposal.curationReview() : null;
        AgentEvalSuiteGateArtifact gate = review != null ? review.candidateGate() : null;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", gate != null ? gate.schemaVersion() : "");
        summary.put("reviewVerdict", review != null ? review.reviewVerdict() : "REVIEW_UNAVAILABLE");
        summary.put("readyForCatalogReview", review != null && review.readyForCatalogReview());
        summary.put("gateVerdict", gate != null ? gate.gateVerdict() : "GATE_UNAVAILABLE");
        summary.put("pass", gate != null && gate.pass());
        summary.put("requiredMinimumScore", gate != null ? gate.requiredMinimumScore() : 0);
        summary.put("observedMinimumScore", gate != null ? gate.observedMinimumScore() : 0);
        summary.put("evaluatedCases", gate != null ? gate.evaluatedCases() : 0);
        summary.put("failedReports", gate != null ? gate.failedReports() : 0);
        summary.put("warningReports", gate != null ? gate.warningReports() : 0);
        summary.put("embeddedReports", false);
        summary.put("embeddedReplay", false);
        return Map.copyOf(summary);
    }

    private static List<String> buildReviewChecklist(AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
                                                     Map<String, Object> reviewedFixtureReadiness) {
        List<String> checklist = new ArrayList<>();
        checklist.add("confirm-redacted-trace-anchors-only");
        checklist.add("confirm-candidate-suite-gate-passed");
        checklist.add("confirm-json-patch-target-resource");
        checklist.add("confirm-added-trace-ids-are-new");
        checklist.add("confirm-reviewed-fixture-manifest-read-only");
        if (fixtureMergeEvidenceMissing(reviewedFixtureReadiness)) {
            checklist.add("prepare-reviewed-redacted-fixture-file-before-catalog-merge");
        } else if (fixtureManifestAvailable(reviewedFixtureReadiness)
            && Boolean.TRUE.equals(reviewedFixtureReadiness.get("currentTraceSetFixturePresent"))) {
            checklist.add("confirm-reviewed-fixture-manifest-row");
        }
        checklist.add("confirm-no-runtime-catalog-write");
        checklist.add("submit-human-git-review");
        if (proposal != null && proposal.readyForGitReview()) {
            checklist.add("regenerate-gate-bundle-after-merge");
        }
        return List.copyOf(checklist);
    }

    private static List<String> buildNextActions(AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
                                                 Map<String, Object> reviewedFixtureReadiness) {
        List<String> actions;
        if (proposal == null) {
            actions = new ArrayList<>(List.of("rerun-promotion-workflow"));
        } else if (proposal.readyForGitReview()) {
            actions = new ArrayList<>(List.of(
                "copy-json-patch-into-git-review",
                "merge-reviewed-catalog-change",
                "regenerate-trace-set-gate-bundle"
            ));
        } else if (proposal.addedTraceCount() == 0) {
            actions = new ArrayList<>(List.of(
                "inspect-candidate-discovery",
                "collect-more-redacted-traces",
                "rerun-catalog-patch-review"
            ));
        } else {
            actions = new ArrayList<>(List.of("inspect-candidate-gate", "open-replay-drill-down", "open-trace-eval-report"));
        }
        if (fixtureMergeEvidenceMissing(reviewedFixtureReadiness)) {
            actions.add(0, "prepare-reviewed-redacted-fixture-file-before-catalog-merge");
        }
        return List.copyOf(actions);
    }

    private static boolean fixtureMergeEvidenceMissing(Map<String, Object> reviewedFixtureReadiness) {
        return fixtureManifestAvailable(reviewedFixtureReadiness)
            && Boolean.TRUE.equals(reviewedFixtureReadiness.get("requiredBeforeCatalogPatchMerge"));
    }

    private static boolean fixtureManifestAvailable(Map<String, Object> reviewedFixtureReadiness) {
        return reviewedFixtureReadiness != null
            && !"MANIFEST_UNAVAILABLE".equals(reviewedFixtureReadiness.get("manifestStatus"));
    }

    private static Map<String, Object> buildEndpointTemplates(String traceSetId) {
        String id = safeText(traceSetId);
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("capabilities", "/api/agent/observability/eval/workbench/capabilities");
        endpoints.put("overview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("detail", "/api/agent/observability/eval/workbench/trace-sets/" + id);
        endpoints.put("workbenchPromotionWorkflow",
            "/api/agent/observability/eval/workbench/trace-sets/" + id + "/promotion-workflow");
        endpoints.put("workbenchCatalogPatchReview",
            "/api/agent/observability/eval/workbench/trace-sets/" + id + "/catalog-patch-review");
        endpoints.put("rawCatalogPatchProposal",
            "/api/agent/observability/eval/trace-sets/" + id + "/catalog-patch-proposal");
        endpoints.put("workbenchGateBundleSummary",
            "/api/agent/observability/eval/workbench/gate-bundle-summary");
        endpoints.put("gateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        endpoints.put("replayTimeline", "/api/agent/observability/replay/trace/{traceId}?limit={limit}");
        endpoints.put("evalReport", "/api/agent/observability/eval/trace/{traceId}?limit={limit}");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildWorkbenchPolicy(
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
        AgentEvalWorkbenchTraceSetView view) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager eval workbench");
        policy.put("catalogPatchReviewOnly", true);
        policy.put("catalogPatchProposalEmbedded", proposal != null);
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("patchApplied", false);
        policy.put("catalogPromotionAuthority", "human Git review only");
        policy.put("requiresHumanReview", true);
        policy.put("requiresGitReview", true);
        policy.put("requiresCiGateBundleRegeneration", proposal != null && proposal.readyForGitReview());
        policy.put("releaseBlockingAfterMergeOnly", true);
        policy.put("ciBlockingEnabled", false);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("rawAuditQuery", false);
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        policy.put("traceSetStatusBeforeReview", view != null ? view.status() : "");
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
                                                    AgentEvalWorkbenchTraceSetView view) {
        Map<String, Object> proposalPrivacy = proposal != null ? proposal.privacy() : Map.of();
        Map<String, Object> viewPrivacy = view != null ? view.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(proposalPrivacy, "containsRawPrincipal")
            || truthy(viewPrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(proposalPrivacy, "containsRawOrganization")
            || truthy(viewPrivacy, "containsRawOrganization");
        boolean containsRawConversation = truthy(proposalPrivacy, "containsRawConversation")
            || truthy(viewPrivacy, "containsRawConversation");
        boolean containsRawEndpoints = truthy(proposalPrivacy, "containsRawEndpoints")
            || truthy(viewPrivacy, "containsRawEndpoints");
        boolean containsRawKubeManagerEndpoints = truthy(viewPrivacy, "containsRawKubeManagerEndpoints");
        boolean containsRawReason = truthy(proposalPrivacy, "containsRawReason")
            || truthy(viewPrivacy, "containsRawReason");
        boolean containsRawParameterValues = truthy(proposalPrivacy, "containsRawParameterValues")
            || truthy(viewPrivacy, "containsRawParameterValues");
        boolean upstreamRedactedOnly = (proposal == null || Boolean.TRUE.equals(proposalPrivacy.get("redactedOnly")))
            && (view == null || Boolean.TRUE.equals(viewPrivacy.get("redactedOnly")));
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", upstreamRedactedOnly && !(containsRawPrincipal
            || containsRawOrganization
            || containsRawConversation
            || containsRawEndpoints
            || containsRawKubeManagerEndpoints
            || containsRawReason
            || containsRawParameterValues));
        proof.put("containsRawPrincipal", containsRawPrincipal);
        proof.put("containsRawOrganization", containsRawOrganization);
        proof.put("containsRawConversation", containsRawConversation);
        proof.put("containsRawEndpoints", containsRawEndpoints);
        proof.put("containsRawKubeManagerEndpoints", containsRawKubeManagerEndpoints);
        proof.put("containsRawReason", containsRawReason);
        proof.put("containsRawParameterValues", containsRawParameterValues);
        proof.put("deterministic", true);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        proof.put("toolExecution", false);
        proof.put("kubeManagerCalls", false);
        return Map.copyOf(proof);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static String safeText(Object value) {
        return value != null ? value.toString() : "";
    }
}
