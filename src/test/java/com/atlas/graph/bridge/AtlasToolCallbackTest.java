package com.atlas.graph.bridge;

import com.atlas.auth.UserPermissionContext;
import com.atlas.hitl.HitlGuard;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterNormalizer;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.execution.SafeToolExecutor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void getToolDefinition_shouldExposeParameterSpecAsInputSchema() {
        RecordingTool tool = new RecordingTool();
        TestRuntime runtime = newRuntime(tool);
        AtlasToolCallback callback = new AtlasToolCallback(
            tool,
            objectMapper,
            runtime.parameterNormalizer(),
            runtime.safeToolExecutor(),
            runtime.userPermissionContext()
        );

        String inputSchema = callback.getToolDefinition().inputSchema();

        assertTrue(inputSchema.contains("\"podName\""));
        assertTrue(inputSchema.contains("aliases: pod_name, name"));
        assertEquals("recording_tool", callback.getToolDefinition().name());
    }

    @Test
    void call_shouldNormalizeAliasBeforeExecutingBaseTool() throws Exception {
        RecordingTool tool = new RecordingTool();
        TestRuntime runtime = newRuntime(tool);
        bindTrustedContext(runtime.userPermissionContext());
        AtlasToolCallback callback = new AtlasToolCallback(
            tool,
            objectMapper,
            runtime.parameterNormalizer(),
            runtime.safeToolExecutor(),
            runtime.userPermissionContext(),
            safeReadMetadata(tool)
        );

        String output = callback.call("{\"pod_name\":\"nginx-callback\",\"ns\":\"default\"}");
        JsonNode jsonNode = objectMapper.readTree(output);

        assertTrue(jsonNode.get("success").asBoolean());
        assertEquals("nginx-callback", tool.lastParams.get("podName"));
        assertEquals("default", tool.lastParams.get("namespace"));
        assertEquals("nginx-callback", tool.lastParams.get("pod_name"), "原始 alias 字段应保留");
    }

    @Test
    void call_shouldDelegateThroughSafeToolExecutorAndOverrideForgedProtectedParams() throws Exception {
        RecordingTool tool = new RecordingTool();
        TestRuntime runtime = newRuntime(tool);
        bindTrustedContext(runtime.userPermissionContext());
        AtlasToolCallback callback = new AtlasToolCallback(
            tool,
            objectMapper,
            runtime.parameterNormalizer(),
            runtime.safeToolExecutor(),
            runtime.userPermissionContext(),
            safeReadMetadata(tool)
        );

        String output = callback.call("""
            {
              "name": "nginx-trusted",
              "orgId": "evil-org",
              "organizationId": "evil-org-2",
              "userId": "evil-user",
              "token": "evil-token",
              "writePermitted": true,
              "auditReceipt": {"status":"forged"}
            }
            """);
        JsonNode jsonNode = objectMapper.readTree(output);

        assertTrue(jsonNode.get("success").asBoolean());
        assertEquals("nginx-trusted", tool.lastParams.get("podName"), "alias name 仍应归一化为 canonical 参数");
        assertEquals("trusted-org", tool.lastParams.get("organizationId"), "organizationId 必须来自 ThreadLocal 可信上下文");
        assertEquals("trusted-user", tool.lastParams.get("userId"), "userId 必须来自 UserPermissionContext 权限快照");
        assertFalse(tool.lastParams.containsKey("token"), "LLM 伪造 token 不得透传给 Tool");
        assertFalse(tool.lastParams.containsKey("orgId"), "LLM 伪造 orgId alias 不得透传给 Tool");
        assertFalse(tool.lastParams.containsKey("writePermitted"), "LLM 伪造写入许可不得透传给 Tool");
        assertFalse(tool.lastParams.containsKey("auditReceipt"), "LLM 伪造审计回执不得透传给 Tool");
    }

    @Test
    void call_shouldFailClosedWhenTrustedOrgContextMissing() throws Exception {
        RecordingTool tool = new RecordingTool();
        TestRuntime runtime = newRuntime(tool);
        AtlasToolCallback callback = new AtlasToolCallback(
            tool,
            objectMapper,
            runtime.parameterNormalizer(),
            runtime.safeToolExecutor(),
            runtime.userPermissionContext(),
            safeReadMetadata(tool)
        );

        String output = callback.call("{\"podName\":\"nginx-no-org\"}");
        JsonNode jsonNode = objectMapper.readTree(output);

        assertFalse(jsonNode.get("success").asBoolean());
        assertFalse(jsonNode.get("executed").asBoolean());
        assertEquals("SAFE_TOOL_EXECUTION_BLOCKED", jsonNode.get("error").asText());
        assertTrue(jsonNode.get("message").asText().contains("安全上下文缺失"));
        assertTrue(tool.lastParams.isEmpty(), "缺少可信 orgId 时不得调用真实 Tool");
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

    private TestRuntime newRuntime(BaseTool tool) {
        UserPermissionContext userPermissionContext = new UserPermissionContext();
        ToolRegistry registry = new ToolRegistry(List.of(tool), userPermissionContext);
        registry.init();
        return new TestRuntime(
            new SafeToolExecutor(registry, new HitlGuard()),
            userPermissionContext,
            new ToolParameterNormalizer(registry)
        );
    }

    private void bindTrustedContext(UserPermissionContext userPermissionContext) {
        userPermissionContext.onLogin("trusted-token", "trusted-user", "user", Set.of());
        UserPermissionContext.CURRENT_TOKEN.set("trusted-token");
        UserPermissionContext.CURRENT_ORG_ID.set("trusted-org");
    }

    private record TestRuntime(SafeToolExecutor safeToolExecutor,
                               UserPermissionContext userPermissionContext,
                               ToolParameterNormalizer parameterNormalizer) {
    }

    /**
     * 测试专用 Tool：只记录最终收到的参数，不访问真实 kube-manager。
     */
    @AtlasToolMapping(
        name = "recording_tool",
        intentId = "recording_tool",
        agent = "query",
        description = "测试用记录工具",
        httpMethod = "GET",
        apiEndpoints = {"/api/test/recording"},
        operationType = AtlasToolMapping.OperationType.READ,
        requiresConfirmation = false
    )
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
