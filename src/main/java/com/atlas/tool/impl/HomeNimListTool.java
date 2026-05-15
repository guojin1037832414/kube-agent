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
 * 查询首页NIM服务列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "home_nim_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/public/home-info/nim</p>
 */
@Component
@AtlasToolMapping(
    name = "home_nim_list",
    agent = "query",
    intentId = "home_nim_list",
    description = "查询首页NIM服务列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class HomeNimListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HomeNimListTool(KubeManagerHttpClient httpClient) {
        super("home_nim_list", "查询首页NIM服务列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/public/home-info/nim";

            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("查询首页NIM服务列表完成", data);
        } catch (Exception e) {
            log.error("[home_nim_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询首页NIM服务列表失败: " + e.getMessage());
        }
    }

}
