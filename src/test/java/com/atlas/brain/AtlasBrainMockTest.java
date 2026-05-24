package com.atlas.brain;

import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AtlasBrain Mock 单元测试 — 验证认知决策中枢的核心逻辑。
 *
 * <p><b>测试范围：</b></p>
 * <ol>
 *   <li>正常决策：parser 正确返回 BrainDecision → AtlasBrain 返回它</li>
 *   <li>解析异常：parser 抛 BrainParseException → AtlasBrain 抛出（由调用方降级处理）</li>
 *   <li>权限校验：目标 Tool 不可见 → 抛出 RuntimeException</li>
 *   <li>高危检测：delete/scale 操作应触发 HITL_CONFIRM 告警日志</li>
 *   <li>PLAN 守卫：用户显式要求先规划/只出方案时应被覆盖为 PLAN</li>
 *   <li>ReAct 守卫：诊断类查询即使 LLM 返回 CALL_TOOL 也应被覆盖为 DELEGATE_REACT</li>
 * </ol>
 *
 * <p><b>Mock 策略：</b>StructuredOutputParser 完全 Mock，绕过真实 LLM 调用，
 * 专注测试 AtlasBrain 的业务逻辑（prompt 构建、决策校验、风险检测、PLAN/ReAct 守卫）。</p>
 *
 * @version 3.1.0-M4.2
 */
@ExtendWith(MockitoExtension.class)
class AtlasBrainMockTest {

    @Mock
    private ChatModel chatModel;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private StructuredOutputParser parser;

    // 使用真实 ObjectMapper（轻量，无需 mock）
    private ObjectMapper objectMapper;

