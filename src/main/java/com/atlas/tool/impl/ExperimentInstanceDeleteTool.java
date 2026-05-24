package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 删除实验实例 Tool。
 * <p><b>⚠️ 危险操作</b>: P0级, 删除后无法恢复, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "experiment_instance_delete"}</p>
 * <p>Agent归属: deploy | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "experiment_instance_delete",
    agent = "deploy",
    intentId = "experiment_instance_delete",
    description = "删除实验实例",
    httpMethod = "DELETE",
    apiEndpoints = {"/api/{orgId}/experiment/instance/{id}"},
    operationType = AtlasToolMapping.OperationType.DELETE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class ExperimentInstanceDeleteTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ExperimentInstanceDeleteTool(KubeManagerHttpClient httpClient) {
        super("experiment_instance_delete", "删除实验实例");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[experiment_instance_delete] 执行删除实验实例");
        String id = params.get("id") != null ? params.get("id").toString() : "";
        if (id.isBlank()) {
            return AtlasToolResult.fail("缺少必需的参数: id（实验实例ID）");
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.delete(
                "/api/" + orgId + "/experiment/instance/" + id,
                Map.of()
            );
            Object data = extractData(response);
            String summary = "实验实例已删除: ID=" + id;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[experiment_instance_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("实验实例删除失败: " + e.getMessage());
        }
    }
}
