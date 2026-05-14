package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询用户列表 Tool。
 *
 * <p>意图映射: {@code intentId = "user_query"}</p>
 * <p>Agent归属: rbac | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "user_query",
    agent = "rbac",
    intentId = "user_query",
    description = "查询用户列表"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class UserQueryTool extends BaseTool {

    public UserQueryTool() {
        super("user_query", "查询用户列表");
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
        log.info("[user_query] 执行查询用户列表");
        List<Map<String, Object>> items = new ArrayList<>();
                items.add(Map.of("id", "1", "username", "admin", "role", "admin", "status", "active", "createTime", "2026-01-01"));
                items.add(Map.of("id", "2", "username", "zhaotiandi", "role", "user", "status", "active", "createTime", "2026-03-15"));
                items.add(Map.of("id", "3", "username", "developer1", "role", "user", "status", "inactive", "createTime", "2026-04-20"));
        
                Map<String, Object> data = Map.of(
                    "total", items.size(),
                    "list", items
                );
        
                String summary = "查询完成, 共 " + items.size() + " 条记录";
                return AtlasToolResult.ok(summary, data);
    }
}
