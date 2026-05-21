package com.atlas.react;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Test
    void runWithEvents_shouldCompleteTwoToolActionsThenFinalAnswer() {
        ScriptedChatModel chatModel = new ScriptedChatModel(List.of(
            "Thought: 先查询 Pod 基础状态。\nAction: {\"tool\":\"pod_status\",\"params\":{\"podName\":\"nginx-1\",\"namespace\":\"default\"}}",
            "Thought: Pod 存在且重启，需要继续查询异常事件。\nAction: {\"tool\":\"event_query\",\"params\":{\"podName\":\"nginx-1\",\"namespace\":\"default\",\"reason\":\"BackOff\"}}",
            "Thought: 已拿到状态和事件，可以给出结论。\nFinal Answer: 现象：nginx-1 发生 CrashLoopBackOff。证据：restartCount=3，事件包含 BackOff。判断：应用进程启动后反复退出。建议：查看容器启动命令和应用日志。"
        ));
        RecordingTool podTool = new RecordingTool(
            "pod_status",
            "查询 Pod 状态",
            AtlasToolResult.ok("Pod 状态查询完成", Map.of(
                "podName", "nginx-1",
                "phase", "Running",
                "ready", false,
                "restartCount", 3,
                "reason", "CrashLoopBackOff"
            ))
        );
        RecordingTool eventTool = new RecordingTool(
            "event_query",
            "查询 Pod 异常事件",
            AtlasToolResult.ok("事件查询完成", List.of(Map.of(
                "reason", "BackOff",
                "message", "Back-off restarting failed container"
            )))
        );
        ToolRegistry registry = new ToolRegistry(List.of(podTool, eventTool), new UserPermissionContext());
        registry.init();
        ReActEngine engine = new ReActEngine(
            chatModel,
            new ObjectMapper(),
            registry,
            new ReActPromptBuilder(registry)
        );
        List<ReActEvent> events = new ArrayList<>();

        ReActResult result = engine.runWithEvents(
            "/react 诊断 default namespace 的 nginx-1 pod CrashLoopBackOff 原因",
            Map.of("token", "test-token", "organizationId", "100002", "conversationId", "conv-1"),
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
        assertEquals("test-token", podTool.lastParams().get("token"), "会话 token 必须透传到工具参数");
        assertEquals("100002", eventTool.lastParams().get("organizationId"), "orgId 必须透传到后续工具参数");
        assertEquals("nginx-1", eventTool.lastParams().get("podName"));
        assertEquals("default", eventTool.lastParams().get("namespace"));

        assertTrue(events.stream().anyMatch(e -> "tool_start".equals(e.type()) && "pod_status".equals(e.tool())));
        assertTrue(events.stream().anyMatch(e -> "tool_done".equals(e.type()) && "event_query".equals(e.tool())));
        assertTrue(events.stream().anyMatch(e -> "observation".equals(e.type()) && e.content().contains("Back-off")));
        assertTrue(events.stream().anyMatch(e -> "content".equals(e.type()) && e.content().contains("应用进程启动后反复退出")));
        assertFalse(events.stream().anyMatch(e -> "error".equals(e.type())), "成功路径不应产生 error 事件");
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
     */
    private static final class RecordingTool extends BaseTool {
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
}
