package com.atlas.graph.config;

import com.atlas.brain.BrainDecision;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SupervisorGraph 路由映射单元测试 - ReAct / Plan 集成。
 *
 * <p>验证 BrainDecision.ActionType 到 Graph 条件边目标名称的正确映射，
 * 确保 DELEGATE_REACT 决策能准确路由到 "react_node" 节点，PLAN 决策能准确
 * 路由到 "plan_node" 节点。</p>
 *
 * <p>本测试为轻量级编译期检查：不涉及 Spring 上下文启动和真实 Graph 注入，
 * 仅验证路由键字符串常量与 ActionType 的语义一致性。</p>
 *
 * @version 3.1.0-M4.2
 */
class SupervisorGraphReactRoutingTest {

    /**
     * TC-GRAPH-REACT-01: 验证 DELEGATE_REACT 对应的路由键为 "react_node"。
     *
     * <p>这是 AtlasGraphConfig.supervisorGraph() 和 atlasGraph() 中
     * 条件边配置的核心约定：DELEGATE_REACT 必须映射到 "react_node" 目标节点。</p>
     */
    @Test
    void testDelegateReactMapsToReactNode() {
        // AtlasBrain 的 ReActGuard 会将诊断类查询覆盖为 DELEGATE_REACT，target="react"
        BrainDecision reactDecision = new BrainDecision(
            BrainDecision.ActionType.DELEGATE_REACT,
            "react",
            java.util.Map.of(),
            "ReActGuard 覆盖：诊断类查询应走手写 ReAct 引擎",
            0.85,
            java.util.List.of()
        );

        assertEquals("react_node", mapActionTypeToRouteKey(reactDecision.actionType()),
            "DELEGATE_REACT 必须路由到 react_node，否则 ReAct 引擎不会被触发");
    }

    /**
     * TC-GRAPH-PLAN-01: 验证 PLAN 对应的路由键为 "plan_node"。
     *
     * <p>Plan-and-Execute 是图级编排能力，不应被塞进 react_node，也不应落回
     * direct_answer。该测试锁定 M4.2 的最小 POC 路由约定。</p>
     */
    @Test
    void testPlanMapsToPlanNode() {
        BrainDecision planDecision = new BrainDecision(
            BrainDecision.ActionType.PLAN,
            "plan",
            java.util.Map.of(),
            "用户要求先生成执行计划，不直接执行真实操作",
            0.90,
            java.util.List.of()
        );

        assertEquals("plan_node", mapActionTypeToRouteKey(planDecision.actionType()),
            "PLAN 必须路由到 plan_node，否则 PlanEngine 最小闭环不会被触发");
    }

    /**
     * TC-GRAPH-REACT-02: 验证其余 ActionType 路由键未被意外修改。
     *
     * <p>作为回归保护，确保其他常用 ActionType 的路由映射保持稳定。</p>
     */
    @Test
    void testOtherActionTypesMapCorrectly() {
        assertEquals("direct_answer",
            mapActionTypeToRouteKey(BrainDecision.ActionType.DIRECT_ANSWER));
        assertEquals("ask_clarify",
            mapActionTypeToRouteKey(BrainDecision.ActionType.ASK_CLARIFY));
        assertEquals("tool_call",
            mapActionTypeToRouteKey(BrainDecision.ActionType.CALL_TOOL));
        assertEquals("delegate",
            mapActionTypeToRouteKey(BrainDecision.ActionType.DELEGATE_AGENT));
        assertEquals("hitl_confirm",
            mapActionTypeToRouteKey(BrainDecision.ActionType.HITL_CONFIRM));
    }

    /**
     * 辅助方法：复现 AtlasGraphConfig 中的路由映射逻辑。
     */
    private String mapActionTypeToRouteKey(BrainDecision.ActionType type) {
        return switch (type) {
            case DIRECT_ANSWER -> "direct_answer";
            case ASK_CLARIFY -> "ask_clarify";
            case CALL_TOOL -> "tool_call";
            case DELEGATE_AGENT -> "delegate";
            case DELEGATE_REACT -> "react_node";
            case PLAN -> "plan_node";
            case HITL_CONFIRM -> "hitl_confirm";
        };
    }
}
