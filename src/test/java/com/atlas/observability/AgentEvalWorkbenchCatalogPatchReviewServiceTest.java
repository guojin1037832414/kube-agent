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
 * Eval workbench catalog patch review 契约测试。
 *
 * <p>中文说明：这些测试保护的是“前端可看、Git 可审、运行时不可写”的边界。catalog patch review
 * 可以汇总候选 traceId、gate 结果和 reviewed fixture manifest 摘要，但不能写 catalog、不能上传 fixture、
 * 不能调用 Tool/MCP/LLM/RAG/kube-manager，也不能泄露审计里的原始敏感值。</p>
 */
class AgentEvalWorkbenchCatalogPatchReviewServiceTest {

    @Test
    void review_shouldBuildGitReviewModelWithoutApplyingCatalogPatch() throws Exception {
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        String traceId = "trc_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        auditRecorder.record(new AgentAuditEvent(
            "aud_workbench_patch_review",
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
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(auditRecorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalWorkbenchCatalogPatchReviewService service =
            new AgentEvalWorkbenchCatalogPatchReviewService(traceSetCatalogService);

        Optional<AgentEvalWorkbenchCatalogPatchReviewResponse> response = service.review(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of(traceId, "secret-token-value"), null, null, null)
        );

        assertThat(response).isPresent();
        AgentEvalWorkbenchCatalogPatchReviewResponse review = response.get();
        assertThat(review.schemaVersion()).isEqualTo("agent-eval-workbench-catalog-patch-review.v1");
        assertThat(review.traceSetId()).isEqualTo("phase1-core-golden");
        assertThat(review.proposalVerdict()).isEqualTo("READY_FOR_GIT_REVIEW");
        assertThat(review.readyForGitReview()).isTrue();
        assertThat(review.candidateTraceIds()).containsExactly(traceId);
        assertThat(review.addedTraceIds()).containsExactly(traceId);
        assertThat(review.proposedTraceIds()).containsExactly(traceId);
        assertThat(review.patchOperations()).hasSize(1);
        assertThat(review.patchOperations().get(0))
            .containsEntry("op", "replace")
            .containsEntry("path", "/0/traceIds")
            .containsEntry("valueKind", "trace-id-list")
            .containsEntry("valueCount", 1)
            .containsEntry("applied", false)
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(review.traceDelta())
            .containsEntry("originalTraceSetTraceCount", 0)
            .containsEntry("candidateTraceCount", 1)
            .containsEntry("addedTraceCount", 1)
            .containsEntry("proposedTraceSetTraceCount", 1)
            .containsEntry("catalogMutated", false)
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(review.candidateGateSummary())
            .containsEntry("reviewVerdict", "READY_FOR_CATALOG_REVIEW")
            .containsEntry("gateVerdict", "PASS")
            .containsEntry("pass", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false);
        assertThat(review.reviewChecklist())
            .contains(
                "confirm-redacted-trace-anchors-only",
                "confirm-no-runtime-catalog-write",
                "submit-human-git-review",
                "regenerate-gate-bundle-after-merge"
            );
        assertThat(review.nextActions())
            .containsExactly(
                "copy-json-patch-into-git-review",
                "merge-reviewed-catalog-change",
                "regenerate-trace-set-gate-bundle"
            );
        assertThat(review.endpointTemplates())
            .containsEntry("workbenchCatalogPatchReview",
                "/api/agent/observability/eval/workbench/trace-sets/phase1-core-golden/catalog-patch-review")
            .containsEntry("workbenchGateBundleSummary",
                "/api/agent/observability/eval/workbench/gate-bundle-summary")
            .containsEntry("rawCatalogPatchProposal",
                "/api/agent/observability/eval/trace-sets/phase1-core-golden/catalog-patch-proposal");
        assertThat(review.workbenchPolicy())
            .containsEntry("catalogPatchReviewOnly", true)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("patchApplied", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(review.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawEndpoints", false)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(review.toString())
            .contains(traceId, "agent-eval-workbench-catalog-patch-review.v1")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("reports=", "replay=");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(review))
            .contains(traceId, "agent-eval-workbench-catalog-patch-review.v1")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("\"reports\"", "\"replay\"");
    }

    @Test
    void review_shouldExposeReviewedFixtureReadinessWithoutEmbeddingRawFixtureRows() throws Exception {
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        String traceId = "trc_cccccccccccccccccccccccccccccccc";
        auditRecorder.record(new AgentAuditEvent(
            "aud_workbench_patch_review_fixture",
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
        ObjectMapper objectMapper = new ObjectMapper();
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(auditRecorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, objectMapper);
        AgentReviewedTraceFixtureManifestService manifestService = new AgentReviewedTraceFixtureManifestService(
            traceSetCatalogService,
            objectMapper
        ) {
            /**
             * 中文说明：这里模拟 repo 中已经存在别的 trace set fixture，但当前 trace set 仍缺 fixture。
             * 测试只验证 review read model 的合并逻辑，不读写真实 classpath 文件。
             */
            @Override
            public AgentReviewedTraceFixtureManifestResponse manifest() {
                return AgentReviewedTraceFixtureManifestResponse.of(
                    Instant.parse("2026-06-24T13:00:00Z"),
                    traceSetCatalogService.catalog(),
                    List.of(Map.ofEntries(
                        Map.entry("traceId", "trc_11111111111111111111111111111111"),
                        Map.entry("traceSetId", "phase1-redaction-regression"),
                        Map.entry("suiteId", "release-gate-strict"),
                        Map.entry("replaySource", Map.of(
                            "type", "redacted-replay-timeline",
                            "digest", "sha256:redacted-replay",
                            "timelineStepCount", 3,
                            "redactedOnly", true
                        )),
                        Map.entry("redactionProof", Map.of(
                            "containsRawPrincipal", false,
                            "containsRawOrganization", false,
                            "containsRawConversation", false,
                            "containsRawEndpoints", false,
                            "containsRawReason", false,
                            "containsRawParameterValues", false
                        )),
                        Map.entry("deterministicEvalProof", Map.of(
                            "deterministic", true,
                            "llmUsed", false,
                            "externalCalls", false,
                            "toolExecution", false,
                            "mcpToolCall", false,
                            "kubeManagerCalls", false
                        )),
                        Map.entry("privacyProof", Map.of(
                            "containsAuthorizationHeader", false,
                            "containsToken", false,
                            "containsPassword", false,
                            "containsRawPrompt", false,
                            "containsRawDocument", false
                        )),
                        Map.entry("sourceCommitSha", "84732f0c"),
                        Map.entry("reviewer", "human-git-review"),
                        Map.entry("reviewTimestamp", "2026-06-24T13:00:00Z"),
                        Map.entry("evidenceDigest", "sha256:fixture-evidence"),
                        Map.entry("candidateGateSummary", Map.of("pass", true)),
                        Map.entry("forbiddenRuntimeClaims", List.of("runtimeCatalogWrite:false", "catalogMutationAllowed:false", "runtimeEvalAllowed:false", "ciBlockingEnabled:false", "releaseAuthority:false", "toolExecution:false", "mcpToolCall:false", "kubeManagerCalls:false", "auditWrite:false", "memoryWrite:false", "phase2Authority:false"))
                    )),
                    "classpath*:observability/reviewed-trace-fixtures/*.json"
                );
            }
        };
        AgentEvalWorkbenchCatalogPatchReviewService service =
            new AgentEvalWorkbenchCatalogPatchReviewService(traceSetCatalogService, manifestService);

        Optional<AgentEvalWorkbenchCatalogPatchReviewResponse> response = service.review(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of(traceId, "secret-token-value"), null, null, null)
        );

        assertThat(response).isPresent();
        AgentEvalWorkbenchCatalogPatchReviewResponse review = response.get();
        assertThat(review.readyForGitReview()).isTrue();
        assertThat(review.reviewedFixtureReadiness())
            .containsEntry("schemaVersion", "agent-reviewed-trace-fixture-manifest.v1")
            .containsEntry("manifestStatus", "REVIEWED_FIXTURES_PARTIAL")
            .containsEntry("currentTraceSetId", "phase1-core-golden")
            .containsEntry("currentTraceSetFixtureStatus", "MISSING_REVIEWED_FIXTURE_FILE")
            .containsEntry("currentTraceSetFixturePresent", false)
            .containsEntry("currentTraceSetQualityGateStatus", "MISSING_REVIEWED_FIXTURE_FILE")
            .containsEntry("currentTraceSetMatchingFixtureFileCount", 0)
            .containsEntry("currentTraceSetReadyFixtureFileCount", 0L)
            .containsEntry("currentTraceSetReworkFixtureFileCount", 0L)
            .containsEntry("currentTraceSetFailedQualityGates", List.of())
            .containsEntry("fixtureRowsEmbedded", false)
            .containsEntry("rawFixtureFieldsEmbedded", false)
            .containsEntry("requiredBeforeCatalogPatchMerge", true)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("fixtureUploadAccepted", false)
            .containsEntry("callerTraceIdsAccepted", false)
            .containsEntry("runtimeEvalAllowed", false)
            .containsEntry("releaseBlockingAllowedNow", false);
        assertThat((Map<String, Object>) review.reviewedFixtureReadiness().get("endpointMap"))
            .containsEntry("fixtureManifest", "/api/agent/observability/eval/reviewed-trace-fixture-manifest")
            .containsEntry("fixtureTemplate", "/api/agent/observability/eval/reviewed-trace-fixture-template")
            .containsEntry("fixtureIntakeContract", "/api/agent/observability/eval/reviewed-trace-fixture-intake-contract");
        assertThat(review.reviewChecklist())
            .contains(
                "confirm-reviewed-fixture-manifest-read-only",
                "prepare-reviewed-redacted-fixture-file-before-catalog-merge"
            );
        assertThat(review.nextActions().get(0))
            .isEqualTo("prepare-reviewed-redacted-fixture-file-before-catalog-merge");
        assertThat(review.toString())
            .contains("reviewedFixtureReadiness", "MISSING_REVIEWED_FIXTURE_FILE")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("fixtureRows=", "reports=", "replay=");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(review))
            .contains("reviewedFixtureReadiness", "MISSING_REVIEWED_FIXTURE_FILE")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive")
            .doesNotContain("\"fixtureRows\"", "\"reports\"", "\"replay\"");
    }

    @Test
    void review_shouldExposeCurrentTraceSetFixtureQualityFailuresWithoutRawRows() throws Exception {
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        String traceId = "trc_dddddddddddddddddddddddddddddddd";
        auditRecorder.record(new AgentAuditEvent(
            "aud_workbench_patch_review_bad_fixture",
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
        ObjectMapper objectMapper = new ObjectMapper();
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(auditRecorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, objectMapper);
        AgentReviewedTraceFixtureManifestService manifestService = new AgentReviewedTraceFixtureManifestService(
            traceSetCatalogService,
            objectMapper
        ) {
            /**
             * 中文说明：这里模拟当前 trace set 已有 fixture 文件，但文件质量门失败。
             * workbench 只能看到失败门名和计数，不能拿到 fixtureRows 原文或把失败文件当成 ready。
             */
            @Override
            public AgentReviewedTraceFixtureManifestResponse manifest() {
                return AgentReviewedTraceFixtureManifestResponse.of(
                    Instant.parse("2026-06-24T13:00:00Z"),
                    traceSetCatalogService.catalog(),
                    List.of(Map.ofEntries(
                        Map.entry("traceId", "trc_11111111111111111111111111111111"),
                        Map.entry("traceSetId", "phase1-core-golden"),
                        Map.entry("suiteId", "release-gate-strict"),
                        Map.entry("replaySource", Map.of("type", "redacted-replay-timeline")),
                        Map.entry("redactionProof", Map.of("containsRawPrincipal", false)),
                        Map.entry("deterministicEvalProof", Map.of("llmUsed", false)),
                        Map.entry("privacyProof", Map.of("containsToken", true, "containsPassword", false)),
                        Map.entry("sourceCommitSha", "84732f0c"),
                        Map.entry("reviewer", "human-git-review"),
                        Map.entry("reviewTimestamp", "2026-06-24T13:00:00Z"),
                        Map.entry("evidenceDigest", "fixture-evidence-without-sha-prefix"),
                        Map.entry("candidateGateSummary", Map.of("pass", true)),
                        Map.entry("forbiddenRuntimeClaims", List.of("runtimeCatalogWrite:false"))
                    )),
                    "classpath*:observability/reviewed-trace-fixtures/*.json"
                );
            }
        };
        AgentEvalWorkbenchCatalogPatchReviewService service =
            new AgentEvalWorkbenchCatalogPatchReviewService(traceSetCatalogService, manifestService);

        Optional<AgentEvalWorkbenchCatalogPatchReviewResponse> response = service.review(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of(traceId, "secret-token-value"), null, null, null)
        );

        assertThat(response).isPresent();
        AgentEvalWorkbenchCatalogPatchReviewResponse review = response.get();
        assertThat(review.reviewedFixtureReadiness())
            .containsEntry("manifestStatus", "REVIEWED_FIXTURES_PRESENT_BUT_NOT_READY")
            .containsEntry("currentTraceSetFixtureStatus", "MISSING_REVIEWED_FIXTURE_FILE")
            .containsEntry("currentTraceSetFixturePresent", false)
            .containsEntry("currentTraceSetQualityGateStatus", "FAIL")
            .containsEntry("currentTraceSetMatchingFixtureFileCount", 1)
            .containsEntry("currentTraceSetReadyFixtureFileCount", 0L)
            .containsEntry("currentTraceSetReworkFixtureFileCount", 1L)
            .containsEntry("fixtureRowsEmbedded", false)
            .containsEntry("rawFixtureFieldsEmbedded", false)
            .containsEntry("requiredBeforeCatalogPatchMerge", true)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("runtimeEvalAllowed", false);
        List<String> failedQualityGates = ((List<?>) review.reviewedFixtureReadiness()
            .get("currentTraceSetFailedQualityGates")).stream()
            .map(String::valueOf)
            .toList();
        assertThat(failedQualityGates)
            .contains(
                "replay-source-digest-missing",
                "privacy-proof-token-not-closed",
                "evidence-digest-sha256-missing",
                "forbidden-runtime-claims-missing-kubeManagerCalls-false"
            );
        assertThat(review.reviewChecklist())
            .contains("prepare-reviewed-redacted-fixture-file-before-catalog-merge", "confirm-no-runtime-catalog-write");
        assertThat(review.toString())
            .contains("currentTraceSetFailedQualityGates", "REVIEWED_FIXTURES_PRESENT_BUT_NOT_READY")
            .doesNotContain("fixtureRows=", "reports=", "replay=")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(review))
            .contains("currentTraceSetFailedQualityGates", "REVIEWED_FIXTURES_PRESENT_BUT_NOT_READY")
            .doesNotContain("\"fixtureRows\"", "\"reports\"", "\"replay\"")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive", "/api/org-sensitive");
    }

    @Test
    void review_shouldFailClosedWhenReadyAndReworkFixturesCoexistForCurrentTraceSet() throws Exception {
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        String candidateTraceId = "trc_eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
        auditRecorder.record(new AgentAuditEvent(
            "aud_workbench_patch_review_mixed_fixture",
            Instant.parse("2026-06-09T00:00:00Z"),
            candidateTraceId,
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
        ObjectMapper objectMapper = new ObjectMapper();
        AgentReplayTimelineService replayTimelineService = new AgentReplayTimelineService(auditRecorder);
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, objectMapper);
        AgentReviewedTraceFixtureManifestService manifestService = new AgentReviewedTraceFixtureManifestService(
            traceSetCatalogService,
            objectMapper
        ) {
            /**
             * 中文说明：同一个 trace set 同时出现合格和待返工 fixture 时，workbench 只能展示聚合质量门；
             * 不能因为有一行 ready 就忽略另一行失败，也不能把 fixtureRows、traceId 或 proof 原文嵌入响应。
             */
            @Override
            public AgentReviewedTraceFixtureManifestResponse manifest() {
                return AgentReviewedTraceFixtureManifestResponse.of(
                    Instant.parse("2026-06-24T13:00:00Z"),
                    traceSetCatalogService.catalog(),
                    List.of(
                        Map.ofEntries(
                            Map.entry("traceId", "trc_22222222222222222222222222222222"),
                            Map.entry("traceSetId", "phase1-core-golden"),
                            Map.entry("suiteId", "release-gate-strict"),
                            Map.entry("replaySource", Map.of(
                                "type", "redacted-replay-timeline",
                                "digest", "sha256:redacted-replay",
                                "timelineStepCount", 3,
                                "redactedOnly", true
                            )),
                            Map.entry("redactionProof", Map.of(
                                "containsRawPrincipal", false,
                                "containsRawOrganization", false,
                                "containsRawConversation", false,
                                "containsRawEndpoints", false,
                                "containsRawReason", false,
                                "containsRawParameterValues", false
                            )),
                            Map.entry("deterministicEvalProof", Map.of(
                                "deterministic", true,
                                "llmUsed", false,
                                "externalCalls", false,
                                "toolExecution", false,
                                "mcpToolCall", false,
                                "kubeManagerCalls", false
                            )),
                            Map.entry("privacyProof", Map.of(
                                "containsAuthorizationHeader", false,
                                "containsToken", false,
                                "containsPassword", false,
                                "containsRawPrompt", false,
                                "containsRawDocument", false
                            )),
                            Map.entry("sourceCommitSha", "84732f0c"),
                            Map.entry("reviewer", "human-git-review"),
                            Map.entry("reviewTimestamp", "2026-06-24T13:00:00Z"),
                            Map.entry("evidenceDigest", "sha256:fixture-evidence"),
                            Map.entry("candidateGateSummary", Map.of("pass", true)),
                            Map.entry("forbiddenRuntimeClaims", List.of(
                                "runtimeCatalogWrite:false",
                                "catalogMutationAllowed:false",
                                "runtimeEvalAllowed:false",
                                "ciBlockingEnabled:false",
                                "releaseAuthority:false",
                                "toolExecution:false",
                                "mcpToolCall:false",
                                "kubeManagerCalls:false",
                                "auditWrite:false",
                                "memoryWrite:false",
                                "phase2Authority:false"
                            ))
                        ),
                        Map.ofEntries(
                            Map.entry("traceId", "trc_33333333333333333333333333333333"),
                            Map.entry("traceSetId", "phase1-core-golden"),
                            Map.entry("suiteId", "release-gate-strict"),
                            Map.entry("replaySource", Map.of("type", "redacted-replay-timeline")),
                            Map.entry("redactionProof", Map.of("containsRawPrincipal", false)),
                            Map.entry("deterministicEvalProof", Map.of("llmUsed", false)),
                            Map.entry("privacyProof", Map.of("containsToken", true, "containsPassword", false)),
                            Map.entry("sourceCommitSha", "84732f0c"),
                            Map.entry("reviewer", "human-git-review"),
                            Map.entry("reviewTimestamp", "2026-06-24T13:00:00Z"),
                            Map.entry("evidenceDigest", "fixture-evidence-without-sha-prefix"),
                            Map.entry("candidateGateSummary", Map.of("pass", true)),
                            Map.entry("forbiddenRuntimeClaims", List.of("runtimeCatalogWrite:false"))
                        )
                    ),
                    "classpath*:observability/reviewed-trace-fixtures/*.json"
                );
            }
        };
        AgentEvalWorkbenchCatalogPatchReviewService service =
            new AgentEvalWorkbenchCatalogPatchReviewService(traceSetCatalogService, manifestService);

        Optional<AgentEvalWorkbenchCatalogPatchReviewResponse> response = service.review(
            "phase1-core-golden",
            new AgentEvalSuiteRequest(List.of(candidateTraceId, "secret-token-value"), null, null, null)
        );

        assertThat(response).isPresent();
        AgentEvalWorkbenchCatalogPatchReviewResponse review = response.get();
        assertThat(review.reviewedFixtureReadiness())
            .containsEntry("manifestStatus", "REVIEWED_FIXTURES_PRESENT_BUT_NOT_READY")
            .containsEntry("currentTraceSetFixturePresent", true)
            .containsEntry("currentTraceSetQualityGateStatus", "FAIL")
            .containsEntry("currentTraceSetMatchingFixtureFileCount", 2)
            .containsEntry("currentTraceSetReadyFixtureFileCount", 1L)
            .containsEntry("currentTraceSetReworkFixtureFileCount", 1L)
            .containsEntry("fixtureRowsEmbedded", false)
            .containsEntry("rawFixtureFieldsEmbedded", false)
            .containsEntry("requiredBeforeCatalogPatchMerge", true)
            .containsEntry("runtimeCatalogWrite", false);
        List<String> failedQualityGates = ((List<?>) review.reviewedFixtureReadiness()
            .get("currentTraceSetFailedQualityGates")).stream()
            .map(String::valueOf)
            .toList();
        assertThat(failedQualityGates)
            .contains("privacy-proof-token-not-closed", "evidence-digest-sha256-missing");
        assertThat(review.reviewChecklist())
            .contains("prepare-reviewed-redacted-fixture-file-before-catalog-merge")
            .doesNotContain("confirm-reviewed-fixture-manifest-row");
        assertThat(review.nextActions().get(0))
            .isEqualTo("prepare-reviewed-redacted-fixture-file-before-catalog-merge");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(review))
            .contains("currentTraceSetFailedQualityGates")
            .doesNotContain("\"fixtureRows\"", "\"reports\"", "\"replay\"")
            .doesNotContain(
                "trc_22222222222222222222222222222222",
                "trc_33333333333333333333333333333333",
                "secret-token-value",
                "conv-sensitive",
                "user-sensitive",
                "org-sensitive",
                "/api/org-sensitive"
            );
    }

    @Test
    void review_shouldRejectUnknownTraceSet() {
        AgentReplayTimelineService replayTimelineService =
            new AgentReplayTimelineService(new InMemoryAgentAuditRecorder());
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalWorkbenchCatalogPatchReviewService service =
            new AgentEvalWorkbenchCatalogPatchReviewService(traceSetCatalogService);

        assertThat(service.review("missing-trace-set", null)).isEmpty();
    }
}
