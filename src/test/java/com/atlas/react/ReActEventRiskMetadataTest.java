package com.atlas.react;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M5.12 ReAct 事件风险元数据契约测试。
 *
 * <p>只验证事件对象构造和 metadata 透传，不启动 Spring 容器，不调用任何真实 Tool 或 kube-manager。</p>
 */
class ReActEventRiskMetadataTest {

    @Test
    void toolStart_shouldKeepParamsAndAttachRiskMetadata() {
        ReActEvent event = ReActEvent.toolStart(
            1,
            "image_delete",
            Map.of("imageId", "demo"),
            Map.of("operationType", "DELETE", "httpMethod", "DELETE", "requiresConfirmation", true)
        );

        assertEquals("tool_start", event.type());
        assertEquals("image_delete", event.tool());
        assertEquals("DELETE", event.metadata().get("operationType"));
        assertEquals("DELETE", event.metadata().get("httpMethod"));
        assertEquals(true, event.metadata().get("requiresConfirmation"));
        assertTrue(event.metadata().containsKey("params"));
    }

    @Test
    void toolStart_shouldAttachSensitiveReadRiskMetadataWithoutLeakingEndpoint() {
        ReActEvent event = ReActEvent.toolStart(
            1,
            "log_query",
            Map.of("podName", "demo-pod"),
            Map.of("operationType", "SENSITIVE_READ", "httpMethod", "GET", "requiresConfirmation", true)
        );

        assertEquals("tool_start", event.type());
        assertEquals("log_query", event.tool());
        assertEquals("SENSITIVE_READ", event.metadata().get("operationType"));
        assertEquals("GET", event.metadata().get("httpMethod"));
        assertEquals(true, event.metadata().get("requiresConfirmation"));
        assertFalse(event.metadata().containsKey("apiEndpoints"));
        assertFalse(event.metadata().toString().contains("/api/"));
    }

    @Test
    void toolDone_shouldKeepCostMsAndAttachRiskMetadata() {
        ReActEvent event = ReActEvent.toolDone(
            1,
            "mpi_job_submit",
            true,
            123L,
            Map.of("operationType", "ACTION", "httpMethod", "POST", "requiresConfirmation", true)
        );

        assertEquals("tool_done", event.type());
        assertEquals("mpi_job_submit", event.tool());
        assertEquals(123L, event.metadata().get("costMs"));
        assertEquals("ACTION", event.metadata().get("operationType"));
        assertEquals("POST", event.metadata().get("httpMethod"));
        assertEquals(true, event.metadata().get("requiresConfirmation"));
    }

    @Test
    void toolDone_shouldAttachCreateAndUpdateRiskMetadataWithoutLeakingEndpoint() {
        ReActEvent createEvent = ReActEvent.toolDone(
            1,
            "deploy_create_instance",
            false,
            88L,
            Map.of("operationType", "CREATE", "httpMethod", "POST", "requiresConfirmation", true)
        );
        ReActEvent updateEvent = ReActEvent.toolDone(
            2,
            "deployment_scale",
            false,
            99L,
            Map.of("operationType", "UPDATE", "httpMethod", "PATCH", "requiresConfirmation", true)
        );

        assertEquals("CREATE", createEvent.metadata().get("operationType"));
        assertEquals("POST", createEvent.metadata().get("httpMethod"));
        assertEquals(true, createEvent.metadata().get("requiresConfirmation"));
        assertFalse(createEvent.metadata().containsKey("apiEndpoints"));
        assertFalse(createEvent.metadata().toString().contains("/api/"));

        assertEquals("UPDATE", updateEvent.metadata().get("operationType"));
        assertEquals("PATCH", updateEvent.metadata().get("httpMethod"));
        assertEquals(true, updateEvent.metadata().get("requiresConfirmation"));
        assertFalse(updateEvent.metadata().containsKey("apiEndpoints"));
        assertFalse(updateEvent.metadata().toString().contains("/api/"));
    }
}
