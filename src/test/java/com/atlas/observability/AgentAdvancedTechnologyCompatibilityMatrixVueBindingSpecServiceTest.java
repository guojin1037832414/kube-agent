package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Advanced technology compatibility matrix Vue binding-spec contract tests.
 */
class AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse.java"
    );

    @Test
    void spec_shouldPublishVueComponentFieldFixtureContractWithoutRuntimeAuthority() {
        AgentOfficialVersionProtocolWatchService watchService = new AgentOfficialVersionProtocolWatchService(
            Clock.fixed(Instant.parse("2026-06-10T01:00:00Z"), ZoneOffset.UTC)
        );
        AgentAdvancedTechnologyCompatibilityMatrixService matrixService =
            new AgentAdvancedTechnologyCompatibilityMatrixService(
                watchService,
                Clock.fixed(Instant.parse("2026-06-10T01:30:00Z"), ZoneOffset.UTC)
            );
        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService service =
            new AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecService(
                matrixService,
                Clock.fixed(Instant.parse("2026-06-10T02:00:00Z"), ZoneOffset.UTC)
            );

        AgentAdvancedTechnologyCompatibilityMatrixVueBindingSpecResponse spec = service.spec();

        assertThat(spec.schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix-vue-binding-spec.v1");
        assertThat(spec.generatedAt()).isEqualTo(Instant.parse("2026-06-10T02:00:00Z"));
        assertThat(spec.bindingStatus()).isEqualTo("VUE_BINDING_SPEC_READY");
        assertThat(spec.frontendTarget())
            .isEqualTo("vue-kube-manager advanced Agent technology compatibility matrix binding");
        assertThat(spec.sourceMatrixEmbedded()).isTrue();
        assertThat(spec.runtimeControlAllowed()).isFalse();
        assertThat(spec.componentSpecCount()).isEqualTo(8);
        assertThat(spec.fieldBindingCount()).isEqualTo(14);
        assertThat(spec.tableColumnGroupCount()).isEqualTo(5);
        assertThat(spec.disabledActionBindingCount()).isEqualTo(7);
        assertThat(spec.fixtureCount()).isEqualTo(5);
        assertThat(spec.componentSpecs()).extracting(component -> component.get("name"))
            .containsExactly(
                "AdvancedTechnologyMatrixSummaryStrip",
                "SourceBaselineTable",
                "CandidateUpgradeLaneMatrix",
                "MigrationGateChecklist",
                "BlockedUpgradeShortcutTable",
                "CompatibilityTestLaneBoard",
                "MatrixImplementationChecklistPanel",
                "CompatibilityMatrixSourceJsonPanel"
            );
        assertThat(spec.componentSpecs()).allSatisfy(component -> assertThat(component)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("inlineEditAllowed", false));
        assertThat(spec.fieldBindings()).extracting(binding -> binding.get("fieldPath"))
            .contains(
                "matrixStatus",
                "sourceBaselines[].officialUrl",
                "matrixItems[].readiness",
                "matrixItems[].mainlineAllowedNow",
                "blockedUpgradeShortcuts[].allowed",
                "testLanes[].status",
                "safety.runtimeControlAllowed"
            );
        assertThat(spec.tableColumnGroups()).extracting(group -> group.get("dataField"))
            .containsExactly(
                "sourceBaselines",
                "matrixItems",
                "migrationGates",
                "blockedUpgradeShortcuts",
                "testLanes"
            );
        assertThat(spec.stateRenderingRules()).extracting(rule -> rule.get("status"))
            .contains("MATRIX_DEFINED_NOT_EXECUTED", "COMPATIBILITY_REQUIRED", "CONTRACT_FIRST",
                "RELEASE_GATED", "EVIDENCE_BLOCKED", "WRITE_AUTHORITY_CLOSED", "QUALITY_GATE_REQUIRED",
                "BLOCKED_SHORTCUT");
        assertThat(spec.stateRenderingRules()).allSatisfy(rule -> assertThat(rule)
            .containsEntry("allowsRuntimeAction", false));
        assertThat(spec.disabledActionBindings()).extracting(binding -> binding.get("actionId"))
            .containsExactly(
                "upgrade-pom-from-readiness-page",
                "treat-rc-preview-as-mainline",
                "trust-mcp-tool-annotations",
                "delegate-authority-to-external-agent",
                "enable-retrieval-before-reviewed-traces",
                "use-otel-experimental-fields-as-contract",
                "enable-ci-blocking-with-empty-fixtures"
            );
        assertThat(spec.disabledActionBindings()).allSatisfy(binding -> assertThat(binding)
            .containsEntry("renderAs", "disabled-row")
            .containsEntry("buttonVisible", false)
            .containsEntry("clickHandlerAllowed", false)
            .containsEntry("requiresSeparateReviewedSlice", true)
            .containsEntry("blocksTopTierClaim", true));
        assertThat(spec.testFixtures()).extracting(fixture -> fixture.get("id"))
            .containsExactly(
                "happy-path-matrix",
                "major-upgrade-lanes-visible",
                "runtime-buttons-absent",
                "blocked-shortcuts-visible",
                "source-watch-drilldown"
            );
        assertThat(spec.testFixtures()).allSatisfy(fixture -> assertThat(fixture)
            .containsEntry("requiresMockedHttp", true)
            .containsEntry("requiresRuntimeBackendCalls", false)
            .containsEntry("requiresKubeManager8100", false));
        assertThat(spec.implementationChecklist()).contains(
            "fetch-matrix-endpoint-with-admin-session",
            "render-candidate-upgrade-lanes-with-evidence-tags",
            "render-test-lanes-without-start-buttons",
            "hide-all-runtime-and-dependency-upgrade-buttons"
        );
        assertThat(spec.endpointMap())
            .containsEntry("advancedTechnologyCompatibilityMatrixVueBindingSpec",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/vue-binding-spec")
            .containsEntry("advancedTechnologyCompatibilityMatrix",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix")
            .containsEntry("advancedTechnologyCompatibilityMatrixEvidenceReadiness",
                "/api/agent/observability/top-tier/advanced-technology-compatibility-matrix/evidence-readiness")
            .containsEntry("officialVersionProtocolWatch",
                "/api/agent/observability/top-tier/official-version-protocol-watch")
            .containsEntry("topTierTechnologyIntroductionPlaybook",
                "/api/agent/observability/top-tier/technology-introduction-playbook");
        assertThat(spec.bindingPolicy())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("bindingSpecOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("sourceMatrixEmbedded", true)
            .containsEntry("componentSpecCount", 8)
            .containsEntry("fieldBindingCount", 14)
            .containsEntry("tableColumnGroupCount", 5)
            .containsEntry("disabledActionBindingCount", 7)
            .containsEntry("fixtureCount", 5)
            .containsEntry("runtimeControlAllowed", false)
            .containsEntry("runtimeButtonsAllowed", false)
            .containsEntry("dependencyUpgradeButtonsAllowed", false)
            .containsEntry("inlineEditAllowed", false)
            .containsEntry("mockedHttpFixturesRequired", true);
        assertThat(spec.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("bindingSpecOnly", true)
            .containsEntry("vueWorkbenchOnly", true)
            .containsEntry("sourceMatrixReadOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("runtimeUpgradeAllowedNow", false)
            .containsEntry("dependencyUpgradeAllowedNow", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvocation", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("mcpToolsCall", false)
            .containsEntry("a2aRuntimeHandoff", false)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("auditWrite", false)
            .containsEntry("memoryWrite", false)
            .containsEntry("retrievalExecuted", false)
            .containsEntry("vectorStoreCalls", false)
            .containsEntry("embeddingModelCalls", false)
            .containsEntry("rerankerCalls", false)
            .containsEntry("ciBlockingChanged", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(spec.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawPrincipal", false)
            .containsEntry("containsRawPrompt", false)
            .containsEntry("containsRawDocument", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(spec.sourceMatrix().schemaVersion())
            .isEqualTo("agent-advanced-technology-compatibility-matrix.v1");
        assertThat(spec.toString())
            .contains("CandidateUpgradeLaneMatrix", "major-upgrade-lanes-visible",
                "upgrade-pom-from-readiness-page")
            .doesNotContain("secret-value", "Bearer abc", "password:abc", "token=secret");
    }

    @Test
    void source_shouldStayBindingSpecOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("matrixService.matrix()")
            .doesNotContain("ChatClient")
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
            .contains("advanced-technology-compatibility-matrix-vue-binding-spec")
            .contains("componentSpecs")
            .contains("fieldBindings")
            .contains("testFixtures")
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
