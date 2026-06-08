package com.atlas.audit;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.execution.SafeToolExecutionSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JSONL durable audit sink contract tests.
 */
class JsonlAgentAuditDurableSinkTest {

    @TempDir
    Path tempDir;

    @Test
    void append_shouldWriteRedactedDurableJsonlRecord() throws Exception {
        Path auditFile = tempDir.resolve("audit").resolve("agent-audit.jsonl");
        AgentAuditProperties properties = enabledProperties(auditFile, true);
        JsonlAgentAuditDurableSink sink = new JsonlAgentAuditDurableSink(properties, new ObjectMapper());

        sink.append(sensitiveEvent());

        String jsonl = Files.readString(auditFile);
        AgentAuditDurabilityStatus status = sink.status();
        assertThat(status.enabled()).isTrue();
        assertThat(status.ready()).isTrue();
        assertThat(status.durableRetention()).isTrue();
        assertThat(status.failClosedForHighRisk()).isTrue();
        assertThat(status.acceptedRecords()).isEqualTo(1);
        assertThat(jsonl)
            .contains("\"schemaVersion\":\"agent-audit-durable.v1\"")
            .contains("\"auditId\":\"aud_0123456789abcdef0123456789abcdef\"")
            .contains("\"traceId\":\"trc_0123456789abcdef0123456789abcdef\"")
            .contains("\"toolName\":\"pod_query_tool\"")
            .contains("\"operationType\":\"SENSITIVE_READ\"")
            .contains("\"containsRawPrincipal\":false")
            .contains("\"containsRawReason\":false")
            .contains("\"containsRawParameterValues\":false")
            .doesNotContain("conv-secret", "user-secret", "org-secret", "secret-token-value", "/api/org-secret");
    }

    @Test
    void status_shouldBeReadyDisabledWhenDurableAuditIsOff() {
        AgentAuditProperties properties = enabledProperties(tempDir.resolve("off.jsonl"), false);
        JsonlAgentAuditDurableSink sink = new JsonlAgentAuditDurableSink(properties, new ObjectMapper());

        AgentAuditDurabilityStatus status = sink.status();

        assertThat(status.enabled()).isFalse();
        assertThat(status.ready()).isTrue();
        assertThat(status.durableRetention()).isFalse();
        assertThat(status.storageType()).isEqualTo("none");
    }

    private AgentAuditProperties enabledProperties(Path path, boolean enabled) {
        AgentAuditProperties properties = new AgentAuditProperties();
        properties.getDurable().setEnabled(enabled);
        properties.getDurable().setFailClosedForHighRisk(true);
        properties.getDurable().setPath(path.toString());
        return properties;
    }

    private AgentAuditEvent sensitiveEvent() {
        return new AgentAuditEvent(
            "aud_0123456789abcdef0123456789abcdef",
            Instant.parse("2026-06-09T00:00:00Z"),
            "trc_0123456789abcdef0123456789abcdef",
            "conv-secret",
            "user-secret",
            "org-secret",
            "pod_query",
            "pod_query_tool",
            SafeToolExecutionSource.REACT_ENGINE,
            "GET",
            List.of("/api/org-secret/pod?token=secret-token-value"),
            AtlasToolMapping.OperationType.SENSITIVE_READ,
            true,
            AgentAuditOutcome.BUSINESS_FAILURE,
            true,
            false,
            "business failed because token=secret-token-value",
            Map.of(
                "count", 2,
                "truncated", false,
                "keys", List.of(
                    Map.of("name", "namespace", "protected", false, "type", "string", "present", true),
                    Map.of("name", "token", "protected", true, "type", "string", "present", true)
                )
            )
        );
    }
}
