package com.atlas.mcp;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.ToolRegistry;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MCP Tool Manifest 服务 — M5.20 最小安全闭环。
 *
 * <p>中文说明：这是 kube-agent 面向未来 MCP 生态的“只读能力目录生成器”，不是 MCP runtime，
 * 也不是 Tool 执行授权器。它把 ToolRegistry 的系统审计元数据筛成一个外部 Agent 可以先阅读的 Manifest。</p>
 *
 * <p>安全边界：Manifest 不是执行授权。当前服务不实现 MCP {@code tools/call}，不会调用
 * {@link com.atlas.tool.execution.SafeToolExecutor}，不会访问 kube-manager，也不会写审计或记忆。
 * 未来真正打开 MCP runtime 时，仍必须经过 SafeToolExecutor、HITL、durable audit 和租户上下文校验。</p>
 *
 * <p>该服务不是直接开放可执行 MCP Server，而是先提供一个“可安全暴露给外部 Agent 的工具清单”。
 * 设计重点是 fail-closed：只有已经完成风险元数据治理、语义为普通 READ、且不需要人工确认的工具，
 * 才会进入 MCP Manifest；敏感读取、写操作、删除、动作类、UNKNOWN 以及未声明 endpoint 的工具全部默认阻断。</p>
 *
 * <p>这样后续接入 stdio/SSE MCP Server 时，可以复用本服务作为唯一出口，避免外部 Agent 绕过
 * HITL 和 Tool 风险治理。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M5.20
 */
@Service
public class McpToolManifestService {

    private final ToolRegistry toolRegistry;

    public McpToolManifestService(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 构建 MCP 安全工具清单。
     *
     * <p>中文说明：这里使用系统审计视角读取全量 Tool，再按 MCP 导出规则过滤。
     * 统计中的 blockedTools 是治理证据，不代表这些 Tool 可以被外部系统绕过调用。</p>
     *
     * @return 包含导出策略、统计信息和安全工具列表的结构化 Manifest
     */
    public Map<String, Object> buildSafeManifest() {
        List<ToolRegistry.ToolMetadata> allTools = toolRegistry.listAllMetadataForSystemAudit();
        List<Map<String, Object>> exportedTools = allTools.stream()
            .filter(this::isExportableToMcp)
            .map(this::toManifestItem)
            .toList();

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("mode", "safe-readonly-manifest");
        policy.put("failClosed", true);
        policy.put("exportRule", "permission=PUBLIC && operationType=READ && requiresConfirmation=false && httpMethod/apiEndpoints declared");
        policy.put("blockedOperationTypes", List.of("UNKNOWN", "SENSITIVE_READ", "CREATE", "UPDATE", "DELETE", "ACTION"));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTools", allTools.size());
        stats.put("exportedTools", exportedTools.size());
        stats.put("blockedTools", Math.max(0, allTools.size() - exportedTools.size()));

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("name", "atlas-kube-agent-mcp-manifest");
        manifest.put("version", "3.1.0-M5.20");
        manifest.put("policy", policy);
        manifest.put("stats", stats);
        manifest.put("tools", exportedTools);
        return manifest;
    }

    /**
     * 判断 Tool 是否允许导出给 MCP。
     *
     * <p>安全边界：只有 PUBLIC + READ + 无 HITL + 声明 HTTP 元数据的 Tool 才能出现在 Manifest。
     * SENSITIVE_READ 即使不改变集群状态，也可能读取用户、日志、订单、权限或配额等敏感信息，因此默认不导出。</p>
     */
    public boolean isExportableToMcp(ToolRegistry.ToolMetadata metadata) {
        if (metadata == null) {
            return false;
        }
        return metadata.operationType() == AtlasToolMapping.OperationType.READ
            && metadata.permissionPolicy() == ToolPermission.Policy.PUBLIC
            && !metadata.requiresConfirmation()
            && metadata.httpMethod() != null
            && !metadata.httpMethod().isBlank()
            && !metadata.apiEndpoints().isEmpty();
    }

    /**
     * 转换成不泄露内部 endpoint 的 Manifest 项。
     *
     * <p>中文说明：Manifest 只告诉外部 Agent “这个能力存在、语义是什么、风险标签是什么”。
     * 不泄露内部 endpoint，是为了避免普通外部调用方绕过 kube-agent 治理直接探测 kube-manager API。</p>
     */
    private Map<String, Object> toManifestItem(ToolRegistry.ToolMetadata metadata) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", metadata.name());
        item.put("description", metadata.description());
        item.put("intentId", metadata.intentId());
        item.put("agent", metadata.agent());
        item.put("operationType", metadata.operationType().name());
        item.put("httpMethod", metadata.httpMethod());
        item.put("requiresConfirmation", metadata.requiresConfirmation());
        item.put("endpointDeclared", !metadata.apiEndpoints().isEmpty());
        return item;
    }
}
