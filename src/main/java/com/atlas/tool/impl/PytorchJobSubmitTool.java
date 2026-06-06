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
 * 提交 PyTorch 训练任务 Tool。
 *
 * <p>该动作会把已保存的 PyTorchJob 提交到 Kubernetes 集群运行，属于会改变后端状态的高风险动作，
 * 必须经过 HITL 人工确认后才能执行。</p>
 */
@Component
@AtlasToolMapping(
    name = "pytorch_job_submit",
    agent = "deploy",
    intentId = "pytorch_job_submit",
    description = "提交 PyTorch 训练任务，会改变后端任务状态",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/pytorch-job/submit/{pyTorchJobId}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class PytorchJobSubmitTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PytorchJobSubmitTool(KubeManagerHttpClient httpClient) {
        super("pytorch_job_submit", "提交 PyTorch 训练任务");
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
                "要提交的 PyTorch Job ID。提交会改变任务状态，必须来自用户明确指定或任务详情/列表查询结果。",
                true,
                List.of("pyTorchJobId", "pytorchJobId", "jobId", "taskId")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        Object rawId = params.get("id");
        if (rawId == null || rawId.toString().isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: id（PyTorch 任务ID）", "MISSING_ID",
                List.of("请提供要提交的 PyTorch 任务 ID"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            String jobId = rawId.toString().trim();
            // 对齐成熟 kube-manager/vue-kube-manager：提交 PyTorchJob 使用路径变量，不使用 JSON body。
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/pytorch-job/submit/" + jobId,
                Map.of()
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("提交 PyTorch 训练任务请求已发送: ID=" + jobId, data);
        } catch (Exception e) {
            log.error("[pytorch_job_submit] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("提交 PyTorch 训练任务失败: " + e.getMessage());
        }
    }
}
