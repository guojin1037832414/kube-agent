package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.annotation.WithDefaults;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * {资源}{操作} Tool — {一句话描述}。
 *
 * <p>意图映射: {@code intentId = "{intentId}"}</p>
 * <p>API 路径：{GET|POST} /api/{orgId}/{resource}</p>
 * <p>Agent归属: {agent} | 安全级别: {P0/P1}</p>
 */
@Component
@AtlasToolMapping(
    name        = "{tool_name}",          // 全局唯一，英文+下划线
    agent       = "{agent}",               // query / diag / deploy / rbac / storage / network
    intentId    = "{intentId}",            // 对应 intents.yml 中的 intentId
    description = "{给LLM看的描述}"
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)   // PUBLIC / AUTHENTICATED / ADMIN_ONLY
@WithDefaults(intentId = "{intentId}")                   // 【可选】仅创建/含默认值类 Tool 需要
public class {Resource}{Action}Tool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public {Resource}{Action}Tool(KubeManagerHttpClient httpClient) {
        super("{tool_name}", "{给LLM看的描述}");
        this.httpClient = httpClient;
    }

    // ═══════════════════════════════════════════
    // 参数定义
    // ═══════════════════════════════════════════

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");   // 空集合 Set.of() 表示无必填
    }

    /** 【可选】需要自动类型转换的参数声明 */
    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("cpuLimits", Integer.class),
            Map.entry("memLimits", Integer.class),
            Map.entry("replicas", Integer.class),
            Map.entry("enableWebSsh", Boolean.class)
        );
    }

    // ═══════════════════════════════════════════
    // 业务执行体
    // ═══════════════════════════════════════════

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        // 1. 取参（安全读取 + 默认值兜底）
        String name = getParam(params, "name", "").trim();
        if (name.isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: name", "MISSING_NAME",
                List.of("请提供名称，例如: my-app"));
        }

        int cpu = getIntParam(params, "cpuLimits", 2);
        int mem = getIntParam(params, "memLimits", 8);
        boolean ssh = getBoolParam(params, "enableWebSsh", true);

        log.info("[{tool_name}] 执行业务 name={}, cpu={}, mem={}", name, cpu, mem);

        // 2. 调用后端 API
        try {
            String orgId = organizationId(params);
            String path = "/api/" + orgId + "/{resource}";

            // 【查询类】httpClient.get(path, queryParams);
            // 【创建类】httpClient.post(path, body);
            Map<String, Object> response = httpClient.post(path, buildBody(params, name, cpu, mem, ssh));
            Object data = response.containsKey("result") ? response.get("result") : response;

            String summary = String.format("操作成功: %s (CPU:%d, MEM:%d)", name, cpu, mem);
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[{tool_name}] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("操作失败: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════
    // 辅助方法
    // ═══════════════════════════════════════════

    private Map<String, Object> buildBody(Map<String, Object> params,
                                          String name, int cpu, int mem, boolean ssh) {
        Map<String, Object> body = filterNullParams(params);
        body.put("name", name);
        body.put("cpuLimits", cpu);
        body.put("memLimits", mem);
        body.put("enableWebSsh", ssh);
        return body;
    }

    private Map<String, Object> filterNullParams(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        params.forEach((key, value) -> { if (value != null) body.put(key, value); });
        return body;
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString().trim()); }
            catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }

    private boolean getBoolParam(Map<String, Object> params, String key, boolean defaultVal) {
        Object v = params.get(key);
        if (v instanceof Boolean b) return b;
        if (v != null) return "true".equalsIgnoreCase(v.toString().trim());
        return defaultVal;
    }
}
