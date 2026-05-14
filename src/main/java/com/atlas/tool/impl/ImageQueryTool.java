package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询镜像资源列表 Tool。
 *
 * <p>意图映射: {@code intentId = "image_query"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "image_query",
    agent = "query",
    intentId = "image_query",
    description = "查询镜像资源列表"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ImageQueryTool extends BaseTool {

    public ImageQueryTool() {
        super("image_query", "查询镜像资源列表");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("page", Integer.class),
            Map.entry("pageSize", Integer.class)
        );
    }
    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[image_query] 执行查询镜像资源列表");
        List<Map<String, Object>> items = new ArrayList<>();
                items.add(Map.of("name", "ubuntu:22.04", "size", "78MB", "tags", List.of("official", "os"), "pulls", 1204500));
                items.add(Map.of("name", "nginx:latest", "size", "45MB", "tags", List.of("official", "web"), "pulls", 980000));
                items.add(Map.of("name", "busybox:latest", "size", "2MB", "tags", List.of("official", "utils"), "pulls", 560000));
                items.add(Map.of("name", "tensorflow/tensorflow:2.15", "size", "2.1GB", "tags", List.of("ml", "gpu"), "pulls", 12000));
        
                Map<String, Object> data = Map.of(
                    "total", items.size(),
                    "list", items
                );
        
                String summary = "查询完成, 共 " + items.size() + " 条记录";
                return AtlasToolResult.ok(summary, data);
    }
}
