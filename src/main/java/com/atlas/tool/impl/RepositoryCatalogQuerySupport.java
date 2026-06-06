package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组织内产品/应用镜像目录 Tool 的参数契约与校验工具。
 *
 * <p>这里的 repository catalog 对齐 mature kube-manager 的
 * {@code /api/{orgId}/repository} 系列接口，用于 NGC、NV AIE、NIM 等产品/应用镜像目录。
 * 它不是站点级 registry 配置，也不是普通组织镜像仓库清单，因此单独维护 schema，避免 Agent
 * 把三个相近名词混成一个能力。</p>
 */
final class RepositoryCatalogQuerySupport {

    private RepositoryCatalogQuerySupport() {
    }

    static List<ToolParameterSpec> catalogListSpecs() {
        return List.of(
            pageSpec(),
            limitSpec(),
            ToolParameterSpec.stringParam("displayName", "按产品/应用镜像展示名称模糊筛选。", false,
                List.of("keyword", "name", "search", "repositoryName")),
            ToolParameterSpec.stringParam("status", "按 latestTagStatus 筛选，例如 Ready、Absent。", false,
                List.of("latestTagStatus", "tagStatus")),
            ToolParameterSpec.stringParam("industryCategory", "按行业/模型分类筛选，例如 Reasoning。", false,
                List.of("category", "industry", "modelCategory")),
            new ToolParameterSpec("aieSupported", "boolean", "是否只查询 NVIDIA AI Enterprise Supported 目录。", false,
                List.of("nvAieSupported", "nvaieSupported")),
            new ToolParameterSpec("aieEssential", "boolean", "是否只查询 NVIDIA AI Enterprise Essential 目录。", false,
                List.of("nvAieEssential", "nvaieEssential")),
            new ToolParameterSpec("isOneClickDeploy", "boolean", "是否只查询一键部署目录，NIM 页面会使用该筛选。", false,
                List.of("oneClickDeploy", "nimOnly", "one_click_deploy"))
        );
    }

    static List<ToolParameterSpec> repositoryOnlySpecs() {
        return List.of(repositorySpec());
    }

    static Map<String, Object> buildCatalogQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", String.valueOf(positiveIntOrDefault(params.get("page"), "page", 1, 10000)));
        query.put("limit", String.valueOf(positiveIntOrDefault(params.get("limit"), "limit", 20, 100)));

        putTrimmed(query, params, "displayName");
        putTrimmed(query, params, "status");
        putTrimmed(query, params, "industryCategory");
        putBooleanIfPresent(query, params, "aieSupported");
        putBooleanIfPresent(query, params, "aieEssential");
        putBooleanIfPresent(query, params, "isOneClickDeploy");
        return query;
    }

    static Map<String, Object> buildRepositoryQuery(Map<String, Object> params) {
        return Map.of("repository", repositoryName(params));
    }

    private static ToolParameterSpec pageSpec() {
        return new ToolParameterSpec("page", "integer", "页码，默认 1。", false,
            List.of("pageNo", "page_no", "current"));
    }

    private static ToolParameterSpec limitSpec() {
        return new ToolParameterSpec("limit", "integer", "每页数量，默认 20，最大 100。", false,
            List.of("pageSize", "page_size", "size"));
    }

    private static ToolParameterSpec repositorySpec() {
        return ToolParameterSpec.stringParam(
            "repository",
            "产品/应用镜像 repository 标识，必须来自 repository_catalog_list 返回的 resourceId/repository 字段。",
            true,
            List.of("resourceId", "resource_id", "imageRepository", "repo")
        );
    }

    private static String repositoryName(Map<String, Object> params) {
        Object raw = params.get("repository");
        if (raw == null || raw.toString().isBlank()) {
            throw new AtlasToolValidationException(
                "缺少必填参数: repository",
                "MISSING_REPOSITORY",
                List.of("请先通过 repository_catalog_list 查询目录，再传入返回的 resourceId/repository")
            );
        }
        String value = raw.toString().trim();
        if (value.length() > 160 || !value.matches("[A-Za-z0-9][A-Za-z0-9._/-]*")) {
            throw new AtlasToolValidationException(
                "repository 仅支持成熟后端返回的镜像目录标识: " + value,
                "INVALID_REPOSITORY",
                List.of("repository 会作为 query 参数发送给 kube-manager，不要传入空格、脚本、URL query 或控制字符")
            );
        }
        return value;
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

    private static void putBooleanIfPresent(Map<String, Object> query, Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null || raw.toString().isBlank()) {
            return;
        }
        query.put(key, parseBoolean(raw, key));
    }

    private static boolean parseBoolean(Object raw, String key) {
        if (raw instanceof Boolean value) {
            return value;
        }
        String value = raw.toString().trim().toLowerCase();
        if (List.of("true", "1", "yes", "y").contains(value)) {
            return true;
        }
        if (List.of("false", "0", "no", "n").contains(value)) {
            return false;
        }
        throw new AtlasToolValidationException(
            "参数 '" + key + "' 期望布尔值，但收到: " + raw,
            "TYPE_MISMATCH",
            List.of("请将 '" + key + "' 改为 true/false 或 1/0")
        );
    }

    private static int positiveIntOrDefault(Object raw, String key, int defaultValue, int maxValue) {
        if (raw == null || raw.toString().isBlank()) {
            return defaultValue;
        }
        int value;
        try {
            value = Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            throw new AtlasToolValidationException(
                "参数 '" + key + "' 期望整数，但收到: " + raw,
                "TYPE_MISMATCH",
                List.of("请将 '" + key + "' 改为有效的正整数")
            );
        }
        if (value <= 0 || value > maxValue) {
            throw new AtlasToolValidationException(
                "参数 '" + key + "' 必须在 1.." + maxValue + " 范围内，当前值: " + value,
                "VALUE_OUT_OF_RANGE",
                List.of("请将 '" + key + "' 调整为允许范围内的正整数")
            );
        }
        return value;
    }
}
