package com.atlas.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Vue-ready manifest for Memory/RAG reviewed redacted trace fixtures.
 */
public record AgentMemoryRagReviewedTraceEvidenceManifestResponse(
    String schemaVersion,
    Instant generatedAt,
    String manifestStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean sourceContractsEmbedded,
    boolean runtimeControlAllowed,
    int requiredTraceSetCount,
    int reviewedTraceSetCount,
    int reviewedTraceAnchorCount,
    int authoritativeFixtureCount,
    int promotionReadyTraceSetCount,
    int blockingRequirementCount,
    List<Map<String, Object>> requiredTraceSets,
    List<Map<String, Object>> evidenceIntakeSchema,
    List<Map<String, Object>> reviewWorkflow,
    List<Map<String, Object>> advancedTechnologyMappings,
    List<String> nextActions,
    AgentMemoryRagTraceSetCurationContractResponse curationContract,
    AgentMemoryRagSourceEvidenceDigestContractResponse sourceEvidenceDigestContract,
    AgentMemoryRagDurableMemoryLifecycleContractResponse durableMemoryLifecycleContract,
    AgentMemoryRagEvalGateContractResponse evalGateContract,
    AgentMemoryRagEvalSuiteBindingContractResponse evalSuiteBindingContract,
    AgentMemoryRagReadinessResponse memoryRagReadiness,
    Map<String, Object> endpointMap,
    Map<String, Object> manifestPolicy,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-memory-rag-reviewed-trace-evidence-manifest.v1";
    public static final String MANIFEST_ENDPOINT =
        "/api/agent/observability/memory-rag/workbench/trace-set-curation/review-manifest";
    private static final String CATALOG_PATCH_TARGET = "src/main/resources/observability/eval-trace-sets.json";

    public static AgentMemoryRagReviewedTraceEvidenceManifestResponse of(
        Instant generatedAt,
        AgentMemoryRagTraceSetCurationContractResponse curationContract,
        AgentMemoryRagSourceEvidenceDigestContractResponse sourceEvidenceDigestContract,
        AgentMemoryRagDurableMemoryLifecycleContractResponse durableMemoryLifecycleContract,
        AgentMemoryRagEvalGateContractResponse evalGateContract,
        AgentMemoryRagEvalSuiteBindingContractResponse evalSuiteBindingContract,
        AgentMemoryRagReadinessResponse memoryRagReadiness
    ) {
        List<Map<String, Object>> traceSets = traceSets(curationContract);
        int reviewedTraceSetCount = countTrue(traceSets, "authoritativeFixturePresent");
        int reviewedTraceAnchorCount = traceSets.stream()
            .mapToInt(row -> intValue(row.get("curatedTraceCount")))
            .sum();
        int promotionReadyTraceSetCount = countTrue(traceSets, "safeToPromoteNow");
        int blockingRequirementCount = traceSets.stream()
            .mapToInt(row -> intValue(row.get("blockingRequirementCount")))
            .sum();
        return new AgentMemoryRagReviewedTraceEvidenceManifestResponse(
            SCHEMA_VERSION,
            generatedAt,
            manifestStatus(curationContract, reviewedTraceSetCount, traceSets.size()),
            "Memory/RAG reviewed redacted trace evidence intake manifest",
            curationContract != null && curationContract.phase1TopTierGoalPreserved(),
            true,
            curationContract != null
                && sourceEvidenceDigestContract != null
                && durableMemoryLifecycleContract != null
                && evalGateContract != null
                && evalSuiteBindingContract != null
                && memoryRagReadiness != null,
            false,
            traceSets.size(),
            reviewedTraceSetCount,
            reviewedTraceAnchorCount,
            reviewedTraceSetCount,
            promotionReadyTraceSetCount,
            blockingRequirementCount,
            traceSets,
            buildEvidenceIntakeSchema(),
            buildReviewWorkflow(),
            buildAdvancedTechnologyMappings(),
            nextActions(reviewedTraceSetCount, traceSets.size()),
            curationContract,
            sourceEvidenceDigestContract,
            durableMemoryLifecycleContract,
            evalGateContract,
            evalSuiteBindingContract,
            memoryRagReadiness,
            buildEndpointMap(),
            manifestPolicy(traceSets, blockingRequirementCount),
            safety(curationContract),
            privacy(curationContract, sourceEvidenceDigestContract, durableMemoryLifecycleContract,
                evalGateContract, evalSuiteBindingContract, memoryRagReadiness)
        );
    }

    private static String manifestStatus(AgentMemoryRagTraceSetCurationContractResponse contract,
                                         int reviewedTraceSetCount,
                                         int requiredTraceSetCount) {
        if (contract == null) {
            return "MANIFEST_SOURCE_CONTRACT_MISSING";
        }
        if (!contract.suiteRuntimePolicyClosed() || !contract.allRequiredTraceSetsPolicyClosed()) {
            return "MANIFEST_BLOCKED_BY_POLICY_LATCH";
        }
        if (!contract.allRequiredTraceSetsDefined()) {
            return "MANIFEST_BLOCKED_BY_TRACE_SET_CATALOG";
        }
        if (requiredTraceSetCount == 0 || reviewedTraceSetCount < requiredTraceSetCount) {
            return "WAITING_FOR_REVIEWED_REDACTED_TRACE_FIXTURES";
        }
        return "REVIEWED_REDACTED_FIXTURES_READY_FOR_ADVISORY_REVIEW";
    }

    private static List<Map<String, Object>> traceSets(
        AgentMemoryRagTraceSetCurationContractResponse contract
    ) {
        if (contract == null) {
            return List.of();
        }
        List<Map<String, Object>> rows = contract.traceSetRows();
        List<Map<String, Object>> traceSets = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            traceSets.add(traceSet(i, rows.get(i)));
        }
        return List.copyOf(traceSets);
    }

    private static Map<String, Object> traceSet(int catalogIndex, Map<String, Object> row) {
        String traceSetId = string(row, "traceSetId");
        List<String> missingEvidence = stringList(row.get("missingEvidence"));
        List<String> blockedReasons = stringList(row.get("blockedReasons"));
        int curatedTraceCount = intValue(row.get("traceIdCount"));
        boolean authoritativeFixturePresent = bool(row, "reviewedTraceIdsPresent") && curatedTraceCount > 0;
        boolean policyClosed = bool(row, "policyLatchDeclaredClosed");
        Map<String, Object> traceSet = new LinkedHashMap<>();
        traceSet.put("traceSetId", traceSetId);
        traceSet.put("suiteId", string(row, "suiteId"));
        traceSet.put("title", string(row, "title"));
        traceSet.put("purpose", string(row, "purpose"));
        traceSet.put("catalogIndex", catalogIndex);
        traceSet.put("catalogPatchTarget", CATALOG_PATCH_TARGET);
        traceSet.put("rowStatus", string(row, "rowStatus"));
        traceSet.put("manifestRowStatus", authoritativeFixturePresent
            ? "REVIEWED_FIXTURE_DECLARED"
            : "FIXTURE_NOT_PRESENT");
        traceSet.put("curatedTraceCount", curatedTraceCount);
        traceSet.put("traceIdsVisibleInManifest", false);
        traceSet.put("authoritativeFixturePresent", authoritativeFixturePresent);
        traceSet.put("safeToPromoteNow", authoritativeFixturePresent && policyClosed);
        traceSet.put("safeToRunEvalNow", false);
        traceSet.put("safeToEnableRetrievalNow", false);
        traceSet.put("safeToEnableCiBlockingNow", false);
        traceSet.put("policyLatchDeclaredClosed", policyClosed);
        traceSet.put("policyKeysPresent", bool(row, "policyKeysPresent"));
        traceSet.put("missingPolicyKeys", stringList(row.get("missingPolicyKeys")));
        traceSet.put("policyMismatches", stringList(row.get("policyMismatches")));
        traceSet.put("missingEvidence", missingEvidence);
        traceSet.put("requiredDigestEvidence", requiredDigestEvidence(traceSetId));
        traceSet.put("requiredTraceAnchorSchema", traceAnchorSchema(traceSetId));
        traceSet.put("intakeAcceptanceCriteria", intakeAcceptanceCriteria(traceSetId));
        traceSet.put("blockedReasons", blockedReasons);
        traceSet.put("blockingRequirementCount", missingEvidence.size() + blockedReasons.size());
        traceSet.put("evidenceRequirements", stringList(row.get("evidenceRequirements")));
        traceSet.put("endpointTemplates", endpointTemplates(traceSetId));
        traceSet.put("disabledRuntimeActions", disabledRuntimeActions(traceSetId));
        traceSet.put("humanGitReviewRequired", true);
        traceSet.put("catalogMutationAllowed", false);
        traceSet.put("runtimeCatalogWrite", false);
        traceSet.put("runtimeRetrievalAllowed", false);
        traceSet.put("ciBlockingAllowed", false);
        return Map.copyOf(traceSet);
    }

    private static List<String> requiredDigestEvidence(String traceSetId) {
        return switch (traceSetId) {
            case "memory-rag-citation-fidelity" -> List.of(
                "sourceDigest",
                "chunkDigest",
                "evidenceDigest",
                "citationSeed",
                "retentionPolicyId",
                "retrievalPolicyId"
            );
            case "memory-rag-privacy-tenant" -> List.of(
                "tenantPartitionDigest",
                "sourceAclDigest",
                "redactionProofDigest",
                "negativeRetrievalProofDigest"
            );
            case "memory-rag-lifecycle-policy" -> List.of(
                "retentionPolicyId",
                "deleteProofDigest",
                "exportProofDigest",
                "recoveryCheckpointDigest",
                "evalGateDigest"
            );
            default -> List.of("redactedReplayDigest");
        };
    }

    private static Map<String, Object> traceAnchorSchema(String traceSetId) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("traceId", "required W3C-compatible redacted trace anchor");
        schema.put("traceSetId", traceSetId);
        schema.put("source", "persisted redacted replay evidence");
        schema.put("reviewNote", "human Git review note without raw prompt, document, tenant secret, or parameter values");
        schema.put("digestEvidence", requiredDigestEvidence(traceSetId));
        schema.put("rawValuesAllowed", false);
        schema.put("callerSubmittedToThisEndpoint", false);
        return Map.copyOf(schema);
    }

    private static List<String> intakeAcceptanceCriteria(String traceSetId) {
        List<String> criteria = new ArrayList<>();
        criteria.add("trace-anchor-is-redacted-and-portable");
        criteria.add("trace-anchor-is-backed-by-persisted-replay-evidence");
        criteria.add("catalog-patch-is-reviewed-through-source-control");
        criteria.add("raw-prompt-document-chunk-principal-organization-and-parameter-values-are-absent");
        criteria.add("runtime-retrieval-and-ci-blocking-remain-closed");
        if ("memory-rag-privacy-tenant".equals(traceSetId)) {
            criteria.add("negative-cross-tenant-retrieval-case-is-reviewed");
        }
        if ("memory-rag-lifecycle-policy".equals(traceSetId)) {
            criteria.add("delete-export-recovery-and-stale-memory-cases-are-reviewed");
        }
        return List.copyOf(criteria);
    }

    private static List<Map<String, Object>> buildEvidenceIntakeSchema() {
        return List.of(
            schemaField("traceId", "W3C-compatible redacted trace anchor", true, false),
            schemaField("traceSetId", "one of the required Memory/RAG trace-set IDs", true, false),
            schemaField("sourceDigest", "stable digest of source custody evidence", true, false),
            schemaField("chunkDigest", "stable digest of retrieved chunk or negative retrieval proof", false, false),
            schemaField("tenantPartitionDigest", "stable digest proving tenant partition boundary", false, false),
            schemaField("retentionPolicyId", "reviewed lifecycle policy identifier", true, false),
            schemaField("reviewNote", "human-readable review note without raw content", true, false)
        );
    }

    private static List<Map<String, Object>> buildReviewWorkflow() {
        return List.of(
            workflowStep(1, "capture-redacted-replay-evidence",
                "Persist only redacted replay/audit anchors before any catalog proposal.", "HUMAN_REVIEW_REQUIRED"),
            workflowStep(2, "verify-memory-rag-digest-evidence",
                "Check source, chunk, tenant, lifecycle, and retrieval-policy digests against the manifest.", "REVIEW_ONLY"),
            workflowStep(3, "prepare-catalog-patch-review",
                "Render sanitized patch evidence for src/main/resources/observability/eval-trace-sets.json.", "REVIEW_ONLY"),
            workflowStep(4, "merge-through-human-git-review",
                "Only source-control review may promote trace anchors into the catalog.", "HUMAN_REQUIRED"),
            workflowStep(5, "return-to-workbench",
                "Re-open the Memory/RAG curation workbench after reviewed trace IDs exist.", "ADVISORY_ONLY"),
            workflowStep(6, "keep-runtime-closed",
                "Retrieval, vector stores, eval runtime, and CI blocking stay closed until later reviewed slices.", "BLOCKED_NOW")
        );
    }

    private static List<Map<String, Object>> buildAdvancedTechnologyMappings() {
        return List.of(
            technologyMapping("spring-ai-memory-rag-vectorstore",
                "Map Spring AI Memory/RAG and VectorStore ideas to digest, lifecycle, and eval gates before runtime.",
                "https://docs.spring.io/spring-ai/reference/"),
            technologyMapping("openai-agents-tracing-guardrails-evals",
                "Map tracing, guardrails, handoffs, and eval loops to reviewed trace anchors and fail-closed policies.",
                "https://openai.github.io/openai-agents-python/"),
            technologyMapping("mcp-2025-11-25-tools-resources-prompts",
                "Keep MCP tools/resources/prompts as governed protocol surfaces until SafeToolExecutor binding exists.",
                "https://modelcontextprotocol.io/specification/2025-11-25"),
            technologyMapping("otel-genai-semantic-conventions",
                "Expose GenAI telemetry through stable redacted atlas.agent fields and adapter-only mappings.",
                "https://opentelemetry.io/docs/specs/semconv/gen-ai/"),
            technologyMapping("a2a-agent-card-task-artifact-provenance",
                "Treat future Agent Card, task, message, and artifact handoff evidence as reviewed provenance.",
                "https://a2a-protocol.org/latest/specification/"),
            technologyMapping("owasp-llm-risk-gates",
                "Bind prompt-injection, sensitive disclosure, and excessive-agency risks to deterministic gates.",
                "https://owasp.org/www-project-top-10-for-large-language-model-applications/")
        );
    }

    private static List<String> nextActions(int reviewedTraceSetCount, int requiredTraceSetCount) {
        if (requiredTraceSetCount > 0 && reviewedTraceSetCount == requiredTraceSetCount) {
            return List.of(
                "review-memory-rag-fixture-manifest-before-advisory-gate-bundle",
                "regenerate-memory-rag-workbench-after-git-review",
                "prepare-separate-advisory-gate-bundle-slice"
            );
        }
        return List.of(
            "capture-authoritative-redacted-memory-rag-trace-fixtures",
            "verify-source-tenant-lifecycle-and-retrieval-policy-digests",
            "prepare-human-git-review-catalog-patch",
            "keep-eval-runtime-retrieval-vector-store-and-ci-blocking-closed",
            "do-not-touch-nim-hpc-slurm-bcm-phase2"
        );
    }

    private static Map<String, Object> endpointTemplates(String traceSetId) {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("reviewManifest", MANIFEST_ENDPOINT);
        endpoints.put("workbenchOverview", AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse.OVERVIEW_ENDPOINT);
        endpoints.put("curationContract", "/api/agent/observability/memory-rag/trace-set-curation-contract");
        endpoints.put("evalWorkbenchTraceSetDetail",
            "/api/agent/observability/eval/workbench/trace-sets/" + traceSetId);
        endpoints.put("catalogPatchReview",
            "/api/agent/observability/eval/workbench/trace-sets/" + traceSetId + "/catalog-patch-review");
        endpoints.put("candidateDiscoveryDisabled",
            "/api/agent/observability/eval/trace-sets/" + traceSetId + "/candidates");
        endpoints.put("curationReviewDisabled",
            "/api/agent/observability/eval/trace-sets/" + traceSetId + "/curation-review");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("memoryRagReviewedTraceEvidenceManifest", MANIFEST_ENDPOINT);
        endpoints.put("memoryRagTraceSetCurationWorkbenchOverview",
            AgentMemoryRagTraceSetCurationWorkbenchOverviewResponse.OVERVIEW_ENDPOINT);
        endpoints.put("memoryRagTraceSetCurationContract",
            "/api/agent/observability/memory-rag/trace-set-curation-contract");
        endpoints.put("memoryRagSourceEvidenceDigestContract",
            "/api/agent/observability/memory-rag/source-evidence-digest-contract");
        endpoints.put("memoryRagDurableMemoryLifecycleContract",
            "/api/agent/observability/memory-rag/durable-memory-lifecycle-contract");
        endpoints.put("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        endpoints.put("memoryRagEvalSuiteBindingContract",
            "/api/agent/observability/memory-rag/eval-suite-binding-contract");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("traceSetCatalog", "/api/agent/observability/eval/trace-sets");
        return Map.copyOf(endpoints);
    }

    private static List<Map<String, Object>> disabledRuntimeActions(String traceSetId) {
        return List.of(
            disabledAction("candidate-discovery", "GET",
                "/api/agent/observability/eval/trace-sets/" + traceSetId + "/candidates"),
            disabledAction("curation-review", "POST",
                "/api/agent/observability/eval/trace-sets/" + traceSetId + "/curation-review"),
            disabledAction("trace-set-gate", "POST",
                "/api/agent/observability/eval/trace-sets/" + traceSetId + "/gate"),
            disabledAction("retrieval-runtime", "N/A", "memory-rag-retrieval-runtime"),
            disabledAction("ci-blocking-switch", "N/A", "agent-eval-ci-blocking")
        );
    }

    private static Map<String, Object> manifestPolicy(List<Map<String, Object>> traceSets,
                                                      int blockingRequirementCount) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("readOnly", true);
        policy.put("manifestOnly", true);
        policy.put("vueWorkbenchOnly", true);
        policy.put("requiredTraceSetCount", traceSets.size());
        policy.put("blockingRequirementCount", blockingRequirementCount);
        policy.put("traceIdsAcceptedFromCaller", false);
        policy.put("traceIdsVisibleInManifest", false);
        policy.put("authoritativeFixturesRequired", true);
        policy.put("catalogPatchTarget", CATALOG_PATCH_TARGET);
        policy.put("catalogMutationAllowed", false);
        policy.put("runtimeCatalogWrite", false);
        policy.put("requiresHumanGitReview", true);
        policy.put("requiresSourceEvidenceDigest", true);
        policy.put("requiresMemoryLifecycleEvidence", true);
        policy.put("requiresTenantPrivacyEvidence", true);
        policy.put("runtimeControlAllowed", false);
        policy.put("evalRuntimeAllowedNow", false);
        policy.put("retrievalRuntimeAllowedNow", false);
        policy.put("ciBlockingAllowedNow", false);
        policy.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> safety(AgentMemoryRagTraceSetCurationContractResponse contract) {
        Map<String, Object> sourceSafety = contract != null ? contract.safety() : Map.of();
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("manifestOnly", true);
        safety.put("sourceContractReadOnly", bool(sourceSafety, "readOnly"));
        safety.put("traceIdsAcceptedFromCaller", false);
        safety.put("candidateDiscoveryInvoked", false);
        safety.put("curationReviewInvoked", false);
        safety.put("traceSetGateInvoked", false);
        safety.put("evalRuntimeExecuted", false);
        safety.put("catalogMutationAllowed", false);
        safety.put("runtimeCatalogWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("ingestionExecuted", false);
        safety.put("memoryWrite", false);
        safety.put("auditWrite", false);
        safety.put("vectorStoreCalls", false);
        safety.put("embeddingModelCalls", false);
        safety.put("rerankerCalls", false);
        safety.put("llmUsed", false);
        safety.put("promptMutation", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("mcpToolCall", false);
        safety.put("kubeManagerCalls", false);
        safety.put("externalCalls", false);
        safety.put("ciBlockingChanged", false);
        safety.put("dependencyUpgrade", false);
        safety.put("durableReceiptIssued", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> privacy(AgentMemoryRagTraceSetCurationContractResponse curationContract,
                                               AgentMemoryRagSourceEvidenceDigestContractResponse sourceEvidence,
                                               AgentMemoryRagDurableMemoryLifecycleContractResponse lifecycle,
                                               AgentMemoryRagEvalGateContractResponse evalGate,
                                               AgentMemoryRagEvalSuiteBindingContractResponse evalSuiteBinding,
                                               AgentMemoryRagReadinessResponse readiness) {
        Map<String, Object> curationPrivacy = curationContract != null ? curationContract.privacy() : Map.of();
        Map<String, Object> sourcePrivacy = sourceEvidence != null ? sourceEvidence.privacy() : Map.of();
        Map<String, Object> lifecyclePrivacy = lifecycle != null ? lifecycle.privacy() : Map.of();
        Map<String, Object> evalGatePrivacy = evalGate != null ? evalGate.privacy() : Map.of();
        Map<String, Object> bindingPrivacy = evalSuiteBinding != null ? evalSuiteBinding.privacy() : Map.of();
        Map<String, Object> readinessPrivacy = readiness != null ? readiness.privacy() : Map.of();
        boolean containsRawPrincipal = truthyAny("containsRawPrincipal", curationPrivacy, sourcePrivacy,
            lifecyclePrivacy, evalGatePrivacy, bindingPrivacy, readinessPrivacy);
        boolean containsRawOrganization = truthyAny("containsRawOrganization", curationPrivacy, sourcePrivacy,
            lifecyclePrivacy, evalGatePrivacy, bindingPrivacy, readinessPrivacy);
        boolean containsRawConversation = truthyAny("containsRawConversation", curationPrivacy, sourcePrivacy,
            lifecyclePrivacy, evalGatePrivacy, bindingPrivacy, readinessPrivacy);
        boolean containsRawDocument = truthyAny("containsRawDocument", curationPrivacy, sourcePrivacy,
            lifecyclePrivacy, evalGatePrivacy, bindingPrivacy, readinessPrivacy);
        boolean containsRawPrompt = truthyAny("containsRawPrompt", curationPrivacy, sourcePrivacy,
            lifecyclePrivacy, evalGatePrivacy, bindingPrivacy, readinessPrivacy);
        boolean containsRawRetrievedChunk = truthyAny("containsRawRetrievedChunk", curationPrivacy, sourcePrivacy,
            lifecyclePrivacy, evalGatePrivacy, bindingPrivacy, readinessPrivacy);
        boolean containsAuthorizationHeader = truthyAny("containsAuthorizationHeader", curationPrivacy, sourcePrivacy,
            lifecyclePrivacy, evalGatePrivacy, bindingPrivacy, readinessPrivacy);
        boolean containsToken = truthyAny("containsToken", curationPrivacy, sourcePrivacy, lifecyclePrivacy,
            evalGatePrivacy, bindingPrivacy, readinessPrivacy);
        boolean containsPassword = truthyAny("containsPassword", curationPrivacy, sourcePrivacy, lifecyclePrivacy,
            evalGatePrivacy, bindingPrivacy, readinessPrivacy);
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", !containsRawPrincipal
            && !containsRawOrganization
            && !containsRawConversation
            && !containsRawDocument
            && !containsRawPrompt
            && !containsRawRetrievedChunk
            && !containsAuthorizationHeader
            && !containsToken
            && !containsPassword);
        privacy.put("traceIdsVisibleInManifest", false);
        privacy.put("containsRawPrincipal", containsRawPrincipal);
        privacy.put("containsRawOrganization", containsRawOrganization);
        privacy.put("containsRawConversation", containsRawConversation);
        privacy.put("containsRawDocument", containsRawDocument);
        privacy.put("containsRawPrompt", containsRawPrompt);
        privacy.put("containsRawRetrievedChunk", containsRawRetrievedChunk);
        privacy.put("containsAuthorizationHeader", containsAuthorizationHeader);
        privacy.put("containsToken", containsToken);
        privacy.put("containsPassword", containsPassword);
        privacy.put("containsEvalTracePayload", false);
        privacy.put("llmUsed", false);
        privacy.put("externalCalls", false);
        privacy.put("toolExecution", false);
        privacy.put("kubeManagerCalls", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> schemaField(String id,
                                                   String purpose,
                                                   boolean required,
                                                   boolean rawValueAllowed) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("id", id);
        field.put("purpose", purpose);
        field.put("required", required);
        field.put("rawValueAllowed", rawValueAllowed);
        field.put("acceptedByThisEndpoint", false);
        return Map.copyOf(field);
    }

    private static Map<String, Object> workflowStep(int order,
                                                    String id,
                                                    String summary,
                                                    String status) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("order", order);
        step.put("id", id);
        step.put("summary", summary);
        step.put("status", status);
        step.put("runtimeMutationAllowed", false);
        return Map.copyOf(step);
    }

    private static Map<String, Object> technologyMapping(String id, String mapping, String sourceUrl) {
        Map<String, Object> technology = new LinkedHashMap<>();
        technology.put("id", id);
        technology.put("mapping", mapping);
        technology.put("sourceUrl", sourceUrl);
        technology.put("runtimeBound", false);
        technology.put("requiresReviewedEvidenceBeforeRuntime", true);
        return Map.copyOf(technology);
    }

    private static Map<String, Object> disabledAction(String id, String method, String path) {
        Map<String, Object> action = new LinkedHashMap<>();
        action.put("id", id);
        action.put("method", method);
        action.put("path", path);
        action.put("enabledNow", false);
        action.put("buttonVisibleNow", false);
        return Map.copyOf(action);
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

    private static int countTrue(List<Map<String, Object>> rows, String key) {
        return (int) rows.stream()
            .filter(row -> Boolean.TRUE.equals(row.get(key)))
            .count();
    }

    private static String string(Map<String, Object> data, String key) {
        Object value = data != null ? data.get(key) : null;
        return value != null ? String.valueOf(value) : "";
    }

    private static boolean bool(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private static int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
