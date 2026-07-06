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
 * reviewed fixture 人审包服务测试。
 *
 * <p>中文说明：这些测试保护 M5.85-45 的“自动候选 -> 人工 Git review 包”契约。
 * 服务可以把 redacted candidate preview 整理成人审清单，但不能创建 fixture 文件、不能写 catalog，
 * 也不能把 caller 或 LLM 输入当成 review / release 权力。</p>
 */
class AgentReviewedTraceFixtureHumanReviewPackageServiceTest {

    @Test
    void packageForTraceSet_shouldBuildHumanReviewPackageFromAutoWorkbench() throws Exception {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        String traceId = "trc_f1111111111111111111111111111111";
        recorder.record(event(
            "aud_fixture_human_review_package",
            traceId,
            AgentAuditOutcome.SUCCESS,
            AtlasToolMapping.OperationType.READ,
            false,
            true,
            true
        ));
        AgentReviewedTraceFixtureHumanReviewPackageService service = service(recorder);

        AgentReviewedTraceFixtureHumanReviewPackageResponse response = service
            .packageForTraceSet("phase1-core-golden", 50)
            .orElseThrow();

        assertThat(response.schemaVersion()).isEqualTo("agent-reviewed-trace-fixture-human-review-package.v1");
        assertThat(response.packageStatus()).isEqualTo("READY_FOR_HUMAN_GIT_REVIEW_PACKAGE");
        assertThat(response.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(response.selectedCandidateTraceId()).isEqualTo(traceId);
        assertThat(response.readyForHumanGitReview()).isTrue();
        assertThat(response.readyForFixtureCommit()).isFalse();
        assertThat(response.suggestedFixtureFilename()).isEqualTo("phase1-core-golden.reviewed-trace-fixture.json");
        assertThat(response.fixtureDirectory())
            .isEqualTo("src/main/resources/observability/reviewed-trace-fixtures");
        assertThat(response.candidateFixtureDraft())
            .containsEntry("traceId", traceId)
            .containsEntry("requiresHumanGitReviewBeforeCommit", true)
            .containsEntry("readyForManifestQualityGateNow", false);
        assertThat(response.manualReviewFields()).extracting(field -> field.get("name"))
            .containsExactly("sourceCommitSha", "reviewer", "reviewTimestamp", "evidenceDigest");
        assertThat(response.manualReviewFields())
            .allSatisfy(field -> assertThat(field)
                .containsEntry("source", "human-git-review")
                .containsEntry("runtimeCallable", false)
                .containsEntry("callerSuppliedAuthorityAccepted", false));
        assertThat(response.reviewChecklist())
            .contains(
                "copy-candidate-fixture-draft-outside-runtime",
                "run-reviewed-fixture-human-review-gate-before-commit",
                "commit-reviewed-fixture-json-through-human-git-review-only"
            );
        assertThat(response.manifestQualityGatePreview())
            .containsEntry("candidateReadyForHumanGitReview", true)
            .containsEntry("candidateReadyForManifestQualityGateNow", false)
            .containsEntry("qualityGateStatusGrantedNow", false)
            .containsEntry("catalogMutationAllowed", false);
        assertThat(response.candidateWorkbench().candidatePreview().candidateStatus())
            .isEqualTo("READY_FOR_HUMAN_FIXTURE_REVIEW");
        assertThat(response.packagePolicy())
            .containsEntry("humanReviewPackageOnly", true)
            .containsEntry("requestTraceIdsAccepted", false)
            .containsEntry("createsFixtureFile", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("releaseAuthority", false);
        assertThat(response.endpointMap())
            .containsEntry("humanReviewGate",
                "/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/reviewed-fixture-human-review-gate");
        assertThat(response.safety())
            .containsEntry("callerTraceIdsAccepted", false)
            .containsEntry("createsFixtureFile", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("ciBlockingEnabled", false);
        assertThat(response.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
            .contains("manualReviewFields", "manifestQualityGatePreview", "READY_FOR_HUMAN_GIT_REVIEW_PACKAGE")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("\"reports\"", "\"steps\"", "\"fixtureRows\"");
    }

    @Test
    void packageForTraceSet_shouldFailClosedWhenAutoWorkbenchHasNoCandidate() throws Exception {
        AgentReviewedTraceFixtureHumanReviewPackageService service = service(new InMemoryAgentAuditRecorder());

        AgentReviewedTraceFixtureHumanReviewPackageResponse response = service
            .packageForTraceSet("phase1-core-golden", 10_000)
            .orElseThrow();

        assertThat(response.packageStatus()).isEqualTo("HUMAN_REVIEW_PACKAGE_BLOCKED_BY_CANDIDATE_EVIDENCE");
        assertThat(response.selectedCandidateTraceId()).isBlank();
        assertThat(response.readyForHumanGitReview()).isFalse();
        assertThat(response.readyForFixtureCommit()).isFalse();
        assertThat(response.blockingReasons())
            .contains("no-recommended-redacted-trace-candidate", "candidate-trace-id-missing-or-invalid");
        assertThat(response.nextActions())
            .contains("capture-real-redacted-audit-evidence", "keep-runtime-fixture-upload-and-catalog-write-disabled");
        assertThat(response.manifestQualityGatePreview())
            .containsEntry("candidateReadyForHumanGitReview", false)
            .containsEntry("readyForFixtureCommit", false);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
            .doesNotContain("secret-token-value", "\"reports\"", "\"steps\"", "\"fixtureRows\"");
    }

    @Test
    void packageForTraceSet_shouldRejectUnknownTraceSet() {
        AgentReviewedTraceFixtureHumanReviewPackageService service = service(new InMemoryAgentAuditRecorder());

        assertThat(service.packageForTraceSet("missing-trace-set", 50)).isEmpty();
    }

    private static AgentReviewedTraceFixtureHumanReviewPackageService service(InMemoryAgentAuditRecorder recorder) {
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
        return new AgentReviewedTraceFixtureHumanReviewPackageService(workbenchService);
    }

    private static AgentAuditEvent event(String auditId,
                                         String traceId,
                                         AgentAuditOutcome outcome,
                                         AtlasToolMapping.OperationType operationType,
                                         boolean requiresConfirmation,
                                         boolean executed,
                                         boolean success) {
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
            operationType,
            requiresConfirmation,
            outcome,
            executed,
            success,
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
