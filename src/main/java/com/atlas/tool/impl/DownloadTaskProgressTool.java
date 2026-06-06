package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询下载任务进度 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "download_task_progress"}</p>
 * <p>Agent归属: storage | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/download/progress/{id}</p>
 */
@Component
@AtlasToolMapping(
    name = "download_task_progress",
    agent = "storage",
    intentId = "download_task_progress",
    description = "查询指定下载任务的实时进度",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/download/progress/{id}"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DownloadTaskProgressTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DownloadTaskProgressTool(KubeManagerHttpClient httpClient) {
        super("download_task_progress", "查询指定下载任务的实时进度");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    /**
     * 下载进度接口必须按任务 id 定位，不接受分页或关键词筛选。
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(DownloadTaskQuerySupport.taskIdSpec());
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String id = DownloadTaskQuerySupport.positiveTaskId(params);
            String path = "/api/" + orgId + "/download/progress/" + id;

            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = extractData(response);
            return AtlasToolResult.ok("查询指定下载任务进度完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[download_task_progress] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询指定下载任务进度失败: " + e.getMessage());
        }
    }
}
