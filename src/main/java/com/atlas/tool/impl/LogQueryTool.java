package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    private final KubeManagerHttpClient httpClient;

    public LogQueryTool(KubeManagerHttpClient httpClient) {
        super("log_query", "查询日志");
        this.httpClient = httpClient;
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
        try {
            log.info("[log_query] 执行查询日志");
            Map<String, Object> query = new HashMap<>();
            if (params.get("podName") != null) {
                query.put("keyword", params.get("podName"));
            }
            params.forEach((key, value) -> {
                if (value != null) {
                    query.putIfAbsent(key, value);
                }
            });

            Map<String, Object> response = httpClient.get("/api/log", query.isEmpty() ? null : query);
            Object data = extractData(response);
            return AtlasToolResult.ok("日志查询完成", data);
        } catch (Exception e) {
            log.error("[log_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("日志查询失败: " + e.getMessage());
        }
    }
}
