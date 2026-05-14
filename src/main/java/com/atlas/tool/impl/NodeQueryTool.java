package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 节点查询 Tool — P1 实验样本。
 *
 * <p>意图映射：{@code intentId = "node_query"}，对应 "查询所有节点状态"。</p>
 * <p>纯查询操作，无参数，最简单的端到端打通样本。</p>
 */
@Component
@AtlasToolMapping(
    name = "node_query",
    agent = "query",
    intentId = "node_query",
    description = "查询 Kubernetes 集群所有节点的状态、资源使用情况"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NodeQueryTool extends BaseTool {

    public NodeQueryTool() {
        super("node_query", "查询 Kubernetes 集群所有节点的状态、资源使用情况");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of(); // 无必填参数
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[node_query] 执行节点查询");

        // P1 阶段：Mock 数据（后续替换为 KubeManagerHttpClient 真实调用）
        List<Map<String, Object>> nodes = mockQueryNodes();

        Map<String, Object> data = Map.of(
            "total", nodes.size(),
            "list", nodes
        );

        String summary = nodes.size() > 0
            ? String.format("集群共有 %d 个节点，其中 %d 个正常，%d 个异常",
                nodes.size(),
                nodes.stream().filter(n -> "Ready".equals(n.get("status"))).count(),
                nodes.stream().filter(n -> !"Ready".equals(n.get("status"))).count())
            : "当前集群无节点";

        return AtlasToolResult.ok(summary, data);
    }

    // ── Mock 数据（P1 阶段） ────────────────────────────

    private List<Map<String, Object>> mockQueryNodes() {
        List<Map<String, Object>> nodes = new ArrayList<>();
        nodes.add(Map.of(
            "name", "node-1", "status", "Ready",
            "cpu", "8c", "mem", "32Gi", "age", "45d"
        ));
        nodes.add(Map.of(
            "name", "node-2", "status", "Ready",
            "cpu", "16c", "mem", "64Gi", "age", "30d"
        ));
        nodes.add(Map.of(
            "name", "node-3", "status", "Ready",
            "cpu", "8c", "mem", "32Gi", "age", "15d"
        ));
        nodes.add(Map.of(
            "name", "node-4", "status", "NotReady",
            "cpu", "4c", "mem", "16Gi", "age", "60d"
        ));
        nodes.add(Map.of(
            "name", "node-5", "status", "Ready",
            "cpu", "8c", "mem", "32Gi", "age", "7d"
        ));
        return nodes;
    }
}
