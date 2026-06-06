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
 * 查询已声明 PVC 可挂载选项。
 *
 * <p>该接口返回的是后端按当前组织命名空间匹配出的挂载候选，用于部署/训练任务参数准备。Tool 不允许用户传
 * namespace，避免绕过服务端 ns{organizationId} 的隔离约束。</p>
 */
@Component
@AtlasToolMapping(
    name = "file_claimed_volume_option_list",
    agent = "storage",
    intentId = "file_claimed_volume_option_list",
    description = "查询当前组织已声明 PVC 可挂载选项",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/file/claimed-volume-option"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class FileClaimedVolumeOptionListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileClaimedVolumeOptionListTool(KubeManagerHttpClient httpClient) {
        super("file_claimed_volume_option_list", "查询当前组织已声明 PVC 可挂载选项");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/file/claimed-volume-option", Map.of());
            return AtlasToolResult.ok("查询 PVC 挂载选项完成", extractData(response));
        } catch (Exception e) {
            log.error("[file_claimed_volume_option_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询 PVC 挂载选项失败: " + e.getMessage());
        }
    }
}
