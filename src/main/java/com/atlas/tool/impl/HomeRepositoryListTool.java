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
 * 查询首页AI应用仓库列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "home_repository_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/public/home-info/repository</p>
 */
@Component
@AtlasToolMapping(
    name = "home_repository_list",
    agent = "query",
    intentId = "home_repository_list",
    description = "查询首页AI应用仓库列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class HomeRepositoryListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HomeRepositoryListTool(KubeManagerHttpClient httpClient) {
        super("home_repository_list", "查询首页AI应用仓库列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/public/home-info/repository";

            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询首页AI应用仓库列表完成", data);
        } catch (Exception e) {
            log.error("[home_repository_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询首页AI应用仓库列表失败: " + e.getMessage());
        }
    }

}
