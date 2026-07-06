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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * reviewed fixture 人工 Git review gate 服务测试。
 *
 * <p>中文说明：这些测试保护 M5.85-47 的“人审字段 + 最终 sha256 摘要”只读门禁。它允许后端判断
 * 人工 Git 提交是否可以继续，但不允许运行时创建 fixture、写 catalog、调用 Tool/MCP/kube-manager，
 * 也不允许把调用方传入的 traceId 或敏感字符串当成 reviewed evidence。</p>
 */
class AgentReviewedTraceFixtureHumanReviewGateServiceTest {

    @Test
    void gate_shouldPassOnlyWhenHumanFieldsAndFinalDigestMatchCurrentPackage() throws Exception {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        String traceId = "trc_e1111111111111111111111111111111";
        recorder.record(event("aud_fixture_human_review_gate", traceId));
        AgentReviewedTraceFixtureHumanReviewGateService service = service(recorder);
        AgentReviewedTraceFixtureHumanReviewGateRequest bootstrap = request(
            traceId,
            "0123456789abcdef0123456789abcdef01234567",
            "zhaotiandi",
            "2026-07-07T00:00:00Z",
            "sha256:0000000000000000000000000000000000000000000000000000000000000000",
            "sha256:0000000000000000000000000000000000000000000000000000000000000000"
        );
        AgentReviewedTraceFixtureHumanReviewGateResponse bootstrapResponse = service
            .gate("phase1-core-golden", 50, bootstrap)
            .orElseThrow();
        String candidateDigest = bootstrapResponse.humanReviewPackage()
            .candidateFixtureDraft()
            .get("candidateEvidenceDigest")
            .toString();
        String expectedDigest = bootstrapResponse.expectedEvidenceDigest();

        AgentReviewedTraceFixtureHumanReviewGateResponse response = service
            .gate("phase1-core-golden", 50, request(
                traceId,
                "0123456789abcdef0123456789abcdef01234567",
                "zhaotiandi",
                "2026-07-07T00:00:00Z",
                candidateDigest,
                expectedDigest
            ))
            .orElseThrow();

        assertThat(response.schemaVersion()).isEqualTo("agent-reviewed-trace-fixture-human-review-gate.v1");
        assertThat(response.gateStatus()).isEqualTo("READY_FOR_MANUAL_GIT_FIXTURE_COMMIT");
        assertThat(response.readyForHumanGitReview()).isTrue();
        assertThat(response.readyForFixtureCommit()).isTrue();
        assertThat(response.runtimeFixtureCommitAllowed()).isFalse();
        assertThat(response.humanFieldsComplete()).isTrue();
        assertThat(response.selectedTraceMatchesPackage()).isTrue();
        assertThat(response.candidateEvidenceDigestMatches()).isTrue();
        assertThat(response.evidenceDigestMatchesExpected()).isTrue();
        assertThat(response.blockingReasons()).isEmpty();
        assertThat(response.fieldResults()).allSatisfy(field -> assertThat(field)
            .containsEntry("valid", true)
            .containsEntry("runtimeCallable", false)
            .containsEntry("callerSuppliedAuthorityAccepted", false));
        assertThat(response.manifestQualityGatePreview())
            .containsEntry("readyForFixtureCommit", true)
            .containsEntry("manualGitCommitOnly", true)
            .containsEntry("runtimeFixtureCommitAllowed", false)
            .containsEntry("qualityGateStatusGrantedNow", false)
            .containsEntry("manifestRescanRequiredAfterCommit", true);
        assertThat(response.gatePolicy())
            .containsEntry("validateOnly", true)
            .containsEntry("readyForFixtureCommit", true)
            .containsEntry("runtimeFixtureCommitAllowed", false)
            .containsEntry("createsFixtureFile", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("releaseAuthority", false);
        assertThat(response.safety())
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("memoryWrite", false);
        assertThat(response.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
            .contains("READY_FOR_MANUAL_GIT_FIXTURE_COMMIT", "expectedEvidenceDigest")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("\"reports\"", "\"steps\"", "\"fixtureRows\"");
    }

    @Test
    void gate_shouldFailClosedWhenHumanReviewPackageHasNoCandidate() {
        AgentReviewedTraceFixtureHumanReviewGateService service = service(new InMemoryAgentAuditRecorder());

        AgentReviewedTraceFixtureHumanReviewGateResponse response = service
            .gate("phase1-core-golden", 50, null)
            .orElseThrow();

        assertThat(response.gateStatus()).isEqualTo("BLOCKED_BY_HUMAN_REVIEW_PACKAGE");
        assertThat(response.readyForFixtureCommit()).isFalse();
        assertThat(response.runtimeFixtureCommitAllowed()).isFalse();
        assertThat(response.blockingReasons())
            .contains("human-review-package-not-ready", "final-evidence-digest-missing-or-mismatch");
        assertThat(response.nextActions())
            .contains("copy-latest-human-review-package", "keep-runtime-fixture-and-catalog-write-disabled");
        assertThat(response.manifestQualityGatePreview())
            .containsEntry("readyForFixtureCommit", false)
            .containsEntry("runtimeCatalogWrite", false);
    }

    @Test
    void gate_shouldRejectMismatchedCallerTraceWithoutEchoingSensitiveInput() throws Exception {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        recorder.record(event("aud_fixture_human_review_gate_mismatch", "trc_e2222222222222222222222222222222"));
        AgentReviewedTraceFixtureHumanReviewGateService service = service(recorder);

        AgentReviewedTraceFixtureHumanReviewGateResponse response = service
            .gate("phase1-core-golden", 50, request(
                "token=secret-token-value",
                "not-a-full-sha",
                "bearer-token",
                "not-a-timestamp",
                "sha256:1111111111111111111111111111111111111111111111111111111111111111",
                "sha256:2222222222222222222222222222222222222222222222222222222222222222"
            ))
            .orElseThrow();

        assertThat(response.gateStatus()).isEqualTo("HUMAN_REVIEW_GATE_REWORK_REQUIRED");
        assertThat(response.requestedCandidateTraceId()).isEqualTo("REDACTED_MISMATCH");
        assertThat(response.readyForFixtureCommit()).isFalse();
        assertThat(response.blockingReasons())
            .contains(
                "selected-candidate-trace-id-mismatch",
                "source-commit-sha-missing-or-not-full-40-hex",
                "reviewer-missing-or-unsafe",
                "review-timestamp-missing-or-not-instant",
                "candidate-evidence-digest-mismatch",
                "final-evidence-digest-missing-or-mismatch"
            );
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
            .doesNotContain("secret-token-value", "bearer-token", "not-a-full-sha", "not-a-timestamp");
    }

    @Test
    void gate_shouldRejectUnknownTraceSet() {
        AgentReviewedTraceFixtureHumanReviewGateService service = service(new InMemoryAgentAuditRecorder());

        assertThat(service.gate("missing-trace-set", 50, null)).isEmpty();
    }

    private static AgentReviewedTraceFixtureHumanReviewGateRequest request(String selectedCandidateTraceId,
                                                                           String sourceCommitSha,
                                                                           String reviewer,
                                                                           String reviewTimestamp,
                                                                           String candidateEvidenceDigest,
                                                                           String evidenceDigest) {
        return new AgentReviewedTraceFixtureHumanReviewGateRequest(
            selectedCandidateTraceId,
            sourceCommitSha,
            reviewer,
            reviewTimestamp,
            candidateEvidenceDigest,
            evidenceDigest
        );
    }

    private static AgentReviewedTraceFixtureHumanReviewGateService service(InMemoryAgentAuditRecorder recorder) {
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(recorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalTraceSetCandidateDiscoveryService discoveryService =
            new AgentEvalTraceSetCandidateDiscoveryService(recorder, traceSetCatalogService);
        AgentReviewedTraceFixtureCandidateService candidateService =
            new AgentReviewedTraceFixtureCandidateService(traceSetCatalogService, evalReportService);
        AgentReviewedTraceFixtureCandidateWorkbenchService workbenchService =
            new AgentReviewedTraceFixtureCandidateWorkbenchService(discoveryService, candidateService);
        AgentReviewedTraceFixtureHumanReviewPackageService packageService =
            new AgentReviewedTraceFixtureHumanReviewPackageService(workbenchService);
        return new AgentReviewedTraceFixtureHumanReviewGateService(packageService);
    }

    private static AgentAuditEvent event(String auditId, String traceId) {
        return new AgentAuditEvent(
            auditId,
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
                "name", "namespace",
                "protected", false,
                "type", "string",
                "present", true
            )))
        );
    }
}
