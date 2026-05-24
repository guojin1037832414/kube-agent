package com.atlas.plan;

import java.util.List;

/**
 * Plan 阶段后的最小 Reflection 结果。
 *
 * <p>M4.2 POC 只做单次自检，不做自动 retry、不做自动 replan，
 * 更不会根据 Reflection 结果绕过 HITL 执行高危动作。</p>
 *
 * @param passed 计划自检是否通过
 * @param issues 发现的问题列表
 * @param suggestion 后续建议
 */
public record ReflectionResult(
    boolean passed,
    List<String> issues,
    String suggestion
) {
    /**
     * 构造一个通过状态的 Reflection 结果。
     *
     * @param suggestion 后续建议
     * @return 通过状态结果
     */
    public static ReflectionResult passed(String suggestion) {
        return new ReflectionResult(true, List.of(), suggestion);
    }
}
