package com.atlas.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BrainDecision.ActionType 枚举纯单元测试 — 验证所有 ActionType 值完整且可访问。
 *
 * <p>ActionType 定义了 AtlasBrain 的单次决策输出类型：</p>
 * <ul>
 *   <li>CALL_TOOL — 调用某个 DomainTool</li>
 *   <li>DELEGATE_AGENT — 委派给某个 Worker Agent</li>
 *   <li>DELEGATE_REACT — 委派给 ReAct 推理引擎（M3.2 新增）</li>
 *   <li>PLAN — 进入 Plan-and-Execute 计划节点（M4.2 新增）</li>
 *   <li>DIRECT_ANSWER — 直接回复用户（无需工具）</li>
 *   <li>ASK_CLARIFY — 需要用户补充信息</li>
 *   <li>HITL_CONFIRM — 需要人工确认（高危操作）</li>
 * </ul>
 *
 * @version 3.1.0-M4.2
 */
class ActionTypeTest {

    @Test
    void testAllActionTypesExist() {
        // 验证枚举值的数量和名称（M4.2 新增 PLAN，共7个）
        BrainDecision.ActionType[] values = BrainDecision.ActionType.values();
        assertEquals(7, values.length, "应有7个ActionType（含M3.2 DELEGATE_REACT 和 M4.2 PLAN）");
    }

    @Test
    void testCallTool() {
        assertNotNull(BrainDecision.ActionType.CALL_TOOL);
        assertEquals("CALL_TOOL", BrainDecision.ActionType.CALL_TOOL.name());
    }

    @Test
    void testDelegateAgent() {
        assertNotNull(BrainDecision.ActionType.DELEGATE_AGENT);
        assertEquals("DELEGATE_AGENT", BrainDecision.ActionType.DELEGATE_AGENT.name());
    }

    @Test
    void testDelegateReact() {
        assertNotNull(BrainDecision.ActionType.DELEGATE_REACT);
        assertEquals("DELEGATE_REACT", BrainDecision.ActionType.DELEGATE_REACT.name());
    }

    @Test
    void testPlan() {
        assertNotNull(BrainDecision.ActionType.PLAN);
        assertEquals("PLAN", BrainDecision.ActionType.PLAN.name());
    }

    @Test
    void testDirectAnswer() {
        assertNotNull(BrainDecision.ActionType.DIRECT_ANSWER);
        assertEquals("DIRECT_ANSWER", BrainDecision.ActionType.DIRECT_ANSWER.name());
    }

    @Test
    void testAskClarify() {
        assertNotNull(BrainDecision.ActionType.ASK_CLARIFY);
        assertEquals("ASK_CLARIFY", BrainDecision.ActionType.ASK_CLARIFY.name());
    }

    @Test
    void testHitlConfirm() {
        assertNotNull(BrainDecision.ActionType.HITL_CONFIRM);
        assertEquals("HITL_CONFIRM", BrainDecision.ActionType.HITL_CONFIRM.name());
    }

    @Test
    void testEnumOrdinalIsStable() {
        // 验证 ordinal 顺序。项目当前仅用于测试锁定，不建议生产逻辑依赖 ordinal。
        assertEquals(0, BrainDecision.ActionType.CALL_TOOL.ordinal());
        assertEquals(1, BrainDecision.ActionType.DELEGATE_AGENT.ordinal());
        assertEquals(2, BrainDecision.ActionType.DELEGATE_REACT.ordinal());
        assertEquals(3, BrainDecision.ActionType.PLAN.ordinal());
        assertEquals(4, BrainDecision.ActionType.DIRECT_ANSWER.ordinal());
        assertEquals(5, BrainDecision.ActionType.ASK_CLARIFY.ordinal());
        assertEquals(6, BrainDecision.ActionType.HITL_CONFIRM.ordinal());
    }

    @Test
    void testValueOfAllTypes() {
        // 验证 valueOf 可正确反序列化所有值
        assertEquals(BrainDecision.ActionType.CALL_TOOL,
            BrainDecision.ActionType.valueOf("CALL_TOOL"));
        assertEquals(BrainDecision.ActionType.DELEGATE_AGENT,
            BrainDecision.ActionType.valueOf("DELEGATE_AGENT"));
        assertEquals(BrainDecision.ActionType.DELEGATE_REACT,
            BrainDecision.ActionType.valueOf("DELEGATE_REACT"));
        assertEquals(BrainDecision.ActionType.PLAN,
            BrainDecision.ActionType.valueOf("PLAN"));
        assertEquals(BrainDecision.ActionType.DIRECT_ANSWER,
            BrainDecision.ActionType.valueOf("DIRECT_ANSWER"));
        assertEquals(BrainDecision.ActionType.ASK_CLARIFY,
            BrainDecision.ActionType.valueOf("ASK_CLARIFY"));
        assertEquals(BrainDecision.ActionType.HITL_CONFIRM,
            BrainDecision.ActionType.valueOf("HITL_CONFIRM"));
    }

    @Test
    void testValueOfInvalidThrowsException() {
        assertThrows(IllegalArgumentException.class,
            () -> BrainDecision.ActionType.valueOf("UNKNOWN_TYPE"),
            "非法枚举值应抛 IllegalArgumentException");
    }

    @Test
    void testActionTypeUsedInDecision() {
        // 验证 ActionType 可正常用于 BrainDecision 构造
        BrainDecision decision = new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            "node_query",
            java.util.Map.of(),
            "测试决策",
            0.95,
            java.util.List.of()
        );
        assertEquals(BrainDecision.ActionType.CALL_TOOL, decision.actionType());
        assertEquals("node_query", decision.target());
    }

    @Test
    void testPlanDecision() {
        BrainDecision decision = new BrainDecision(
            BrainDecision.ActionType.PLAN,
            "plan",
            java.util.Map.of(),
            "用户要求先制定执行计划，不直接执行",
            0.90,
            java.util.List.of()
        );
        assertEquals(BrainDecision.ActionType.PLAN, decision.actionType());
        assertEquals("plan", decision.target());
    }

    @Test
    void testHitlConfirmDecision() {
        BrainDecision decision = new BrainDecision(
            BrainDecision.ActionType.HITL_CONFIRM,
            "deploy_delete",
            java.util.Map.of("id", "123"),
            "高危操作需要确认",
            0.92,
            java.util.List.of("token", "orgId")
        );
        assertEquals(BrainDecision.ActionType.HITL_CONFIRM, decision.actionType());
        assertEquals("deploy_delete", decision.target());
    }
}
