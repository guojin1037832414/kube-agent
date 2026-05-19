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
 * 资源监控查询(CPU/内存/存储) Tool。
 *
 * <p>意图映射: {@code intentId = "resource_monitor"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "resource_monitor",
    agent = "query",
    intentId = "resource_monitor",
    description = "资源监控查询(CPU/内存/存储)"
)
// P3 资源监控属于只读查询，不产生集群写操作；允许公开访问，便于匿名看板和健康检查复用。
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ResourceMonitorTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ResourceMonitorTool(KubeManagerHttpClient httpClient) {
        super("resource_monitor", "资源监控查询(CPU/内存/存储)");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            log.info("[resource_monitor] 执行资源监控查询(CPU/内存/存储)");
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/resource";
            Map<String, Object> response = httpClient.get(path);
            Object data = extractData(response);
            return AtlasToolResult.ok("资源监控查询完成", data);
        } catch (Exception e) {
            log.error("[resource_monitor] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("资源监控查询失败: " + e.getMessage());
        }
    }
}
