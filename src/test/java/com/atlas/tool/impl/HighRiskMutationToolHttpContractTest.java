package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 高风险写操作 Tool 的 HTTP 契约测试。
 *
 * <p>HITL 审批前必须能明确展示“将要执行的真实动作”。因此这里不用真实 kube-manager，
 * 只用 mock 锁住最终 HTTP 方法、路径、query/body，防止 Tool 文案成功但实际接口漂移。</p>
 */
class HighRiskMutationToolHttpContractTest {

    private KubeManagerHttpClient httpClient;

    @BeforeEach
    void setUpTrustedContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        httpClient = mock(KubeManagerHttpClient.class);
    }

    @AfterEach
    void tearDownTrustedContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
        UserPermissionContext.CURRENT_TOKEN.remove();
    }

    @Test
    void deployDelete_shouldUseReviewedDeleteEndpointAndQueryParam() {
        when(httpClient.delete(eq("/api/100001/deployment"), eq(Map.of("name", "demo-app"))))
            .thenReturn(Map.of("result", Map.of("deleted", true)));

        Map<String, Object> result = new DeployDeleteTool(httpClient)
            .execute(Map.of("name", "demo-app", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).delete(eq("/api/100001/deployment"), eq(Map.of("name", "demo-app")));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void deployScale_shouldUsePatchEndpointWithNameAndReplicasBody() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("name", "demo-app");
        expectedBody.put("replicas", 3);
        when(httpClient.patch(eq("/api/100001/deployment/scale"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of()));

        Map<String, Object> result = new DeployScaleTool(httpClient)
            .execute(Map.of("name", "demo-app", "targetReplicas", "3", "orgId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).patch(eq("/api/100001/deployment/scale"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void mpiJobSubmit_shouldUseReviewedPathVariableEndpointWithoutBodyPayload() {
        when(httpClient.post(eq("/api/100001/mpi-job/submit/42"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("submitted", true)));

        Map<String, Object> result = new MpiJobSubmitTool(httpClient)
            .execute(Map.of("id", "42", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/mpi-job/submit/42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void pytorchJobSubmit_shouldUseReviewedPathVariableEndpointWithoutBodyPayload() {
        when(httpClient.post(eq("/api/100001/pytorch-job/submit/42"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("submitted", true)));

        Map<String, Object> result = new PytorchJobSubmitTool(httpClient)
            .execute(Map.of("id", "42", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/pytorch-job/submit/42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void mpiJobAbort_shouldUseReviewedStopEndpointAndNotLegacyAbortPath() {
        when(httpClient.post(eq("/api/100001/mpi-job/42"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("stopped", true)));

        Map<String, Object> result = new MpiJobAbortTool(httpClient)
            .execute(Map.of("id", "42", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/mpi-job/42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void deployRestart_shouldFailClosedWhenBackendHasNoReviewedRestartApi() {
        Map<String, Object> result = new DeployRestartTool(httpClient)
            .execute(Map.of("name", "demo-app", "organizationId", "100002"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("UNSUPPORTED_BACKEND_OPERATION", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void composeDeployCreate_shouldUseReviewedDeployEndpointAndComposeDeployDtoShape() {
        Map<String, Object> resource = Map.of("cpuLimits", 500, "memLimits", 1024);
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("contentYaml", "services:\n  web:\n    image: nginx:1.25");
        expectedBody.put("composeName", "nginx-compose");
        expectedBody.put("resourceList", List.of(resource));
        expectedBody.put("sizeList", List.of(10));
        when(httpClient.post(eq("/api/100001/compose/deploy"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("composeId", 7)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "nginx-compose");
        params.put("yaml", "services:\n  web:\n    image: nginx:1.25");
        params.put("resourceList", List.of(resource));
        params.put("sizeList", List.of(10));
        params.put("organizationId", "100002");
        params.put("token", "forged");
        params.put("approved", true);

        Map<String, Object> result = new ComposeDeployCreateTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/compose/deploy"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void composeDeployCreate_shouldFailClosedWhenRequiredDtoFieldsMissing() {
        Map<String, Object> result = new ComposeDeployCreateTool(httpClient)
            .execute(Map.of("name", "nginx-compose"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("MISSING_COMPOSE_DEPLOY_PARAMS", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void helmRepoAdd_shouldUseReviewedRepositoriesEndpointAndFilterProtectedContext() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("name", "bitnami");
        expectedBody.put("url", "https://charts.bitnami.com/bitnami");
        when(httpClient.post(eq("/api/100001/helm/repositories"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("added", true)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "bitnami");
        params.put("url", "https://charts.bitnami.com/bitnami");
        params.put("organizationId", "100002");
        params.put("token", "forged");
        params.put("approved", true);

        Map<String, Object> result = new HelmRepoAddTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/helm/repositories"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void helmReleaseDelete_shouldUseReviewedDeleteEndpointWithoutBodyPayload() {
        when(httpClient.delete(eq("/api/100001/helm/releases/nginx"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("deleted", true)));

        Map<String, Object> result = new HelmReleaseDeleteTool(httpClient)
            .execute(Map.of("releaseName", "nginx", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).delete(eq("/api/100001/helm/releases/nginx"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void helmReleaseInstall_shouldUseReviewedPathQueryAndInstallDtoWhitelist() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("wait", true);
        expectedBody.put("atomic", true);
        expectedBody.put("version", "1.2.3");
        expectedBody.put("set", List.of("image.tag=1.2.3"));
        when(httpClient.post(
            eq("/api/100001/helm/releases/redis"),
            eq(Map.of("chart", "bitnami/redis")),
            eq(expectedBody)
        )).thenReturn(Map.of("result", Map.of("installed", true)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("release", "redis");
        params.put("chart", "bitnami/redis");
        params.put("wait", true);
        params.put("atomic", true);
        params.put("version", "1.2.3");
        params.put("set", List.of("image.tag=1.2.3"));
        params.put("organizationId", "100002");
        params.put("token", "forged");
        params.put("approved", true);

        Map<String, Object> result = new HelmReleaseInstallTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/helm/releases/redis"), eq(Map.of("chart", "bitnami/redis")), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void helmReleaseUpgrade_shouldUseReviewedPathQueryAndUpgradeDtoWhitelist() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("wait", true);
        expectedBody.put("force", true);
        expectedBody.put("values", "https://example.test/values.yaml");
        when(httpClient.put(
            eq("/api/100001/helm/release/redis/upgrade"),
            eq(Map.of("chart", "bitnami/redis")),
            eq(expectedBody)
        )).thenReturn(Map.of("result", Map.of("upgraded", true)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("release", "redis");
        params.put("chart", "bitnami/redis");
        params.put("wait", true);
        params.put("force", true);
        params.put("values", "https://example.test/values.yaml");
        params.put("sessionId", "session-x");
        params.put("approved", true);

        Map<String, Object> result = new HelmReleaseUpgradeTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).put(eq("/api/100001/helm/release/redis/upgrade"), eq(Map.of("chart", "bitnami/redis")), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void helmReleaseRollback_shouldUseReviewedPathAndRollbackDtoWhitelist() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("wait", true);
        expectedBody.put("cleanup_on_fail", true);
        expectedBody.put("history_max", 5);
        when(httpClient.put(eq("/api/100001/helm/release/redis/rollback/2"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("rolledBack", true)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("release", "redis");
        params.put("version", "2");
        params.put("wait", true);
        params.put("cleanup_on_fail", true);
        params.put("history_max", 5);
        params.put("token", "forged");

        Map<String, Object> result = new HelmReleaseRollbackTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).put(eq("/api/100001/helm/release/redis/rollback/2"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void helmRepoUpdate_shouldUseReviewedAdminOnlyEndpointWithoutPayload() {
        when(httpClient.put(eq("/api/100001/helm/repositories"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("updated", true)));

        Map<String, Object> result = new HelmRepoUpdateTool(httpClient)
            .execute(Map.of("approved", true, "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).put(eq("/api/100001/helm/repositories"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void helmRepoRemove_shouldUseReviewedAdminOnlyDeleteEndpointWithoutPayload() {
        when(httpClient.delete(eq("/api/100001/helm/repositories/bitnami"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("removed", true)));

        Map<String, Object> result = new HelmRepoRemoveTool(httpClient)
            .execute(Map.of("repoName", "bitnami", "approved", true, "token", "forged"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).delete(eq("/api/100001/helm/repositories/bitnami"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void composeDeployDelete_shouldUseReviewedDeleteEndpointWithoutPayload() {
        when(httpClient.delete(eq("/api/100001/compose/77"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("deleted", true)));

        Map<String, Object> result = new ComposeDeployDeleteTool(httpClient)
            .execute(Map.of("composeId", "77", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).delete(eq("/api/100001/compose/77"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void composeDeployUpdate_shouldOnlySendComposeNameBecauseBackendOnlyRenames() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("composeName", "redis-stack");
        when(httpClient.put(eq("/api/100001/compose/77"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("updated", true)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("composeId", "77");
        params.put("composeName", "redis-stack");
        params.put("contentYaml", "should-not-be-forwarded");
        params.put("token", "forged");

        Map<String, Object> result = new ComposeDeployUpdateTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).put(eq("/api/100001/compose/77"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void distributedCreate_shouldSendSlurmDtoShapeAndFilterProtectedContext() {
        Map<String, Object> loginNode = Map.of("nodeName", "login-01", "privateIp", "10.0.0.1");
        List<Map<String, Object>> workNode = List.of(Map.of("nodeName", "work-01", "privateIp", "10.0.0.2"));
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("displayName", "train-slurm");
        expectedBody.put("clusterName", "train-slurm");
        expectedBody.put("slurmConfigTemplateId", 3);
        expectedBody.put("queues", List.of("defq"));
        expectedBody.put("loginNode", loginNode);
        expectedBody.put("workNode", workNode);
        expectedBody.put("userId", List.of(10000002));
        when(httpClient.post(eq("/api/100001/bcm/slurm-cluster"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("created", true)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("displayName", "train-slurm");
        params.put("clusterName", "train-slurm");
        params.put("slurmConfigTemplateId", 3);
        params.put("queues", List.of("defq"));
        params.put("assignedUserIds", List.of(10000002));
        params.put("loginNode", loginNode);
        params.put("workNode", workNode);
        params.put("organizationId", "100002");
        params.put("orgId", "100003");
        params.put("token", "forged");
        params.put("sessionId", "session-x");
        params.put("approved", true);

        Map<String, Object> result = new DistributedCreateTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/bcm/slurm-cluster"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void distributedCreate_shouldFailClosedWhenSlurmRequiredDtoFieldsMissing() {
        Map<String, Object> result = new DistributedCreateTool(httpClient)
            .execute(Map.of("name", "train-slurm"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("MISSING_SLURM_CREATE_PARAMS", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void nimCreate_shouldFailClosedUntilReviewedNimDeploymentOrchestrationExists() {
        Map<String, Object> result = new NimCreateTool(httpClient)
            .execute(Map.of("name", "llama-nim", "model", "llama"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("UNSUPPORTED_BACKEND_OPERATION", result.get(AtlasToolResult.KEY_ERROR_CODE));
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get(AtlasToolResult.KEY_DATA);
        @SuppressWarnings("unchecked")
        Map<String, Object> stateMachine = (Map<String, Object>) data.get("stateMachine");
        assertEquals("NIM_CREATE_WRITE_GUARD", stateMachine.get("stateMachine"));
        assertEquals(false, stateMachine.get("writePermitted"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void experimentStart_shouldUseReviewedPutPathVariableEndpointWithoutBodyPayload() {
        when(httpClient.put(eq("/api/100001/experiment/instance/start/42"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("started", true)));

        Map<String, Object> result = new ExperimentStartTool(httpClient)
            .execute(Map.of("id", "42", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).put(eq("/api/100001/experiment/instance/start/42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void experimentStop_shouldUseReviewedShutdownPutPathVariableEndpointWithoutBodyPayload() {
        when(httpClient.put(eq("/api/100001/experiment/instance/shutdown/42"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("shutdown", true)));

        Map<String, Object> result = new ExperimentInstanceStopTool(httpClient)
            .execute(Map.of("id", "42", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).put(eq("/api/100001/experiment/instance/shutdown/42"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void experimentDelete_shouldFailClosedWhenBackendHasNoReviewedDeleteApi() {
        Map<String, Object> result = new ExperimentInstanceDeleteTool(httpClient)
            .execute(Map.of("id", "42", "organizationId", "100002"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("UNSUPPORTED_BACKEND_OPERATION", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void imageDelete_shouldUseReviewedDeleteEndpointWithEntirelyQueryDefaultFalse() {
        when(httpClient.delete(eq("/api/100001/image/7"), eq(Map.of("entirely", false))))
            .thenReturn(Map.of("result", Map.of("deleted", true)));

        Map<String, Object> result = new ImageDeleteTool(httpClient)
            .execute(Map.of("id", "7", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).delete(eq("/api/100001/image/7"), eq(Map.of("entirely", false)));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void imagePull_shouldSendImageEntityBodyAndFilterProtectedContext() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("repoTag", "nvcr.io/nvidia/cuda:12.4.1-devel-ubuntu22.04");
        expectedBody.put("clientRepoTag", "nvcr.io/nvidia/cuda:12.4.1-devel-ubuntu22.04");
        expectedBody.put("description", "cuda base");
        expectedBody.put("registryAuthId", 12);
        expectedBody.put("scope", "ORGANIZATION");
        when(httpClient.post(eq("/api/100001/image/pull"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("status", "Pulling")));

        Map<String, Object> result = new ImagePullTool(httpClient).execute(Map.of(
            "imageName", "nvcr.io/nvidia/cuda:12.4.1-devel-ubuntu22.04",
            "description", "cuda base",
            "registryAuthId", 12,
            "scope", "ORGANIZATION",
            "organizationId", "100002",
            "userId", "999"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/image/pull"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void storageCreate_shouldSendStorageApplyDtoShapeAndFilterProtectedContext() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("areaCode", "shanghai");
        expectedBody.put("description", "训练数据盘");
        expectedBody.put("displayName", "train-data");
        expectedBody.put("size", 20);
        expectedBody.put("type", "fileset");
        expectedBody.put("scope", "user");
        when(httpClient.post(eq("/api/100001/file/storage"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("storageRequestId", 88)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("location", "shanghai");
        params.put("name", "train-data");
        params.put("description", "训练数据盘");
        params.put("size", "20");
        params.put("type", "fileset");
        params.put("scope", "user");
        params.put("organizationId", "100002");
        params.put("userId", "999");
        params.put("approved", true);

        Map<String, Object> result = new StorageCreateTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/file/storage"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void storageDelete_shouldUseReviewedDeleteStorageQueryEndpoint() {
        when(httpClient.delete(eq("/api/100001/file/deleteStorage"), eq(Map.of("name", "train-data"))))
            .thenReturn(Map.of("result", Map.of("deleted", true)));

        Map<String, Object> result = new StorageDeleteTool(httpClient)
            .execute(Map.of("name", "train-data", "userId", "999"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).delete(eq("/api/100001/file/deleteStorage"), eq(Map.of("name", "train-data")));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void storageDelete_shouldProtectSystemStorageNames() {
        Map<String, Object> result = new StorageDeleteTool(httpClient).execute(Map.of("name", "user"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("SYSTEM_STORAGE_PROTECTED", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void userCreate_shouldSendUserDetailDtoShapeAndFilterProtectedContext() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("username", "alice");
        expectedBody.put("password", "ChangeMe-123");
        expectedBody.put("name", "Alice");
        expectedBody.put("roles", List.of("ROLE_USER"));
        expectedBody.put("cpuLimits", 500);
        expectedBody.put("memLimits", 1024);
        when(httpClient.post(eq("/api/100001/user"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("id", 10000002)));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("username", "alice");
        params.put("password", "ChangeMe-123");
        params.put("name", "Alice");
        params.put("roles", List.of("ROLE_USER"));
        params.put("cpuLimits", 500);
        params.put("memLimits", 1024);
        params.put("organizationId", "100002");
        params.put("orgId", "100003");
        params.put("userId", "999");
        params.put("token", "forged");
        params.put("sessionId", "session-x");
        params.put("approved", true);

        Map<String, Object> result = new UserCreateTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/user"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void userDelete_shouldUseReviewedBackendControllerPathWithId() {
        when(httpClient.delete(eq("/api/100001/user/10000002"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of("deleted", true)));

        Map<String, Object> result = new UserDeleteTool(httpClient)
            .execute(Map.of("id", "10000002", "organizationId", "100002"));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).delete(eq("/api/100001/user/10000002"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void userDelete_shouldFailClosedWhenTargetIdMissing() {
        Map<String, Object> result = new UserDeleteTool(httpClient)
            .execute(Map.of("organizationId", "100002"));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("MISSING_USER_ID", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void deployCreate_shouldFormatHumanUnitsIntoKubeManagerBackendUnits() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("name", "demo-app");
        expectedBody.put("image", "ubuntu:22.04");
        expectedBody.put("cpuLimits", 2500);
        expectedBody.put("memLimits", 4096);
        expectedBody.put("cpuRequests", 2500);
        expectedBody.put("memRequests", 4096);
        expectedBody.put("gpuPercentLimits", 0);
        expectedBody.put("gpuMemLimits", 0);
        expectedBody.put("replicas", 2);
        expectedBody.put("bandwidth", 5);
        expectedBody.put("ingressBandwidth", "5M");
        expectedBody.put("egressBandwidth", "5M");
        expectedBody.put("enableWebSsh", true);
        expectedBody.put("enableSecondNetwork", true);
        expectedBody.put("autoScaleSwitch", false);
        expectedBody.put("autoScaleConfig", null);
        when(httpClient.post(eq("/api/100001/deployment"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("name", "demo-app")));

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", "demo-app");
        params.put("image", "ubuntu:22.04");
        params.put("cpuLimits", "2.5");
        params.put("memLimits", "4");
        params.put("replicas", "2");
        params.put("organizationId", "100002");

        Map<String, Object> result = new DeployCreateTool(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).post(eq("/api/100001/deployment"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void deployCreate_shouldFailClosedWhenGpuRequestedWithoutExplicitGpuModel() {
        Map<String, Object> result = new DeployCreateTool(httpClient).execute(Map.of(
            "name", "gpu-app",
            "image", "cuda:12",
            "gpuPercentLimits", 1
        ));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("MISSING_GPU_SPEC", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void deployCreate_shouldResolveGpuSpecFromOrganizationGpuMapBeforePost() {
        Map<String, Object> gpuMap = Map.of(
            "A100#all-2g.10gb", Map.of(
                "spec", "A100#all-2g.10gb",
                "gpuModel", "A100",
                "migConfig", "all-2g.10gb",
                "memory", 10240,
                "instancePerGpu", 4
            )
        );
        when(httpClient.get(eq("/api/100001/node/all/gpu-map")))
            .thenReturn(Map.of("result", gpuMap));

        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("name", "gpu-app");
        expectedBody.put("image", "cuda:12");
        expectedBody.put("gpuPercentLimits", 100);
        expectedBody.put("gpuSpec", "A100#all-2g.10gb");
        expectedBody.put("cpuLimits", 2000);
        expectedBody.put("memLimits", 8192);
        expectedBody.put("cpuRequests", 2000);
        expectedBody.put("memRequests", 8192);
        expectedBody.put("gpuModel", "A100");
        expectedBody.put("migConfig", "all-2g.10gb");
        expectedBody.put("gpuMemLimits", 0);
        expectedBody.put("replicas", 1);
        expectedBody.put("bandwidth", 5);
        expectedBody.put("ingressBandwidth", "5M");
        expectedBody.put("egressBandwidth", "5M");
        expectedBody.put("enableWebSsh", true);
        expectedBody.put("enableSecondNetwork", true);
        expectedBody.put("autoScaleSwitch", false);
        expectedBody.put("autoScaleConfig", null);
        when(httpClient.post(eq("/api/100001/deployment"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("name", "gpu-app")));

        Map<String, Object> result = new DeployCreateTool(httpClient).execute(Map.of(
            "name", "gpu-app",
            "image", "cuda:12",
            "gpuPercentLimits", 1,
            "gpuSpec", "A100#all-2g.10gb"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100001/node/all/gpu-map"));
        verify(httpClient).post(eq("/api/100001/deployment"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void deployCreate_shouldFailClosedWhenGpuModelMatchesMultipleMigOptions() {
        Map<String, Object> gpuMap = Map.of(
            "A100#all-1g.5gb", Map.of("spec", "A100#all-1g.5gb", "gpuModel", "A100", "migConfig", "all-1g.5gb", "memory", 5120),
            "A100#all-2g.10gb", Map.of("spec", "A100#all-2g.10gb", "gpuModel", "A100", "migConfig", "all-2g.10gb", "memory", 10240)
        );
        when(httpClient.get(eq("/api/100001/node/all/gpu-map")))
            .thenReturn(Map.of("result", gpuMap));

        Map<String, Object> result = new DeployCreateTool(httpClient).execute(Map.of(
            "name", "gpu-app",
            "image", "cuda:12",
            "gpuPercentLimits", 1,
            "gpuModel", "A100"
        ));

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("AMBIGUOUS_GPU_SPEC", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verify(httpClient).get(eq("/api/100001/node/all/gpu-map"));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void deployScale_shouldFailClosedWhenReplicaParamIsInvalid() {
        Map<String, Object> result = new DeployScaleTool(httpClient)
            .execute(Map.of("name", "demo-app", "targetReplicas", -1));

        assertFalse(Boolean.TRUE.equals(result.get(AtlasToolResult.KEY_SUCCESS)));
        assertEquals("INVALID_REPLICAS", result.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void userEnableAndDisable_shouldUseTrustedOrgAndTargetIdOnly() {
        when(httpClient.put(eq("/api/100001/user/enable/42"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of()));
        when(httpClient.put(eq("/api/100001/user/disable/43"), eq(Map.of())))
            .thenReturn(Map.of("result", Map.of()));

        Map<String, Object> enableResult = new UserEnableTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "orgId", "888888",
            "id", "42",
            "userId", "777",
            "token", "forged"
        ));
        Map<String, Object> disableResult = new UserDisableTool(httpClient).execute(Map.of(
            "targetUserId", "43",
            "organizationId", "999999",
            "sessionId", "session-x"
        ));

        assertEquals(Boolean.TRUE, enableResult.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(Boolean.TRUE, disableResult.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).put(eq("/api/100001/user/enable/42"), eq(Map.of()));
        verify(httpClient).put(eq("/api/100001/user/disable/43"), eq(Map.of()));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void userRecharge_shouldWhitelistRechargeDtoAndValidateAmount() {
        Map<String, Object> expectedBody = new LinkedHashMap<>();
        expectedBody.put("userId", 42);
        expectedBody.put("amount", 10000);
        expectedBody.put("remark", "季度补贴");
        when(httpClient.put(eq("/api/100001/user/recharge"), eq(expectedBody)))
            .thenReturn(Map.of("result", Map.of("ok", true)));

        Map<String, Object> result = new UserRechargeTool(httpClient).execute(Map.of(
            "organizationId", "999999",
            "targetUserId", "42",
            "amount", "10000",
            "remark", " 季度补贴 ",
            "balance", 1,
            "approved", true,
            "token", "forged"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).put(eq("/api/100001/user/recharge"), eq(expectedBody));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void userRiskMutation_shouldFailClosedBeforeHttpForInvalidTargetOrAmount() {
        Map<String, Object> badEnable = new UserEnableTool(httpClient).execute(Map.of("id", "../42"));
        Map<String, Object> badRecharge = new UserRechargeTool(httpClient).execute(Map.of(
            "id", "42",
            "amount", "-100"
        ));

        assertEquals(Boolean.FALSE, badEnable.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_TARGET_USER_ID", badEnable.get(AtlasToolResult.KEY_ERROR_CODE));
        assertEquals(Boolean.FALSE, badRecharge.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals("INVALID_RECHARGE_AMOUNT", badRecharge.get(AtlasToolResult.KEY_ERROR_CODE));
        verifyNoMoreInteractions(httpClient);
    }

    @Test
    void deployCreate_shouldExposeRichParameterSchemaForAgentPlanning() {
        Map<String, ToolParameterSpec> specs = new DeployCreateTool(httpClient).getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertEquals(true, specs.get("name").required());
        assertEquals(true, specs.get("image").required());
        assertEquals("number", specs.get("cpuLimits").type());
        assertEquals("number", specs.get("memLimits").type());
        assertEquals("integer", specs.get("replicas").type());
        assertEquals("boolean", specs.get("enableWebSsh").type());
        assertEquals("number", specs.get("gpuPercentLimits").type());
        assertEquals("string", specs.get("gpuModel").type());
        assertEquals("string", specs.get("gpuSpec").type());

        assertContains(specs.get("cpuLimits").description(), "核", "毫核");
        assertContains(specs.get("memLimits").description(), "GB", "MiB");
        assertContains(specs.get("gpuPercentLimits").description(), "gpuModel");
        assertContains(specs.get("gpuModel").description(), "gpu_query");
        assertContains(specs.get("gpuSpec").description(), "gpu_query", "组织级 GPU map");

        List<String> allAliases = specs.values().stream()
            .flatMap(spec -> spec.aliases().stream())
            .map(alias -> alias.replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT))
            .toList();
        assertFalse(allAliases.contains("orgid"));
        assertFalse(allAliases.contains("organizationid"));
        assertFalse(allAliases.contains("token"));
        assertFalse(allAliases.contains("userid"));
        assertFalse(allAliases.contains("conversationid"));
    }

    @Test
    void highRiskMutationTools_shouldExposeParameterSchemaForApprovalAndPlanning() {
        Map<String, ToolParameterSpec> deleteSpecs = specsByName(new DeployDeleteTool(httpClient).getParameterSpecs());
        assertEquals(true, deleteSpecs.get("name").required());
        assertEquals("string", deleteSpecs.get("name").type());
        assertContains(deleteSpecs.get("name").description(), "删除", "高风险", "实例名称");

        Map<String, ToolParameterSpec> scaleSpecs = specsByName(new DeployScaleTool(httpClient).getParameterSpecs());
        assertEquals(true, scaleSpecs.get("name").required());
        assertEquals(true, scaleSpecs.get("targetReplicas").required());
        assertEquals("integer", scaleSpecs.get("targetReplicas").type());
        assertContains(scaleSpecs.get("targetReplicas").description(), "非负整数", "targetReplicas=0");

        Map<String, ToolParameterSpec> mpiSubmitSpecs = specsByName(new MpiJobSubmitTool(httpClient).getParameterSpecs());
        assertEquals(true, mpiSubmitSpecs.get("id").required());
        assertEquals("string", mpiSubmitSpecs.get("id").type());
        assertContains(mpiSubmitSpecs.get("id").description(), "MPI Job ID", "改变任务状态");

        Map<String, ToolParameterSpec> userRechargeSpecs = specsByName(new UserRechargeTool(httpClient).getParameterSpecs());
        assertEquals(true, userRechargeSpecs.get("id").required());
        assertEquals(true, userRechargeSpecs.get("amount").required());
        assertEquals("integer", userRechargeSpecs.get("amount").type());
        assertContains(userRechargeSpecs.get("amount").description(), "单位为分", "正整数");
    }

    @Test
    void composeAndHelmTools_shouldExposeParameterSchemaForApprovalAndPlanning() {
        Map<String, ToolParameterSpec> composeSpecs = specsByName(new ComposeDeployCreateTool(httpClient).getParameterSpecs());
        assertEquals(true, composeSpecs.get("composeName").required());
        assertEquals(true, composeSpecs.get("contentYaml").required());
        assertEquals("array", composeSpecs.get("resourceList").type());
        assertEquals("array", composeSpecs.get("sizeList").type());
        assertEquals(List.of("name", "displayName"), composeSpecs.get("composeName").aliases());
        assertEquals(List.of("yaml", "composeYaml", "content"), composeSpecs.get("contentYaml").aliases());

        Map<String, ToolParameterSpec> helmRepoSpecs = specsByName(new HelmRepoAddTool(httpClient).getParameterSpecs());
        assertEquals(true, helmRepoSpecs.get("name").required());
        assertEquals(true, helmRepoSpecs.get("url").required());
        assertEquals("string", helmRepoSpecs.get("name").type());
        assertEquals("string", helmRepoSpecs.get("url").type());

        Map<String, ToolParameterSpec> helmDeleteSpecs = specsByName(new HelmReleaseDeleteTool(httpClient).getParameterSpecs());
        assertEquals(true, helmDeleteSpecs.get("releaseName").required());
        assertEquals("string", helmDeleteSpecs.get("releaseName").type());
        assertEquals(List.of("release", "name"), helmDeleteSpecs.get("releaseName").aliases());

        Map<String, ToolParameterSpec> helmInstallSpecs = specsByName(new HelmReleaseInstallTool(httpClient).getParameterSpecs());
        assertEquals(true, helmInstallSpecs.get("release").required());
        assertEquals(true, helmInstallSpecs.get("chart").required());
        assertEquals("boolean", helmInstallSpecs.get("dry_run").type());

        Map<String, ToolParameterSpec> helmUpgradeSpecs = specsByName(new HelmReleaseUpgradeTool(httpClient).getParameterSpecs());
        assertEquals(true, helmUpgradeSpecs.get("release").required());
        assertEquals(true, helmUpgradeSpecs.get("chart").required());
        assertEquals("boolean", helmUpgradeSpecs.get("force").type());

        Map<String, ToolParameterSpec> helmRollbackSpecs = specsByName(new HelmReleaseRollbackTool(httpClient).getParameterSpecs());
        assertEquals(true, helmRollbackSpecs.get("release").required());
        assertEquals(true, helmRollbackSpecs.get("version").required());
        assertEquals("integer", helmRollbackSpecs.get("history_max").type());

        Map<String, ToolParameterSpec> helmRepoRemoveSpecs = specsByName(new HelmRepoRemoveTool(httpClient).getParameterSpecs());
        assertEquals(true, helmRepoRemoveSpecs.get("repoName").required());

        Map<String, ToolParameterSpec> composeDeleteSpecs = specsByName(new ComposeDeployDeleteTool(httpClient).getParameterSpecs());
        assertEquals(true, composeDeleteSpecs.get("composeId").required());

        Map<String, ToolParameterSpec> composeUpdateSpecs = specsByName(new ComposeDeployUpdateTool(httpClient).getParameterSpecs());
        assertEquals(true, composeUpdateSpecs.get("composeId").required());
        assertEquals(true, composeUpdateSpecs.get("composeName").required());
    }

    private Map<String, ToolParameterSpec> specsByName(List<ToolParameterSpec> specs) {
        return specs.stream().collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));
    }

    private void assertContains(String text, String... expectedParts) {
        for (String part : expectedParts) {
            org.junit.jupiter.api.Assertions.assertTrue(text.contains(part),
                "参数说明应包含关键语义: " + part + "，实际说明: " + text);
        }
    }
}
