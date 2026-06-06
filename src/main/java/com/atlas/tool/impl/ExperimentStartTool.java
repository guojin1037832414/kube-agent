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
 * 启动实验实例 Tool。
 *
 * <p>专家会诊结论：成熟前端 vue-kube-manager 的 experiment.js 将启动实例绑定到
 * {@code PUT /api/{organizationId}/experiment/instance/start/{id}}。本 Tool 只对齐这个已存在的
 * 前端契约，不再向 body 中塞 id，避免审批展示和真实 HTTP 动作不一致。</p>
 */
@Component
@AtlasToolMapping(
    name = "experiment_start",
    agent = "deploy",
    intentId = "experiment_start",
    description = "启动实验实例，会修改后端实例运行状态",
    httpMethod = "PUT",
    apiEndpoints = {"/api/{orgId}/experiment/instance/start/{id}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ExperimentStartTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ExperimentStartTool(KubeManagerHttpClient httpClient) {
        super("experiment_start", "启动实验实例");
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
                "实验实例 ID。该动作会启动实例，必须先经过用户确认。",
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
                "/api/" + orgId + "/experiment/instance/start/" + id,
                Map.of()
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("实验实例启动请求已发送: ID=" + id, data);
        } catch (Exception e) {
            log.error("[experiment_start] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("启动实验实例失败: " + e.getMessage());
        }
    }
}
