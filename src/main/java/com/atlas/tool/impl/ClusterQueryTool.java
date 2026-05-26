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
 * 集群列表查询 Tool — 接入真实 kube-manager API。
 */
@Component
@AtlasToolMapping(
    name = "cluster_query",
    agent = "query",
    intentId = "cluster_query",
    description = "查询集群列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/hpc-job/cluster"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ClusterQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ClusterQueryTool(KubeManagerHttpClient httpClient) {
        super("cluster_query", "查询集群列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 cluster_query 的标准列表查询参数契约。
     *
     * <p>集群列表是典型只读检索 Tool，page/limit/keyword 足以覆盖分页浏览
     * 和按集群名称关键词筛选两类自然语言诉求。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("集群名称、队列名称或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/hpc-job/cluster";
            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("集群列表查询完成", data);
        } catch (Exception e) {
            log.error("[cluster_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("集群列表查询失败: " + e.getMessage());
        }
    }
}
