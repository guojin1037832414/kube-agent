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
 * 节点查询 Tool — P1 阶段接入真实 kube-manager API。
 *
 * <p>意图映射：{@code intentId = "node_query"}，对应 "查询所有节点状态"。</p>
 * <p>API 路径：GET /api/{organizationId}/node?page=1&limit=100</p>
 */
@Component
@AtlasToolMapping(
    name = "node_query",
    agent = "query",
    intentId = "node_query",
    description = "查询 Kubernetes 集群所有节点的状态、资源使用情况",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/node"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NodeQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NodeQueryTool(KubeManagerHttpClient httpClient) {
        super("node_query", "查询 Kubernetes 集群所有节点的状态、资源使用情况");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of(); // 无必填参数
    }

    /**
     * 声明 page/limit-only 参数契约。
     *
     * <p>本 Tool 只允许模型控制分页参数，不暴露 keyword/name/search/kw 等搜索别名，
     * 避免只读展示或权限相关列表被扩展为批量枚举/探测入口。执行层会通过
     * {@link #buildPageLimitOnlyQuery(Map, int)} 使用同一份分页契约，防止出现
     * “Prompt 中可见参数但 HTTP 请求仍固定 page=1/limit=100”的伪 schema。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return pageLimitOnlyParameterSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);

            String path = "/api/" + orgId + "/node";
            Map<String, Object> response = httpClient.get(path, buildPageLimitOnlyQuery(params, 100));
            Object data = extractData(response);

            return AtlasToolResult.ok(listMessage("节点", data), data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[node_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("节点查询失败: " + e.getMessage());
        }
    }
}
