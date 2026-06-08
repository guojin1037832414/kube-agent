package com.atlas.http;

import com.atlas.auth.UserPermissionContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * M5.28 kube-manager HTTP 出口韧性治理契约。
 *
 * <p>读请求可以自动重试，写请求在没有 idempotency key / durable audit / HITL release evidence 前不能自动重试。
 * 这条边界防止一次 Agent 写动作因为网络抖动被放大成多次真实后端副作用。</p>
 */
class KubeManagerHttpResiliencePolicyTest {

    private static final Path HTTP_CLIENT_SOURCE = Path.of("src/main/java/com/atlas/http/KubeManagerHttpClient.java");
    private static final Path RETRY_CONFIG_SOURCE = Path.of("src/main/java/com/atlas/http/HttpRetryConfig.java");

    @Test
    void get_shouldRetryTransientNetworkFailureThroughResilience4jReadPolicy() {
        UserPermissionContext context = new UserPermissionContext();
        context.bind("user-token", "100002");
        HttpClientFixture fixture = newFixture(context, 3);

        try {
            fixture.server().expect(requestTo("http://kube-manager.test/api/100002/pod?page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new IOException("temporary read outage")));
            fixture.server().expect(requestTo("http://kube-manager.test/api/100002/pod?page=1"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"success\":true,\"data\":[{\"name\":\"pod-a\"}]}",
                    org.springframework.http.MediaType.APPLICATION_JSON));

            Map<String, Object> result = fixture.client().get("/api/100002/pod", Map.of("page", 1));

            assertThat(result).containsEntry("success", true);
            fixture.server().verify();
        } finally {
            context.unbind();
        }
    }

    @Test
    void post_shouldNotRetryTransientNetworkFailureBeforeIdempotencyAuditAndHitlAreAvailable() {
        UserPermissionContext context = new UserPermissionContext();
        context.bind("user-token", "100002");
        HttpClientFixture fixture = newFixture(context, 3);

        try {
            fixture.server().expect(requestTo("http://kube-manager.test/api/100002/deployment"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withException(new IOException("write response lost")));

            assertThatThrownBy(() -> fixture.client().post("/api/100002/deployment", Map.of("name", "demo")))
                .isInstanceOf(ResourceAccessException.class)
                .hasMessageContaining("write response lost");

            fixture.server().verify();
        } finally {
            context.unbind();
        }
    }

    @Test
    void source_shouldUseResilience4jPolicyAndKeepSpringRetryOutOfBusinessHttpClient() throws Exception {
        String source = Files.readString(HTTP_CLIENT_SOURCE, StandardCharsets.UTF_8);

        assertThat(source)
            .doesNotContain("@Retryable")
            .doesNotContain("@Recover")
            .doesNotContain("org.springframework.retry");
        assertThat(source)
            .contains("resiliencePolicy.executeRead(() -> restClient.get()")
            .contains("resiliencePolicy.executeWrite(() -> restClient.post()")
            .contains("resiliencePolicy.executeWrite(() -> restClient.patch()")
            .contains("resiliencePolicy.executeWrite(() -> restClient.put()")
            .contains("resiliencePolicy.executeWrite(() -> restClient.delete()");
        assertThat(Files.exists(RETRY_CONFIG_SOURCE))
            .as("Spring Retry 注解载体已经下线，真实治理应由 Resilience4j policy 承担")
            .isFalse();
    }

    @Test
    void client_shouldPreferResilienceAwareConstructorInSpringContext() throws Exception {
        Constructor<KubeManagerHttpClient> constructor = KubeManagerHttpClient.class.getConstructor(
            UserPermissionContext.class,
            KubeManagerHttpResiliencePolicy.class
        );

        assertThat(constructor.getAnnotation(Autowired.class)).isNotNull();
    }

    @Test
    void policy_shouldPreferRegistryConstructorInSpringContext() throws Exception {
        Constructor<KubeManagerHttpResiliencePolicy> constructor =
            KubeManagerHttpResiliencePolicy.class.getConstructor(
                RetryRegistry.class,
                CircuitBreakerRegistry.class,
                BulkheadRegistry.class
            );

        assertThat(constructor.getAnnotation(Autowired.class)).isNotNull();
    }

    private HttpClientFixture newFixture(UserPermissionContext context, int readRetryAttempts) {
        RestClient.Builder builder = RestClient.builder()
            .baseUrl("http://kube-manager.test")
            .defaultHeader("Accept", "application/json")
            .defaultHeader("Content-Type", "application/json");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KubeManagerHttpClient client = new KubeManagerHttpClient(
            context,
            TestResilienceFactory.policy(readRetryAttempts)
        );
        ReflectionTestUtils.setField(client, "restClient", builder.build());
        return new HttpClientFixture(client, server);
    }

    private record HttpClientFixture(KubeManagerHttpClient client, MockRestServiceServer server) {
    }
}
