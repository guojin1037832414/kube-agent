package com.atlas.e2e;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.observability.AgentTraceContext;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.impl.NodeQueryTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * kube-manager 8100 只读链路 smoke。
 *
 * <p>中文说明：这是给本地真实联调用的 opt-in 测试。默认情况下不会访问 kube-manager；
 * 只有显式设置 {@code -Datlas.kube-manager.smoke.enabled=true} 或环境变量
 * {@code ATLAS_KUBE_MANAGER_SMOKE_ENABLED=true} 时才会连到 8100。这样日常 CI 和普通单测
 * 不依赖用户本机服务，但需要现场验证 READ Tool 时有一条可重复入口。</p>
 *
 * <p>安全边界：本测试只绑定 {@link NodeQueryTool}，该 Tool 的注解必须保持 GET + READ +
 * no-confirmation。测试会传入伪造的 {@code organizationId/token} 参数来证明 Tool 仍应使用
 * {@link UserPermissionContext} 中的服务端可信 orgId/token；禁止在这里接入 POST/PUT/DELETE、
 * kube-manager 写入、MCP runtime、HITL 触发、audit 写入、Memory/RAG 写入或二期
 * NIM/HPC/Slurm/BCM 专项能力。</p>
 *
 * <p>本地运行示例：</p>
 * <pre>{@code
 * mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" ^
 *   "-Datlas.kube-manager.smoke.enabled=true" ^
 *   "-Datlas.kube-manager.smoke.base-url=http://localhost:8100" ^
 *   "-Datlas.kube-manager.smoke.token=<当前用户token>" ^
 *   "-Datlas.kube-manager.smoke.org-id=<当前组织ID>" test
 * }</pre>
 */
class KubeManagerReadOnlySmokeTest {

    private static final Path SELF = Path.of("src/test/java/com/atlas/e2e/KubeManagerReadOnlySmokeTest.java");

    private static final String ENABLED_PROPERTY = "atlas.kube-manager.smoke.enabled";
    private static final String BASE_URL_PROPERTY = "atlas.kube-manager.smoke.base-url";
    private static final String TOKEN_PROPERTY = "atlas.kube-manager.smoke.token";
    private static final String ORG_ID_PROPERTY = "atlas.kube-manager.smoke.org-id";

    private static final String ENABLED_ENV = "ATLAS_KUBE_MANAGER_SMOKE_ENABLED";
    private static final String BASE_URL_ENV = "ATLAS_KUBE_MANAGER_SMOKE_BASE_URL";
    private static final String TOKEN_ENV = "ATLAS_KUBE_MANAGER_SMOKE_TOKEN";
    private static final String ORG_ID_ENV = "ATLAS_KUBE_MANAGER_SMOKE_ORG_ID";

    @AfterEach
    void clearThreadLocalContext() {
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
        AgentTraceContext.clear();
    }

    @Test
    void smokeTarget_shouldRemainPhase1ReadOnlyNodeQueryTool() {
        AtlasToolMapping mapping = NodeQueryTool.class.getAnnotation(AtlasToolMapping.class);

        // 中文说明：这个测试始终运行，用元数据锁住 smoke 的目标 Tool，防止未来误把写 Tool 或二期域 Tool 接进现场 smoke。
        assertNotNull(mapping, "NodeQueryTool 必须声明 AtlasToolMapping，smoke 才能审计 HTTP/风险元数据");
        assertEquals("GET", mapping.httpMethod(), "8100 smoke 只能调用 GET 只读接口");
        assertEquals(AtlasToolMapping.OperationType.READ, mapping.operationType(), "8100 smoke 只能绑定 READ Tool");
        assertFalse(mapping.requiresConfirmation(), "READ smoke 不应创建 HITL 确认流程");
        assertArrayEquals(new String[]{"/api/{orgId}/node"}, mapping.apiEndpoints(),
            "READ smoke 必须固定在节点查询接口，不能被扩展成写入或二期域联调");
    }

