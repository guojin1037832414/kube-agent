package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询配额审批列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "quota_receive_list"}</p>
 * <p>Agent归属: rbac | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/quota/receive</p>
 */
@Component
@AtlasToolMapping(
    name = "quota_receive_list",
    agent = "rbac",
    intentId = "quota_receive_list",
    description = "查询配额审批列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class QuotaReceiveListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public QuotaReceiveListTool(KubeManagerHttpClient httpClient) {
        super("quota_receive_list", "查询配额审批列表");
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
            String path = "/api/" + orgId + "/quota/receive";

            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询配额审批列表完成", data);
        } catch (Exception e) {
            log.error("[quota_receive_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询配额审批列表失败: " + e.getMessage());
        }
    }
}
