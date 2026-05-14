package com.atlas.auth.async;

import com.atlas.auth.UserPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 异步上下文透传单元测试 — 验证 ThreadLocal Token 在 CompletableFuture 中不丢失。
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>{@link AsyncContextHolder#wrap(Runnable, String)} — 基础透传</li>
 *   <li>{@link AsyncContextHolder#runAsync(Runnable, String, Executor)} — supplyAsync 透传</li>
 *   <li>null/blank Token — 不包装，原样执行</li>
 *   <li>线程池复用 — 验证任务结束后 ThreadLocal 已清理</li>
 * </ul>
 *
 * @version 3.1.0-P1.4
 */
class AsyncContextHolderTest {

    private ExecutorService executor;
    private static final String TEST_TOKEN = "test-bearer-token-123";

    @BeforeEach
    void setUp() {
        // 固定2线程池，强制复用场景（更容易暴露 ThreadLocal 泄漏）
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        // 清理当前线程 ThreadLocal（所有测试必须兜底 remove）
        UserPermissionContext.CURRENT_TOKEN.remove();
    }

    /**
     * 测试1：基础 Runnable 包装 — Token 在异步线程中可读。
     */
    @Test
    void testWrapRunnable_tokenPropagated() throws InterruptedException {
        // 主线程绑定 Token（模拟 AuthTokenFilter 行为）
        UserPermissionContext.CURRENT_TOKEN.set(TEST_TOKEN);

        // 提交异步任务（runAsync 使用 ForkJoinPool，与主线程不同）
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            // 通过 ThreadLocal 读取 Token（模拟 ToolRegistry/HttpClient 行为）
            return UserPermissionContext.CURRENT_TOKEN.get();
        });

        // ❌ 不包装时 Token 丢失
        String lostToken = future.join();
        assertNull(lostToken, "不包装时 Token 应该丢失");

        // ✅ 包装后 Token 透传
        CompletableFuture<String> wrappedFuture = CompletableFuture.supplyAsync(
            AsyncContextHolder.wrap(
                (java.util.function.Supplier<String>) () -> UserPermissionContext.CURRENT_TOKEN.get(),
                TEST_TOKEN
            )
        );
        String propagatedToken = wrappedFuture.join();
        assertEquals(TEST_TOKEN, propagatedToken, "包装后 Token 应该透传到异步线程");
    }

    /**
     * 测试2：runAsync 便捷方法 — 使用自定义线程池。
     */
    @Test
    void testRunAsync_withExecutor() throws Exception {
        CompletableFuture<Void> future = AsyncContextHolder.runAsync(() -> {
            String token = UserPermissionContext.CURRENT_TOKEN.get();
            assertEquals(TEST_TOKEN, token, "runAsync 便捷方法应正确透传 Token");
        }, TEST_TOKEN, executor);

        future.get(5, TimeUnit.SECONDS);
    }

    /**
     * 测试3：supplyAsync 便捷方法 — 返回值正确。
     */
    @Test
    void testSupplyAsync_withExecutor() throws Exception {
        CompletableFuture<String> future = AsyncContextHolder.supplyAsync(() -> {
            return UserPermissionContext.CURRENT_TOKEN.get();
        }, TEST_TOKEN, executor);

        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals(TEST_TOKEN, result);
    }

    /**
     * 测试4：null/blank Token — 不做包装，避免不必要的 ThreadLocal 操作。
     */
    @Test
    void testWrap_nullOrBlankToken_noOp() {
        Runnable original = () -> {};

        Runnable r1 = AsyncContextHolder.wrap(original, null);
        Runnable r2 = AsyncContextHolder.wrap(original, "");
        Runnable r3 = AsyncContextHolder.wrap(original, "   ");

        // null/blank 时返回原始对象（不包装）
        assertSame(original, r1, "null token 时不应包装");
        assertSame(original, r2, "空字符串 token 时不应包装");
        assertSame(original, r3, "空白 token 时不应包装");
    }

    /**
     * 测试5：线程池复用安全 — 任务结束后 ThreadLocal 已清理。
     *
     * <p>这是核心安全测试：如果清理逻辑有 bug，第二个任务会"继承"第一个任务的 Token。</p>
     */
    @Test
    void testThreadPoolReuse_cleanupAfterExecution() throws Exception {
        // 先提交带 Token 的任务到线程池
        AsyncContextHolder.runAsync(() -> {
            // 验证 Token 已绑定
            assertEquals("token-A", UserPermissionContext.CURRENT_TOKEN.get());
        }, "token-A", executor).get(5, TimeUnit.SECONDS);

        // 立即提交不带 Token 的任务（同一个线程可能被复用）
        AsyncContextHolder.runAsync(() -> {
            // 验证上一个任务的 Token 不会泄漏
            assertNull(UserPermissionContext.CURRENT_TOKEN.get(),
                "线程池复用时上一个任务的 Token 不应残留");
        }, null, executor).get(5, TimeUnit.SECONDS);
    }

    /**
     * 测试6：Callable 包装 — Token 透传正确。
     */
    @Test
    void testWrapCallable() throws Exception {
        Callable<String> task = AsyncContextHolder.wrap(
            (Callable<String>) () -> UserPermissionContext.CURRENT_TOKEN.get(),
            TEST_TOKEN
        );
        Future<String> future = executor.submit(task);
        String result = future.get(5, TimeUnit.SECONDS);
        assertEquals(TEST_TOKEN, result);
    }

    /**
     * 测试7：异常场景 — 即使任务抛出异常，ThreadLocal 仍被清理。
     */
    @Test
    void testCleanupOnException() {
        CompletableFuture<Void> future = AsyncContextHolder.runAsync(() -> {
            UserPermissionContext.CURRENT_TOKEN.set("dummy");
            throw new RuntimeException("模拟业务异常");
        }, TEST_TOKEN, executor);

        // 等待异常完成
        assertThrows(ExecutionException.class, future::get);

        // 验证 ThreadLocal 已被清理（通过再 submit 一个任务检查）
        AsyncContextHolder.runAsync(() -> {
            assertNull(UserPermissionContext.CURRENT_TOKEN.get(), "异常后 Token 应被清理");
        }, null, executor).join();
    }
}
