package com.atlas.auth.async;

import com.atlas.auth.UserPermissionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 异步上下文透传器 — ThreadLocal 安全上下文在 CompletableFuture / Executor 线程切换中不丢失。
 *
 * <p><b>M5.6 治理原则：</b>在多租户场景中，token 与 orgId 必须被视为一个原子的安全上下文快照。
 * token 代表认证身份，orgId 代表 Tool 层唯一可信租户边界；任何异步入口只传播 token、不传播 orgId，
 * 都会造成后续 Tool 无法解析可信租户并触发 fail-safe 拒绝执行。</p>
 *
 * <p><b>清理策略：</b>执行任务前保存当前线程旧值，绑定快照后执行业务，finally 中恢复旧值。
 * 这样既能防止线程池复用泄漏，也能避免 CallerRunsPolicy / 嵌套异步包装把外层请求上下文误删。</p>
 *
 * @see UserPermissionContext
 * @see AuthTokenFilter
 * @see DelegatingExecutor
 * @version 3.1.0-M5.6
 */
public class AsyncContextHolder {

    private static final Logger log = LoggerFactory.getLogger(AsyncContextHolder.class);

    /**
     * 包装 Runnable：兼容旧 token-only 调用。
     *
     * <p>旧调用不显式传 orgId 时，自动从当前线程捕获 orgId，最大限度避免遗留调用点丢失租户上下文。</p>
     */
    public static Runnable wrap(Runnable task, String token) {
        return wrap(task, token, UserPermissionContext.getCurrentOrgId());
    }

    /**
     * 包装 Runnable：同时透传 token + orgId。
     */
    public static Runnable wrap(Runnable task, String token, String orgId) {
        return () -> runWithContext(task, token, orgId);
    }

    /**
     * 包装 Supplier：兼容旧 token-only 调用。
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier, String token) {
        return wrap(supplier, token, UserPermissionContext.getCurrentOrgId());
    }

    /**
     * 包装 Supplier：同时透传 token + orgId。
     */
    public static <T> Supplier<T> wrap(Supplier<T> supplier, String token, String orgId) {
        return () -> supplyWithContext(supplier, token, orgId);
    }

    /**
     * 包装 Callable：兼容旧 token-only 调用。
     */
    public static <T> Callable<T> wrap(Callable<T> callable, String token) {
        return wrap(callable, token, UserPermissionContext.getCurrentOrgId());
    }

    /**
     * 包装 Callable：同时透传 token + orgId。
     */
    public static <T> Callable<T> wrap(Callable<T> callable, String token, String orgId) {
        return () -> callWithContext(callable, token, orgId);
    }

    /**
     * 便捷方法：在指定 Executor 上执行带上下文透传的 Runnable（兼容旧 token-only 调用）。
     */
    public static CompletableFuture<Void> runAsync(Runnable task, String token, Executor executor) {
        return CompletableFuture.runAsync(wrap(task, token), executor);
    }

    /**
     * 便捷方法：在指定 Executor 上执行带 token + orgId 上下文透传的 Runnable。
     */
    public static CompletableFuture<Void> runAsync(Runnable task, String token, String orgId, Executor executor) {
        return CompletableFuture.runAsync(wrap(task, token, orgId), executor);
    }

    /**
     * 便捷方法：在指定 Executor 上执行带上下文透传的 Supplier（兼容旧 token-only 调用）。
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, String token, Executor executor) {
        return CompletableFuture.supplyAsync(wrap(supplier, token), executor);
    }

    /**
     * 便捷方法：在指定 Executor 上执行带 token + orgId 上下文透传的 Supplier。
     */
    public static <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier, String token, String orgId, Executor executor) {
        return CompletableFuture.supplyAsync(wrap(supplier, token, orgId), executor);
    }

    /**
     * 便捷方法：ForkJoinPool.commonPool() 上执行（不推荐生产环境使用，因为无法管理线程数）。
     */
    public static CompletableFuture<Void> runAsync(Runnable task, String token) {
        return CompletableFuture.runAsync(wrap(task, token));
    }

    /**
     * 在当前线程临时绑定安全上下文并执行 Runnable。
     */
    private static void runWithContext(Runnable task, String token, String orgId) {
        applyAndRestore(token, orgId, () -> {
            task.run();
            return null;
        });
    }

    /**
     * 在当前线程临时绑定安全上下文并执行 Supplier。
     */
    private static <T> T supplyWithContext(Supplier<T> supplier, String token, String orgId) {
        return applyAndRestore(token, orgId, supplier::get);
    }

    /**
     * 在当前线程临时绑定安全上下文并执行 Callable。
     */
    private static <T> T callWithContext(Callable<T> callable, String token, String orgId) throws Exception {
        return applyAndRestoreChecked(token, orgId, callable);
    }

    /**
     * 非受检异常任务的上下文应用与恢复模板。
     */
    private static <T> T applyAndRestore(String token, String orgId, Supplier<T> supplier) {
        String previousToken = UserPermissionContext.CURRENT_TOKEN.get();
        String previousOrgId = UserPermissionContext.CURRENT_ORG_ID.get();
        bindSnapshot(token, orgId);
        try {
            return supplier.get();
        } finally {
            restore(previousToken, previousOrgId);
        }
    }

    /**
     * 受检异常任务的上下文应用与恢复模板。
     */
    private static <T> T applyAndRestoreChecked(String token, String orgId, Callable<T> callable) throws Exception {
        String previousToken = UserPermissionContext.CURRENT_TOKEN.get();
        String previousOrgId = UserPermissionContext.CURRENT_ORG_ID.get();
        bindSnapshot(token, orgId);
        try {
            return callable.call();
        } finally {
            restore(previousToken, previousOrgId);
        }
    }

    /**
     * 将捕获的安全上下文快照应用到当前线程。
     */
    private static void bindSnapshot(String token, String orgId) {
        if (token != null && !token.isBlank()) {
            UserPermissionContext.CURRENT_TOKEN.set(token);
        } else {
            UserPermissionContext.CURRENT_TOKEN.remove();
        }
        if (orgId != null && !orgId.isBlank()) {
            UserPermissionContext.CURRENT_ORG_ID.set(orgId);
        } else {
            UserPermissionContext.CURRENT_ORG_ID.remove();
        }
        log.debug("[AsyncContextHolder] 安全上下文已绑定: token={}, orgId={}",
            token != null && !token.isBlank() ? token.substring(0, Math.min(8, token.length())) + "..." : "none",
            orgId != null && !orgId.isBlank() ? orgId : "none");
    }

    /**
     * 恢复当前线程原有上下文，避免线程池复用、嵌套包装或 CallerRunsPolicy 造成污染。
     */
    private static void restore(String previousToken, String previousOrgId) {
        if (previousToken != null) {
            UserPermissionContext.CURRENT_TOKEN.set(previousToken);
        } else {
            UserPermissionContext.CURRENT_TOKEN.remove();
        }
        if (previousOrgId != null) {
            UserPermissionContext.CURRENT_ORG_ID.set(previousOrgId);
        } else {
            UserPermissionContext.CURRENT_ORG_ID.remove();
        }
        log.debug("[AsyncContextHolder] 安全上下文已恢复");
    }
}
