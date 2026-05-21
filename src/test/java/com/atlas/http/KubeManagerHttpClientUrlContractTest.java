package com.atlas.http;

import com.atlas.auth.UserPermissionContext;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * KubeManagerHttpClient URL 查询参数契约测试。
 *
 * <p>历史上批量 Tool 接口对接时，最容易出现把 {@code ?page=1&limit=100}
 * 手工拼进 path 后再二次编码的问题，最终变成 {@code %253F} 或把 query 当成路径一部分。
 * 本测试直接锁定公共 {@link KubeManagerHttpClient#get(String, Map)} 行为：</p>
 * <ol>
 *   <li>调用方只传纯 path；</li>
 *   <li>query 参数统一交给 {@code queryParams} Map；</li>
 *   <li>最终请求 URL 必须只有一次标准编码。</li>
 * </ol>
 */
class KubeManagerHttpClientUrlContractTest {

    @Test
    void get_shouldAppendQueryParamsExactlyOnceWithoutEncodingQuestionMarkAsPath() {
        UserPermissionContext context = new UserPermissionContext();
        context.bind("test-token", "100002");
        try {
            RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://kube-manager.test")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json");
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            KubeManagerHttpClient client = new KubeManagerHttpClient(context);
            ReflectionTestUtils.setField(client, "restClient", builder.build());

            server.expect(request -> {
                    assertEquals("http", request.getURI().getScheme());
                    assertEquals("kube-manager.test", request.getURI().getHost());
                    assertEquals("/api/100002/mpi-job", request.getURI().getPath(), "query 不能被编码进 path，例如 /mpi-job%253Fpage=1");
                })
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("limit", "100"))
                .andExpect(request -> {
                    String rawQuery = request.getURI().getRawQuery();
                    assertNotNull(rawQuery);
                    assertTrue(rawQuery.contains("keyword=redis%20chart"), "空格应只编码一次为 %20");
                    assertFalse(rawQuery.contains("redis%2520chart"), "禁止二次编码为 %2520");
                })
                .andExpect(header("X-Token", "test-token"))
                .andRespond(withSuccess("{\"success\":true,\"data\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));

            Map<String, Object> result = client.get("/api/100002/mpi-job", Map.of(
                "page", "1",
                "limit", "100",
                "keyword", "redis chart"
            ));

            assertEquals(true, result.get("success"));
            server.verify();
        } finally {
            context.unbind();
        }
    }
}
