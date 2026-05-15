package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 删除镜像 Tool。
 * <p><b>⚠️ 危险操作</b>: P0级, 删除后镜像无法恢复, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "image_delete"}</p>
 * <p>Agent归属: deploy | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "image_delete",
    agent = "deploy",
    intentId = "image_delete",
    description = "删除镜像"
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class ImageDeleteTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ImageDeleteTool(KubeManagerHttpClient httpClient) {
        super("image_delete", "删除镜像");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[image_delete] 执行删除镜像");
        String id = params.get("id") != null ? params.get("id").toString() : "";
        if (id.isBlank()) {
            return AtlasToolResult.fail("缺少必需的参数: id（镜像ID或名称）");
        }

        try {
            String orgId = organizationId(params);
            Map<String, Object> response = httpClient.delete(
                "/api/" + orgId + "/image/" + id,
                Map.of()
            );
            Object data = response.containsKey("result") ? response.get("result") : response;
            String summary = "镜像已删除: " + id;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[image_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("镜像删除失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
