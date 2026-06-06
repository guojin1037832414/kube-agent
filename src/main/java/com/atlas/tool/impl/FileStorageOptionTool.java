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
 * 查询当前组织可用存储选项。
 *
 * <p>这是创建/扩容存储前的只读准备能力。返回值可能包含 NFS、Spectrum Scale、融合存储等开关和作用域，
 * 需按敏感配置读取处理，并且不允许用户传入自定义筛选条件。</p>
 */
@Component
@AtlasToolMapping(
    name = "file_storage_option",
    agent = "storage",
    intentId = "file_storage_option",
    description = "查询当前组织可用存储选项",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/file/storage/option"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class FileStorageOptionTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileStorageOptionTool(KubeManagerHttpClient httpClient) {
        super("file_storage_option", "查询当前组织可用存储选项");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/file/storage/option", Map.of());
            return AtlasToolResult.ok("查询存储选项完成", extractData(response));
        } catch (Exception e) {
            log.error("[file_storage_option] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询存储选项失败: " + e.getMessage());
        }
    }
}
