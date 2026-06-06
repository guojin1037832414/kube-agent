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
 * 查询当前用户 TensorBoard 环境信息。
 *
 * <p>该接口只读取训练监控环境，不创建、更新或删除 TensorBoard。返回值可能包含用户训练路径、
 * 访问地址或环境状态，因此按敏感读取处理，并要求 HITL 确认。</p>
 */
@Component
@AtlasToolMapping(
    name = "tensorboard_environment",
    agent = "query",
    intentId = "tensorboard_environment",
    description = "查询当前用户 TensorBoard 环境信息，属于敏感读取",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/tensorboard/data/environment"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class TensorBoardEnvironmentTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public TensorBoardEnvironmentTool(KubeManagerHttpClient httpClient) {
        super("tensorboard_environment", "查询 TensorBoard 环境信息");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/tensorboard/data/environment", Map.of());
            return AtlasToolResult.ok("TensorBoard 环境信息查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[tensorboard_environment] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("TensorBoard 环境信息查询失败: " + e.getMessage());
        }
    }
}
