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
 * 查询 EasyFlow 单个流程阶段详情 Tool。
 *
 * <p>用于在 AI 总结日志前解释该阶段运行的镜像、命令、资源请求和日志解析器。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_stage_detail",
    agent = "query",
    intentId = "easy_flow_stage_detail",
    description = "查询 EasyFlow 流程阶段详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/flow/{flowId}/stage/{stageId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowStageDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowStageDetailTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_stage_detail", "查询 EasyFlow 流程阶段详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("flowId", "stageId");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.of(
            "flowId", String.class,
            "stageId", String.class
        );
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("flowId", "EasyFlow 流程 ID。", true,
                List.of("flow_id")),
            ToolParameterSpec.stringParam("stageId", "EasyFlow 阶段 ID。", true,
                List.of("id", "stage_id"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/{orgId}/easy-flow/flow/{flowId}/stage/{stageId}"
                .replace("{orgId}", resolveOrganizationId(params))
                .replace("{flowId}", EasyFlowLogToolSupport.flowId(params))
                .replace("{stageId}", EasyFlowLogToolSupport.stageId(params));
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 流程阶段详情查询完成", data);
        } catch (IllegalArgumentException e) {
            log.warn("[easy_flow_stage_detail] 参数校验失败: {}", e.getMessage());
            return AtlasToolResult.fail("EasyFlow 流程阶段详情查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[easy_flow_stage_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 流程阶段详情查询失败: " + e.getMessage());
        }
    }
}
