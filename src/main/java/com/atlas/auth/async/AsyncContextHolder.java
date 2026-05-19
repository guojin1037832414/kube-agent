package com.atlas.auth.async;

import com.atlas.auth.UserPermissionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 异步上下文透传器 — ThreadLocal Token 在 CompletableFuture 线程切换中不丢失的核心方案。
 *
 * <p><b>问题背景：</b></p>
 * <ul>
 *   <li>主线程（Tomcat HTTP 工作线程）通过 {@link AuthTokenFilter} 将 token 写入 {@code ThreadLocal}</li>
 *   <li>{@link CompletableFuture#runAsync(Runnable)} 使用 ForkJoinPool.commonPool() 或自定义线程池</li>
 *   <li>新线程没有继承主线程的 ThreadLocal 值 → Token 丢失</li>
 *   <li>后果：异步代码中 {@code UserPermissionContext#current()} 返回空，权限校验全部失效</li>
 * </ul>
 *
 * <p><b>解决方案（最佳实践 — 显式包装法）：</b></p>
 * <ul>
 *   <li>在主线程显式捕获 token → 生成带上下文绑定的 Runnable/Callable/Supplier</li>
 *   <li>异步执行前自动 bind(token)，执行后自动 unbind()</li>
 *   <li>无论线程池如何复用，都能保证上下文隔离与安全清理</li>
 * </ul>
 *
 * <p><b>与 TaskDecorator 的关系：</b></p>
 * <ul>
 *   <li>TaskDecorator 是 Spring Async 配置层面的方案（@Async 方法生效）</li>
 *   <li>本类是低层工具类（直接操作 Runnable），适用于任意线程池 + CompletableFuture 场景</li>
 *   <li>两者可互补：TaskDecorator 处理 @Async 方法，AsyncContextHolder 处理手动 runAsync</li>
 * </ul>
 *
 * @see UserPermissionContext
 * @see AuthTokenFilter
 * @see DelegatingExecutor
 * @version 3.1.0-P1.4
 */
public class AsyncContextHolder {

    private static final Logger log = LoggerFactory.getLogger(AsyncContextHolder.class);

    /**
     * 包装 Runnable：主线程捕获 Token → 异步线程中 bind/unbind。
     *
     * <p>用法替换：</p>
     * <pre>{@code
     * // ❌ 旧写法（Token 丢失）
     * CompletableFuture.runAsync(() -> ...);
     *
     * // ✅ 新写法（Token 透传）
     * String token = userPermissionContext.getCurrentToken();
     * CompletableFuture.runAsync(AsyncContextHolder.wrap(() -> ... , token));
     * }</pre>
     *
     * @param task  原始任务
     * @param token 主线程捕获的 token（可为 null，null 时不做任何操作）
     * @return 包装后的任务（执行时自动绑定/解绑上下文）
     */
    public static Runnable wrap(Runnable task, String token) {
        if (token == null || token.isBlank()) {
            // Token 为空时不包装，避免不必要的 ThreadLocal 操作
            log.debug("[AsyncContextHolder] Token 为空，跳过上下文包装");
            return task;
        }
        return () -> {
            // ① 绑定 Token 到当前线程
            UserPermissionContext.CURRENT_TOKEN.set(token);
            log.debug("[AsyncContextHolder] Token 已绑定到异步线程: {}", token.substring(0, Math.min(8, token.length())) + "...");
            try {
                // ② 执行业务逻辑
                task.run();
            } finally {
                // ③ 务必清理，防止线程池复用时信息泄漏
                UserPermissionContext.CURRENT_TOKEN.remove();
                log.debug("[AsyncContextHolder] Token 已从异步线程解绑");
            }
        };
    }

    /**
     * 包装 Runnable — 同时透传 token + orgId（P3.1 orgId 链路修复新增）。
     *
     * <p>用法：</p>
     * <pre>{@code
     * String token = userPermissionContext.getCurrentToken();
     * String orgId = UserPermissionContext.getCurrentOrgId();
     * CompletableFuture.runAsync(AsyncContextHolder.wrap(() -> ... , token, orgId));
     * }</pre>
     */
    public static Runnable wrap(Runnable task, String token, String orgId) {
        boolean hasToken = token != null && !token.isBlank();
        boolean hasOrgId = orgId != null && !orgId.isBlank();
        if (!hasToken && !hasOrgId) {
            log.debug("[AsyncContextHolder] Token 和 OrgId 均为空，跳过上下文包装");
            return task;
        }
        return () -> {
            if (hasToken) {
                UserPermissionContext.CURRENT_TOKEN.set(token);
            }
            if (hasOrgId) {
                UserPermissionContext.CURRENT_ORG_ID.set(orgId);
            }
            log.debug("[AsyncContextHolder] Token+OrgId 已绑定到异步线程: token={}, orgId={}",
                hasToken ? token.substring(0, Math.min(8, token.length())) + "..." : "none",
                hasOrgId ? orgId : "none");
            try {
                task.run();
            } finally {
                UserPermissionContext.CURRENT_TOKEN.remove();
                UserPermissionContext.CURRENT_ORG_ID.remove();
                log.debug("[AsyncContextHolder] Token+OrgId 已从异步线程解绑");
            }
        };
    }

    /**
     * 包装 Supplier（用于 {@link CompletableFuture#supplyAsync(Supplier, Executor)}）。
     *
     * @param supplier 原始 Supplier
     * @param token    主线程捕获的 token
     * @param <T>      返回值类型
     * @return 包装后的 Supplier
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier, String token) {
        if (token == null || token.isBlank()) {
            return supplier;
        }
        return () -> {
            UserPermissionContext.CURRENT_TOKEN.set(token);
            log.debug("[AsyncContextHolder] Supplier Token 已绑定");
            try {
                return supplier.get();
            } finally {
                UserPermissionContext.CURRENT_TOKEN.remove();
                log.debug("[AsyncContextHolder] Supplier Token 已解绑");
            }
        };
    }

    /**
     * 包装 Callable（用于向线程池提交 Callable 任务）。
     *
     * @param callable 原始 Callable
     * @param token    主线程捕获的 token
     * @param <T>      返回值类型
     * @return 包装后的 Callable
     */
    public static <T> Callable<T> wrap(Callable<T> callable, String token) {
        if (token == null || token.isBlank()) {
            return callable;
        }
        return () -> {
            UserPermissionContext.CURRENT_TOKEN.set(token);
            log.debug("[AsyncContextHolder] Callable Token 已绑定");
            try {
                return callable.call();
            } finally {
                UserPermissionContext.CURRENT_TOKEN.remove();
                log.debug("[AsyncContextHolder] Callable Token 已解绑");
            }
        };
    }

    /**
     * 便捷方法：在指定 Executor 上执行带上下文透传的 Runnable。
     *
     * <p>等价于：{@code CompletableFuture.runAsync(wrap(task, token), executor)}</p>
     *
     * @param task    业务任务
     * @param token   当前线程的 token
     * @param executor 执行线程池
     * @return CompletableFuture<Void>
     */
    public static CompletableFuture<Void> runAsync(Runnable task, String token, Executor executor) {
        return CompletableFuture.runAsync(wrap(task, token), executor);
    }

    /**
     * 便捷方法：在指定 Executor 上执行带上下文透传的 Supplier。
     *
     * @param supplier 业务 Supplier
     * @param token    当前线程的 token
     * @param executor 执行线程池
     * @param <T>      返回值类型
     * @return CompletableFuture<T>
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, String token, Executor executor) {
        return CompletableFuture.supplyAsync(wrap(supplier, token), executor);
    }

    /**
     * 便捷方法：ForkJoinPool.commonPool() 上执行（不推荐生产环境使用，因为无法管理线程数）。
     */
    public static CompletableFuture<Void> runAsync(Runnable task, String token) {
        return CompletableFuture.runAsync(wrap(task, token));
    }
}
