package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reviewed redacted trace fixture 作者模板。
 *
 * <p>中文说明：这个 record 把“真实 fixture 应如何入仓”投影成前端可渲染的只读结构：
 * JSON schema、示例骨架、命名规则、每个 trace set 的待补文件建议和禁止捷径。
 * 它帮助推进功能落地，但不会假装已经有 reviewed trace，也不会替代人审/Git review。</p>
 *
 * <p>安全边界：template 是 repo-native authoring guide，不创建文件、不扫描 raw audit、不写 catalog、
 * 不运行 eval/replay、不调用 Tool/MCP/LLM/RAG/kube-manager、不写 HITL/audit/memory，
 * 也不授予 CI blocking 或 release authority。</p>
 */
public record AgentReviewedTraceFixtureTemplateResponse(
    String schemaVersion,
    Instant generatedAt,
    String templateStatus,
    String target,
    String fixtureDirectory,
    String fixtureClasspathPattern,
    boolean phase1TopTierGoalPreserved,
    boolean templateOnly,
    boolean createsFixtureFile,
    boolean placeholderTraceIdsAllowed,
    boolean runtimeIntakeAllowedNow,
    boolean fixtureUploadAccepted,
    boolean callerTraceIdsAccepted,
    boolean runtimeCatalogWrite,
    boolean catalogMutationAllowed,
    boolean releaseBlockingAllowedNow,
    boolean ciBlockingEnabled,
    boolean runtimeEvalAllowed,
    int traceSetCount,
    List<Map<String, Object>> requiredFields,
    List<Map<String, Object>> structuredProofBlocks,
    Map<String, Object> fixtureJsonSchema,
    Map<String, Object> exampleFixtureSkeleton,
    List<Map<String, Object>> traceSetTemplates,
    List<String> fileNamingRules,
    List<String> authoringWorkflow,
    List<String> forbiddenShortcuts,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-reviewed-trace-fixture-template.v1";
    public static final String ENDPOINT =
        "/api/agent/observability/eval/reviewed-trace-fixture-template";
    public static final String FIXTURE_DIRECTORY =
        "src/main/resources/observability/reviewed-trace-fixtures";

    public static AgentReviewedTraceFixtureTemplateResponse of(Instant generatedAt,
                                                               AgentEvalTraceSetCatalogResponse catalog) {
        List<AgentEvalTraceSetDefinition> traceSets = catalog != null ? catalog.traceSets() : List.of();
        return new AgentReviewedTraceFixtureTemplateResponse(
            SCHEMA_VERSION,
            generatedAt,
            "TEMPLATE_READY_FOR_HUMAN_AUTHORED_FIXTURES",
            "Reviewed redacted trace fixture authoring template before real fixture files exist",
            FIXTURE_DIRECTORY,
            AgentReviewedTraceFixtureManifestService.FIXTURE_RESOURCE_PATTERN,
            true,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            traceSets.size(),
            buildRequiredFields(),
            buildStructuredProofBlocks(),
            buildFixtureJsonSchema(),
            buildExampleFixtureSkeleton(),
            traceSets.stream()
                .map(AgentReviewedTraceFixtureTemplateResponse::traceSetTemplate)
                .toList(),
            buildFileNamingRules(),
            buildAuthoringWorkflow(),
            buildForbiddenShortcuts(),
            buildEndpointMap(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> buildRequiredFields() {
        return List.of(
            field("traceId", "Reviewed W3C-compatible redacted trace anchor; placeholder values are rejected.", "string", "OBSERVABILITY_ANCHOR"),
            field("traceSetId", "Catalog trace-set id selected by human review.", "string", "CATALOG_SCOPE"),
            field("suiteId", "Eval suite id bound to the target trace set.", "string", "EVAL_SCOPE"),
            field("replaySource", "Redacted replay timeline source and digest, never raw audit export.", "object", "REPLAY_EVIDENCE"),
            field("redactionProof", "Proof that raw principal/org/conversation/endpoint/reason/params are absent.", "object", "PRIVACY_GATE"),
            field("deterministicEvalProof", "Proof that eval checks require no LLM, Tool, MCP, kube-manager, or external network.", "object", "EVAL_GATE"),
            field("privacyProof", "Token/password/prompt/document absence assertions.", "object", "PRIVACY_GATE"),
            field("sourceCommitSha", "Git commit base used to reproduce and review the fixture.", "string", "GIT_REVIEW"),
            field("reviewer", "Human reviewer identity from source-control review, not runtime caller text.", "string", "HUMAN_REVIEW"),
            field("reviewTimestamp", "Human review timestamp in ISO-8601 form.", "string", "HUMAN_REVIEW"),
            field("evidenceDigest", "Stable digest over the redacted fixture payload.", "string", "INTEGRITY"),
            field("candidateGateSummary", "Deterministic curation/gate summary captured during review.", "object", "REVIEW_CONTEXT"),
            field("forbiddenRuntimeClaims", "Explicit false claims for closed runtime powers.", "array", "SAFETY_PROOF")
        );
    }

    private static List<Map<String, Object>> buildStructuredProofBlocks() {
        return List.of(
            proofBlock("replaySource", List.of("type", "digest", "timelineStepCount", "redactedOnly")),
            proofBlock("redactionProof", List.of("containsRawPrincipal", "containsRawOrganization", "containsRawConversation",
                "containsRawEndpoints", "containsRawReason", "containsRawParameterValues")),
            proofBlock("deterministicEvalProof", List.of("deterministic", "llmUsed", "externalCalls", "toolExecution",
                "mcpToolCall", "kubeManagerCalls")),
            proofBlock("privacyProof", List.of("containsAuthorizationHeader", "containsToken", "containsPassword",
                "containsRawPrompt", "containsRawDocument")),
            proofBlock("candidateGateSummary", List.of("traceSetId", "suiteId", "gateVerdict", "score", "warnings")),
            proofBlock("forbiddenRuntimeClaims", List.of("runtimeCatalogWrite:false", "ciBlocking:false",
                "releaseAuthority:false", "phase2Authority:false"))
        );
    }

    private static Map<String, Object> buildFixtureJsonSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("$schema", "https://json-schema.org/draft/2020-12/schema");
        schema.put("$id", "agent-reviewed-trace-fixture.v1");
        schema.put("title", "Reviewed redacted trace fixture");
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", buildRequiredFields().stream()
            .map(field -> field.get("name"))
            .toList());
        schema.put("properties", jsonSchemaProperties());
        schema.put("templateOnly", true);
        schema.put("placeholderTraceIdsAllowed", false);
        schema.put("runtimeCatalogWrite", false);
        return Map.copyOf(schema);
    }

    private static Map<String, Object> jsonSchemaProperties() {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (Map<String, Object> field : buildRequiredFields()) {
            Map<String, Object> property = new LinkedHashMap<>();
            property.put("type", field.get("jsonType"));
            property.put("description", field.get("description"));
            property.put("callerSuppliedRuntimeAuthority", false);
            properties.put(String.valueOf(field.get("name")), Map.copyOf(property));
        }
        return Map.copyOf(properties);
    }

    private static Map<String, Object> buildExampleFixtureSkeleton() {
        Map<String, Object> skeleton = new LinkedHashMap<>();
        skeleton.put("traceId", "<reviewed-w3c-trace-id>");
        skeleton.put("traceSetId", "<catalog-trace-set-id>");
        skeleton.put("suiteId", "<catalog-suite-id>");
        skeleton.put("replaySource", Map.of(
            "type", "redacted-replay-timeline",
            "digest", "sha256:<redacted-replay-digest>",
            "timelineStepCount", "<reviewed-step-count>",
            "redactedOnly", true
        ));
        skeleton.put("redactionProof", Map.of(
            "containsRawPrincipal", false,
            "containsRawOrganization", false,
            "containsRawConversation", false,
            "containsRawEndpoints", false,
            "containsRawReason", false,
            "containsRawParameterValues", false
        ));
        skeleton.put("deterministicEvalProof", Map.of(
            "deterministic", true,
            "llmUsed", false,
            "externalCalls", false,
            "toolExecution", false,
            "mcpToolCall", false,
            "kubeManagerCalls", false
        ));
        skeleton.put("privacyProof", Map.of(
            "containsAuthorizationHeader", false,
            "containsToken", false,
            "containsPassword", false,
            "containsRawPrompt", false,
            "containsRawDocument", false
        ));
        skeleton.put("sourceCommitSha", "<git-commit-sha>");
        skeleton.put("reviewer", "<human-git-reviewer>");
        skeleton.put("reviewTimestamp", "<iso-8601-review-time>");
        skeleton.put("evidenceDigest", "sha256:<fixture-evidence-digest>");
        skeleton.put("candidateGateSummary", Map.of(
            "traceSetId", "<catalog-trace-set-id>",
            "suiteId", "<catalog-suite-id>",
            "gateVerdict", "<pass-or-review>",
            "score", "<deterministic-score>",
            "warnings", List.of()
        ));
        skeleton.put("forbiddenRuntimeClaims", forbiddenRuntimeClaims());
        return Map.copyOf(skeleton);
    }

    private static Map<String, Object> traceSetTemplate(AgentEvalTraceSetDefinition traceSet) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("traceSetId", traceSet.id());
        row.put("suiteId", traceSet.suiteId());
        row.put("suggestedFilename", traceSet.id() + ".reviewed-trace-fixture.json");
        row.put("fixtureDirectory", FIXTURE_DIRECTORY);
        row.put("catalogTraceIdsPresent", traceSet.traceIds() != null && !traceSet.traceIds().isEmpty());
        row.put("templateStatus", "AWAITING_REAL_REVIEWED_REDACTED_TRACE");
        row.put("templateOnly", true);
        row.put("placeholderTraceIdsAllowed", false);
        row.put("catalogMutationAllowed", false);
        row.put("runtimeCatalogWrite", false);
        row.put("runtimeEvalAllowed", false);
        row.put("requiresHumanGitReview", true);
        row.put("tags", traceSet.tags() != null ? List.copyOf(traceSet.tags()) : List.of());
        row.put("evidenceRequirements", traceSet.evidenceRequirements() != null
            ? List.copyOf(traceSet.evidenceRequirements())
            : List.of());
        return Map.copyOf(row);
    }

    private static List<String> buildFileNamingRules() {
        return List.of(
            "store-reviewed-files-under-src-main-resources-observability-reviewed-trace-fixtures",
            "use-<traceSetId>.reviewed-trace-fixture.json-as-the-default-name",
            "commit-only-real-reviewed-redacted-fixtures",
            "do-not-commit-placeholder-template-json-to-the-scanned-fixture-directory",
            "one-fixture-file-should-target-one-catalog-trace-set"
        );
    }

    private static List<String> buildAuthoringWorkflow() {
        return List.of(
            "discover-redacted-candidate-through-admin-only-candidates-endpoint",
            "run-curation-review-and-catalog-patch-proposal-as-review-artifacts",
            "copy-the-template-structure-outside-runtime-and-fill-real-reviewed-evidence",
            "prove-redaction-privacy-determinism-and-evidence-digest",
            "commit-fixture-json-through-human-git-review",
            "then-use-manifest-and-catalog-patch-review-to-prepare-catalog-update"
        );
    }

    private static List<String> buildForbiddenShortcuts() {
        return List.of(
            "placeholder-trace-id-commit",
            "fake-reviewed-fixture-file",
            "runtime-fixture-upload",
            "caller-trace-id-intake",
            "raw-audit-export",
            "runtime-catalog-write",
            "eval-trace-sets-json-mutation",
            "eval-or-replay-runtime-execution",
            "ci-blocking-switch",
            "release-authority",
            "mcp-tools-call",
            "safe-tool-executor-invocation",
            "kube-manager-call",
            "audit-or-memory-write",
            "nim-hpc-slurm-bcm-phase2-authority"
        );
    }

    private static List<String> forbiddenRuntimeClaims() {
        return List.of(
            "runtimeCatalogWrite:false",
            "catalogMutationAllowed:false",
            "runtimeEvalAllowed:false",
            "ciBlockingEnabled:false",
            "releaseAuthority:false",
            "toolExecution:false",
            "mcpToolCall:false",
            "kubeManagerCalls:false",
            "auditWrite:false",
            "memoryWrite:false",
            "phase2Authority:false"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("fixtureTemplate", ENDPOINT);
        endpoints.put("fixtureManifest", AgentReviewedTraceFixtureManifestResponse.ENDPOINT);
        endpoints.put("fixtureIntakeContract", AgentReviewedTraceFixtureIntakeContractResponse.ENDPOINT);
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        endpoints.put("traceSetCandidates", "/api/agent/observability/eval/trace-sets/{traceSetId}/candidates");
        endpoints.put("traceSetCurationReview", "/api/agent/observability/eval/trace-sets/{traceSetId}/curation-review");
        endpoints.put("catalogPatchProposal", "/api/agent/observability/eval/trace-sets/{traceSetId}/catalog-patch-proposal");
        endpoints.put("catalogPatchReview", "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/catalog-patch-review");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("templateOnly", true);
        safety.put("schemaOnly", true);
        safety.put("createsFixtureFile", false);
        safety.put("placeholderTraceIdsAllowed", false);
        safety.put("runtimeIntakeAllowedNow", false);
        safety.put("fixtureUploadAccepted", false);
        safety.put("callerTraceIdsAccepted", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("catalogMutationAllowed", false);
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

    private static Map<String, Object> buildPrivacy() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("rawAuditExportAllowed", false);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawConversation", false);
        privacy.put("containsRawEndpoints", false);
        privacy.put("containsRawReason", false);
        privacy.put("containsRawParameterValues", false);
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
                                             String jsonType,
                                             String evidenceRole) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("name", name);
        field.put("description", description);
        field.put("jsonType", jsonType);
        field.put("required", true);
        field.put("evidenceRole", evidenceRole);
        field.put("callerSuppliedRuntimeAuthority", false);
        return Map.copyOf(field);
    }

    private static Map<String, Object> proofBlock(String name, List<String> requiredKeys) {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("name", name);
        block.put("requiredKeys", requiredKeys);
        block.put("runtimeAuthority", false);
        return Map.copyOf(block);
    }
}
