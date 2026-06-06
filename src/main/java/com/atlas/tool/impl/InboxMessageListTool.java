package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.exception.AtlasToolValidationException;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询消息通知列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "inbox_message_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/inbox-message</p>
 */
@Component
@AtlasToolMapping(
    name = "inbox_message_list",
    agent = "query",
    intentId = "inbox_message_list",
    description = "查询消息通知列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/inbox-message"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class InboxMessageListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public InboxMessageListTool(KubeManagerHttpClient httpClient) {
        super("inbox_message_list", "查询消息通知列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 向 ReAct/LLM 暴露标准列表查询参数契约，确保分页和关键词筛选能真实透传到 kube-manager。
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("消息标题、内容或通知类型关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/inbox-message";

            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询消息通知列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[inbox_message_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询消息通知列表失败: " + e.getMessage());
        }
    }
}
