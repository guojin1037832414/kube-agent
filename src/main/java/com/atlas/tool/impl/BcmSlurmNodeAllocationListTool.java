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
 * 查询当前组织已分配的 Slurm 节点。
 *
 * <p>节点分配信息可用于创建 Slurm 集群前的资源盘点，但也会暴露组织算力边界。
 * 本 Tool 只读取 mature 后端固定接口，不透传筛选条件或跨组织参数。</p>
 */
@Component
@AtlasToolMapping(
    name = "bcm_slurm_node_allocation_list",
    agent = "deploy",
    intentId = "bcm_slurm_node_allocation_list",
    description = "查询当前组织已分配的 Slurm 节点",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/bcm/all-slurm-nodes"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class BcmSlurmNodeAllocationListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public BcmSlurmNodeAllocationListTool(KubeManagerHttpClient httpClient) {
        super("bcm_slurm_node_allocation_list", "查询当前组织已分配的 Slurm 节点");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/bcm/all-slurm-nodes", Map.of());
            return AtlasToolResult.ok("BCM Slurm 节点分配查询完成", extractData(response));
        } catch (Exception e) {
            log.error("[bcm_slurm_node_allocation_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("BCM Slurm 节点分配查询失败: " + e.getMessage());
        }
    }
}
