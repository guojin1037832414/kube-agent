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
 * 查询指定 HPC 集群中的 Lmod module 列表。
 *
 * <p>module 名称和版本是作业提交前的重要上下文，也可能泄露集群软件栈。
 * 本 Tool 只做读取；安装、删除 module 都会改变共享 HPC 环境，继续 HOLD。</p>
 */
@Component
@AtlasToolMapping(
    name = "hpc_module_list",
    agent = "deploy",
    intentId = "hpc_module_list",
    description = "查询 HPC 集群 Lmod module 列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/hpc-env/modules"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class HpcModuleListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HpcModuleListTool(KubeManagerHttpClient httpClient) {
        super("hpc_module_list", "查询 HPC 集群 Lmod module 列表");
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

            // 成熟后端只需要 clusterId；不要把 moduleName、version、package 等字段透传成搜索条件。
            Map<String, Object> response = httpClient.get(
                "/api/" + orgId + "/hpc-env/modules",
                Map.of("clusterId", clusterId)
            );
            return AtlasToolResult.ok("HPC module 列表查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[hpc_module_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("HPC module 列表查询失败: " + e.getMessage());
        }
    }
}
