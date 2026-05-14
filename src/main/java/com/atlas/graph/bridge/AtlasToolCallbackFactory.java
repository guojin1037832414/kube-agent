package com.atlas.graph.bridge;

import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AtlasToolCallback 工厂 — 按 Agent 分组批量构建 ToolCallback。
 *
 * <p>从 {@link ToolRegistry} 读取当前用户可见的 Tool，为每个 Agent
 * 生成对应的 {@link ToolCallback} 列表，供 ReactAgent 注册。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-P2
 */
@Component
public class AtlasToolCallbackFactory {

    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;

    public AtlasToolCallbackFactory(ToolRegistry toolRegistry, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
    }

    /**
     * 为指定 Agent 构建可见的 ToolCallback 列表。
     */
    public List<ToolCallback> buildForAgent(String agentCode) {
        return toolRegistry.listByAgent(agentCode).stream()
                .filter(meta -> meta.instance() instanceof BaseTool)
                .map(meta -> new AtlasToolCallback((BaseTool) meta.instance(), objectMapper))
                .collect(Collectors.toList());
    }

    /**
     * 构建所有可见 Tool 的 ToolCallback（用于 Supervisor Agent）。
     */
    public List<ToolCallback> buildAllVisible() {
        return toolRegistry.getAllTools().stream()
                .filter(tool -> toolRegistry.isVisible(tool.getToolName()))
                .map(tool -> new AtlasToolCallback(tool, objectMapper))
                .collect(Collectors.toList());
    }

    /**
     * 按 Agent 分组构建所有 ToolCallback Map。
     */
    public Map<String, List<ToolCallback>> buildAllByAgent() {
        return Map.of(
                "query", buildForAgent("query"),
                "deploy", buildForAgent("deploy"),
                "rbac", buildForAgent("rbac"),
                "storage", buildForAgent("storage"),
                "network", buildForAgent("network"),
                "diag", buildForAgent("diag")
        );
    }
}
