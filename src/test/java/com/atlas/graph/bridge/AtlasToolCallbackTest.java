package com.atlas.graph.bridge;

import com.atlas.hitl.HitlGuard;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterNormalizer;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.core.ToolRegistry;
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
        AtlasToolCallback callback = new AtlasToolCallback(
            tool,
            objectMapper,
            new ToolParameterNormalizer(),
            new HitlGuard(),
            safeReadMetadata(tool)
        );

        String output = callback.call("{\"pod_name\":\"nginx-callback\",\"ns\":\"default\"}");
        JsonNode jsonNode = objectMapper.readTree(output);

        assertTrue(jsonNode.get("success").asBoolean());
        assertEquals("nginx-callback", tool.lastParams.get("podName"));
        assertEquals("default", tool.lastParams.get("namespace"));
        assertEquals("nginx-callback", tool.lastParams.get("pod_name"), "原始 alias 字段应保留");
    }

    /**
     * 构造测试专用的安全 READ 元数据。
     *
     * <p>M5.13 之后 HITL 守卫采用 fail-closed 策略：缺少风险元数据的 Tool 会被视为高风险并拒绝执行。
     * 本用例关注的是 ToolCallback 参数归一化是否会真正传入 BaseTool，因此必须显式声明该测试 Tool
     * 是无确认要求的只读查询，既保留生产 fail-closed 安全边界，又让测试契约表达清楚。</p>
     */
    private ToolRegistry.ToolMetadata safeReadMetadata(RecordingTool tool) {
        return new ToolRegistry.ToolMetadata(
            tool.getToolName(),
            tool.getDescription(),
            tool.getToolName(),
            "query",
            tool,
            ToolPermission.Policy.PUBLIC,
            Set.of(),
            false,
            "GET",
            new String[]{"/api/test/recording"},
            AtlasToolMapping.OperationType.READ,
            false
        );
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
