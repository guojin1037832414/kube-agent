package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行业应用分析类 Tool 的参数白名单工具。
 *
 * <p>成熟 kube-manager 的 IndustryAppController 暴露了模板、实例、API 文档、API 调用历史、
 * 资源预设和高级参数等 GET 接口。Agent 侧不能把 LLM 传入的任意字段整体透传给后端，
 * 这里只组装 DTO 明确支持的字段，并统一限制分页上限和路径片段格式。</p>
 */
final class IndustryAppQuerySupport {

    private IndustryAppQuerySupport() {
    }

    static List<ToolParameterSpec> templateListSpecs() {
        return List.of(
            pageSpec(),
            limitSpec(),
            stringSpec("category", "行业应用分类筛选，例如 traffic、medical、manufacturing 等"),
            stringSpec("keyword", "模板名称或描述关键字"),
            stringSpec("tags", "模板标签，多个标签按成熟前端习惯用英文逗号分隔"),
            new ToolParameterSpec("includeDetail", "boolean", "是否包含模板详情，默认由后端决定", false, List.of())
        );
    }

    static List<ToolParameterSpec> instanceListSpecs() {
        return List.of(
            pageSpec(),
            limitSpec(),
            stringSpec("name", "行业应用实例名称筛选"),
            stringSpec("status", "实例状态筛选，例如 Running、Pending、Failed"),
            new ToolParameterSpec("mineOnly", "boolean", "是否只查询当前用户实例，最终可见范围由后端权限决定", false, List.of()),
            new ToolParameterSpec("includeDetail", "boolean", "是否包含模板详情，默认由后端决定", false, List.of())
        );
    }

    static List<ToolParameterSpec> apiHistorySpecs() {
        return List.of(
            pageSpec(),
            limitSpec(),
            stringSpec("httpMethod", "HTTP 方法筛选，例如 GET、POST、PUT、DELETE"),
            stringSpec("url", "API URL 关键字筛选"),
            new ToolParameterSpec("sinceSeconds", "integer", "只查询最近多少秒内的调用历史，必须为非负整数", false, List.of())
        );
    }

    static List<ToolParameterSpec> appIdSpecs(String description) {
        return List.of(new ToolParameterSpec(
            "appId",
            "integer",
            description + "，仅允许正整数，禁止路径片段或脚本内容",
            true,
            List.of("id", "templateId", "applicationId")
        ));
    }

    static List<ToolParameterSpec> instanceIdSpecs() {
        return List.of(new ToolParameterSpec(
            "instanceId",
            "integer",
            "行业应用实例 ID，仅允许正整数，禁止路径片段或脚本内容",
            true,
            List.of("id", "appInstanceId")
        ));
    }

    static Map<String, Object> buildTemplateListQuery(Map<String, Object> params) {
        Map<String, Object> query = buildPageLimitQuery(params);
        putTrimmed(query, params, "category");
        putTrimmed(query, params, "keyword");
        putTrimmed(query, params, "tags");
        putBooleanIfPresent(query, params, "includeDetail");
        return query;
    }

    static Map<String, Object> buildInstanceListQuery(Map<String, Object> params) {
        Map<String, Object> query = buildPageLimitQuery(params);
        putTrimmed(query, params, "name");
        putTrimmed(query, params, "status");
        putBooleanIfPresent(query, params, "mineOnly");
        putBooleanIfPresent(query, params, "includeDetail");
        return query;
    }

    static Map<String, Object> buildApiHistoryQuery(Map<String, Object> params) {
        Map<String, Object> query = buildPageLimitQuery(params);
        putTrimmed(query, params, "httpMethod");
        putTrimmed(query, params, "url");
        putNonNegativeIntegerIfPresent(query, params, "sinceSeconds", 86_400);
        return query;
    }

    static String positiveId(Map<String, Object> params, String name) {
        Object raw = params.get(name);
        if (raw == null) {
            throw new AtlasToolValidationException(name + " 不能为空");
        }

        String value = String.valueOf(raw).trim();
        if (!value.matches("[1-9][0-9]*")) {
            throw new AtlasToolValidationException(name + " 仅支持正整数");
        }
        return value;
    }

    private static Map<String, Object> buildPageLimitQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", normalizePositiveInteger(params.get("page"), "page", 1, 10000));
        query.put("limit", normalizePositiveInteger(params.get("limit"), "limit", 10, 1000));
        return query;
    }

    private static String normalizePositiveInteger(Object raw, String name, int defaultValue, int maxValue) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return String.valueOf(defaultValue);
        }

        String value = String.valueOf(raw).trim();
        if (!value.matches("[1-9][0-9]*")) {
            throw new AtlasToolValidationException(name + " 必须是正整数");
        }

        int parsed = Integer.parseInt(value);
        if (parsed > maxValue) {
            throw new AtlasToolValidationException(name + " 不得大于 " + maxValue);
        }
        return String.valueOf(parsed);
    }

    private static void putNonNegativeIntegerIfPresent(Map<String, Object> query, Map<String, Object> params,
                                                       String key, int maxValue) {
        Object raw = params.get(key);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return;
        }

        String value = String.valueOf(raw).trim();
        if (!value.matches("0|[1-9][0-9]*")) {
            throw new AtlasToolValidationException(key + " 必须是非负整数");
        }

        int parsed = Integer.parseInt(value);
        if (parsed > maxValue) {
            throw new AtlasToolValidationException(key + " 不得大于 " + maxValue);
        }
        query.put(key, value);
    }

    private static void putTrimmed(Map<String, Object> query, Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null) {
            return;
        }
        String value = String.valueOf(raw).trim();
        if (!value.isEmpty()) {
            query.put(key, value);
        }
    }

    private static void putBooleanIfPresent(Map<String, Object> query, Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return;
        }

        String value = String.valueOf(raw).trim().toLowerCase();
        if ("true".equals(value) || "1".equals(value) || "yes".equals(value)) {
            query.put(key, true);
            return;
        }
        if ("false".equals(value) || "0".equals(value) || "no".equals(value)) {
            query.put(key, false);
            return;
        }
        throw new AtlasToolValidationException(key + " 必须是布尔值");
    }

    private static ToolParameterSpec pageSpec() {
        return new ToolParameterSpec("page", "integer", "页码，默认 1，最大 10000", false, List.of());
    }

    private static ToolParameterSpec limitSpec() {
        return new ToolParameterSpec("limit", "integer", "每页数量，默认 10，最大 1000", false, List.of());
    }

    private static ToolParameterSpec stringSpec(String name, String description) {
        return ToolParameterSpec.stringParam(name, description, false, List.of());
    }
}
