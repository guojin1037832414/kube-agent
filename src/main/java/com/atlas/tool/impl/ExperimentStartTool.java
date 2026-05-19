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
 * 启动实验实例 Tool — 会改变后端状态的操作类接口。
 *
 * <p>⚠️ <b>安全警告</b>: 此为POST操作，会修改数据！</p>
 * <p>意图映射: {@code intentId = "experiment_start"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 * <p>API路径: POST /api/{orgId}/experiment/instance/start</p>
 */
@Component
@AtlasToolMapping(
    name = "experiment_start",
    agent = "deploy",
    intentId = "experiment_start",
    description = "启动实验实例，会修改后端状态"
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
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/experiment/instance/start";

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("id", params.get("id"));

            Map<String, Object> response = httpClient.post(path, body);
            Object data = extractData(response);
            return AtlasToolResult.ok("启动实验实例请求已发送", data);
        } catch (Exception e) {
            log.error("[experiment_start] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("启动实验实例失败: " + e.getMessage());
        }
    }
}
