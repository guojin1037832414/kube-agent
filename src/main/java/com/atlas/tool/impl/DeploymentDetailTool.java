package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询部署实例详情 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "deployment_detail"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/deployment</p>
 */
@Component
@AtlasToolMapping(
    name = "deployment_detail",
    agent = "query",
    intentId = "deployment_detail",
    description = "查询部署实例详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/deployment"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DeploymentDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DeploymentDetailTool(KubeManagerHttpClient httpClient) {
        super("deployment_detail", "查询部署实例详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    /**
     * 部署实例详情参数契约。
     *
     * <p>注意：当前 Tool 执行逻辑读取的 canonical 字段是 {@code name}。本轮作为小样本
     * 先不重构执行字段为 deploymentName，避免 schema 与实际读取逻辑不一致导致必填校验失败。
     * description 中明确该 name 仅表示 Deployment/实例名称。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "name",
                "要查询详情的 Deployment/实例名称。这里的 name 不是 Pod、Node、Service、Namespace 或用户名称。",
                true,
                List.of("deploymentName", "deployment_name", "deployment", "deploy", "deployName", "instanceName", "instance_name", "targetName", "target_name")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/deployment".replace("{orgId}", orgId);

            Object nameParam = params.get("name");
            Map<String, Object> query = new java.util.HashMap<>();
            query.put("page", "1");
            query.put("limit", "100");
            if (nameParam != null && !nameParam.toString().isBlank()) {
                query.put("name", nameParam.toString());
            }
            Map<String, Object> response = httpClient.get(path, query);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询部署实例详情完成", data);
        } catch (Exception e) {
            log.error("[deployment_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询部署实例详情失败: " + e.getMessage());
        }
    }
}
