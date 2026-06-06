package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本/账单分析类 Tool 的 query 白名单工具。
 *
 * <p>这些接口涉及消费记录、账单和定价配置，不能把 LLM 传入的参数整体透传给 kube-manager。
 * 本类只组装成熟 DTO 明确支持的字段，组织、用户、Token 等上下文全部交给后端鉴权链路处理。</p>
 */
final class FinancialAnalysisQuerySupport {

    private FinancialAnalysisQuerySupport() {
    }

    static List<ToolParameterSpec> podUseRecordSpecs() {
        return List.of(
            pageSpec(),
            limitSpec(),
            stringSpec("userName", "用户姓名筛选，仅由后端按当前角色决定是否生效"),
            stringSpec("containerName", "容器或 Pod 名称筛选"),
            stringSpec("startTime", "查询开始时间，格式建议为 yyyy-MM-dd HH:mm:ss"),
            stringSpec("endTime", "查询结束时间，格式建议为 yyyy-MM-dd HH:mm:ss")
        );
    }

    static List<ToolParameterSpec> podUseBillSpecs() {
        return List.of(
            pageSpec(),
            limitSpec(),
            stringSpec("applicationName", "Deployment 或 Job 名称筛选"),
            stringSpec("startTime", "账单开始时间，格式建议为 yyyy-MM-dd HH:mm:ss"),
            stringSpec("endTime", "账单结束时间，格式建议为 yyyy-MM-dd HH:mm:ss"),
            stringSpec("podStatus", "容器状态筛选，例如 running、finish 或 all")
        );
    }

    static List<ToolParameterSpec> costConfigSpecs() {
        return List.of(
            pageSpec(),
            limitSpec(),
            stringSpec("startTime", "计费配置创建开始时间，格式建议为 yyyy-MM-dd HH:mm:ss"),
            stringSpec("endTime", "计费配置创建结束时间，格式建议为 yyyy-MM-dd HH:mm:ss")
        );
    }

    static Map<String, Object> buildPodUseRecordQuery(Map<String, Object> params) {
        Map<String, Object> query = buildPageLimitQuery(params);
        putTrimmed(query, params, "userName");
        putTrimmed(query, params, "containerName");
        putTrimmed(query, params, "startTime");
        putTrimmed(query, params, "endTime");
        return query;
    }

    static Map<String, Object> buildPodUseBillQuery(Map<String, Object> params) {
        Map<String, Object> query = buildPageLimitQuery(params);
        putTrimmed(query, params, "applicationName");
        putTrimmed(query, params, "startTime");
        putTrimmed(query, params, "endTime");
        putTrimmed(query, params, "podStatus");
        return query;
    }

    static Map<String, Object> buildCostConfigQuery(Map<String, Object> params) {
        Map<String, Object> query = buildPageLimitQuery(params);
        putTrimmed(query, params, "startTime");
        putTrimmed(query, params, "endTime");
        return query;
    }

    private static Map<String, Object> buildPageLimitQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", normalizePositiveInteger(params.get("page"), "page", 1, 10000));
        query.put("limit", normalizePositiveInteger(params.get("limit"), "limit", 100, 1000));
        return query;
    }

    private static String normalizePositiveInteger(Object raw, String name, int defaultValue, int maxValue) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            return String.valueOf(defaultValue);
        }

        String value = String.valueOf(raw).trim();
        if (!value.matches("[1-9][0-9]*")) {
            throw new com.atlas.tool.exception.AtlasToolValidationException(name + " 必须是正整数");
        }

        int parsed = Integer.parseInt(value);
        if (parsed > maxValue) {
            throw new com.atlas.tool.exception.AtlasToolValidationException(name + " 不得大于 " + maxValue);
        }
        return String.valueOf(parsed);
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

    private static ToolParameterSpec pageSpec() {
        return new ToolParameterSpec("page", "integer", "页码，默认 1，最大 10000", false, List.of());
    }

    private static ToolParameterSpec limitSpec() {
        return new ToolParameterSpec("limit", "integer", "每页数量，默认 100，最大 1000", false, List.of());
    }

    private static ToolParameterSpec stringSpec(String name, String description) {
        return ToolParameterSpec.stringParam(name, description, false, List.of());
    }
}
