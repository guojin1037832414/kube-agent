package com.atlas.support;

import com.atlas.intent.config.IntentDefinition;
import com.atlas.intent.core.IntentResult;
import com.atlas.intent.core.ScoreNormalizer;
import com.atlas.intent.llm.L3ClassificationResult;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.defaults.IntentDefaults;
import com.atlas.tool.execution.SafeToolExecutionResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Batch 5 支撑契约行为测试。
 *
 * <p>中文说明：本测试覆盖第二片支撑契约层的“硬行为”：
 * Tool 参数 schema、执行回执、意图分数、LLM 分类结果和默认值补参都只是候选输入、
 * 展示结果或路由信号，不能变成权限、HITL、审计、release 或 kube-manager 写授权。</p>
 *
 * <p>安全边界：测试不启动 Spring、不调用 LLM/Embedding/Tool/MCP/kube-manager，
 * 也不写审计或记忆；它只验证这些轻量对象自身的不可变、防注入和候选信号语义。</p>
 */
class Batch5SupportContractBehaviorTest {

    @Test
    void toolParameterSpec_shouldTreatAliasesAsImmutableBusinessHints() {
        ToolParameterSpec nullAliasSpec =
            ToolParameterSpec.stringParam("namespace", "命名空间", false, null);
        ToolParameterSpec copiedAliasSpec =
            ToolParameterSpec.stringParam("podName", "Pod 名称", true, List.of("pod_name", "name"));

        assertThat(nullAliasSpec.aliases())
            .as("aliases 为空时应转为不可变空列表，避免调用方运行期追加受保护字段")
            .isEmpty();
        assertThatThrownBy(() -> copiedAliasSpec.aliases().add("orgId"))
            .as("ToolParameterSpec aliases 是 LLM 业务别名提示，不允许后续被动态篡改")
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(copiedAliasSpec.required())
            .as("required 只影响 schema/提示，不代表权限或写操作授权")
            .isTrue();
    }

    @Test
    void safeToolExecutionResult_shouldSeparateExecutionClarificationAndGraphDisplay() {
        SafeToolExecutionResult blocked = SafeToolExecutionResult.notExecuted("权限不足", "");
        Map<String, Object> blockedUpdates = blocked.toGraphUpdates();

        assertThat(blocked.executed()).isFalse();
        assertThat(blocked.success()).isFalse();
        assertThat(blockedUpdates)
            .as("未执行结果不能写入 tool_result 或 traceId，避免前端把阻断态误读为真实 Tool 输出")
            .containsEntry("answer", "权限不足")
            .doesNotContainKeys("tool_result", "traceId");

        SafeToolExecutionResult clarify = SafeToolExecutionResult.executed(
            false,
            "缺少参数",
            Map.of("errorCode", "MISSING_PARAM", "suggestions", List.of("gpuSpec")),
            "trc_0123456789abcdef0123456789abcdef"
        );

        assertThat(clarify.executed()).isTrue();
        assertThat(clarify.success()).isFalse();
        assertThat(clarify.requiresClarification())
            .as("结构化 errorCode/suggestions 是澄清信号，不是自动执行授权")
            .isTrue();
        assertThat(clarify.toGraphUpdates())
            .containsEntry("requires_clarification", true)
            .containsEntry("tool_error_code", "MISSING_PARAM")
            .containsEntry("traceId", "trc_0123456789abcdef0123456789abcdef");
    }

    @Test
    void intentScores_shouldRemainRoutingSignalsOnlyAndClampIntoSafeRange() {
        IntentDefinition definition = new IntentDefinition(
            "pod_query",
            "查询 Pod",
            "query",
            "p2",
            List.of("pod"),
            List.of(),
            List.of("查看 pod")
        );

        IntentResult result = IntentResult.of(definition, 95.0, "L3", false, "查看 pod");

        assertThat(result.confidence())
            .as("L3 百分比分数会被保守归一化，只能用于候选排序")
            .isEqualTo(0.9025);
        assertThat(result.withNormalizedScore(1.5).confidence())
            .as("重新校准不能超过 1.0，避免观测分变成绝对授权")
            .isEqualTo(1.0);
        assertThat(result.withNormalizedScore(-0.5).confidence())
            .as("重新校准不能低于 0.0")
            .isEqualTo(0.0);
        assertThat(ScoreNormalizer.normalize(2.0, "UNKNOWN", false))
            .as("未知层级只做 clamp，不创建新的高权限层级")
            .isEqualTo(1.0);
    }

    @Test
    void l3ClassificationResult_shouldTreatLlmOutputAsUntrustedCandidate() {
        L3ClassificationResult confident = new L3ClassificationResult("pod_query", 0.8, "语义相似");
        L3ClassificationResult blankIntent = new L3ClassificationResult("", 0.99, "模型猜测");
        L3ClassificationResult unknown = new L3ClassificationResult("unknown", 0.99, "无法判断");
        L3ClassificationResult lowConfidence = new L3ClassificationResult("pod_query", 0.49, "低置信度");

        assertThat(confident.isConfident(0.7)).isTrue();
        assertThat(blankIntent.isConfident(0.7))
            .as("空 intentId 即使置信度高，也不能进入可执行候选")
            .isFalse();
        assertThat(unknown.isUnknown()).isTrue();
        assertThat(lowConfidence.isUnknown())
            .as("低置信度应触发降级/澄清，不能让模型猜参数")
            .isTrue();
    }

    @Test
    void intentDefaults_shouldStripProtectedKeysAndExposeImmutableDraftValues() {
        IntentDefaults defaults = new IntentDefaults("deploy_create", Map.of(
            "replicas", 1,
            "token", "secret",
            "nested", Map.of("orgId", "100001", "safeName", "demo"),
            "list", List.of(Map.of("Authorization", "Bearer secret", "label", "safe"))
        ));

        assertThat(defaults.parameters())
            .as("默认值只能保留普通表单草稿字段")
            .containsEntry("replicas", 1)
            .doesNotContainKey("token");
        assertThat(asMap(defaults.getDefault("nested")))
            .containsEntry("safeName", "demo")
            .doesNotContainKey("orgId");
        assertThat(asMap(asList(defaults.getDefault("list")).get(0)))
            .containsEntry("label", "safe")
            .doesNotContainKey("Authorization");

        assertThatThrownBy(() -> defaults.parameters().put("writeAllowed", true))
            .as("顶层默认值 Map 不可变，避免运行期追加写授权字段")
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> asMap(defaults.getDefault("nested")).put("userId", "alice"))
            .as("嵌套默认值 Map 也必须不可变")
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> asList(defaults.getDefault("list")).add(Map.of("releaseApproved", true)))
            .as("嵌套默认值 List 也必须不可变")
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object raw) {
        assertThat(raw).isInstanceOf(Map.class);
        return (Map<String, Object>) raw;
    }

    @SuppressWarnings("unchecked")
    private List<Object> asList(Object raw) {
        assertThat(raw).isInstanceOf(List.class);
        return (List<Object>) raw;
    }
}
