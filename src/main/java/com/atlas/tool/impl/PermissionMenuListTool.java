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
 * 查询权限菜单列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "permission_menu_list"}</p>
 * <p>Agent归属: rbac | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/permission/menu</p>
 */
@Component
@AtlasToolMapping(
    name = "permission_menu_list",
    agent = "rbac",
    intentId = "permission_menu_list",
    description = "查询权限菜单列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class PermissionMenuListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PermissionMenuListTool(KubeManagerHttpClient httpClient) {
        super("permission_menu_list", "查询权限菜单列表");
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
            String path = "/api/{orgId}/permission/menu".replace("{orgId}", orgId);
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询权限菜单列表完成", data);
        } catch (Exception e) {
            log.error("[permission_menu_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询权限菜单列表失败: " + e.getMessage());
        }
    }
}
