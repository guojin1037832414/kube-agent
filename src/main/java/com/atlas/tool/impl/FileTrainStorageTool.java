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
 * 查询训练任务申请的存储上下文。
 *
 * <p>训练任务创建前需要知道后端认可的存储配置。本 Tool 只读取当前组织的训练存储信息，不能替代存储申请、
 * 扩容、删除等变更操作。</p>
 */
@Component
@AtlasToolMapping(
    name = "file_train_storage",
    agent = "storage",
    intentId = "file_train_storage",
    description = "查询训练任务申请的存储上下文",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/file/train-storage"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class FileTrainStorageTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileTrainStorageTool(KubeManagerHttpClient httpClient) {
        super("file_train_storage", "查询训练任务申请的存储上下文");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/file/train-storage", Map.of());
            return AtlasToolResult.ok("查询训练存储上下文完成", extractData(response));
        } catch (Exception e) {
            log.error("[file_train_storage] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询训练存储上下文失败: " + e.getMessage());
        }
    }
}
