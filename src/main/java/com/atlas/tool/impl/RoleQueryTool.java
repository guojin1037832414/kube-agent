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
 * 查询角色列表 Tool。
 *
 * <p>意图映射: {@code intentId = "role_query"}</p>
 * <p>Agent归属: rbac | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "role_query",
    agent = "rbac",
    intentId = "role_query",
    description = "查询角色列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class RoleQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public RoleQueryTool(KubeManagerHttpClient httpClient) {
        super("role_query", "查询角色列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            log.info("[role_query] 执行查询角色列表");
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/role";
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("角色列表查询完成", data);
        } catch (Exception e) {
            log.error("[role_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("角色列表查询失败: " + e.getMessage());
        }
    }
}
