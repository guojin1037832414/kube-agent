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
 * 查询公开按量付费产品 Tool，用于部署前成本和资源规格分析。
 */
@Component
@AtlasToolMapping(
    name = "public_post_pay_product_list",
    agent = "query",
    intentId = "public_post_pay_product_list",
    description = "查询公开按量付费产品",
    httpMethod = "GET",
    apiEndpoints = {"/api/public/product/post-pay"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class PublicPostPayProductListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PublicPostPayProductListTool(KubeManagerHttpClient httpClient) {
        super("public_post_pay_product_list", "查询公开按量付费产品");
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
                "/api/public/product/post-pay",
                SaleProductQuerySupport.buildProductQuery(params)
            );
            return AtlasToolResult.ok("查询公开按量付费产品完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[public_post_pay_product_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询公开按量付费产品失败: " + e.getMessage());
        }
    }
}
