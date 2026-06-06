package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 列表类 Tool 参数透传契约测试。
 *
 * <p>M4.2 已经让部分列表 Tool 暴露 page / limit / keyword 参数契约，
 * 但如果执行层仍固定使用 page=1、limit=100，就会形成“伪参数”：
 * LLM 认为筛选生效，真实 kube-manager 请求却完全忽略用户参数。</p>
 *
 * <p>本测试采用 TDD 红灯方式先锁定缺陷：调用 Tool 时传入 page / limit / keyword，
 * 必须精确透传到 {@link KubeManagerHttpClient#get(String, Map)} 的 query map。
 * 测试不依赖真实 kube-manager，只验证 Tool 到 HTTP 客户端的契约边界。</p>
 */
class ListToolParameterPassThroughContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100002");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void listTools_shouldPassPageLimitAndKeywordToKubeManagerQueryParams() {
        assertPassThrough(MpiJobListTool::new, "/api/100002/mpi-job");
        assertPassThrough(PytorchJobListTool::new, "/api/100002/pytorch-job");
        assertPassThrough(FileMaterialListTool::new, "/api/100002/material/folders");
        assertPassThrough(GpuDetailListTool::new, "/api/100002/gpu-detail");
        assertPassThrough(DataSetListTool::new, "/api/100002/data-set");
        assertPassThrough(ModelListTool::new, "/api/100002/model");
        assertPassThrough(FileListTool::new, "/api/100002/file");
        assertPassThrough(TensorBoardListTool::new, "/api/100002/tensorboard");
        assertPassThrough(JobTemplateListTool::new, "/api/100002/train-job-template");
        assertPassThrough(TemplateListTool::new, "/api/100002/template");
        assertPassThrough(ResourcePresetListTool::new, "/api/100002/resource-preset");
        assertPassThrough(BareMetalAppListTool::new, "/api/100002/bare-metal-application");
        assertPassThrough(CloudResourceListTool::new, "/api/100002/cloud");
        assertPassThrough(ComposeListTool::new, "/api/100002/compose");
        assertPassThrough(ExperimentInstanceListTool::new, "/api/100002/experiment/instance");
        assertPassThrough(ExperimentTemplateListTool::new, "/api/100002/experiment/template");
        assertPassThrough(HelmRepoListTool::new, "/api/100002/helm/repositories");
        assertPassThrough(HelmReleaseListTool::new, "/api/100002/helm/releases");
        assertPassThrough(CoursewareListTool::new, "/api/100002/courseware/list");
        assertPassThrough(DownloadTaskListTool::new, "/api/100002/download");
        assertPassThrough(InboxMessageListTool::new, "/api/100002/inbox-message");
        assertPassThrough(NamespaceListTool::new, "/api/100002/namespace");
        assertPassThrough(TableListTool::new, "/api/100002/table");
        assertPassThrough(SlurmNodeListTool::new, "/api/100002/slurm-node");
        assertPassThrough(SlurmClusterListTool::new, "/api/100002/bcm/slurm-cluster");
        assertPassThrough(ResourceUsageListTool::new, "/api/100002/resource");
        assertPassThrough(QuotaMyListTool::new, "/api/100002/quota/my");
        assertPassThrough(CurrencyQueryListTool::new, "/api/100002/currency");
    }

    @Test
    void listTools_shouldUseDefaultPaginationAndIgnoreBlankKeyword() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/mpi-job"), anyMap())).thenReturn(Map.of(
                "result", Map.of("records", List.of())
        ));

        BaseTool tool = new MpiJobListTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of(
                "organizationId", "100002",
                "page", " ",
                "limit", "",
                "keyword", "   "
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100002/mpi-job"), eq(Map.of(
                "page", "1",
                "limit", "100"
        )));
    }

    @Test
    void listTools_shouldTrimKeywordAndKeepItInQueryMapOnly() {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq("/api/100002/mpi-job"), anyMap())).thenReturn(Map.of(
                "result", Map.of("records", List.of())
        ));

        BaseTool tool = new MpiJobListTool(httpClient);

        Map<String, Object> result = tool.execute(Map.of(
                "organizationId", "100002",
                "page", 2,
                "limit", 10L,
                "keyword", "  bert chart?x=1&unsafe=true  "
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq("/api/100002/mpi-job"), eq(Map.of(
                "page", "2",
                "limit", "10",
                "keyword", "bert chart?x=1&unsafe=true"
        )));
    }

    @Test
    void listTools_shouldRejectNonPositivePaginationBeforeHttpCall() {
        assertInvalidPagination(MpiJobListTool::new, "/api/100002/mpi-job", "page", "0", "VALUE_OUT_OF_RANGE");
        assertInvalidPagination(PytorchJobListTool::new, "/api/100002/pytorch-job", "limit", "-1", "VALUE_OUT_OF_RANGE");
        assertInvalidPagination(FileMaterialListTool::new, "/api/100002/material/folders", "page", "abc", "TYPE_MISMATCH");
        assertInvalidPagination(GpuDetailListTool::new, "/api/100002/gpu-detail", "limit", 1.5D, "TYPE_MISMATCH");
        assertInvalidPagination(DataSetListTool::new, "/api/100002/data-set", "page", "0", "VALUE_OUT_OF_RANGE");
        assertInvalidPagination(ModelListTool::new, "/api/100002/model", "limit", 1.5D, "TYPE_MISMATCH");
        assertInvalidPagination(ComposeListTool::new, "/api/100002/compose", "page", "0", "VALUE_OUT_OF_RANGE");
        assertInvalidPagination(HelmRepoListTool::new, "/api/100002/helm/repositories", "limit", 1.5D, "TYPE_MISMATCH");
        assertInvalidPagination(CoursewareListTool::new, "/api/100002/courseware/list", "page", "0", "VALUE_OUT_OF_RANGE");
        assertInvalidPagination(DownloadTaskListTool::new, "/api/100002/download", "limit", 1.5D, "TYPE_MISMATCH");
        assertInvalidPagination(SlurmClusterListTool::new, "/api/100002/bcm/slurm-cluster", "page", "abc", "TYPE_MISMATCH");
        assertInvalidPagination(ResourceUsageListTool::new, "/api/100002/resource", "page", "0", "VALUE_OUT_OF_RANGE");
        assertInvalidPagination(QuotaMyListTool::new, "/api/100002/quota/my", "limit", 1.5D, "TYPE_MISMATCH");
        assertInvalidPagination(CurrencyQueryListTool::new, "/api/100002/currency", "page", "0", "VALUE_OUT_OF_RANGE");
    }

    private void assertPassThrough(ToolFactory factory, String expectedPath) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq(expectedPath), anyMap())).thenReturn(Map.of(
                "result", Map.of("records", List.of())
        ));

        BaseTool tool = factory.create(httpClient);

        Map<String, Object> result = tool.execute(Map.of(
                "organizationId", "100002",
                "page", "3",
                "limit", "25",
                "keyword", "bert"
        ));

        assertEquals(true, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq(expectedPath), eq(Map.of(
                "page", "3",
                "limit", "25",
                "keyword", "bert"
        )));
    }

    private void assertInvalidPagination(ToolFactory factory,
                                         String expectedPath,
                                         String invalidKey,
                                         Object invalidValue,
                                         String expectedErrorCode) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        BaseTool tool = factory.create(httpClient);

        Map<String, Object> result = tool.execute(Map.of(
                "organizationId", "100002",
                invalidKey, invalidValue
        ));

        assertEquals(false, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(expectedErrorCode, result.get(AtlasToolResult.KEY_ERROR_CODE));
        assertTrue(result.containsKey(AtlasToolResult.KEY_SUGGESTIONS));
        verify(httpClient, never()).get(eq(expectedPath), anyMap());
    }

    @FunctionalInterface
    private interface ToolFactory {
        BaseTool create(KubeManagerHttpClient httpClient);
    }
}
