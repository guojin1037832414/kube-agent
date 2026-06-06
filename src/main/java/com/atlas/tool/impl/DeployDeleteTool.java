package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 删除部署实例 Tool。
 * <p><b>⚠️ 危险操作</b>: P0级, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "deploy_delete"}</p>
 * <p>Agent归属: deploy | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "deploy_delete",
    agent = "deploy",
    intentId = "deploy_delete",
    description = "删除部署实例",
    httpMethod = "DELETE",
    apiEndpoints = {"/api/{orgId}/deployment?name={name}"},
    operationType = AtlasToolMapping.OperationType.DELETE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class DeployDeleteTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DeployDeleteTool(KubeManagerHttpClient httpClient) {
        super("deploy_delete", "删除部署实例");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "name",
                "要删除的 Deployment/实例名称。删除是高风险操作，必须精确到实例名称，不能传组织、用户或模糊关键词。",
                true,
                List.of("deploymentName", "instanceName", "targetName")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[deploy_delete] 执行删除部署实例");
        String target = params.get("name") != null ? params.get("name").toString().trim() : "";
        if (target.isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: name（部署实例名称）", "MISSING_NAME",
                List.of("请提供要删除的部署实例名称"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            // 对齐成熟 kube-manager：DELETE /api/{organizationId}/deployment?name=xxx
            // 删除属于 P0 高风险操作，外层 HITL 与真实用户 Token 校验必须先通过。
            Map<String, Object> response = httpClient.delete(
                "/api/" + orgId + "/deployment",
                Map.of("name", target)
            );
            Object data = extractData(response);
            String summary = "实例删除成功: " + target;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[deploy_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("实例删除失败: " + e.getMessage());
        }
    }
}
