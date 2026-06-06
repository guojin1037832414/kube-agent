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
 * 停止实验实例 Tool。
 *
 * <p>成熟前端将“停止实例”实现为 shutdown 动作：
 * {@code PUT /api/{organizationId}/experiment/instance/shutdown/{id}}。为了兼容既有 agent 意图，
 * Tool 名仍保留 {@code experiment_instance_stop}，但实际 HTTP 契约对齐 shutdown。</p>
 */
@Component
@AtlasToolMapping(
    name = "experiment_instance_stop",
    agent = "deploy",
    intentId = "experiment_instance_stop",
    description = "停止实验实例，会关闭实例运行状态",
    httpMethod = "PUT",
    apiEndpoints = {"/api/{orgId}/experiment/instance/shutdown/{id}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ExperimentInstanceStopTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ExperimentInstanceStopTool(KubeManagerHttpClient httpClient) {
        super("experiment_instance_stop", "停止实验实例");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "id",
                "实验实例 ID。该动作会关闭实例，必须先经过用户确认。",
                true,
                List.of("instanceId", "experimentInstanceId", "targetId")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String id = params.get("id").toString().trim();
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.put(
                "/api/" + orgId + "/experiment/instance/shutdown/" + id,
                Map.of()
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("实验实例停止请求已发送: ID=" + id, data);
        } catch (Exception e) {
            log.error("[experiment_instance_stop] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("实验实例停止失败: " + e.getMessage());
        }
    }
}
