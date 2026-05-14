package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询GPU使用情况 Tool。
 *
 * <p>意图映射: {@code intentId = "gpu_query"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "gpu_query",
    agent = "query",
    intentId = "gpu_query",
    description = "查询GPU使用情况"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class GpuQueryTool extends BaseTool {

    public GpuQueryTool() {
        super("gpu_query", "查询GPU使用情况");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[gpu_query] 执行查询GPU使用情况");
        List<Map<String, Object>> items = new ArrayList<>();
                items.add(Map.of("name", "gpu-0", "type", "NVIDIA A100", "utilization", "78%", "memUsed", "32Gi", "memTotal", "80Gi", "status", "Active"));
                items.add(Map.of("name", "gpu-1", "type", "NVIDIA A100", "utilization", "45%", "memUsed", "20Gi", "memTotal", "80Gi", "status", "Active"));
                items.add(Map.of("name", "gpu-2", "type", "NVIDIA V100", "utilization", "0%", "memUsed", "0Gi", "memTotal", "32Gi", "status", "Idle"));
        
                Map<String, Object> data = Map.of(
                    "total", items.size(),
                    "list", items
                );
        
                String summary = "查询完成, 共 " + items.size() + " 条记录";
                return AtlasToolResult.ok(summary, data);
    }
}
