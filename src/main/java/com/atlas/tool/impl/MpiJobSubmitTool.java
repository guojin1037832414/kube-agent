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
 * 提交MPI分布式任务 Tool — 会改变后端状态的操作类接口。
 *
 * <p>⚠️ <b>安全警告</b>: 此为POST操作，会修改数据！</p>
 * <p>意图映射: {@code intentId = "mpi_job_submit"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 * <p>API路径: POST /api/{orgId}/mpi-job/submit/{mpiJobId}</p>
 */
@Component
@AtlasToolMapping(
    name = "mpi_job_submit",
    agent = "deploy",
    intentId = "mpi_job_submit",
    description = "提交MPI分布式任务，会修改后端状态",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/mpi-job/submit/{mpiJobId}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class MpiJobSubmitTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public MpiJobSubmitTool(KubeManagerHttpClient httpClient) {
        super("mpi_job_submit", "提交MPI分布式任务");
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
                "要提交的 MPI Job ID。提交会改变任务状态，必须来自用户明确指定或任务详情/列表查询结果。",
                true,
                List.of("mpiJobId", "jobId", "taskId")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Object rawId = params.get("id");
            if (rawId == null || rawId.toString().isBlank()) {
                return AtlasToolResult.fail("缺少必填参数: id（MPI任务ID）", "MISSING_ID",
                    java.util.List.of("请提供要提交的 MPI 任务 ID"));
            }

            // 对齐成熟 kube-manager/vue-kube-manager：提交 MPI 任务使用路径变量，不使用 JSON body。
            String path = "/api/" + orgId + "/mpi-job/submit/" + rawId.toString().trim();

            Map<String, Object> response = httpClient.post(path, Map.of());
            Object data = extractData(response);
            return AtlasToolResult.ok("提交MPI分布式任务请求已发送", data);
        } catch (Exception e) {
            log.error("[mpi_job_submit] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("提交MPI分布式任务失败: " + e.getMessage());
        }
    }
}
