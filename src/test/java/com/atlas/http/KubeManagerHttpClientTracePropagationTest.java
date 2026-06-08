package com.atlas.http;

import com.atlas.auth.UserPermissionContext;
import com.atlas.observability.AgentTraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * M5.24 kube-manager HTTP outlet trace 传播测试。
 *
 * <p>本测试不访问真实 8100，只用 MockRestServiceServer 锁住出口请求头契约：
 * Agent 内部 traceId 必须继续传播到 kube-manager 请求，后续审计、OpenTelemetry、
 * 前端回放和故障定位才能按同一条证据链聚合。</p>
 */
class KubeManagerHttpClientTracePropagationTest {

    private static final Path HTTP_CLIENT_SOURCE = Path.of("src/main/java/com/atlas/http/KubeManagerHttpClient.java");

    @AfterEach
    void clearContext() {
        UserPermissionContext.CURRENT_TOKEN.remove();
        UserPermissionContext.CURRENT_ORG_ID.remove();
        AgentTraceContext.clear();
    }

    @Test
    void get_shouldPropagateTraceHeadersFromCurrentAgentTraceContext() {
        UserPermissionContext context = new UserPermissionContext();
        context.bind("user-token", "100002");
        HttpClientFixture fixture = newFixture(context);

        try (AgentTraceContext.Scope ignored = AgentTraceContext.bind("trc_0123456789abcdef0123456789abcdef")) {
            fixture.server().expect(requestTo("http://kube-manager.test/api/100002/pod?page=1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Token", "user-token"))
                .andExpect(header("X-Trace-Id", "trc_0123456789abcdef0123456789abcdef"))
                .andExpect(request -> assertTrue(
                    request.getHeaders().getFirst("traceparent")
                        .matches("00-0123456789abcdef0123456789abcdef-[0-9a-f]{16}-01"),
                    "HTTP outlet 应生成 W3C traceparent 以便后续 OpenTelemetry 串联"))
                .andRespond(withSuccess("{\"success\":true,\"data\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));

            Map<String, Object> result = fixture.client().get("/api/100002/pod", Map.of("page", 1));

            assertEquals(true, result.get("success"));
            fixture.server().verify();
        } finally {
            context.unbind();
        }
    }

    @Test
    void post_shouldPropagateGeneratedTraceWhenNoOuterTraceExists() {
        UserPermissionContext context = new UserPermissionContext();
        context.bind("user-token", "100002");
        HttpClientFixture fixture = newFixture(context);

        try {
            fixture.server().expect(requestTo("http://kube-manager.test/api/100002/read-only-plan"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Token", "user-token"))
                .andExpect(request -> assertTrue(
                    request.getHeaders().getFirst("X-Trace-Id").matches("trc_[0-9a-f]{32}"),
                    "没有外层 trace 时，HTTP outlet 应服务端生成 traceId"))
                .andExpect(request -> assertTrue(
                    request.getHeaders().getFirst("traceparent").matches("00-[0-9a-f]{32}-[0-9a-f]{16}-01"),
                    "服务端生成的 traceId 应可转换为 W3C traceparent"))
                .andRespond(withSuccess("{\"success\":true,\"result\":{}}", org.springframework.http.MediaType.APPLICATION_JSON));

            Map<String, Object> result = fixture.client().post("/api/100002/read-only-plan", Map.of("name", "demo"));

            assertEquals(true, result.get("success"));
            fixture.server().verify();
        } finally {
            context.unbind();
        }
    }

    @Test
    void resolveOrgId_shouldPropagateTraceHeadersDuringBucketSearch() {
        UserPermissionContext context = new UserPermissionContext();
        HttpClientFixture fixture = newFixture(context);

        try (AgentTraceContext.Scope ignored = AgentTraceContext.bind("trc_abcdefabcdefabcdefabcdefabcdefab")) {
            fixture.server().expect(request -> assertEquals("/api/100001/user", request.getURI().getPath()))
                .andExpect(header("X-Token", "login-token"))
                .andExpect(header("X-Trace-Id", "trc_abcdefabcdefabcdefabcdefabcdefab"))
                .andExpect(request -> assertTrue(
                    request.getHeaders().getFirst("traceparent")
                        .matches("00-abcdefabcdefabcdefabcdefabcdefab-[0-9a-f]{16}-01")))
                .andRespond(withSuccess("{\"success\":true,\"result\":[{\"username\":\"alice\",\"organizationId\":\"100001\"}]}",
                    org.springframework.http.MediaType.APPLICATION_JSON));

            assertEquals("100001", fixture.client().resolveOrgId("alice", "login-token"));
            fixture.server().verify();
        }
    }

    @Test
    void get_shouldPropagateExternalNonW3cTraceIdWithoutForgingTraceparent() {
        UserPermissionContext context = new UserPermissionContext();
        context.bind("user-token", "100002");
        HttpClientFixture fixture = newFixture(context);

        try (AgentTraceContext.Scope ignored = AgentTraceContext.bind("gateway-trace-001")) {
            fixture.server().expect(requestTo("http://kube-manager.test/api/100002/pod"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Trace-Id", "gateway-trace-001"))
                .andExpect(request -> assertNull(request.getHeaders().getFirst("traceparent"),
                    "非 W3C 形态的外部 trace 只能作为 X-Trace-Id 传播，不能伪造 traceparent"))
                .andRespond(withSuccess("{\"success\":true,\"data\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));

            Map<String, Object> result = fixture.client().get("/api/100002/pod", Map.of());

            assertEquals(true, result.get("success"));
            fixture.server().verify();
        } finally {
            context.unbind();
        }
    }

    @Test
    void source_shouldRouteAllBusinessTokenHeadersThroughTraceHelper() throws IOException {
        String source = Files.readString(HTTP_CLIENT_SOURCE, StandardCharsets.UTF_8);

        assertThat(source)
            .as("M5.24: kube-manager 业务出口不能重新手写 X-Token，否则容易漏掉 X-Trace-Id / traceparent")
            .doesNotContain(".header(\"X-Token\"");
        assertThat(source)
            .contains(".headers(headers -> applyUserAndTraceHeaders(headers, token))")
            .contains(".headers(headers -> applyUserAndTraceHeaders(headers, authToken))");
        assertEquals(8, count(source, ".headers(headers -> applyUserAndTraceHeaders"),
            "当前 KubeManagerHttpClient 的 7 个公开业务 HTTP 分支加 resolveOrgId 都必须走 trace helper");
    }

    private long count(String source, String needle) {
        long total = 0;
        int index = source.indexOf(needle);
        while (index >= 0) {
            total++;
            index = source.indexOf(needle, index + needle.length());
        }
        return total;
    }

    private HttpClientFixture newFixture(UserPermissionContext context) {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://kube-manager.test")
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Content-Type", "application/json");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "restClient", builder.build());
        return new HttpClientFixture(client, server);
    }

    private record HttpClientFixture(KubeManagerHttpClient client, MockRestServiceServer server) {
    }
}
