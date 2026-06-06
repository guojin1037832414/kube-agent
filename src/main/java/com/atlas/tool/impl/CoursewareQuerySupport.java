package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;
import java.util.Map;

/**
 * 课件类 Tool 的路径参数校验。
 *
 * <p>课件详情和班级查询都把 coursewareId 放进 URL path。
 * Agent 侧必须只接受正整数，避免 LLM 输出路径片段把请求导向非预期接口。</p>
 */
final class CoursewareQuerySupport {

    private CoursewareQuerySupport() {
    }

    static String positiveCoursewareId(Map<String, Object> params) {
        Object raw = params.get("coursewareId");
        if (raw == null || raw.toString().isBlank()) {
            throw new AtlasToolValidationException(
                "缺少必填参数: coursewareId",
                "MISSING_COURSEWARE_ID",
                List.of("请先通过 courseware_list 查询课件，再使用返回的数字 ID 查询详情"));
        }
        String value = raw.toString().trim();
        if (!value.matches("[1-9]\\d*")) {
            throw new AtlasToolValidationException(
                "课件 ID 仅支持正整数: " + value,
                "INVALID_COURSEWARE_ID",
                List.of("请提供 courseware_list 返回的数字课件 ID"));
        }
        return value;
    }
}
