package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 集群概览/运营看板 Tool。
 *
 * <p>意图映射: {@code intentId = "cluster_overview"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "cluster_overview",
    agent = "query",
    intentId = "cluster_overview",
    description = "集群概览/运营看板"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ClusterOverviewTool extends BaseTool {

    public ClusterOverviewTool() {
        super("cluster_overview", "集群概览/运营看板");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[cluster_overview] 执行集群概览/运营看板");
        Map<String, Object> data = Map.of(
                    "clusterName", "prod-cluster-1",
                    "status", "Healthy",
                    "nodes", Map.of("total", 5, "ready", 4, "notReady", 1),
                    "pods", Map.of("total", 128, "running", 120, "pending", 5, "failed", 3),
                    "resources", Map.of(
                        "cpu", Map.of("total", "64c", "used", "42c", "usage", "65.6%"),
                        "memory", Map.of("total", "256Gi", "used", "180Gi", "usage", "70.3%"),
                        "gpu", Map.of("total", 8, "used", 6, "usage", "75%")
                    ),
                    "alerts", List.of(
                        Map.of("level", "warning", "message", "node-4 NotReady", "time", "10m ago")
                    )
                );
                String summary = "集群概览查询完成, 当前集群状态: Healthy";
                return AtlasToolResult.ok(summary, data);
    }
}
