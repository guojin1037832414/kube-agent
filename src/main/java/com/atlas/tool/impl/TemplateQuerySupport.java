package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;
import java.util.Map;

/**
 * 模板类只读 Tool 的通用参数校验。
 *
 * <p>应用模板和训练 Job 模板的详情接口都使用数字 ID 作为 URL path 片段。
 * 这里统一做正整数校验，避免 LLM 输出 "../1"、"1/extra" 等内容把请求导向非预期路径。</p>
 */
final class TemplateQuerySupport {

    private TemplateQuerySupport() {
    }

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
