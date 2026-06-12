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
 *
 * <p>中文说明：Agent 编排里 SSE、Graph、ReAct、Tool 调用经常跨线程执行。如果异步线程丢失
 * token/orgId，就会出现“主线程已认证、执行线程匿名或租户缺失”的隐蔽安全问题。</p>
 *
 * <p>安全边界：本配置只传播服务端已经确认的 ThreadLocal 快照，不能从请求体、LLM 参数或前端字段
 * 推导身份。任务结束必须恢复旧值，避免线程池复用时把 A 用户上下文泄漏给 B 用户。</p>
 */
@Configuration
public class AtlasAsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AtlasAsyncConfig.class);

    /**
     * 核心异步线程池 Bean（供 Spring AI SSE、后台任务使用）。
     *
     * <p>中文说明：队列和线程数保持保守，避免 Agent 过程事件或后台任务无限堆积；CallerRunsPolicy
     * 让压力回到提交线程，而不是静默丢弃安全上下文任务。</p>
     */
    @Bean(name = "atlasTaskExecutor")
    public ThreadPoolTaskExecutor atlasTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(256);
        executor.setThreadNamePrefix("atlas-async-");
        // 关键：设置 TaskDecorator，实现服务端可信 ThreadLocal 快照自动透传。
        executor.setTaskDecorator(new TokenPropagatingTaskDecorator());
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        log.info("[AtlasAsyncConfig] atlasTaskExecutor 初始化完成: core={}, max={}, queue={}",
            executor.getCorePoolSize(), executor.getMaxPoolSize(), executor.getQueueCapacity());
        return executor;
    }

    /**
     * Spring AsyncConfigurer 默认 Executor — 让 @Async 方法自动使用带 Token 透传的线程池。
     *
     * <p>安全边界：如果后续新增 @Async 方法触达 Tool/kube-manager，也会默认走本 executor，
     * 避免忘记手动包装 AsyncContextHolder。</p>
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
     *
     * <p>中文说明：这里传播的是 token + orgId 二元组。只传播 token 会让 kube-manager 调用有凭证
     * 但缺少租户边界；只传播 orgId 又无法代表真实用户身份。</p>
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
