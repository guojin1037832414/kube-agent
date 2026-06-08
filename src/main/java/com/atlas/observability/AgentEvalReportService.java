package com.atlas.observability;

import com.atlas.tool.annotation.AtlasToolMapping;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于脱敏 replay evidence 构建确定性 Agent eval report。
 *
 * <p>M5.33 的边界很重要：评测器只读 replay DTO，不读 raw audit，不调用 LLM，
 * 不访问 kube-manager，也不参与 Tool 放行。</p>
 */
@Service
public class AgentEvalReportService {

    private final AgentReplayTimelineService replayTimelineService;

    public AgentEvalReportService(AgentReplayTimelineService replayTimelineService) {
        this.replayTimelineService = replayTimelineService;
    }

    public AgentEvalReportResponse evaluateTrace(String traceId, int maxResults) {
        AgentReplayTimelineResponse replay = replayTimelineService.traceTimeline(traceId, maxResults);
        List<AgentReplayTimelineStep> steps = replay.steps();
        Summary summary = summarize(steps);
        Map<String, Object> privacy = privacyProof(replay.privacy());
        List<AgentEvalCheck> checks = checks(replay, summary);
        long failures = checks.stream().filter(check -> "FAIL".equals(check.status())).count();
        long warnings = checks.stream().filter(check -> "WARN".equals(check.status())).count();
        boolean pass = failures == 0;
        String verdict = pass ? (warnings > 0 ? "PASS_WITH_WARNINGS" : "PASS") : "FAIL";
        int score = score(failures, warnings);

        return AgentEvalReportResponse.of(
            replay.traceId(),
            replay.schemaVersion(),
            replay.resultCount(),
            replay.maxResults(),
            replay.truncated(),
            replay.order(),
            verdict,
            score,
            pass,
            summary.toMap(),
            privacy,
            replay,
            checks
        );
    }

    public AgentEvalSuiteResponse evaluateSuite(List<String> traceIds,
                                                int maxResults,
                                                int minimumScore,
                                                boolean failOnWarnings) {
        List<String> normalizedTraceIds = normalizeTraceIds(traceIds);
        List<AgentEvalReportResponse> reports = normalizedTraceIds.stream()
            .map(traceId -> evaluateTrace(traceId, maxResults))
            .toList();
        SuiteSummary summary = summarizeSuite(reports, normalizedTraceIds.isEmpty());
        int boundedMinimumScore = Math.max(0, Math.min(100, minimumScore));
        boolean pass = !normalizedTraceIds.isEmpty()
            && summary.failedReports == 0
            && summary.minimumScore >= boundedMinimumScore
            && (!failOnWarnings || summary.warningReports == 0);
        return AgentEvalSuiteResponse.of(
            pass ? "PASS" : "FAIL",
            pass,
            boundedMinimumScore,
            failOnWarnings,
            maxResults,
            normalizedTraceIds,
            summary.toMap(),
            suitePrivacy(reports),
            reports
        );
    }

    private List<AgentEvalCheck> checks(AgentReplayTimelineResponse replay, Summary summary) {
        List<AgentEvalCheck> checks = new ArrayList<>();
        checks.add(tracePresenceCheck(replay));
        checks.add(privacyCheck(replay));
        checks.add(timelineOrderCheck(replay.steps()));
        checks.add(traceConsistencyCheck(replay.traceId(), replay.steps()));
        checks.add(phaseSequenceCheck(summary));
        checks.add(executionSemanticsCheck(summary));
        checks.add(preExecutionEvidenceCheck(summary));
        checks.add(highRiskExecutionCheck(summary));
        checks.add(outcomeCheck(summary));
        checks.add(limitCheck(replay));
        return checks;
    }

