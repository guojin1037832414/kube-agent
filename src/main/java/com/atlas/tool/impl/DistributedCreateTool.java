package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 创建分布式计算任务 Tool。
 *
 * <p>意图映射: {@code intentId = "distributed_create"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 */
@Component
@AtlasToolMapping(
    name = "distributed_create",
    agent = "deploy",
    intentId = "distributed_create",
    description = "创建分布式计算任务"
)

@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class DistributedCreateTool extends BaseTool {

    public DistributedCreateTool() {
        super("distributed_create", "创建分布式计算任务");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[distributed_create] 执行创建分布式计算任务");
        String createdName = params.get("name") != null ? params.get("name").toString() : "unknown";
                Map<String, Object> data = Map.of(
                    "success", true,
                    "createdName", createdName,
                    "action", "distributed_create",
                    "status", "Created",
                    "message", "创建任务已提交, 请稍候确认状态"
                );
                String summary = "创建任务 '" + createdName + "' 已提交";
                return AtlasToolResult.ok(summary, data);
    }
}
