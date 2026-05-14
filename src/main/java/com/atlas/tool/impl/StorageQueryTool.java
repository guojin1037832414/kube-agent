package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询存储卷 Tool。
 *
 * <p>意图映射: {@code intentId = "storage_query"}</p>
 * <p>Agent归属: storage | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "storage_query",
    agent = "storage",
    intentId = "storage_query",
    description = "查询存储卷"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class StorageQueryTool extends BaseTool {

    public StorageQueryTool() {
        super("storage_query", "查询存储卷");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[storage_query] 执行查询存储卷");
        List<Map<String, Object>> items = new ArrayList<>();
                items.add(Map.of("name", "pvc-data-1", "namespace", "default", "size", "10Gi", "status", "Bound", "storageClass", "default"));
                items.add(Map.of("name", "pvc-model-1", "namespace", "ml", "size", "100Gi", "status", "Bound", "storageClass", "fast-ssd"));
                items.add(Map.of("name", "pvc-log-1", "namespace", "default", "size", "50Gi", "status", "Pending", "storageClass", "default"));
        
                Map<String, Object> data = Map.of(
                    "total", items.size(),
                    "list", items
                );
        
                String summary = "查询完成, 共 " + items.size() + " 条记录";
                return AtlasToolResult.ok(summary, data);
    }
}
