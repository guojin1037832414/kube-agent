package com.atlas.react;

import com.atlas.audit.AgentAuditEvent;
import com.atlas.audit.AgentAuditOutcome;
import com.atlas.audit.InMemoryAgentAuditRecorder;
import com.atlas.auth.UserPermissionContext;
import com.atlas.hitl.HitlGuard;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterNormalizer;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.execution.SafeToolExecutionSource;
import com.atlas.tool.execution.SafeToolExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReActEngine 多步成功路径端到端契约测试。
 *
 * <p>该测试不连接真实 LLM 和真实 kube-manager，而是使用脚本化 {@link ChatModel}
 * 与内存 Tool，锁定 ReAct 引擎最关键的生产路径：</p>
 * <ol>
 *   <li>第一轮 LLM 输出 Action 调用 pod_status；</li>
 *   <li>第二轮 LLM 基于 Observation 输出 Action 调用 event_query；</li>
 *   <li>第三轮 LLM 汇总两个 Observation 输出 Final Answer；</li>
 *   <li>整个过程中事件顺序、工具参数透传、最终 stopReason 均保持稳定。</li>
 * </ol>
 *
 * <p>这是 ReAct 4.1 铺开前的最小成功路径护栏，避免后续 Tool Schema、Prompt 或
 * SSE 调整时把多步循环悄悄改坏。</p>
 */
