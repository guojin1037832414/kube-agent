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
 * 查询组织注册审核列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "register_audit_list"}</p>
 * <p>Agent归属: rbac | 安全级别: P3</p>
 * <p>API路径: GET /api/register/organization</p>
 */
@Component
@AtlasToolMapping(
    name = "register_audit_list",
    agent = "rbac",
    intentId = "register_audit_list",
    description = "查询组织注册审核列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class RegisterAuditListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public RegisterAuditListTool(KubeManagerHttpClient httpClient) {
        super("register_audit_list", "查询组织注册审核列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/register/organization";
            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询组织注册审核列表完成", data);
        } catch (Exception e) {
            log.error("[register_audit_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询组织注册审核列表失败: " + e.getMessage());
        }
    }

}
