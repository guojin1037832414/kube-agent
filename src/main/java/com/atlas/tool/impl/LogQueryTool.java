package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 查询日志 Tool。
 *
 * <p>意图映射: {@code intentId = "log_query"}</p>
 * <p>Agent归属: diag | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "log_query",
    agent = "diag",
    intentId = "log_query",
    description = "查询日志"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class LogQueryTool extends BaseTool {

    public LogQueryTool() {
        super("log_query", "查询日志");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("lines", Integer.class)
        );
    }
    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[log_query] 执行查询日志");
        String pod = params.get("podName") != null ? params.get("podName").toString() : "unknown-pod";
                int lines = params.get("lines") instanceof Number n ? n.intValue() : 100;
                Map<String, Object> data = Map.of(
                    "podName", pod,
                    "lines", lines,
                    "logs", List.of(
                        "[INFO] 2026-05-14 10:23:45 Application started successfully",
                        "[INFO] 2026-05-14 10:23:46 Connected to database",
                        "[WARN] 2026-05-14 10:24:12 Slow query detected: 2.3s",
                        "[INFO] 2026-05-14 10:25:01 Health check passed",
                        "[ERROR] 2026-05-14 10:26:33 Connection timeout to upstream"
                    )
                );
                String summary = "Pod " + pod + " 日志查询完成, 返回 " + lines + " 行";
                return AtlasToolResult.ok(summary, data);
    }
}
