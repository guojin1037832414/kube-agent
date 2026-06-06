package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品目录与租赁报价分析 Tool 的 query 白名单工具。
 *
 * <p>sale 域涉及价格、订单和支付。Agent 侧只能把成熟 DTO 明确支持的只读筛选字段传给
 * kube-manager，不能把订单创建、支付、状态流转等字段混入 GET 查询。</p>
 */
final class SaleProductQuerySupport {

    private SaleProductQuerySupport() {
    }

    static List<ToolParameterSpec> productListSpecs() {
        return List.of(
            pageSpec(),
            limitSpec(),
            stringSpec("productTypeCode", "产品分类编码，例如 GPU、server、pod 等"),
            stringSpec("resourceCode", "资源配置编码，仅用于筛选产品目录"),
            stringSpec("software", "软件配置筛选，例如 NVAIE 全套或无"),
            stringSpec("startTime", "查询开始时间，格式由成熟后端解释"),
            stringSpec("endTime", "查询结束时间，格式由成熟后端解释"),
            stringSpec("gpuModel", "GPU 型号，多个型号用英文逗号分隔，例如 A800,3090"),
            new ToolParameterSpec("gpuPercentLimits", "integer", "GPU 百分比上限，必须为非负整数，最大 10000", false, List.of())
        );
    }

    static List<ToolParameterSpec> orderCountSpecs() {
        return List.of(
            new ToolParameterSpec("id", "integer", "服务器配置 ID，仅允许正整数", true,
                List.of("serviceConfigId", "serverConfigId", "productConfigId")),
            stringSpec("startTime", "租赁开始时间，必填，格式由成熟后端解释"),
            stringSpec("endTime", "租赁结束时间，必填，格式由成熟后端解释")
        );
    }

    static Map<String, Object> buildProductQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", normalizePositiveInteger(params.get("page"), "page", 1, 10000));
        query.put("limit", normalizePositiveInteger(params.get("limit"), "limit", 10, 1000));
        putTrimmed(query, params, "productTypeCode");
        putTrimmed(query, params, "resourceCode");
        putTrimmed(query, params, "software");
        putTrimmed(query, params, "startTime");
        putTrimmed(query, params, "endTime");
        putTrimmed(query, params, "gpuModel");
        putNonNegativeIntegerIfPresent(query, params, "gpuPercentLimits", 10000);
        return query;
    }

    static Map<String, Object> buildOrderCountQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("id", positiveId(params, "id"));
        query.put("startTime", requiredTrimmed(params, "startTime"));
        query.put("endTime", requiredTrimmed(params, "endTime"));
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

    private static String requiredTrimmed(Map<String, Object> params, String key) {
        Object raw = params.get(key);
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            throw new AtlasToolValidationException(key + " 不能为空");
        }
        return String.valueOf(raw).trim();
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
