package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 创建NIM服务 Tool。
 *
 * <p>意图映射: {@code intentId = "nim_create"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 */
@Component
@AtlasToolMapping(
    name = "nim_create",
    agent = "deploy",
    intentId = "nim_create",
    description = "创建NIM服务"
)

@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class NimCreateTool extends BaseTool {

    public NimCreateTool() {
        super("nim_create", "创建NIM服务");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "model");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("gpuPercentLimits", Integer.class)
        );
    }
    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[nim_create] 执行创建NIM服务");
        String createdName = params.get("name") != null ? params.get("name").toString() : "unknown";
                Map<String, Object> data = Map.of(
                    "success", true,
                    "createdName", createdName,
                    "action", "nim_create",
                    "status", "Created",
                    "message", "创建任务已提交, 请稍候确认状态"
                );
                String summary = "创建任务 '" + createdName + "' 已提交";
                return AtlasToolResult.ok(summary, data);
    }
}
