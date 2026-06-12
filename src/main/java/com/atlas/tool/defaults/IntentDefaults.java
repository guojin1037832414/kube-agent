package com.atlas.tool.defaults;

import java.util.Collections;
import java.util.Map;

/**
 * 单个意图的默认值封装。
 *
 * <p>中文说明：该 record 封装 {@code defaults.yml} 中某个 intentId 的表单草稿默认值，
 * 输出给 DefaultValueRegistry / DefaultValueApplier 在执行前做“缺省业务字段补齐”。
 * 它让前端或 LLM 没有显式填写的普通业务参数有一个可审计的来源。</p>
 *
 * <p>安全边界：默认值不是可信控制面事实，不能生成 token、orgId、userId、sessionId、HITL、
 * audit receipt、release decision、writeAllowed、admin 或任何 kube-manager 写授权字段。
 * 构造时统一调用 DefaultValueSafety 过滤，并返回不可变 Map，避免运行期被其他链路偷偷加入受保护字段。</p>
 */
public record IntentDefaults(
    /** 意图 ID 来自配置或测试构造，用于定位默认值分组，不代表该意图已被授权执行。 */
    String intentId,
    /** 已经过安全过滤的默认业务参数；仅用于补普通表单字段，不承载身份、审计或发布权威。 */
    Map<String, Object> parameters
) {
    public IntentDefaults {
        parameters = Collections.unmodifiableMap(
            DefaultValueSafety.sanitizeParameters(intentId, parameters)
        );
    }

    /**
     * 查询单个默认值。
     *
     * <p>中文说明：返回值仍然只是补参候选，调用方不能因为它来自 defaults.yml 就跳过 Tool 参数校验。</p>
     */
    public Object getDefault(String paramName) {
        return parameters.get(paramName);
    }

    public boolean isEmpty() {
        return parameters.isEmpty();
    }
}
