package com.atlas.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reviewed trace fixture manifest 测试。
 *
 * <p>中文说明：fixture manifest 是从“接入合同”走向“repo 内可审查文件”的桥。
 * 测试用内存 Resource 模拟已经提交到仓库的 fixture JSON，验证服务只汇总文件状态和缺口，
 * 不把 traceId 写回 catalog，也不把 fixture 当成 release/CI 权力。</p>
 *
 * <p>安全边界：测试不启动 Spring，不访问网络，不调用 Tool/MCP/LLM/RAG/kube-manager，
 * 不运行 eval/replay，不写 audit/memory，也不修改 `eval-trace-sets.json`。</p>
 */
class AgentReviewedTraceFixtureManifestServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedTraceFixtureManifestService.java"
    );
    private static final Path RESPONSE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentReviewedTraceFixtureManifestResponse.java"
    );

    @Test
    void manifest_shouldReportReviewedFixtureFilesWithoutPromotingCatalog() {
        ObjectMapper objectMapper = new ObjectMapper();
        AgentReviewedTraceFixtureManifestService service = new AgentReviewedTraceFixtureManifestService(
            traceSetCatalogService(objectMapper),
            objectMapper,
            new StubResourcePatternResolver(resource("phase1-core-golden.json", """
                {
                  "traceId": "trc_11111111111111111111111111111111",
                  "traceSetId": "phase1-core-golden",
                  "suiteId": "release-gate-strict",
                  "replaySource": {"type": "redacted-replay-timeline", "digest": "sha256:redacted-replay"},
                  "redactionProof": {"redactedOnly": true},
                  "deterministicEvalProof": {"llmUsed": false, "externalCalls": false},
                  "privacyProof": {"containsToken": false, "containsPassword": false},
                  "sourceCommitSha": "84732f0c",
                  "reviewer": "human-git-review",
                  "reviewTimestamp": "2026-06-24T13:00:00Z",
                  "evidenceDigest": "sha256:fixture-evidence",
                  "candidateGateSummary": {"pass": true},
                  "forbiddenRuntimeClaims": ["runtime-catalog-write:false", "ci-blocking:false"]
                }
                """)),
            Clock.fixed(Instant.parse("2026-06-24T13:00:00Z"), ZoneOffset.UTC)
        );

        AgentReviewedTraceFixtureManifestResponse manifest = service.manifest();

        assertThat(manifest.schemaVersion()).isEqualTo("agent-reviewed-trace-fixture-manifest.v1");
        assertThat(manifest.generatedAt()).isEqualTo(Instant.parse("2026-06-24T13:00:00Z"));
        assertThat(manifest.manifestStatus()).isEqualTo("REVIEWED_FIXTURES_PARTIAL");
        assertThat(manifest.phase1TopTierGoalPreserved()).isTrue();
        assertThat(manifest.runtimeIntakeAllowedNow()).isFalse();
        assertThat(manifest.fixtureUploadAccepted()).isFalse();
        assertThat(manifest.callerTraceIdsAccepted()).isFalse();
        assertThat(manifest.runtimeCatalogWrite()).isFalse();
        assertThat(manifest.catalogMutationAllowed()).isFalse();
        assertThat(manifest.releaseBlockingAllowedNow()).isFalse();
        assertThat(manifest.ciBlockingEnabled()).isFalse();
        assertThat(manifest.runtimeEvalAllowed()).isFalse();
        assertThat(manifest.traceSetCount()).isEqualTo(7);
        assertThat(manifest.fixtureFileCount()).isEqualTo(1);
        assertThat(manifest.matchedFixtureTraceSetCount()).isEqualTo(1);
        assertThat(manifest.missingFixtureTraceSetCount()).isEqualTo(6);
        assertThat(manifest.fixtureRows()).singleElement().satisfies(row -> assertThat(row)
            .containsEntry("fixtureResource", "phase1-core-golden.json")
            .containsEntry("traceSetId", "phase1-core-golden")
            .containsEntry("status", "READY_FOR_HUMAN_GIT_REVIEW")
            .containsEntry("knownTraceSet", true)
            .containsEntry("traceIdValid", true)
            .containsEntry("redactedOnly", true)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("catalogMutated", false)
            .containsEntry("runtimeEvalAllowed", false));
        assertThat(manifest.traceSetCoverage())
            .filteredOn(row -> "phase1-core-golden".equals(row.get("traceSetId")))
            .singleElement()
            .satisfies(row -> assertThat(row)
                .containsEntry("status", "REVIEWED_FIXTURE_PRESENT_AWAITING_CATALOG_PATCH")
                .containsEntry("reviewedFixtureFilePresent", true)
                .containsEntry("catalogTraceIdsPresent", false)
                .containsEntry("catalogMutationAllowed", false));
        assertThat(manifest.traceSetCoverage())
            .filteredOn(row -> "phase1-redaction-regression".equals(row.get("traceSetId")))
            .singleElement()
            .satisfies(row -> assertThat(row)
                .containsEntry("status", "MISSING_REVIEWED_FIXTURE_FILE")
                .containsEntry("reviewedFixtureFilePresent", false));
        assertThat(manifest.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("manifestOnly", true)
            .containsEntry("classpathScanOnly", true)
            .containsEntry("runtimeCatalogWrite", false)
            .containsEntry("evalTraceSetsJsonWrite", false)
            .containsEntry("toolExecution", false)
            .containsEntry("mcpToolCall", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(manifest.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("rawAuditExportAllowed", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsPassword", false);
        assertThat(manifest.toString())
            .contains("phase1-core-golden", "reviewed-trace-fixture-manifest")
            .doesNotContain("secret-token-value", "Bearer abc", "password:abc", "/api/login");
    }

    @Test
    void source_shouldKeepManifestReadOnlyAndRuntimeClosedMarkers() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("manifest-only / read-only / classpath-scan-only")
            .contains("不上传 fixture")
            .contains("不接收 caller")
            .contains("不修改")
            .contains("不运行 eval/replay")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("ChatClient")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("append(")
            .doesNotContain("writeValue");
        assertThat(responseSource)
            .contains("fixtureResourcePattern")
            .contains("runtimeIntakeAllowedNow")
            .contains("fixtureUploadAccepted")
            .contains("callerTraceIdsAccepted")
            .contains("runtimeCatalogWrite")
            .contains("nim-hpc-slurm-bcm-phase2-authority")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("@PostMapping")
            .doesNotContain("append(")
            .doesNotContain("writeValue");
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

    private static Resource resource(String filename, String body) {
        return new NamedByteArrayResource(filename, body.getBytes(StandardCharsets.UTF_8));
    }

    private record StubResourcePatternResolver(Resource... resources) implements ResourcePatternResolver {

        @Override
        public Resource[] getResources(String locationPattern) {
            return resources;
        }

        @Override
        public Resource getResource(String location) {
            return resources.length > 0 ? resources[0] : new NamedByteArrayResource("empty.json", new byte[0]);
        }

        @Override
        public ClassLoader getClassLoader() {
            return ResourceLoader.class.getClassLoader();
        }
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        NamedByteArrayResource(String filename, byte[] byteArray) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }
}
