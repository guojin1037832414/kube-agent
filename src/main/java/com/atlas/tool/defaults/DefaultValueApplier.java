package com.atlas.tool.defaults;

import com.atlas.tool.annotation.WithDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 默认值回填执行器 — 为 Tool 参数自动装配默认值。
 *
 * <p>中文说明：这是 Tool 执行前的补参辅助器，输入来自 intentId、反射注解 {@link WithDefaults}
 * 和当前参数 Map，输出仍是同一个业务参数 Map。它的价值是减少重复表单字段，让教学样例和真实 Tool
 * 都能清楚看到“默认值从哪里来”。</p>
 *
 * <p>安全边界：默认值回填不是权限系统，不能生成或覆盖 token、orgId、userId、sessionId、HITL、
 * audit receipt、release decision、writeAllowed、admin 或其他受保护控制字段。回填后仍必须继续经过
 * SafeToolExecutor、受保护参数过滤、Tool 参数校验、HITL、审计和 kube-manager 权限检查。</p>
 *
 * <p>使用方式：</p>
 * <ol>
 *   <li>Tool 类或方法加 {@link WithDefaults} 注解，指定 intentId</li>
 *   <li>调用 execute 前，先调用 {@link #apply} 回填参数</li>
 *   <li>完成后发送给后端 API</li>
 * </ol>
 */
@Component
public class DefaultValueApplier {

    private static final Logger log = LoggerFactory.getLogger(DefaultValueApplier.class);

    private final DefaultValueRegistry registry;

    public DefaultValueApplier(DefaultValueRegistry registry) {
        this.registry = registry;
    }

    /**
     * 为指定 intentId 的参数 Map 回填默认值。
     *
     * @param intentId 意图 ID
     * @param params   当前参数
     * @return 回填后的参数 Map
     */
    public Map<String, Object> apply(String intentId, Map<String, Object> params) {
        return registry.apply(intentId, params);
    }

    /**
     * 从方法上提取 {@link WithDefaults} 注解，并为参数回填。
     *
     * <p>中文说明：注解只告诉我们使用哪组默认表单字段，不代表该方法对应的 Tool 已经通过权限、
     * HITL、审计或 release gate。</p>
     */
    public Map<String, Object> applyFromMethod(Method method, Map<String, Object> params) {
        WithDefaults ann = method.getAnnotation(WithDefaults.class);
        if (ann == null) {
            ann = method.getDeclaringClass().getAnnotation(WithDefaults.class);
        }
        if (ann == null || ann.intentId().isBlank()) {
            return params;
        }
        return apply(ann.intentId(), params);
    }

    /**
     * 验证回填结果—检查必要的默认参数是否都已填充。
     *
     * <p>安全边界：这里的 mandatory 只能描述普通业务参数是否存在，不能把 token/orgId/HITL/audit/release
     * 等控制面字段列为“可由默认值补齐”的必要参数。</p>
     *
     * @param intentId  意图 ID
     * @param params    验证后的参数
     * @param mandatory 必要存在的参数名列表
     */
    public boolean validate(String intentId, Map<String, Object> params, String... mandatory) {
        for (String key : mandatory) {
            Object val = params.get(key);
            if (val == null) {
                // 尝试从默认值表装填
                IntentDefaults defs = registry.getDefaults(intentId);
                if (defs != null && defs.getDefault(key) != null) {
                    params.put(key, defs.getDefault(key));
                    log.warn("[DefaultValueApplier] 强制补填必要参数 {}.{}", intentId, key);
                } else {
                    log.error("[DefaultValueApplier] 缺少必要参数 {}.{}", intentId, key);
                    return false;
                }
            }
        }
        return true;
    }
}
