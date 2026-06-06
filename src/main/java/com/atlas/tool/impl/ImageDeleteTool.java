package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 删除镜像 Tool。
 *
 * <p>对齐成熟 kube-manager：删除接口是
 * {@code DELETE /api/{organizationId}/image/{imageId}?entirely=false}。其中 {@code entirely=true}
 * 会同时删除仓库镜像，风险更高，必须在 HITL 审批里明确展示。</p>
 */
@Component
@AtlasToolMapping(
    name = "image_delete",
    agent = "deploy",
    intentId = "image_delete",
    description = "删除镜像，可选择是否同时删除仓库中的镜像",
    httpMethod = "DELETE",
    apiEndpoints = {"/api/{orgId}/image/{imageId}?entirely={entirely}"},
    operationType = AtlasToolMapping.OperationType.DELETE,
    requiresConfirmation = true
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
    protected Map<String, Class<?>> getParamTypes() {
        return Map.of("entirely", Boolean.class);
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "id",
                "要删除的镜像数据库 ID，必须来自镜像列表或详情查询结果，不能传镜像名称。",
                true,
                List.of("imageId")
            ),
            new ToolParameterSpec(
                "entirely",
                "boolean",
                "是否同时删除镜像仓库中的真实镜像。默认 false；true 属于更高风险操作，审批时必须明确说明。",
                false,
                List.of("deleteRepositoryImage", "deleteFromRegistry")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String imageId = params.get("id") != null ? params.get("id").toString().trim() : "";
        if (imageId.isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: id（镜像数据库ID）", "MISSING_IMAGE_ID",
                List.of("请先查询镜像列表或详情，确认要删除的镜像 ID"));
        }

        boolean entirely = Boolean.TRUE.equals(params.get("entirely"));
        try {
            String orgId = resolveOrganizationId(params);
            // kube-manager 删除镜像要求 entirely query 参数；默认 false，避免误删仓库镜像。
            Map<String, Object> response = httpClient.delete(
                "/api/" + orgId + "/image/" + imageId,
                Map.of("entirely", entirely)
            );
            Object data = extractData(response);
            String summary = entirely
                ? "镜像删除请求已发送，并将同时删除仓库镜像: ID=" + imageId
                : "镜像删除请求已发送，仅删除平台记录/标记: ID=" + imageId;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[image_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("镜像删除失败: " + e.getMessage());
        }
    }
}
