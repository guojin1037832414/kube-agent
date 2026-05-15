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
 * 查询全局模型列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "sys_model_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/model</p>
 */
@Component
@AtlasToolMapping(
    name = "sys_model_list",
    agent = "query",
    intentId = "sys_model_list",
    description = "查询全局模型列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class SysModelListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public SysModelListTool(KubeManagerHttpClient httpClient) {
        super("sys_model_list", "查询全局模型列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/model";

            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("查询全局模型列表完成", data);
        } catch (Exception e) {
            log.error("[sys_model_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询全局模型列表失败: " + e.getMessage());
        }
    }

}
