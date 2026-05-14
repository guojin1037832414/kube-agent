package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

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

    public ResourceMonitorTool() {
        super("resource_monitor", "资源监控查询(CPU/内存/存储)");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[resource_monitor] 执行资源监控查询(CPU/内存/存储)");
        String metric = params.get("metricType") != null ? params.get("metricType").toString() : "all";
                String timeRange = params.get("timeRange") != null ? params.get("timeRange").toString() : "1h";
                Map<String, Object> data = Map.of(
                    "metricType", metric,
                    "timeRange", timeRange,
                    "cpu", Map.of("usage", "65.6%", "trend", "stable", "peak", "89%"),
                    "memory", Map.of("usage", "70.3%", "trend", "rising", "peak", "85%"),
                    "storage", Map.of("usage", "45.2%", "trend", "stable", "peak", "52%")
                );
                String summary = "资源监控查询完成";
                return AtlasToolResult.ok(summary, data);
    }
}
