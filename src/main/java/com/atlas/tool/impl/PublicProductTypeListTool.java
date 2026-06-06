package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询公开产品分类 Tool，用于部署前理解可购买/可租赁的产品目录。
 */
@Component
@AtlasToolMapping(
    name = "public_product_type_list",
    agent = "query",
    intentId = "public_product_type_list",
    description = "查询公开产品分类",
    httpMethod = "GET",
    apiEndpoints = {"/api/public/product/type"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class PublicProductTypeListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PublicProductTypeListTool(KubeManagerHttpClient httpClient) {
        super("public_product_type_list", "查询公开产品分类");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            Map<String, Object> response = httpClient.get("/api/public/product/type", Map.of());
            return AtlasToolResult.ok("查询公开产品分类完成", extractData(response));
        } catch (Exception e) {
            log.error("[public_product_type_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询公开产品分类失败: " + e.getMessage());
        }
    }
}
