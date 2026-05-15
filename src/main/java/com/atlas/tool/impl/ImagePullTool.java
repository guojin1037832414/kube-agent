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
 * 拉取容器镜像到仓库 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "image_pull"}</p>
 * <p>Agent归属: deploy | 安全级别: P3</p>
 * <p>API路径: POST /api/{orgId}/image/pull</p>
 */
@Component
@AtlasToolMapping(
    name = "image_pull",
    agent = "deploy",
    intentId = "image_pull",
    description = "拉取容器镜像到仓库"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ImagePullTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ImagePullTool(KubeManagerHttpClient httpClient) {
        super("image_pull", "拉取容器镜像到仓库");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("imageName");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/{orgId}/image/pull".replace("{orgId}", orgId);

            // image_pull 需要 imageName 参数
            Object imageName = params.get("imageName") != null ? params.get("imageName") : params.get("image");
            if (imageName == null || imageName.toString().isBlank()) {
                return AtlasToolResult.fail("缺少必填参数: imageName (镜像名称)");
            }
            Map<String, Object> response = httpClient.post(path, Map.of("imageName", imageName.toString()));
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("拉取容器镜像到仓库完成", data);
        } catch (Exception e) {
            log.error("[image_pull] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("拉取容器镜像到仓库失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
