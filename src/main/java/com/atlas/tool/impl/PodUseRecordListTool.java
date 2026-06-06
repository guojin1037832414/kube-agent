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
 * 查询容器使用记录 Tool，用于成本、资源占用和历史运行分析。
 *
 * <p>意图映射: {@code intentId = "pod_use_record_list"}</p>
 * <p>Agent 归属: query | 安全级别: P2 敏感读取</p>
 * <p>API 路径: GET /api/{orgId}/pod-use/record</p>
 */
@Component
@AtlasToolMapping(
    name = "pod_use_record_list",
    agent = "query",
    intentId = "pod_use_record_list",
    description = "查询容器使用记录",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/pod-use/record"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class PodUseRecordListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PodUseRecordListTool(KubeManagerHttpClient httpClient) {
        super("pod_use_record_list", "查询容器使用记录");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return FinancialAnalysisQuerySupport.podUseRecordSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/pod-use/record";
            Map<String, Object> response = httpClient.get(path, FinancialAnalysisQuerySupport.buildPodUseRecordQuery(params));
            return AtlasToolResult.ok("查询容器使用记录完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[pod_use_record_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询容器使用记录失败: " + e.getMessage());
        }
    }
}
