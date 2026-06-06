package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 查询类 Tool 的参数契约加固测试。
 *
 * <p>这些 Tool 在业务上虽然都是“列表查询”，但 LLM 经常会携带 page、limit、keyword、name 等筛选词。
 * 如果 Tool 不声明结构化参数契约，ReAct 工具目录就只能暴露模糊说明，容易导致 Action.params 使用错误字段。
 * 本测试用小批代表性 Tool 锁定分页与关键词参数契约，先验证一组高频查询工具，再逐步铺开到其它 Tool。</p>
 */
class ListToolParameterSpecContractTest {

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
        UserPermissionContext.CURRENT_TOKEN.remove();
    }

    @Test
    void listTools_shouldExposePaginationAndKeywordContractForReact() {
        assertListQueryContract(new MpiJobListTool(null), "mpi_job_list");
        assertListQueryContract(new PytorchJobListTool(null), "pytorch_job_list");
        assertListQueryContract(new FileMaterialListTool(null), "file_material_list");
        assertListQueryContract(new GpuDetailListTool(null), "gpu_detail_list");
        assertListQueryContract(new DataSetListTool(null), "data_set_list");
        assertListQueryContract(new ModelListTool(null), "model_list");
        assertListQueryContract(new FileListTool(null), "file_list");
        assertListQueryContract(new RegistryListTool(null), "registry_list");
        assertListQueryContract(new TensorBoardListTool(null), "tensorboard_list");
        assertListQueryContract(new JobTemplateListTool(null), "job_template_list");
        assertListQueryContract(new TemplateListTool(null), "template_list");
        assertListQueryContract(new ResourcePresetListTool(null), "resource_preset_list");
        assertListQueryContract(new BareMetalAppListTool(null), "bare_metal_app_list");
        assertListQueryContract(new CloudResourceListTool(null), "cloud_resource_list");
        assertListQueryContract(new ComposeListTool(null), "compose_list");
        assertListQueryContract(new ExperimentInstanceListTool(null), "experiment_instance_list");
        assertListQueryContract(new ExperimentTemplateListTool(null), "experiment_template_list");
        assertListQueryContract(new HelmRepoListTool(null), "helm_repo_list");
        assertListQueryContract(new HelmReleaseListTool(null), "helm_release_list");
        assertListQueryContract(new CoursewareListTool(null), "courseware_list");
        assertListQueryContract(new DownloadTaskListTool(null), "download_task_list");
        assertListQueryContract(new InboxMessageListTool(null), "inbox_message_list");
        assertListQueryContract(new MigConfigListTool(null), "mig_config_list");
        assertListQueryContract(new NamespaceListTool(null), "namespace_list");
        assertListQueryContract(new TableListTool(null), "table_list");
        assertListQueryContract(new SlurmNodeListTool(null), "slurm_node_list");
        assertListQueryContract(new SlurmClusterListTool(null), "slurm_cluster_list");
        assertListQueryContract(new UploadStatusListTool(null), "upload_status_list");
        assertListQueryContract(new ResourceUsageListTool(null), "resource_usage_list");
        assertListQueryContract(new QuotaMyListTool(null), "quota_my_list");
        assertListQueryContract(new CurrencyQueryListTool(null), "currency_query_list");
        assertListQueryContract(new NamespaceQueryTool(null), "namespace_status");
        assertListQueryContract(new ServiceQueryTool(null), "service_status");
        assertListQueryContract(new IngressQueryTool(null), "ingress_query");
        assertListQueryContract(new DaemonSetQueryTool(null), "daemonset_status");
        assertListQueryContract(new ClusterQueryTool(null), "cluster_query");
        assertListQueryContract(new ImageQueryTool(null), "image_query");
    }

    @Test
    void secondBatchQueryTools_shouldPassListQueryParamsToHttpClient() {
        assertListQueryPassThrough(NamespaceQueryTool::new, "/api/namespace");
        assertListQueryPassThrough(ServiceQueryTool::new, "/api/100001/dashboard/resources");
        assertListQueryPassThrough(IngressQueryTool::new, "/api/100001/dashboard/deployment");
        assertListQueryPassThrough(DaemonSetQueryTool::new, "/api/100001/dashboard/deployment");
        assertListQueryPassThrough(ClusterQueryTool::new, "/api/100001/hpc-job/cluster");
        assertListQueryPassThrough(ImageQueryTool::new, "/api/100001/image");
    }

    /**
     * 校验本批查询 Tool 不只是“声明参数”，还必须把 page/limit/keyword 真正透传给 HTTP 客户端。
     *
     * <p>这是为了防止出现伪 schema：LLM 在 ReAct/Plan 中看到参数可用，但 doExecute 仍然固定
     * page=1、limit=100 或完全忽略 keyword，最终让用户的自然语言筛选条件失效。</p>
     */
    private void assertListQueryPassThrough(ToolFactory factory, String expectedPath) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq(expectedPath), eq(Map.of(
            "page", "3",
            "limit", "20",
            "keyword", "gpu node"
        )))).thenReturn(Map.of("result", List.of()));

        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        Map<String, Object> result = factory.create(httpClient).execute(Map.of(
            "page", "3",
            "limit", "20",
            "keyword", "  gpu node  "
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq(expectedPath), eq(Map.of(
            "page", "3",
            "limit", "20",
            "keyword", "gpu node"
        )));
    }

    /**
     * 校验列表查询 Tool 必须向 ReAct/LLM 暴露统一的 page、limit、keyword 参数契约。
     */
    private void assertListQueryContract(com.atlas.tool.core.BaseTool tool, String toolName) {
        Map<String, ToolParameterSpec> specs = tool.getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertTrue(specs.containsKey("page"), toolName + " 应声明 page 参数，避免 LLM 手写 URL query");
        assertTrue(specs.containsKey("limit"), toolName + " 应声明 limit 参数，避免 LLM 手写 URL query");
        assertTrue(specs.containsKey("keyword"), toolName + " 应声明 keyword 参数，承接用户自然语言筛选词");

        assertEquals("string", specs.get("page").type());
        assertEquals("string", specs.get("limit").type());
        assertEquals("string", specs.get("keyword").type());

        assertFalse(specs.get("page").required(), toolName + " 的 page 应为可选参数");
        assertFalse(specs.get("limit").required(), toolName + " 的 limit 应为可选参数");
        assertFalse(specs.get("keyword").required(), toolName + " 的 keyword 应为可选参数");

        assertTrue(specs.get("page").aliases().containsAll(List.of("pageNo", "page_no", "current")));
        assertTrue(specs.get("limit").aliases().containsAll(List.of("pageSize", "page_size", "size")));
        assertTrue(specs.get("keyword").aliases().containsAll(List.of("name", "search", "kw")));
    }

    @FunctionalInterface
    private interface ToolFactory {
        com.atlas.tool.core.BaseTool create(KubeManagerHttpClient httpClient);
    }
}
