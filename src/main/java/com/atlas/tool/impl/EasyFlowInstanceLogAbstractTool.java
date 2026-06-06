package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询 EasyFlow 实例日志摘要 Tool。
 *
 * <p>kube-manager 会根据 analyzerCode 调用内置解析器，把训练/测试/推理日志转换成结构化摘要。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_instance_log_abstract",
    agent = "diag",
    intentId = "easy_flow_instance_log_abstract",
    description = "查询 EasyFlow 实例指定阶段的日志摘要",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}/abstract"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowInstanceLogAbstractTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowInstanceLogAbstractTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_instance_log_abstract", "查询 EasyFlow 实例指定阶段的日志摘要");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("instanceId", "stageCode");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.of(
            "instanceId", String.class,
            "stageCode", String.class,
            "analyzerCode", String.class
        );
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("instanceId", "EasyFlow 实例 ID。", true,
                List.of("id", "flowInstanceId", "instance_id")),
            ToolParameterSpec.stringParam("stageCode", "流程阶段编码，例如 train、test、infer、stage1。", true,
                List.of("stage", "stage_code", "phase")),
            ToolParameterSpec.stringParam("analyzerCode", "日志解析器编码；不确定时先调用 easy_flow_analyzer_list。", false,
                List.of("analyzer", "parser", "parserCode"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = EasyFlowLogToolSupport.logPath(resolveOrganizationId(params), params) + "/abstract";
            Map<String, Object> query = new LinkedHashMap<>();
            Object analyzerCode = params.get("analyzerCode");
            if (analyzerCode != null && !String.valueOf(analyzerCode).isBlank()) {
                query.put("analyzerCode", String.valueOf(analyzerCode).trim());
            }
            Map<String, Object> response = httpClient.get(path, query.isEmpty() ? null : query);
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 实例日志摘要查询完成", data);
        } catch (IllegalArgumentException e) {
            log.warn("[easy_flow_instance_log_abstract] 参数校验失败: {}", e.getMessage());
            return AtlasToolResult.fail("EasyFlow 实例日志摘要查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[easy_flow_instance_log_abstract] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 实例日志摘要查询失败: " + e.getMessage());
        }
    }
}
