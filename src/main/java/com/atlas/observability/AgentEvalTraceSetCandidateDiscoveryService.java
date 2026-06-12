package com.atlas.observability;

import com.atlas.audit.AgentAuditQueryEvent;
import com.atlas.audit.AgentAuditQueryResponse;
import com.atlas.audit.AgentAuditQueryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Discovers redacted trace candidates that operators can send to curation review.
 *
 * <p>The service consumes only {@link AgentAuditQueryEvent}; it does not read raw
 * audit records, call kube-manager, execute Tools, or mutate trace-set catalog files.</p>
 *
 * <p>中文说明：本服务从 {@link AgentAuditQueryService} 的 redacted recentEvents 里聚合同一个
 * traceId 的多条审计投影，给管理员推荐“可能值得进入 trace-set curation review”的候选锚点。
 * 输入是已脱敏审计读模型，不是 raw audit；输出给 Eval 工作台的候选列表和 review endpoint。</p>
 *
 * <p>安全边界：候选发现是 read-only / recommendation-only，不写 catalog、不调用 curationReview、
 * 不运行 eval、不执行 Tool、不访问 kube-manager、不调用 MCP/LLM/RAG、不写 audit/memory，也不打开
 * retrieval/vector/CI blocking。推荐结果不是自动提升，必须继续经过 review-only gate、JSON Patch、
 * 人工 Git review 和恢复记忆记录。</p>
 */
@Service
public class AgentEvalTraceSetCandidateDiscoveryService {

    public static final int DEFAULT_MAX_EVENTS = 50;
    public static final int MAX_EVENTS = 200;

    private static final Set<String> HIGH_RISK_OPERATIONS = Set.of(
        "CREATE", "UPDATE", "DELETE", "ACTION", "PLACEHOLDER"
    );

    private final AgentAuditQueryService auditQueryService;
    private final AgentEvalTraceSetCatalogService traceSetCatalogService;

    public AgentEvalTraceSetCandidateDiscoveryService(AgentAuditQueryService auditQueryService,
                                                      AgentEvalTraceSetCatalogService traceSetCatalogService) {
        this.auditQueryService = auditQueryService;
        this.traceSetCatalogService = traceSetCatalogService;
    }

    public Optional<AgentEvalTraceSetCandidateDiscoveryResponse> discover(String traceSetId, Integer maxEvents) {
        return traceSetCatalogService.findDefinition(traceSetId)
            .map(definition -> discover(definition, maxEvents));
    }

