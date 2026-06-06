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
 * 拉取容器镜像到 kube-manager 仓库。
 *
 * <p>成熟前端传递的是 Image 实体风格 body：{@code repoTag/registryAuthId/scope/description}。
 * 这里兼容用户自然语言里的 {@code imageName/image}，但最终发送给后端时统一落到 {@code repoTag}。</p>
 */
@Component
@AtlasToolMapping(
    name = "image_pull",
    agent = "deploy",
    intentId = "image_pull",
    description = "拉取容器镜像到仓库，会新增或更新镜像记录",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/image/pull"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ImagePullTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ImagePullTool(KubeManagerHttpClient httpClient) {
        super("image_pull", "拉取容器镜像到仓库");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        // 兼容 imageName/image/clientRepoTag 等别名，必填校验放到 doExecute 里统一归一。
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "repoTag",
                "要拉取的镜像名，例如 nvcr.io/nvidia/cuda:12.4.1-devel-ubuntu22.04。",
                true,
                List.of("imageName", "image", "clientRepoTag")
            ),
            ToolParameterSpec.stringParam(
                "registryAuthId",
                "可选的镜像仓库认证 ID，来自镜像仓库列表；公共镜像可不传。",
                false,
                List.of("registryId", "authId")
            ),
            ToolParameterSpec.stringParam(
                "scope",
                "镜像归属范围，通常为 ORGANIZATION 或 PERSONAL；未传时由后端/权限策略处理。",
                false,
                List.of("ownershipScope")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String repoTag = firstNonBlank(params, "repoTag", "imageName", "image", "clientRepoTag");
        if (repoTag == null) {
            return AtlasToolResult.fail("缺少必填参数: repoTag（镜像名称）", "MISSING_REPO_TAG",
                List.of("请提供要拉取的镜像名称，例如 nvcr.io/nvidia/cuda:12.4.1-devel-ubuntu22.04"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> body = buildPullBody(params, repoTag);
            Map<String, Object> response = httpClient.post("/api/" + orgId + "/image/pull", body);
            Object data = extractData(response);
            return AtlasToolResult.ok("拉取容器镜像请求已发送: " + repoTag, data);
        } catch (Exception e) {
            log.error("[image_pull] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("拉取容器镜像失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildPullBody(Map<String, Object> params, String repoTag) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("repoTag", repoTag);
        body.put("clientRepoTag", repoTag);
        putIfPresent(body, params, "description");
        putIfPresent(body, params, "registryAuthId");
        putIfPresent(body, params, "scope");
        putIfPresent(body, params, "frameworkType");
        return body;
    }

    private void putIfPresent(Map<String, Object> body, Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value != null && !(value instanceof String s && s.isBlank())) {
            body.put(key, value);
        }
    }

    private String firstNonBlank(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString().trim();
            }
        }
        return null;
    }
}
