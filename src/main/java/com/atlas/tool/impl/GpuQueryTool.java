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
 * 查询GPU使用情况 Tool。
 *
 * <p>意图映射: {@code intentId = "gpu_query"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "gpu_query",
    agent = "query",
    intentId = "gpu_query",
    description = "查询GPU使用情况"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class GpuQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public GpuQueryTool(KubeManagerHttpClient httpClient) {
        super("gpu_query", "查询GPU使用情况");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            // M5.5 多租户安全治理：orgId 必须来自可信 ThreadLocal，禁止使用 params.organizationId。
            String orgId = resolveOrganizationId(params);

            String path = "/api/" + orgId + "/node/all/gpu-map";
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);

            return AtlasToolResult.ok("GPU配置查询完成", data);
        } catch (Exception e) {
            log.error("[gpu_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("GPU配置查询失败: " + e.getMessage());
        }
    }
}
