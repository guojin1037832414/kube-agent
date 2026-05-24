package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询可分配角色列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "role_assignable"}</p>
 * <p>Agent归属: rbac | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/role/assignable</p>
 * <p>备注: </p>
 */
@Component
@AtlasToolMapping(
    name = "role_assignable",
    agent = "rbac",
    intentId = "role_assignable",
    description = "查询可分配角色列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/role/assignable"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class RoleAssignableListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public RoleAssignableListTool(KubeManagerHttpClient httpClient) {
        super("role_assignable", "查询可分配角色列表");
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
            String path = "/api/{orgId}/role/assignable".replace("{orgId}", orgId);

            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询可分配角色列表完成", data);
        } catch (Exception e) {
            log.error("[role_assignable] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询可分配角色列表失败: " + e.getMessage());
        }
    }
}
