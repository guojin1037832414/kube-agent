package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 终止 MPI 任务 Tool。
 *
 * <p>历史意图名叫 {@code mpi_job_abort}，但成熟 kube-manager 的真实语义是
 * {@code POST /api/{organizationId}/mpi-job/{jobId}} 停止运行中的 MPIJob，数据库记录会保留。
 * 这是会释放集群资源并改变任务状态的动作，必须经过 HITL 确认。</p>
 */
@Component
@AtlasToolMapping(
    name = "mpi_job_abort",
    agent = "deploy",
    intentId = "mpi_job_abort",
    description = "终止运行中的 MPI 分布式训练任务，会改变任务状态并释放资源",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/mpi-job/{jobId}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class MpiJobAbortTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public MpiJobAbortTool(KubeManagerHttpClient httpClient) {
        super("mpi_job_abort", "终止 MPI 任务");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "id",
                "要终止的 MPI Job ID 或 kube-manager 任务标识。终止会改变任务状态并释放资源，必须精确到单个任务。",
                true,
                List.of("mpiJobId", "jobId", "taskId")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        Object rawId = params.get("id");
        if (rawId == null || rawId.toString().isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: id（MPI任务ID）", "MISSING_ID",
                List.of("请提供要终止的 MPI 任务 ID"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            String jobId = rawId.toString().trim();
            // 对齐成熟 kube-manager：停止 MPIJob 是 POST /mpi-job/{jobId}，不是 /abort/{id}。
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/mpi-job/" + jobId,
                Map.of()
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("MPI任务终止请求已发送: ID=" + jobId, data);
        } catch (Exception e) {
            log.error("[mpi_job_abort] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("MPI任务终止失败: " + e.getMessage());
        }
    }
}
