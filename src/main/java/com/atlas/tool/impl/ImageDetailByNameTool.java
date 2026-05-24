package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
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
    description = "根据名称查询镜像详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/image/name"},
    operationType = AtlasToolMapping.OperationType.READ
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

    /**
     * 镜像详情查询参数契约。
     *
     * <p>当前执行逻辑读取的 canonical 字段是 {@code name}，后端 query 参数同样为 name。
     * 这里的 name 仅表示容器镜像名称或镜像引用，不是 Pod、Deployment 或存储名称。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "name",
                "要查询详情的容器镜像名称或镜像引用，例如 nginx:latest、library/nginx:1.25。这里的 name 不是 Pod、Deployment 或存储名称。",
                false,
                List.of("imageName", "image_name", "image", "containerImage", "container_image", "imageRef", "image_ref", "targetName", "target_name")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/image/name".replace("{orgId}", orgId);
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("page", "1");
            query.put("limit", "100");

            Object nameParam = params.get("name");
            if (nameParam != null && !nameParam.toString().isBlank()) {
                query.put("name", nameParam.toString());
            }
            Map<String, Object> response = httpClient.get(path, query);
            Object data = extractData(response);
            return AtlasToolResult.ok("根据名称查询镜像详情完成", data);
        } catch (Exception e) {
            log.error("[image_detail_by_name] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("根据名称查询镜像详情失败: " + e.getMessage());
        }
    }
}
