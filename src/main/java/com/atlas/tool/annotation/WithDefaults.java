package com.atlas.tool.annotation;

import java.lang.annotation.*;

/**
 * 标记此 Tool 导入默认值回填机制。
 *
 * <p>用法：加在 Tool 的 execute 方法上，
 * 系统会自动给该意图参数填充默认值。</p>
 *
 * <pre>
 * &#64;WithDefaults(intentId = "deploy_create_instance")
 * public Map&lt;String, Object&gt; execute(Map&lt;String, Object&gt; params) { ... }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WithDefaults {

    /**
     * 意图 ID，用于从 defaults.yml 中查找默认值。
     * 如果为空，则ύ乳 Tool 类名 / 方法名进行 fallback 匹配（预留）。
     */
    String intentId() default "";

    /**
     * 是否强制覆盖已有值。默认 false（只填充 null / 缺失 key）。
     */
    boolean override() default false;
}
