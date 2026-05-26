package com.atlas.tool.impl;

import com.atlas.auth.UserPermissionContext;
import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * M5.3 首页公共展示 Tool 的 page/limit-only 参数契约测试。
 *
 * <p>这些 Tool 对接 {@code /api/public/home-info/*} 首页公共展示接口，产品语义上允许用户
 * 翻页浏览公共目录，但不能因为复用普通列表三件套而把 keyword/name/search/kw 搜索能力暴露给 PUBLIC
 * 场景。PUBLIC + keyword 会把首页展示能力扩大为公开探测入口。</p>
 */
class HomeInfoPublicPageLimitContractTest {

    @AfterEach
    void tearDown() {
        UserPermissionContext.CURRENT_ORG_ID.remove();
        UserPermissionContext.CURRENT_TOKEN.remove();
    }

    @Test
    void thirdBatchPageLimitOnlyTools_shouldExposeOnlyPageAndLimitSpecsWithoutKeywordOrSearchAliases() {
        assertPageLimitOnlySpecs(new NodeQueryTool(null), "node_query");
        assertPageLimitOnlySpecs(new FileVolumePathTool(null), "file_volume_path");
        assertPageLimitOnlySpecs(new FileStorageOptionTool(null), "file_storage_option");
        assertPageLimitOnlySpecs(new ImageRepositoryTool(null), "image_repository");
        assertPageLimitOnlySpecs(new BareMetalTemplateTool(null), "bare_metal_template");
    }

    @Test
    void thirdBatchPageLimitOnlyTools_shouldPassThroughPageAndLimitButNeverKeyword() {
        assertPageLimitPassThroughWithOrg(NodeQueryTool::new, "/api/100001/node");
        assertPageLimitPassThroughWithOrg(FileVolumePathTool::new, "/api/100001/file/volume-path");
        assertPageLimitPassThroughWithOrg(FileStorageOptionTool::new, "/api/100001/file/storage/option");
        assertPageLimitPassThroughWithOrg(ImageRepositoryTool::new, "/api/100001/image/repository");
        assertPageLimitPassThrough(BareMetalTemplateTool::new, "/api/bare-metal-config-template");
    }

    @Test
    void thirdBatchPageLimitOnlyTools_shouldRejectLimitGreaterThan100BeforeHttpCall() {
        assertRejectLimitTooLargeWithOrg(NodeQueryTool::new, "/api/100001/node");
        assertRejectLimitTooLargeWithOrg(FileVolumePathTool::new, "/api/100001/file/volume-path");
        assertRejectLimitTooLargeWithOrg(FileStorageOptionTool::new, "/api/100001/file/storage/option");
        assertRejectLimitTooLargeWithOrg(ImageRepositoryTool::new, "/api/100001/image/repository");
        assertRejectLimitTooLarge(BareMetalTemplateTool::new, "/api/bare-metal-config-template");
    }

    @Test
    void homeInfoTools_shouldExposeOnlyPageAndLimitSpecsWithoutKeywordOrSearchAliases() {
        assertPageLimitOnlySpecs(new HomeIndustryClassListTool(null), "home_industry_class_list");
        assertPageLimitOnlySpecs(new HomeIndustryListTool(null), "home_industry_list");
        assertPageLimitOnlySpecs(new HomeModelListTool(null), "home_model_list");
        assertPageLimitOnlySpecs(new HomeNimListTool(null), "home_nim_list");
        assertPageLimitOnlySpecs(new HomeRepositoryListTool(null), "home_repository_list");
    }

    @Test
    void homeInfoTools_shouldPassThroughPageAndLimitButNeverKeyword() {
        assertPageLimitPassThrough(HomeIndustryClassListTool::new,
            "/api/public/home-info/industry-classification");
        assertPageLimitPassThrough(HomeIndustryListTool::new,
            "/api/public/home-info/industry-solutions");
        assertPageLimitPassThrough(HomeModelListTool::new,
            "/api/public/home-info/model-list");
        assertPageLimitPassThrough(HomeNimListTool::new,
            "/api/public/home-info/nim");
        assertPageLimitPassThrough(HomeRepositoryListTool::new,
            "/api/public/home-info/repository");
    }

    @Test
    void homeInfoTools_shouldRejectLimitGreaterThan100BeforeHttpCall() {
        assertRejectLimitTooLarge(HomeIndustryClassListTool::new,
            "/api/public/home-info/industry-classification");
        assertRejectLimitTooLarge(HomeIndustryListTool::new,
            "/api/public/home-info/industry-solutions");
        assertRejectLimitTooLarge(HomeModelListTool::new,
            "/api/public/home-info/model-list");
        assertRejectLimitTooLarge(HomeNimListTool::new,
            "/api/public/home-info/nim");
        assertRejectLimitTooLarge(HomeRepositoryListTool::new,
            "/api/public/home-info/repository");
    }

    @Test
    void homeInfoTools_shouldRejectInvalidPageOrLimitBeforeHttpCall() {
        assertRejectInvalidPagination(HomeIndustryClassListTool::new,
            "/api/public/home-info/industry-classification", Map.of("page", "0", "limit", "10"),
            "VALUE_OUT_OF_RANGE");
        assertRejectInvalidPagination(HomeIndustryListTool::new,
            "/api/public/home-info/industry-solutions", Map.of("page", "1.5", "limit", "10"),
            "TYPE_MISMATCH");
        assertRejectInvalidPagination(HomeModelListTool::new,
            "/api/public/home-info/model-list", Map.of("page", "1", "limit", "0"),
            "VALUE_OUT_OF_RANGE");
        assertRejectInvalidPagination(HomeNimListTool::new,
            "/api/public/home-info/nim", Map.of("page", "1", "limit", 1.5D),
            "TYPE_MISMATCH");
    }

    private void assertPageLimitOnlySpecs(BaseTool tool, String toolName) {
        Map<String, ToolParameterSpec> specs = tool.getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertEquals(Set.of("page", "limit"), specs.keySet(),
            toolName + " 只能暴露 page/limit，不能额外暴露 keyword/name/search/kw 等搜索入口");
        assertFalse(specs.containsKey("keyword"), toolName + " 不得暴露 keyword 搜索参数");
        assertEquals(List.of("pageNo", "page_no", "current"), specs.get("page").aliases(),
            toolName + " page aliases 只能是分页语义");
        assertEquals(List.of("pageSize", "page_size", "size"), specs.get("limit").aliases(),
            toolName + " limit aliases 只能是分页语义");
        assertNoSearchAlias(specs.get("page"), toolName);
        assertNoSearchAlias(specs.get("limit"), toolName);
    }

    private void assertNoSearchAlias(ToolParameterSpec spec, String toolName) {
        assertFalse(spec.aliases().contains("keyword"), toolName + " aliases 不得包含 keyword");
        assertFalse(spec.aliases().contains("name"), toolName + " aliases 不得包含 name");
        assertFalse(spec.aliases().contains("search"), toolName + " aliases 不得包含 search");
        assertFalse(spec.aliases().contains("kw"), toolName + " aliases 不得包含 kw");
    }

    private void assertPageLimitPassThrough(ToolFactory factory, String expectedPath) {
        assertPageLimitPassThrough(factory, expectedPath, false);
    }

    private void assertPageLimitPassThroughWithOrg(ToolFactory factory, String expectedPath) {
        assertPageLimitPassThrough(factory, expectedPath, true);
    }

    private void assertPageLimitPassThrough(ToolFactory factory, String expectedPath, boolean bindOrg) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        when(httpClient.get(eq(expectedPath), anyMap())).thenReturn(Map.of("result", List.of()));

        if (bindOrg) {
            UserPermissionContext.CURRENT_ORG_ID.set("100001");
        }
        BaseTool tool = factory.create(httpClient);
        Map<String, Object> result = tool.execute(Map.of(
            "page", "2",
            "limit", "25",
            "keyword", "gpu",
            "name", "internal-model",
            "search", "hidden",
            "kw", "probe",
            "orgId", "100001",
            "organizationId", "100002"
        ));

        assertEquals(Boolean.TRUE, result.get(AtlasToolResult.KEY_SUCCESS));
        verify(httpClient).get(eq(expectedPath), eq(Map.of("page", "2", "limit", "25")));
    }

    private void assertRejectLimitTooLarge(ToolFactory factory, String expectedPath) {
        assertRejectInvalidPagination(factory, expectedPath, Map.of("page", "1", "limit", "101"),
            "VALUE_OUT_OF_RANGE");
    }

    private void assertRejectLimitTooLargeWithOrg(ToolFactory factory, String expectedPath) {
        UserPermissionContext.CURRENT_ORG_ID.set("100001");
        assertRejectInvalidPagination(factory, expectedPath, Map.of("page", "1", "limit", "101"),
            "VALUE_OUT_OF_RANGE");
    }

    private void assertRejectInvalidPagination(ToolFactory factory,
                                               String expectedPath,
                                               Map<String, Object> params,
                                               String expectedErrorCode) {
        KubeManagerHttpClient httpClient = mock(KubeManagerHttpClient.class);
        BaseTool tool = factory.create(httpClient);

        Map<String, Object> result = tool.execute(params);

        assertEquals(Boolean.FALSE, result.get(AtlasToolResult.KEY_SUCCESS));
        assertEquals(expectedErrorCode, result.get(AtlasToolResult.KEY_ERROR_CODE));
        verify(httpClient, never()).get(eq(expectedPath), anyMap());
    }

    @FunctionalInterface
    private interface ToolFactory {
        BaseTool create(KubeManagerHttpClient httpClient);
    }
}
