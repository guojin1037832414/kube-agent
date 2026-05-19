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
 * 查询系统信息配置 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "sys_info_map"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/public/sys-info/all/map</p>
 */
@Component
@AtlasToolMapping(
    name = "sys_info_map",
    agent = "query",
    intentId = "sys_info_map",
    description = "查询系统信息配置"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class SysInfoMapTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public SysInfoMapTool(KubeManagerHttpClient httpClient) {
        super("sys_info_map", "查询系统信息配置");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/public/sys-info/all/map";

            Map<String, Object> response = httpClient.getWithAutoPagination(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("查询系统信息配置完成", data);
        } catch (Exception e) {
            log.error("[sys_info_map] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询系统信息配置失败: " + e.getMessage());
        }
    }
}
