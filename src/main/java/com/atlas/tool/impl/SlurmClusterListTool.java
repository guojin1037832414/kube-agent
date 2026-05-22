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
 * 查询Slurm集群列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "slurm_cluster_list"}</p>
 * <p>Agent归属: deploy | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/bcm/slurm-cluster</p>
 */
@Component
@AtlasToolMapping(
    name = "slurm_cluster_list",
    agent = "deploy",
    intentId = "slurm_cluster_list",
    description = "查询Slurm集群列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class SlurmClusterListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public SlurmClusterListTool(KubeManagerHttpClient httpClient) {
        super("slurm_cluster_list", "查询Slurm集群列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 向 ReAct / Tool Schema 暴露标准列表查询参数，确保 LLM 生成的分页与关键词条件
     * 会和执行层的 {@link #buildListQuery(Map)} 保持同一套语义。
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("Slurm 集群名称或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/bcm/slurm-cluster";

            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询Slurm集群列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[slurm_cluster_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询Slurm集群列表失败: " + e.getMessage());
        }
    }
}
