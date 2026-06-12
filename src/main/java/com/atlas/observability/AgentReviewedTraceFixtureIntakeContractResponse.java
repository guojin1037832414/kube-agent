package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reviewed redacted trace fixture intake specification for human/Git review.
 *
 * <p>中文说明：这个 record 是前端和学习文档可渲染的只读合同，描述 fixture 文件在被纳入
 * trace-set catalog 前必须提供的字段、质量门、隐私证明、端点导航和禁止捷径。</p>
 *
 * <p>安全边界：它只表达合同状态，不上传 fixture、不接收 caller traceIds、不写目录、不运行 eval、
 * 不触发 Tool/MCP/kube-manager/LLM/RAG，不创建 HITL/audit/memory，也不授予 release authority。</p>
 */
public record AgentReviewedTraceFixtureIntakeContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean runtimeIntakeAllowedNow,
    boolean fixtureUploadAccepted,
    boolean callerTraceIdsAccepted,
    boolean runtimeCatalogWrite,
    boolean catalogMutationAllowed,
    boolean releaseBlockingAllowedNow,
    boolean ciBlockingEnabled,
    boolean runtimeEvalAllowed,
    int traceSetCount,
    int reviewedTraceSetCount,
    int missingFixtureTraceSetCount,
    List<Map<String, Object>> requiredFixtureFields,
    List<Map<String, Object>> reviewWorkflow,
    List<Map<String, Object>> qualityGates,
    List<Map<String, Object>> traceSetReadiness,
    List<String> forbiddenShortcuts,
    List<String> nextActions,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-reviewed-trace-fixture-intake-contract.v1";
    public static final String ENDPOINT =
        "/api/agent/observability/eval/reviewed-trace-fixture-intake-contract";

    public static AgentReviewedTraceFixtureIntakeContractResponse of(Instant generatedAt,
                                                                     AgentEvalTraceSetCatalogResponse catalog) {
        List<AgentEvalTraceSetDefinition> traceSets = catalog != null ? catalog.traceSets() : List.of();
        List<Map<String, Object>> readiness = traceSets.stream()
            .map(AgentReviewedTraceFixtureIntakeContractResponse::traceSetReadiness)
            .toList();
        int reviewedTraceSets = (int) readiness.stream()
            .filter(row -> Boolean.TRUE.equals(row.get("reviewedTraceIdsPresent")))
            .count();
        int missing = Math.max(0, traceSets.size() - reviewedTraceSets);
        return new AgentReviewedTraceFixtureIntakeContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            "FIXTURE_INTAKE_CONTRACT_DEFINED_NOT_RUNTIME_BOUND",
            "Reviewed redacted trace fixture intake before catalog promotion",
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            traceSets.size(),
            reviewedTraceSets,
            missing,
            buildRequiredFixtureFields(),
            buildReviewWorkflow(),
            buildQualityGates(),
            readiness,
            buildForbiddenShortcuts(),
            nextActions(missing),
            buildEndpointMap(),
            buildSafety(),
            buildPrivacy(catalog)
        );
    }

    private static List<Map<String, Object>> buildRequiredFixtureFields() {
        return List.of(
            field("traceId", "W3C-compatible redacted trace anchor.", true, "OBSERVABILITY_ANCHOR"),
            field("traceSetId", "Target trace-set catalog entry selected by human review.", true, "CATALOG_SCOPE"),
            field("suiteId", "Eval suite that will consume the reviewed fixture.", true, "EVAL_SCOPE"),
            field("replaySource", "Redacted replay timeline source, never raw audit export.", true, "REPLAY_EVIDENCE"),
            field("redactionProof", "Proof that raw principal/org/conversation/endpoint/reason/params are absent.", true, "PRIVACY_GATE"),
            field("deterministicEvalProof", "Evidence that checks can run without LLM/external calls.", true, "EVAL_GATE"),
            field("privacyProof", "Structured privacy assertions for token/password/prompt/document absence.", true, "PRIVACY_GATE"),
            field("sourceCommitSha", "Git commit or review base that makes the fixture reproducible.", true, "GIT_REVIEW"),
            field("reviewer", "Human reviewer identity from source-control review, not runtime caller text.", true, "HUMAN_REVIEW"),
            field("reviewTimestamp", "Human review timestamp for provenance.", true, "HUMAN_REVIEW"),
            field("evidenceDigest", "Stable digest over redacted fixture evidence.", true, "INTEGRITY"),
            field("candidateGateSummary", "Deterministic curation/gate summary used during review.", true, "REVIEW_CONTEXT"),
            field("forbiddenRuntimeClaims", "Explicit false claims for Tool/MCP/kube-manager/CI/release authority.", true, "SAFETY_PROOF")
        );
    }

    private static List<Map<String, Object>> buildReviewWorkflow() {
        return List.of(
            stage(1, "candidate-discovery", "/api/agent/observability/eval/trace-sets/{traceSetId}/candidates",
                "Find redacted trace candidates from admin-only evidence.", "ADVISORY_ONLY"),
            stage(2, "curation-review", "/api/agent/observability/eval/trace-sets/{traceSetId}/curation-review",
                "Inspect deterministic gate evidence and privacy proof.", "REVIEW_ONLY"),
            stage(3, "fixture-intake-contract", ENDPOINT,
                "Confirm required fixture fields before human Git review.", "CONTRACT_ONLY"),
            stage(4, "catalog-patch-proposal", "/api/agent/observability/eval/trace-sets/{traceSetId}/catalog-patch-proposal",
                "Generate sanitized JSON Patch artifact for source review.", "PROPOSAL_ONLY"),
            stage(5, "human-git-review", "observability/eval-trace-sets.json",
                "Only source-control review may promote trace anchors.", "HUMAN_REQUIRED"),
            stage(6, "gate-bundle-regeneration", "/api/agent/observability/eval/workbench/gate-bundle-summary",
                "Regenerate compact release evidence after reviewed catalog changes.", "ADVISORY_NOW")
        );
    }

    private static List<Map<String, Object>> buildQualityGates() {
        return List.of(
            gate("w3c-trace-anchor", "Fixture traceId must be a redacted observability anchor, not user identity."),
            gate("redacted-replay-only", "Fixture source must be redacted replay evidence, never raw audit export."),
            gate("deterministic-eval-proof", "Fixture must be checkable without model, network, Tool, MCP, or kube-manager calls."),
            gate("privacy-proof", "Fixture must prove raw principal, org, conversation, endpoint, reason, params, token, password, prompt, and document are absent."),
            gate("human-git-review", "Catalog promotion requires human Git review; runtime callers cannot self-promote."),
            gate("no-runtime-authority", "Fixture intake does not open CI blocking, release gates, catalog writes, Tool execution, or retrieval prompt influence."),
            gate("phase1-core-only", "NIM, HPC, Slurm, and BCM are Phase 2 and cannot appear as Phase 1 fixture authority.")
        );
    }

    private static Map<String, Object> traceSetReadiness(AgentEvalTraceSetDefinition traceSet) {
        boolean reviewed = traceSet.traceIds() != null && !traceSet.traceIds().isEmpty();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("traceSetId", traceSet.id());
        row.put("suiteId", traceSet.suiteId());
        row.put("status", reviewed ? "HAS_REVIEWED_TRACE_IDS" : "NEEDS_REVIEWED_FIXTURE_INTAKE");
        row.put("reviewedTraceIdsPresent", reviewed);
        row.put("fixtureIntakeRequired", !reviewed);
        row.put("callerTraceIdOverrideAllowed", false);
        row.put("catalogMutationAllowed", false);
        row.put("runtimeEvalAllowed", false);
        row.put("evidenceRequirements", List.copyOf(traceSet.evidenceRequirements()));
        row.put("tags", List.copyOf(traceSet.tags()));
        return Map.copyOf(row);
    }

    private static List<String> buildForbiddenShortcuts() {
        return List.of(
            "caller-submitted-trace-ids",
            "raw-audit-export",
            "runtime-catalog-mutation",
            "eval-trace-sets-json-write",
            "ci-blocking-switch",
            "llm-as-judge-runtime-shortcut",
            "retrieval-prompt-influence",
            "mcp-tools-call",
            "safe-tool-executor-invocation",
            "kube-manager-read-or-write",
            "hitl-marker-creation",
            "audit-or-memory-write",
            "dependency-upgrade",
            "nim-hpc-slurm-bcm-phase2-authority"
        );
    }

    private static List<String> nextActions(int missingFixtureTraceSetCount) {
        if (missingFixtureTraceSetCount == 0) {
            return List.of(
                "inspect-reviewed-evidence-before-release-gate-promotion",
                "regenerate-gate-bundle-after-human-git-review",
                "keep-ci-blocking-disabled-until-separate-release-slice"
            );
        }
        return List.of(
            "discover-redacted-trace-candidates",
            "run-curation-review-with-deterministic-gates",
            "prepare-reviewed-redacted-fixture-files-outside-runtime",
            "attach-redaction-privacy-and-deterministic-eval-proof",
            "submit-catalog-patch-through-human-git-review",
            "keep-runtime-intake-upload-and-ci-blocking-disabled"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("fixtureIntakeContract", ENDPOINT);
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        endpoints.put("traceSetCandidates", "/api/agent/observability/eval/trace-sets/{traceSetId}/candidates");
        endpoints.put("traceSetCurationReview", "/api/agent/observability/eval/trace-sets/{traceSetId}/curation-review");
        endpoints.put("catalogPatchProposal", "/api/agent/observability/eval/trace-sets/{traceSetId}/catalog-patch-proposal");
        endpoints.put("promotionWorkflow", "/api/agent/observability/eval/trace-sets/{traceSetId}/promotion-workflow");
        endpoints.put("workbenchPromotionWorkflow", "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/promotion-workflow");
        endpoints.put("catalogPatchReview", "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review");
        endpoints.put("replayTimeline", "/api/agent/observability/replay/trace/{traceId}");
        endpoints.put("evalReport", "/api/agent/observability/eval/trace/{traceId}");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("contractOnly", true);
        safety.put("intakeSpecOnly", true);
        safety.put("runtimeIntakeAllowedNow", false);
        safety.put("fixtureUploadAccepted", false);
        safety.put("callerTraceIdsAccepted", false);
        safety.put("runtimeMutationAllowed", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("evalTraceSetsJsonWrite", false);
        safety.put("releaseBlockingAllowedNow", false);
        safety.put("ciBlockingEnabled", false);
        safety.put("runtimeEvalAllowed", false);
        safety.put("replayExecuted", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("auditWrite", false);
        safety.put("memoryWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("ragPromptInfluence", false);
        safety.put("mcpToolCall", false);
        safety.put("kubeManagerCalls", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(AgentEvalTraceSetCatalogResponse catalog) {
        Map<String, Object> catalogPrivacy = catalog != null ? catalog.privacy() : Map.of();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !truthy(catalogPrivacy, "containsRawPrincipal")
            && !truthy(catalogPrivacy, "containsRawOrganization")
            && !truthy(catalogPrivacy, "containsRawConversation")
            && !truthy(catalogPrivacy, "containsRawEndpoints")
            && !truthy(catalogPrivacy, "containsRawReason")
            && !truthy(catalogPrivacy, "containsRawParameterValues"));
        privacy.put("rawAuditExportAllowed", false);
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

    private static Map<String, Object> field(String name,
                                             String description,
                                             boolean required,
                                             String evidenceRole) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("description", description);
        field.put("required", required);
        field.put("evidenceRole", evidenceRole);
        field.put("callerSuppliedRuntimeAuthority", false);
        return Map.copyOf(field);
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
        return Map.copyOf(stage);
    }

    private static Map<String, Object> gate(String id, String description) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("id", id);
        gate.put("description", description);
        gate.put("blocking", true);
        return Map.copyOf(gate);
    }

    private static boolean truthy(Map<String, Object> map, String key) {
        Object value = map != null ? map.get(key) : null;
        return Boolean.TRUE.equals(value);
    }
}
