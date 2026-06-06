package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询 EasyFlow 实例最新日志 Tool。
 *
 * <p>日志可能包含训练参数、路径或错误堆栈，按敏感读取处理并要求 HITL 确认。</p>
 */
@Component
@AtlasToolMapping(
    name = "easy_flow_instance_log",
    agent = "diag",
    intentId = "easy_flow_instance_log",
    description = "查询 EasyFlow 实例指定阶段的最新日志",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class EasyFlowInstanceLogTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public EasyFlowInstanceLogTool(KubeManagerHttpClient httpClient) {
        super("easy_flow_instance_log", "查询 EasyFlow 实例指定阶段的最新日志");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("instanceId", "stageCode");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("instanceId", String.class),
            Map.entry("stageCode", String.class),
            Map.entry("limitBytes", Integer.class),
            Map.entry("sinceSeconds", Integer.class),
            Map.entry("tailLines", Integer.class),
            Map.entry("timestamps", Boolean.class)
        );
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return EasyFlowLogToolSupport.logParameterSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = EasyFlowLogToolSupport.logPath(resolveOrganizationId(params), params);
            Map<String, Object> response = httpClient.get(path, EasyFlowLogToolSupport.logQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("EasyFlow 实例日志查询完成", data);
        } catch (IllegalArgumentException e) {
            log.warn("[easy_flow_instance_log] 参数校验失败: {}", e.getMessage());
            return AtlasToolResult.fail("EasyFlow 实例日志查询失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("[easy_flow_instance_log] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("EasyFlow 实例日志查询失败: " + e.getMessage());
        }
    }
}

final class EasyFlowLogToolSupport {

    private EasyFlowLogToolSupport() {
    }

    static List<ToolParameterSpec> logParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("instanceId", "EasyFlow 实例 ID。", true,
                List.of("id", "flowInstanceId", "instance_id")),
            ToolParameterSpec.stringParam("stageCode", "流程阶段编码，例如 train、test、infer、stage1。", true,
                List.of("stage", "stage_code", "phase")),
            new ToolParameterSpec("tailLines", "integer", "返回尾部日志行数，例如 50、100、200。", false,
                List.of("lines", "tail", "limit")),
            new ToolParameterSpec("limitBytes", "integer", "日志字节上限，避免一次读取过大的训练日志。", false,
                List.of("bytes", "maxBytes")),
            new ToolParameterSpec("sinceSeconds", "integer", "只查看最近多少秒内的日志。", false,
                List.of("since", "recentSeconds")),
            new ToolParameterSpec("timestamps", "boolean", "是否让 kube-manager 返回日志时间戳。", false,
                List.of("withTimestamps", "time"))
        );
    }

    static String logPath(String orgId, Map<String, Object> params) {
        return "/api/{orgId}/easy-flow/instance/{instanceId}/log/{stageCode}"
            .replace("{orgId}", orgId)
            .replace("{instanceId}", instanceId(params))
            .replace("{stageCode}", stageCode(params));
    }

    static String instanceId(Map<String, Object> params) {
        return pathSegment(params, "instanceId", "\\d+", "EasyFlow 实例 ID 必须是数字");
    }

    static String flowId(Map<String, Object> params) {
        return pathSegment(params, "flowId", "\\d+", "EasyFlow 流程 ID 必须是数字");
    }

    static String stageId(Map<String, Object> params) {
        return pathSegment(params, "stageId", "\\d+", "EasyFlow 阶段 ID 必须是数字");
    }

    static String stageCode(Map<String, Object> params) {
        return pathSegment(params, "stageCode", "[A-Za-z0-9_.-]+", "EasyFlow 阶段编码只能包含字母、数字、下划线、点和短横线");
    }

    static String positivePageOrDefault(Object raw, String key, int defaultValue) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return String.valueOf(defaultValue);
        }
        try {
            int parsed = Integer.parseInt(String.valueOf(raw).trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException("参数 '" + key + "' 必须大于 0，当前值: " + parsed);
            }
            return String.valueOf(parsed);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("参数 '" + key + "' 期望整数，但收到: " + raw);
        }
    }

    private static String pathSegment(Map<String, Object> params, String key, String pattern, String message) {
        String value = String.valueOf(params.get(key)).trim();
        // path variable 必须是单个安全片段，避免用户输入把请求导向非预期 kube-manager 路径。
        if (!value.matches(pattern)) {
            throw new IllegalArgumentException(message + ": " + value);
        }
        return value;
    }

    static Map<String, Object> logQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        putIfPresent(query, params, "limitBytes");
        putIfPresent(query, params, "sinceSeconds");
        putIfPresent(query, params, "tailLines");
        putIfPresent(query, params, "timestamps");
        return query.isEmpty() ? null : query;
    }

    static void putIfPresent(Map<String, Object> query, Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value != null) {
            query.put(key, value);
        }
    }
}
