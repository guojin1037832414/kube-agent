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
 * 查询镜像仓库列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "image_repository"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/image/repository</p>
 */
@Component
@AtlasToolMapping(
    name = "image_repository",
    agent = "query",
    intentId = "image_repository",
    description = "查询镜像仓库列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/image/repository"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ImageRepositoryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ImageRepositoryTool(KubeManagerHttpClient httpClient) {
        super("image_repository", "查询镜像仓库列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/image/repository".replace("{orgId}", orgId);
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询镜像仓库列表完成", data);
        } catch (Exception e) {
            log.error("[image_repository] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询镜像仓库列表失败: " + e.getMessage());
        }
    }
}
