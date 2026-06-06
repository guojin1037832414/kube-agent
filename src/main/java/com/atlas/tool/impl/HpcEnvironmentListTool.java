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
 * 查询指定 HPC 集群中的 Miniconda 环境列表。
 *
 * <p>环境名和包信息会暴露集群内部软件栈，虽然是 GET 读取，也按敏感读取处理。
 * 创建、删除环境以及安装包会改变真实 HPC 环境，必须由后续高风险 Tool 单独接入。</p>
 */
@Component
@AtlasToolMapping(
    name = "hpc_environment_list",
    agent = "deploy",
    intentId = "hpc_environment_list",
    description = "查询 HPC 集群 Miniconda 环境列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/hpc-env/environments/{clusterId}"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class HpcEnvironmentListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HpcEnvironmentListTool(KubeManagerHttpClient httpClient) {
        super("hpc_environment_list", "查询 HPC 集群 Miniconda 环境列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("clusterId");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(new ToolParameterSpec(
            "clusterId",
            "integer",
            "HPC/Slurm 集群 ID，必须来自 cluster_query 返回的数字 ID。",
            true,
            List.of("id", "slurmClusterId")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String clusterId = HpcJobQuerySupport.positiveId(params, "clusterId");

            // clusterId 是路径片段，只允许正整数，避免把 LLM 生成的路径或命令拼进 URL。
            Map<String, Object> response = httpClient.get(
                "/api/" + orgId + "/hpc-env/environments/" + clusterId,
                Map.of()
            );
            return AtlasToolResult.ok("HPC 环境列表查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[hpc_environment_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("HPC 环境列表查询失败: " + e.getMessage());
        }
    }
}
