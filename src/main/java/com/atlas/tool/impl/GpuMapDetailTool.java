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
 * 查询GPU映射配置详情 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "gpu_map_detail"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/gpu/all/gpu-map</p>
 * <p>备注: </p>
 */
@Component
@AtlasToolMapping(
    name = "gpu_map_detail",
    agent = "query",
    intentId = "gpu_map_detail",
    description = "查询GPU映射配置详情"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class GpuMapDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public GpuMapDetailTool(KubeManagerHttpClient httpClient) {
        super("gpu_map_detail", "查询GPU映射配置详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/gpu/all/gpu-map";

            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("查询GPU映射配置详情完成", data);
        } catch (Exception e) {
            log.error("[gpu_map_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询GPU映射配置详情失败: " + e.getMessage());
        }
    }

}
