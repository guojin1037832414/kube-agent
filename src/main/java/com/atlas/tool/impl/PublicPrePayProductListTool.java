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
 * 查询公开预付费产品 Tool，用于对比租赁/购买前的产品规格和价格。
 */
@Component
@AtlasToolMapping(
    name = "public_pre_pay_product_list",
    agent = "query",
    intentId = "public_pre_pay_product_list",
    description = "查询公开预付费产品",
    httpMethod = "GET",
    apiEndpoints = {"/api/public/product/pre-pay"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class PublicPrePayProductListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PublicPrePayProductListTool(KubeManagerHttpClient httpClient) {
        super("public_pre_pay_product_list", "查询公开预付费产品");
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
                "/api/public/product/pre-pay",
                SaleProductQuerySupport.buildProductQuery(params)
            );
            return AtlasToolResult.ok("查询公开预付费产品完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[public_pre_pay_product_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询公开预付费产品失败: " + e.getMessage());
        }
    }
}
