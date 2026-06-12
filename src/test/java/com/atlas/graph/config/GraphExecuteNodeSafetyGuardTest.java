package com.atlas.graph.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Graph execute_node 入口安全契约测试。
 *
 * <p>中文说明：execute_node 是 Plan-and-Execute 的自动执行候选入口。即使当前只允许单步 READ，
 * PlanStep 仍然来自规划链路，不能携带或推导租户上下文。本测试锁定 execute_node 在创建
 * SafeToolExecutionRequest 之前必须先确认服务端可信 orgId，并且缺失时直接 fail-closed。</p>
 *
 * <p>安全边界：本测试只读取源码，不启动 Spring、不调用 LLM、不访问 kube-manager、不执行 Tool，
 * 也不写审计或记忆。它保护的是 Graph 入口契约：Plan 自动执行候选不能在租户边界不明时进入
 * SafeToolExecutor，更不能让 PlanStep.parameters 中的 orgId 成为控制面事实。</p>
 */
class GraphExecuteNodeSafetyGuardTest {

    private static final Path GRAPH_CONFIG = Path.of("src/main/java/com/atlas/graph/config/AtlasGraphConfig.java");

    @Test
    void executeNode_shouldResolveTrustedOrgIdBeforeCreatingSafeToolRequest() throws IOException {
        String executeNode = executeNodeSource();

        assertThat(executeNode)
            .as("execute_node 必须先从 Graph State 读取服务端写入的 orgId")
            .contains("String orgId = state.value(\"orgId\").map(Object::toString).orElse(\"\");");
        assertThat(executeNode)
            .as("Graph State 为空时只能从服务端 ThreadLocal 恢复，不能从 Plan 参数或前端字段猜租户")
            .contains("orgId = com.atlas.auth.UserPermissionContext.getCurrentOrgId();");
        assertThat(executeNode)
            .as("缺失可信 orgId 时必须在创建 SafeToolExecutionRequest 前停止")
            .contains("EXECUTE_TRUSTED_ORG_MISSING")
            .contains("Plan 自动执行候选缺少服务端可信 orgId");
        assertThat(executeNode.indexOf("EXECUTE_TRUSTED_ORG_MISSING"))
            .isLessThan(executeNode.indexOf("SafeToolExecutionRequest request = new SafeToolExecutionRequest"));
    }

    @Test
    void executeNode_shouldPassResolvedOrgIdAndNeverUsePlanParametersAsControlContext() throws IOException {
        String executeNode = executeNodeSource();
        String requestBlock = substringBetween(
            executeNode,
            "SafeToolExecutionRequest request = new SafeToolExecutionRequest",
            "SafeToolExecutionResult result = safeToolExecutor.executeIntent(request)"
        );

        assertThat(requestBlock)
            .as("SafeToolExecutionRequest 必须使用入口解析出的可信 orgId，而不是重新直接读 State 或 Plan 参数")
            .contains("orgId,")
            .doesNotContain("state.value(\"orgId\")")
            .doesNotContain("stepParameters.get(\"orgId\")")
            .doesNotContain("step.parameters()");
        assertThat(executeNode)
            .contains("orgId 必须来自 Graph State 或服务端 ThreadLocal，不能来自 PlanStep.parameters。")
            .contains("containsProtectedToolParam(stepParameters)")
            .contains("PROTECTED_PLAN_PARAMETER");
    }

    private String executeNodeSource() throws IOException {
        String source = Files.readString(GRAPH_CONFIG, StandardCharsets.UTF_8);
        return substringBetween(
            source,
            "private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildExecuteNode",
            "private static com.alibaba.cloud.ai.graph.action.AsyncNodeAction buildReActNode"
        );
    }

    private String substringBetween(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end);
        assertThat(startIndex).as("start marker must exist").isGreaterThanOrEqualTo(0);
        assertThat(endIndex).as("end marker must exist").isGreaterThan(startIndex);
        return source.substring(startIndex, endIndex);
    }
}
