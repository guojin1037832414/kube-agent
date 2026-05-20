package com.atlas.react;

import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * ReActEngine 收敛策略单元测试。
 *
 * <p>这些测试不调用真实 LLM，只通过反射锁定引擎层的纯策略逻辑，确保：
 * 1. 工具返回“未找到目标资源并返回列表”时能够提前终止，避免继续喂长列表给 LLM；
 * 2. Observation 超长截断采用头尾保留，避免只保留开头丢失尾部错误线索。</p>
 */
class ReActEnginePolicyTest {

    private ReActEngine newEngine() {
        return new ReActEngine(
            mock(ChatModel.class),
            new ObjectMapper(),
            mock(ToolRegistry.class),
            mock(ReActPromptBuilder.class)
        );
    }

    @Test
    void testIsTargetResourceNotFoundObservation_detectsKubeManagerFallbackList() throws Exception {
        ReActEngine engine = newEngine();
        Method method = ReActEngine.class.getDeclaredMethod("isTargetResourceNotFoundObservation", String.class);
        method.setAccessible(true);

        String observation = """
            {"success":true,"message":"未找到 Pod nginx-1，返回 Pod 列表","data":[{"name":"pod-a"}]}
            """;

        boolean result = (boolean) method.invoke(engine, observation);

        assertTrue(result, "未找到目标资源且返回列表时，应触发提前收敛策略");
    }

    @Test
    void testIsTargetResourceNotFoundObservation_ignoresNormalObservation() throws Exception {
        ReActEngine engine = newEngine();
        Method method = ReActEngine.class.getDeclaredMethod("isTargetResourceNotFoundObservation", String.class);
        method.setAccessible(true);

        String observation = """
            {"success":true,"message":"Pod诊断完成","data":{"name":"nginx-1","status":"Running"}}
            """;

        boolean result = (boolean) method.invoke(engine, observation);

        assertFalse(result, "正常诊断结果不能误触发 target_not_found");
    }

    @Test
    void testTruncateObservation_keepsHeadAndTail() throws Exception {
        ReActEngine engine = newEngine();
        Method method = ReActEngine.class.getDeclaredMethod("truncateObservation", String.class, int.class);
        method.setAccessible(true);

        String text = "HEAD-重要概要-" + "x".repeat(5000) + "-TAIL-最新错误线索";
        String truncated = (String) method.invoke(engine, text, 300);

        assertTrue(truncated.length() <= 300, "截断结果不能超过预算");
        assertTrue(truncated.contains("HEAD-重要概要"), "头部概要应保留");
        assertTrue(truncated.contains("TAIL-最新错误线索"), "尾部最新线索应保留");
        assertTrue(truncated.contains("截断/不完整"), "必须明确标记 Observation 不完整");
    }

    @Test
    void testGenerateTargetNotFoundSummary_isUserFriendlyAndNoLlmRequired() throws Exception {
        ReActEngine engine = newEngine();
        Method method = ReActEngine.class.getDeclaredMethod("generateTargetNotFoundSummary", String.class, String.class);
        method.setAccessible(true);

        String observation = """
            {"success":true,"message":"未找到 Pod nginx-1，返回 Pod 列表"}
            """;
        String summary = (String) method.invoke(
            engine,
            observation,
            "/react 诊断 default namespace 的 nginx-1 pod CrashLoopBackOff 原因"
        );

        assertTrue(summary.contains("没有找到你指定的目标资源"));
        assertTrue(summary.contains("资源名称是否拼写正确"));
        assertTrue(summary.contains("namespace 是否正确"));
        assertTrue(summary.contains("避免基于其它资源误判"));
    }
}
