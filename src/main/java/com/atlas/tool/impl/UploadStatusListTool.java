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
 * 查询下载任务状态 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "upload_status_list"}</p>
 * <p>Agent归属: storage | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/download/status/{id}</p>
 */
@Component
@AtlasToolMapping(
    name = "upload_status_list",
    agent = "storage",
    intentId = "upload_status_list",
    description = "查询指定下载任务的状态",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/download/status/{id}"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class UploadStatusListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public UploadStatusListTool(KubeManagerHttpClient httpClient) {
        super("upload_status_list", "查询指定下载任务的状态");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    /**
     * 成熟 kube-manager 的状态接口必须按任务 id 定位。
     *
     * <p>下载任务列表由 {@code download_task_list} 负责，本 Tool 只读取某个任务的当前状态。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(new ToolParameterSpec(
            "id",
            "integer",
            "下载任务 ID，必须来自 download_task_list 返回的数字 ID。",
            true,
            List.of("taskId", "task_id", "downloadTaskId", "download_task_id")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String id = positiveTaskId(params);
            String path = "/api/" + orgId + "/download/status/" + id;

            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = extractData(response);
            return AtlasToolResult.ok("查询指定下载任务状态完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[upload_status_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询指定下载任务状态失败: " + e.getMessage());
        }
    }

    private String positiveTaskId(Map<String, Object> params) {
        Object raw = params.get("id");
        if (raw == null || raw.toString().isBlank()) {
            throw new AtlasToolValidationException(
                "缺少必填参数: id",
                "MISSING_DOWNLOAD_TASK_ID",
                List.of("请先通过 download_task_list 查询下载任务，再使用返回的数字 ID 查询状态")
            );
        }
        String value = raw.toString().trim();
        if (!value.matches("[1-9]\\d*")) {
            throw new AtlasToolValidationException(
                "下载任务 ID 仅支持正整数: " + value,
                "INVALID_DOWNLOAD_TASK_ID",
                List.of("id 会进入 URL path，必须使用成熟后端返回的数字 ID，不能包含路径、脚本或查询字符串")
            );
        }
        return value;
    }
}
