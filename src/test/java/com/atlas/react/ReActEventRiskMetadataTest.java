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
}
