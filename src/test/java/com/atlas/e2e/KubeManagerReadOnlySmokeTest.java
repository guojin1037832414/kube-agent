package com.atlas.e2e;

import com.atlas.auth.UserPermissionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.observability.AgentTraceContext;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.impl.NodeQueryTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * kube-manager 8100 只读链路 smoke。
 *
 * <p>中文说明：这是给本地真实联调用的 opt-in 测试。默认情况下不会访问 kube-manager；
 * 只有显式设置 {@code -Datlas.kube-manager.smoke.enabled=true} 或环境变量
 * {@code ATLAS_KUBE_MANAGER_SMOKE_ENABLED=true} 时才会连到 8100。这样日常 CI 和普通单测
 * 不依赖用户本机服务，但需要现场验证 READ Tool 时有一条可重复入口。测试支持两种身份来源：
 * 一是直接传入当前用户 token/orgId；二是传入 username/password，由测试在进程内登录 kube-manager、
 * 调用生产同款 {@link KubeManagerHttpClient#resolveOrgId(String, String)} 解析可信 orgId 后再执行只读 Tool。</p>
 *
 * <p>安全边界：本测试只绑定 {@link NodeQueryTool}，该 Tool 的注解必须保持 GET + READ +
 * no-confirmation。测试会传入伪造的 {@code organizationId/token} 参数来证明 Tool 仍应使用
 * {@link UserPermissionContext} 中的服务端可信 orgId/token；禁止在这里接入 POST/PUT/DELETE、
 * kube-manager 写入、MCP runtime、HITL 触发、audit 写入、Memory/RAG 写入或二期
 * NIM/HPC/Slurm/BCM 专项能力。唯一允许的 POST 是认证 bootstrap 的 {@code /api/login}，
 * 它只用于获得本次测试进程内的临时 token，不把密码/token 写入文件、日志、文档或 git。</p>
 *
 * <p>本地运行示例：</p>
 * <pre>{@code
 * mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" ^
 *   "-Datlas.kube-manager.smoke.enabled=true" ^
 *   "-Datlas.kube-manager.smoke.base-url=http://localhost:8100" ^
 *   "-Datlas.kube-manager.smoke.token=<当前用户token>" ^
 *   "-Datlas.kube-manager.smoke.org-id=<当前组织ID>" test
 *
 * set ATLAS_KUBE_MANAGER_SMOKE_USERNAME=<当前用户名>
 * set ATLAS_KUBE_MANAGER_SMOKE_PASSWORD=<当前密码>
 * mvn -q "-Dtest=KubeManagerReadOnlySmokeTest" ^
 *   "-Datlas.kube-manager.smoke.enabled=true" ^
 *   "-Datlas.kube-manager.smoke.base-url=http://localhost:8100" test
 * }</pre>
 */
class KubeManagerReadOnlySmokeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Path SELF = Path.of("src/test/java/com/atlas/e2e/KubeManagerReadOnlySmokeTest.java");

    private static final String ENABLED_PROPERTY = "atlas.kube-manager.smoke.enabled";
    private static final String BASE_URL_PROPERTY = "atlas.kube-manager.smoke.base-url";
    private static final String TOKEN_PROPERTY = "atlas.kube-manager.smoke.token";
    private static final String ORG_ID_PROPERTY = "atlas.kube-manager.smoke.org-id";
    private static final String USERNAME_PROPERTY = "atlas.kube-manager.smoke.username";
    private static final String PASSWORD_PROPERTY = "atlas.kube-manager.smoke.password";
    private static final String LOGIN_ORG_ID_PROPERTY = "atlas.kube-manager.smoke.login-organization-id";
    private static final String LOGIN_TYPE_PROPERTY = "atlas.kube-manager.smoke.login-type";

    private static final String ENABLED_ENV = "ATLAS_KUBE_MANAGER_SMOKE_ENABLED";
    private static final String BASE_URL_ENV = "ATLAS_KUBE_MANAGER_SMOKE_BASE_URL";
    private static final String TOKEN_ENV = "ATLAS_KUBE_MANAGER_SMOKE_TOKEN";
    private static final String ORG_ID_ENV = "ATLAS_KUBE_MANAGER_SMOKE_ORG_ID";
    private static final String USERNAME_ENV = "ATLAS_KUBE_MANAGER_SMOKE_USERNAME";
    private static final String PASSWORD_ENV = "ATLAS_KUBE_MANAGER_SMOKE_PASSWORD";
    private static final String LOGIN_ORG_ID_ENV = "ATLAS_KUBE_MANAGER_SMOKE_LOGIN_ORGANIZATION_ID";
    private static final String LOGIN_TYPE_ENV = "ATLAS_KUBE_MANAGER_SMOKE_LOGIN_TYPE";

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
    void smokeSource_shouldNotCallBusinessWriteHttpMethodsOrPhase2Tools() throws IOException {
        String source = Files.readString(SELF, StandardCharsets.UTF_8);

        // 中文说明：这是源码级安全护栏，防止未来把“真实 8100 smoke”顺手改成写接口联调。
        // 安全边界：8100 smoke 的存在价值是验证 READ Tool 的 token/orgId/query/path 传播，
        // 不是发布写能力、触发 HITL、联调 MCP runtime，或提前恢复 NIM/HPC/Slurm/BCM 二期域。
        // 唯一允许的 POST 是 /api/login 认证 bootstrap；业务链路仍只能走 NodeQueryTool 的 GET。
        assertEquals(1, countOccurrences(source, "." + "post("),
            "8100 smoke 只允许一个认证 POST，用于 /api/login 获取临时 token，不能新增业务 POST");
        assertTrue(source.contains(".uri(\"/api/login\")"),
            "8100 smoke 的唯一 POST 必须固定在 kube-manager /api/login，不能指向业务写接口");

        String[] forbiddenRuntimeTokens = {
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

        String baseUrl = config(BASE_URL_PROPERTY, BASE_URL_ENV, "http://localhost:8100");
        UserPermissionContext context = new UserPermissionContext();
        KubeManagerHttpClient client = kubeManagerClient(baseUrl, context);
        SmokeIdentity identity = resolveSmokeIdentity(baseUrl, client);

        assertFalse(identity.token().isBlank(), "真实 smoke 必须提供当前用户 token，不能使用 sysadmin fallback");
        assertFalse(identity.orgId().isBlank(), "真实 smoke 必须提供当前用户可信 orgId");

        context.bind(identity.token(), identity.orgId());

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

    private static SmokeIdentity resolveSmokeIdentity(String baseUrl, KubeManagerHttpClient client) {
        String token = config(TOKEN_PROPERTY, TOKEN_ENV, "");
        String orgId = config(ORG_ID_PROPERTY, ORG_ID_ENV, "");
        boolean tokenProvided = !token.isBlank();
        boolean orgIdProvided = !orgId.isBlank();

        // 中文说明：token/orgId 必须作为一组可信上下文出现。只提供其中一个时不自动补齐，
        // 避免把旧 token、手填 orgId 或登录态混搭成一个看似可用但不可审计的身份。
        assertEquals(tokenProvided, orgIdProvided,
            "真实 smoke 的 token/orgId 必须成对提供；若想自动登录解析 orgId，请改用 username/password 模式");
        if (tokenProvided) {
            return new SmokeIdentity(token, orgId);
        }

        String username = requiredConfig(USERNAME_PROPERTY, USERNAME_ENV);
        String password = requiredSecretConfig(PASSWORD_PROPERTY, PASSWORD_ENV);
        String loginOrganizationId = config(LOGIN_ORG_ID_PROPERTY, LOGIN_ORG_ID_ENV, "1");
        String loginType = config(LOGIN_TYPE_PROPERTY, LOGIN_TYPE_ENV, "local_login");

        String loginToken = loginForToken(baseUrl, username, password, loginOrganizationId, loginType);
        String resolvedOrgId = client.resolveOrgId(username, loginToken);
        assertFalse(resolvedOrgId.isBlank(), "登录型 smoke 必须通过 kube-manager 用户列表解析出可信 orgId");
        assertFalse("1".equals(resolvedOrgId), "登录型 smoke 不能把登录表单 organizationId=1 当成可信租户");
        return new SmokeIdentity(loginToken, resolvedOrgId);
    }

    private static KubeManagerHttpClient kubeManagerClient(String baseUrl, UserPermissionContext context) {
        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "backendBaseUrl", baseUrl);
        ReflectionTestUtils.setField(client, "connectTimeoutSeconds", 2);
        ReflectionTestUtils.setField(client, "readTimeoutSeconds", 8);
        client.init();
        return client;
    }

    private static String loginForToken(String baseUrl,
                                        String username,
                                        String password,
                                        String loginOrganizationId,
                                        String loginType) {
        // 中文说明：loginOrganizationId 只是 kube-manager 登录接口要求的表单字段，
        // 不是 kube-agent 的可信租户来源；可信 orgId 必须在登录成功后用当前 token 反查 /api/{orgId}/user 得到。
        String formBody = "username=" + encodeFormValue(username)
            + "&password=" + encodeFormValue(password)
            + "&organizationId=" + encodeFormValue(loginOrganizationId)
            + "&loginType=" + encodeFormValue(loginType);

        try {
            String body = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build()
                .post()
                .uri("/api/login")
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .body(formBody)
                .retrieve()
                .body(String.class);
            return extractToken(parseJsonObject(body));
        } catch (RestClientResponseException e) {
            fail("kube-manager 登录失败，HTTP status=" + e.getStatusCode().value() + "；测试不会回显响应体、密码或 token");
            return "";
        }
    }

    private static Map<String, Object> parseJsonObject(String body) {
        try {
            Map<String, Object> response = OBJECT_MAPPER.readValue(body, new TypeReference<>() {
            });
            assertEquals(Boolean.TRUE, response.get("success"),
                "kube-manager 登录响应 success 必须为 true；测试不会回显响应体、密码或 token");
            return response;
        } catch (IOException e) {
            fail("kube-manager 登录响应必须是 JSON object；测试不会回显响应体、密码或 token");
            return Map.of();
        }
    }

    private static String extractToken(Map<String, Object> response) {
        Object token = response.get("result");
        if (token instanceof Map<?, ?> resultMap) {
            token = resultMap.get("token");
        }
        if (token == null) {
            token = response.get("token");
        }
        if (token == null && response.get("data") instanceof Map<?, ?> dataMap) {
            token = dataMap.get("token");
        }
        if (token == null) {
            token = response.get("data");
        }

        String tokenValue = token == null ? "" : String.valueOf(token).trim();
        assertFalse(tokenValue.isBlank(),
            () -> "kube-manager 登录成功但未返回 token；响应 keys=" + response.keySet() + "，测试不会回显响应体或凭据");
        return tokenValue;
    }

    private static String encodeFormValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static int countOccurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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

    private static String requiredSecretConfig(String property, String env) {
        String value = secretConfig(property, env);
        assertFalse(value.isBlank(),
            "启用登录型 8100 smoke 时必须提供 " + property + " 或环境变量 " + env + "；测试不会回显该值");
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

    private static String secretConfig(String property, String env) {
        String propertyValue = System.getProperty(property);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue;
        }
        String envValue = System.getenv(env);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return "";
    }

    private record SmokeIdentity(String token, String orgId) {
    }
}
