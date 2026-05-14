package com.atlas.tool.annotation;

import java.lang.annotation.*;

/**
 * Tool 权限注解 — 对标后端 {@code @Isolation(IsolationPolicy.SYS_ADMIN_ONLY)}。
 *
 * <p>标记在 Tool 类上，注册中心据此判断该 Tool 是否为"管理员专属"。</p>
 *
 * <p>权限策略：</p>
 * <ul>
 *   <li>{@link Policy#PUBLIC} — 任何人可用（默认）</li>
 *   <li>{@link Policy#ADMIN_ONLY} — 仅 sys_admin 可调用</li>
 *   <li>{@link Policy#AUTHENTICATED} — 需登录，不区分角色</li>
 * </ul>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * @Component
 * @AtlasToolMapping(name = "user_delete", agent = AtlasAgent.RBAC, ...)
 * @ToolPermission(Policy.ADMIN_ONLY)
 * public class UserDeleteTool implements AtlasTool { ... }
 * }</pre>
 *
 * @version 3.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ToolPermission {

    /**
     * 权限策略，默认 PUBLIC。
     */
    Policy value() default Policy.PUBLIC;

    /**
     * 显式角色列表（可选，覆盖 value 的粗粒度判断）。
     * 例: roles = {"sys_admin", "ops_admin"}
     */
    String[] roles() default {};

    /**
     * 权限策略枚举。
     */
    enum Policy {
        /** 公共 — 所有用户（含匿名）均可调用 */
        PUBLIC,
        /** 需登录 — 仅认证用户可调用，不限制角色 */
        AUTHENTICATED,
        /** 管理员专属 — 仅 sys_admin / admin 可调用 */
        ADMIN_ONLY
    }
}
