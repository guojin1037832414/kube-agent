package com.atlas.observability;

import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Generates the machine-readable trace-set gate bundle uploaded by CI.
 */
class AgentEvalTraceSetGateBundleArtifactTest {

    private static final Path ARTIFACT_PATH = Path.of("target", "agent-eval", "trace-set-gate-bundle.json");

    @Test
    void shouldWriteCiTraceSetGateBundleArtifact() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        AgentEvalTraceSetCatalogService service = service(objectMapper);

        AgentEvalTraceSetGateBundleArtifact bundle = service.gateBundle(new AgentEvalSuiteRequest(
            List.of("trc_request_override_must_not_run"),
            null,
            null,
            null
        ));

        Files.createDirectories(ARTIFACT_PATH.getParent());
        objectMapper.writeValue(ARTIFACT_PATH.toFile(), bundle);

        assertThat(Files.exists(ARTIFACT_PATH)).isTrue();
        Map<String, Object> artifact = objectMapper.readValue(
            ARTIFACT_PATH.toFile(),
            new TypeReference<Map<String, Object>>() {
            }
        );
        assertThat(artifact)
            .containsEntry("schemaVersion", "agent-eval-trace-set-gate-bundle.v1")
            .containsEntry("gateVerdict", "FAIL")
            .containsEntry("pass", false)
            .containsEntry("releaseEligible", false)
            .containsEntry("traceSetCount", 4)
            .containsEntry("failedTraceSets", 4)
            .containsEntry("emptyTraceSets", 4);
        Map<String, Object> bundlePolicy = objectMapper.convertValue(
            artifact.get("bundlePolicy"),
            new TypeReference<Map<String, Object>>() {
            }
        );
        Map<String, Object> privacy = objectMapper.convertValue(
            artifact.get("privacy"),
            new TypeReference<Map<String, Object>>() {
            }
        );
        assertThat(bundlePolicy)
            .containsEntry("artifactOnly", true)
            .containsEntry("embeddedReports", false)
            .containsEntry("embeddedReplay", false)
            .containsEntry("ciArtifactPath", "target/agent-eval/trace-set-gate-bundle.json")
            .containsEntry("ciBlockingEnabled", false);
        assertThat(privacy)
            .containsEntry("redactedOnly", true)
            .containsEntry("llmUsed", false)
            .containsEntry("externalCalls", false)
            .containsEntry("toolExecution", false)
            .containsEntry("kubeManagerCalls", false);
        assertThat(Files.readString(ARTIFACT_PATH))
            .doesNotContain("trc_request_override_must_not_run")
            .doesNotContain("conv-sensitive", "user-sensitive", "org-sensitive", "secret-token-value", "/api/org-sensitive")
            .doesNotContain("\"reports\"", "\"replay\"");
    }

    private AgentEvalTraceSetCatalogService service(ObjectMapper objectMapper) {
        InMemoryAgentAuditRecorder recorder = new InMemoryAgentAuditRecorder();
        AgentEvalReportService evalReportService = new AgentEvalReportService(new AgentReplayTimelineService(recorder));
        AgentEvalSuiteCatalogService suiteCatalogService = new AgentEvalSuiteCatalogService(evalReportService);
        return new AgentEvalTraceSetCatalogService(suiteCatalogService, objectMapper);
    }
}
