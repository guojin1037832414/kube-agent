package com.atlas.observability;

import com.atlas.audit.AgentAuditEvent;
import com.atlas.audit.AgentAuditOutcome;
import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * reviewed fixture candidate 预检测试。
 *
 * <p>中文说明：这些测试保护 M5.85-43 的“真实 fixture 入仓前一站”契约。服务可以把 redacted
 * replay/eval 摘要整理成候选草稿，但不能创建 fixture 文件、不能把 caller traceId 直接提升成 reviewed evidence，
 * 也不能把 raw audit、reports 或 replay steps 暴露给前端。</p>
 */
class AgentReviewedTraceFixtureCandidateServiceTest {

    @Test
    void candidate_shouldBuildReviewOnlyDraftFromRedactedReplayEvidence() throws Exception {
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        String traceId = "trc_f1111111111111111111111111111111";
        auditRecorder.record(new AgentAuditEvent(
            "aud_fixture_candidate",
            Instant.parse("2026-06-09T00:00:00Z"),
            traceId,
            "conv-sensitive",
            "user-sensitive",
            "org-sensitive",
            "intent",
            "tool",
            SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            List.of("/api/org-sensitive/pod?token=secret-token-value"),
            AtlasToolMapping.OperationType.READ,
            false,
            AgentAuditOutcome.SUCCESS,
            true,
            true,
            "ok token=secret-token-value",
            Map.of("count", 1, "keys", List.of(Map.of(
                "name", "token",
                "protected", true,
                "type", "string",
                "present", true
            )))
        ));
        AgentReviewedTraceFixtureCandidateService service = service(auditRecorder);

        Optional<AgentReviewedTraceFixtureCandidateResponse> response = service.candidate(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of(traceId, "secret-token-value"), null, null, null)
        );

        assertThat(response).isPresent();
        AgentReviewedTraceFixtureCandidateResponse candidate = response.get();
        assertThat(candidate.schemaVersion()).isEqualTo("agent-reviewed-trace-fixture-candidate.v1");
        assertThat(candidate.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(candidate.selectedTraceId()).isEqualTo(traceId);
        assertThat(candidate.candidateStatus()).isEqualTo("READY_FOR_HUMAN_FIXTURE_REVIEW");
        assertThat(candidate.readyForHumanGitReview()).isTrue();
        assertThat(candidate.readyForFixtureCommit()).isFalse();
        assertThat(candidate.acceptedCandidateTraceIdCount()).isEqualTo(1);
        assertThat(candidate.rejectedCandidateTraceIdCount()).isEqualTo(1);
        assertThat(candidate.blockingReasons()).isEmpty();
        assertThat(candidate.remainingHumanReviewFields())
            .containsExactly("sourceCommitSha", "reviewer", "reviewTimestamp", "evidenceDigest");
        assertThat(candidate.replaySource())
            .containsEntry("type", "redacted-replay-timeline")
            .containsEntry("timelineStepCount", 1)
            .containsEntry("redactedOnly", true)
            .containsEntry("embeddedReplay", false);
        assertThat(String.valueOf(candidate.replaySource().get("digest"))).startsWith("sha256:");
        assertThat(candidate.deterministicEvalProof())
            .containsEntry("deterministic", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("embeddedReports", false);
        assertThat(candidate.candidateGateSummary())
            .containsEntry("traceSetId", "phase1-core-golden")
            .containsEntry("suiteId", "release-gate-strict")
            .containsEntry("pass", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false);
        assertThat(candidate.candidateFixtureDraft())
            .containsEntry("traceId", traceId)
            .containsEntry("traceSetId", "phase1-core-golden")
            .containsEntry("sourceCommitSha", "<fill-during-human-git-review>")
            .containsEntry("reviewer", "<fill-during-human-git-review>")
            .containsEntry("reviewTimestamp", "<fill-during-human-git-review>")
            .containsEntry("evidenceDigest", "<sha256-after-human-review>")
            .containsEntry("readyForManifestQualityGateNow", false)
            .containsEntry("requiresHumanGitReviewBeforeCommit", true);
        assertThat(candidate.safety())
            .containsEntry("previewOnly", true)
            .containsEntry("createsFixtureFile", false)
            .containsEntry("fixtureUploadAccepted", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("runtimeEvalAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(candidate.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false)
            .containsEntry("containsRawEndpoints", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(candidate.nextActions().get(0)).isEqualTo("copy-candidate-draft-outside-runtime");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(candidate))
            .contains("candidateFixtureDraft", "READY_FOR_HUMAN_FIXTURE_REVIEW")
            .doesNotContain("\"replay\"", "\"reports\"", "\"steps\"", "\"fixtureRows\"")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void candidate_shouldFailClosedForMissingOrInvalidTraceEvidence() throws Exception {
        AgentReviewedTraceFixtureCandidateService service = service(new InMemoryAgentAuditRecorder());

        Optional<AgentReviewedTraceFixtureCandidateResponse> response = service.candidate(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of("secret-token-value", "../bad-trace"), null, null, null)
        );

        assertThat(response).isPresent();
        AgentReviewedTraceFixtureCandidateResponse candidate = response.get();
        assertThat(candidate.selectedTraceId()).isBlank();
        assertThat(candidate.candidateStatus()).isEqualTo("TRACE_ID_REQUIRED_FOR_FIXTURE_CANDIDATE");
        assertThat(candidate.readyForHumanGitReview()).isFalse();
        assertThat(candidate.readyForFixtureCommit()).isFalse();
        assertThat(candidate.acceptedCandidateTraceIdCount()).isZero();
        assertThat(candidate.rejectedCandidateTraceIdCount()).isEqualTo(2);
        assertThat(candidate.blockingReasons())
            .contains(
                "candidate-trace-id-missing-or-invalid",
                "redacted-replay-timeline-missing",
                "deterministic-eval-not-passing"
            );
        assertThat(candidate.candidateFixtureDraft())
            .containsEntry("readyForManifestQualityGateNow", false)
            .containsEntry("requiresHumanGitReviewBeforeCommit", true);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(candidate))
            .doesNotContain("secret-token-value", "../bad-trace", "\"replay\"", "\"reports\"", "\"steps\"");
    }

    @Test
    void candidate_shouldRejectUnknownTraceSet() {
        AgentReviewedTraceFixtureCandidateService service = service(new InMemoryAgentAuditRecorder());

        assertThat(service.candidate("missing-trace-set", null)).isEmpty();
    }

    private static AgentReviewedTraceFixtureCandidateService service(InMemoryAgentAuditRecorder auditRecorder) {
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(auditRecorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService evalSuiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(evalSuiteCatalogService, new ObjectMapper());
        return new AgentReviewedTraceFixtureCandidateService(traceSetCatalogService, evalReportService);
    }
}
