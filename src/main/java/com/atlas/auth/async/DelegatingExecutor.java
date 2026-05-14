package com.atlas.auth.async;

import com.atlas.auth.UserPermissionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * Spring TaskDecorator 风格的可编程 Executor 包装器 — ThreadLocal 上下文透传。
 *
 * <p><b>为什么需要？</b></p>
 * <ul>
 *   <li>Spring 的 {@code @Async} 方法使用 {@link org.springframework.core.task.TaskDecorator} 实现上下文透传</li>
 *   <li>但在手动使用 {@link java.util.concurrent.Executor} 的场景（如 SSE 流处理），需要同样能力</li>
 *   <li>本类提供一种与 Spring AsyncConfigurer 同级别的方案，适用于任意 Executor（包括自定义线程池）</li>
 * </ul>
 *
 * <p><b>两种使用方式：</b></p>
 * <ol>
 *   <li><b>显式包裹</b>：{@code new DelegatingExecutor(executor, token)} — 捕获主线程 token 后包装</li>
 *   <li><b>自动继承</b>：{@code DelegatingExecutor.inheritFromCurrentThread(executor)} —
 *       自动读取当前 ThreadLocal 的 token，适合在同一线程内创建</li>
 * </ol>
 *
 * <p><b>与 {@link AsyncContextHolder} 的关系：</b></p>
 * <ul>
 *   <li>{@link AsyncContextHolder} 直接操作 Runnable，适合单次临时任务</li>
 *   <li>{@link DelegatingExecutor} 是 Executor 级别的拦截，适合批量提交、线程池复用</li>
 *   <li>两者底层原理一致：捕获 token → 执行任务 → 清理 ThreadLocal</li>
 * </ul>
 *
 * @see AsyncContextHolder
 * @see UserPermissionContext
 * @version 3.1.0-P1.4
 */
public class DelegatingExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(DelegatingExecutor.class);

    private final Executor delegate;
    private final String token;

    /**
     * 构建一个自动透传 Token 的 Executor。
     *
     * @param delegate 底层线程池（如 ThreadPoolTaskExecutor、ForkJoinPool 等）
     * @param token    需要透传的用户 Token（主线程捕获）
     */
    public DelegatingExecutor(Executor delegate, String token) {
        this.delegate = delegate;
        this.token = token;
    }

    /**
     * 便捷工厂：自动从当前线程的 ThreadLocal 读取 Token 并构建包装器。
     *
     * <p>用法：</p>
     * <pre>{@code
     * Executor delegating = DelegatingExecutor.inheritFromCurrentThread(executor);
     * CompletableFuture.runAsync(() -> { ... }, delegating);
     * // 异步线程内 UserPermissionContext.current() 正常返回
     * }</pre>
     *
     * @param delegate 底层线程池
     * @return 包装后的 Executor（Token 从当前线程自动继承）
     */
    public static Executor inheritFromCurrentThread(Executor delegate) {
        String token = UserPermissionContext.CURRENT_TOKEN.get();
        if (token == null || token.isBlank()) {
            log.warn("[DelegatingExecutor] 当前线程无 Token，透传将失效（匿名请求）");
        }
        return new DelegatingExecutor(delegate, token);
    }

    @Override
    public void execute(Runnable command) {
        // 仅 wrapper 中的逻辑需要实际写入 token，这里复用 AsyncContextHolder
        delegate.execute(AsyncContextHolder.wrap(command, token));
    }

    /**
     * 获取底层原始 Executor（用于需要绕过透传的场景）。
     */
    public Executor getDelegate() {
        return delegate;
    }
}
