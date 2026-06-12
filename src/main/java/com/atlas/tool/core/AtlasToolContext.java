package com.atlas.tool.core;

import java.util.Map;

/**
 * Atlas 上下文 — 透传给每个 Tool 的调用上下文。
 *
 * <p>中文说明：这是早期 Spring AI {@code ToolContext} 适配模型，用来把用户、命名空间、
 * 认证令牌和少量扩展字段投影成底层 Map。它主要服务兼容路径和教学理解：让学习者看到
 * “上下文对象”和“Tool 入参 Map”是两层不同的概念。</p>
 *
 * <p>安全边界：当前生产安全链路不应把这里的 {@code authToken} 或 extras 当作最终权威。
 * token/orgId/userId 必须来自认证链路、{@link com.atlas.auth.UserPermissionContext} 或
 * {@link com.atlas.auth.AgentPrincipal} 这类服务端可信上下文；本 record 不能从前端、
 * LLM、PlanStep 或 MCP 参数里恢复权限，也不能写审计、记忆或 RAG。</p>
 *
 * <p>通过 {@link org.springframework.ai.chat.model.ToolContext} 传递，
 * Spring AI 会在调用 {@code ToolCallback.call(String, ToolContext)} 时自动注入。</p>
 *
 * <p>AtlasOrchestrator 每次调用前设置：</p>
 * <pre>{@code
 *   chatClient.prompt()
 *       .user(query)
 *       .toolContext(Map.of(
 *           "userId",    userId,
 *           "namespace", "default",
 *           "authToken", token
 *       ))
 *       .call();
 * }</pre>
 */
public record AtlasToolContext(
    String userId,
    String namespace,
    String authToken,
    Map<String, Object> extras
) {

    /**
     * 转换为 Spring AI ToolContext 底层 Map。
     *
     * <p>中文说明：输出 Map 只用于框架适配和兼容，不是给 LLM 生成控制平面字段的模板。
     * extras 可能包含调用方传来的普通扩展字段，因此后续执行层仍要做受保护字段过滤。</p>
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (userId != null)    map.put("userId", userId);
        if (namespace != null) map.put("namespace", namespace);
        if (authToken != null) map.put("authToken", authToken);
        if (extras != null)    map.putAll(extras);
        return map;
    }

    public static AtlasToolContext empty() {
        return new AtlasToolContext(null, null, null, Map.of());
    }
}
