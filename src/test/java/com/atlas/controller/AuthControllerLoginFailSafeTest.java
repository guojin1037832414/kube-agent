package com.atlas.controller;

import com.atlas.auth.UserPermissionContext;
import com.atlas.dto.LoginRequest;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.store.SessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * M5.7 登录组织上下文 fail-safe 测试。
 *
 * <p>登录成功拿到 token 但无法获得可信 orgId 时，不能继续创建 Session。否则 SessionStore 中的
 * organizationId 会被后续 Orchestrator/Tool 当成可信租户边界，导致默认组织被洗白。</p>
 */
class AuthControllerLoginFailSafeTest {

    @Test
    void login_shouldFailSafeWithoutCreatingSessionWhenOrgIdCannotBeResolved() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserPermissionContext userPermissionContext = mock(UserPermissionContext.class);
        SessionStore sessionStore = mock(SessionStore.class);
        KubeManagerHttpClient kubeManagerClient = mock(KubeManagerHttpClient.class);
        AuthController controller = new AuthController(
            "http://kube-manager.test",
            builder,
            userPermissionContext,
            sessionStore,
            new ObjectMapper(),
            kubeManagerClient
        );

        server.expect(requestTo("http://kube-manager.test/api/login"))
            .andRespond(withSuccess("{\"success\":true,\"result\":\"jwt-token-without-org\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        when(kubeManagerClient.resolveOrgId("alice", "jwt-token-without-org"))
            .thenThrow(new IllegalStateException("cannot resolve trusted orgId"));

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");

        ResponseEntity<?> response = controller.login(request);

        assertTrue(response.getStatusCode().isError(), "无法解析可信 orgId 时登录必须失败");
        verify(sessionStore, never()).createSession(anyString(), anyString(), anyString(), anyString(), any(Set.class));
        verify(userPermissionContext, never()).onLogin(anyString(), anyString(), anyString(), any(Set.class));
        verify(userPermissionContext, never()).onLogin(anyString(), anyString(), anyString(), any(Set.class), anyString());
        server.verify();
    }

    @Test
    void login_shouldNotTrustNonDefaultRequestOrgIdWhenLoginResponseHasNoOrgId() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserPermissionContext userPermissionContext = mock(UserPermissionContext.class);
        SessionStore sessionStore = mock(SessionStore.class);
        KubeManagerHttpClient kubeManagerClient = mock(KubeManagerHttpClient.class);
        AuthController controller = new AuthController(
            "http://kube-manager.test",
            builder,
            userPermissionContext,
            sessionStore,
            new ObjectMapper(),
            kubeManagerClient
        );

        server.expect(requestTo("http://kube-manager.test/api/login"))
            .andRespond(withSuccess("{\"success\":true,\"result\":\"jwt-token-without-org\"}", org.springframework.http.MediaType.APPLICATION_JSON));
        when(kubeManagerClient.resolveOrgId("alice", "jwt-token-without-org")).thenReturn("100002");
        when(sessionStore.createSession("jwt-token-without-org", "alice", "100002", "user", Set.of()))
            .thenReturn("ses_safe");

        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");
        request.setOrganizationId("999999");

        ResponseEntity<?> response = controller.login(request);

        assertTrue(response.getStatusCode().is2xxSuccessful(), "反查到可信 orgId 后应允许登录");
        assertEquals("100002", ((com.atlas.dto.LoginResponse) response.getBody()).getOrganizationId(),
            "返回给前端的组织上下文必须来自 token 反查，而不是请求体里的 organizationId");
        verify(kubeManagerClient).resolveOrgId("alice", "jwt-token-without-org");
        verify(userPermissionContext).onLogin("jwt-token-without-org", "alice", "user", Set.of(), "100002");
        verify(sessionStore).createSession("jwt-token-without-org", "alice", "100002", "user", Set.of());
        server.verify();
    }

    @Test
    void login_shouldExtractTokenAndTrustedOrgIdFromObjectResult() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://kube-manager.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UserPermissionContext userPermissionContext = mock(UserPermissionContext.class);
        SessionStore sessionStore = mock(SessionStore.class);
        KubeManagerHttpClient kubeManagerClient = mock(KubeManagerHttpClient.class);
        AuthController controller = new AuthController(
            "http://kube-manager.test",
            builder,
            userPermissionContext,
            sessionStore,
            new ObjectMapper(),
            kubeManagerClient
        );

        server.expect(requestTo("http://kube-manager.test/api/login"))
            .andRespond(withSuccess(
                "{\"success\":true,\"result\":{\"token\":\"jwt-token-from-object\",\"organizationId\":\"100003\"}}",
                org.springframework.http.MediaType.APPLICATION_JSON));
        when(sessionStore.createSession("jwt-token-from-object", "bob", "100003", "user", Set.of()))
            .thenReturn("ses_object");

        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("secret");
        request.setOrganizationId("999999");

        ResponseEntity<?> response = controller.login(request);

        assertTrue(response.getStatusCode().is2xxSuccessful(), "对象型 result 同时带 token/orgId 时应直接创建会话");
        verify(kubeManagerClient, never()).resolveOrgId(anyString(), anyString());
        verify(userPermissionContext).onLogin("jwt-token-from-object", "bob", "user", Set.of(), "100003");
        verify(sessionStore).createSession("jwt-token-from-object", "bob", "100003", "user", Set.of());
        server.verify();
    }
}
