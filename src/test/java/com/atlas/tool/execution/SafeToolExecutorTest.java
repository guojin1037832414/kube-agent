package com.atlas.tool.execution;

import com.atlas.auth.UserPermissionContext;
import com.atlas.hitl.HitlConfirmation;
import com.atlas.hitl.HitlGuard;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SafeToolExecutor 安全执行契约测试。
 *
 * <p>M4-PX.3-A 将 Graph tool_call 的内联执行链下沉到 {@link SafeToolExecutor}。
 * 本测试只使用内存假 Tool，不访问真实 kube-manager，重点验证统一执行入口仍保持
 * M5 安全语义：只读可执行、高危无确认 fail-closed、不可信上下文参数被过滤、
 * ThreadLocal 在执行后恢复。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M4-PX.3
 */
class SafeToolExecutorTest {

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void executeIntent_shouldRunPlainReadToolAndReturnGraphCompatibleResult() {
        String previousToken = UserPermissionContext.CURRENT_TOKEN.get();
        String previousOrgId = UserPermissionContext.getCurrentOrgId();
        RecordingReadTool readTool = new RecordingReadTool();
        SafeToolExecutor executor = newExecutor(readTool);

        SafeToolExecutionResult result = executor.executeIntent(new SafeToolExecutionRequest(
            "test.read",
            Map.of("keyword", "gpu"),
            "user-A",
            "token-A",
            "100002",
            "conv-A",
            null,
            SafeToolExecutionSource.GRAPH_TOOL_CALL
        ));

        assertTrue(result.executed(), "普通 READ 工具应被执行");
        assertTrue(result.success(), "测试 READ 工具应返回成功");
        assertTrue(result.answer().contains("读取成功"), "answer 应保持旧 tool_call 摘要格式");
        assertEquals("test.read", result.toolResult().get("tool"));
        assertEquals("gpu", readTool.lastParams.get("keyword"));
        assertEquals("user-A", readTool.lastParams.get("userId"));
        assertEquals("100002", readTool.lastParams.get("organizationId"));
        assertEquals("conv-A", readTool.lastParams.get("conversationId"));
        assertEquals(previousToken, UserPermissionContext.CURRENT_TOKEN.get(),
            "执行完成后 token ThreadLocal 必须恢复为执行前快照");
        assertEquals(previousOrgId, UserPermissionContext.getCurrentOrgId(),
            "执行完成后 orgId ThreadLocal 必须恢复为执行前快照");
    }

    @Test
    void executeIntent_shouldFilterForgedProtectedContextParams() {
        RecordingReadTool readTool = new RecordingReadTool();
        SafeToolExecutor executor = newExecutor(readTool);

        executor.executeIntent(new SafeToolExecutionRequest(
            "test.read",
            Map.of(
                "keyword", "node",
                "organizationId", "evil-org",
                "orgId", "evil-org-2",
                "userId", "evil-user",
                "token", "evil-token"
            ),
            "trusted-user",
            "trusted-token",
            "trusted-org",
            "conv-B",
            null,
            SafeToolExecutionSource.GRAPH_TOOL_CALL
        ));

        assertEquals("trusted-org", readTool.lastParams.get("organizationId"),
            "organizationId 必须来自服务端可信上下文，而不是 LLM/Plan 参数");
        assertEquals("trusted-user", readTool.lastParams.get("userId"),
            "userId 必须来自服务端可信上下文，而不是 LLM/Plan 参数");
        assertFalse(readTool.lastParams.containsKey("token"), "token 不应透传到业务 Tool 参数");
        assertFalse(readTool.lastParams.containsKey("orgId"), "orgId alias 不应透传到业务 Tool 参数");
    }

    @Test
    void executeIntent_shouldBlockHighRiskToolWithoutServerConfirmation() {
        RecordingDeleteTool deleteTool = new RecordingDeleteTool();
        SafeToolExecutor executor = newExecutor(deleteTool);

        SafeToolExecutionResult result = executor.executeIntent(new SafeToolExecutionRequest(
            "test.delete",
            Map.of("name", "danger"),
            "user-A",
            "token-A",
            "100002",
            "conv-C",
            null,
            SafeToolExecutionSource.GRAPH_TOOL_CALL
        ));

        assertFalse(result.executed(), "DELETE 工具无服务端确认时必须 fail-closed");
        assertTrue(result.answer().contains(HitlGuard.HITL_REQUIRED_CODE)
                || result.answer().contains("已阻止高风险操作"),
            "拦截提示应明确指向 HITL fail-closed");
        assertNull(deleteTool.lastParams, "被 HITL 拦截后不得调用 Tool.execute");
    }

    @Test
    void executeIntent_shouldPreserveOuterThreadLocalContext() {
        RecordingReadTool readTool = new RecordingReadTool();
        SafeToolExecutor executor = newExecutor(readTool);
        UserPermissionContext.CURRENT_TOKEN.set("outer-token");
        UserPermissionContext.CURRENT_ORG_ID.set("outer-org");

        executor.executeIntent(new SafeToolExecutionRequest(
            "test.read",
            Map.of("keyword", "gpu"),
            "user-A",
            "inner-token",
            "inner-org",
            "conv-D",
            null,
            SafeToolExecutionSource.GRAPH_TOOL_CALL
        ));

        assertEquals("outer-token", UserPermissionContext.CURRENT_TOKEN.get(),
            "SafeToolExecutor 执行后必须恢复外层 token ThreadLocal");
        assertEquals("outer-org", UserPermissionContext.getCurrentOrgId(),
            "SafeToolExecutor 执行后必须恢复外层 orgId ThreadLocal");
    }

    private SafeToolExecutor newExecutor(BaseTool... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools), new UserPermissionContext());
        registry.init();
        return new SafeToolExecutor(registry, new HitlGuard());
    }

    /**
     * 测试用普通只读 Tool。
     */
    @AtlasToolMapping(
        name = "test_read_tool",
        intentId = "test.read",
        agent = "query",
        description = "测试读取工具",
        httpMethod = "GET",
        apiEndpoints = {"/api/test/read"},
        operationType = AtlasToolMapping.OperationType.READ,
        requiresConfirmation = false
    )
    private static class RecordingReadTool extends BaseTool {
        private Map<String, Object> lastParams;

        private RecordingReadTool() {
            super("test_read_tool", "测试读取工具");
        }

        @Override
        protected Set<String> getRequiredParams() {
            return Set.of();
        }

        @Override
        protected AtlasToolResult doExecute(Map<String, Object> params) {
            lastParams = Map.copyOf(params);
            return AtlasToolResult.ok("读取成功", List.of(Map.of("name", "node-a")));
        }
    }

    /**
     * 测试用高危删除 Tool。
     */
    @AtlasToolMapping(
        name = "test_delete_tool",
        intentId = "test.delete",
        agent = "deploy",
        description = "测试删除工具",
        httpMethod = "DELETE",
        apiEndpoints = {"/api/test/delete"},
        operationType = AtlasToolMapping.OperationType.DELETE,
        requiresConfirmation = true
    )
    private static class RecordingDeleteTool extends BaseTool {
        private Map<String, Object> lastParams;

        private RecordingDeleteTool() {
            super("test_delete_tool", "测试删除工具");
        }

        @Override
        protected Set<String> getRequiredParams() {
            return Set.of();
        }

        @Override
        protected AtlasToolResult doExecute(Map<String, Object> params) {
            lastParams = Map.copyOf(params);
            return AtlasToolResult.ok("删除成功", Map.of("deleted", true));
        }
    }
}
