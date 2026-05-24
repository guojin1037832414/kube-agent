package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 停止实验实例 Tool。
 * <p><b>⚠️ 危险操作</b>: P1级, 停止后实验不再运行, 但可重新启动, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "experiment_instance_stop"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 */
@Component
@AtlasToolMapping(
    name = "experiment_instance_stop",
    agent = "deploy",
    intentId = "experiment_instance_stop",
    description = "停止实验实例",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/experiment/instance/stop/{id}"},
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
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[experiment_instance_stop] 执行停止实验实例");
        String id = params.get("id") != null ? params.get("id").toString() : "";
        if (id.isBlank()) {
            return AtlasToolResult.fail("缺少必需的参数: id（实验实例ID）");
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/experiment/instance/stop/" + id,
                Map.of()
            );
            Object data = extractData(response);
            String summary = "实验实例已停止: ID=" + id;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[experiment_instance_stop] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("实验实例停止失败: " + e.getMessage());
        }
    }
}
