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
 * 查询 EasyFlow 实例列表 Tool。
 *
 * <p>该接口只读取流程实例元数据，不推进、清理或删除实例；后续日志分析 Tool 会依赖这里返回的实例 ID。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_instance_list",
    agent = "query",
    intentId = "easy_flow_instance_list",
    description = "查询 EasyFlow 流程实例列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/instance"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowInstanceListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowInstanceListTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_instance_list", "查询 EasyFlow 流程实例列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("流程名称、实例名称、阶段或状态等筛选关键词");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/{orgId}/easy-flow/instance".replace("{orgId}", resolveOrganizationId(params));
            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 实例列表查询完成", data);
        } catch (Exception e) {
            log.error("[easy_flow_instance_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 实例列表查询失败: " + e.getMessage());
        }
    }
}
