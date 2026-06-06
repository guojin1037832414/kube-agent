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
 * 查询计费配置 Tool，用于解释账单金额与资源单价来源。
 *
 * <p>意图映射: {@code intentId = "cost_config_list"}</p>
 * <p>Agent 归属: query | 安全级别: P2 敏感读取</p>
 * <p>API 路径: GET /api/{orgId}/cost</p>
 */
@Component
@AtlasToolMapping(
    name = "cost_config_list",
    agent = "query",
    intentId = "cost_config_list",
    description = "查询计费配置",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/cost"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class CostConfigListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public CostConfigListTool(KubeManagerHttpClient httpClient) {
        super("cost_config_list", "查询计费配置");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return FinancialAnalysisQuerySupport.costConfigSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/cost";
            Map<String, Object> response = httpClient.get(path, FinancialAnalysisQuerySupport.buildCostConfigQuery(params));
            return AtlasToolResult.ok("查询计费配置完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[cost_config_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询计费配置失败: " + e.getMessage());
        }
    }
}
