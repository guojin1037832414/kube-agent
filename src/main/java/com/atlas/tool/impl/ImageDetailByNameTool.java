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
 * 根据名称查询镜像详情 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "image_detail_by_name"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/image/name</p>
 */
@Component
@AtlasToolMapping(
    name = "image_detail_by_name",
    agent = "query",
    intentId = "image_detail_by_name",
    description = "根据名称查询镜像详情"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ImageDetailByNameTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ImageDetailByNameTool(KubeManagerHttpClient httpClient) {
        super("image_detail_by_name", "根据名称查询镜像详情");
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
            String path = "/api/{orgId}/image/name".replace("{orgId}", orgId);
            Object nameParam = params.get("name");
            if (nameParam != null && !nameParam.toString().isBlank()) {
                path += "?name=" + nameParam;
            }
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("根据名称查询镜像详情完成", data);
        } catch (Exception e) {
            log.error("[image_detail_by_name] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("根据名称查询镜像详情失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
