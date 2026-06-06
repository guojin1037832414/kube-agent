package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询当前用户 TensorBoard runs。
 *
 * <p>runs 数据用于分析训练曲线、实验轮次和训练状态，不改变 kube-manager 或 Kubernetes 状态。
 * 由于其中可能包含用户训练任务名称、路径或指标摘要，Agent 侧按敏感读取处理。</p>
 */
@Component
@AtlasToolMapping(
    name = "tensorboard_runs",
    agent = "query",
    intentId = "tensorboard_runs",
    description = "查询当前用户 TensorBoard runs，属于敏感读取",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/tensorboard/data/runs"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class TensorBoardRunsTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public TensorBoardRunsTool(KubeManagerHttpClient httpClient) {
        super("tensorboard_runs", "查询 TensorBoard runs");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/tensorboard/data/runs", Map.of());
            return AtlasToolResult.ok("TensorBoard runs 查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[tensorboard_runs] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("TensorBoard runs 查询失败: " + e.getMessage());
        }
    }
}
