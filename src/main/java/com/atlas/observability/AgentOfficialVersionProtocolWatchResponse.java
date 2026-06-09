package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only watch list for official Agent technology and protocol versions.
 *
 * <p>中文说明：这个契约把“最新先进技术”变成可审计清单。它只记录官方来源、采纳阶段、
 * 安全门禁和一期动作，不联网抓取文档、不升级依赖、不绑定外部运行时。</p>
 */
public record AgentOfficialVersionProtocolWatchResponse(
    String schemaVersion,
    Instant generatedAt,
    String watchStatus,
    String sourceReviewDate,
    String target,
    boolean officialSourcesOnly,
    boolean phase1TopTierGoalPreserved,
    boolean javaSpringControlPlanePreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean runtimeUpgradePerformed,
    boolean dependencyUpgradePerformed,
    boolean externalCallsPerformed,
    int officialSourceCount,
    int technologyTrackCount,
    List<Map<String, Object>> officialSources,
    List<Map<String, Object>> technologyTracks,
    List<Map<String, Object>> adoptionGates,
    List<Map<String, Object>> blockedRuntimeShortcuts,
    List<String> recommendedBuildOrder,
    Map<String, Object> standardsAlignment,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-official-version-protocol-watch.v1";
    public static final String WATCH_ENDPOINT =
        "/api/agent/observability/top-tier/official-version-protocol-watch";
    private static final String SOURCE_REVIEW_DATE = "2026-06-09";

    public static AgentOfficialVersionProtocolWatchResponse of(Instant generatedAt) {
        List<Map<String, Object>> sources = buildOfficialSources();
        List<Map<String, Object>> tracks = buildTechnologyTracks();
        return new AgentOfficialVersionProtocolWatchResponse(
            SCHEMA_VERSION,
            generatedAt,
            "OFFICIAL_WATCH_DEFINED_NOT_RUNTIME_BOUND",
            SOURCE_REVIEW_DATE,
            "Phase 1 top-tier Agent official technology and protocol adoption watch",
            true,
            true,
            true,
            true,
            false,
            false,
            false,
            sources.size(),
            tracks.size(),
            sources,
            tracks,
            buildAdoptionGates(),
            buildBlockedRuntimeShortcuts(),
            buildRecommendedBuildOrder(),
            buildStandardsAlignment(),
            buildEndpointMap(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> buildOfficialSources() {
        return List.of(
            source("spring-ai-reference",
                "Spring AI reference documentation",
                "https://docs.spring.io/spring-ai/reference/",
                "OFFICIAL_REFERENCE",
                "Spring AI reference line observed; verify Maven artifacts and migration notes before any dependency upgrade.",
                "EVIDENCE_FIRST_MAINLINE_ADAPTER"),
            source("openai-responses-api",
                "OpenAI Responses API guide",
                "https://platform.openai.com/docs/guides/migrate-to-responses",
                "OFFICIAL_API_GUIDE",
                "Responses-style model, tool, and tracing semantics are tracked as an interop target, not direct runtime authority.",
                "CONTRACT_MATRIX"),
            source("openai-agents-sdk",
                "OpenAI Agents SDK guide",
                "https://platform.openai.com/docs/guides/agents-sdk/",
                "OFFICIAL_SDK_GUIDE",
                "Agent, runner, tool, handoff, guardrail, session, and tracing concepts map to local contracts before runtime binding.",
                "CONTRACT_MATRIX"),
            source("mcp-2025-11-25",
                "Model Context Protocol 2025-11-25 specification",
                "https://modelcontextprotocol.io/specification/2025-11-25",
                "OFFICIAL_PROTOCOL_SPEC",
                "Latest MCP snapshot is tracked for capabilities, lifecycle, authorization, tools, resources, prompts, and Tasks semantics.",
                "MANIFEST_FIRST_RUNTIME_LATER"),
            source("nsa-mcp-security-2026-06",
                "NSA MCP Security Cybersecurity Information",
                "https://media.defense.gov/2026/Jun/02/2003943289/-1/-1/0/CSI_MCP_SECURITY.PDF",
                "OFFICIAL_SECURITY_GUIDANCE",
                "Latest MCP security guidance is tracked for threat controls before any runtime MCP tools/call authority.",
                "SECURITY_GATE_BASELINE"),
            source("a2a-latest-spec",
                "Agent2Agent latest specification",
                "https://a2a-protocol.org/latest/specification/",
                "OFFICIAL_PROTOCOL_SPEC",
                "Agent Card, Task, message, artifact, transport, streaming, and security concepts are tracked for future provenance.",
                "PROVENANCE_BEFORE_HANDOFF"),
            source("otel-genai-semconv",
                "OpenTelemetry GenAI semantic conventions",
                "https://opentelemetry.io/docs/specs/semconv/gen-ai/",
                "OFFICIAL_SEMCONV",
                "GenAI conventions are tracked through an adapter because the official status is still development-level.",
                "EXPERIMENTAL_ADAPTER_ONLY"),
            source("owasp-llm-top-10-2025",
                "OWASP Top 10 for LLM Applications",
                "https://genai.owasp.org/llm-top-10/",
                "OFFICIAL_SECURITY_BASELINE",
                "LLM threat categories map to identity, prompt-injection, supply-chain, privacy, tool, and eval gates.",
                "SECURITY_GATE_BASELINE")
        );
    }

    private static List<Map<String, Object>> buildTechnologyTracks() {
        return List.of(
            track("java-spring-governed-control-plane", "KEEP_MAINLINE",
                "Java/Spring remains the typed backend control plane for identity, RBAC, audit, eval, release gates, and recovery.",
                List.of("do not replace control plane with prompt-only runtime", "keep build/test/recovery green"),
                List.of("typed contracts", "Spring Security", "Micrometer/Actuator", "deterministic tests")),
            track("spring-ai-memory-rag-mcp", "EVIDENCE_FIRST",
                "Spring AI capabilities are adopted through contracts for memory, RAG, VectorStore, MCP, advisors, and model access.",
                List.of("source digest", "citation", "durable lifecycle", "eval-suite binding", "reviewed redacted traces"),
                List.of("AgentMemoryRagReadinessService", "AgentMemoryRagReviewedTraceEvidenceManifestService")),
            track("openai-responses-agents-interop", "CONTRACT_MATRIX",
                "Responses/Agents concepts are mapped to local tool authority, handoff provenance, tracing, guardrails, and eval evidence.",
                List.of("SafeToolExecutor binding", "HITL release evidence", "redacted audit replay", "deterministic eval gates"),
                List.of("advanced-technology-adoption-contract", "eval workbench", "release-blocking-gate-contract")),
            track("mcp-runtime-call-plane", "MANIFEST_FIRST",
                "MCP runtime remains discovery/governance-first; tools/call requires identity, consent, HITL, audit, eval, and SafeToolExecutor.",
                List.of("mcp manifest", "runtime policy", "rate limits", "tenant-safe tool metadata", "release gate"),
                List.of("mcp-governance-overview", "safe manifest")),
            track("a2a-handoff-provenance", "PROVENANCE_BEFORE_HANDOFF",
                "A2A-style multi-Agent exchange is tracked as future Agent Card, task, message, artifact, and security provenance.",
                List.of("local authority proof", "artifact digest", "trace/audit bridge", "handoff eval coverage"),
                List.of("top-tier readiness", "eval trace-set catalog")),
            track("otel-genai-observability-adapter", "EXPERIMENTAL_ADAPTER_ONLY",
                "Stable atlas.agent telemetry remains primary; OTel GenAI fields stay adapter-scoped until the semconv status stabilizes.",
                List.of("stable internal attributes", "redaction", "cardinality bounds", "no raw prompt/documents"),
                List.of("AgentAuditObservationPublisher", "trace-audit-replay")),
            track("owasp-llm-risk-controls", "SECURITY_GATE_BASELINE",
                "OWASP LLM risks become release-gate questions for prompt injection, sensitive data, supply chain, tool misuse, and overreliance.",
                List.of("threat mapping", "red-team trace fixtures", "privacy gates", "tool authority checks"),
                List.of("deterministic eval suite", "reviewed trace evidence")),
            track("advanced-rag-graphrag-rerankers-vector-stores", "RUNTIME_BLOCKED_UNTIL_EVIDENCE",
                "GraphRAG, rerankers, and vector stores remain Phase 1 targets, but retrieval cannot affect prompts before evidence gates pass.",
                List.of("reviewed Memory/RAG trace sets", "source custody", "tenant isolation", "citation fidelity", "lifecycle proof"),
                List.of("memory-rag-eval-suite-binding", "reviewed trace-evidence manifest"))
        );
    }

    private static List<Map<String, Object>> buildAdoptionGates() {
        return List.of(
            gate("official-source-review", "A human-reviewed official source URL and review date must exist before changing the watch."),
            gate("compatibility-matrix-before-upgrade", "Major runtime or dependency changes need matrix evidence before entering mainline."),
            gate("contract-before-runtime", "Every advanced technology must first expose a backend-owned typed contract."),
            gate("safe-authority-boundary", "Tool, MCP, A2A, kube-manager, and retrieval authority cannot bypass SafeToolExecutor and release gates."),
            gate("trace-audit-replay-before-influence", "New prompt/tool influence needs trace, redacted audit, replay, and eval evidence first."),
            gate("vue-read-model-before-controls", "Vue may render evidence before any runtime enable button exists."),
            gate("phase2-domain-pause", "NIM, HPC, Slurm, and BCM remain Phase 2 without lowering the Phase 1 quality bar.")
        );
    }

    private static List<Map<String, Object>> buildBlockedRuntimeShortcuts() {
        return List.of(
            shortcut("blind-latest-version-bump", "Latest version labels do not replace migration tests, security review, and rollback evidence."),
            shortcut("direct-mcp-tools-call", "MCP tools/call cannot become a second Tool execution boundary."),
            shortcut("direct-a2a-handoff-authority", "A2A handoff cannot grant runtime authority without local provenance and eval evidence."),
            shortcut("direct-retrieval-prompt-influence", "Vector, reranker, or GraphRAG output cannot influence prompts before Memory/RAG gates pass."),
            shortcut("otel-genai-as-primary-schema", "Development-status GenAI semconv fields cannot replace stable internal telemetry."),
            shortcut("external-agent-runtime-as-control-plane", "External Agent runtimes cannot replace Java/Spring identity, audit, release, and recovery contracts.")
        );
    }

    private static List<String> buildRecommendedBuildOrder() {
        return List.of(
            "publish-official-version-protocol-watch",
            "bind-vue-official-watch-dashboard",
            "refresh-official-sources-through-git-review",
            "turn-watch-items-into-compatibility-matrix-tests-before-upgrades",
            "populate-reviewed-redacted-eval-and-memory-rag-traces",
            "only-then-prototype-mcp-tools-call-a2a-handoff-or-retrieval-runtime",
            "keep-nim-hpc-slurm-bcm-paused-until-phase2"
        );
    }

    private static Map<String, Object> buildStandardsAlignment() {
        Map<String, Object> standards = new LinkedHashMap<>();
        standards.put("springAiOfficialReferenceTracked", true);
        standards.put("openAiResponsesApiTracked", true);
        standards.put("openAiAgentsSdkTracked", true);
        standards.put("mcp20251125SpecTracked", true);
        standards.put("nsaMcpSecurityGuidanceTracked", true);
        standards.put("a2aLatestSpecTracked", true);
        standards.put("otelGenAiDevelopmentStatusRespected", true);
        standards.put("owaspLlmTop10MappedToSecurityGates", true);
        standards.put("javaSpringControlPlanePreserved", true);
        standards.put("runtimeBound", false);
        return Map.copyOf(standards);
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("officialVersionProtocolWatch", WATCH_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchDashboard",
            AgentOfficialVersionProtocolWatchDashboardResponse.DASHBOARD_ENDPOINT);
        endpoints.put("officialVersionProtocolWatchVueBindingSpec",
            AgentOfficialVersionProtocolWatchVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("advancedTechnologyAdoptionContract",
            "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        endpoints.put("advancedTechnologyCompatibilityMatrix",
            AgentAdvancedTechnologyCompatibilityMatrixResponse.MATRIX_ENDPOINT);
        endpoints.put("advancedTechnologyCompatibilityMatrixVueBindingSpec",
            AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.BINDING_SPEC_ENDPOINT);
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("vueReadinessControlPlane", "/api/agent/observability/top-tier/vue-readiness-control-plane");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("memoryRagReviewedTraceEvidenceManifest",
            AgentMemoryRagReviewedTraceEvidenceManifestResponse.MANIFEST_ENDPOINT);
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("watchOnly", true);
        safety.put("officialSourcesResolvedAtBuildReviewTime", true);
        safety.put("runtimeMutationAllowed", false);
        safety.put("runtimeUpgradePerformed", false);
        safety.put("dependencyUpgradePerformed", false);
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
        privacy.put("containsRuntimeSecrets", false);
        privacy.put("containsInternalEndpointSecrets", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> source(String id,
                                              String title,
                                              String officialUrl,
                                              String sourceType,
                                              String currentFinding,
                                              String adoptionMode) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", id);
        source.put("title", title);
        source.put("officialUrl", officialUrl);
        source.put("sourceType", sourceType);
        source.put("currentFinding", currentFinding);
        source.put("sourceReviewDate", SOURCE_REVIEW_DATE);
        source.put("adoptionMode", adoptionMode);
        source.put("runtimeBound", false);
        return Map.copyOf(source);
    }

    private static Map<String, Object> track(String id,
                                             String adoptionDecision,
                                             String phase1Interpretation,
                                             List<String> beforeRuntimeEvidence,
                                             List<String> localAnchors) {
        Map<String, Object> track = new LinkedHashMap<>();
        track.put("id", id);
        track.put("adoptionDecision", adoptionDecision);
        track.put("phase1Interpretation", phase1Interpretation);
        track.put("beforeRuntimeEvidence", List.copyOf(beforeRuntimeEvidence));
        track.put("localAnchors", List.copyOf(localAnchors));
        track.put("phase1Scope", true);
        track.put("runtimeBound", false);
        track.put("requiresGitReview", true);
        return Map.copyOf(track);
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
