package com.atlas.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviewed trace fixture 作者模板测试。
 *
 * <p>中文说明：这个测试保护的是“从缺口 manifest 走向真实 fixture 入仓”的功能入口。
 * 模板可以指导人审者写文件，但它本身不能制造 fixture、不能提交占位 traceId、不能把 catalog patch
 * 从人工 Git review 变成运行时写入。</p>
 *
 * <p>安全边界：测试不启动 Spring，不访问网络，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不运行 eval/replay，不写 audit/memory，也不创建真实 reviewed fixture JSON。</p>
 */
class AgentReviewedTraceFixtureTemplateServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedTraceFixtureTemplateService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedTraceFixtureTemplateResponse.java"
    );
    private static final Path FIXTURE_README = Path.of(
        "src/main/resources/observability/reviewed-trace-fixtures/README.md"
    );

    @Test
    void template_shouldPublishAuthoringSchemaWithoutCreatingFixtureAuthority() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentReviewedTraceFixtureTemplateService service = new AgentReviewedTraceFixtureTemplateService(
            traceSetCatalogService(objectMapper),
            Clock.fixed(Instant.parse("2026-06-24T14:00:00Z"), ZoneOffset.UTC)
        );

        AgentReviewedTraceFixtureTemplateResponse template = service.template();

        assertThat(template.schemaVersion()).isEqualTo("agent-reviewed-trace-fixture-template.v1");
        assertThat(template.generatedAt()).isEqualTo(Instant.parse("2026-06-24T14:00:00Z"));
        assertThat(template.templateStatus()).isEqualTo("TEMPLATE_READY_FOR_HUMAN_AUTHORED_FIXTURES");
        assertThat(template.fixtureDirectory()).isEqualTo("src/main/resources/observability/reviewed-trace-fixtures");
        assertThat(template.fixtureClasspathPattern()).isEqualTo("classpath*:observability/reviewed-trace-fixtures/*.json");
        assertThat(template.phase1TopTierGoalPreserved()).isTrue();
        assertThat(template.templateOnly()).isTrue();
        assertThat(template.createsFixtureFile()).isFalse();
        assertThat(template.placeholderTraceIdsAllowed()).isFalse();
        assertThat(template.runtimeIntakeAllowedNow()).isFalse();
        assertThat(template.fixtureUploadAccepted()).isFalse();
        assertThat(template.callerTraceIdsAccepted()).isFalse();
        assertThat(template.runtimeCatalogWrite()).isFalse();
        assertThat(template.catalogMutationAllowed()).isFalse();
        assertThat(template.releaseBlockingAllowedNow()).isFalse();
        assertThat(template.ciBlockingEnabled()).isFalse();
        assertThat(template.runtimeEvalAllowed()).isFalse();
        assertThat(template.traceSetCount()).isEqualTo(7);
        assertThat(template.requiredFields()).extracting(field -> field.get("name"))
            .contains("traceId", "traceSetId", "suiteId", "replaySource", "redactionProof",
                "deterministicEvalProof", "privacyProof", "sourceCommitSha", "reviewer",
                "reviewTimestamp", "evidenceDigest", "candidateGateSummary", "forbiddenRuntimeClaims");
        assertThat(template.structuredProofBlocks()).extracting(block -> block.get("name"))
            .contains("replaySource", "redactionProof", "deterministicEvalProof", "privacyProof",
                "candidateGateSummary", "forbiddenRuntimeClaims");

        Map<String, Object> schema = template.fixtureJsonSchema();
        assertThat(schema)
            .containsEntry("$id", "agent-reviewed-trace-fixture.v1")
            .containsEntry("type", "object")
            .containsEntry("additionalProperties", false)
            .containsEntry("templateOnly", true)
            .containsEntry("placeholderTraceIdsAllowed", false)
            .containsEntry("runtimeCatalogWrite", false);
        assertThat(String.valueOf(schema.get("required")))
            .contains("traceId", "traceSetId", "privacyProof", "evidenceDigest");
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        assertThat(properties.containsKey("traceId")).isTrue();
        assertThat(properties.containsKey("redactionProof")).isTrue();
        assertThat(properties.containsKey("forbiddenRuntimeClaims")).isTrue();

        assertThat(template.exampleFixtureSkeleton())
            .containsEntry("traceId", "<reviewed-w3c-trace-id>")
            .containsEntry("traceSetId", "<catalog-trace-set-id>");
        assertThat(template.exampleFixtureSkeleton().toString())
            .contains("runtimeCatalogWrite:false", "phase2Authority:false")
            .doesNotContain("trc_11111111111111111111111111111111", "secret-token-value");
        assertThat(template.traceSetTemplates())
            .filteredOn(row -> "phase1-core-golden".equals(row.get("traceSetId")))
            .singleElement()
            .satisfies(row -> assertThat(row)
                .containsEntry("suiteId", "release-gate-strict")
                .containsEntry("suggestedFilename", "phase1-core-golden.reviewed-trace-fixture.json")
                .containsEntry("templateStatus", "AWAITING_REAL_REVIEWED_REDACTED_TRACE")
                .containsEntry("templateOnly", true)
                .containsEntry("placeholderTraceIdsAllowed", false)
                .containsEntry("catalogMutationAllowed", false)
                .containsEntry("runtimeCatalogWrite", false)
                .containsEntry("requiresHumanGitReview", true));
        assertThat(template.fileNamingRules())
            .contains("do-not-commit-placeholder-template-json-to-the-scanned-fixture-directory");
        assertThat(template.authoringWorkflow())
            .contains("commit-fixture-json-through-human-git-review");
        assertThat(template.forbiddenShortcuts())
            .contains("placeholder-trace-id-commit", "fake-reviewed-fixture-file", "runtime-fixture-upload",
                "runtime-catalog-write", "kube-manager-call", "nim-hpc-slurm-bcm-phase2-authority");
        assertThat(template.endpointMap())
            .containsEntry("fixtureTemplate", "/api/agent/observability/eval/reviewed-trace-fixture-template")
            .containsEntry("fixtureManifest", "/api/agent/observability/eval/reviewed-trace-fixture-manifest");
        assertThat(template.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("templateOnly", true)
            .containsEntry("schemaOnly", true)
            .containsEntry("createsFixtureFile", false)
            .containsEntry("placeholderTraceIdsAllowed", false)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(template.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("rawAuditExportAllowed", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(template.toString())
            .contains("reviewed-trace-fixture-template", "phase1-core-golden")
            .doesNotContain("secret-token-value", "Bearer abc", "password:abc", "/api/login");
    }

    @Test
    void sourceAndFixtureReadme_shouldKeepTemplateOnlyRuntimeClosedMarkers() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE, StandardCharsets.UTF_8);
        String responseSource = Files.readString(RESPONSE_SOURCE, StandardCharsets.UTF_8);
        String readme = Files.readString(FIXTURE_README, StandardCharsets.UTF_8);

        assertThat(serviceSource)
            .contains("template-only / read-only / schema-only")
            .contains("不创建 fixture 文件")
            .contains("不接收 caller traceId")
            .contains("不运行 eval/replay")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("writeValue");
        assertThat(responseSource)
            .contains("placeholderTraceIdsAllowed")
            .contains("fake-reviewed-fixture-file")
            .contains("runtime-catalog-write")
            .contains("nim-hpc-slurm-bcm-phase2-authority")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("writeValue");
        assertThat(readme)
            .contains("不要把模板或占位 JSON 提交到本目录")
            .contains("不要提交 fake traceId")
            .contains("不授予 CI blocking")
            .contains("reviewed-trace-fixture-template")
            .doesNotContain("trc_11111111111111111111111111111111", "secret-token-value");
    }

    private static AgentEvalTraceSetCatalogService traceSetCatalogService(ObjectMapper objectMapper) {
        AgentEvalReportService reportService = new AgentEvalReportService(
            new AgentReplayTimelineService(new com.atlas.audit.InMemoryAgentAuditRecorder())
        );
        return new AgentEvalTraceSetCatalogService(
            new AgentEvalSuiteCatalogService(reportService),
            objectMapper
        );
    }
}
