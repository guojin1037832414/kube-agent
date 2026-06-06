package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询 EasyFlow 日志解析器列表 Tool。
 *
 * <p>解析器编码会传给日志摘要接口，例如训练日志、测试日志、推理日志等不同格式的摘要提取。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_analyzer_list",
    agent = "diag",
    intentId = "easy_flow_analyzer_list",
    description = "查询 EasyFlow 日志解析器列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/analyzer"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowAnalyzerListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowAnalyzerListTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_analyzer_list", "查询 EasyFlow 日志解析器列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/{orgId}/easy-flow/analyzer".replace("{orgId}", resolveOrganizationId(params));
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 日志解析器列表查询完成", data);
        } catch (Exception e) {
            log.error("[easy_flow_analyzer_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 日志解析器列表查询失败: " + e.getMessage());
        }
    }
}
