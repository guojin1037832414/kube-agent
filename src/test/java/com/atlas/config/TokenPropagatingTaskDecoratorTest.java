package com.atlas.config;

import com.atlas.auth.UserPermissionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskDecorator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * M5.6 Spring TaskDecorator 上下文透传契约测试。
 *
 * <p>核心目标：验证 atlasTaskExecutor 的 TaskDecorator 在提交任务时捕获 token + orgId，
 * 并在异步线程执行期间恢复为完整可信上下文，执行结束后不泄漏。</p>
 */
class TokenPropagatingTaskDecoratorTest {

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    /**
     * M5.6 RED：TaskDecorator 必须传播可信 orgId。
     * <p>当前生产代码只传播 token，本测试应先以 orgId 为 null 的断言失败暴露缺口。</p>
     */
    @Test
    void m56_taskDecorator_shouldPropagateTrustedOrgIdAndCleanup() {
        TaskDecorator decorator = new AtlasAsyncConfig.TokenPropagatingTaskDecorator();
        UserPermissionContext.CURRENT_TOKEN.set("token-A");
        UserPermissionContext.CURRENT_ORG_ID.set("100002");

        Runnable decorated = decorator.decorate(() -> {
            assertEquals("token-A", UserPermissionContext.CURRENT_TOKEN.get());
            assertEquals("100002", UserPermissionContext.getCurrentOrgId());
        });

        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
        decorated.run();

        assertNull(UserPermissionContext.CURRENT_TOKEN.get(), "TaskDecorator 执行后 Token 不应泄漏");
        assertNull(UserPermissionContext.getCurrentOrgId(), "TaskDecorator 执行后 orgId 不应泄漏");
    }

    /**
     * M5.6 RED：即使 token 为空，只要存在可信 orgId，也必须执行上下文包装。
     * <p>当前实现 token 为空时直接返回原始 Runnable，会导致 orgId 快照丢失。</p>
     */
    @Test
    void m56_taskDecorator_shouldPropagateOrgIdEvenWhenTokenBlank() {
        TaskDecorator decorator = new AtlasAsyncConfig.TokenPropagatingTaskDecorator();
        UserPermissionContext.CURRENT_ORG_ID.set("100002");

        Runnable decorated = decorator.decorate(() -> {
            assertNull(UserPermissionContext.CURRENT_TOKEN.get());
            assertEquals("100002", UserPermissionContext.getCurrentOrgId());
        });

        UserPermissionContext.CURRENT_ORG_ID.remove();
        decorated.run();

        assertNull(UserPermissionContext.CURRENT_TOKEN.get());
        assertNull(UserPermissionContext.getCurrentOrgId());
    }
}
