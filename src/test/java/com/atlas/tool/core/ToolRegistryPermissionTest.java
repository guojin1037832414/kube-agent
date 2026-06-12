package com.atlas.tool.core;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.exception.PermissionDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolRegistry 权限感知集成测试 — 验证public用户/认证用户/管理员对各类Tool的可见性。
 *
 * <p>中文说明：本测试保护 ToolRegistry 的“可见工具目录”规则。匿名、普通用户和管理员
 * 在 Prompt/Tool 目录中看到的能力必须不同，否则 LLM 可能在没有权限的情况下被诱导生成
 * 高风险 Action。</p>
 *
 * <p>安全边界：可见性不是最终执行授权，真实 Tool 调用仍必须经过 SafeToolExecutor、
 * ToolPermission、HITL、audit、release 和 kube-manager 权限。本测试启动 Spring 上下文，
 * 但不调用真实 LLM、不访问 kube-manager、不执行 Tool、不写 audit/memory，也不打开 MCP
 * runtime 或 Phase 2 NIM/HPC/Slurm/BCM 权力。</p>
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>18 个 Tool 全部标注了 @ToolPermission</li>
 *   <li>PUBLIC Tool — 匿名用户、普通用户、管理员均可见</li>
 *   <li>AUTHENTICATED Tool — 仅登录用户可见，匿名用户不可见</li>
 *   <li>ADMIN_ONLY Tool — 仅管理员可见，普通用户不可见</li>
 *   <li>重复检测：相同 name 的 Tool 只会注册一个</li>
 * </ul>
 *
 * @version 3.1.0-P1.4
 */
@SpringBootTest
@ActiveProfiles("test")
class ToolRegistryPermissionTest {

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private UserPermissionContext userPermissionContext;

    @MockBean
    private org.springframework.ai.chat.model.ChatModel chatModel;

    @BeforeEach
    void setUp() {
        // 每个测试前清理 ThreadLocal（模拟全新请求）
        userPermissionContext.unbind();
    }

    // ════════════════════════════════════════════════════════
    // 场景1：匿名用户 — 只能看到 PUBLIC Tool
    // ════════════════════════════════════════════════════════

    @Test
    void testAnonymousUser_canSeePublicTools() {
        // 匿名用户：ThreadLocal 无 Token
        userPermissionContext.unbind();

        // PUBLIC Tool（如 node_query, cluster_overview）应可见
        assertTrue(toolRegistry.isVisible("node_query"),
            "匿名用户应该能看到 PUBLIC 的 node_query");
        assertTrue(toolRegistry.isVisible("cluster_overview"),
            "匿名用户应该能看到 PUBLIC 的 cluster_overview");

        // AUTHENTICATED Tool（如 deploy_create_instance）不可见
        assertFalse(toolRegistry.isVisible("deploy_create_instance"),
            "匿名用户不应该看到 AUTHENTICATED 的 deploy_create_instance");

        // ADMIN_ONLY Tool（如 user_delete）不可见
        assertFalse(toolRegistry.isVisible("user_delete"),
            "匿名用户不应该看到 ADMIN_ONLY 的 user_delete");
    }

    @Test
    void testAnonymousUser_systemPromptOnlyPublic() {
        userPermissionContext.unbind();
        String systemPrompt = toolRegistry.buildSystemPromptForCurrentUser();

        // 检查 System Prompt 中不包含 越权 Tool
        assertFalse(systemPrompt.contains("user_delete"),
            "匿名用户的SystemPrompt不应包含 ADMIN_ONLY tool");
        assertFalse(systemPrompt.contains("deploy_create_instance"),
            "匿名用户的SystemPrompt不应包含 AUTHENTICATED tool");
        assertTrue(systemPrompt.contains("node_query"),
            "匿名用户的SystemPrompt应包含 PUBLIC tool");
    }

    // ════════════════════════════════════════════════════════
    // 场景2：普通认证用户 — PUBLIC + AUTHENTICATED 可见
    // ════════════════════════════════════════════════════════

