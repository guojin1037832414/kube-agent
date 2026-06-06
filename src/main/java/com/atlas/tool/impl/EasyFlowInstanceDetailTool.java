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
 * 查询 EasyFlow 单个实例详情 Tool。
 *
 * <p>详情用于 AI 分析流程阶段、当前状态与后续日志定位，不包含任何会改变实例状态的动作。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_instance_detail",
    agent = "query",
    intentId = "easy_flow_instance_detail",
    description = "查询 EasyFlow 流程实例详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/instance/{instanceId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowInstanceDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowInstanceDetailTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_instance_detail", "查询 EasyFlow 流程实例详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("instanceId");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.of("instanceId", String.class);
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(ToolParameterSpec.stringParam(
            "instanceId",
            "EasyFlow 实例 ID，通常先通过 easy_flow_instance_list 查询获得。",
            true,
            List.of("id", "flowInstanceId", "instance_id")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/{orgId}/easy-flow/instance/{instanceId}"
                .replace("{orgId}", resolveOrganizationId(params))
                .replace("{instanceId}", EasyFlowLogToolSupport.instanceId(params));
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 实例详情查询完成", data);
        } catch (IllegalArgumentException e) {
            log.warn("[easy_flow_instance_detail] 参数校验失败: {}", e.getMessage());
            return AtlasToolResult.fail("EasyFlow 实例详情查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[easy_flow_instance_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 实例详情查询失败: " + e.getMessage());
        }
    }
}
