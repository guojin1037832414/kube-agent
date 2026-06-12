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
 * <p>中文说明：该切面让 Tool 可以通过 {@link WithDefaults} 复用 defaults.yml 中的表单草稿字段，
 * 不需要每个 Tool 手写相同的默认值合并逻辑。它位于业务执行前，只负责把“缺失的普通业务参数”
 * 补成更容易被 Tool 校验和展示的形态。</p>
 *
 * <p>安全边界：默认值回填不是权限系统。切面不能生成、覆盖或信任 {@code token/orgId/userId}、
 * {@code sessionId}、HITL、audit、release、writeAllowed 等控制平面字段，也不能绕过
 * {@link com.atlas.tool.execution.SafeToolExecutor}。如果 defaults.yml 或前端参数携带这些字段，
 * 后续 ProtectedToolParameterFilter / SafeToolExecutor 仍必须删除或拒绝。</p>
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
     *
     * <p>中文说明：这里只处理第一个 {@code Map<String,Object>} 参数，是为了保持切面行为可预测，
     * 避免扫描多个 Map 后把默认值写进审计、HTTP header、上下文或其它非业务容器。</p>
     *
     * <p>安全边界：返回的新参数仍然只是候选业务输入，之后必须继续走 Tool 自身校验和统一执行边界。</p>
     */
    @Around("@annotation(withDefaults)")
    public Object around(ProceedingJoinPoint pjp, WithDefaults withDefaults) throws Throwable {
        Object[] args = pjp.getArgs();
        String intentId = withDefaults.intentId();

        // 中文说明：查找并填充第一个 Map 参数；默认值合并由 DefaultValueApplier 负责过滤受保护字段。
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
