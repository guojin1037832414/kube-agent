package com.atlas.graph.bridge;

import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterNormalizer;
import com.atlas.tool.core.ToolParameterSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AtlasToolCallback 桥接层测试。
 *
 * <p>该测试锁定 Graph/ReactAgent 路径的两个关键契约：</p>
 * <ol>
 *   <li>ToolDefinition.inputSchema 使用 BaseTool 声明的 ToolParameterSpec；</li>
 *   <li>Tool 调用前经过 ToolParameterNormalizer，alias 参数会补齐为 canonical 参数。</li>
 * </ol>
 */
class AtlasToolCallbackTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void getToolDefinition_shouldExposeParameterSpecAsInputSchema() {
        RecordingTool tool = new RecordingTool();
        AtlasToolCallback callback = new AtlasToolCallback(tool, objectMapper, new ToolParameterNormalizer());

        String inputSchema = callback.getToolDefinition().inputSchema();

        assertTrue(inputSchema.contains("\"podName\""));
        assertTrue(inputSchema.contains("aliases: pod_name, name"));
        assertEquals("recording_tool", callback.getToolDefinition().name());
    }

    @Test
    void call_shouldNormalizeAliasBeforeExecutingBaseTool() throws Exception {
        RecordingTool tool = new RecordingTool();
        AtlasToolCallback callback = new AtlasToolCallback(tool, objectMapper, new ToolParameterNormalizer());

        String output = callback.call("{\"pod_name\":\"nginx-callback\",\"ns\":\"default\"}");
        JsonNode jsonNode = objectMapper.readTree(output);

        assertTrue(jsonNode.get("success").asBoolean());
        assertEquals("nginx-callback", tool.lastParams.get("podName"));
        assertEquals("default", tool.lastParams.get("namespace"));
        assertEquals("nginx-callback", tool.lastParams.get("pod_name"), "原始 alias 字段应保留");
    }

    /**
     * 测试专用 Tool：只记录最终收到的参数，不访问真实 kube-manager。
     */
    private static class RecordingTool extends BaseTool {

        private Map<String, Object> lastParams = new HashMap<>();

        RecordingTool() {
            super("recording_tool", "测试用记录工具");
        }

        @Override
        public List<ToolParameterSpec> getParameterSpecs() {
            return List.of(
                ToolParameterSpec.stringParam("podName", "Pod名称", false, List.of("pod_name", "name")),
                ToolParameterSpec.stringParam("namespace", "命名空间", false, List.of("ns"))
            );
        }

        @Override
        protected Set<String> getRequiredParams() {
            return Set.of();
        }

        @Override
        protected AtlasToolResult doExecute(Map<String, Object> params) {
            lastParams = new HashMap<>(params);
            return AtlasToolResult.ok("recorded", Map.of("received", lastParams));
        }
    }
}
