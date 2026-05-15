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
 * 查询Helm Release历史记录 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "helm_release_history"}</p>
 * <p>Agent归属: deploy | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/helm/releases</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_release_history",
    agent = "deploy",
    intentId = "helm_release_history",
    description = "查询Helm Release历史记录"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class HelmReleaseHistoryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmReleaseHistoryTool(KubeManagerHttpClient httpClient) {
        super("helm_release_history", "查询Helm Release历史记录");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("release");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/{orgId}/helm/releases".replace("{orgId}", orgId);
            Object releaseParam = params.get("release");
            if (releaseParam != null && !releaseParam.toString().isBlank()) {
                path += "/" + releaseParam + "/histories";
            }
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("查询Helm Release历史记录完成", data);
        } catch (Exception e) {
            log.error("[helm_release_history] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询Helm Release历史记录失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
