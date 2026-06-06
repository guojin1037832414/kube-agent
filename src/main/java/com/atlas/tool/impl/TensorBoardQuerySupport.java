package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;
import java.util.Map;

/**
 * TensorBoard 只读 Tool 的路径参数校验辅助类。
 *
 * <p>成熟 kube-manager 的 trainjob TensorBoard runs 接口会把 deploymentId 放进 URL path。
 * Agent 侧必须只接受正整数，避免 LLM 或用户输入把请求路径导向非预期接口。</p>
 */
final class TensorBoardQuerySupport {

    private TensorBoardQuerySupport() {
    }

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
