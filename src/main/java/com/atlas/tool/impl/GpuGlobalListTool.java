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
 * 查询全局GPU信息列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "gpu_global_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/gpu</p>
 */
@Component
@AtlasToolMapping(
    name = "gpu_global_list",
    agent = "query",
    intentId = "gpu_global_list",
    description = "查询全局GPU信息列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class GpuGlobalListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public GpuGlobalListTool(KubeManagerHttpClient httpClient) {
        super("gpu_global_list", "查询全局GPU信息列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/gpu";
            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询全局GPU信息列表完成", data);
        } catch (Exception e) {
            log.error("[gpu_global_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询全局GPU信息列表失败: " + e.getMessage());
        }
    }

}
