package com.atlas.http;

import com.atlas.auth.UserPermissionContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * M5.8 业务 HTTP 请求 Token fallback 安全契约测试。
 *
 * <p>安全背景：{@link KubeManagerHttpClient} 内部仍保留 sysadmin fallback token，
 * 以便未来显式系统任务使用。但 Agent Tool 发起的业务 GET/POST/DELETE 必须继承
 * 当前登录用户的 ThreadLocal Token；一旦缺失用户 Token，就必须 fail-closed，禁止
 * 自动降级为 sysadmin 统一 Token，否则会把“兼容能力”变成权限放大器。</p>
 */
class KubeManagerHttpClientTokenFallbackSecurityTest {

    /** 业务请求缺失用户 Token 时的统一安全错误片段。 */
    private static final String FALLBACK_REJECTED_MESSAGE = "拒绝使用 sysadmin 降级 Token";

    @Test
    void get_shouldFailClosedBeforeHttpRequestWhenUserTokenMissing() {
        UserPermissionContext context = new UserPermissionContext();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "restClient", builder.build());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> client.get("/api/test", Map.of("page", 1)));

        assertTrue(ex.getMessage().contains(FALLBACK_REJECTED_MESSAGE));
        server.verify();
    }

    @Test
    void post_shouldFailClosedBeforeHttpRequestWhenUserTokenMissing() {
        UserPermissionContext context = new UserPermissionContext();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "restClient", builder.build());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> client.post("/api/test", Map.of("name", "demo")));

        assertTrue(ex.getMessage().contains(FALLBACK_REJECTED_MESSAGE));
        server.verify();
    }

    @Test
    void delete_shouldFailClosedBeforeHttpRequestWhenUserTokenMissing() {
        UserPermissionContext context = new UserPermissionContext();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "restClient", builder.build());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> client.delete("/api/test", Map.of()));

        assertTrue(ex.getMessage().contains(FALLBACK_REJECTED_MESSAGE));
        server.verify();
    }

    @Test
    void getPostDelete_shouldStillUseUserThreadLocalTokenWhenPresent() {
        UserPermissionContext context = new UserPermissionContext();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "restClient", builder.build());

        UserPermissionContext.CURRENT_TOKEN.set("user-token");
        try {
            server.expect(requestTo("http://kube-manager.test/api/test?page=1"))
                .andExpect(header("X-Token", "user-token"))
                .andRespond(withSuccess("{\"success\":true,\"result\":{}}", org.springframework.http.MediaType.APPLICATION_JSON));
            server.expect(requestTo("http://kube-manager.test/api/test"))
                .andExpect(header("X-Token", "user-token"))
                .andRespond(withSuccess("{\"success\":true,\"result\":{}}", org.springframework.http.MediaType.APPLICATION_JSON));
            server.expect(requestTo("http://kube-manager.test/api/test"))
                .andExpect(header("X-Token", "user-token"))
                .andRespond(withSuccess("{\"success\":true,\"result\":{}}", org.springframework.http.MediaType.APPLICATION_JSON));

            assertDoesNotThrow(() -> client.get("/api/test", Map.of("page", 1)));
            assertDoesNotThrow(() -> client.post("/api/test", Map.of("name", "demo")));
            assertDoesNotThrow(() -> client.delete("/api/test", Map.of()));
            server.verify();
        } finally {
            UserPermissionContext.CURRENT_TOKEN.remove();
        }
    }

    @Test
    void productionGetPostDeleteMethods_shouldNotCallFallbackCapableResolveToken() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/atlas/http/KubeManagerHttpClient.java"));

        assertTrue(methodBody(source, "get", "String path, Map<String, Object> queryParams")
            .contains("resolveUserTokenRequired(\"GET\", path)"));
        assertTrue(methodBody(source, "post", "String path, Map<String, Object> body")
            .contains("resolveUserTokenRequired(\"POST\", path)"));
        assertTrue(methodBody(source, "delete", "String path, Map<String, Object> queryParams")
            .contains("resolveUserTokenRequired(\"DELETE\", path)"));

        assertTrue(!methodBody(source, "get", "String path, Map<String, Object> queryParams").contains("resolveToken()"));
        assertTrue(!methodBody(source, "post", "String path, Map<String, Object> body").contains("resolveToken()"));
        assertTrue(!methodBody(source, "delete", "String path, Map<String, Object> queryParams").contains("resolveToken()"));
    }

    /**
     * 从源码中提取指定方法体，用于锁定“业务方法不得回调 fallback-capable resolveToken”的源码契约。
     * 这里不做 Java AST 解析，原因是测试目标很窄：只需确保三个公共业务入口的首层实现不退回旧方法。
     */
    private String methodBody(String source, String methodName, String parameterSignature) {
        Pattern pattern = Pattern.compile("public\\s+Map<String, Object>\\s+" + methodName
            + "\\s*\\(" + Pattern.quote(parameterSignature) + "\\)\\s*\\{");
        Matcher matcher = pattern.matcher(source);
        assertTrue(matcher.find(), "未找到方法: " + methodName + "(" + parameterSignature + ")");

        int bodyStart = matcher.end();
        int depth = 1;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i);
                }
            }
        }
        throw new AssertionError("方法体括号不完整: " + methodName);
    }
}
