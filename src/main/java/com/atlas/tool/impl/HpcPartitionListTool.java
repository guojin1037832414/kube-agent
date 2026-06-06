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
 * 查询 HPC/Slurm 集群分区 Tool。
 *
 * <p>该 Tool 用于作业提交前的容量与队列分析，只读取成熟后端封装的分区信息；真正提交、
 * 删除或重提作业仍属于高风险动作，必须走单独 HITL Tool。</p>
 */
@Component
@AtlasToolMapping(
    name = "hpc_partition_list",
    agent = "deploy",
    intentId = "hpc_partition_list",
    description = "查询 HPC 集群分区列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/hpc-job/partition/{clusterId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class HpcPartitionListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HpcPartitionListTool(KubeManagerHttpClient httpClient) {
        super("hpc_partition_list", "查询 HPC 集群分区列表");
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
            "HPC/Slurm 集群 ID，来源应为 cluster_query 返回的数字 ID。",
            true,
            List.of("id", "slurmClusterId")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String clusterId = HpcJobQuerySupport.positiveId(params, "clusterId");
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/hpc-job/partition/" + clusterId, Map.of());
            return AtlasToolResult.ok("HPC 集群分区查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[hpc_partition_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("HPC 集群分区查询失败: " + e.getMessage());
        }
    }
}
