package com.atlas.react;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.impl.EventQueryTool;
import com.atlas.tool.impl.LogQueryTool;
import com.atlas.tool.impl.PodQueryTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ReAct Pod 故障诊断提示词契约测试。
 *
 * <p>本测试不调用真实 LLM，只锁定系统提示词必须明确告诉模型：
 * Pod 故障排查应按状态、事件、日志的证据链逐步诊断；其中 event_query
 * 是 Pod Warning/异常事件摘要查询工具，而不是完整 Kubernetes EventList。</p>
 */
class ReActPromptBuilderPodDiagnosticContractTest {

    @Test
    void buildSystemPrompt_shouldGuidePodDiagnosticsThroughStatusEventAndLogs() {
        ToolRegistry registry = new ToolRegistry(List.of(
            new PodQueryTool(null),
            new EventQueryTool(null),
            new LogQueryTool(null)
        ), new UserPermissionContext());
        registry.init();

        ReActPromptBuilder builder = new ReActPromptBuilder(registry);
        String prompt = builder.buildSystemPrompt(
            "为什么 default/nginx-1 Pod 一直 Pending，提示 FailedScheduling？",
            new ReActMemory(new ObjectMapper())
        );

        assertTrue(prompt.contains("pod_status"), "ReAct 提示词必须包含 Pod 状态查询工具");
        assertTrue(prompt.contains("event_query"), "ReAct 提示词必须包含 Pod Warning/事件摘要工具");
        assertTrue(prompt.contains("log_query"), "ReAct 提示词必须包含日志查询工具");

        assertTrue(prompt.contains("Pod 诊断工具调用规则"), "必须有明确的 Pod 诊断工具调用规则");
        assertTrue(prompt.contains("默认先调用 pod_status"), "诊断链路第一步应先确认 Pod 状态");
        assertTrue(prompt.contains("Pending"), "规则必须覆盖 Pending 场景");
        assertTrue(prompt.contains("FailedScheduling"), "规则必须覆盖调度失败场景");
        assertTrue(prompt.contains("优先调用 event_query"), "调度/镜像/创建失败应优先查事件摘要");
        assertTrue(prompt.contains("CrashLoopBackOff"), "规则必须覆盖 CrashLoopBackOff 场景");
        assertTrue(prompt.contains("必须结合 log_query"), "CrashLoopBackOff/重启类问题必须结合日志");
        assertTrue(prompt.contains("不是完整 Kubernetes EventList"), "必须声明 event_query 能力边界，避免伪装完整 Event API");
        assertTrue(prompt.contains("现象、证据、判断、建议"), "最终诊断输出必须按证据链组织");

        assertTrue(prompt.contains("不得构造 fieldSelector"), "ReAct Prompt 必须明确禁止 event_query 不支持的原生 Event 参数");
        assertTrue(prompt.contains("labelSelector"), "ReAct Prompt 必须明确列出并禁止不支持的原生 Event 参数");
    }
}
