package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询角色列表 Tool。
 *
 * <p>意图映射: {@code intentId = "role_query"}</p>
 * <p>Agent归属: rbac | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "role_query",
    agent = "rbac",
    intentId = "role_query",
    description = "查询角色列表"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class RoleQueryTool extends BaseTool {

    public RoleQueryTool() {
        super("role_query", "查询角色列表");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[role_query] 执行查询角色列表");
        List<Map<String, Object>> items = new ArrayList<>();
                items.add(Map.of("name", "admin", "permissions", List.of("*"), "userCount", 2));
                items.add(Map.of("name", "user", "permissions", List.of("read", "write"), "userCount", 5));
                items.add(Map.of("name", "viewer", "permissions", List.of("read"), "userCount", 3));
        
                Map<String, Object> data = Map.of(
                    "total", items.size(),
                    "list", items
                );
        
                String summary = "查询完成, 共 " + items.size() + " 条记录";
                return AtlasToolResult.ok(summary, data);
    }
}
