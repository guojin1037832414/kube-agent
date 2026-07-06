package com.atlas.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * reviewed fixture Vue 绑定规格测试。
 *
 * <p>中文说明：这些测试保护后端给 `vue-kube-manager` 的只读实现契约。前端可以根据该规格渲染
 * candidate workbench、人审包、人审 gate 和 readiness，但不能据此新增 fixture 上传、catalog 写入、
 * CI/release 按钮或 Tool/kube-manager 运行时动作。</p>
 */
class AgentReviewedTraceFixtureVueBindingSpecServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedTraceFixtureVueBindingSpecService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedTraceFixtureVueBindingSpecResponse.java"
    );

    @Test
    void spec_shouldPublishReviewedFixtureFrontendBindingWithoutRuntimeAuthority() throws Exception {
        AgentReplayTimelineService replayTimelineService =
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder());
        AgentEvalReportService evalReportService = new AgentEvalReportService(replayTimelineService);
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        AgentEvalTraceSetCatalogService traceSetCatalogService =
            new AgentEvalTraceSetCatalogService(suiteCatalogService, new ObjectMapper());
        AgentEvalWorkbenchCapabilitiesService capabilitiesService = new AgentEvalWorkbenchCapabilitiesService();
        AgentEvalWorkbenchOverviewService overviewService =
            new AgentEvalWorkbenchOverviewService(capabilitiesService, traceSetCatalogService);
        AgentReviewedTraceFixtureVueBindingSpecService service =
            new AgentReviewedTraceFixtureVueBindingSpecService(
                capabilitiesService,
                overviewService,
                Clock.fixed(Instant.parse("2026-07-07T02:00:00Z"), ZoneOffset.UTC)
            );

        AgentReviewedTraceFixtureVueBindingSpecResponse spec = service.spec();

        assertThat(spec.schemaVersion()).isEqualTo("agent-reviewed-trace-fixture-vue-binding-spec.v1");
        assertThat(spec.generatedAt()).isEqualTo(Instant.parse("2026-07-07T02:00:00Z"));
        assertThat(spec.bindingStatus()).isEqualTo("VUE_BINDING_SPEC_READY");
        assertThat(spec.frontendTarget()).isEqualTo("vue-kube-manager reviewed fixture eval workbench binding");
        assertThat(spec.sourceCapabilitiesEmbedded()).isTrue();
        assertThat(spec.sourceOverviewEmbedded()).isTrue();
        assertThat(spec.runtimeControlAllowed()).isFalse();
        assertThat(spec.componentSpecCount()).isEqualTo(8);
        assertThat(spec.fieldBindingCount()).isEqualTo(18);
        assertThat(spec.workflowStageCount()).isEqualTo(8);
        assertThat(spec.disabledActionBindingCount()).isEqualTo(11);
        assertThat(spec.fixtureCount()).isEqualTo(6);
        assertThat(spec.componentSpecs()).extracting(component -> component.get("name"))
            .containsExactly(
                "ReviewedFixtureWorkflowSummary",
                "ReviewedFixtureTraceSetTable",
                "CandidateWorkbenchPanel",
                "HumanReviewPackagePanel",
                "HumanReviewGatePanel",
                "ReviewedFixtureReadinessPanel",
                "DisabledRuntimeActionPanel",
                "ReviewedFixtureRawReadModelPanel"
            );
        assertThat(spec.componentSpecs()).allSatisfy(component -> assertThat(component)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("inlineEditAllowed", false)
            .containsEntry("writesBackendState", false));
        assertThat(spec.fieldBindings()).extracting(binding -> binding.get("fieldPath"))
            .contains(
                "sourceOverview.traceSets[].reviewedFixtureHumanReviewGatePath",
                "humanReviewPackage.manualReviewFields[].name",
                "humanReviewGate.expectedEvidenceDigest",
                "humanReviewGate.runtimeFixtureCommitAllowed",
                "catalogPatchReview.reviewedFixtureReadiness.currentTraceSetFailedQualityGates",
                "humanReviewGate.gatePolicy.runtimeCatalogWrite"
            );
        assertThat(spec.workflowStages()).extracting(stage -> stage.get("id"))
            .containsExactly(
                "capability-discovery",
                "candidate-workbench",
                "human-review-package",
                "human-review-gate",
                "manual-git-fixture-commit",
                "manifest-rescan",
                "catalog-patch-review",
                "release-review"
            );
        assertThat(spec.stateRenderingRules()).extracting(rule -> rule.get("status"))
            .contains(
                "VUE_BINDING_SPEC_READY",
                "READY_FOR_MANUAL_GIT_FIXTURE_COMMIT",
                "HUMAN_REVIEW_GATE_REWORK_REQUIRED",
                "QUALITY_GATE_STATUS_GRANTED_NOW_FALSE"
            );
        assertThat(spec.stateRenderingRules()).allSatisfy(rule -> assertThat(rule)
            .containsEntry("allowsRuntimeAction", false));
        assertThat(spec.disabledActionBindings()).extracting(action -> action.get("actionId"))
            .containsExactly(
                "create-fixture-json-from-browser",
                "upload-reviewed-fixture",
                "write-eval-trace-sets-json",
                "grant-quality-gate-status-now",
                "enable-ci-blocking",
                "approve-release-from-workbench",
                "call-mcp-tools-call",
                "call-kube-manager-write",
                "invoke-hitl-from-review-gate",
                "run-llm-eval",
                "execute-retrieval-or-vector-runtime"
            );
        assertThat(spec.disabledActionBindings()).allSatisfy(action -> assertThat(action)
            .containsEntry("buttonVisible", false)
            .containsEntry("clickHandlerAllowed", false)
            .containsEntry("runtimeControlAllowed", false));
        assertThat(spec.testFixtures()).extracting(fixture -> fixture.get("id"))
            .containsExactly(
                "overview-renders-gate-path",
                "package-renders-manual-fields",
                "gate-success-does-not-enable-runtime-write",
                "failed-gate-redacts-caller-input",
                "runtime-actions-absent",
                "raw-read-model-json-is-read-only"
            );
        assertThat(spec.testFixtures()).allSatisfy(fixture -> assertThat(fixture)
            .containsEntry("requiresMockedHttp", true)
            .containsEntry("requiresRuntimeBackendCalls", false)
            .containsEntry("requiresKubeManager8100", false));
        assertThat(spec.implementationChecklist())
            .contains(
                "render-gate-success-as-manual-git-signal-not-runtime-write",
                "hide-fixture-upload-catalog-write-ci-release-and-mcp-tools-call-buttons",
                "assert-no-token-password-raw-replay-report-fixtureRows-in-rendered-output"
            );
        assertThat(spec.endpointMap())
            .containsEntry("reviewedFixtureVueBindingSpec",
                "/api/agent/observability/eval/workbench/reviewed-fixture-vue-binding-spec")
            .containsEntry("humanReviewGate",
                "/api/agent/observability/eval/workbench/trace-sets/{traceSetId}/reviewed-fixture-human-review-gate");
        assertThat(spec.bindingPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("bindingSpecOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("fixtureUploadAccepted", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("ciBlockingEnabled", false)
            .containsEntry("releaseAuthority", false);
        assertThat(spec.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("bindingSpecOnly", true)
            .containsEntry("callerTraceIdsAccepted", false)
            .containsEntry("createsFixtureFile", false)
            .containsEntry("catalogMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("releaseAuthority", false);
        assertThat(spec.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(spec.sourceCapabilities().capabilities()).extracting(AgentEvalWorkbenchCapability::id)
            .contains("workbench-reviewed-fixture-vue-binding-spec",
                "workbench-reviewed-fixture-human-review-gate");
        assertThat(spec.sourceOverview().nextActions())
            .contains("open-reviewed-fixture-vue-binding-spec",
                "validate-reviewed-fixture-human-review-gate");
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(spec))
            .contains("ReviewedFixtureWorkflowSummary", "HumanReviewGatePanel",
                "gate-success-does-not-enable-runtime-write")
            .doesNotContain("secret-token-value", "conv-sensitive", "user-sensitive", "org-sensitive",
                "/api/org-sensitive", "\"fixtureRows\"", "\"reports\"", "\"steps\"");
    }

    @Test
    void source_shouldStayVueBindingSpecOnlyAndAvoidRuntimeExecution() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("capabilitiesService.capabilities()")
            .contains("overviewService.overview()")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("SafeToolExecutor.")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(responseSource)
            .contains("agent-reviewed-trace-fixture-vue-binding-spec")
            .contains("componentSpecs")
            .contains("disabledActionBindings")
            .contains("runtimeFixtureCommitAllowed")
            .doesNotContain("import org.springframework.ai")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("ToolRegistry")
            .doesNotContain("SafeToolExecutor.")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("record(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
    }
}
