package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询 EasyFlow 流程列表 Tool。
 *
 * <p>流程元数据能帮助 AI 在查看实例日志前先理解流程类型、描述和阶段数量；本工具只读取元数据。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_flow_list",
    agent = "query",
    intentId = "easy_flow_flow_list",
    description = "查询 EasyFlow 流程列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/flow"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowFlowListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowFlowListTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_flow_list", "查询 EasyFlow 流程列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.of(
            "flowId", String.class,
            "type", String.class,
            "description", String.class
        );
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("page", "页码，默认使用 1。", false,
                List.of("pageNo", "page_no", "current")),
            ToolParameterSpec.stringParam("limit", "每页数量，默认使用 100。", false,
                List.of("pageSize", "page_size", "size")),
            ToolParameterSpec.stringParam("flowId", "流程 ID 精确筛选。", false,
                List.of("id", "flow_id")),
            ToolParameterSpec.stringParam("type", "流程类型筛选，例如训练、测试、推理相关类型。", false,
                List.of("flowType", "category")),
            ToolParameterSpec.stringParam("description", "流程描述关键词筛选。", false,
                List.of("keyword", "search", "kw"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/{orgId}/easy-flow/flow".replace("{orgId}", resolveOrganizationId(params));
            Map<String, Object> response = httpClient.get(path, flowQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 流程列表查询完成", data);
        } catch (IllegalArgumentException e) {
            log.warn("[easy_flow_flow_list] 参数校验失败: {}", e.getMessage());
            return AtlasToolResult.fail("EasyFlow 流程列表查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[easy_flow_flow_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 流程列表查询失败: " + e.getMessage());
        }
    }

    private Map<String, Object> flowQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", EasyFlowLogToolSupport.positivePageOrDefault(params.get("page"), "page", 1));
        query.put("limit", EasyFlowLogToolSupport.positivePageOrDefault(params.get("limit"), "limit", 100));
        putFlowIdIfPresent(query, params);
        putTrimmed(query, params, "type");
        putTrimmed(query, params, "description");
        return query;
    }

    private void putFlowIdIfPresent(Map<String, Object> query, Map<String, Object> params) {
        Object value = params.get("flowId");
        if (value != null && !String.valueOf(value).isBlank()) {
            query.put("flowId", EasyFlowLogToolSupport.flowId(params));
        }
    }

    private void putTrimmed(Map<String, Object> query, Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value != null && !String.valueOf(value).isBlank()) {
            query.put(key, String.valueOf(value).trim());
        }
    }
}