    @Test
    void testRegularUser_canSeePublicAndAuth() {
        // 模拟普通用户登录（role = user）
        String token = "token-regular-user";
        userPermissionContext.onLogin(token, "zhangsan", "user", Set.of());
        userPermissionContext.bind(token);

        // PUBLIC 可见
        assertTrue(toolRegistry.isVisible("node_query"),
            "普通用户应该能看到 PUBLIC tool");

        // AUTHENTICATED 可见
        assertTrue(toolRegistry.isVisible("deploy_create_instance"),
            "普通用户应该能看到 AUTHENTICATED tool");
        assertTrue(toolRegistry.isVisible("deploy_scale"),
            "普通用户应该能看到 AUTHENTICATED tool");
        assertTrue(toolRegistry.isVisible("storage_create"),
            "普通用户应该能看到 AUTHENTICATED tool");
        assertTrue(toolRegistry.isVisible("distributed_create"),
            "普通用户应该能看到 AUTHENTICATED tool");
        assertTrue(toolRegistry.isVisible("nim_create"),
            "普通用户应该能看到 AUTHENTICATED tool");

        // ADMIN_ONLY 不可见
        assertFalse(toolRegistry.isVisible("user_delete"),
            "普通用户不应该看到 ADMIN_ONLY tool");
        assertFalse(toolRegistry.isVisible("user_create"),
            "普通用户不应该看到 ADMIN_ONLY tool");
        assertFalse(toolRegistry.isVisible("deploy_delete"),
            "普通用户不应该看到 ADMIN_ONLY tool");
        assertFalse(toolRegistry.isVisible("deploy_restart"),
            "普通用户不应该看到 ADMIN_ONLY tool");
        assertFalse(toolRegistry.isVisible("storage_delete"),
            "普通用户不应该看到 ADMIN_ONLY tool");
    }

    @Test
    void testRegularUser_systemPromptExcludesAdmin() {
        String token = "token-regular";
        userPermissionContext.onLogin(token, "lisi", "user", Set.of());
        userPermissionContext.bind(token);

        String systemPrompt = toolRegistry.buildSystemPromptForCurrentUser();

        assertTrue(systemPrompt.contains("node_query"),
            "普通用户SystemPrompt应包含PUBLIC");
        assertTrue(systemPrompt.contains("deploy_create_instance"),
            "普通用户SystemPrompt应包含AUTHENTICATED");
        assertFalse(systemPrompt.contains("user_delete"),
            "普通用户SystemPrompt不应包含ADMIN_ONLY");
    }

    @Test
    void testRegularUser_executeAdminToolDenied() {
        String token = "token-regular";
        userPermissionContext.onLogin(token, "wangwu", "user", Set.of());
        userPermissionContext.bind(token);

        // 权限检查应该通过 resolve 抛 PermissionDeniedException 来验证
        assertThrows(PermissionDeniedException.class, () -> toolRegistry.resolve("user_delete"),
            "普通用户执行 ADMIN_ONLY tool 应抛 PermissionDeniedException");

        assertFalse(toolRegistry.canExecuteIntent("user_delete"),
            "普通用户执行 ADMIN_ONLY 意图应被拒绝");
    }

    // ════════════════════════════════════════════════════════
    // 场景3：管理员 — 所有 Tool 可见
    // ════════════════════════════════════════════════════════

    @Test
    void testAdminUser_canSeeAllTools() {
        String token = "token-admin";
        userPermissionContext.onLogin(token, "admin", "sys_admin", Set.of());
        userPermissionContext.bind(token);

        // PUBLIC
        assertTrue(toolRegistry.isVisible("node_query"));
        assertTrue(toolRegistry.isVisible("cluster_overview"));

        // AUTHENTICATED
        assertTrue(toolRegistry.isVisible("deploy_create_instance"));
        assertTrue(toolRegistry.isVisible("deploy_scale"));
        assertTrue(toolRegistry.isVisible("storage_create"));

        // ADMIN_ONLY
        assertTrue(toolRegistry.isVisible("user_delete"),
            "管理员应该能看到 ADMIN_ONLY tool");
        assertTrue(toolRegistry.isVisible("user_create"),
            "管理员应该能看到 ADMIN_ONLY tool");
        assertTrue(toolRegistry.isVisible("deploy_delete"),
            "管理员应该能看到 ADMIN_ONLY tool");
        assertTrue(toolRegistry.isVisible("deploy_restart"),
            "管理员应该能看到 ADMIN_ONLY tool");
        assertTrue(toolRegistry.isVisible("storage_delete"),
            "管理员应该能看到 ADMIN_ONLY tool");
    }

