package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;
import java.util.Map;

/**
 * 课件类 Tool 的路径参数校验。
 *
 * <p>中文说明：课件详情、班级成绩和学习状态查询都会把 coursewareId 放进 URL path。
 * 输入来自 LLM/Plan/前端参数 Map，输出给 kube-manager 课件只读接口，因此 Agent 侧必须只接受
 * 课件列表等成熟后端返回的正整数 ID。</p>
 *
 * <p>安全边界：coursewareId 只是资源定位符，不是课程访问授权。这里拒绝路径片段、查询字符串、
 * 负数、小数、空白和脚本内容，避免 LLM 把请求导向非预期接口；是否能读取课件仍由当前可信用户、
 * 组织上下文和 kube-manager 权限决定。</p>
 */
final class CoursewareQuerySupport {

    private CoursewareQuerySupport() {
    }

    /**
     * 提取并校验课件正整数 ID。
     *
     * <p>中文说明：校验失败时只返回结构化补参/纠错建议，不访问 kube-manager，
     * 方便前端让用户先回到 courseware_list 选择真实 ID。</p>
     */
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
