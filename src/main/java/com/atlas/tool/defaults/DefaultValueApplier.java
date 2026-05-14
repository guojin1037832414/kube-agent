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
     * @return 回填后的参数µap
     */
    public Map<String, Object> apply(String intentId, Map<String, Object> params) {
        return registry.apply(intentId, params);
    }

    /**
     * 从方法上提取 {@link WithDefaults} 注解，并为参数回填。
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
