package com.atlas.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Eval workbench capability manifest contract tests.
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
                "trace-set-catalog",
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
                .containsEntry("toolExecution", false)
                .containsEntry("kubeManagerCalls", false);
        });
        assertThat(response.recommendedWorkflow())
            .containsExactly(
                "trace-set-catalog",
                "trace-set-promotion-workflow",
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
            .containsEntry("containsRawKubeManagerEndpoints", false)
            .containsEntry("containsRawParameterValues", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(response.toString())
            .contains("promotion-workflow", "agent-eval-trace-set-promotion-workflow.v1")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive");
    }
}
