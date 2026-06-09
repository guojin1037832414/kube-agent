package com.atlas.observability;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 1 execution roadmap for the top-tier kube-manager Agent Core.
 *
 * <p>中文说明：路线表必须成为后端契约，而不是聊天记录里的口头计划。它只描述
 * 下一步顺序和门禁，不启用任何运行时能力。</p>
 */
public record AgentPhase1ExecutionRoadmapResponse(
    String schemaVersion,
    Instant generatedAt,
    String roadmapStatus,
    String target,
    boolean phase1TopTierGoalPreserved,
    boolean phase2NimHpcSlurmBcmPaused,
    boolean roadmapOnly,
    boolean runtimeMutationAllowed,
    int stepCount,
    List<Map<String, Object>> executionSteps,
    List<Map<String, Object>> dependencyGates,
    List<Map<String, Object>> vueWorkbenchTargets,
    List<String> doNotStartYet,
    Map<String, Object> endpointMap,
    Map<String, Object> safety,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-phase1-execution-roadmap.v1";

    public static AgentPhase1ExecutionRoadmapResponse of(Instant generatedAt) {
        List<Map<String, Object>> steps = buildExecutionSteps();
        return new AgentPhase1ExecutionRoadmapResponse(
            SCHEMA_VERSION,
            generatedAt,
            "PHASE_1_TOP_TIER_ROADMAP_ACTIVE",
            "top-tier kube-manager Agent Core Phase 1 execution order",
            true,
            true,
            true,
            false,
            steps.size(),
            steps,
            buildDependencyGates(),
            buildVueWorkbenchTargets(),
            buildDoNotStartYet(),
            buildEndpointMap(),
            buildSafety(),
            buildPrivacy()
        );
    }

    private static List<Map<String, Object>> buildExecutionSteps() {
        return List.of(
            step(1, "vue-readiness-control-plane",
                "Wire Vue to render top-tier readiness, technology adoption, kube-manager governance, MCP governance, and Memory/RAG contracts.",
                "PARTIAL_BACKEND_READY",
                List.of("backend-read-models-exist", "admin-auth-required"),
                List.of("top-tier-readiness-overview", "advanced-technology-adoption-contract", "kube-manager-governance-workbench", "memory-rag-readiness")),
            step(2, "reviewed-eval-trace-evidence",
                "Curate reviewed redacted traces so eval gates stop being schema-only evidence.",
                "NOT_STARTED",
                List.of("replay-timeline-exists", "trace-set-catalog-exists", "catalog-patch-review-exists"),
                List.of("eval-workbench-overview", "trace-set-detail", "catalog-patch-review")),
            step(3, "release-blocking-eval-gates",
                "Promote deterministic eval gate bundles from advisory evidence to reviewed release gates.",
                "BLOCKED_BY_TRACE_EVIDENCE",
                List.of("reviewed-redacted-traces", "gate-bundle-summary", "human-git-review"),
                List.of("eval-gate-bundle-summary", "promotion-workflow")),
            step(4, "memory-rag-eval-suite-binding",
                "Implement Memory/RAG eval suites for citation, source digest, privacy, tenant isolation, lifecycle, and prompt-injection gates.",
                "CONTRACT_DEFINED_NOT_BOUND",
                List.of("memory-rag-eval-gate-contract", "source-evidence-digest-contract", "durable-lifecycle-contract"),
                List.of("memory-rag-eval-gate-contract", "memory-rag-readiness")),
            step(5, "durable-memory-store-binding",
                "Bind durable memory only after lifecycle, source digest, retention, delete/export/recovery, and eval gates are ready.",
                "BLOCKED_BY_LIFECYCLE_AND_EVAL",
                List.of("tenant-partition-digest", "retention-policy", "delete-export-recovery-proof", "eval-gate-pass"),
                List.of("durable-memory-lifecycle-contract", "source-evidence-digest-contract")),
            step(6, "retrieval-runtime-binding",
                "Introduce retrieval after citation/source custody, lifecycle, privacy, tenant, and eval gates are enforceable.",
                "BLOCKED_BY_MEMORY_RAG_GATES",
                List.of("citation-contract", "source-digest-contract", "durable-memory-lifecycle", "memory-rag-eval-suite"),
                List.of("citation-source-contract", "source-evidence-digest-contract", "memory-rag-eval-gate-contract")),
            step(7, "mcp-runtime-safe-call-plane",
                "Prototype MCP tools/list and tools/call only behind identity, consent, HITL, audit, eval, rate limits, and SafeToolExecutor.",
                "COMPATIBILITY_MATRIX",
                List.of("mcp-governance-overview", "safe-tool-executor-binding", "release-gate-evidence"),
                List.of("mcp-governance-overview", "mcp-manifest")),
            step(8, "agent-handoff-and-a2a-provenance",
                "Add multi-Agent handoff/A2A artifact provenance after local authority and eval evidence are stable.",
                "COMPATIBILITY_MATRIX",
                List.of("advanced-technology-adoption-contract", "trace-audit-replay", "eval-release-gates"),
                List.of("advanced-technology-adoption-contract", "top-tier-readiness-overview"))
        );
    }

    private static List<Map<String, Object>> buildDependencyGates() {
        return List.of(
            gate("admin-auth-required", "Every roadmap endpoint remains admin-only and read-only."),
            gate("safe-tool-executor-only", "No Tool or external protocol may bypass SafeToolExecutor when execution is later enabled."),
            gate("trace-audit-replay-required", "New runtime influence needs trace, redacted audit, and replay evidence."),
            gate("eval-before-runtime", "Prompt influence, retrieval, MCP calls, and handoffs need deterministic eval coverage first."),
            gate("vue-read-model-before-control", "Vue renders backend-owned state before any runtime control button exists."),
            gate("kube-manager-write-authority-closed", "Write retry and state-changing calls stay closed until release evidence is bound."),
            gate("phase2-domain-pause", "NIM, HPC, Slurm, and BCM remain Phase 2 and cannot consume Phase 1 roadmap slots.")
        );
    }

    private static List<Map<String, Object>> buildVueWorkbenchTargets() {
        return List.of(
            vueTarget("top-tier-overview", "/api/agent/observability/top-tier/readiness-overview"),
            vueTarget("technology-adoption", "/api/agent/observability/top-tier/advanced-technology-adoption-contract"),
            vueTarget("phase1-roadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap"),
            vueTarget("kube-manager-governance", "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview"),
            vueTarget("memory-rag-readiness", "/api/agent/observability/memory-rag/readiness"),
            vueTarget("eval-workbench", "/api/agent/observability/eval/workbench/overview"),
            vueTarget("mcp-governance", "/api/agent/mcp/governance/overview")
        );
    }

    private static List<String> buildDoNotStartYet() {
        return List.of(
            "nim-runtime-reopen",
            "hpc-slurm-bcm-domain-plugins",
            "kube-manager-state-changing-write-runtime",
            "automatic-write-retry-enable-switch",
            "mcp-tools-call-without-safe-tool-executor",
            "retrieval-prompt-injection-before-eval-gates",
            "blind-spring-boot-4-or-spring-ai-2-mainline-upgrade"
        );
    }

    private static Map<String, Object> buildEndpointMap() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("phase1ExecutionRoadmap", "/api/agent/observability/top-tier/phase1-execution-roadmap");
        endpoints.put("topTierReadinessOverview", "/api/agent/observability/top-tier/readiness-overview");
        endpoints.put("advancedTechnologyAdoptionContract", "/api/agent/observability/top-tier/advanced-technology-adoption-contract");
        endpoints.put("kubeManagerGovernanceOverview", "/api/agent/observability/kube-manager/http-outlet/governance-workbench/overview");
        endpoints.put("evalWorkbenchOverview", "/api/agent/observability/eval/workbench/overview");
        endpoints.put("memoryRagReadiness", "/api/agent/observability/memory-rag/readiness");
        endpoints.put("memoryRagEvalGateContract", "/api/agent/observability/memory-rag/eval-gate-contract");
        endpoints.put("mcpGovernanceOverview", "/api/agent/mcp/governance/overview");
        return Map.copyOf(endpoints);
    }

    private static Map<String, Object> buildSafety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("roadmapOnly", true);
        safety.put("runtimeMutationAllowed", false);
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
        safety.put("dependencyUpgrade", false);
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
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsPassword", false);
        return Map.copyOf(privacy);
    }

    private static Map<String, Object> step(int order,
                                            String id,
                                            String summary,
                                            String status,
                                            List<String> requiredEvidence,
                                            List<String> vueTargets) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("order", order);
        step.put("id", id);
        step.put("summary", summary);
        step.put("status", status);
        step.put("requiredEvidence", List.copyOf(requiredEvidence));
        step.put("vueTargets", List.copyOf(vueTargets));
        step.put("runtimeMutationAllowed", false);
        step.put("phase2Scope", false);
        return Map.copyOf(step);
    }

    private static Map<String, Object> gate(String id, String summary) {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("id", id);
        gate.put("summary", summary);
        gate.put("required", true);
        gate.put("runtimeBound", false);
        return Map.copyOf(gate);
    }

    private static Map<String, Object> vueTarget(String id, String endpoint) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("id", id);
        target.put("endpoint", endpoint);
        target.put("readOnly", true);
        target.put("runtimeControlAllowed", false);
        return Map.copyOf(target);
    }
}
