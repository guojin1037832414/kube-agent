package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * M5.5 非普通列表固定查询 Tool 的 HOLD 保护测试。
 *
 * <p>这些 Tool 当前虽然在执行层使用了 {@code page=1, limit=100}，但产品语义并不是普通资源列表：
 * 有的是指标摘要，有的是节点分配/映射配置，有的是指定对象的历史记录。若机械套用
 * {@code listQueryParameterSpecs()}，会向 LLM 暴露 keyword/name/search/kw 等搜索入口，
 * 把“摘要/详情/历史”误扩成可枚举列表。</p>
 *
 * <p>因此本测试先把它们纳入 HOLD：不声明标准列表三件套，执行层继续忽略调用方注入的分页和搜索参数。
 * 后续如需开放，应按具体业务语义设计细粒度 schema，例如 release/id/nodeName，而不是复用普通列表契约。</p>
 */
class NonListFixedQueryHoldContractTest {

    @BeforeEach
    void setUpTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
    }

    @AfterEach
    void tearDownTrustedOrganizationContext() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
    }

    @Test
    void m55_nonListFixedQueryTools_shouldNotExposeStandardListParameterSpecs() {
        assertNoStandardListSpecs(new NodeMetricsTool(null), "node_metrics", "节点指标摘要查询");
        assertNoStandardListSpecs(new GpuMetricsTool(null), "gpu_metrics", "GPU 配置映射摘要查询");
        assertNoStandardListSpecs(new GpuMapDetailTool(null), "gpu_map_detail", "全局 GPU 映射详情查询");
        assertNoStandardListSpecs(new NodeAllocationTool(null), "node_allocation", "节点分配摘要查询");
        assertNoStandardListSpecs(new HelmReleaseHistoryTool(null), "helm_release_history", "指定 Helm Release 历史查询");
        assertNoStandardListSpecs(new MpiJobDetailTool(null), "mpi_job_detail", "指定 MPI 任务详情查询");
    }

    @Test
    void m55_nonListFixedQueryTools_shouldIgnoreCallerPaginationAndSearchParams() {
        assertFixedQuery(NodeMetricsTool::new, "/api/100001/node", Map.of());
        assertFixedQuery(GpuMetricsTool::new, "/api/100001/node/all/gpu-map", Map.of());
        assertFixedQuery(GpuMapDetailTool::new, "/api/gpu/all/gpu-map", Map.of());
        assertFixedQuery(NodeAllocationTool::new, "/api/100001/node/organization/allocation", Map.of());
        assertFixedQuery(HelmReleaseHistoryTool::new,
            "/api/100001/helm/releases/demo-release/histories",
            Map.of("release", "demo-release"));
        assertFixedQuery(MpiJobDetailTool::new,
            "/api/100001/mpi-job/demo-job",
            Map.of("id", "demo-job"));
    }

    /**
     * 断言非普通列表 Tool 不暴露标准列表三件套及其搜索别名。
     */
    private void assertNoStandardListSpecs(BaseTool tool, String toolName, String querySemantics) {
        Map<String, ToolParameterSpec> specs = tool.getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertFalse(specs.containsKey("page"), toolName + " 是" + querySemantics + "，专项设计前不得暴露 page");
        assertFalse(specs.containsKey("limit"), toolName + " 是" + querySemantics + "，专项设计前不得暴露 limit");
        assertFalse(specs.containsKey("keyword"), toolName + " 是" + querySemantics + "，专项设计前不得暴露 keyword");

        for (ToolParameterSpec spec : specs.values()) {
            assertFalse(spec.aliases().contains("name"), toolName + " 不得通过 alias 暴露 name 搜索");
            assertFalse(spec.aliases().contains("search"), toolName + " 不得通过 alias 暴露 search 搜索");
            assertFalse(spec.aliases().contains("kw"), toolName + " 不得通过 alias 暴露 kw 搜索");
        }
    }

    /**
     * 断言执行层仍保持固定查询参数，调用方注入的分页/搜索探测参数不能下传到 kube-manager。
     */
    private void assertFixedQuery(ToolFactory factory, String expectedPath, Map<String, Object> requiredParams) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq(expectedPath), eq(Map.of("page", "1", "limit", "100"))))
            .thenReturn(Map.of("result", List.of()));

        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.putAll(requiredParams);
        params.put("orgId", "100001");
        params.put("page", "9");
        params.put("limit", "999");
        params.put("keyword", "probe");
        params.put("search", "hidden");
        params.put("kw", "x");

        Map<String, Object> result = factory.create(httpClient).execute(params);

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq(expectedPath), eq(Map.of("page", "1", "limit", "100")));
        verifyNoMoreInteractions(httpClient);
    }

    @FunctionalInterface
    private interface ToolFactory {
        BaseTool create(KubeManagerHttpClient httpClient);
    }
}
