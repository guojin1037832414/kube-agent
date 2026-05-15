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
 * 查询可编辑角色列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "role_editable"}</p>
 * <p>Agent归属: rbac | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/role/editable</p>
 * <p>备注: </p>
 */
@Component
@AtlasToolMapping(
    name = "role_editable",
    agent = "rbac",
    intentId = "role_editable",
    description = "查询可编辑角色列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class RoleEditableListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public RoleEditableListTool(KubeManagerHttpClient httpClient) {
        super("role_editable", "查询可编辑角色列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/{orgId}/role/editable".replace("{orgId}", orgId);

            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("查询可编辑角色列表完成", data);
        } catch (Exception e) {
            log.error("[role_editable] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询可编辑角色列表失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