class ReActEngineMultiStepE2ETest {

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void runWithEvents_shouldCompleteTwoToolActionsThenFinalAnswer() {
        ScriptedChatModel chatModel = new ScriptedChatModel(List.of(
            "Thought: 先查询 Pod 基础状态。\nAction: {\"tool\":\"pod_status\",\"params\":{\"podName\":\"nginx-1\",\"namespace\":\"default\"}}",
            "Thought: Pod 存在且重启，需要继续查询异常事件。\nAction: {\"tool\":\"event_query\",\"params\":{\"podName\":\"nginx-1\",\"namespace\":\"default\",\"reason\":\"BackOff\",\"traceId\":\"forged-action-trace\"}}",
            "Thought: 已拿到状态和事件，可以给出结论。\nFinal Answer: 现象：nginx-1 发生 CrashLoopBackOff。证据：restartCount=3，事件包含 BackOff。判断：应用进程启动后反复退出。建议：查看容器启动命令和应用日志。"
        ));
        RecordingTool podTool = new PodStatusRecordingTool(
            "查询 Pod 状态",
            AtlasToolResult.ok("Pod 状态查询完成", Map.of(
                "podName", "nginx-1",
                "phase", "Running",
                "ready", false,
                "restartCount", 3,
                "reason", "CrashLoopBackOff"
            ))
        );
        RecordingTool eventTool = new EventQueryRecordingTool(
            "查询 Pod 异常事件",
            AtlasToolResult.ok("事件查询完成", List.of(Map.of(
                "reason", "BackOff",
                "message", "Back-off restarting failed container"
            )))
        );
        UserPermissionContext permissionContext = new UserPermissionContext();
        ToolRegistry registry = new ToolRegistry(List.of(podTool, eventTool), permissionContext);
        registry.init();
        InMemoryAgentAuditRecorder auditRecorder = new InMemoryAgentAuditRecorder();
        SafeToolExecutor safeToolExecutor = new SafeToolExecutor(
            registry,
            new HitlGuard(),
            auditRecorder
        );
        ReActEngine engine = new ReActEngine(
            chatModel,
            new ObjectMapper(),
            registry,
            new ReActPromptBuilder(registry),
            new ToolParameterNormalizer(registry),
            new HitlGuard(),
            safeToolExecutor,
            null
        );
        List<ReActEvent> events = new ArrayList<>();
        String traceId = "trc_react_e2e_trace_001";

        ReActResult result = engine.runWithEvents(
            "/react 诊断 default namespace 的 nginx-1 pod CrashLoopBackOff 原因",
            Map.of("token", "test-token", "organizationId", "100002", "conversationId", "conv-1", "traceId", traceId),
            events::add
        );

        assertTrue(result.success(), "两轮工具 + Final Answer 应整体成功");
        assertEquals("final_answer", result.stopReason());
        assertEquals(3, chatModel.callCount(), "应严格调用三次 LLM：两次 Action + 一次 Final Answer");
        assertEquals(3, result.steps().size(), "两次工具步骤 + 一次最终回答步骤都应进入 ReActMemory");
        assertTrue(result.finalAnswer().contains("CrashLoopBackOff"));
        assertTrue(result.finalAnswer().contains("BackOff"));

        assertEquals(1, podTool.callCount(), "pod_status 应只调用一次");
        assertEquals(1, eventTool.callCount(), "event_query 应只调用一次");
        assertFalse(podTool.lastParams().containsKey("token"), "token 只用于 SafeToolExecutor 绑定 ThreadLocal，不得透传给业务 Tool");
        assertEquals("100002", eventTool.lastParams().get("organizationId"), "orgId 必须由 SafeToolExecutor 作为可信上下文补齐");
        assertEquals("conv-1", eventTool.lastParams().get("conversationId"), "conversationId 必须由 SafeToolExecutor 作为可信上下文补齐");
        assertFalse(eventTool.lastParams().containsKey("traceId"), "traceId 属于控制平面上下文，不得透传给业务 Tool");
        assertEquals("nginx-1", eventTool.lastParams().get("podName"));
        assertEquals("default", eventTool.lastParams().get("namespace"));
        assertFalse(result.steps().get(0).params().containsKey("token"), "ReAct 记忆不得暴露 token");
        assertFalse(result.steps().get(0).params().containsKey("organizationId"), "ReAct 记忆不得暴露租户控制字段");

        assertFalse(result.steps().get(0).params().containsKey("traceId"), "ReAct 记忆不应把 traceId 当作业务参数保存");

        assertTrue(events.stream().anyMatch(e -> "tool_start".equals(e.type()) && "pod_status".equals(e.tool())));
        ReActEvent podStart = events.stream()
            .filter(e -> "tool_start".equals(e.type()) && "pod_status".equals(e.tool()))
            .findFirst()
            .orElseThrow();
        assertEquals(traceId, podStart.metadata().get("traceId"), "tool_start 事件必须带同一 traceId");
        assertFalse(String.valueOf(podStart.metadata().get("params")).contains("test-token"),
            "ReAct tool_start 事件不得泄露 token");
        assertFalse(String.valueOf(podStart.metadata().get("params")).contains("organizationId"),
            "ReAct tool_start 事件不得泄露租户控制字段");
        assertFalse(String.valueOf(podStart.metadata().get("params")).contains("traceId"),
            "ReAct tool_start 展示参数不得把 traceId 当业务参数暴露");
        assertTrue(events.stream().anyMatch(e -> "tool_done".equals(e.type()) && "event_query".equals(e.tool())));
        assertTrue(events.stream().anyMatch(e -> "tool_done".equals(e.type()) && traceId.equals(e.metadata().get("traceId"))),
            "tool_done 事件必须带同一 traceId");
        assertTrue(events.stream().anyMatch(e -> "observation".equals(e.type()) && e.content().contains("Back-off")
                && traceId.equals(e.metadata().get("traceId"))),
            "observation 事件必须带同一 traceId");
        assertTrue(events.stream().anyMatch(e -> "content".equals(e.type()) && e.content().contains("应用进程启动后反复退出")
                && traceId.equals(e.metadata().get("traceId"))),
            "final content 事件必须带同一 traceId");
        assertFalse(events.stream().anyMatch(e -> "error".equals(e.type())), "成功路径不应产生 error 事件");

        // 中文说明：这里把 ReAct 成功路径从“内存 Tool 被调用”提升为“真实统一执行边界被调用并留下审计”。
        // 安全边界：审计事件只证明本次 READ 候选经过 SafeToolExecutor，不代表 release、HITL 或写操作权限已经打开。
        assertEquals(2, auditRecorder.recentEvents().size(),
            "两轮 ReAct Action 都必须经过 SafeToolExecutor 并留下可回放审计事件");
        assertReactAuditEvent(auditRecorder.recentEvents().get(1), "pod_status", traceId, "100002", "conv-1");
        assertReactAuditEvent(auditRecorder.recentEvents().get(0), "event_query", traceId, "100002", "conv-1");
    }

