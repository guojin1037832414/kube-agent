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
 * 查询实验实例列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "experiment_instance_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/experiment/instance</p>
 */
@Component
@AtlasToolMapping(
    name = "experiment_instance_list",
    agent = "query",
    intentId = "experiment_instance_list",
    description = "查询实验实例列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ExperimentInstanceListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ExperimentInstanceListTool(KubeManagerHttpClient httpClient) {
        super("experiment_instance_list", "查询实验实例列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/" + orgId + "/experiment/instance";

            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("查询实验实例列表完成", data);
        } catch (Exception e) {
            log.error("[experiment_instance_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询实验实例列表失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
