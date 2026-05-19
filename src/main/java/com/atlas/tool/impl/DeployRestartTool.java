package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 重启实例 Tool。
 *
 * <p>意图映射: {@code intentId = "deploy_restart"}</p>
 * <p>Agent归属: deploy | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "deploy_restart",
    agent = "deploy",
    intentId = "deploy_restart",
    description = "重启实例"
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class DeployRestartTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DeployRestartTool(KubeManagerHttpClient httpClient) {
        super("deploy_restart", "重启实例");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[deploy_restart] 执行重启实例");
        String target = params.get("name") != null ? params.get("name").toString()
            : (params.get("userId") != null ? params.get("userId").toString() : "unknown");

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/deployment/" + target + "/restart",
                Map.of()
            );
            Object data = extractData(response);
            String summary = "实例重启成功: " + target;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[deploy_restart] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("实例重启失败: " + e.getMessage());
        }
    }
}