    @Test
    void smokeSource_shouldNotCallWriteHttpMethodsOrPhase2Tools() throws IOException {
        String source = Files.readString(SELF, StandardCharsets.UTF_8);

        // 中文说明：这是源码级安全护栏，防止未来把“真实 8100 smoke”顺手改成写接口联调。
        // 安全边界：8100 smoke 的存在价值是验证 READ Tool 的 token/orgId/query/path 传播，
        // 不是发布写能力、触发 HITL、联调 MCP runtime，或提前恢复 NIM/HPC/Slurm/BCM 二期域。
        String[] forbiddenRuntimeTokens = {
            "." + "post(",
            "." + "put(",
            "." + "patch(",
            "." + "delete(",
            "Nim" + "CreateTool",
            "Hpc" + "Environment",
            "Hpc" + "Job",
            "Slurm" + "Node",
            "Slurm" + "Cluster",
            "Bcm" + "User",
            "Bcm" + "Allocation"
        };
        for (String forbidden : forbiddenRuntimeTokens) {
            assertFalse(source.contains(forbidden),
                "8100 READ smoke 源码不能包含禁止的运行时调用或二期 Tool: " + forbidden);
        }
    }

    @Test
    void nodeQuery_shouldReachKubeManager8100WhenSmokeExplicitlyEnabled() {
        assumeTrue(smokeEnabled(),
            () -> "跳过真实 8100 smoke：设置 " + ENABLED_PROPERTY + "=true 或 " + ENABLED_ENV + "=true 后才访问 kube-manager");

        String token = requiredConfig(TOKEN_PROPERTY, TOKEN_ENV);
        String orgId = requiredConfig(ORG_ID_PROPERTY, ORG_ID_ENV);
        String baseUrl = config(BASE_URL_PROPERTY, BASE_URL_ENV, "http://localhost:8100");

        assertFalse(token.isBlank(), "真实 smoke 必须提供当前用户 token，不能使用 sysadmin fallback");
        assertFalse(orgId.isBlank(), "真实 smoke 必须提供当前用户可信 orgId");

        UserPermissionContext context = new UserPermissionContext();
        context.bind(token, orgId);

        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "backendBaseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "connectTimeoutSeconds", 2);
        ReflectionTestUtils.setField(client, "readTimeoutSeconds", 8);
        client.init();

        try (AgentTraceContext.Scope ignored = AgentTraceContext.bind("trc_8100readsmoke000000000000000000")) {
            NodeQueryTool tool = new NodeQueryTool(client);

            Map<String, Object> result = tool.execute(Map.of(
                "page", "1",
                "limit", "1",
                // 中文说明：伪造字段用于证明 Tool 不能信任参数里的控制面身份，只能使用 ThreadLocal 可信上下文。
                "organizationId", "forged-org",
                "orgId", "forged-org",
                "token", "forged-token"
            ));

            assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS),
                () -> "node_query READ smoke 失败，baseUrl=" + baseUrl + ", message=" + result.get(AtlasToolResult.KEY_MESSAGE));
            assertEquals("node_query", result.get(AtlasToolResult.KEY_TOOL_NAME));
            assertTrue(result.containsKey(AtlasToolResult.KEY_DATA),
                "成功结果必须保留 data 字段，供前端/LLM 后续展示或总结");
        }
    }

    private static boolean smokeEnabled() {
        return Boolean.parseBoolean(config(ENABLED_PROPERTY, ENABLED_ENV, "false"));
    }

    private static String requiredConfig(String property, String env) {
        String value = config(property, env, "");
        assertFalse(value.isBlank(),
            "启用真实 8100 smoke 时必须提供 " + property + " 或环境变量 " + env);
        return value;
    }

    private static String config(String property, String env, String defaultValue) {
        String propertyValue = System.getProperty(property);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }
        String envValue = System.getenv(env);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        return defaultValue;
    }
}
