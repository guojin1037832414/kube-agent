package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only read model for reviewed eval trace evidence readiness.
 */
public record AgentReviewedEvalTraceEvidenceResponse(
    String schemaVersion,
    Instant generatedAt,
    String evidenceStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean reviewedEvidenceReady,
    boolean releaseBlockingAllowedNow,
    boolean ciBlockingEnabled,
    boolean runtimeMutationAllowed,
    int traceSetCount,
    int reviewedTraceSetCount,
    int reviewedTraceAnchorCount,
    List<Map<String, Object>> traceSetEvidence,
    List<Map<String, Object>> reviewPipeline,
    List<Map<String, Object>> qualityGates,
    List<Map<String, Object>> standardsAlignment,
    List<String> nextActions,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-reviewed-eval-trace-evidence.v1";

    public static AgentReviewedEvalTraceEvidenceResponse of(Instant generatedAt,
                                                            AgentEvalTraceSetCatalogResponse catalog) {
        List<AgentEvalTraceSetDefinition> traceSets = catalog != null ? catalog.traceSets() : List.of();
        List<Map<String, Object>> evidence = traceSets.stream()
            .map(AgentReviewedEvalTraceEvidenceResponse::traceSetEvidence)
            .toList();
        int reviewedTraceSets = (int) evidence.stream()
            .filter(row -> Boolean.TRUE.equals(row.get("reviewedEvidencePresent")))
            .count();
        int anchorCount = traceSets.stream()
            .mapToInt(traceSet -> traceSet.traceIds().size())
            .sum();
        boolean ready = !traceSets.isEmpty() && reviewedTraceSets == traceSets.size() && anchorCount > 0;
        return new AgentReviewedEvalTraceEvidenceResponse(
            SCHEMA_VERSION,
            generatedAt,
            ready ? "REVIEWED_TRACE_EVIDENCE_READY_FOR_RELEASE_GATE_PROMOTION" : "NEEDS_REVIEWED_REDACTED_TRACE_EVIDENCE",
            "Phase 1 reviewed eval trace evidence control plane",
            true,
            ready,
            false,
            false,
            false,
            traceSets.size(),
            reviewedTraceSets,
            anchorCount,
            evidence,
            buildReviewPipeline(),
            buildQualityGates(),
            buildStandardsAlignment(),
            nextActions(ready),
            buildEndpointMap(),
            buildSafety(),
            privacy(catalog)
        );
    }

    private static Map<String, Object> traceSetEvidence(AgentEvalTraceSetDefinition traceSet) {
        int traceCount = traceSet.traceIds().size();
        boolean present = traceCount > 0;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("traceSetId", traceSet.id());
        evidence.put("suiteId", traceSet.suiteId());
        evidence.put("status", present ? "HAS_REVIEWED_TRACE_ANCHORS" : "NEEDS_REDACTED_REVIEWED_TRACE_EVIDENCE");
        evidence.put("reviewedEvidencePresent", present);
        evidence.put("reviewedTraceAnchorCount", traceCount);
        evidence.put("requiresPersistedRedactedReplayEvidence", true);
        evidence.put("requiresHumanGitReview", true);
        evidence.put("requestTraceIdOverrideAllowed", false);
        evidence.put("placeholderTraceIdsAllowed", false);
        evidence.put("releaseBlockingAllowedNow", false);
        evidence.put("phase2Scope", false);
        evidence.put("tags", List.copyOf(traceSet.tags()));
        evidence.put("evidenceRequirements", List.copyOf(traceSet.evidenceRequirements()));
        return Map.copyOf(evidence);
    }

    private static List<Map<String, Object>> buildReviewPipeline() {
        return List.of(
            stage(1, "redacted-candidate-discovery", "/api/agent/observability/eval/trace-sets/{traceSetId}/candidates", "Find redacted trace anchors without raw audit exposure.", "ADVISORY_ONLY"),
            stage(2, "curation-review", "/api/agent/observability/eval/trace-sets/{traceSetId}/curation-review", "Review candidate anchors through deterministic eval gates.", "REVIEW_ONLY"),
            stage(3, "workbench-catalog-patch-review", "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review", "Render sanitized patch evidence for Vue and Git review.", "REVIEW_ONLY"),
            stage(4, "human-git-review", "observability/eval-trace-sets.json", "Only reviewed source control changes may promote trace anchors.", "HUMAN_REQUIRED"),
            stage(5, "gate-bundle-regeneration", "/api/agent/observability/eval/workbench/gate-bundle-summary", "Regenerate compact gate evidence after catalog promotion.", "ADVISORY_NOW"),
            stage(6, "release-blocking-promotion", "/api/agent/observability/eval/reviewed-trace-evidence", "A later release slice may promote gates after reviewed evidence exists.", "BLOCKED_NOW")
        );
    }

    private static List<Map<String, Object>> buildQualityGates() {
        return List.of(
            gate("w3c-trace-anchor", "Trace anchors must stay compatible with portable trace context correlation."),
            gate("redacted-replay-only", "Reviewed evidence must come from replay outputs that hide raw principals, organizations, conversations, endpoints, reasons, and parameter values."),
            gate("deterministic-eval", "Release evidence must be reproducible without model calls or external calls."),
            gate("human-git-review", "Trace-set promotion authority stays with source control review, not runtime requests."),
            gate("no-runtime-authority", "This endpoint cannot execute tools, mutate catalogs, enable CI blocking, or call kube-manager."),
            gate("phase1-core-only", "NIM, HPC, Slurm, and BCM remain Phase 2 and are not required for Phase 1 reviewed evidence.")
        );
    }

    private static List<Map<String, Object>> buildStandardsAlignment() {
        return List.of(
            standard("openai-agents-tracing", "Trace/span evidence for agent runs, tools, guardrails, and handoffs.", "https://openai.github.io/openai-agents-python/tracing/"),
            standard("mcp-tools-governance", "External tool-call protocols need discovery and authorization separated from local authority.", "https://modelcontextprotocol.io/specification/2025-06-18/server/tools"),
            standard("otel-genai-semconv", "GenAI telemetry should remain portable through stable semantic conventions.", "https://opentelemetry.io/docs/specs/semconv/gen-ai/"),
            standard("owasp-llm-top-10", "Prompt injection, sensitive disclosure, and excessive agency risks need eval and governance gates.", "https://owasp.org/www-project-top-10-for-large-language-model-applications/"),
            standard("w3c-trace-context", "Trace identifiers must support interoperable distributed tracing.", "https://www.w3.org/TR/trace-context/")
        );
    }

    private static List<String> nextActions(boolean ready) {
        if (ready) {
            return List.of(
                "inspect-reviewed-trace-evidence-before-release-gate-promotion",
                "regenerate-gate-bundle-after-human-git-review",
                "prepare-release-blocking-eval-gate-contract"
            );
        }
        return List.of(
            "discover-redacted-trace-candidates",
            "review-candidates-through-curation-review",
            "prepare-catalog-patch-review-for-human-git-review",
            "merge-reviewed-trace-anchors-through-source-control",
            "regenerate-gate-bundle-after-catalog-update",
            "keep-ci-blocking-disabled-until-reviewed-evidence-is-present"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("evalWorkbenchOverview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("evalWorkbenchGateBundleSummary", "/api/agent/observability/eval/workbench/gate-bundle-summary");
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        endpoints.put("traceSetCandidates", "/api/agent/observability/eval/trace-sets/{traceSetId}/candidates");
        endpoints.put("traceSetCurationReview", "/api/agent/observability/eval/trace-sets/{traceSetId}/curation-review");
        endpoints.put("catalogPatchReview", "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review");
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
        safety.put("runtimeMutationAllowed", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("releaseBlockingAllowedNow", false);
        safety.put("ciBlockingEnabled", false);
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

    private static Map<String, Object> privacy(AgentEvalTraceSetCatalogResponse catalog) {
        Map<String, Object> catalogPrivacy = catalog != null ? catalog.privacy() : Map.of();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !truthy(catalogPrivacy, "containsRawPrincipal")
            && !truthy(catalogPrivacy, "containsRawOrganization")
            && !truthy(catalogPrivacy, "containsRawConversation")
            && !truthy(catalogPrivacy, "containsRawEndpoints")
            && !truthy(catalogPrivacy, "containsRawReason")
            && !truthy(catalogPrivacy, "containsRawParameterValues"));
        privacy.put("containsRawPrincipal", truthy(catalogPrivacy, "containsRawPrincipal"));
        privacy.put("containsRawOrganization", truthy(catalogPrivacy, "containsRawOrganization"));
        privacy.put("containsRawConversation", truthy(catalogPrivacy, "containsRawConversation"));
        privacy.put("containsRawEndpoints", truthy(catalogPrivacy, "containsRawEndpoints"));
        privacy.put("containsRawReason", truthy(catalogPrivacy, "containsRawReason"));
        privacy.put("containsRawParameterValues", truthy(catalogPrivacy, "containsRawParameterValues"));
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawDocument", false);
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

    private static Map<String, Object> stage(int order,
                                             String id,
                                             String endpoint,
                                             String evidence,
                                             String status) {
        Map<String, Object> stage = new LinkedHashMap<>();
        stage.put("order", order);
        stage.put("id", id);
        stage.put("endpoint", endpoint);
        stage.put("evidence", evidence);
        stage.put("status", status);
        stage.put("runtimeMutationAllowed", false);
        return Map.copyOf(stage);
    }

    private static Map<String, Object> gate(String id, String requirement) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("id", id);
        gate.put("requirement", requirement);
        gate.put("required", true);
        gate.put("runtimeBound", false);
        return Map.copyOf(gate);
    }

    private static Map<String, Object> standard(String id, String mapping, String sourceUrl) {
        Map<String, Object> standard = new LinkedHashMap<>();
        standard.put("id", id);
        standard.put("mapping", mapping);
        standard.put("sourceUrl", sourceUrl);
        standard.put("runtimeBound", false);
        return Map.copyOf(standard);
    }

    private static boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }
}
