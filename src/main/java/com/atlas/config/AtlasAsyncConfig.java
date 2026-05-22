package com.atlas.config;

import com.atlas.auth.UserPermissionContext;
import com.atlas.auth.async.AsyncContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Spring 异步任务配置 — ThreadLocal Token 透传（TaskDecorator 方案）。
 *
 * <p><b>作用域：</b> 为所有 {@code @Async} 方法提供"自动上下文继承"能力。
 * 主线程通过 {@link com.atlas.auth.AuthTokenFilter} 绑定的 Token，
 * 在异步执行前由 {@link TokenPropagatingTaskDecorator} 自动复制到新线程。</p>
 *
 * <p><b>三种透传方案的映射：</b></p>
 * <ul>
 *   <li>{@code @Async} 方法  → 使用本配置的 TaskDecorator（自动透传）</li>
 *   <li>{@code CompletableFuture.runAsync()}  → 使用 {@link com.atlas.auth.async.AsyncContextHolder}（显式包装）</li>
 *   <li>{@code Executor.execute()}  → 使用 {@link com.atlas.auth.async.DelegatingExecutor}（Executor 级别拦截）</li>
 * </ul>
 *
 * @see com.atlas.auth.async.AsyncContextHolder
 * @see com.atlas.auth.async.DelegatingExecutor
 * @see UserPermissionContext
 * @version 3.1.0-P1.4
 */
@Configuration
public class AtlasAsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AtlasAsyncConfig.class);

    /** 核心异步线程池 Bean（供 Spring AI SSE、后台任务使用） */
    @Bean(name = "atlasTaskExecutor")
    public ThreadPoolTaskExecutor atlasTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("atlas-async-");
        // 关键：设置 TaskDecorator，实现 ThreadLocal 自动透传
        executor.setTaskDecorator(new TokenPropagatingTaskDecorator());
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("[AtlasAsyncConfig] atlasTaskExecutor 初始化完成: core={}, max={}, queue={}",
            executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }

    /**
     * Spring AsyncConfigurer 默认 Executor — 让 @Async 方法自动使用带 Token 透传的线程池。
     */
    @Override
    public Executor getAsyncExecutor() {
        return atlasTaskExecutor();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 内部 TaskDecorator 实现
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Token 传播装饰器 — Spring TaskDecorator 标准实现。
     *
     * <p>原理：提交任务时（主线程）捕获 Token，执行时（异步线程）bind，
     * 执行完毕后 finally 中 unbind，保证线程池复用安全。</p>
     */
    static class TokenPropagatingTaskDecorator implements TaskDecorator {

        private static final Logger log = LoggerFactory.getLogger(TokenPropagatingTaskDecorator.class);

        @Override
        public Runnable decorate(Runnable runnable) {
            // M5.6：提交任务时捕获完整安全上下文快照（token + orgId）。
            // orgId 是 Tool 层唯一可信租户边界，不能只传播 token。
            String token = UserPermissionContext.CURRENT_TOKEN.get();
            String orgId = UserPermissionContext.getCurrentOrgId();
            if ((token == null || token.isBlank()) && (orgId == null || orgId.isBlank())) {
                log.debug("[TokenPropagatingTaskDecorator] 主线程无 token/orgId，按空上下文隔离执行");
            }

            // 统一委托给 AsyncContextHolder，复用保存旧值→绑定快照→finally恢复旧值的安全策略。
            return AsyncContextHolder.wrap(runnable, token, orgId);
        }
    }
}
