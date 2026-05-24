package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.exception.AtlasToolValidationException;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询Compose部署列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "compose_list"}</p>
 * <p>Agent归属: deploy | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/compose</p>
 */
@Component
@AtlasToolMapping(
    name = "compose_list",
    agent = "deploy",
    intentId = "compose_list",
    description = "查询Compose部署列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/compose"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ComposeListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ComposeListTool(KubeManagerHttpClient httpClient) {
        super("compose_list", "查询Compose部署列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("名称或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/compose".replace("{orgId}", orgId);
            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询Compose部署列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[compose_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询Compose部署列表失败: " + e.getMessage());
        }
    }
}
