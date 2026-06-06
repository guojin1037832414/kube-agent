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
 * 创建 Docker Compose 部署 Tool。
 *
 * <p>成熟前端与后端都将“部署 Compose”绑定到 {@code POST /api/{orgId}/compose/deploy}，
 * body 为 {@code ComposeDeployDTO(contentYaml, composeName, resourceList, sizeList)}。历史 Tool
 * 打到 {@code /compose} 且使用 {@code name/yaml}，会导致审批展示和真实动作错位，因此本 Tool
 * 统一改为后端 DTO 语义。</p>
 */
@Component
@AtlasToolMapping(
    name = "compose_deploy_create",
    agent = "deploy",
    intentId = "compose_deploy_create",
    description = "创建 Docker Compose 部署，会修改后端状态",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/compose/deploy"},
    operationType = AtlasToolMapping.OperationType.CREATE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ComposeDeployCreateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ComposeDeployCreateTool(KubeManagerHttpClient httpClient) {
        super("compose_deploy_create", "创建 Docker Compose 部署");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        // doExecute 中兼容 yaml/name 历史别名后再校验。
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "composeName",
                "Compose 组合名称。",
                true,
                List.of("name", "displayName")
            ),
            ToolParameterSpec.stringParam(
                "contentYaml",
                "Docker Compose 原始 YAML 内容。",
                true,
                List.of("yaml", "composeYaml", "content")
            ),
            new ToolParameterSpec("resourceList", "array", "按 services 填写的资源需求列表，可先通过 compose convert 获取服务清单。", false, List.of()),
            new ToolParameterSpec("sizeList", "array", "按 volumes 填写的存储容量需求列表，单位 GB。", false, List.of("storageSizeList"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        Map<String, Object> normalized = normalizeAliases(params);
        List<String> missing = requiredMissing(normalized, "composeName", "contentYaml");
        if (!missing.isEmpty()) {
            return AtlasToolResult.fail(
                "缺少创建 Compose 部署所需参数: " + missing,
                "MISSING_COMPOSE_DEPLOY_PARAMS",
                List.of("请提供 composeName 和 contentYaml；resourceList/sizeList 可根据 compose convert 结果补充")
            );
        }

        String composeName = normalized.get("composeName").toString().trim();
        try {
            String orgId = resolveOrganizationId(normalized);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/compose/deploy",
                buildComposeDeployBody(normalized)
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("Compose 部署创建请求已发送: " + composeName, data);
        } catch (Exception e) {
            log.error("[compose_deploy_create] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("创建 Compose 部署失败: " + e.getMessage());
        }
    }

    private Map<String, Object> normalizeAliases(Map<String, Object> params) {
        Map<String, Object> normalized = new LinkedHashMap<>(params);
        if (!normalized.containsKey("composeName") && normalized.get("name") != null) {
            normalized.put("composeName", normalized.get("name"));
        }
        if (!normalized.containsKey("contentYaml") && normalized.get("yaml") != null) {
            normalized.put("contentYaml", normalized.get("yaml"));
        }
        return normalized;
    }

    private List<String> requiredMissing(Map<String, Object> params, String... keys) {
        return java.util.Arrays.stream(keys)
            .filter(key -> {
                Object value = params.get(key);
                return value == null || (value instanceof String s && s.isBlank());
            })
            .toList();
    }

    private Map<String, Object> buildComposeDeployBody(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contentYaml", params.get("contentYaml"));
        body.put("composeName", params.get("composeName"));
        putOptional(body, params, "resourceList");
        putOptional(body, params, "sizeList");
        return body;
    }

    private void putOptional(Map<String, Object> body, Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value != null) {
            body.put(key, value);
        }
    }
}