    private List<String> normalizeTraceIds(List<String> traceIds) {
        if (traceIds == null || traceIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> uniqueTraceIds = new LinkedHashSet<>();
        for (String traceId : traceIds) {
            String normalized = safeText(traceId).trim();
            if (!normalized.isBlank()) {
                uniqueTraceIds.add(normalized);
            }
        }
        return List.copyOf(uniqueTraceIds);
    }

    private SuiteSummary summarizeSuite(List<AgentEvalReportResponse> reports, boolean emptyInput) {
        SuiteSummary summary = new SuiteSummary();
        summary.caseCount = reports.size();
        summary.emptyInput = emptyInput;
        int scoreTotal = 0;
        int minimumScore = reports.isEmpty() ? 0 : 100;
        for (AgentEvalReportResponse report : reports) {
            scoreTotal += report.score();
            minimumScore = Math.min(minimumScore, report.score());
            if (report.pass()) {
                summary.passedReports++;
            } else {
                summary.failedReports++;
                summary.failedTraceIds.add(report.traceId());
            }
            if ("PASS_WITH_WARNINGS".equals(report.verdict())) {
                summary.warningReports++;
                summary.warningTraceIds.add(report.traceId());
            }
            for (AgentEvalCheck check : report.checks()) {
                if ("FAIL".equals(check.status())) {
                    summary.failedChecks++;
                }
                if ("WARN".equals(check.status())) {
                    summary.warningChecks++;
                }
            }
        }
        summary.minimumScore = minimumScore;
        summary.averageScore = reports.isEmpty() ? 0.0 : Math.round((scoreTotal * 100.0 / reports.size())) / 100.0;
        return summary;
    }

    private Map<String, Object> suitePrivacy(List<AgentEvalReportResponse> reports) {
        boolean containsRawPrincipal = reports.stream().anyMatch(report -> truthy(report.privacy(), "containsRawPrincipal"));
        boolean containsRawOrganization = reports.stream().anyMatch(report -> truthy(report.privacy(), "containsRawOrganization"));
        boolean containsRawConversation = reports.stream().anyMatch(report -> truthy(report.privacy(), "containsRawConversation"));
        boolean containsRawEndpoints = reports.stream().anyMatch(report -> truthy(report.privacy(), "containsRawEndpoints"));
        boolean containsRawReason = reports.stream().anyMatch(report -> truthy(report.privacy(), "containsRawReason"));
        boolean containsRawParameterValues = reports.stream().anyMatch(report -> truthy(report.privacy(), "containsRawParameterValues"));
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", !(containsRawPrincipal
            || containsRawOrganization
            || containsRawConversation
            || containsRawEndpoints
            || containsRawReason
            || containsRawParameterValues));
        proof.put("containsRawPrincipal", containsRawPrincipal);
        proof.put("containsRawOrganization", containsRawOrganization);
        proof.put("containsRawConversation", containsRawConversation);
        proof.put("containsRawEndpoints", containsRawEndpoints);
        proof.put("containsRawReason", containsRawReason);
        proof.put("containsRawParameterValues", containsRawParameterValues);
        proof.put("deterministic", true);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        return proof;
    }

    private boolean truthy(Map<String, Object> data, String key) {
        return data != null && Boolean.TRUE.equals(data.get(key));
    }

    private AgentEvalCheck tracePresenceCheck(AgentReplayTimelineResponse replay) {
        Map<String, Object> evidence = Map.of(
            "traceIdPresent", replay.traceId() != null && !replay.traceId().isBlank(),
            "stepCount", replay.steps().size()
        );
        if (replay.traceId() == null || replay.traceId().isBlank()) {
            return AgentEvalCheck.fail("TRACE_ID_PRESENT", "trace", "Trace replay is missing traceId.", evidence);
        }
        if (replay.steps().isEmpty()) {
            return AgentEvalCheck.warn("TRACE_HAS_STEPS", "trace", "No replay steps were found for this trace.", evidence);
        }
        return AgentEvalCheck.pass("TRACE_HAS_STEPS", "trace", "Trace has replayable evidence steps.", evidence);
    }

    private AgentEvalCheck privacyCheck(AgentReplayTimelineResponse replay) {
        Map<String, Object> privacy = replay.privacy();
        boolean redacted = Boolean.TRUE.equals(privacy.get("redactedOnly"))
            && Boolean.FALSE.equals(privacy.get("containsRawPrincipal"))
            && Boolean.FALSE.equals(privacy.get("containsRawOrganization"))
            && Boolean.FALSE.equals(privacy.get("containsRawConversation"))
            && Boolean.FALSE.equals(privacy.get("containsRawEndpoints"))
            && Boolean.FALSE.equals(privacy.get("containsRawReason"))
            && Boolean.FALSE.equals(privacy.get("containsRawParameterValues"));
        if (redacted) {
            return AgentEvalCheck.pass("PRIVACY_REDACTED_ONLY", "privacy", "Replay evidence is marked redacted-only.", privacy);
        }
        return AgentEvalCheck.fail("PRIVACY_REDACTED_ONLY", "privacy", "Replay privacy metadata is not redacted-only.", privacy);
    }

    private AgentEvalCheck timelineOrderCheck(List<AgentReplayTimelineStep> steps) {
        boolean ordered = true;
        boolean positionsContinuous = true;
        Instant previous = null;
        for (int i = 0; i < steps.size(); i++) {
            AgentReplayTimelineStep step = steps.get(i);
            if (step.position() != i + 1) {
                positionsContinuous = false;
            }
            Instant occurredAt = step.occurredAt();
            if (occurredAt != null && previous != null && occurredAt.isBefore(previous)) {
                ordered = false;
                break;
            }
            if (occurredAt != null) {
                previous = occurredAt;
            }
        }
        Map<String, Object> evidence = Map.of(
            "order", "oldest-first",
            "stepCount", steps.size(),
            "positionsContinuous", positionsContinuous
        );
        if (ordered && positionsContinuous) {
            return AgentEvalCheck.pass("TIMELINE_ORDER", "replay", "Replay steps are oldest-first with continuous positions.", evidence);
        }
        return AgentEvalCheck.fail("TIMELINE_ORDER", "replay", "Replay steps are not oldest-first or positions are not continuous.", evidence);
    }

    private AgentEvalCheck traceConsistencyCheck(String traceId, List<AgentReplayTimelineStep> steps) {
        long mismatches = steps.stream()
            .filter(step -> traceId != null && !traceId.equals(step.traceId()))
            .count();
        Map<String, Object> evidence = Map.of("traceId", traceId != null ? traceId : "", "mismatches", mismatches);
        if (mismatches == 0) {
            return AgentEvalCheck.pass("TRACE_CONSISTENCY", "trace", "All replay steps belong to the requested trace.", evidence);
        }
        return AgentEvalCheck.fail("TRACE_CONSISTENCY", "trace", "Replay contains steps from another trace.", evidence);
    }

    private AgentEvalCheck phaseSequenceCheck(Summary summary) {
        Map<String, Object> evidence = Map.of(
            "auditIdsWithPreExecution", summary.preExecutionAuditIds.size(),
            "auditIdsWithFinal", summary.finalAuditIds.size(),
            "finalBeforePreExecutionAuditIds", List.copyOf(summary.finalBeforePreExecutionAuditIds)
        );
        if (summary.finalBeforePreExecutionAuditIds.isEmpty()) {
            return AgentEvalCheck.pass("PHASE_SEQUENCE", "audit", "Audit phases are in pre-execution then final order.", evidence);
        }
        return AgentEvalCheck.fail("PHASE_SEQUENCE", "audit", "Some audit phases place FINAL before PRE_EXECUTION.", evidence);
    }

    private AgentEvalCheck executionSemanticsCheck(Summary summary) {
        Map<String, Object> evidence = Map.of(
            "successWithoutExecutionSteps", summary.successWithoutExecutionSteps,
            "blockedSuccessSteps", summary.blockedSuccessSteps
        );
        if (summary.successWithoutExecutionSteps == 0 && summary.blockedSuccessSteps == 0) {
            return AgentEvalCheck.pass("EXECUTION_SEMANTICS", "outcome", "Success and blocked replay statuses match execution flags.", evidence);
        }
        return AgentEvalCheck.fail("EXECUTION_SEMANTICS", "outcome", "Replay contains impossible success/execution combinations.", evidence);
    }

    private AgentEvalCheck preExecutionEvidenceCheck(Summary summary) {
        Map<String, Object> evidence = Map.of(
            "preExecutionSteps", summary.preExecutionSteps,
            "finalSteps", summary.finalSteps,
            "highRiskSteps", summary.highRiskSteps,
            "highRiskFinalAuditIds", summary.highRiskFinalAuditIds.size(),
            "missingPreExecutionAuditIds", List.copyOf(summary.highRiskFinalMissingPreExecutionAuditIds)
        );
        if (summary.highRiskFinalAuditIds.isEmpty()) {
            return AgentEvalCheck.pass("HIGH_RISK_PREWRITE_EVIDENCE", "audit", "No high-risk steps require durable pre-execution evidence.", evidence);
        }
        if (summary.highRiskFinalMissingPreExecutionAuditIds.isEmpty()) {
            return AgentEvalCheck.pass("HIGH_RISK_PREWRITE_EVIDENCE", "audit", "High-risk final steps include matching pre-execution evidence.", evidence);
        }
        return AgentEvalCheck.fail("HIGH_RISK_PREWRITE_EVIDENCE", "audit", "High-risk trace is missing pre-execution evidence.", evidence);
    }

    private AgentEvalCheck highRiskExecutionCheck(Summary summary) {
        Map<String, Object> evidence = Map.of(
            "executedHighRiskSteps", summary.executedHighRiskSteps,
            "confirmationRequiredSteps", summary.confirmationRequiredSteps,
            "missingConfirmationAuditIds", List.copyOf(summary.executedHighRiskMissingConfirmationAuditIds)
        );
        if (summary.executedHighRiskSteps == 0 || summary.executedHighRiskMissingConfirmationAuditIds.isEmpty()) {
            return AgentEvalCheck.pass("HIGH_RISK_CONFIRMATION_MARKER", "safety", "Executed high-risk steps carry confirmation evidence or no high-risk execution occurred.", evidence);
        }
        return AgentEvalCheck.warn("HIGH_RISK_CONFIRMATION_MARKER", "safety", "Executed high-risk steps do not carry confirmation markers in replay evidence.", evidence);
    }

    private AgentEvalCheck outcomeCheck(Summary summary) {
        Map<String, Object> evidence = Map.of(
            "successSteps", summary.successSteps,
            "blockedSteps", summary.blockedSteps,
            "errorSteps", summary.errorSteps,
            "businessFailureSteps", summary.businessFailureSteps
        );
        if (summary.errorSteps > 0) {
            return AgentEvalCheck.warn("OUTCOME_HEALTH", "outcome", "Trace contains Tool execution errors.", evidence);
        }
        if (summary.blockedSteps > 0 || summary.businessFailureSteps > 0) {
            return AgentEvalCheck.warn("OUTCOME_HEALTH", "outcome", "Trace contains blocked or business-failure steps.", evidence);
        }
        return AgentEvalCheck.pass("OUTCOME_HEALTH", "outcome", "Trace contains no blocked/error/business-failure replay steps.", evidence);
    }

    private AgentEvalCheck limitCheck(AgentReplayTimelineResponse replay) {
        Map<String, Object> evidence = Map.of(
            "maxResults", replay.maxResults(),
            "resultCount", replay.resultCount(),
            "truncated", replay.truncated()
        );
        if (replay.truncated()) {
            return AgentEvalCheck.warn("REPLAY_NOT_TRUNCATED", "coverage", "Replay evidence is truncated by the query limit.", evidence);
        }
        return AgentEvalCheck.pass("REPLAY_NOT_TRUNCATED", "coverage", "Replay evidence was not truncated.", evidence);
    }

    private Summary summarize(List<AgentReplayTimelineStep> steps) {
        Summary summary = new Summary();
        Map<String, Integer> firstPreExecutionPositionByAuditId = new HashMap<>();
        Map<String, Integer> firstFinalPositionByAuditId = new HashMap<>();
        for (AgentReplayTimelineStep step : steps) {
            summary.stepCount++;
            String auditId = evidenceAuditId(step);
            String recordPhase = safeText(step.recordPhase()).toUpperCase(java.util.Locale.ROOT);
            boolean preExecutionPhase = "PRE_EXECUTION".equals(recordPhase);
            boolean finalPhase = "FINAL".equals(recordPhase);
            boolean highRisk = isHighRisk(step.operationType());

            if (preExecutionPhase) {
                summary.preExecutionSteps++;
                summary.preExecutionAuditIds.add(auditId);
                firstPreExecutionPositionByAuditId.putIfAbsent(auditId, step.position());
            }
            if (finalPhase) {
                summary.finalSteps++;
                summary.finalAuditIds.add(auditId);
                firstFinalPositionByAuditId.putIfAbsent(auditId, step.position());
            }
            if (highRisk) {
                summary.highRiskSteps++;
                if (step.executed()) {
                    summary.executedHighRiskSteps++;
                }
                if (finalPhase && step.executed()) {
                    summary.highRiskFinalAuditIds.add(auditId);
                }
                if (step.executed() && !step.requiresConfirmation()) {
                    summary.executedHighRiskMissingConfirmationAuditIds.add(auditId);
                }
            }
            if (step.requiresConfirmation()) {
                summary.confirmationRequiredSteps++;
            }
            if (step.success()) {
                summary.successSteps++;
            }
            if ("blocked".equals(step.status())) {
                summary.blockedSteps++;
            }
            if ("error".equals(step.status())) {
                summary.errorSteps++;
            }
            if ("business_failure".equals(step.status())) {
                summary.businessFailureSteps++;
            }
            if (step.success() && !step.executed()) {
                summary.successWithoutExecutionSteps++;
            }
            if ("blocked".equals(step.status()) && step.success()) {
                summary.blockedSuccessSteps++;
            }
        }
        summary.highRiskFinalMissingPreExecutionAuditIds.addAll(summary.highRiskFinalAuditIds);
        summary.highRiskFinalMissingPreExecutionAuditIds.removeAll(summary.preExecutionAuditIds);
        for (String auditId : summary.preExecutionAuditIds) {
            Integer prePosition = firstPreExecutionPositionByAuditId.get(auditId);
            Integer finalPosition = firstFinalPositionByAuditId.get(auditId);
            if (prePosition != null && finalPosition != null && finalPosition < prePosition) {
                summary.finalBeforePreExecutionAuditIds.add(auditId);
            }
        }
        return summary;
    }

    private boolean isHighRisk(String operationType) {
        return AtlasToolMapping.OperationType.CREATE.name().equals(operationType)
            || AtlasToolMapping.OperationType.UPDATE.name().equals(operationType)
            || AtlasToolMapping.OperationType.DELETE.name().equals(operationType)
            || AtlasToolMapping.OperationType.ACTION.name().equals(operationType)
            || AtlasToolMapping.OperationType.PLACEHOLDER.name().equals(operationType);
    }

    private String evidenceAuditId(AgentReplayTimelineStep step) {
        String auditId = safeText(step.auditId());
        if (!auditId.isBlank()) {
            return auditId;
        }
        String stepId = safeText(step.stepId());
        return !stepId.isBlank() ? stepId : "position-" + step.position();
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }

    private int score(long failures, long warnings) {
        return (int) Math.max(0, 100 - failures * 35 - warnings * 10);
    }

    private Map<String, Object> privacyProof(Map<String, Object> replayPrivacy) {
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", Boolean.TRUE.equals(replayPrivacy.get("redactedOnly")));
        proof.put("containsRawPrincipal", Boolean.TRUE.equals(replayPrivacy.get("containsRawPrincipal")));
        proof.put("containsRawOrganization", Boolean.TRUE.equals(replayPrivacy.get("containsRawOrganization")));
        proof.put("containsRawConversation", Boolean.TRUE.equals(replayPrivacy.get("containsRawConversation")));
        proof.put("containsRawEndpoints", Boolean.TRUE.equals(replayPrivacy.get("containsRawEndpoints")));
        proof.put("containsRawReason", Boolean.TRUE.equals(replayPrivacy.get("containsRawReason")));
        proof.put("containsRawParameterValues", Boolean.TRUE.equals(replayPrivacy.get("containsRawParameterValues")));
        proof.put("deterministic", true);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        return proof;
    }

    private static final class Summary {
        private int stepCount;
        private int preExecutionSteps;
        private int finalSteps;
        private int highRiskSteps;
        private int executedHighRiskSteps;
        private int confirmationRequiredSteps;
        private int successSteps;
        private int blockedSteps;
        private int errorSteps;
        private int businessFailureSteps;
        private int successWithoutExecutionSteps;
        private int blockedSuccessSteps;
        private final Set<String> preExecutionAuditIds = new LinkedHashSet<>();
        private final Set<String> finalAuditIds = new LinkedHashSet<>();
        private final Set<String> highRiskFinalAuditIds = new LinkedHashSet<>();
        private final Set<String> highRiskFinalMissingPreExecutionAuditIds = new LinkedHashSet<>();
        private final Set<String> executedHighRiskMissingConfirmationAuditIds = new LinkedHashSet<>();
        private final List<String> finalBeforePreExecutionAuditIds = new ArrayList<>();

        private Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stepCount", stepCount);
            data.put("preExecutionSteps", preExecutionSteps);
            data.put("finalSteps", finalSteps);
            data.put("highRiskSteps", highRiskSteps);
            data.put("executedHighRiskSteps", executedHighRiskSteps);
            data.put("confirmationRequiredSteps", confirmationRequiredSteps);
            data.put("successSteps", successSteps);
            data.put("blockedSteps", blockedSteps);
            data.put("errorSteps", errorSteps);
            data.put("businessFailureSteps", businessFailureSteps);
            data.put("successWithoutExecutionSteps", successWithoutExecutionSteps);
            data.put("blockedSuccessSteps", blockedSuccessSteps);
            data.put("highRiskFinalAuditIds", highRiskFinalAuditIds.size());
            data.put("highRiskFinalMissingPreExecutionAuditIds", List.copyOf(highRiskFinalMissingPreExecutionAuditIds));
            data.put("executedHighRiskMissingConfirmationAuditIds", List.copyOf(executedHighRiskMissingConfirmationAuditIds));
            data.put("finalBeforePreExecutionAuditIds", List.copyOf(finalBeforePreExecutionAuditIds));
            return data;
        }
    }

    private static final class SuiteSummary {
        private int caseCount;
        private int passedReports;
        private int failedReports;
        private int warningReports;
        private int failedChecks;
        private int warningChecks;
        private int minimumScore;
        private double averageScore;
        private boolean emptyInput;
        private final List<String> failedTraceIds = new ArrayList<>();
        private final List<String> warningTraceIds = new ArrayList<>();

        private Map<String, Object> toMap() {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("caseCount", caseCount);
            data.put("passedReports", passedReports);
            data.put("failedReports", failedReports);
            data.put("warningReports", warningReports);
            data.put("failedChecks", failedChecks);
            data.put("warningChecks", warningChecks);
            data.put("minimumScore", minimumScore);
            data.put("averageScore", averageScore);
            data.put("emptyInput", emptyInput);
            data.put("failedTraceIds", List.copyOf(failedTraceIds));
            data.put("warningTraceIds", List.copyOf(warningTraceIds));
            return data;
        }
    }
}
