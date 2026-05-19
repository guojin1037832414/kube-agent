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
 * </ol>
 *
 * <p><b>Mock 策略：</b>StructuredOutputParser 完全 Mock，绕过真实 LLM 调用，
 * 专注测试 AtlasBrain 的业务逻辑（prompt 构建、决策校验、风险检测）。</p>
 *
 * @version 3.1.0-M2
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
        ExecutionContext ctx = buildCtx("删除某个东西");

        when(toolRegistry.buildSystemPromptForCurrentUser()).thenReturn("node_query: 查询节点");
        when(toolRegistry.getVisibleToolNamesForCurrentUser()).thenReturn(List.of("node_query"));

        // parser 返回了一个当前用户无权访问的 tool
        BrainDecision badDecision = new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            "admin_delete_all",  // 不在可见列表中
            Map.of(),
            "想删除",
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
