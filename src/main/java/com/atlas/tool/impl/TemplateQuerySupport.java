package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;
import java.util.Map;

/**
 * 模板类只读 Tool 的通用参数校验。
 *
 * <p>中文说明：应用模板和训练 Job 模板的详情接口都使用数字 ID 作为 URL path 片段。
 * 该辅助类把多个模板 Tool 的 path 参数校验集中到同一处，避免每个 Tool 复制一份规则后逐渐漂移。</p>
 *
 * <p>安全边界：模板 ID 必须来自模板列表等成熟后端返回结果，不能来自 LLM 自行构造的路径片段。
 * 这里只负责 path 参数形状校验，不授予模板读取权限，也不允许绕过当前用户 token/orgId、
 * kube-manager RBAC、ToolPermission 或敏感读取治理。</p>
 */
final class TemplateQuerySupport {

    private TemplateQuerySupport() {
    }

    /**
     * 提取指定 key 的模板正整数 ID。
     *
     * <p>中文说明：key/label 由具体 Tool 传入，用于复用同一套错误码和用户提示。
     * 返回值会进入 URL path，因此必须在这里阻断 {@code ../1}、{@code 1/extra}、{@code 1?debug=true}
     * 等路径或 query 注入形态。</p>
     */
    static String positiveTemplateId(Map<String, Object> params, String key, String label) {
        Object raw = params.get(key);
        if (raw == null || raw.toString().isBlank()) {
            throw new AtlasToolValidationException(
                "缺少必填参数: " + key,
                "MISSING_TEMPLATE_ID",
                List.of("请提供要查询的" + label + "数字 ID"));
        }
        String value = raw.toString().trim();
        if (!value.matches("[1-9]\\d*")) {
            throw new AtlasToolValidationException(
                label + "ID 仅支持正整数: " + value,
                "INVALID_TEMPLATE_ID",
                List.of("请先通过列表 Tool 查询可用模板，再使用返回的数字 ID 查询详情"));
        }
        return value;
    }
}
