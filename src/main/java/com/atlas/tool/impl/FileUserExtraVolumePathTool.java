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
 * 查询当前用户额外挂载路径。
 *
 * <p>额外挂载路径可能包含用户级扩展存储的落点，能帮助 AI 在创建任务前做路径建议，但不能扩大为文件浏览、
 * 预览或下载能力。</p>
 */
@Component
@AtlasToolMapping(
    name = "file_user_extra_volume_path",
    agent = "storage",
    intentId = "file_user_extra_volume_path",
    description = "查询当前用户额外挂载路径",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/file/volume-path/user-extra"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class FileUserExtraVolumePathTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileUserExtraVolumePathTool(KubeManagerHttpClient httpClient) {
        super("file_user_extra_volume_path", "查询当前用户额外挂载路径");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/file/volume-path/user-extra", Map.of());
            return AtlasToolResult.ok("查询用户额外挂载路径完成", extractData(response));
        } catch (Exception e) {
            log.error("[file_user_extra_volume_path] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询用户额外挂载路径失败: " + e.getMessage());
        }
    }
}
