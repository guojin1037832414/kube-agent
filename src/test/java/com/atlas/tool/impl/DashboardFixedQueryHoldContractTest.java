package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
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
 * M5.4 Dashboard 固定查询 Tool 的 HOLD 保护测试。
 *
 * <p>Dashboard count / easy-flow 当前产品语义是“看板固定摘要查询”，不是普通资源列表。
 * 因此本阶段不向 LLM 暴露用户可控 page / limit / keyword，也不允许把调用方传入的
 * 搜索参数透传给 kube-manager。该测试用于防止后续批量脚本误把 Dashboard 工具接入
 * {@code listQueryParameterSpecs()}、{@code buildListQuery(params)} 或 M5.3 的
 * {@code buildPageLimitOnlyQuery(params, 100)}。</p>
 */
class DashboardFixedQueryHoldContractTest {

    @Test
    void m54_dashboardFixedQueryTools_shouldNotExposeStandardListParameterSpecs() {
        assertNoListSpecs(new DashboardDeploymentCountTool(null), "dashboard_deployment_count");
        assertNoListSpecs(new DashboardImageCountTool(null), "dashboard_image_count");
        assertNoListSpecs(new DashboardEasyFlowTool(null), "dashboard_easy_flow");
    }

    @Test
    void m54_dashboardFixedQueryTools_shouldIgnoreCallerPaginationAndSearchParams() {
        assertFixedQuery(DashboardDeploymentCountTool::new,
            "/api/100001/dashboard/deployment/count");
        assertFixedQuery(DashboardImageCountTool::new,
            "/api/100001/dashboard/image/count");
        assertFixedQuery(DashboardEasyFlowTool::new,
            "/api/100001/dashboard/easy-flow");
    }

    /**
     * 断言 Dashboard 固定查询不声明任何普通列表参数。
     *
     * <p>这里连 page/limit 也禁止声明，因为本阶段专家未对 Dashboard count 是否可由用户
     * 自由翻页达成一致；在无共识时选择 HOLD，避免把看板摘要接口扩大成枚举入口。</p>
     */
    private void assertNoListSpecs(BaseTool tool, String toolName) {
        Map<String, ToolParameterSpec> specs = tool.getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertFalse(specs.containsKey("page"), toolName + " 是 Dashboard 固定查询，不得暴露用户可控 page");
        assertFalse(specs.containsKey("limit"), toolName + " 是 Dashboard 固定查询，不得暴露用户可控 limit");
        assertFalse(specs.containsKey("keyword"), toolName + " 是 Dashboard 固定查询，不得暴露 keyword 搜索入口");

        for (ToolParameterSpec spec : specs.values()) {
            assertFalse(spec.aliases().contains("name"), toolName + " 不得通过 alias 暴露 name 搜索");
            assertFalse(spec.aliases().contains("search"), toolName + " 不得通过 alias 暴露 search 搜索");
            assertFalse(spec.aliases().contains("kw"), toolName + " 不得通过 alias 暴露 kw 搜索");
        }
    }

    /**
     * 断言执行层仍使用 Dashboard 固定查询参数，忽略调用方注入的分页和搜索探测参数。
     */
    private void assertFixedQuery(ToolFactory factory, String expectedPath) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq(expectedPath), eq(Map.of("page", "1", "limit", "100"))))
            .thenReturn(Map.of("result", List.of()));

        BaseTool tool = factory.create(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "orgId", "100001",
            "page", "9",
            "limit", "999",
            "keyword", "probe",
            "name", "internal",
            "search", "hidden",
            "kw", "x"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq(expectedPath), eq(Map.of("page", "1", "limit", "100")));
        verifyNoMoreInteractions(httpClient);
    }

    @FunctionalInterface
    private interface ToolFactory {
        BaseTool create(KubeManagerHttpClient httpClient);
    }
}