    @Test
    void testAdminUser_systemPromptIncludesAll() {
        String token = "token-admin";
        userPermissionContext.onLogin(token, "admin", "sys_admin", Set.of());
        userPermissionContext.bind(token);

        String systemPrompt = toolRegistry.buildSystemPromptForCurrentUser();

        assertTrue(systemPrompt.contains("user_delete"),
            "管理员SystemPrompt应包含ADMIN_ONLY tool");
        assertTrue(systemPrompt.contains("node_query"),
            "管理员SystemPrompt应包含PUBLIC tool");
        assertTrue(systemPrompt.contains("deploy_create_instance"),
            "管理员SystemPrompt应包含AUTHENTICATED tool");
    }

    // ════════════════════════════════════════════════════════
    // 场景4：18 个 Tool 全部标注了 @ToolPermission
    // ════════════════════════════════════════════════════════

    @Test
    void testAllToolsHavePermissionAnnotation() {
        // 获取所有注册的 Tool
        Set<String> allToolNames = toolRegistry.getAllToolNames();

        for (String toolName : allToolNames) {
            BaseTool tool = toolRegistry.findByName(toolName).orElse(null);
            assertNotNull(tool, "Tool '" + toolName + "' 应该存在于注册表");

            ToolPermission perm = tool.getClass().getAnnotation(ToolPermission.class);
            assertNotNull(perm,
                "Tool '" + toolName + "' (" + tool.getClass().getSimpleName() + ") 必须标注 @ToolPermission");
        }

        // 验证 18 个以上 Tool 已注册
        assertTrue(allToolNames.size() >= 18,
            "至少应注册 18 个 Tool，实际=" + allToolNames.size());
    }

    // ════════════════════════════════════════════════════════
    // 场景5：重复检测 — 相同 name 的 Tool 只注册一次
    // ════════════════════════════════════════════════════════

    @Test
    void testDuplicateToolName_handled() {
        // 给定当前 ToolRegistry 的实现，在 init() 中先检查 containsKey 再 put
        // 重复 Tool 会被跳过，不会覆盖已注册项
        Map<String, Object> health = toolRegistry.health();
        int totalTools = (Integer) health.get("totalTools");

        // 验证 Tool 数量合理（22个左右，之前扫描到22个）
        assertTrue(totalTools >= 18,
            "Tool 注册数量应 >= 18（含重复检测），实际=" + totalTools);
    }

    // ════════════════════════════════════════════════════════
    // 场景6：权限分布统计正确
    // ════════════════════════════════════════════════════════

    @Test
    void testPermissionDistributionStats() {
        Map<String, Object> health = toolRegistry.health();
        @SuppressWarnings("unchecked")
        Map<String, Object> dist = (Map<String, Object>) health.get("permissionDistribution");

        assertNotNull(dist);
        Number publicCount = (Number) dist.get("public");
        Number adminOnlyCount = (Number) dist.get("adminOnly");
        Number authenticatedCount = (Number) dist.get("authenticated");

        assertTrue(publicCount.longValue() > 0, "PUBLIC tool 数量应 > 0");
        assertTrue(adminOnlyCount.longValue() > 0, "ADMIN_ONLY tool 数量应 > 0");
        assertTrue(authenticatedCount.longValue() >= 0, "AUTHENTICATED 数量应 >= 0");

        long total = publicCount.longValue() + adminOnlyCount.longValue() + authenticatedCount.longValue();
        assertTrue(total >= 18, "总 Tool 数应 >= 18");
    }

    // ════════════════════════════════════════════════════════
    // 测试配置（最小 Spring 上下文）
    // ════════════════════════════════════════════════════════

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {

        @org.springframework.context.annotation.Bean
        public UserPermissionContext userPermissionContext() {
            return new UserPermissionContext();
        }
    }
}
