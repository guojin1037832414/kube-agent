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
 * 查询MIG配置列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "mig_config_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/migConfig</p>
 */
@Component
@AtlasToolMapping(
    name = "mig_config_list",
    agent = "query",
    intentId = "mig_config_list",
    description = "查询MIG配置列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class MigConfigListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public MigConfigListTool(KubeManagerHttpClient httpClient) {
        super("mig_config_list", "查询MIG配置列表");
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
            String path = "/api/" + orgId + "/migConfig";

            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询MIG配置列表完成", data);
        } catch (Exception e) {
            log.error("[mig_config_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询MIG配置列表失败: " + e.getMessage());
        }
    }
}
