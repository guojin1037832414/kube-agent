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
 * Namespace列表查询 Tool — 接入真实 kube-manager API。
 */
@Component
@AtlasToolMapping(
    name = "namespace_status",
    agent = "query",
    intentId = "namespace_status",
    description = "查询Namespace列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/namespace"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NamespaceQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NamespaceQueryTool(KubeManagerHttpClient httpClient) {
        super("namespace_status", "查询Namespace列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 namespace_status 的标准列表查询参数契约。
     *
     * <p>该 Tool 面向“查看 Namespace/命名空间列表”场景，默认可零参数查询；
     * 当用户补充命名空间名称、页码或条数时，LLM 应通过 page/limit/keyword
     * 这组 canonical 参数表达，执行层再统一透传给 kube-manager。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("命名空间名称或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/namespace";
            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("Namespace列表查询完成", data);
        } catch (Exception e) {
            log.error("[namespace_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Namespace列表查询失败: " + e.getMessage());
        }
    }
}
