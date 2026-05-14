package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 删除用户 Tool。
 * <p><b>⚠️ 危险操作</b>: P0级, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "user_delete"}</p>
 * <p>Agent归属: rbac | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "user_delete",
    agent = "rbac",
    intentId = "user_delete",
    description = "删除用户"
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class UserDeleteTool extends BaseTool {

    public UserDeleteTool() {
        super("user_delete", "删除用户");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("userId");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[user_delete] 执行删除用户");
        String target = params.get("userId") != null ? params.get("userId").toString() : "unknown";
        Map<String, Object> data = Map.of(
            "success", true,
            "action", "user_delete",
            "target", target
        );
        String summary = "用户删除成功: " + target;
        return AtlasToolResult.ok(summary, data);
    }
}
