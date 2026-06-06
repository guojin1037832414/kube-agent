package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询公开服务器产品 Tool，用于 GPU/CPU/内存/磁盘租赁规格分析。
 */
@Component
@AtlasToolMapping(
    name = "public_server_product_list",
    agent = "query",
    intentId = "public_server_product_list",
    description = "查询公开服务器产品",
    httpMethod = "GET",
    apiEndpoints = {"/api/public/product/server"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class PublicServerProductListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PublicServerProductListTool(KubeManagerHttpClient httpClient) {
        super("public_server_product_list", "查询公开服务器产品");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return SaleProductQuerySupport.productListSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            Map<String, Object> response = httpClient.get(
                "/api/public/product/server",
                SaleProductQuerySupport.buildProductQuery(params)
            );
            return AtlasToolResult.ok("查询公开服务器产品完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[public_server_product_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询公开服务器产品失败: " + e.getMessage());
        }
    }
}
