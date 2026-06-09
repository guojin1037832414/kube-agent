package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Contract that explains how Phase 1 adopts advanced Agent technologies.
 *
 * <p>中文说明：这个合同回答“哪些最新技术现在进主线，哪些先进入兼容矩阵”。它只发布
 * 只读治理证据，不加载新运行时、不改依赖、不调用模型、不访问 kube-manager。</p>
 */
public record AgentAdvancedTechnologyAdoptionContractResponse(
    String schemaVersion,
    Instant generatedAt,
    String contractStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean javaSpringControlPlanePreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean runtimeUpgradePerformed,
    boolean dependencyUpgradePerformed,
    boolean externalAgentRuntimeBound,
    List<Map<String, Object>> mainlineTechnologies,
    List<Map<String, Object>> compatibilityMatrix,
    List<Map<String, Object>> adoptionGates,
    List<Map<String, Object>> rejectedShortcuts,
    List<String> recommendedBuildOrder,
    Map<String, Object> standardsAlignment,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-advanced-technology-adoption-contract.v1";

    public static AgentAdvancedTechnologyAdoptionContractResponse of(Instant generatedAt) {
        return new AgentAdvancedTechnologyAdoptionContractResponse(
            SCHEMA_VERSION,
            generatedAt,
            "CONTRACT_DEFINED_NOT_BOUND",
            "Phase 1 top-tier Agent advanced-technology adoption gate",
            true,
            true,
            true,
            false,
            false,
            false,
            buildMainlineTechnologies(),
            buildCompatibilityMatrix(),
            buildAdoptionGates(),
            buildRejectedShortcuts(),
            buildRecommendedBuildOrder(),
            buildStandardsAlignment(),
            buildEndpointMap(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> buildMainlineTechnologies() {
        return List.of(
            technology("java-spring-control-plane", "MAINLINE_STABLE",
                "Java 17 plus Spring Boot 3.5.x remain the buildable control-plane baseline.",
                "identity, audit, Tool boundary, release gates, and typed read models"),
            technology("spring-ai-1-1-access-layer", "MAINLINE_STABLE",
                "Spring AI 1.1.x stays as the verified model and ToolCallback access layer.",
                "model access, local compatibility, and future provider abstraction"),
            technology("safe-tool-executor-boundary", "MAINLINE_STABLE",
                "All real Tool authority must stay behind SafeToolExecutor, HITL, audit, and protected-parameter guards.",
                "tool execution safety"),
            technology("deterministic-eval-workbench", "MAINLINE_STABLE",
                "Deterministic eval suites, trace sets, gate bundles, and review artifacts stay in the release path.",
                "quality gates and regression prevention"),
            technology("memory-rag-contract-stack", "MAINLINE_CONTRACT",
                "Memory/RAG advances through readiness, citation, source digest, durable lifecycle, and eval-gate contracts.",
                "safe learning layer before retrieval runtime"),
            technology("mcp-manifest-governance", "MAINLINE_CONTRACT",
                "MCP remains manifest/governance first; runtime tool calls need SafeToolExecutor and release evidence.",
                "interoperability without authority bypass"),
            technology("trace-audit-replay-observability", "MAINLINE_STABLE",
                "Trace, redacted audit, replay timeline, and telemetry projection remain the evidence backbone.",
                "operator visibility and recoverability"),
            technology("reviewed-eval-trace-evidence", "MAINLINE_CONTRACT",
                "Reviewed redacted trace evidence links replay, deterministic eval, human Git review, and future release-blocking gates.",
                "quality evidence before runtime expansion"),
            technology("kube-manager-http-governance", "MAINLINE_STABLE",
                "Read-side resilience and write-side release contracts govern kube-manager access.",
                "manager integration safety")
        );
    }

    private static List<Map<String, Object>> buildCompatibilityMatrix() {
        return List.of(
            technology("java-21-25-26-toolchains", "COMPATIBILITY_MATRIX",
                "Validate newer Java toolchains before changing the project baseline.",
                "virtual threads, structured concurrency, and long-term support planning"),
            technology("spring-boot-4-framework-7", "COMPATIBILITY_MATRIX",
                "Track Boot 4 and Framework 7 in a matrix before any mainline migration.",
                "future Spring baseline without breaking current verification"),
            technology("spring-ai-2-line", "COMPATIBILITY_MATRIX",
                "Track Spring AI 2.x APIs before replacing the verified 1.1.x access layer.",
                "future Tool, advisor, and model-client changes"),
            technology("responses-agents-runtime", "COMPATIBILITY_MATRIX",
                "Map Responses/Agents-style tools, tracing, handoffs, and guardrails to local contracts first.",
                "agentic workflow interoperability"),
            technology("mcp-runtime-server", "COMPATIBILITY_MATRIX",
                "Treat tools/list and tools/call as governed protocol surfaces, never as direct authority.",
                "external Agent interoperability"),
            technology("otel-genai-semconv-adapter", "COMPATIBILITY_MATRIX",
                "Keep stable atlas.agent fields and map to GenAI semantic conventions through an adapter.",
                "portable observability"),
            technology("a2a-agent-artifact-provenance", "COMPATIBILITY_MATRIX",
                "Require artifact evidence before any cross-Agent handoff becomes runtime authority.",
                "multi-Agent collaboration"),
            technology("hybrid-rag-graphrag-reranker-vector-stores", "COMPATIBILITY_MATRIX",
                "Adopt retrieval engines only after source digest, lifecycle, privacy, tenant, and eval gates exist.",
                "advanced retrieval")
        );
    }

    private static List<Map<String, Object>> buildAdoptionGates() {
        return List.of(
            gate("source-owned-contract", "Every advanced capability needs a backend-owned contract before runtime binding."),
            gate("build-test-recovery", "The mainline must stay buildable, testable, documented, and recoverable after each slice."),
            gate("identity-tenant-privacy", "Trusted identity, tenant partitioning, and redaction must be proven before exposure."),
            gate("safe-execution-boundary", "Tool, MCP, kube-manager, and handoff authority must flow through guarded evidence."),
            gate("trace-audit-replay", "Important decisions need trace, redacted audit, replay, and operator-readable state."),
            gate("eval-before-release", "New runtime influence needs deterministic eval gates and reviewed trace evidence."),
            gate("vue-read-model-first", "Frontend workbenches should render backend-owned read models before control buttons exist."),
            gate("phase2-domain-pause", "NIM, HPC, Slurm, and BCM stay paused without lowering Phase 1 quality.")
        );
    }

    private static List<Map<String, Object>> buildRejectedShortcuts() {
        return List.of(
            shortcut("blind-major-version-upgrade", "A version bump without matrix evidence does not count as top-tier adoption."),
            shortcut("prompt-only-security", "Prompt text cannot replace RBAC, HITL, audit, eval, or release evidence."),
            shortcut("direct-protocol-authority", "External Agent protocols cannot bypass local Tool and tenant boundaries."),
            shortcut("vector-first-rag", "Vector search cannot influence answers before citation, digest, lifecycle, and eval gates."),
            shortcut("runtime-switch-without-vue-evidence", "Operators need read-only evidence before any runtime enablement switch."),
            shortcut("phase2-specialist-scope-creep", "Phase 2 domain plugins must not distract from Phase 1 core quality.")
        );
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "publish-advanced-technology-adoption-contract",
            "keep-java-spring-control-plane-as-phase1-mainline",
            "add-official-version-and-protocol-watch-to-compatibility-matrix",
            "bind-memory-rag-eval-suite-before-retrieval-runtime",
            "wire-vue-top-tier-readiness-and-technology-adoption-workbench",
            "promote-reviewed-eval-and-security-gates-to-release-blocking",
            "prototype-mcp-runtime-and-agent-handoff-only-behind-safe-execution-boundary",
            "keep-nim-hpc-slurm-bcm-paused-until-phase2"
        );
    }

    private static Map<String, Object> buildStandardsAlignment() {
        Map<String, Object> standards = new LinkedHashMap<>();
        standards.put("openAiResponsesAndAgentsMappedToLocalContracts", true);
        standards.put("openAiTracingAndEvalEvidenceMappedToReviewedTraceContracts", true);
        standards.put("springAiMainlineAndUpgradeMatrixSeparated", true);
        standards.put("mcpDiscoverySeparatedFromRuntimeAuthority", true);
        standards.put("otelGenAiMappedThroughStableInternalFields", true);
        standards.put("owaspLlmSecurityThreatsMappedToGates", true);
        standards.put("javaSpringStillPreferredBackendControlPlane", true);
        standards.put("runtimeBound", false);
        return Map.copyOf(standards);
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("evalWorkbenchCapabilities", "/api/agent/observability/eval/workbench/capabilities");
        endpoints.put("reviewedEvalTraceEvidence", "/api/agent/observability/eval/reviewed-trace-evidence");
        endpoints.put("kubeManagerGovernanceOverview", "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview");
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("contractOnly", true);
        safety.put("runtimeMutationAllowed", false);
        safety.put("dependencyUpgrade", false);
        safety.put("toolExecution", false);
        safety.put("safeToolExecutorInvocation", false);
        safety.put("hitlInvocation", false);
        safety.put("kubeManagerCalls", false);
        safety.put("mcpToolCall", false);
        safety.put("agentHandoffRuntime", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableReceiptIssued", false);
        safety.put("memoryWrite", false);
        safety.put("retrievalExecuted", false);
        safety.put("nimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private static Map<String, Object> buildPrivacy() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawConversation", false);
        privacy.put("containsRawPrompt", false);
        privacy.put("containsRawDocument", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsRuntimeSecrets", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> technology(String id, String adoptionMode, String summary, String phase1Value) {
        Map<String, Object> technology = new LinkedHashMap<>();
        technology.put("id", id);
        technology.put("adoptionMode", adoptionMode);
        technology.put("summary", summary);
        technology.put("phase1Value", phase1Value);
        technology.put("runtimeBound", false);
        technology.put("requiresEvidenceBeforeRuntime", true);
        return Map.copyOf(technology);
    }

    private static Map<String, Object> gate(String id, String summary) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("id", id);
        gate.put("summary", summary);
        gate.put("required", true);
        gate.put("runtimeBound", false);
        return Map.copyOf(gate);
    }

    private static Map<String, Object> shortcut(String id, String summary) {
        Map<String, Object> shortcut = new LinkedHashMap<>();
        shortcut.put("id", id);
        shortcut.put("summary", summary);
        shortcut.put("allowed", false);
        shortcut.put("blocksTopTierClaim", true);
        return Map.copyOf(shortcut);
    }
}