    @InjectMocks
    private AtlasBrain atlasBrain;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // 重新创建 AtlasBrain，确保 parser 是 mock
        atlasBrain = new AtlasBrain(chatModel, objectMapper, toolRegistry, parser);
    }

    // ═══════════════════════════════════════════════════════════
    // TC-BRAIN-01: 正常决策 — CALL_TOOL
    // ═══════════════════════════════════════════════════════════

    @Test
    void testDecide_callTool_returnsDecision() {
        // 准备上下文
        ExecutionContext ctx = buildCtx("查看所有节点状态");

        // Mock visible tools
        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("node_query: 查询节点");
        when(toolRegistry.getVisibleToolNamesForCurrentUser()).thenReturn(List.of("node_query"));

        // Mock parser 返回 CALL_TOOL 决策
        BrainDecision expected = new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            "node_query",
            Map.of(),
            "用户想查询节点状态",
            0.95,
            Collections.emptyList()
        );
        when(parser.parse(any(), eq("查看所有节点状态"), eq(BrainDecision.class), any()))
            .thenReturn(expected);

        // 执行
        BrainDecision result = atlasBrain.decide(ctx);

        // 断言
        assertNotNull(result);
        assertEquals(BrainDecision.ActionType.CALL_TOOL, result.actionType());
        assertEquals("node_query", result.target());
        assertEquals(0.95, result.confidence(), 0.001);

        // 验证 parser 被调用，且 systemPrompt 包含工具列表
        verify(parser).parse(any(), eq("查看所有节点状态"), eq(BrainDecision.class), contains("node_query"));
    }

    // ═══════════════════════════════════════════════════════════
    // TC-BRAIN-02: 解析失败 → BrainParseException 外抛
    // ═══════════════════════════════════════════════════════════

    @Test
    void testDecide_parserThrows_propagatesException() {
        ExecutionContext ctx = buildCtx("随便说点什么");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("node_query: 查询节点");
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenThrow(new BrainParseException("LLM 返回无效 JSON", new RuntimeException("parse error")));

        assertThrows(BrainParseException.class, () -> atlasBrain.decide(ctx));
    }

    // ═══════════════════════════════════════════════════════════
    // TC-BRAIN-03: 目标 Tool 不可见 → RuntimeException
    // ═══════════════════════════════════════════════════════════

    @Test
    void testDecide_invisibleTool_throwsRuntimeException() {
        ExecutionContext ctx = buildCtx("查询内部管理员审计报表");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("node_query: 查询节点");
        when(toolRegistry.getVisibleToolNamesForCurrentUser()).thenReturn(List.of("node_query"));

        // parser 返回了一个当前用户无权访问的 tool
        BrainDecision badDecision = new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            "admin_audit_report",  // 不在可见列表中
            Map.of(),
            "用户想查询内部管理员审计报表",
            0.90,
            Collections.emptyList()
        );
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenReturn(badDecision);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> atlasBrain.decide(ctx));
        assertTrue(ex.getMessage().contains("invisible tool"));
    }

    // ═══════════════════════════════════════════════════════════
    // TC-BRAIN-04: 闲聊 → DIRECT_ANSWER（无需权限校验）
    // ═══════════════════════════════════════════════════════════

    @Test
    void testDecide_directAnswer_noPermissionCheck() {
        ExecutionContext ctx = buildCtx("你好");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("node_query: 查询节点");

        BrainDecision expected = new BrainDecision(
            BrainDecision.ActionType.DIRECT_ANSWER,
            "",
            Map.of(),
            "用户打招呼",
            0.99,
            Collections.emptyList()
        );
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenReturn(expected);

        BrainDecision result = atlasBrain.decide(ctx);

        assertEquals(BrainDecision.ActionType.DIRECT_ANSWER, result.actionType());
        // DIRECT_ANSWER 不需要校验 target 是否在可见列表
        verify(toolRegistry, never()).getVisibleToolNamesForCurrentUser();
    }

    // ═══════════════════════════════════════════════════════════
    // TC-BRAIN-05: 信息不足 → ASK_CLARIFY
    // ═══════════════════════════════════════════════════════════

    @Test
    void testDecide_askClarify_returnsCorrectType() {
        ExecutionContext ctx = buildCtx("帮我看看");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("node_query: 查询节点");

        BrainDecision expected = new BrainDecision(
            BrainDecision.ActionType.ASK_CLARIFY,
            "",
            Map.of(),
            "用户意图不明确",
            0.45,
            List.of("请确认您想查询哪种资源")
        );
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenReturn(expected);

        BrainDecision result = atlasBrain.decide(ctx);

        assertEquals(BrainDecision.ActionType.ASK_CLARIFY, result.actionType());
        assertFalse(result.requiredContext().isEmpty());
    }

    // ═══════════════════════════════════════════════════════════
    // TC-BRAIN-06: 诊断类查询（为什么 Pod 起不来）→ ReAct 守卫覆盖为 DELEGATE_REACT
    // ═══════════════════════════════════════════════════════════

    @Test
    void testDecide_reactGuard_overridesCallToolToReact() {
        // LLM 误判为 CALL_TOOL（实际应该 DELEGATE_REACT）
        ExecutionContext ctx = buildCtx("为什么我的Pod一直起不来，报错CrashLoopBackOff");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("node_query: 查询节点");
        when(toolRegistry.getVisibleToolNamesForCurrentUser()).thenReturn(List.of("node_query"));

        BrainDecision llmWrong = new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            "node_query",
            Map.of(),
            "用户想查看节点信息",
            0.65,
            Collections.emptyList()
        );
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenReturn(llmWrong);

        BrainDecision result = atlasBrain.decide(ctx);

        // 被 ReAct 守卫覆盖
        assertEquals(BrainDecision.ActionType.DELEGATE_REACT, result.actionType());
        assertEquals("react", result.target());
        assertTrue(result.confidence() >= 0.80, "覆盖后置信度应至少提升到0.80");
        assertTrue(result.reasoning().contains("ReActGuard"), "reasoning 应包含 ReActGuard 标记");
    }


    @Test
    void testDecide_planGuard_overridesCallToolToPlan() {
        // 用户显式要求“先规划/不要执行”时，即使 LLM 误判为普通工具调用，也必须进入 PLAN。
        ExecutionContext ctx = buildCtx("先给我一个分步骤执行计划，不要执行，如何创建 nginx deployment");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("deploy_create: 创建 Deployment");
        when(toolRegistry.getVisibleToolNamesForCurrentUser()).thenReturn(List.of("deploy_create"));

        BrainDecision llmWrong = new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            "deploy_create",
            Map.of(),
            "用户想创建 Deployment",
            0.72,
            Collections.emptyList()
        );
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenReturn(llmWrong);

        BrainDecision result = atlasBrain.decide(ctx);

        assertEquals(BrainDecision.ActionType.PLAN, result.actionType());
        assertEquals("plan", result.target());
        assertTrue(result.confidence() >= 0.82, "覆盖后置信度应至少提升到0.82");
        assertTrue(result.reasoning().contains("PlanGuard"), "reasoning 应包含 PlanGuard 标记");
    }

    @Test
    void testShouldUsePlan_explicitPlanningKeywords() {
        // PLAN 只覆盖显式规划类请求，避免普通查询被误导到计划节点。
        assertTrue(AtlasBrain.shouldUsePlan("/plan 帮我规划创建 Deployment 的步骤"));
        assertTrue(AtlasBrain.shouldUsePlan("先规划一下扩展前端按钮覆盖的方案"));
        assertTrue(AtlasBrain.shouldUsePlan("只生成计划，不要执行真实操作"));
        assertTrue(AtlasBrain.shouldUsePlan("请分步骤说明下一步怎么做"));
        assertFalse(AtlasBrain.shouldUsePlan("查看所有节点状态"));
        assertFalse(AtlasBrain.shouldUsePlan("为什么 pod crashloopbackoff"));
    }

    @Test
    void testDecide_highRiskPlanPrefix_stillHitlFirst() {
        // 高危 HITL 优先级必须高于 /plan，避免用户通过“只规划”前缀绕过确认边界。
        ExecutionContext ctx = buildCtx("/plan 删除 production namespace 下所有 pod 的步骤");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("pod_delete: 删除 Pod");

        BrainDecision llmWrong = new BrainDecision(
            BrainDecision.ActionType.PLAN,
            "plan",
            Map.of(),
            "用户要求规划删除生产命名空间资源",
            0.88,
            Collections.emptyList()
        );
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenReturn(llmWrong);

        BrainDecision result = atlasBrain.decide(ctx);

        assertEquals(BrainDecision.ActionType.HITL_CONFIRM, result.actionType());
        assertTrue(result.reasoning().contains("SafetyGuard"));
    }

    @Test
    void testDecide_reactPrefix_overridesCallToolToReact() {
        // 用户显式使用 /react 前缀时，即使 LLM 误判为普通工具调用，也必须进入 ReAct。
        ExecutionContext ctx = buildCtx("/react 诊断 default namespace 的 nginx-1 pod CrashLoopBackOff 原因");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("pod_query: 查询 Pod");
        when(toolRegistry.getVisibleToolNamesForCurrentUser()).thenReturn(List.of("pod_query"));

        BrainDecision llmWrong = new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            "pod_query",
            Map.of(),
            "用户想查询 Pod 列表",
            0.70,
            Collections.emptyList()
        );
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenReturn(llmWrong);

        BrainDecision result = atlasBrain.decide(ctx);

        assertEquals(BrainDecision.ActionType.DELEGATE_REACT, result.actionType());
        assertEquals("react", result.target());
        assertTrue(result.reasoning().contains("ReActGuard"));
    }

    @Test
    void testShouldUseReAct_deepPrefixAndKubernetesFailureKeywords() {
        // 静态守卫直接覆盖显式深度推理前缀和常见 K8s 故障状态。
        assertTrue(AtlasBrain.shouldUseReAct("/deep 排查 default namespace nginx-1 为什么起不来"));
        assertTrue(AtlasBrain.shouldUseReAct("帮我看看 nginx-1 为什么 CrashLoopBackOff"));
        assertTrue(AtlasBrain.shouldUseReAct("Pod ImagePullBackOff 怎么排查"));
        assertTrue(AtlasBrain.shouldUseReAct("服务启动失败是什么原因"));
        assertTrue(AtlasBrain.shouldUseReAct("default/nginx-1 有 Warning 事件，帮我分析原因"));
        assertTrue(AtlasBrain.shouldUseReAct("Pod 一直 FailedScheduling 是为什么"));
        assertTrue(AtlasBrain.shouldUseReAct("这个 Pod 调度失败怎么处理"));
    }

    @Test
    void testDecide_highRiskReactPrefix_stillHitlFirst() {
        // 高危 HITL 优先级必须高于 /react，避免用户通过显式前缀绕过确认边界。
        ExecutionContext ctx = buildCtx("/react 删除 production namespace 下所有 pod");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("pod_delete: 删除 Pod");

        BrainDecision llmWrong = new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            "pod_delete",
            Map.of(),
            "用户要求删除生产命名空间资源",
            0.80,
            Collections.emptyList()
        );
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenReturn(llmWrong);

        BrainDecision result = atlasBrain.decide(ctx);

        assertEquals(BrainDecision.ActionType.HITL_CONFIRM, result.actionType());
        assertTrue(result.reasoning().contains("SafetyGuard"));
    }

    // ═══════════════════════════════════════════════════════════
    // TC-BRAIN-07: 高危+诊断混合查询 → 不覆盖为 ReAct（保持原决策）
    // ═══════════════════════════════════════════════════════════

    @Test
    void testDecide_highRiskQuery_doesNotOverrideToReact() {
        ExecutionContext ctx = buildCtx("为什么删除这个 Pod 一直失败");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("node_query: 查询节点");

        // LLM 返回 DELEGATE_AGENT
        BrainDecision llmDecision = new BrainDecision(
            BrainDecision.ActionType.DELEGATE_AGENT,
            "deploy_agent",
            Map.of(),
            "用户遇到删除 Pod 失败",
            0.75,
            Collections.emptyList()
        );
        when(parser.parse(any(), anyString(), eq(BrainDecision.class), any()))
            .thenReturn(llmDecision);

        BrainDecision result = atlasBrain.decide(ctx);

        // 含"删除"关键词，属于高危，应被 SafetyGuard 强制转为 HITL_CONFIRM，而不是 ReAct 或普通 Agent
        assertEquals(BrainDecision.ActionType.HITL_CONFIRM, result.actionType());
    }

    // ═══════════════════════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════════════════════

    private ExecutionContext buildCtx(String query) {
        return new ExecutionContext(
            "test-session", "zhaotiandi", query,
            Collections.emptyList(),
            Map.of("token", "fake-token"),
            "test-conv", Instant.now()
        );
    }
}
