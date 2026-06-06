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
 * 查询 Pod 使用账单 Tool，用于按应用汇总扣费与运行成本。
 *
 * <p>意图映射: {@code intentId = "pod_use_bill_list"}</p>
 * <p>Agent 归属: query | 安全级别: P2 敏感读取</p>
 * <p>API 路径: GET /api/{orgId}/pod-use/bill</p>
 */
@Component
@AtlasToolMapping(
    name = "pod_use_bill_list",
    agent = "query",
    intentId = "pod_use_bill_list",
    description = "查询 Pod 使用账单",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/pod-use/bill"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class PodUseBillListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PodUseBillListTool(KubeManagerHttpClient httpClient) {
        super("pod_use_bill_list", "查询 Pod 使用账单");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return FinancialAnalysisQuerySupport.podUseBillSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/pod-use/bill";
            Map<String, Object> response = httpClient.get(path, FinancialAnalysisQuerySupport.buildPodUseBillQuery(params));
            return AtlasToolResult.ok("查询 Pod 使用账单完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[pod_use_bill_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询 Pod 使用账单失败: " + e.getMessage());
        }
    }
}
