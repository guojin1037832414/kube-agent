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
 * 查询货币列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "currency_query_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/currency</p>
 */
@Component
@AtlasToolMapping(
    name = "currency_query_list",
    agent = "query",
    intentId = "currency_query_list",
    description = "查询货币列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/currency"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class CurrencyQueryListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public CurrencyQueryListTool(KubeManagerHttpClient httpClient) {
        super("currency_query_list", "查询货币列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 向 ReAct/LLM 暴露货币列表的标准分页和关键词参数契约。
     *
     * <p>货币列表属于账务域中的低敏元数据查询，本阶段仅允许按货币名称、
     * 编码或关键词做保守筛选；订单和审批类敏感列表仍保持 HOLD。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("货币名称、币种代码或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/currency".replace("{orgId}", orgId);

            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询货币列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[currency_query_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询货币列表失败: " + e.getMessage());
        }
    }
}
