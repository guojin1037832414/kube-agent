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
 * 查询数据集列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "data_set_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/data-set</p>
 */
@Component
@AtlasToolMapping(
    name = "data_set_list",
    agent = "query",
    intentId = "data_set_list",
    description = "查询数据集列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DataSetListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DataSetListTool(KubeManagerHttpClient httpClient) {
        super("data_set_list", "查询数据集列表");
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
            String path = "/api/" + orgId + "/data-set";

            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询数据集列表完成", data);
        } catch (Exception e) {
            log.error("[data_set_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询数据集列表失败: " + e.getMessage());
        }
    }
}
