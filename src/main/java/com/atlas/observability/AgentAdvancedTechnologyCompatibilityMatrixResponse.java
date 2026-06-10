package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compatibility matrix for adopting advanced Agent technologies safely.
 *
 * <p>中文说明：本响应把“最新技术升级”拆成来源、候选版本、证据门禁、测试矩阵和阻断捷径。
 * 它只发布只读升级治理规格，不修改依赖、不运行兼容性测试、不打开任何运行时能力。</p>
 */
public record AgentAdvancedTechnologyCompatibilityMatrixResponse(
    String schemaVersion,
    Instant generatedAt,
    String matrixStatus,
    String sourceReviewDate,
    String target,
    boolean sourceWatchEmbedded,
    boolean phase1TopTierGoalPreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean runtimeUpgradeAllowedNow,
    boolean dependencyUpgradeAllowedNow,
    boolean runtimeControlAllowed,
    int sourceBaselineCount,
    int matrixItemCount,
    int migrationGateCount,
    int blockedShortcutCount,
    int testLaneCount,
    List<Map<String, Object>> sourceBaselines,
    List<Map<String, Object>> matrixItems,
    List<Map<String, Object>> migrationGates,
    List<Map<String, Object>> blockedUpgradeShortcuts,
    List<Map<String, Object>> testLanes,
    List<String> implementationChecklist,
    AgentOfficialVersionProtocolWatchResponse sourceWatch,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-advanced-technology-compatibility-matrix.v1";
    public static final String MATRIX_ENDPOINT =
        "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix";
    private static final String SOURCE_REVIEW_DATE = "2026-06-10";

    public static AgentAdvancedTechnologyCompatibilityMatrixResponse of(
        Instant generatedAt,
        AgentOfficialVersionProtocolWatchResponse sourceWatch
    ) {
        List<Map<String, Object>> sourceBaselines = buildSourceBaselines(sourceWatch);
        List<Map<String, Object>> matrixItems = buildMatrixItems();
        List<Map<String, Object>> migrationGates = buildMigrationGates();
        List<Map<String, Object>> blockedShortcuts = buildBlockedUpgradeShortcuts();
        List<Map<String, Object>> testLanes = buildTestLanes();
        return new AgentAdvancedTechnologyCompatibilityMatrixResponse(
            SCHEMA_VERSION,
            generatedAt,
            matrixStatus(sourceWatch),
            SOURCE_REVIEW_DATE,
            "Phase 1 top-tier Agent advanced technology compatibility matrix",
            sourceWatch != null,
            true,
            true,
            false,
            false,
            false,
            sourceBaselines.size(),
            matrixItems.size(),
            migrationGates.size(),
            blockedShortcuts.size(),
            testLanes.size(),
            sourceBaselines,
            matrixItems,
            migrationGates,
            blockedShortcuts,
            testLanes,
            buildImplementationChecklist(),
            sourceWatch,
            buildEndpointMap(),
            buildSafety(sourceWatch),
            buildPrivacy(sourceWatch)
        );
    }

    private static String matrixStatus(AgentOfficialVersionProtocolWatchResponse sourceWatch) {
        if (sourceWatch == null) {
            return "MATRIX_SOURCE_WATCH_MISSING";
        }
        if (sourceWatch.runtimeUpgradePerformed()
            || sourceWatch.dependencyUpgradePerformed()
            || Boolean.TRUE.equals(sourceWatch.safety().get("mcpToolsCall"))) {
            return "UNEXPECTED_RUNTIME_CHANGE_IN_SOURCE_WATCH";
        }
        return "MATRIX_DEFINED_NOT_EXECUTED";
    }

    private static List<Map<String, Object>> buildSourceBaselines(
        AgentOfficialVersionProtocolWatchResponse sourceWatch
    ) {
        if (sourceWatch == null) {
            return List.of();
        }
        return sourceWatch.officialSources().stream()
            .map(source -> sourceBaseline(
                String.valueOf(source.get("id")),
                String.valueOf(source.get("sourceType")),
                String.valueOf(source.get("officialUrl")),
                String.valueOf(source.get("adoptionMode"))
            ))
            .toList();
    }

    private static Map<String, Object> sourceBaseline(String sourceId,
                                                      String sourceType,
                                                      String officialUrl,
                                                      String adoptionMode) {
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("sourceId", sourceId);
        baseline.put("sourceType", sourceType);
        baseline.put("officialUrl", officialUrl);
        baseline.put("reviewDate", SOURCE_REVIEW_DATE);
        baseline.put("adoptionMode", adoptionMode);
        baseline.put("runtimeBound", false);
        return Map.copyOf(baseline);
    }

    private static List<Map<String, Object>> buildMatrixItems() {
        return List.of(
            item("java-runtime-toolchains", "Java 17 mainline", "Java 21 and Java 25 compatibility lanes",
                "COMPATIBILITY_REQUIRED",
                List.of("mvn-validate-on-candidate-jdk", "trace-context-threadlocal-regression",
                    "safe-tool-executor-audit-regression"),
                "Keep Java 17 as recoverable baseline until context propagation and audit prewrite pass."),
            item("spring-boot-framework", "Spring Boot 3.5.x mainline", "Spring Boot 4.0.x / Framework 7 lane",
                "COMPATIBILITY_REQUIRED",
                List.of("security-filter-chain-regression", "webmvc-controller-contracts",
                    "actuator-observability-regression"),
                "Boot 4 is tracked as a matrix item, not a blind mainline bump."),
            item("spring-ai-access-layer", "Spring AI 1.1.7 stable", "Spring AI 2.0.0-RC2 preview lane",
                "COMPATIBILITY_REQUIRED",
                List.of("tool-callback-api-regression", "mcp-starter-compatibility-review",
                    "chat-memory-rag-advisor-contract-review"),
                "Spring AI 2 cannot replace the current access layer without Tool/RAG/MCP evidence."),
            item("openai-responses-agents", "Responses/Agents concepts mapped to contracts",
                "Agents SDK orchestration, tools, handoffs, guardrails, tracing, evals",
                "CONTRACT_FIRST",
                List.of("safe-tool-executor-mapping", "handoff-provenance-contract",
                    "redacted-trace-eval-coverage"),
                "OpenAI runtime concepts are adopted through local authority contracts first."),
            item("mcp-runtime-call-plane", "MCP manifest/governance only", "MCP tools/list and tools/call lane",
                "RELEASE_GATED",
                List.of("explicit-consent-ui", "safe-tool-executor-binding", "tenant-tool-policy",
                    "durable-audit-prewrite", "eval-release-gate"),
                "MCP tools/call must never become a second execution boundary."),
            item("a2a-multi-agent-provenance", "A2A tracked as provenance spec", "Agent Card, task, artifact, streaming lane",
                "PROVENANCE_REQUIRED",
                List.of("agent-card-sanitization", "artifact-digest-chain", "handoff-eval-fixtures"),
                "Multi-Agent handoff needs local authority and artifact evidence before runtime."),
            item("otel-genai-mcp-semconv", "Stable atlas.agent telemetry", "OTel GenAI/MCP semantic adapter lane",
                "ADAPTER_REQUIRED",
                List.of("redaction-cardinality-check", "internal-schema-backward-compatibility",
                    "semconv-status-review"),
                "Development semantic conventions stay adapter-scoped until safe."),
            item("memory-rag-graphrag-reranker-vectorstore", "Memory/RAG contracts and trace-set catalog",
                "GraphRAG, reranker, and vector store runtime lane",
                "EVIDENCE_BLOCKED",
                List.of("citation-fidelity-traces", "tenant-privacy-traces", "source-digest-lifecycle",
                    "memory-rag-release-gate"),
                "Retrieval cannot influence prompts before reviewed evidence exists."),
            item("kubernetes-manager-control-plane", "kube-manager read/governance integration",
                "state-changing write lane through release gate",
                "WRITE_AUTHORITY_CLOSED",
                List.of("idempotency-contract", "operation-safety-contract", "release-gate-contract",
                    "readback-executor-contract"),
                "Manager writes stay closed until release evidence and readback proof exist."),
            item("supply-chain-ci-quality", "manual verified Maven/test path",
                "SBOM, dependency audit, CI blocking, compatibility matrix automation",
                "QUALITY_GATE_REQUIRED",
                List.of("sbom-artifact", "dependency-diff-review", "matrix-green-evidence",
                    "post-push-recovery-checkpoint"),
                "CI blocking is enabled only after reviewed trace/eval evidence is real.")
        );
    }

    private static Map<String, Object> item(String id,
                                            String currentBaseline,
                                            String candidateTarget,
                                            String readiness,
                                            List<String> requiredEvidence,
                                            String adoptionRule) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("currentBaseline", currentBaseline);
        item.put("candidateTarget", candidateTarget);
        item.put("readiness", readiness);
        item.put("requiredEvidence", List.copyOf(requiredEvidence));
        item.put("adoptionRule", adoptionRule);
        item.put("mainlineAllowedNow", false);
        item.put("runtimeControlAllowed", false);
        return Map.copyOf(item);
    }

    private static List<Map<String, Object>> buildMigrationGates() {
        return List.of(
            gate("official-source-rechecked", "Official source URL and review date must be updated in Git."),
            gate("compatibility-branch-created", "Major changes must run on a compatibility branch before mainline."),
            gate("build-and-focused-tests-green", "Validate and focused security/observability tests must pass."),
            gate("security-boundary-regression-green", "RBAC, admin-only endpoints, SafeToolExecutor, HITL, and audit gates must stay closed."),
            gate("privacy-redaction-regression-green", "No raw principal, prompt, document, token, password, or endpoint secret may appear."),
            gate("vue-readonly-evidence-updated", "Vue read models must expose evidence before any enable button exists."),
            gate("recovery-memory-updated", "Workspace-local recovery memory and SHA manifests must be updated."),
            gate("git-reviewed-release-decision", "Runtime authority changes need human/Git reviewed release evidence.")
        );
    }

    private static Map<String, Object> gate(String id, String summary) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("id", id);
        gate.put("summary", summary);
        gate.put("required", true);
        gate.put("runtimeBound", false);
        return Map.copyOf(gate);
    }

    private static List<Map<String, Object>> buildBlockedUpgradeShortcuts() {
        return List.of(
            shortcut("upgrade-pom-from-readiness-page", "Dependency changes must not be triggered from a dashboard."),
            shortcut("treat-rc-preview-as-mainline", "Preview framework lines need matrix proof before mainline adoption."),
            shortcut("trust-mcp-tool-annotations", "MCP tool annotations are not trusted security evidence by themselves."),
            shortcut("delegate-authority-to-external-agent", "External Agent runtimes cannot own local RBAC or Tool authority."),
            shortcut("enable-retrieval-before-reviewed-traces", "RAG/GraphRAG/reranker outputs need reviewed eval traces first."),
            shortcut("use-otel-experimental-fields-as-contract", "Experimental semantic fields cannot replace stable atlas.agent fields."),
            shortcut("enable-ci-blocking-with-empty-fixtures", "CI cannot block releases on empty or unreviewed trace sets.")
        );
    }

    private static Map<String, Object> shortcut(String id, String summary) {
        Map<String, Object> shortcut = new LinkedHashMap<>();
        shortcut.put("id", id);
        shortcut.put("summary", summary);
        shortcut.put("allowed", false);
        shortcut.put("blocksTopTierClaim", true);
        return Map.copyOf(shortcut);
    }

    private static List<Map<String, Object>> buildTestLanes() {
        return List.of(
            testLane("current-mainline", "Java 17 + Spring Boot 3.5.x + Spring AI 1.1.x", "REQUIRED_GREEN"),
            testLane("java-21-candidate", "Java 21 compatibility branch", "PLANNED"),
            testLane("java-25-candidate", "Java 25 compatibility branch", "PLANNED"),
            testLane("boot-4-candidate", "Spring Boot 4 / Framework 7 compatibility branch", "PLANNED"),
            testLane("spring-ai-2-candidate", "Spring AI 2 preview API compatibility branch", "PLANNED"),
            testLane("mcp-runtime-prototype", "MCP tools/list and tools/call release-gated prototype", "BLOCKED_BY_RELEASE_EVIDENCE"),
            testLane("a2a-provenance-prototype", "A2A Agent Card/task/artifact provenance prototype", "BLOCKED_BY_PROVENANCE_EVIDENCE"),
            testLane("memory-rag-runtime-prototype", "Vector/RAG/GraphRAG/reranker runtime prototype", "BLOCKED_BY_REVIEWED_TRACES")
        );
    }

    private static Map<String, Object> testLane(String id, String target, String status) {
        Map<String, Object> lane = new LinkedHashMap<>();
        lane.put("id", id);
        lane.put("target", target);
        lane.put("status", status);
        lane.put("runtimeAuthorityOpened", false);
        lane.put("requiresSeparateReviewedSlice", true);
        return Map.copyOf(lane);
    }

    private static List<String> buildImplementationChecklist() {
        return List.of(
            "keep-current-mainline-green-before-any-upgrade",
            "create-compatibility-branch-per-major-technology",
            "capture-official-source-review-date-and-url",
            "run-validate-and-focused-security-observability-tests",
            "publish-vue-readonly-matrix-before-controls",
            "require-reviewed-trace-eval-audit-evidence-before-runtime",
            "update-workspace-local-recovery-memory-and-sha",
            "commit-and-push-each-reviewed-slice"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("advancedTechnologyCompatibilityMatrix", MATRIX_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixVueBindingSpec",
            AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
            AgentAdvancedTechnologyCompatibilityMatrixEvidenceReadinessResponse.EVIDENCE_READINESS_ENDPOINT);
        endpoints.put("backendTechnologyModernizationDecision",
            AgentBackendTechnologyModernizationDecisionResponse.DECISION_ENDPOINT);
        endpoints.put("topTierTechnologyIntroductionPlaybook",
            AgentTopTierTechnologyIntroductionPlaybookResponse.PLAYBOOK_ENDPOINT);
        endpoints.put("advancedTechnologyAdoptionContract",
            "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        endpoints.put("officialVersionProtocolWatch", AgentOfficialVersionProtocolWatchResponse.WATCH_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchDashboard",
            AgentOfficialVersionProtocolWatchDashboardResponse.DASHBOARD_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchVueBindingSpec",
            AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("topTierVueWorkbenchImplementationPackage",
            AgentTopTierVueWorkbenchImplementationPackageResponse.PACKAGE_ENDPOINT);
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("memoryRagReviewedTraceEvidenceManifest",
            AgentMemoryRagReviewedTraceEvidenceManifestResponse.MANIFEST_ENDPOINT);
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety(AgentOfficialVersionProtocolWatchResponse sourceWatch) {
        Map<String, Object> sourceSafety = sourceWatch != null ? sourceWatch.safety() : Map.of();
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("matrixOnly", true);
        safety.put("sourceWatchReadOnly", Boolean.TRUE.equals(sourceSafety.get("readOnly")));
        safety.put("runtimeMutationAllowed", false);
        safety.put("runtimeUpgradeAllowedNow", false);
        safety.put("dependencyUpgradeAllowedNow", false);
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
        safety.put("rerankerCalls", false);
        safety.put("ciBlockingChanged", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy(AgentOfficialVersionProtocolWatchResponse sourceWatch) {
        Map<String, Object> sourcePrivacy = sourceWatch != null ? sourceWatch.privacy() : Map.of();
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", Boolean.TRUE.equals(sourcePrivacy.get("containsRawPrincipal")));
        privacy.put("containsRawOrganization", Boolean.TRUE.equals(sourcePrivacy.get("containsRawOrganization")));
        privacy.put("containsRawPrompt", Boolean.TRUE.equals(sourcePrivacy.get("containsRawPrompt")));
        privacy.put("containsRawDocument", Boolean.TRUE.equals(sourcePrivacy.get("containsRawDocument")));
        privacy.put("containsAuthorizationHeader", Boolean.TRUE.equals(sourcePrivacy.get("containsAuthorizationHeader")));
        privacy.put("containsToken", Boolean.TRUE.equals(sourcePrivacy.get("containsToken")));
        privacy.put("containsPassword", Boolean.TRUE.equals(sourcePrivacy.get("containsPassword")));
        privacy.put("containsRuntimeSecrets", Boolean.TRUE.equals(sourcePrivacy.get("containsRuntimeSecrets")));
        return Map.copyOf(privacy);
    }
}
