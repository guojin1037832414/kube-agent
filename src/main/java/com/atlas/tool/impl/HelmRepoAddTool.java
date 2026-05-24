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
 * 添加Helm仓库 Tool — 会改变后端状态的操作类接口。
 *
 * <p>⚠️ <b>安全警告</b>: 此为POST操作，会修改数据！</p>
 * <p>意图映射: {@code intentId = "helm_repo_add"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 * <p>API路径: POST /api/{orgId}/helm/repo</p>
 */
@Component
@AtlasToolMapping(
    name = "helm_repo_add",
    agent = "deploy",
    intentId = "helm_repo_add",
    description = "添加Helm仓库，会修改后端状态",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/helm/repositories"},
    operationType = AtlasToolMapping.OperationType.CREATE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class HelmRepoAddTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HelmRepoAddTool(KubeManagerHttpClient httpClient) {
        super("helm_repo_add", "添加Helm仓库");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "url");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/helm/repo";

            Map<String, Object> body = new java.util.HashMap<>();
            body.put("name", params.get("name"));
            body.put("url", params.get("url"));

            Map<String, Object> response = httpClient.post(path, body);
            Object data = extractData(response);
            return AtlasToolResult.ok("添加Helm仓库请求已发送", data);
        } catch (Exception e) {
            log.error("[helm_repo_add] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("添加Helm仓库失败: " + e.getMessage());
        }
    }
}
