package com.atlas.auth.async;

import com.atlas.auth.UserPermissionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;

/**
 * Spring TaskDecorator 风格的可编程 Executor 包装器 — ThreadLocal 安全上下文透传。
 *
 * <p><b>M5.6 变更：</b>多租户上下文必须以 token + orgId 原子传播。token-only 传播会让 Tool 层
 * 丢失可信租户边界，因此本类保存并传播完整安全上下文，同时保留旧构造器兼容既有调用点。</p>
 *
 * @see AsyncContextHolder
 * @see UserPermissionContext
 * @version 3.1.0-M5.6
 */
public class DelegatingExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(DelegatingExecutor.class);

    private final Executor delegate;
    private final String token;
    private final String orgId;

    /**
     * 构建一个自动透传安全上下文的 Executor（兼容旧 token-only 调用）。
     * <p>旧调用没有显式 orgId 时，会从当前线程捕获 orgId，避免遗留代码路径丢失租户上下文。</p>
     *
     * @param delegate 底层线程池（如 ThreadPoolTaskExecutor、ForkJoinPool 等）
     * @param token    需要透传的用户 Token（主线程捕获）
     */
    public DelegatingExecutor(Executor delegate, String token) {
        this(delegate, token, UserPermissionContext.getCurrentOrgId());
    }

    /**
     * 构建一个自动透传 token + orgId 的 Executor。
     *
     * @param delegate 底层线程池
     * @param token    认证 Token
     * @param orgId    可信组织 ID
     */
    public DelegatingExecutor(Executor delegate, String token, String orgId) {
        this.delegate = delegate;
        this.token = token;
        this.orgId = orgId;
    }

    /**
     * 便捷工厂：自动从当前线程的 ThreadLocal 读取 token + orgId 并构建包装器。
     *
     * @param delegate 底层线程池
     * @return 包装后的 Executor（安全上下文从当前线程自动继承）
     */
    public static Executor inheritFromCurrentThread(Executor delegate) {
        String token = UserPermissionContext.CURRENT_TOKEN.get();
        String orgId = UserPermissionContext.getCurrentOrgId();
        if ((token == null || token.isBlank()) && (orgId == null || orgId.isBlank())) {
            log.warn("[DelegatingExecutor] 当前线程无 token/orgId，透传上下文为空");
        }
        return new DelegatingExecutor(delegate, token, orgId);
    }

    @Override
    public void execute(Runnable command) {
        // 复用 AsyncContextHolder 的统一绑定/恢复逻辑，确保 token + orgId 语义一致。
        delegate.execute(AsyncContextHolder.wrap(command, token, orgId));
    }

    /**
     * 获取底层原始 Executor（用于需要绕过透传的场景）。
     */
    public Executor getDelegate() {
        return delegate;
    }
}
