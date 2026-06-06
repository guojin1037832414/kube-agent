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
 * 查询训练 Job 模板详情 Tool。
 *
 * <p>该 Tool 只读取训练任务模板定义，帮助 AI 助手解释 MPI/PyTorch 任务创建前的默认配置。
 * 创建、更新、删除训练模板仍属于高风险配置变更，不在本批接入。</p>
 */
@Component
@AtlasToolMapping(
    name = "job_template_detail",
    agent = "deploy",
    intentId = "job_template_detail",
    description = "查询训练 Job 模板详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/train-job-template/{templateId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class JobTemplateDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public JobTemplateDetailTool(KubeManagerHttpClient httpClient) {
        super("job_template_detail", "查询训练 Job 模板详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("templateId");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(new ToolParameterSpec(
            "templateId",
            "integer",
            "训练 Job 模板 ID，来源应为 job_template_list 返回的数字 ID。",
            true,
            List.of("id", "template_id", "jobTemplateId", "job_template_id", "trainJobTemplateId")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String templateId = TemplateQuerySupport.positiveTemplateId(params, "templateId", "训练 Job 模板");
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/train-job-template/" + templateId, Map.of());
            return AtlasToolResult.ok("训练 Job 模板详情查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[job_template_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("训练 Job 模板详情查询失败: " + e.getMessage());
        }
    }
}
