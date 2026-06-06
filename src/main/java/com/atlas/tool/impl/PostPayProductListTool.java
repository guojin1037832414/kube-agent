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
 * 查询组织内按量付费产品配置。
 *
 * <p>相比公开按量付费商品目录，该接口读取的是当前组织维护的产品配置，可能包含内部资源编码、
 * 软件组合和价格策略上下文，因此按敏感读取处理。产品保存、折扣增删改和删除继续 HOLD。</p>
 */
@Component
@AtlasToolMapping(
    name = "post_pay_product_list",
    agent = "query",
    intentId = "post_pay_product_list",
    description = "查询组织内按量付费产品配置，属于敏感读取",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/product/post-pay"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class PostPayProductListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PostPayProductListTool(KubeManagerHttpClient httpClient) {
        super("post_pay_product_list", "查询组织内按量付费产品配置");
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
            String orgId = resolveOrganizationId(params);
            Map<String, Object> query = SaleProductQuerySupport.buildProductQuery(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/product/post-pay", query);
            return AtlasToolResult.ok("组织内按量付费产品配置查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[post_pay_product_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("组织内按量付费产品配置查询失败: " + e.getMessage());
        }
    }
}
