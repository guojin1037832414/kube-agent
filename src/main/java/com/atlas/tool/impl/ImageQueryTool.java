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
 * 查询镜像资源列表 Tool。
 *
 * <p>意图映射: {@code intentId = "image_query"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "image_query",
    agent = "query",
    intentId = "image_query",
    description = "查询镜像资源列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ImageQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ImageQueryTool(KubeManagerHttpClient httpClient) {
        super("image_query", "查询镜像资源列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = params.get("organizationId") != null
                ? params.get("organizationId").toString()
                : "100001";

            String path = "/api/" + orgId + "/image";
            Map<String, Object> response = httpClient.get(path, Map.of("current", "1", "size", "100"));
            Object data = response.containsKey("result") ? response.get("result") : response;

            return AtlasToolResult.ok("镜像查询完成", data);
        } catch (Exception e) {
            log.error("[image_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("镜像查询失败: " + e.getMessage());
        }
    }
}
