package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frontend-ready wrapper around the trace-set promotion workflow.
 *
 * <p>The raw workflow artifact remains the source of truth. This read model
 * adds UI steps, patch summary, and next-action guidance for the future
 * vue-kube-manager eval workbench without granting catalog write authority.</p>
 */
public record AgentEvalWorkbenchPromotionWorkflowResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String traceSetId,
    String traceSetTitle,
    String suiteId,
    String workflowVerdict,
    boolean readyForGitReview,
    int selectedCandidateTraceCount,
    List<String> selectedCandidateTraceIds,
    AgentEvalWorkbenchTraceSetView traceSetView,
    AgentEvalTraceSetPromotionWorkflowArtifact workflow,
    List<Map<String, Object>> uiSteps,
    Map<String, Object> patchSummary,
    Map<String, Object> candidateGateSummary,
    List<String> nextActions,
    Map<String, Object> endpointTemplates,
    Map<String, Object> workbenchPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-workbench-promotion-workflow.v1";

    public static AgentEvalWorkbenchPromotionWorkflowResponse from(
        AgentEvalTraceSetDefinition definition,
        AgentEvalTraceSetGateArtifact traceSetGate,
        AgentEvalTraceSetPromotionWorkflowArtifact workflow) {
        AgentEvalWorkbenchTraceSetView view = AgentEvalWorkbenchTraceSetView.from(definition, traceSetGate);
        AgentEvalTraceSetCatalogPatchProposalArtifact proposal = workflow != null
            ? workflow.catalogPatchProposal()
            : null;
        return new AgentEvalWorkbenchPromotionWorkflowResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            workflow != null ? workflow.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION,
            workflow != null ? workflow.traceSetId() : safeText(definition != null ? definition.id() : ""),
            workflow != null ? workflow.traceSetTitle() : safeText(definition != null ? definition.title() : ""),
            workflow != null ? workflow.suiteId() : safeText(definition != null ? definition.suiteId() : ""),
            workflow != null ? workflow.workflowVerdict() : "REJECT_WORKFLOW_UNAVAILABLE",
            workflow != null && workflow.readyForGitReview(),
            workflow != null ? workflow.selectedCandidateTraceCount() : 0,
            workflow != null ? List.copyOf(workflow.selectedCandidateTraceIds()) : List.of(),
            view,
            workflow,
            buildUiSteps(workflow, proposal),
            buildPatchSummary(proposal),
            buildCandidateGateSummary(proposal),
            buildNextActions(workflow, proposal),
            buildEndpointTemplates(view.id()),
            buildWorkbenchPolicy(workflow, proposal, view),
            privacyProof(workflow, view)
        );
    }

    private static List<Map<String, Object>> buildUiSteps(AgentEvalTraceSetPromotionWorkflowArtifact workflow,
                                                          AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        AgentEvalTraceSetCandidateDiscoveryResponse discovery = workflow != null ? workflow.candidateDiscovery() : null;
        AgentEvalTraceSetCurationReviewArtifact review = proposal != null ? proposal.curationReview() : null;
        return List.of(
            step(
                "candidate-discovery",
                discovery != null && discovery.candidateTraceCount() > 0 ? "CANDIDATES_FOUND" : "NO_RECOMMENDED_CANDIDATES",
                "review-redacted-candidate-traces",
                Map.of(
                    "candidateTraceCount", discovery != null ? discovery.candidateTraceCount() : 0,
                    "selectedCandidateTraceCount", workflow != null ? workflow.selectedCandidateTraceCount() : 0,
                    "auditQueryTruncated", discovery != null && discovery.auditQueryTruncated()
                )
            ),
            step(
                "curation-review",
                review != null && review.readyForCatalogReview() ? "READY_FOR_CATALOG_REVIEW" : "REVIEW_NOT_READY",
                "inspect-deterministic-candidate-gate",
                Map.of(
                    "candidateTraceCount", review != null ? review.candidateTraceCount() : 0,
                    "reviewVerdict", review != null ? review.reviewVerdict() : "REVIEW_UNAVAILABLE",
                    "readyForCatalogReview", review != null && review.readyForCatalogReview()
                )
            ),
            step(
                "catalog-patch-proposal",
                proposal != null && proposal.readyForGitReview() ? "READY_FOR_GIT_REVIEW" : "PATCH_NOT_READY",
                "open-git-reviewed-catalog-patch",
                Map.of(
                    "proposalVerdict", proposal != null ? proposal.proposalVerdict() : "PROPOSAL_UNAVAILABLE",
                    "addedTraceCount", proposal != null ? proposal.addedTraceCount() : 0,
                    "jsonPatchOperationCount", proposal != null ? proposal.jsonPatch().size() : 0
                )
            ),
            step(
                "gate-bundle-regeneration",
                proposal != null && proposal.readyForGitReview() ? "WAITING_FOR_GIT_MERGE" : "BLOCKED_UNTIL_PATCH_READY",
                "regenerate-gate-bundle-after-reviewed-merge",
                Map.of(
                    "runtimeCatalogWrite", false,
                    "requiresGitReview", true,
                    "ciBlockingEnabled", false
                )
            )
        );
    }

    private static Map<String, Object> step(String id,
                                            String status,
                                            String recommendedAction,
                                            Map<String, Object> evidence) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("status", status);
        step.put("recommendedAction", recommendedAction);
        step.put("evidence", evidence != null ? Map.copyOf(evidence) : Map.of());
        return Map.copyOf(step);
    }

    private static Map<String, Object> buildPatchSummary(AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", proposal != null ? proposal.schemaVersion() : "");
        summary.put("proposalVerdict", proposal != null ? proposal.proposalVerdict() : "PROPOSAL_UNAVAILABLE");
        summary.put("readyForGitReview", proposal != null && proposal.readyForGitReview());
        summary.put("source", proposal != null ? proposal.source() : "");
        summary.put("targetResource", proposal != null ? proposal.targetResource() : "");
        summary.put("traceSetIndex", proposal != null ? proposal.traceSetIndex() : -1);
        summary.put("originalTraceSetTraceCount", proposal != null ? proposal.originalTraceSetTraceCount() : 0);
        summary.put("candidateTraceCount", proposal != null ? proposal.candidateTraceCount() : 0);
        summary.put("addedTraceCount", proposal != null ? proposal.addedTraceCount() : 0);
        summary.put("jsonPatchOperationCount", proposal != null ? proposal.jsonPatch().size() : 0);
        summary.put("catalogMutated", false);
        summary.put("runtimeCatalogWrite", false);
        summary.put("requiresGitReview", true);
        summary.put("releaseBlockingAfterMergeOnly", true);
        return Map.copyOf(summary);
    }

    private static Map<String, Object> buildCandidateGateSummary(AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        AgentEvalTraceSetCurationReviewArtifact review = proposal != null ? proposal.curationReview() : null;
        AgentEvalSuiteGateArtifact suiteGate = review != null ? review.candidateGate() : null;
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", suiteGate != null ? suiteGate.schemaVersion() : "");
        summary.put("reviewVerdict", review != null ? review.reviewVerdict() : "REVIEW_UNAVAILABLE");
        summary.put("readyForCatalogReview", review != null && review.readyForCatalogReview());
        summary.put("gateVerdict", suiteGate != null ? suiteGate.gateVerdict() : "GATE_UNAVAILABLE");
        summary.put("pass", suiteGate != null && suiteGate.pass());
        summary.put("observedMinimumScore", suiteGate != null ? suiteGate.observedMinimumScore() : 0);
        summary.put("evaluatedCases", suiteGate != null ? suiteGate.evaluatedCases() : 0);
        summary.put("failedReports", suiteGate != null ? suiteGate.failedReports() : 0);
        summary.put("warningReports", suiteGate != null ? suiteGate.warningReports() : 0);
        summary.put("embeddedReports", false);
        summary.put("embeddedReplay", false);
        return Map.copyOf(summary);
    }

    private static List<String> buildNextActions(AgentEvalTraceSetPromotionWorkflowArtifact workflow,
                                                 AgentEvalTraceSetCatalogPatchProposalArtifact proposal) {
        if (workflow == null) {
            return List.of("refresh-trace-set-detail");
        }
        if (proposal != null && proposal.readyForGitReview()) {
            return List.of(
                "open-catalog-patch-proposal",
                "create-human-git-review",
                "regenerate-gate-bundle-after-merge"
            );
        }
        if (workflow.selectedCandidateTraceCount() == 0) {
            return List.of(
                "inspect-candidate-discovery",
                "generate-more-redacted-agent-traces",
                "retry-promotion-workflow"
            );
        }
        return List.of(
            "inspect-candidate-gate",
            "open-replay-drill-down",
            "open-trace-eval-report"
        );
    }

    private static Map<String, Object> buildEndpointTemplates(String traceSetId) {
        String id = safeText(traceSetId);
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("capabilities", "/api/agent/observability/eval/workbench/capabilities");
        endpoints.put("overview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("detail", "/api/agent/observability/eval/workbench/trace-sets/" + id);
        endpoints.put("workbenchPromotionWorkflow",
            "/api/agent/observability/eval/workbench/trace-sets/" + id + "/promotion-workflow");
        endpoints.put("rawPromotionWorkflow",
            "/api/agent/observability/eval/trace-sets/" + id + "/promotion-workflow");
        endpoints.put("catalogPatchProposal",
            "/api/agent/observability/eval/trace-sets/" + id + "/catalog-patch-proposal");
        endpoints.put("workbenchGateBundleSummary",
            "/api/agent/observability/eval/workbench/gate-bundle-summary");
        endpoints.put("gateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        endpoints.put("replayTimeline", "/api/agent/observability/replay/trace/{traceId}?limit={limit}");
        endpoints.put("evalReport", "/api/agent/observability/eval/trace/{traceId}?limit={limit}");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildWorkbenchPolicy(AgentEvalTraceSetPromotionWorkflowArtifact workflow,
                                                            AgentEvalTraceSetCatalogPatchProposalArtifact proposal,
                                                            AgentEvalWorkbenchTraceSetView view) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager eval workbench");
        policy.put("workbenchWrapperOnly", true);
        policy.put("workflowExecuted", workflow != null);
        policy.put("candidateDiscoveryExecuted", workflow != null && workflow.candidateDiscovery() != null);
        policy.put("catalogPatchProposalEmbedded", proposal != null);
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("catalogPromotionAuthority", "human Git review only");
        policy.put("requiresGitReview", true);
        policy.put("requiresCiGateBundleRegeneration", proposal != null && proposal.readyForGitReview());
        policy.put("releaseBlockingAfterMergeOnly", true);
        policy.put("ciBlockingEnabled", false);
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        policy.put("traceSetStatusBeforePromotion", view != null ? view.status() : "");
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetPromotionWorkflowArtifact workflow,
                                                    AgentEvalWorkbenchTraceSetView view) {
        Map<String, Object> workflowPrivacy = workflow != null ? workflow.privacy() : Map.of();
        Map<String, Object> viewPrivacy = view != null ? view.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(workflowPrivacy, "containsRawPrincipal")
            || truthy(viewPrivacy, "containsRawPrincipal");
        boolean containsRawOrganization = truthy(workflowPrivacy, "containsRawOrganization")
            || truthy(viewPrivacy, "containsRawOrganization");
        boolean containsRawConversation = truthy(workflowPrivacy, "containsRawConversation")
            || truthy(viewPrivacy, "containsRawConversation");
        boolean containsRawEndpoints = truthy(workflowPrivacy, "containsRawEndpoints")
            || truthy(viewPrivacy, "containsRawEndpoints");
        boolean containsRawKubeManagerEndpoints = truthy(viewPrivacy, "containsRawKubeManagerEndpoints");
        boolean containsRawReason = truthy(workflowPrivacy, "containsRawReason")
            || truthy(viewPrivacy, "containsRawReason");
        boolean containsRawParameterValues = truthy(workflowPrivacy, "containsRawParameterValues")
            || truthy(viewPrivacy, "containsRawParameterValues");
        boolean upstreamRedactedOnly = (workflow == null || Boolean.TRUE.equals(workflowPrivacy.get("redactedOnly")))
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

    private static String safeText(String value) {
        return value != null ? value : "";
    }
}
