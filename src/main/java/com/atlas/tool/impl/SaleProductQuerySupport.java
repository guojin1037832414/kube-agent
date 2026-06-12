package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品目录与租赁报价分析 Tool 的 query 白名单工具。
 *
 * <p>中文说明：sale 域涉及价格、订单和支付。Agent 侧只能把成熟 DTO 明确支持的只读筛选字段传给
 * kube-manager，不能把订单创建、支付、状态流转等字段混入 GET 查询。本类把公共产品目录、
 * 预付费/后付费产品列表和租赁金额预估的 query 统一收敛，避免每个 Tool 各自拼 Map。</p>
 *
 * <p>安全边界：这里的输出只是只读 query，不是下单、支付、续费或报价确认。organizationId、orgId、
 * token、userId、orderStatus、approved、writeAllowed、releaseDecision 等控制或写入字段必须被丢弃；
 * 真实订单创建/支付未来必须走独立高风险写链路。</p>
 */
final class SaleProductQuerySupport {

    private SaleProductQuerySupport() {
    }

    /** 产品列表筛选 schema；字段只影响目录过滤，不产生订单或支付动作。 */
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

    /** 租赁金额预估参数 schema；id 是服务器配置 ID，不是订单 ID 或支付凭证。 */
    static List<ToolParameterSpec> orderCountSpecs() {
        return List.of(
            new ToolParameterSpec("id", "integer", "服务器配置 ID，仅允许正整数", true,
                List.of("serviceConfigId", "serverConfigId", "productConfigId")),
            stringSpec("startTime", "租赁开始时间，必填，格式由成熟后端解释"),
            stringSpec("endTime", "租赁结束时间，必填，格式由成熟后端解释")
        );
    }

    /**
     * 构造产品目录 GET query 白名单。
     *
     * <p>中文说明：只复制成熟 DTO 支持的筛选字段，并对分页和 GPU 百分比做范围限制；
     * 任何订单、支付、用户、租户和写入字段都会被忽略。</p>
     */
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

    /**
     * 构造租赁金额预估 query。
     *
     * <p>安全边界：金额预估只是只读计算，不创建订单、不锁库存、不发起支付；
     * startTime/endTime 仍是业务候选字符串，最终语义由 mature kube-manager 校验。</p>
     */
    static Map<String, Object> buildOrderCountQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("id", positiveId(params, "id"));
        query.put("startTime", requiredTrimmed(params, "startTime"));
        query.put("endTime", requiredTrimmed(params, "endTime"));
        return query;
    }

    /** 校验正整数 ID，防止路径/query 注入形态混入报价预估请求。 */
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
