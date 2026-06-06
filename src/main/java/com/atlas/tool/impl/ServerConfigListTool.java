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
 * 查询组织内服务器产品配置。
 *
 * <p>成熟前端云服务器配置页使用该接口展示组织内可租赁服务器规格、库存与价格配置。
 * 这些信息不是公开商品目录，可能包含内部库存和成本配置，因此按敏感读取处理。
 * 本 Tool 只接入 GET 查询；服务器配置保存和删除继续走高风险 HOLD。</p>
 */
@Component
@AtlasToolMapping(
    name = "server_config_list",
    agent = "query",
    intentId = "server_config_list",
    description = "查询组织内服务器产品配置，属于敏感读取",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/server"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ServerConfigListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ServerConfigListTool(KubeManagerHttpClient httpClient) {
        super("server_config_list", "查询服务器产品配置");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/server", query);
            return AtlasToolResult.ok("服务器产品配置查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[server_config_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("服务器产品配置查询失败: " + e.getMessage());
        }
    }
}
