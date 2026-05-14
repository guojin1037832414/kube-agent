package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询单个节点详情 Tool。
 *
 * <p>意图映射: {@code intentId = "node_detail"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "node_detail",
    agent = "query",
    intentId = "node_detail",
    description = "查询单个节点详情"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class NodeDetailTool extends BaseTool {

    public NodeDetailTool() {
        super("node_detail", "查询单个节点详情");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("nodeName");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[node_detail] 执行查询单个节点详情");
        String nodeName = (String) params.get("nodeName");
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("name", nodeName);
        node.put("status", "Ready");
        node.put("role", "worker");
        node.put("cpu", "8c");
        node.put("mem", "32Gi");
        node.put("pods", 12);
        node.put("age", "45d");
        node.put("conditions", List.of(
            Map.of("type", "Ready", "status", "True"),
            Map.of("type", "MemoryPressure", "status", "False")
        ));
        String summary = "节点 " + nodeName + " 详情查询完成";
        return AtlasToolResult.ok(summary, node);
    }
}
