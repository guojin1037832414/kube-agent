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
 * 查询资源使用列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "resource_usage_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/resource</p>
 */
@Component
@AtlasToolMapping(
    name = "resource_usage_list",
    agent = "query",
    intentId = "resource_usage_list",
    description = "查询资源使用列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/resource"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ResourceUsageListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ResourceUsageListTool(KubeManagerHttpClient httpClient) {
        super("resource_usage_list", "查询资源使用列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 暴露资源使用列表的标准分页与关键词参数契约。
     *
     * <p>资源使用列表属于组织内只读查询，本方法让 ReAct/LLM 可以通过结构化参数传入
     * page / limit / keyword，避免把分页条件写入自然语言或手工拼接 URL。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("资源名称、资源类型、命名空间/项目或使用情况关键词筛选条件，具体匹配字段以后端接口为准。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/resource";

            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询资源使用列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[resource_usage_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询资源使用列表失败: " + e.getMessage());
        }
    }
}
