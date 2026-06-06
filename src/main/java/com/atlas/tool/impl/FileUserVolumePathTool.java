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
 * 查询当前用户优先级最高的挂载路径。
 *
 * <p>该路径通常会被训练任务、Notebook、课程环境等能力复用，属于个人文件系统上下文。只允许读取服务端
 * 基于登录态计算出的结果，不允许用户通过参数指定其他用户或组织。</p>
 */
@Component
@AtlasToolMapping(
    name = "file_user_volume_path",
    agent = "storage",
    intentId = "file_user_volume_path",
    description = "查询当前用户优先级最高的挂载路径",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/file/volume-path/user"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class FileUserVolumePathTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileUserVolumePathTool(KubeManagerHttpClient httpClient) {
        super("file_user_volume_path", "查询当前用户优先级最高的挂载路径");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/file/volume-path/user", Map.of());
            return AtlasToolResult.ok("查询用户挂载路径完成", extractData(response));
        } catch (Exception e) {
            log.error("[file_user_volume_path] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询用户挂载路径失败: " + e.getMessage());
        }
    }
}