    /**
     * 中文说明：对指定 trace set 做有界扫描，避免 Observability 页面为了推荐候选而读取过多审计投影。
     * 安全边界：扫描上限被固定在 {@link #MAX_EVENTS}，且只接受 W3C trace anchor；非法或敏感字符串会被丢弃。
     */
    private AgentEvalTraceSetCandidateDiscoveryResponse discover(AgentEvalTraceSetDefinition definition,
                                                                 Integer maxEvents) {
        int boundedMaxEvents = boundMaxEvents(maxEvents);
        AgentAuditQueryResponse recent = auditQueryService.recentEvents(boundedMaxEvents);
        Map<String, CandidateAccumulator> traces = new LinkedHashMap<>();
        for (AgentAuditQueryEvent event : recent.events()) {
            String traceId = acceptedTraceId(event.traceId());
            if (traceId.isBlank()) {
                continue;
            }
            traces.computeIfAbsent(traceId, CandidateAccumulator::new).add(event);
        }
        List<AgentEvalTraceSetCandidate> candidates = traces.values().stream()
            .map(accumulator -> accumulator.toCandidate(definition.id()))
            .sorted(Comparator.comparing(AgentEvalTraceSetCandidate::recommendedForCurationReview).reversed()
                .thenComparing(AgentEvalTraceSetCandidate::lastSeenAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
        return AgentEvalTraceSetCandidateDiscoveryResponse.of(
            definition,
            backend(recent.index()),
            boundedMaxEvents,
            recent.truncated(),
            candidates,
            discoveryPolicy(definition, boundedMaxEvents),
            privacyProof()
        );
    }

    private int boundMaxEvents(Integer maxEvents) {
        if (maxEvents == null) {
            return DEFAULT_MAX_EVENTS;
        }
        return Math.max(1, Math.min(maxEvents, MAX_EVENTS));
    }

    private String acceptedTraceId(String traceId) {
        String candidate = AgentTraceContext.safeCandidateOrBlank(traceId);
        if (candidate.isBlank() || AgentTraceContext.w3cTraceIdOrBlank(candidate).isBlank()) {
            return "";
        }
        return candidate;
    }

    private Map<String, Object> discoveryPolicy(AgentEvalTraceSetDefinition definition, int maxEvents) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", AgentEvalTraceSetCandidateDiscoveryResponse.SCHEMA_VERSION);
        policy.put("traceSetId", definition.id());
        policy.put("suiteId", definition.suiteId());
        policy.put("maxEvents", maxEvents);
        policy.put("source", "AgentAuditQueryService#recentEvents");
        policy.put("sourceRedactedOnly", true);
        policy.put("requiresCurationReview", true);
        policy.put("reviewEndpoint", "/api/agent/observability/eval/trace-sets/" + definition.id() + "/curation-review");
        policy.put("candidateTraceIdFormat", "W3C trace anchor: trc_ + 32 lowercase hex or 32 lowercase hex");
        policy.put("catalogMutationAllowed", false);
        policy.put("catalogMutated", false);
        policy.put("toolExecution", false);
        policy.put("kubeManagerCalls", false);
        return Map.copyOf(policy);
    }

    private Map<String, Object> privacyProof() {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", true);
        proof.put("containsRawPrincipal", false);
        proof.put("containsRawOrganization", false);
        proof.put("containsRawConversation", false);
        proof.put("containsRawEndpoints", false);
        proof.put("containsRawReason", false);
        proof.put("containsRawParameterValues", false);
        proof.put("deterministic", true);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        proof.put("toolExecution", false);
        proof.put("kubeManagerCalls", false);
        return Map.copyOf(proof);
    }

    private String backend(Map<String, Object> index) {
        Object backend = index != null ? index.get("backend") : "";
        return backend != null ? backend.toString() : "";
    }

    private static final class CandidateAccumulator {
        /**
         * 中文说明：Accumulator 只在内存中汇总同一 traceId 的 redacted audit 投影，用于生成推荐理由。
         * 安全边界：它只统计 phase/outcome/operationType/参数是否受保护等元数据，不读取 raw 参数值、
         * raw endpoint、raw principal，也不触发任何 Tool 或外部调用。
         */
        private final String traceId;
        private Instant firstSeenAt;
        private Instant lastSeenAt;
        private int eventCount;
        private int preExecutionEvents;
        private int finalEvents;
        private int highRiskEvents;
        private int readEvents;
        private int executedEvents;
        private int successEvents;
        private int blockedEvents;
        private int errorEvents;
        private int businessFailureEvents;
        private boolean requiresConfirmation;
        private boolean protectedParameterEvidence;
        private final LinkedHashSet<String> operationTypes = new LinkedHashSet<>();
        private final LinkedHashSet<String> outcomes = new LinkedHashSet<>();
        private final LinkedHashSet<String> evidenceTags = new LinkedHashSet<>();

        private CandidateAccumulator(String traceId) {
            this.traceId = traceId;
        }

        private void add(AgentAuditQueryEvent event) {
            eventCount++;
            Instant occurredAt = event.occurredAt();
            if (occurredAt != null) {
                if (firstSeenAt == null || occurredAt.isBefore(firstSeenAt)) {
                    firstSeenAt = occurredAt;
                }
                if (lastSeenAt == null || occurredAt.isAfter(lastSeenAt)) {
                    lastSeenAt = occurredAt;
                }
            }
            String operationType = safeText(event.operationType());
            String outcome = safeText(event.outcome());
            String recordPhase = safeText(event.recordPhase());
            if (!operationType.isBlank()) {
                operationTypes.add(operationType);
            }
            if (!outcome.isBlank()) {
                outcomes.add(outcome);
            }
            if ("PRE_EXECUTION".equals(recordPhase)) {
                preExecutionEvents++;
                evidenceTags.add("pre-execution");
            }
            if ("FINAL".equals(recordPhase)) {
                finalEvents++;
                evidenceTags.add("final");
            }
            if (HIGH_RISK_OPERATIONS.contains(operationType)) {
                highRiskEvents++;
                evidenceTags.add("high-risk");
            }
            if ("READ".equals(operationType) || "SENSITIVE_READ".equals(operationType) || "GET".equals(safeText(event.httpMethod()))) {
                readEvents++;
                evidenceTags.add("read");
            }
            if (event.executed()) {
                executedEvents++;
                evidenceTags.add("executed");
            }
            if (event.success()) {
                successEvents++;
                evidenceTags.add("success");
            }
            if ("BLOCKED".equals(outcome)) {
                blockedEvents++;
                evidenceTags.add("blocked");
            }
            if ("ERROR".equals(outcome)) {
                errorEvents++;
                evidenceTags.add("error");
            }
            if ("BUSINESS_FAILURE".equals(outcome)) {
                businessFailureEvents++;
                evidenceTags.add("business-failure");
            }
            if (event.requiresConfirmation()) {
                requiresConfirmation = true;
                evidenceTags.add("hitl");
            }
            if (hasProtectedParameterEvidence(event.parameterSummary())) {
                protectedParameterEvidence = true;
                evidenceTags.add("protected-parameters");
            }
        }

        private AgentEvalTraceSetCandidate toCandidate(String traceSetId) {
            Recommendation recommendation = recommend(traceSetId);
            return new AgentEvalTraceSetCandidate(
                traceId,
                recommendation.recommended() ? "RECOMMENDED" : "NEEDS_MORE_REVIEW",
                recommendation.recommended(),
                recommendation.reasons(),
                firstSeenAt,
                lastSeenAt,
                eventCount,
                preExecutionEvents,
                finalEvents,
                highRiskEvents,
                readEvents,
                executedEvents,
                successEvents,
                blockedEvents,
                errorEvents,
                businessFailureEvents,
                requiresConfirmation,
                protectedParameterEvidence,
                List.copyOf(operationTypes),
                List.copyOf(outcomes),
                List.copyOf(evidenceTags),
                privacyProof()
            );
        }

        private Recommendation recommend(String traceSetId) {
            /*
             * 中文说明：推荐逻辑只决定“是否值得人工审阅”，不是自动加入目录。
             * 安全边界：不同 trace set 的推荐条件是 deterministic metadata rule，不调用模型，也不执行 eval runtime。
             */
            List<String> reasons = new ArrayList<>();
            boolean recommended;
            switch (safeText(traceSetId)) {
                case "phase1-core-golden" -> {
                    recommended = finalEvents > 0
                        && successEvents > 0
                        && readEvents > 0
                        && highRiskEvents == 0
                        && blockedEvents == 0
                        && errorEvents == 0
                        && businessFailureEvents == 0;
                    reasons.add("ordinary read workflow with successful final evidence");
                    reasons.add("no high-risk operation or failure outcome");
                }
                case "phase1-redaction-regression" -> {
                    recommended = finalEvents > 0 && protectedParameterEvidence;
                    reasons.add("contains protected parameter summary evidence");
                    reasons.add("final replay evidence is present");
                }
                case "phase1-high-risk-prewrite" -> {
                    recommended = highRiskEvents > 0 && preExecutionEvents > 0 && finalEvents > 0;
                    reasons.add("high-risk operation evidence is present");
                    reasons.add("PRE_EXECUTION and FINAL phases are both present");
                }
                case "phase1-red-team-safety" -> {
                    recommended = blockedEvents > 0 || errorEvents > 0 || businessFailureEvents > 0;
                    reasons.add("adversarial or safety-relevant blocked/error/business-failure outcome");
                }
                default -> {
                    recommended = finalEvents > 0;
                    reasons.add("final replay evidence is present");
                }
            }
            if (!recommended) {
                reasons.add("candidate still visible for operator inspection but is not auto-recommended");
            }
            return new Recommendation(recommended, List.copyOf(reasons));
        }

        private boolean hasProtectedParameterEvidence(Map<String, Object> parameterSummary) {
            Object keys = parameterSummary != null ? parameterSummary.get("keys") : null;
            if (keys instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item instanceof Map<?, ?> map && Boolean.TRUE.equals(map.get("protected"))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private Map<String, Object> privacyProof() {
            Map<String, Object> proof = new LinkedHashMap<>();
            proof.put("redactedOnly", true);
            proof.put("containsRawPrincipal", false);
            proof.put("containsRawOrganization", false);
            proof.put("containsRawConversation", false);
            proof.put("containsRawEndpoints", false);
            proof.put("containsRawReason", false);
            proof.put("containsRawParameterValues", false);
            return Map.copyOf(proof);
        }

        private String safeText(String value) {
            return value != null ? value : "";
        }
    }

    private record Recommendation(boolean recommended, List<String> reasons) {
    }
}
