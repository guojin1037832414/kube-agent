package com.atlas.agent;

import com.atlas.tool.core.AtlasTool;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.core.ToolRegistry.ToolMetadata;
import com.atlas.tool.exception.PermissionDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 抽象基类 — 每个专业 Agent 的公共骨架。
 *
 * <p><b>职责：</b></p>
 * <ul>
 *   <li>持有自身关联的 Tool 集合（通过 ToolRegistry 按 Agent 归属过滤）</li>
 *   <li>执行用户请求前进行权限二次校验</li>
 *   <li>支持单步执行与 ReAct 多步推理两种模式（P2 扩展）</li>
 * </ul>
 *
 * <p><b>⚠️ 废弃说明：</b>P2 后由 {@link com.alibaba.cloud.ai.graph.agent.ReactAgent}
 * 替代，旧路由逻辑保留仅作向后兼容。新接口请使用 {@code /chat/graph}。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 * @deprecated 3.1.0-P2 后由 ReactAgent + StateGraph 替代
 */
@Deprecated(since = "3.1.0-P2", forRemoval = false)
public abstract class AtlasAgentBase {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final ToolRegistry toolRegistry;

    protected AtlasAgentBase(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    /**
     * 返回当前 Agent 的类型标识。
     */
    public abstract String getAgentType();

    /**
     * 返回当前 Agent 的名称（用于日志展示）。
     */
    public abstract String getAgentName();

    /**
     * 获取当前 Agent 的全部可用 Tool（已按用户权限过滤）。
     */
    public List<ToolMetadata> getAvailableTools() {
        return toolRegistry.listByAgent(getAgentType());
    }

    /**
     * 执行一次 Tool 调用。
     *
     * <p>权限二次校验：如果 LLM 强行选择了越权的 Tool，在此处拦截。</p>
     */
    public Map<String, Object> executeTool(String toolName,
                                           Map<String, Object> params) {
        // ① 前校验：Tool 是否存在
        if (toolRegistry.findByName(toolName).isEmpty()) {
            log.warn("[{}] Tool '{}' 未注册", getAgentName(), toolName);
            return gracefulDeny(toolName, "Tool未注册");
        }

        // ② 前校验：当前用户是否有权调用此 Tool
        if (!toolRegistry.isVisible(toolName)) {
            log.warn("[{}] 用户越权尝试调用 Tool '{}'", getAgentName(), toolName);
            return gracefulDeny(toolName, "权限不足：需要管理员权限");
        }

        // ③ 获取 Tool 实例（权限预检在 resolve 中也会做，双重保险）
        ToolMetadata meta;
        try {
            meta = toolRegistry.resolve(toolName);
        } catch (PermissionDeniedException e) {
            log.warn("[{}] Tool resolve 权限拒绝: {}", getAgentName(), e.getMessage());
            return gracefulDeny(toolName, e.getMessage());
        }

        // ④ 执行
        log.info("[{}] 执行 Tool: {} (intent={}, policy={})",
            getAgentName(), toolName, meta.intentId(), meta.permissionPolicy());

        return meta.instance().execute(params);
    }

    /**
     * 按意图 ID 执行对应的 Tool。
     *
     * <p>P1.4 权限感知：区分 "权限不足" 和 "该功能暂未实现" 两种错误。</p>
     */
    public Map<String, Object> executeIntent(String intentId,
                                              Map<String, Object> params) {
        // ① 查找意图对应的 Tool
        Optional<BaseTool> toolOpt = toolRegistry.findByIntentId(intentId);

        if (toolOpt.isEmpty()) {
            // Tool 真的不存在 → 未实现
            log.warn("[{}] 意图 '{}' 暂无实现（Tool未注册）", getAgentName(), intentId);
            return Map.of(
                "success", false,
                "error", "未实现",
                "message", "该功能暂未实现",
                "deniedIntent", intentId
            );
        }

        String toolName = toolOpt.get().getToolName();

        // ② 权限预检：Tool 存在但当前用户无权访问
        if (!toolRegistry.isVisible(toolName)) {
            log.warn("[{}] 用户越权尝试执行意图 '{}' (Tool='{}')",
                getAgentName(), intentId, toolName);
            return gracefulDenyByIntent(intentId,
                "权限不足：当前操作需要管理员权限");
        }

        // ③ 执行 Tool
        return executeTool(toolName, params);
    }

    /**
     * 构建 System Prompt（告知 LLM 当前 Agent 可用 Tool 列表）。
     */
    public String buildSystemPrompt(List<ToolMetadata> tools) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 Atlas ").append(getAgentName()).append("。\n\n");
        sb.append("可用工具列表（仅以下工具可调用）：\n");
        for (ToolMetadata t : tools) {
            sb.append(String.format("  - %s: %s\n", t.name(), t.description()));
        }
        sb.append("\n请根据用户意图，选择最合适的工具并提取参数。");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════════════
    // 权限拒绝的优雅处理
    // ═══════════════════════════════════════════════════════════

    protected Map<String, Object> gracefulDeny(String toolName, String reason) {
        return Map.of(
            "success", false,
            "error", "权限不足",
            "message", reason,
            "deniedTool", toolName
        );
    }

    protected Map<String, Object> gracefulDenyByIntent(String intentId, String reason) {
        return Map.of(
            "success", false,
            "error", "权限不足",
            "message", reason,
            "deniedIntent", intentId
        );
    }
}
