package com.atlas.tool.core;

import java.util.Map;

/**
 * Atlas 上下文 — 透传给每个 Tool 的调用上下文。
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
