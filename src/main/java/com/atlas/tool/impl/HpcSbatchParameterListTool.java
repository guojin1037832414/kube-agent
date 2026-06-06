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
 * 查询 Slurm sbatch 参数模板 Tool。
 *
 * <p>sbatch 参数是 HPC 作业提交前的只读准备数据。Tool 会校验并编码 category 路径片段，
 * 不允许把命令、脚本或路径穿透到后端。</p>
 */
@Component
@AtlasToolMapping(
    name = "hpc_sbatch_parameter_list",
    agent = "deploy",
    intentId = "hpc_sbatch_parameter_list",
    description = "查询 Slurm sbatch 参数分类列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/hpc-job/sbatch_parameter/{category}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class HpcSbatchParameterListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HpcSbatchParameterListTool(KubeManagerHttpClient httpClient) {
        super("hpc_sbatch_parameter_list", "查询 Slurm sbatch 参数分类列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("category");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(ToolParameterSpec.stringParam(
            "category",
            "sbatch 参数分类名称，例如 Basic Job Information 或 Resource Allocation。",
            true,
            List.of("parameterCategory", "sbatchCategory")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String category = HpcJobQuerySupport.categorySegment(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/hpc-job/sbatch_parameter/" + category, Map.of());
            return AtlasToolResult.ok("Slurm sbatch 参数查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[hpc_sbatch_parameter_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Slurm sbatch 参数查询失败: " + e.getMessage());
        }
    }
}
