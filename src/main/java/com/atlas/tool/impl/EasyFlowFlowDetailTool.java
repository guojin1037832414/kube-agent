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
 * 查询 EasyFlow 流程详情 Tool。
 *
 * <p>详情通常包含流程描述、阶段列表和输出文件定义，可作为 AI 解释训练流程的结构化上下文。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_flow_detail",
    agent = "query",
    intentId = "easy_flow_flow_detail",
    description = "查询 EasyFlow 流程详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/flow/{flowId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowFlowDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowFlowDetailTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_flow_detail", "查询 EasyFlow 流程详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("flowId");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.of("flowId", String.class);
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(ToolParameterSpec.stringParam(
            "flowId",
            "EasyFlow 流程 ID，通常先通过 easy_flow_flow_list 查询获得。",
            true,
            List.of("id", "flow_id")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/{orgId}/easy-flow/flow/{flowId}"
                .replace("{orgId}", resolveOrganizationId(params))
                .replace("{flowId}", EasyFlowLogToolSupport.flowId(params));
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 流程详情查询完成", data);
        } catch (IllegalArgumentException e) {
            log.warn("[easy_flow_flow_detail] 参数校验失败: {}", e.getMessage());
            return AtlasToolResult.fail("EasyFlow 流程详情查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[easy_flow_flow_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 流程详情查询失败: " + e.getMessage());
        }
    }
}
