package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询组织内产品分类配置。
 *
 * <p>成熟前端云产品/云服务器配置页使用该接口管理产品分类。该数据属于组织内商品配置，
 * 不等同于公开产品分类，因此按敏感读取处理。保存和删除分类属于配置变更，继续 HOLD。</p>
 */
@Component
@AtlasToolMapping(
    name = "product_type_list",
    agent = "query",
    intentId = "product_type_list",
    description = "查询组织内产品分类配置，属于敏感读取",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/product/type"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ProductTypeListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ProductTypeListTool(KubeManagerHttpClient httpClient) {
        super("product_type_list", "查询组织内产品分类配置");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/product/type", Map.of());
            return AtlasToolResult.ok("组织内产品分类配置查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[product_type_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("组织内产品分类配置查询失败: " + e.getMessage());
        }
    }
}
