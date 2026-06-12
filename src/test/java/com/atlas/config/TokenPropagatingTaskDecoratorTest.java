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
 *
 * <p>中文说明：本测试把“异步线程也必须拥有同一份服务端可信身份快照”作为学习重点。
 * 输入来自 {@link UserPermissionContext} 中已经由认证链路写入的 ThreadLocal token/orgId，
 * 输出是包装后的 {@link Runnable}，供 Spring TaskExecutor 在线程池中执行时恢复上下文。</p>
 *
 * <p>安全边界：本测试只构造 TaskDecorator 和内存 Runnable，不启动 Spring 容器、不调用 Tool、
 * 不调用 MCP/LLM/RAG、不访问 kube-manager、不写 audit/memory，也不打开 Phase 2 NIM/HPC/Slurm/BCM
 * 能力。token/orgId 只能来自服务端可信上下文，不能从请求体、前端字段或 LLM 参数补出来；
 * 异步任务结束后必须恢复旧 ThreadLocal，避免把上一个用户或组织的权限泄漏给后续任务。</p>
 */
class TokenPropagatingTaskDecoratorTest {

    /**
     * 每个用例结束后主动清理 ThreadLocal，防止测试线程复用时把 token/orgId 污染到下一条契约。
     */
    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    /**
     * M5.6 RED：TaskDecorator 必须传播可信 orgId。
     * <p>中文说明：token 和 orgId 必须作为同一份可信身份快照一起传播，否则 kube-manager
     * READ Tool 即使拿到 token，也可能丢失租户边界。</p>
     *
     * <p>安全边界：这里不会触发真实 HTTP，也不会执行 Tool；断言只保护异步上下文恢复与清理。</p>
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
     * <p>中文说明：部分只读或本地编排链路可能只需要可信 orgId 做租户隔离，本测试确保
     * TaskDecorator 不会因为 token 为空就跳过 orgId 快照。</p>
     *
     * <p>安全边界：orgId 仍然必须来自服务端可信 ThreadLocal；本测试不允许从请求体、
     * LLM 参数或前端字段临时制造 orgId。</p>
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
