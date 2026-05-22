package com.atlas.auth.async;

import com.atlas.auth.UserPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * M5.6 异步 Executor 级上下文透传契约测试。
 *
 * <p>核心目标：验证 {@link DelegatingExecutor} 不仅传播 token，也必须传播可信 orgId。
 * 多租户隔离中 orgId 是 Tool 层唯一可信租户边界，不能在 Executor 包装层丢失。</p>
 */
class DelegatingExecutorTest {

    private ExecutorService delegate;

    @AfterEach
    void tearDown() {
        if (delegate != null) {
            delegate.shutdownNow();
        }
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    /**
     * M5.6 RED：显式构造 DelegatingExecutor 时应支持 token + orgId 原子传播。
     * <p>当前生产代码只有 (Executor, token) 构造器，本测试应先以编译失败暴露契约缺口。</p>
     */
    @Test
    void m56_execute_shouldPropagateExplicitTrustedOrgIdAndCleanup() throws Exception {
        delegate = Executors.newSingleThreadExecutor();
        Executor executor = new DelegatingExecutor(delegate, "token-A", "100002");

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            assertEquals("token-A", UserPermissionContext.CURRENT_TOKEN.get());
            assertEquals("100002", UserPermissionContext.getCurrentOrgId());
        }, executor);
        future.get(5, TimeUnit.SECONDS);

        CompletableFuture<Void> cleanupProbe = CompletableFuture.runAsync(() -> {
            assertNull(UserPermissionContext.CURRENT_TOKEN.get(), "DelegatingExecutor 执行后 Token 不应泄漏");
            assertNull(UserPermissionContext.getCurrentOrgId(), "DelegatingExecutor 执行后 orgId 不应泄漏");
        }, delegate);
        cleanupProbe.get(5, TimeUnit.SECONDS);
    }

    /**
     * M5.6 RED：inheritFromCurrentThread 必须同时捕获当前线程的 token 和 orgId。
     */
    @Test
    void m56_inheritFromCurrentThread_shouldCaptureTokenAndTrustedOrgId() throws Exception {
        delegate = Executors.newSingleThreadExecutor();
        UserPermissionContext.CURRENT_TOKEN.set("token-A");
        UserPermissionContext.CURRENT_ORG_ID.set("100002");

        Executor executor = DelegatingExecutor.inheritFromCurrentThread(delegate);
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            assertEquals("token-A", UserPermissionContext.CURRENT_TOKEN.get());
            assertEquals("100002", UserPermissionContext.getCurrentOrgId());
        }, executor);

        future.get(5, TimeUnit.SECONDS);
    }
}
