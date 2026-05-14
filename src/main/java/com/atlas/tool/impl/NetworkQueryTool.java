package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询网络配置 Tool。
 *
 * <p>意图映射: {@code intentId = "network_query"}</p>
 * <p>Agent归属: network | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "network_query",
    agent = "network",
    intentId = "network_query",
    description = "查询网络配置"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NetworkQueryTool extends BaseTool {

    public NetworkQueryTool() {
        super("network_query", "查询网络配置");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[network_query] 执行查询网络配置");
        String ns = params.get("namespace") != null ? params.get("namespace").toString() : "all";
                Map<String, Object> data = Map.of(
                    "namespace", ns,
                    "services", List.of(
                        Map.of("name", "svc-web", "type", "ClusterIP", "clusterIP", "10.96.123.45", "ports", List.of(80, 443)),
                        Map.of("name", "svc-api", "type", "NodePort", "clusterIP", "10.96.123.46", "ports", List.of(8080))
                    ),
                    "bandwidth", Map.of("total", "10Gbps", "used", "3.2Gbps", "usage", "32%"),
                    "totalEndpoints", 24
                );
                String summary = "网络配置查询完成";
                return AtlasToolResult.ok(summary, data);
    }
}
