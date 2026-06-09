package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Frontend-ready release gate summary for the eval workbench.
 *
 * <p>This read model wraps the compact trace-set gate bundle for a Vue page.
 * It never accepts caller trace IDs, runs Tools, calls kube-manager, writes the
 * catalog, or enables CI blocking at runtime.</p>
 */
public record AgentEvalWorkbenchGateBundleSummaryResponse(
    String schemaVersion,
    Instant generatedAt,
    String evaluationVersion,
    String gateVerdict,
    boolean pass,
    boolean releaseEligible,
    int traceSetCount,
    int passedTraceSets,
    int failedTraceSets,
    int emptyTraceSets,
    int readyForCiBlockingTraceSets,
    List<String> traceSetIds,
    List<String> failedTraceSetIds,
    List<String> emptyTraceSetIds,
    List<AgentEvalWorkbenchTraceSetView> traceSets,
    AgentEvalTraceSetGateBundleArtifact gateBundle,
    Map<String, Object> bundleSummary,
    List<Map<String, Object>> traceSetGateRows,
    Map<String, Object> ciArtifact,
    Map<String, Object> blockerSummary,
    List<String> nextActions,
    Map<String, Object> endpointTemplates,
    Map<String, Object> workbenchPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-workbench-gate-bundle-summary.v1";

    public static AgentEvalWorkbenchGateBundleSummaryResponse from(AgentEvalTraceSetCatalogResponse catalog,
                                                                   AgentEvalTraceSetGateBundleArtifact bundle) {
        List<AgentEvalTraceSetDefinition> definitions = catalog != null
            ? List.copyOf(catalog.traceSets())
            : List.of();
        Map<String, AgentEvalTraceSetGateArtifact> gatesByTraceSetId = gatesByTraceSetId(bundle);
        List<AgentEvalWorkbenchTraceSetView> traceSets = definitions.stream()
            .map(definition -> AgentEvalWorkbenchTraceSetView.from(
                definition,
                gatesByTraceSetId.get(definition.id())
            ))
            .toList();
        int readyCount = (int) traceSets.stream()
            .filter(AgentEvalWorkbenchTraceSetView::readyForCiBlocking)
            .count();
        return new AgentEvalWorkbenchGateBundleSummaryResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            bundle != null ? bundle.evaluationVersion() : AgentEvalReportResponse.EVALUATION_VERSION,
            bundle != null ? bundle.gateVerdict() : "UNKNOWN",
            bundle != null && bundle.pass(),
            bundle != null && bundle.releaseEligible(),
            bundle != null ? bundle.traceSetCount() : traceSets.size(),
            bundle != null ? bundle.passedTraceSets() : 0,
            bundle != null ? bundle.failedTraceSets() : 0,
            bundle != null ? bundle.emptyTraceSets() : 0,
            readyCount,
            bundle != null ? List.copyOf(bundle.traceSetIds()) : traceSets.stream().map(AgentEvalWorkbenchTraceSetView::id).toList(),
            bundle != null ? List.copyOf(bundle.failedTraceSetIds()) : List.of(),
            bundle != null ? List.copyOf(bundle.emptyTraceSetIds()) : List.of(),
            traceSets,
            bundle,
            bundleSummary(bundle, readyCount),
            traceSetGateRows(traceSets, gatesByTraceSetId),
            ciArtifact(bundle),
            blockerSummary(bundle, traceSets),
            nextActions(bundle, traceSets),
            buildEndpointTemplates(),
            workbenchPolicy(bundle, readyCount),
            privacyProof(catalog, bundle, traceSets)
        );
    }

    private static Map<String, AgentEvalTraceSetGateArtifact> gatesByTraceSetId(
        AgentEvalTraceSetGateBundleArtifact bundle) {
        Map<String, AgentEvalTraceSetGateArtifact> gates = new LinkedHashMap<>();
        if (bundle == null) {
            return Map.of();
        }
        for (AgentEvalTraceSetGateArtifact gate : bundle.traceSetGates()) {
            gates.put(gate.traceSetId(), gate);
        }
        return Map.copyOf(gates);
    }

    private static Map<String, Object> bundleSummary(AgentEvalTraceSetGateBundleArtifact bundle, int readyCount) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("schemaVersion", bundle != null ? bundle.schemaVersion() : "");
        summary.put("gateVerdict", bundle != null ? bundle.gateVerdict() : "UNKNOWN");
        summary.put("pass", bundle != null && bundle.pass());
        summary.put("releaseEligible", bundle != null && bundle.releaseEligible());
        summary.put("traceSetCount", bundle != null ? bundle.traceSetCount() : 0);
        summary.put("passedTraceSets", bundle != null ? bundle.passedTraceSets() : 0);
        summary.put("failedTraceSets", bundle != null ? bundle.failedTraceSets() : 0);
        summary.put("emptyTraceSets", bundle != null ? bundle.emptyTraceSets() : 0);
        summary.put("readyForCiBlockingTraceSets", readyCount);
        summary.put("ciBlockingEnabled", false);
        summary.put("requestTraceIdOverrideAllowed", false);
        summary.put("embeddedReports", false);
        summary.put("embeddedReplay", false);
        return Map.copyOf(summary);
    }

    private static List<Map<String, Object>> traceSetGateRows(
        List<AgentEvalWorkbenchTraceSetView> traceSets,
        Map<String, AgentEvalTraceSetGateArtifact> gatesByTraceSetId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AgentEvalWorkbenchTraceSetView traceSet : traceSets) {
            AgentEvalTraceSetGateArtifact gate = gatesByTraceSetId.get(traceSet.id());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("traceSetId", traceSet.id());
            row.put("title", traceSet.title());
            row.put("suiteId", traceSet.suiteId());
            row.put("status", traceSet.status());
            row.put("gateVerdict", traceSet.gateVerdict());
            row.put("pass", traceSet.pass());
            row.put("emptyInput", traceSet.emptyInput());
            row.put("curatedTraceCount", traceSet.curatedTraceCount());
            row.put("evaluatedCases", gate != null && gate.suiteGate() != null ? gate.suiteGate().evaluatedCases() : 0);
            row.put("failedReports", gate != null && gate.suiteGate() != null ? gate.suiteGate().failedReports() : 0);
            row.put("warningReports", gate != null && gate.suiteGate() != null ? gate.suiteGate().warningReports() : 0);
            row.put("nextAction", traceSet.nextAction());
            row.put("detailPath", "/api/agent/observability/eval/workbench/trace-sets/" + traceSet.id());
            row.put("promotionWorkflowPath",
                "/api/agent/observability/eval/workbench/trace-sets/" + traceSet.id() + "/promotion-workflow");
            row.put("catalogPatchReviewPath",
                "/api/agent/observability/eval/workbench/trace-sets/" + traceSet.id() + "/catalog-patch-review");
            row.put("embeddedReports", false);
            row.put("embeddedReplay", false);
            rows.add(Map.copyOf(row));
        }
        return List.copyOf(rows);
    }

    private static Map<String, Object> ciArtifact(AgentEvalTraceSetGateBundleArtifact bundle) {
        Map<String, Object> artifact = new LinkedHashMap<>();
        Map<String, Object> policy = bundle != null ? bundle.bundlePolicy() : Map.of();
        artifact.put("path", safeText(policy.get("ciArtifactPath")));
        artifact.put("source", bundle != null ? bundle.source() : "");
        artifact.put("schemaVersion", bundle != null ? bundle.schemaVersion() : "");
        artifact.put("ciBlockingEnabled", false);
        artifact.put("releaseEligible", bundle != null && bundle.releaseEligible());
        artifact.put("enablementCondition", safeText(policy.get("ciBlockingEnablementCondition")));
        artifact.put("requestTraceIdOverrideAllowed", false);
        artifact.put("regenerateEndpoint", "/api/agent/observability/eval/trace-sets/gate-bundle");
        artifact.put("runtimeCatalogWrite", false);
        return Map.copyOf(artifact);
    }

    private static Map<String, Object> blockerSummary(AgentEvalTraceSetGateBundleArtifact bundle,
                                                      List<AgentEvalWorkbenchTraceSetView> traceSets) {
        Map<String, Object> blockers = new LinkedHashMap<>();
        blockers.put("hasBlockingIssues", bundle == null || !bundle.releaseEligible());
        blockers.put("emptyTraceSetIds", bundle != null ? List.copyOf(bundle.emptyTraceSetIds()) : List.of());
        blockers.put("failedTraceSetIds", bundle != null ? List.copyOf(bundle.failedTraceSetIds()) : List.of());
        blockers.put("needsEvidenceTraceSetIds", traceSets.stream()
            .filter(traceSet -> "NEEDS_REDACTED_EVIDENCE".equals(traceSet.status()))
            .map(AgentEvalWorkbenchTraceSetView::id)
            .toList());
        blockers.put("ciBlockingDisabled", true);
        blockers.put("catalogMutationAllowed", false);
        blockers.put("releaseBlockingAfterReviewedEvidenceOnly", true);
        return Map.copyOf(blockers);
    }

    private static List<String> nextActions(AgentEvalTraceSetGateBundleArtifact bundle,
                                            List<AgentEvalWorkbenchTraceSetView> traceSets) {
        List<String> actions = new ArrayList<>();
        boolean hasNeedsEvidence = traceSets.stream()
            .anyMatch(traceSet -> "NEEDS_REDACTED_EVIDENCE".equals(traceSet.status()));
        if (hasNeedsEvidence) {
            actions.add("discover-redacted-candidates");
            actions.add("open-catalog-patch-review");
        }
        if (bundle == null || !bundle.releaseEligible()) {
            actions.add("regenerate-gate-bundle-after-reviewed-merge");
        }
        actions.add("keep-ci-blocking-disabled-until-reviewed-real-evidence");
        actions.add("use-replay-and-eval-drill-down-for-failed-traces");
        return List.copyOf(actions);
    }

    private static Map<String, Object> buildEndpointTemplates() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("capabilities", "/api/agent/observability/eval/workbench/capabilities");
        endpoints.put("overview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("gateBundleSummary", "/api/agent/observability/eval/workbench/gate-bundle-summary");
        endpoints.put("rawGateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        endpoints.put("detail", "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}");
        endpoints.put("workbenchPromotionWorkflow",
            "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/promotion-workflow");
        endpoints.put("workbenchCatalogPatchReview",
            "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review");
        endpoints.put("replayTimeline", "/api/agent/observability/replay/trace/{traceId}?limit={limit}");
        endpoints.put("evalReport", "/api/agent/observability/eval/trace/{traceId}?limit={limit}");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> workbenchPolicy(AgentEvalTraceSetGateBundleArtifact bundle, int readyCount) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager eval workbench");
        policy.put("summaryOnly", true);
        policy.put("readOnly", true);
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("requestTraceIdOverrideAllowed", false);
        policy.put("catalogPromotionAuthority", "human Git review only");
        policy.put("releaseBlockingAfterReviewedEvidenceOnly", true);
        policy.put("ciBlockingEnabled", false);
        policy.put("ciBlockingCanBeEnabledNow", false);
        policy.put("readyForCiBlockingTraceSets", readyCount);
        policy.put("releaseEligibleAccordingToBundle", bundle != null && bundle.releaseEligible());
        policy.put("embeddedReports", false);
        policy.put("embeddedReplay", false);
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentEvalTraceSetCatalogResponse catalog,
                                                    AgentEvalTraceSetGateBundleArtifact bundle,
                                                    List<AgentEvalWorkbenchTraceSetView> traceSets) {
        Map<String, Object> catalogPrivacy = catalog != null ? catalog.privacy() : Map.of();
        Map<String, Object> bundlePrivacy = bundle != null ? bundle.privacy() : Map.of();
        boolean containsRawPrincipal = truthy(catalogPrivacy, "containsRawPrincipal")
            || truthy(bundlePrivacy, "containsRawPrincipal")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawPrincipal"));
        boolean containsRawOrganization = truthy(catalogPrivacy, "containsRawOrganization")
            || truthy(bundlePrivacy, "containsRawOrganization")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawOrganization"));
        boolean containsRawConversation = truthy(catalogPrivacy, "containsRawConversation")
            || truthy(bundlePrivacy, "containsRawConversation")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawConversation"));
        boolean containsRawEndpoints = truthy(catalogPrivacy, "containsRawEndpoints")
            || truthy(bundlePrivacy, "containsRawEndpoints")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawEndpoints"));
        boolean containsRawKubeManagerEndpoints = truthy(catalogPrivacy, "containsRawKubeManagerEndpoints")
            || truthy(bundlePrivacy, "containsRawKubeManagerEndpoints")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawKubeManagerEndpoints"));
        boolean containsRawReason = truthy(catalogPrivacy, "containsRawReason")
            || truthy(bundlePrivacy, "containsRawReason")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawReason"));
        boolean containsRawParameterValues = truthy(catalogPrivacy, "containsRawParameterValues")
            || truthy(bundlePrivacy, "containsRawParameterValues")
            || traceSets.stream().anyMatch(traceSet -> truthy(traceSet.privacy(), "containsRawParameterValues"));
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", !(containsRawPrincipal
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
