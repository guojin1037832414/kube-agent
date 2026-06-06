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
 * 查询 EasyFlow 实例阶段日志列表 Tool。
 *
 * <p>用于多 Pod/多副本场景下对比各 Pod 的日志片段，仍按敏感读取处理。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_instance_log_list",
    agent = "diag",
    intentId = "easy_flow_instance_log_list",
    description = "查询 EasyFlow 实例指定阶段的日志列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}/list"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowInstanceLogListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowInstanceLogListTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_instance_log_list", "查询 EasyFlow 实例指定阶段的日志列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("instanceId", "stageCode");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("instanceId", String.class),
            Map.entry("stageCode", String.class),
            Map.entry("limitBytes", Integer.class),
            Map.entry("sinceSeconds", Integer.class),
            Map.entry("tailLines", Integer.class),
            Map.entry("timestamps", Boolean.class)
        );
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return EasyFlowLogToolSupport.logParameterSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = EasyFlowLogToolSupport.logPath(resolveOrganizationId(params), params) + "/list";
            Map<String, Object> response = httpClient.get(path, EasyFlowLogToolSupport.logQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 实例日志列表查询完成", data);
        } catch (IllegalArgumentException e) {
            log.warn("[easy_flow_instance_log_list] 参数校验失败: {}", e.getMessage());
            return AtlasToolResult.fail("EasyFlow 实例日志列表查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[easy_flow_instance_log_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 实例日志列表查询失败: " + e.getMessage());
        }
    }
}
