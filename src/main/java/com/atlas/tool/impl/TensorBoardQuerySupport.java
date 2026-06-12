package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;
import java.util.Map;

/**
 * TensorBoard 只读 Tool 的路径参数校验辅助类。
 *
 * <p>中文说明：成熟 kube-manager 的 trainjob TensorBoard runs 接口会把 deploymentId 放进 URL path。
 * 输入可能来自 tensorboard_list 的返回、LLM 提取或前端选择；输出会拼进训练任务 runs 查询路径，
 * 因此必须先收敛成正整数文本。</p>
 *
 * <p>安全边界：deploymentId 只是 TensorBoard 资源定位符，不代表用户有权读取训练任务 runs。
 * 这里拒绝路径、query、fragment、脚本、负数和小数，避免把只读查询导向非预期接口；
 * 真实读取仍由可信 token/orgId、ToolPermission、敏感读取确认和 kube-manager 权限共同约束。</p>
 */
final class TensorBoardQuerySupport {

    private TensorBoardQuerySupport() {
    }

    /**
     * 提取并校验 TensorBoard deploymentId。
     *
     * <p>中文说明：校验失败会在 Tool 层转成结构化失败和补参建议，不触发 HTTP client。</p>
     */
    static String positiveTensorBoardDeploymentId(Map<String, Object> params) {
        Object raw = params.get("tensorBoardDeploymentId");
        if (raw == null || raw.toString().isBlank()) {
            throw new AtlasToolValidationException(
                "缺少必填参数: tensorBoardDeploymentId",
                "MISSING_TENSORBOARD_DEPLOYMENT_ID",
                List.of("请先通过 tensorboard_list 查询 TensorBoard，再使用返回的数字 deploymentId 查询训练任务 runs"));
        }

        String value = raw.toString().trim();
        if (!value.matches("[1-9]\\d*")) {
            throw new AtlasToolValidationException(
                "TensorBoard deploymentId 仅支持正整数: " + value,
                "INVALID_TENSORBOARD_DEPLOYMENT_ID",
                List.of("请提供 tensorboard_list 返回的数字 tensorBoardDeploymentId，不能包含 /、?、# 等路径字符"));
        }
        return value;
    }
}