    private void assertReactAuditEvent(AgentAuditEvent event,
                                       String expectedIntentId,
                                       String expectedTraceId,
                                       String expectedOrgId,
                                       String expectedConversationId) {
        assertEquals(expectedIntentId, event.intentId(), "审计必须记录 ReAct 实际委托的 intentId");
        assertEquals(SafeToolExecutionSource.REACT_ENGINE, event.source(), "ReAct 入口必须以 REACT_ENGINE 来源进入审计");
        assertEquals(AgentAuditOutcome.SUCCESS, event.outcome(), "本用例只覆盖低风险 READ 成功链路");
        assertEquals(expectedTraceId, event.traceId(), "审计 traceId 必须与 ReAct 事件时间线一致");
        assertEquals(expectedOrgId, event.organizationId(), "审计租户必须来自服务端可信上下文");
        assertEquals(expectedConversationId, event.conversationId(), "审计会话必须来自服务端初始上下文");
        assertEquals("GET", event.httpMethod(), "ReAct 多步成功样例只允许 GET/READ Tool");
        assertEquals(AtlasToolMapping.OperationType.READ, event.operationType(), "本测试不能悄悄混入写操作");
        assertFalse(event.requiresConfirmation(), "低风险 READ 样例不应触发 HITL");
        assertTrue(event.executed(), "成功审计必须标记已进入 Tool 执行");
        assertTrue(event.success(), "成功审计必须标记业务成功");
        assertFalse(event.parameterSummary().toString().contains("test-token"),
            "审计参数摘要不得泄露 token 原文");
    }

    /**
     * 脚本化 ChatModel：按顺序返回预设 LLM 文本，便于稳定复现多轮 ReAct 行为。
     */
    private static final class ScriptedChatModel implements ChatModel {
        private final List<String> outputs;
        private final AtomicInteger cursor = new AtomicInteger();

        private ScriptedChatModel(List<String> outputs) {
            this.outputs = outputs;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            int index = cursor.getAndIncrement();
            if (index >= outputs.size()) {
                throw new IllegalStateException("脚本化 LLM 输出已耗尽，index=" + index);
            }
            return new ChatResponse(List.of(new Generation(new AssistantMessage(outputs.get(index)))));
        }

        private int callCount() {
            return cursor.get();
        }
    }

    /**
     * 内存记录型 Tool：记录每次入参并返回固定 AtlasToolResult。
     *
     * <p>基类只承载测试夹具逻辑；真正注册给 {@link ToolRegistry} 的子类需要各自声明
     * {@link AtlasToolMapping} READ 元数据。这样可以在不放松生产 HITL fail-closed 的前提下，
     * 明确告诉测试守卫：本 E2E 成功路径中的两个工具都是无需人工确认的只读查询。</p>
     */
    private abstract static class RecordingTool extends BaseTool {
        private final String runtimeName;
        private final AtlasToolResult result;
        private int callCount;
        private Map<String, Object> lastParams = Map.of();

        private RecordingTool(String runtimeName, String description, AtlasToolResult result) {
            super(runtimeName, description);
            this.runtimeName = runtimeName;
            this.result = result;
        }

        @Override
        protected Set<String> getRequiredParams() {
            return Set.of();
        }

        @Override
        protected AtlasToolResult doExecute(Map<String, Object> params) {
            callCount++;
            lastParams = Map.copyOf(params);
            return result;
        }

        private int callCount() {
            return callCount;
        }

        private Map<String, Object> lastParams() {
            return lastParams;
        }

        @Override
        public String getToolName() {
            return runtimeName;
        }
    }

    /**
     * Pod 状态测试 Tool：声明为 READ，避免被 HITL fail-closed 当作 UNKNOWN 高风险拦截。
     */
    @AtlasToolMapping(
        name = "pod_status",
        agent = "query",
        intentId = "pod_status",
        description = "测试用 Pod 状态只读查询工具",
        httpMethod = "GET",
        apiEndpoints = {"/api/{orgId}/pod"},
        operationType = AtlasToolMapping.OperationType.READ
    )
    @ToolPermission(ToolPermission.Policy.PUBLIC)
    private static final class PodStatusRecordingTool extends RecordingTool {

        private PodStatusRecordingTool(String description, AtlasToolResult result) {
            super("pod_status", description, result);
        }
    }

    /**
     * 事件查询测试 Tool：声明为 READ，保持 ReAct 多步成功路径测试聚焦工具编排本身。
     */
    @AtlasToolMapping(
        name = "event_query",
        agent = "query",
        intentId = "event_query",
        description = "测试用事件只读查询工具",
        httpMethod = "GET",
        apiEndpoints = {"/api/{orgId}/event"},
        operationType = AtlasToolMapping.OperationType.READ
    )
    @ToolPermission(ToolPermission.Policy.PUBLIC)
    private static final class EventQueryRecordingTool extends RecordingTool {

        private EventQueryRecordingTool(String description, AtlasToolResult result) {
            super("event_query", description, result);
        }
    }
}
