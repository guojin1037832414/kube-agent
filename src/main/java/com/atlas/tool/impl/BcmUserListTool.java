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
 * 查询当前组织具备超算权限的用户列表。
 *
 * <p>该列表会暴露组织内可参与 HPC/BCM 资源分配的用户边界，因此不作为普通公开列表处理。
 * Tool 不接收 organizationId、page、keyword 等用户参数，避免扩大用户枚举面。</p>
 */
@Component
@AtlasToolMapping(
    name = "bcm_user_list",
    agent = "deploy",
    intentId = "bcm_user_list",
    description = "查询当前组织具备超算权限的用户列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/bcm/users"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class BcmUserListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public BcmUserListTool(KubeManagerHttpClient httpClient) {
        super("bcm_user_list", "查询当前组织具备超算权限的用户列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/bcm/users", Map.of());
            return AtlasToolResult.ok("BCM 用户列表查询完成", extractData(response));
        } catch (Exception e) {
            log.error("[bcm_user_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("BCM 用户列表查询失败: " + e.getMessage());
        }
    }
}
