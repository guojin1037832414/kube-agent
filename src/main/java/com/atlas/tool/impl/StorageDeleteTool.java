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
 * 删除存储卷 Tool。
 * <p><b>⚠️ 危险操作</b>: P0级, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "storage_delete"}</p>
 * <p>Agent归属: storage | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "storage_delete",
    agent = "storage",
    intentId = "storage_delete",
    description = "删除存储卷",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/file/storage/{target}/delete"},
    operationType = AtlasToolMapping.OperationType.DELETE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class StorageDeleteTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public StorageDeleteTool(KubeManagerHttpClient httpClient) {
        super("storage_delete", "删除存储卷");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[storage_delete] 执行删除存储卷");
        String target = params.get("name") != null ? params.get("name").toString()
            : (params.get("userId") != null ? params.get("userId").toString() : "unknown");

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/file/storage/" + target + "/delete",
                Map.of()
            );
            Object data = extractData(response);
            String summary = "存储卷删除成功: " + target;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[storage_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("存储卷删除失败: " + e.getMessage());
        }
    }
}
