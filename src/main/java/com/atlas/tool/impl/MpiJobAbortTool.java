package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 中止MPI任务 Tool。
 * <p><b>⚠️ 危险操作</b>: P1级, 中止后任务不可恢复, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "mpi_job_abort"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 */
@Component
@AtlasToolMapping(
    name = "mpi_job_abort",
    agent = "deploy",
    intentId = "mpi_job_abort",
    description = "中止MPI分布式计算任务",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/mpi-job/abort/{id}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class MpiJobAbortTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public MpiJobAbortTool(KubeManagerHttpClient httpClient) {
        super("mpi_job_abort", "中止MPI任务");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[mpi_job_abort] 执行中止MPI任务");
        String id = params.get("id") != null ? params.get("id").toString() : "";
        if (id.isBlank()) {
            return AtlasToolResult.fail("缺少必需的参数: id（MPI任务ID）");
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/mpi-job/abort/" + id,
                Map.of()
            );
            Object data = extractData(response);
            String summary = "MPI任务已中止: ID=" + id;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[mpi_job_abort] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("MPI任务中止失败: " + e.getMessage());
        }
    }
}
