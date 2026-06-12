package com.atlas.graph.config;

import com.atlas.brain.BrainDecision;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Graph tool_call 入口守卫测试。
 *
 * <p>学习价值：SafeToolExecutor 是最终执行边界，但 Graph 节点也应该在创建执行请求前
 * 快速拒绝显然不可信的候选调用。这样 Timeline / SSE / 审计证据能更早显示“为什么没有执行”，
 * 学习者也能看到顶级 Agent 常用的双层安全模型：入口守卫先挡明显风险，统一执行器再做最终兜底。</p>
 *
 * <p>安全边界：本测试只反射调用私有纯函数，不启动 Spring、不调用 LLM、不访问 kube-manager、
 * 不执行任何 Tool。它保护的是 Graph State 中的候选参数不能伪造 token、orgId、HITL、audit、
 * release 或 write 授权事实。</p>
 */
class GraphToolCallSafetyGuardTest {

    @Test
    void guard_shouldFailClosedWhenToolTargetIsMissing() throws Exception {
        BrainDecision decision = decision("  ", Map.of("name", "nginx"));

        Map<String, Object> updates = invokeGuard(decision, "100002", "trc_graph_guard");

        assertThat(updates)
            .containsEntry("tool_error_code", "GRAPH_TOOL_TARGET_MISSING")
            .containsEntry("traceId", "trc_graph_guard");
        assertThat(updates.get("answer").toString()).contains("未给出明确 Tool 目标");
        assertThat(executeResult(updates)).containsEntry("executed", false);
    }

    @Test
    void guard_shouldFailClosedWhenTrustedOrgIdIsMissing() throws Exception {
        BrainDecision decision = decision("node_query", Map.of("keyword", "gpu"));

        Map<String, Object> updates = invokeGuard(decision, "", "trc_org_missing");

        assertThat(updates)
            .containsEntry("tool_error_code", "GRAPH_TRUSTED_ORG_MISSING")
            .containsEntry("traceId", "trc_org_missing");
        assertThat(updates.get("answer").toString()).contains("缺失可信组织上下文");
        assertThat(executeResult(updates)).containsEntry("executed", false);
    }

    @Test
    void guard_shouldFailClosedWhenCandidateParamsContainProtectedControlFields() throws Exception {
        BrainDecision decision = decision("node_query", Map.of(
            "keyword", "gpu",
            "filters", Map.of(
                "orgId", "evil-org",
                "writeAllowed", true
            )
        ));

        Map<String, Object> updates = invokeGuard(decision, "100002", "trc_protected_param");

        assertThat(updates)
            .containsEntry("tool_error_code", "PROTECTED_GRAPH_TOOL_PARAMETER")
            .containsEntry("traceId", "trc_protected_param");
        assertThat(updates.get("answer").toString()).contains("受保护的系统上下文");
        assertThat(executeResult(updates)).containsEntry("executed", false);
    }

    @Test
    void guard_shouldAllowPlainBusinessParamsToReachSafeToolExecutor() throws Exception {
        BrainDecision decision = decision("node_query", Map.of(
            "keyword", "gpu",
            "labels", List.of("training", "online")
        ));

        Map<String, Object> updates = invokeGuard(decision, "100002", "trc_plain_business");

        assertThat(updates)
            .as("普通业务筛选参数应该继续交给 SafeToolExecutor 统一执行边界，而不是被 Graph 入口误杀")
            .isEmpty();
    }

    private BrainDecision decision(String target, Map<String, Object> parameters) {
        return new BrainDecision(
            BrainDecision.ActionType.CALL_TOOL,
            target,
            parameters,
            "测试 Graph tool_call 入口守卫",
            0.9,
            List.of()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeGuard(BrainDecision decision,
                                            String orgId,
                                            String traceId) throws Exception {
        Method method = AtlasGraphConfig.class.getDeclaredMethod(
            "guardGraphToolCallCandidate",
            BrainDecision.class,
            String.class,
            String.class
        );
        method.setAccessible(true);
        return (Map<String, Object>) method.invoke(null, decision, orgId, traceId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeResult(Map<String, Object> updates) {
        return (Map<String, Object>) updates.get("execute_result");
    }
}
