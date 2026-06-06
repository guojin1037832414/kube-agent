package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NIM 部署预检的参数校验与只读规划辅助。
 *
 * <p>这里刻意不生成 DeploymentDTO，也不复用写操作 Tool 的 body 构造。预检阶段只回答
 * “成熟前端会读取哪些数据、当前是否能找到候选 tag/template”，避免把只读 planning
 * 误升级成隐式创建。</p>
 */
final class NimDeploymentPreflightSupport {

    private NimDeploymentPreflightSupport() {
    }

    static List<ToolParameterSpec> parameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "repository",
                "NIM repository/resourceId，必须来自 repository_catalog_list 返回值；未提供时会先按目录筛选查询候选。",
                false,
                List.of("resourceId", "resource_id", "repo", "modelRepository")
            ),
            ToolParameterSpec.stringParam(
                "displayName",
                "NIM 展示名称或模型名称筛选，例如 llama、mistral；用于查询一键部署目录候选。",
                false,
                List.of("model", "modelName", "name", "keyword", "search")
            ),
            ToolParameterSpec.stringParam(
                "industryCategory",
                "NIM 行业/场景分类，例如 Reasoning、Retrieval、Visual Design。",
                false,
                List.of("category", "scenario", "serviceScenario")
            ),
            ToolParameterSpec.stringParam(
                "tag",
                "期望使用的镜像 tag。未提供时只返回成熟前端默认会选择的首个 tag 候选，不自动创建。",
                false,
                List.of("imageTag", "version")
            ),
            ToolParameterSpec.stringParam(
                "serviceName",
                "用户期望创建的 NIM 服务名称，仅用于预检摘要；本 Tool 不会创建服务。",
                false,
                List.of("name", "displayServiceName", "deploymentName")
            )
        );
    }

    static Map<String, Object> buildCatalogQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", "1");
        query.put("limit", "20");
        putTrimmed(query, params, "displayName");
        putTrimmed(query, params, "industryCategory");
        query.put("isOneClickDeploy", true);
        return query;
    }

    static String optionalRepository(Map<String, Object> params) {
        Object raw = params.get("repository");
        if (raw == null || raw.toString().isBlank()) {
            return "";
        }
        return safeRepository(raw.toString().trim());
    }

    static String selectRepository(Object catalogData, String requestedRepository) {
        if (hasText(requestedRepository)) {
            return requestedRepository;
        }
        if (catalogData instanceof List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map) {
                String resourceId = text(map.get("resourceId"));
                if (hasText(resourceId)) {
                    return safeRepository(resourceId);
                }
                String repository = text(map.get("repository"));
                if (hasText(repository)) {
                    return safeRepository(repository);
                }
            }
        }
        throw new AtlasToolValidationException(
            "未找到可用于 NIM 预检的 repository/resourceId",
            "NIM_REPOSITORY_NOT_FOUND",
            List.of("请先通过 repository_catalog_list 查询 NIM 一键部署目录，并传入返回的 resourceId")
        );
    }

    static Map<String, Object> buildTagQuery(String repository) {
        return Map.of("repository", safeRepository(repository));
    }

    static Map<String, Object> buildTemplateQuery(String image) {
        if (!hasText(image) || image.length() > 240 || image.contains("?") || image.contains("&")) {
            throw new AtlasToolValidationException(
                "NIM image 格式不安全，拒绝用于模板查询: " + image,
                "INVALID_NIM_IMAGE",
                List.of("image 必须来自 repository/nim/tags 返回的 repository + ':' + tag")
            );
        }
        return Map.of("image", image, "templateType", "NIM");
    }

    static Map<String, Object> chooseTag(Object tagData, String requestedTag) {
        if (!(tagData instanceof List<?> list) || list.isEmpty()) {
            throw new AtlasToolValidationException(
                "NIM repository 当前没有可用 tag",
                "NIM_TAG_EMPTY",
                List.of("请等待镜像同步完成，或换一个 repository/resourceId 重试")
            );
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String tag = text(map.get("tag"));
                if (!hasText(requestedTag) || requestedTag.equals(tag)) {
                    return copyMap(map);
                }
            }
        }
        throw new AtlasToolValidationException(
            "未找到指定 NIM tag: " + requestedTag,
            "NIM_TAG_NOT_FOUND",
            List.of("请使用 repository_catalog_nim_tag_list 查看可用 tag 后重试")
        );
    }

    static String buildImage(Map<String, Object> tagEntry) {
        String repository = text(tagEntry.get("repository"));
        String tag = text(tagEntry.get("tag"));
        if (!hasText(repository) || !hasText(tag)) {
            throw new AtlasToolValidationException(
                "NIM tag 响应缺少 repository 或 tag 字段",
                "NIM_TAG_RESPONSE_INVALID",
                List.of("请确认 kube-manager repository/nim/tags 返回结构与成熟前端一致")
            );
        }
        return repository + ":" + tag;
    }

    static Map<String, Object> chooseTemplate(Object templateData) {
        if (!(templateData instanceof List<?> list) || list.isEmpty()) {
            throw new AtlasToolValidationException(
                "未找到匹配 image + templateType=NIM 的应用模板",
                "NIM_TEMPLATE_NOT_FOUND",
                List.of("请先由管理员在应用模板中为该 NIM image 配置 templateType=NIM 的模板")
            );
        }
        Object first = list.get(0);
        if (first instanceof Map<?, ?> map) {
            return copyMap(map);
        }
        throw new AtlasToolValidationException(
            "NIM 模板响应结构不是对象",
            "NIM_TEMPLATE_RESPONSE_INVALID",
            List.of("请检查 kube-manager /template 返回结构")
        );
    }

    static Map<String, Object> buildPlan(Map<String, Object> params,
                                         Object catalogData,
                                         Object tagData,
                                         Map<String, Object> selectedTag,
                                         String image,
                                         Object templateData,
                                         Map<String, Object> selectedTemplate) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("sideEffect", "NONE");
        plan.put("preflightOnly", true);
        plan.put("serviceName", optional(params, "serviceName"));
        plan.put("selectedRepository", selectedRepositoryLabel(selectedTag));
        plan.put("selectedTag", selectedTag.get("tag"));
        plan.put("selectedImage", image);
        plan.put("selectedTemplate", selectedTemplate);
        plan.put("deploymentBodyPreview", NimTemplateMergeSupport.buildDeploymentBodyPreview(
            params,
            image,
            selectedTemplate
        ));
        plan.put("catalogCandidates", catalogData);
        plan.put("tagCandidates", tagData);
        plan.put("templateCandidates", templateData);
        plan.put("nextRequiredConfirmation", List.of(
            "人工确认最终 NIM 服务名称、镜像 image、模板资源参数、GPU 规格、网络暴露方式和费用/配额影响",
            "正式创建时必须走 deploy_create_instance 或未来已审计的 nim_create 编排，不能复用本预检 Tool 直接 POST"
        ));
        plan.put("holdItems", List.of(
            "尚未自动合并成熟前端 mergeTemplate/formatApplication 的完整 DeploymentDTO",
            "尚未开放 NIM 创建、服务轮询、NIM API readiness 探测和 API Key 展示"
        ));
        return plan;
    }

    private static String selectedRepositoryLabel(Map<String, Object> selectedTag) {
        String repository = text(selectedTag.get("repository"));
        return hasText(repository) ? repository : text(selectedTag.get("resourceId"));
    }

    private static String optional(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        return raw == null ? "" : raw.toString().trim();
    }

    private static void putTrimmed(Map<String, Object> query, Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) {
            return;
        }
        String value = raw.toString().trim();
        if (!value.isBlank()) {
            query.put(key, value);
        }
    }

    private static String safeRepository(String value) {
        if (value.length() > 160 || !value.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")) {
            throw new AtlasToolValidationException(
                "repository 仅支持成熟后端返回的 NIM 目录标识: " + value,
                "INVALID_REPOSITORY",
                List.of("请传入 repository_catalog_list 返回的 resourceId/repository，不要传入 URL、query 或脚本")
            );
        }
        return value;
    }

    private static Map<String, Object> copyMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
