package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
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

    /**
     * 日志查询参数契约。
     *
     * <p>当前后端日志接口以 keyword 查询 Pod/日志关键字，因此 canonical 仍使用
     * 现有 Tool 实际读取的 {@code podName}，避免 schema-first 后字段名与执行逻辑脱节。
     * aliases 仅用于兼容历史 LLM 输出，不会在 ReAct 工具目录中主动展示。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "podName",
                "要查询日志的 Pod 名称或明确日志目标。不要填写 Deployment、Node、Namespace 名称。",
                false,
                List.of("pod_name", "pod", "targetName", "target_name", "keyword")
            ),
            ToolParameterSpec.stringParam(
                "namespace",
                "Pod 所在命名空间，例如 default、prod。",
                false,
                List.of("name_space", "ns", "namespaceName")
            ),
            new ToolParameterSpec(
                "lines",
                "integer",
                "返回最近多少行日志，例如 50、100、200。未指定时由后端默认处理。",
                false,
                List.of("line", "tail", "tailLines", "limit")
            )
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
