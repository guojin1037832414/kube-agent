package com.atlas.http;

import com.atlas.auth.UserPermissionContext;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * M5.7 resolveOrgId 安全语义测试。
 *
 * <p>{@link KubeManagerHttpClient#resolveOrgId(String, String)} 只能返回已确认可信的组织 ID。
 * 当 username / token 缺失或 kube-manager 无法确认用户所属组织时，必须 fail-closed，
 * 不能返回默认组织、不能使用 sysadmin/fallback token 洗白，也不能缓存 fallback 结果。</p>
 */
class KubeManagerHttpClientResolveOrgIdSecurityTest {

    @Test
    void resolveOrgId_shouldRejectBlankUsernameInsteadOfReturningFallbackOrgId() {
        KubeManagerHttpClient client = new KubeManagerHttpClient(new UserPermissionContext());

        OrgIdResolutionException ex = assertThrows(OrgIdResolutionException.class,
            () -> client.resolveOrgId(" ", "user-token"));

        assertEquals(OrgIdResolutionException.Reason.USERNAME_EMPTY, ex.getReason());
    }

    @Test
    void resolveOrgId_shouldRejectMissingAuthTokenInsteadOfUsingFallbackToken() {
        KubeManagerHttpClient client = new KubeManagerHttpClient(new UserPermissionContext());

        OrgIdResolutionException ex = assertThrows(OrgIdResolutionException.class,
            () -> client.resolveOrgId("alice", null));

        assertEquals(OrgIdResolutionException.Reason.TOKEN_UNAVAILABLE, ex.getReason());
    }

    @Test
    void resolveOrgId_shouldRequireTokenEvenForSysadminMarker() {
        KubeManagerHttpClient client = new KubeManagerHttpClient(new UserPermissionContext());

        OrgIdResolutionException ex = assertThrows(OrgIdResolutionException.class,
            () -> client.resolveOrgId("sysadmin", " "));

        assertEquals(OrgIdResolutionException.Reason.TOKEN_UNAVAILABLE, ex.getReason());
    }

    @Test
    void resolveOrgId_shouldReturnSysadminMarkerOnlyAfterTokenExists() {
        KubeManagerHttpClient client = new KubeManagerHttpClient(new UserPermissionContext());

        assertEquals("sysadmin", client.resolveOrgId("sysadmin", "login-token"));
    }

    @Test
    void resolveOrgId_shouldFailClosedAndNotCacheFallbackWhenUserNotFound() {
        UserPermissionContext context = new UserPermissionContext();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "restClient", builder.build());

        // 当前已知桶式搜索组织全部返回空列表时，必须 fail-closed。
        for (String orgId : knownOrgIds()) {
            server.expect(request -> assertEquals("/api/" + orgId + "/user", request.getURI().getPath()))
                .andRespond(withSuccess("{\"success\":true,\"result\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));
        }

        OrgIdResolutionException ex = assertThrows(OrgIdResolutionException.class,
            () -> client.resolveOrgId("alice", "user-token"));

        assertEquals(OrgIdResolutionException.Reason.USER_NOT_FOUND, ex.getReason());
        server.verify();
    }

    @Test
    void resolveOrgId_shouldRejectInvalidResolvedOrgIdAndStopSearchingImmediately() {
        UserPermissionContext context = new UserPermissionContext();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "restClient", builder.build());

        server.expect(request -> assertEquals("/api/100001/user", request.getURI().getPath()))
            .andRespond(withSuccess("{\"success\":true,\"result\":[{\"username\":\"alice\",\"organizationId\":\"1\"}]}",
                org.springframework.http.MediaType.APPLICATION_JSON));

        OrgIdResolutionException ex = assertThrows(OrgIdResolutionException.class,
            () -> client.resolveOrgId("alice", "user-token"));

        assertEquals(OrgIdResolutionException.Reason.INVALID_RESOLVED_ORG_ID, ex.getReason());
        // 只允许请求第一个桶；一旦命中用户但 orgId 不可信，必须立即 fail-safe，不能继续搜索其他桶洗白。
        server.verify();
    }

    @Test
    void resolveOrgId_shouldReturnTrustedOrgIdResolvedByCurrentToken() {
        UserPermissionContext context = new UserPermissionContext();
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        KubeManagerHttpClient client = new KubeManagerHttpClient(context);
        ReflectionTestUtils.setField(client, "restClient", builder.build());

        server.expect(request -> assertEquals("/api/100001/user", request.getURI().getPath()))
            .andRespond(withSuccess("{\"success\":true,\"result\":[]}", org.springframework.http.MediaType.APPLICATION_JSON));
        server.expect(request -> assertEquals("/api/100002/user", request.getURI().getPath()))
            .andRespond(withSuccess("{\"success\":true,\"result\":[{\"username\":\"alice\",\"organizationId\":\"100002\"}]}",
                org.springframework.http.MediaType.APPLICATION_JSON));

        assertEquals("100002", client.resolveOrgId("alice", "user-token"));
        server.verify();
    }

    private List<String> knownOrgIds() {
        return List.of("100001", "100002", "100003", "100050", "100051", "100057", "100061", "100062");
    }
}
