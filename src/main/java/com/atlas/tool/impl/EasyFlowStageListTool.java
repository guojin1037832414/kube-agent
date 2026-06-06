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
 * 查询 EasyFlow 流程阶段列表 Tool。
 *
 * <p>阶段元数据包含镜像、命令、资源与 analyzerCode，是日志摘要和训练失败分析的重要上下文。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_stage_list",
    agent = "query",
    intentId = "easy_flow_stage_list",
    description = "查询 EasyFlow 流程阶段列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/flow/{flowId}/stage"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowStageListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowStageListTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_stage_list", "查询 EasyFlow 流程阶段列表");
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
            "EasyFlow 流程 ID。",
            true,
            List.of("id", "flow_id")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/{orgId}/easy-flow/flow/{flowId}/stage"
                .replace("{orgId}", resolveOrganizationId(params))
                .replace("{flowId}", EasyFlowLogToolSupport.flowId(params));
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 流程阶段列表查询完成", data);
        } catch (IllegalArgumentException e) {
            log.warn("[easy_flow_stage_list] 参数校验失败: {}", e.getMessage());
            return AtlasToolResult.fail("EasyFlow 流程阶段列表查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[easy_flow_stage_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 流程阶段列表查询失败: " + e.getMessage());
        }
    }
}
