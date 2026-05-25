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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    void executeIntent_shouldAllowHighRiskDeleteToolWhenConfirmationTargetMatches() {
        // 【契约1】高危 DELETE Tool 带服务端 HitlConfirmation 且 target 精确匹配 intentId 时，应放行执行
        RecordingDeleteTool deleteTool = new RecordingDeleteTool();
        SafeToolExecutor executor = newExecutor(deleteTool);

        // 构造精确匹配的确认凭证：target = "test.delete"
        HitlConfirmation confirmation = HitlConfirmation.human("thread-1", "test.delete");

        SafeToolExecutionResult result = executor.executeIntent(new SafeToolExecutionRequest(
            "test.delete",
            Map.of("name", "node-x"),
            "user-A",
            "token-A",
            "100002",
            "conv-E",
            confirmation,
            SafeToolExecutionSource.GRAPH_TOOL_CALL
        ));

        assertTrue(result.executed(), "确认 target 匹配时 DELETE 工具应被执行");
        assertTrue(result.success(), "DELETE 工具业务执行应返回成功");
        assertNotNull(deleteTool.lastParams, "确认凭证有效时 Tool 应收到参数");
        assertEquals("node-x", deleteTool.lastParams.get("name"), "业务参数应正确透传");
    }

    @Test
    void executeIntent_shouldBlockHighRiskDeleteToolWhenConfirmationTargetMismatch() {
        // 【契约2】高危 DELETE Tool 的 confirmation target 不匹配时必须阻断
        RecordingDeleteTool deleteTool = new RecordingDeleteTool();
        SafeToolExecutor executor = newExecutor(deleteTool);

        // 构造不匹配的确认凭证：target = "other.intent"，与 "test.delete" 不一致
        HitlConfirmation confirmation = HitlConfirmation.human("thread-1", "other.intent");

        SafeToolExecutionResult result = executor.executeIntent(new SafeToolExecutionRequest(
            "test.delete",
            Map.of("name", "node-y"),
            "user-A",
            "token-A",
            "100002",
            "conv-F",
            confirmation,
            SafeToolExecutionSource.GRAPH_TOOL_CALL
        ));

        assertFalse(result.executed(), "确认 target 不匹配时必须 fail-closed");
        assertNull(deleteTool.lastParams, "被拦截后不得调用 Tool.execute");
    }

    @Test
    void executeIntent_shouldRestoreOuterThreadLocalWhenToolThrowsException() {
        // 【契约3】Tool 执行抛异常时必须恢复外层 ThreadLocal，并返回结构化失败/未执行结果
        ThrowingTool throwingTool = new ThrowingTool();
        SafeToolExecutor executor = newExecutor(throwingTool);

        // 执行前设置外层 ThreadLocal
        UserPermissionContext.CURRENT_TOKEN.set("outer-token");
        UserPermissionContext.CURRENT_ORG_ID.set("outer-org");

        SafeToolExecutionResult result = executor.executeIntent(new SafeToolExecutionRequest(
            "test.throwing",
            Map.of("keyword", "boom"),
            "user-A",
            "inner-token",
            "inner-org",
            "conv-G",
            null,
            SafeToolExecutionSource.GRAPH_TOOL_CALL
        ));

        assertFalse(result.executed(), "Tool 抛异常时应返回未执行状态");
        assertTrue(
            result.answer().contains("boom") || result.answer().contains("Tool 执行异常"),
            "异常 answer 应包含原始异常信息或通用异常提示"
        );
        assertEquals("outer-token", UserPermissionContext.CURRENT_TOKEN.get(),
            "异常后 token ThreadLocal 必须恢复为外层值");
        assertEquals("outer-org", UserPermissionContext.getCurrentOrgId(),
            "异常后 orgId ThreadLocal 必须恢复为外层值");
    }

    @Test
    void executeIntent_shouldClearThreadLocalWhenToolThrowsAndOuterWasEmpty() {
        // 【契约4】外层 ThreadLocal 原为空时，Tool 抛异常后必须清空，防止线程池污染
        ThrowingTool throwingTool = new ThrowingTool();
        SafeToolExecutor executor = newExecutor(throwingTool);

        // 确保外层 ThreadLocal 为空
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();

        executor.executeIntent(new SafeToolExecutionRequest(
            "test.throwing",
            Map.of("keyword", "boom"),
            "user-A",
            "inner-token",
            "inner-org",
            "conv-H",
            null,
            SafeToolExecutionSource.GRAPH_TOOL_CALL
        ));

        assertNull(UserPermissionContext.CURRENT_TOKEN.get(),
            "外层 token 为空时异常后必须保持为 null");
        assertNull(UserPermissionContext.getCurrentOrgId(),
            "外层 orgId 为空时异常后必须保持为 null");
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

    /**
     * 测试用异常抛出 Tool，用于验证异常后 ThreadLocal 恢复契约。
     */
    @AtlasToolMapping(
        name = "test_throwing_tool",
        intentId = "test.throwing",
        agent = "query",
        description = "测试异常工具",
        httpMethod = "GET",
        apiEndpoints = {"/api/test/throw"},
        operationType = AtlasToolMapping.OperationType.READ,
        requiresConfirmation = false
    )
    private static class ThrowingTool extends BaseTool {
        private ThrowingTool() {
            super("test_throwing_tool", "测试异常工具");
        }

        @Override
        protected Set<String> getRequiredParams() {
            return Set.of();
        }

        @Override
        protected AtlasToolResult doExecute(Map<String, Object> params) {
            throw new IllegalStateException("boom");
        }
    }
}
