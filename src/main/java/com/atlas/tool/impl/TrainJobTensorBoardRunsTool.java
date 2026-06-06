package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询指定训练任务 TensorBoard 的 runs。
 *
 * <p>该 Tool 只读取已存在 TensorBoard deployment 的训练曲线索引，不创建或删除 TensorBoard。
 * path 参数必须是正整数，避免路径穿透；返回训练任务相关数据，因此按敏感读取处理。</p>
 */
@Component
@AtlasToolMapping(
    name = "trainjob_tensorboard_runs",
    agent = "query",
    intentId = "trainjob_tensorboard_runs",
    description = "查询指定训练任务 TensorBoard runs，属于敏感读取",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/tensorboard/trainjob-runs/{tensorBoardDeploymentId}"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class TrainJobTensorBoardRunsTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public TrainJobTensorBoardRunsTool(KubeManagerHttpClient httpClient) {
        super("trainjob_tensorboard_runs", "查询训练任务 TensorBoard runs");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("tensorBoardDeploymentId");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(new ToolParameterSpec(
            "tensorBoardDeploymentId",
            "integer",
            "TensorBoard deployment ID，来源应为 tensorboard_list 返回的数字 ID。",
            true,
            List.of("deploymentId", "tensorboardDeploymentId", "tensorBoardId", "id")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String tensorBoardDeploymentId = TensorBoardQuerySupport.positiveTensorBoardDeploymentId(params);
            String path = "/api/" + orgId + "/tensorboard/trainjob-runs/" + tensorBoardDeploymentId;
            Map<String, Object> response = httpClient.get(path, Map.of());
            return AtlasToolResult.ok("训练任务 TensorBoard runs 查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[trainjob_tensorboard_runs] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("训练任务 TensorBoard runs 查询失败: " + e.getMessage());
        }
    }
}
