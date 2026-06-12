package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * query/path/body 支撑 helper 行为契约测试。
 *
 * <p>中文说明：这些断言保护 Agent 调用 kube-manager 前最后一道轻量参数收敛：
 * path ID 必须是成熟后端返回的正整数，充值 body 必须是白名单字段，文件/存储字符串只做最小非空收敛。
 * 这对学习 Agent 工程很重要，因为很多安全问题不是出在 LLM 生成答案，而是出在把候选参数拼进真实 HTTP。</p>
 *
 * <p>安全边界：测试不启动 Spring、不调用 kube-manager、不执行 Tool，也不写审计或记忆；
 * 它只验证 helper 在本地 fail-closed。</p>
 */
class QuerySupportPathAndBodyContractTest {

    @Test
    void pathIds_shouldRejectPathQueryScriptAndNumberShapeInjection() {
        assertThat(DownloadTaskQuerySupport.positiveTaskId(Map.of("id", "42"))).isEqualTo("42");
        assertThat(CoursewareQuerySupport.positiveCoursewareId(Map.of("coursewareId", "7"))).isEqualTo("7");
        assertThat(TemplateQuerySupport.positiveTemplateId(Map.of("templateId", "9"), "templateId", "应用模板"))
            .isEqualTo("9");
        assertThat(TensorBoardQuerySupport.positiveTensorBoardDeploymentId(Map.of("tensorBoardDeploymentId", "11")))
            .isEqualTo("11");

        assertPathIdRejected(() -> DownloadTaskQuerySupport.positiveTaskId(Map.of("id", "../42")));
        assertPathIdRejected(() -> CoursewareQuerySupport.positiveCoursewareId(Map.of("coursewareId", "42/extra")));
        assertPathIdRejected(() -> TemplateQuerySupport.positiveTemplateId(Map.of("templateId", "1?debug=true"), "templateId", "模板"));
        assertPathIdRejected(() -> TensorBoardQuerySupport.positiveTensorBoardDeploymentId(Map.of("tensorBoardDeploymentId", "1#frag")));
        assertPathIdRejected(() -> DownloadTaskQuerySupport.positiveTaskId(Map.of("id", "-1")));
        assertPathIdRejected(() -> CoursewareQuerySupport.positiveCoursewareId(Map.of("coursewareId", "1.5")));
    }

    @Test
    void fileStorageRequiredString_shouldTrimButNotPretendToAuthorizePath() {
        assertThat(FileStorageQuerySupport.requiredTrimmedString("  pvc-a  ", "name", "存储名称"))
            .isEqualTo("pvc-a");

        assertThatThrownBy(() -> FileStorageQuerySupport.requiredTrimmedString("   ", "name", "存储名称"))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("缺少必填参数");
    }

    @Test
    void userRiskMutation_shouldWhitelistTargetIdAndRechargeBodyOnly() {
        Map<String, Object> body = UserRiskMutationSupport.rechargeBody(Map.of(
            "targetUserId", "42",
            "amount", "10000",
            "remark", "  季度补贴  ",
            "organizationId", "999999",
            "token", "forged",
            "approved", true,
            "writeAllowed", true,
            "releaseDecision", "approved"
        ));

        assertThat(body)
            .containsEntry("userId", 42)
            .containsEntry("amount", 10000)
            .containsEntry("remark", "季度补贴")
            .doesNotContainKeys("organizationId", "token", "approved", "writeAllowed", "releaseDecision");
    }

    @Test
    void userRiskMutation_shouldRejectUnsafeTargetAndAmountBeforeHttp() {
        assertThatThrownBy(() -> UserRiskMutationSupport.targetUserId(Map.of("id", "42/extra")))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("目标用户 ID 必须是正整数");

        assertThatThrownBy(() -> UserRiskMutationSupport.rechargeBody(Map.of("id", "42", "amount", "-1")))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("充值金额 amount 必须是正整数");

        assertThatThrownBy(() -> UserRiskMutationSupport.rechargeBody(Map.of("id", "42", "amount", "999999999999999999999")))
            .isInstanceOf(AtlasToolValidationException.class)
            .hasMessageContaining("充值金额 amount 超出整数范围");
    }

    private void assertPathIdRejected(PathIdCall call) {
        assertThatThrownBy(call::invoke)
            .isInstanceOf(AtlasToolValidationException.class);
    }

    @FunctionalInterface
    private interface PathIdCall {
        String invoke();
    }
}
