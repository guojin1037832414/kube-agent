package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行业应用分析类 Tool 的参数白名单工具。
 *
 * <p>中文说明：成熟 kube-manager 的 IndustryAppController 暴露了模板、实例、API 文档、API 调用历史、
 * 资源预设和高级参数等 GET 接口。Agent 侧不能把 LLM 传入的任意字段整体透传给后端，
 * 这里只组装 DTO 明确支持的字段，并统一限制分页上限和路径片段格式。</p>
 *
 * <p>安全边界：行业应用查询会看到模板、实例和调用历史，可能包含业务上下文。这里的 helper 只构造
 * 只读 query/path 参数，不创建实例、不调用 API、不重放历史、不发起部署。caller 传入的 token、
 * orgId、creatorId、requestBody、writeAllowed 或 releaseDecision 都不能进入 kube-manager。</p>
 */
final class IndustryAppQuerySupport {

    private IndustryAppQuerySupport() {
    }

    /** 模板列表 schema；includeDetail 只影响只读返回形状，不代表可部署。 */
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

    /** 实例列表 schema；mineOnly 只是筛选候选，最终可见范围仍由 kube-manager 权限决定。 */
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

    /** API 调用历史 schema；只读查看历史，不重放请求、不发送 requestBody。 */
    static List<ToolParameterSpec> apiHistorySpecs() {
        return List.of(
            pageSpec(),
            limitSpec(),
            stringSpec("httpMethod", "HTTP 方法筛选，例如 GET、POST、PUT、DELETE"),
            stringSpec("url", "API URL 关键字筛选"),
            new ToolParameterSpec("sinceSeconds", "integer", "只查询最近多少秒内的调用历史，必须为非负整数", false, List.of())
        );
    }

    /** 行业应用模板 path ID schema；正整数要求防止路径片段注入。 */
    static List<ToolParameterSpec> appIdSpecs(String description) {
        return List.of(new ToolParameterSpec(
            "appId",
            "integer",
            description + "，仅允许正整数，禁止路径片段或脚本内容",
            true,
            List.of("id", "templateId", "applicationId")
        ));
    }

    /** 行业应用实例 path ID schema；实例 ID 只是定位符，不是访问授权。 */
    static List<ToolParameterSpec> instanceIdSpecs() {
        return List.of(new ToolParameterSpec(
            "instanceId",
            "integer",
            "行业应用实例 ID，仅允许正整数，禁止路径片段或脚本内容",
            true,
            List.of("id", "appInstanceId")
        ));
    }

    /** 构造模板列表 query 白名单，只复制成熟 DTO 支持的只读字段。 */
    static Map<String, Object> buildTemplateListQuery(Map<String, Object> params) {
        Map<String, Object> query = buildPageLimitQuery(params);
        putTrimmed(query, params, "category");
        putTrimmed(query, params, "keyword");
        putTrimmed(query, params, "tags");
        putBooleanIfPresent(query, params, "includeDetail");
        return query;
    }

    /** 构造实例列表 query 白名单，丢弃 namespace、token、orgId 等无关或控制字段。 */
    static Map<String, Object> buildInstanceListQuery(Map<String, Object> params) {
        Map<String, Object> query = buildPageLimitQuery(params);
        putTrimmed(query, params, "name");
        putTrimmed(query, params, "status");
        putBooleanIfPresent(query, params, "mineOnly");
        putBooleanIfPresent(query, params, "includeDetail");
        return query;
    }

    /** 构造 API 历史 query 白名单；不会传递 requestBody、responseBody 或重放标记。 */
    static Map<String, Object> buildApiHistoryQuery(Map<String, Object> params) {
        Map<String, Object> query = buildPageLimitQuery(params);
        putTrimmed(query, params, "httpMethod");
        putTrimmed(query, params, "url");
        putNonNegativeIntegerIfPresent(query, params, "sinceSeconds", 86_400);
        return query;
    }

    /**
     * 校验 path 中的行业应用模板/实例 ID。
     *
     * <p>安全边界：只返回正整数文本，拒绝 {@code ../42}、{@code 42/extra}、query、fragment 和小数。</p>
     */
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
