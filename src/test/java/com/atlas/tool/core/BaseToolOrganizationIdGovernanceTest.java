package com.atlas.tool.core;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * M5.5 organizationId 来源治理契约测试。
 *
 * <p>中文说明：本测试保护 BaseTool 中的租户解析逻辑，确保 kube-manager URL path 中的
 * organizationId 只能来自服务端可信 {@link UserPermissionContext}，不能来自 LLM Action、
 * 用户自然语言、前端 Map 或测试伪造参数。</p>
 *
 * <p>安全边界：本测试只调用测试专用 Tool 暴露的解析方法，不触发真实 HTTP、不调用 kube-manager、
 * 不执行外部 Tool、不调用 LLM/MCP、不写 audit/memory。它保护的是多租户隔离不变量：
 * 缺少可信 orgId 必须 fail-closed。</p>
 *
 * <p>organizationId 是多租户安全边界，不是普通业务参数。LLM Action、用户自然语言
 * 或外部调用者构造的 params 都不能决定最终访问哪个租户路径；Tool 执行层必须以
 * {@link UserPermissionContext} 中的认证会话 orgId 为权威来源。</p>
 */
class BaseToolOrganizationIdGovernanceTest {

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void m55_resolveOrganizationId_shouldPreferThreadLocalOverOrganizationIdParam() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        TestTool tool = new TestTool();

        String resolved = tool.exposeResolveOrganizationId(Map.of("organizationId", "100002"));

        assertEquals("100001", resolved, "Tool 执行层必须以会话 orgId 为准，不能被 params.organizationId 覆盖");
    }

    @Test
    void m55_resolveOrganizationId_shouldPreferThreadLocalOverOrgIdAliasParam() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        TestTool tool = new TestTool();

        String resolved = tool.exposeResolveOrganizationId(Map.of("orgId", "100002"));

        assertEquals("100001", resolved, "Tool 执行层必须以会话 orgId 为准，不能被 params.orgId 别名覆盖");
    }

    @Test
    void m55_resolveOrganizationId_shouldUseThreadLocalWhenParamsMissing() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        TestTool tool = new TestTool();

        String resolved = tool.exposeResolveOrganizationId(Map.of());

        assertEquals("100001", resolved);
    }

    @Test
    void m55_resolveOrganizationId_shouldFailWithoutTrustedOrgContextEvenWhenParamProvided() {
        TestTool tool = new TestTool();

        assertThrows(AtlasToolValidationException.class,
            () -> tool.exposeResolveOrganizationId(Map.of("organizationId", "100002")),
            "缺少可信会话 orgId 时，不能仅凭 params.organizationId 构造租户路径");
    }

    /**
     * 测试专用 Tool：只暴露 BaseTool 的 orgId 解析逻辑，不触发真实 HTTP 调用。
     */
    private static class TestTool extends BaseTool {
        TestTool() {
            super("test_org_id_governance", "测试 organizationId 治理");
        }

        String exposeResolveOrganizationId(Map<String, Object> params) {
            return resolveOrganizationId(params);
        }

        @Override
        protected Set<String> getRequiredParams() {
            return Set.of();
        }

        @Override
        protected AtlasToolResult doExecute(Map<String, Object> params) {
            return AtlasToolResult.ok("ok", Map.of());
        }
    }
}
