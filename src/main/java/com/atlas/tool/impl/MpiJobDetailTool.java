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
 * 查询MPI分布式计算任务详情 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "mpi_job_detail"}</p>
 * <p>Agent归属: deploy | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/mpi-job</p>
 */
@Component
@AtlasToolMapping(
    name = "mpi_job_detail",
    agent = "deploy",
    intentId = "mpi_job_detail",
    description = "查询MPI分布式计算任务详情"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class MpiJobDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public MpiJobDetailTool(KubeManagerHttpClient httpClient) {
        super("mpi_job_detail", "查询MPI分布式计算任务详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/mpi-job".replace("{orgId}", orgId);
            Object idParam = params.get("id");
            if (idParam != null && !idParam.toString().isBlank()) {
                path += "/" + idParam;
            }
            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询MPI分布式计算任务详情完成", data);
        } catch (Exception e) {
            log.error("[mpi_job_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询MPI分布式计算任务详情失败: " + e.getMessage());
        }
    }
}
