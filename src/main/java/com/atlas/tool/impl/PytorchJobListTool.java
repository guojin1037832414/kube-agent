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
 * 查询PyTorch任务列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "pytorch_job_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/pytorch-job</p>
 */
@Component
@AtlasToolMapping(
    name = "pytorch_job_list",
    agent = "query",
    intentId = "pytorch_job_list",
    description = "查询PyTorch任务列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class PytorchJobListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PytorchJobListTool(KubeManagerHttpClient httpClient) {
        super("pytorch_job_list", "查询PyTorch任务列表");
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
            String path = "/api/" + orgId + "/pytorch-job";

            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询PyTorch任务列表完成", data);
        } catch (Exception e) {
            log.error("[pytorch_job_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询PyTorch任务列表失败: " + e.getMessage());
        }
    }
}
