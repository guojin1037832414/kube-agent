package com.atlas.tool.core;

import com.atlas.tool.annotation.WithDefaults;
import com.atlas.tool.defaults.DefaultValueApplier;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认值回填 AOP 拦截器 — 自动为带 {@link WithDefaults} 注解的 Tool 方法装填参数。
 *
 * <p>无需改动 Tool execute 的一行代码，加注解即生效。</p>
 */
@Aspect
@Component
public class DefaultValueAspect {

    private static final Logger log = LoggerFactory.getLogger(DefaultValueAspect.class);

    private final DefaultValueApplier applier;

    public DefaultValueAspect(DefaultValueApplier applier) {
        this.applier = applier;
    }

    /**
     * 拦截所有带 @WithDefaults 注解的方法调用。
     * 在执行前啊禅填第一个 Map<String,Object> 参数。
     */
    @Around("@annotation(withDefaults)")
    public Object around(ProceedingJoinPoint pjp, WithDefaults withDefaults) throws Throwable {
        Object[] args = pjp.getArgs();
        String intentId = withDefaults.intentId();

        // 查找并填充第一个 Map 参数
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Map<?, ?> raw) {
                if (raw.isEmpty() || raw.keySet().stream().allMatch(k -> k instanceof String)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> params = (Map<String, Object>) raw;
                    args[i] = applier.apply(intentId, params);
                    break;
                }
            }
        }

        return pjp.proceed(args);
    }
}
