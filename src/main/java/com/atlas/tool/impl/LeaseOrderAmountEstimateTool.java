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
 * 预估租赁订单金额 Tool，只计算报价，不创建订单。
 *
 * <p>该接口对齐成熟前端购买页的 `getOrderAmount`，只允许传入服务器配置 ID 和起止时间。
 * 真正提交订单属于写操作，必须另走 HITL，不在本 Tool 范围内。</p>
 */
@Component
@AtlasToolMapping(
    name = "lease_order_amount_estimate",
    agent = "query",
    intentId = "lease_order_amount_estimate",
    description = "预估租赁订单金额，不创建订单",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/lease/order/count"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class LeaseOrderAmountEstimateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public LeaseOrderAmountEstimateTool(KubeManagerHttpClient httpClient) {
        super("lease_order_amount_estimate", "预估租赁订单金额，不创建订单");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id", "startTime", "endTime");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return SaleProductQuerySupport.orderCountSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/" + resolveOrganizationId(params) + "/lease/order/count";
            Map<String, Object> response = httpClient.get(path, SaleProductQuerySupport.buildOrderCountQuery(params));
            return AtlasToolResult.ok("预估租赁订单金额完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[lease_order_amount_estimate] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("预估租赁订单金额失败: " + e.getMessage());
        }
    }
}
