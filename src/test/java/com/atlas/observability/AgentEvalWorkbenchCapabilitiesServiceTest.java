package com.atlas.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eval workbench capability manifest contract tests.
 *
 * <p>中文说明：这些测试保护前端能力目录的治理契约。能力可以让管理员发现 review-only 入口，
 * 但不能把目录本身变成 Tool/MCP/kube-manager 执行入口，也不能授予 fixture 上传或 catalog 写入能力。</p>
 */
class AgentEvalWorkbenchCapabilitiesServiceTest {

    @Test
    void capabilities_shouldDescribeFrontendWorkbenchFlowWithoutExecutionAuthority() {
        AgentEvalWorkbenchCapabilitiesService service = new AgentEvalWorkbenchCapabilitiesService();

        AgentEvalWorkbenchCapabilitiesResponse response = service.capabilities();

        assertThat(response.schemaVersion()).isEqualTo("agent-eval-workbench-capabilities.v1");
        assertThat(response.evaluationVersion()).isEqualTo("deterministic-replay-eval.v1");
        assertThat(response.capabilityCount()).isEqualTo(response.capabilities().size());
        assertThat(response.capabilities()).extracting(AgentEvalWorkbenchCapability::id)
            .containsExactly(
                "workbench-overview",
                "workbench-reviewed-fixture-vue-binding-spec",
                "trace-set-catalog",
                "workbench-trace-set-detail",
                "workbench-promotion-workflow",
                "workbench-catalog-patch-review",
                "workbench-reviewed-fixture-candidate-autopreview",
                "workbench-reviewed-fixture-human-review-package",
                "workbench-reviewed-fixture-human-review-gate",
                "workbench-reviewed-fixture-candidate",
                "workbench-gate-bundle-summary",
                "reviewed-trace-evidence",
                "release-blocking-gate-contract",
                "memory-rag-eval-suite-binding-contract",
                "trace-set-candidate-discovery",
                "trace-set-curation-review",
                "trace-set-catalog-patch-proposal",
                "trace-set-promotion-workflow",
                "trace-set-gate-bundle",
                "trace-replay-timeline",
                "trace-eval-report"
            );
        assertThat(response.capabilities()).allSatisfy(capability -> {
            assertThat(capability.adminOnly()).isTrue();
            assertThat(capability.readOnly()).isTrue();
            assertThat(capability.mutatesCatalog()).isFalse();
            assertThat(capability.toolExecution()).isFalse();
            assertThat(capability.kubeManagerCalls()).isFalse();
            assertThat(capability.policy())
                .containsEntry("runtimeCatalogWrite", false)
                .containsEntry("createsFixtureFile", false)
                .containsEntry("fixtureUploadAccepted", false)
                .containsEntry("toolExecution", false)
                .containsEntry("kubeManagerCalls", false);
        });
        assertThat(response.capabilities())
            .filteredOn(capability -> "workbench-reviewed-fixture-vue-binding-spec".equals(capability.id()))
            .singleElement()
            .satisfies(capability -> {
                assertThat(capability.httpMethod()).isEqualTo("GET");
                assertThat(capability.pathTemplate())
                    .isEqualTo("/api/agent/observability/eval/workbench/reviewed-fixture-vue-binding-spec");
                assertThat(capability.responseSchema()).isEqualTo("agent-reviewed-trace-fixture-vue-binding-spec.v1");
                assertThat(capability.policy())
                    .containsEntry("runtimeCatalogWrite", false)
                    .containsEntry("createsFixtureFile", false)
                    .containsEntry("fixtureUploadAccepted", false)
                    .containsEntry("toolExecution", false)
                    .containsEntry("kubeManagerCalls", false);
            });
        assertThat(response.capabilities())
            .filteredOn(capability -> "workbench-reviewed-fixture-candidate".equals(capability.id()))
            .singleElement()
            .satisfies(capability -> {
                assertThat(capability.pathTemplate())
                    .isEqualTo("/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate");
                assertThat(capability.responseSchema()).isEqualTo("agent-reviewed-trace-fixture-candidate.v1");
                assertThat(capability.policy())
                    .containsEntry("requiresHumanFixtureReviewBeforeCommit", true)
                    .containsEntry("requiresGitReviewForPromotion", true)
                    .containsEntry("createsFixtureFile", false)
                    .containsEntry("fixtureUploadAccepted", false);
            });
        assertThat(response.capabilities())
            .filteredOn(capability -> "workbench-reviewed-fixture-candidate-autopreview".equals(capability.id()))
            .singleElement()
            .satisfies(capability -> {
                assertThat(capability.httpMethod()).isEqualTo("GET");
                assertThat(capability.pathTemplate())
                    .isEqualTo("/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-candidate-workbench?limit={limit}");
                assertThat(capability.responseSchema()).isEqualTo("agent-reviewed-trace-fixture-candidate-workbench.v1");
                assertThat(capability.policy())
                    .containsEntry("requiresHumanFixtureReviewBeforeCommit", true)
                    .containsEntry("requiresGitReviewForPromotion", true)
                    .containsEntry("createsFixtureFile", false)
                    .containsEntry("fixtureUploadAccepted", false);
            });
        assertThat(response.capabilities())
            .filteredOn(capability -> "workbench-reviewed-fixture-human-review-package".equals(capability.id()))
            .singleElement()
            .satisfies(capability -> {
                assertThat(capability.httpMethod()).isEqualTo("GET");
                assertThat(capability.pathTemplate())
                    .isEqualTo("/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-human-review-package?limit={limit}");
                assertThat(capability.responseSchema()).isEqualTo("agent-reviewed-trace-fixture-human-review-package.v1");
                assertThat(capability.policy())
                    .containsEntry("requiresHumanFixtureReviewBeforeCommit", true)
                    .containsEntry("requiresGitReviewForPromotion", true)
                    .containsEntry("createsFixtureFile", false)
                    .containsEntry("fixtureUploadAccepted", false);
            });
        assertThat(response.capabilities())
            .filteredOn(capability -> "workbench-reviewed-fixture-human-review-gate".equals(capability.id()))
            .singleElement()
            .satisfies(capability -> {
                assertThat(capability.httpMethod()).isEqualTo("POST");
                assertThat(capability.pathTemplate())
                    .isEqualTo("/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-human-review-gate");
                assertThat(capability.requestSchema()).isEqualTo("AgentReviewedTraceFixtureHumanReviewGateRequest");
                assertThat(capability.responseSchema()).isEqualTo("agent-reviewed-trace-fixture-human-review-gate.v1");
                assertThat(capability.policy())
                    .containsEntry("requiresHumanFixtureReviewBeforeCommit", true)
                    .containsEntry("requiresGitReviewForPromotion", true)
                    .containsEntry("createsFixtureFile", false)
                    .containsEntry("fixtureUploadAccepted", false);
            });
        assertThat(response.recommendedWorkflow())
            .containsExactly(
                "workbench-overview",
                "workbench-reviewed-fixture-vue-binding-spec",
                "trace-set-catalog",
                "workbench-trace-set-detail",
                "workbench-promotion-workflow",
                "workbench-catalog-patch-review",
                "workbench-reviewed-fixture-candidate-autopreview",
                "workbench-reviewed-fixture-human-review-package",
                "workbench-reviewed-fixture-human-review-gate",
                "workbench-reviewed-fixture-candidate",
                "workbench-gate-bundle-summary",
                "reviewed-trace-evidence",
                "release-blocking-gate-contract",
                "memory-rag-eval-suite-binding-contract",
                "trace-set-catalog-patch-proposal",
                "trace-set-gate-bundle",
                "trace-replay-timeline",
                "trace-eval-report"
            );
        assertThat(response.workbenchPolicy())
            .containsEntry("frontendTarget", "vue-kube-manager eval workbench")
            .containsEntry("catalogPromotionAuthority", "human Git review only")
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(response.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawEndpoints", false)
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("containsRawParameterValues", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(response.toString())
            .contains(
                "workbench-overview",
                "workbench-reviewed-fixture-vue-binding-spec",
                "workbench-trace-set-detail",
                "agent-eval-workbench-catalog-patch-review.v1",
                "agent-reviewed-trace-fixture-vue-binding-spec.v1",
                "agent-reviewed-trace-fixture-candidate-workbench.v1",
                "agent-reviewed-trace-fixture-human-review-package.v1",
                "agent-reviewed-trace-fixture-human-review-gate.v1",
                "agent-reviewed-trace-fixture-candidate.v1",
                "agent-eval-workbench-gate-bundle-summary.v1",
                "agent-reviewed-eval-trace-evidence.v1",
                "agent-release-blocking-eval-gate-contract.v1",
                "agent-memory-rag-eval-suite-binding-contract.v1",
                "agent-eval-workbench-promotion-workflow.v1"
            )
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }
}
