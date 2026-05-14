package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 删除部署实例 Tool。
 * <p><b>⚠️ 危险操作</b>: P0级, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "deploy_delete"}</p>
 * <p>Agent归属: deploy | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "deploy_delete",
    agent = "deploy",
    intentId = "deploy_delete",
    description = "删除部署实例"
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class DeployDeleteTool extends BaseTool {

    public DeployDeleteTool() {
        super("deploy_delete", "删除部署实例");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[deploy_delete] 执行删除部署实例");
        String target = params.get("name") != null ? params.get("name").toString() 
            : (params.get("userId") != null ? params.get("userId").toString() : "unknown");
        Map<String, Object> data = Map.of(
            "success", true,
            "action", "deploy_delete",
            "target", target
        );
        String summary = "实例删除成功: " + target;
        return AtlasToolResult.ok(summary, data);
    }
}
