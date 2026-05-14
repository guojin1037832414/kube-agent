package com.atlas.tool.core;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.exception.PermissionDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Atlas Tool 注册中心 — v3.1 P1.4 权限感知完整版。
 *
 * <p>结合 Spring AI Function Calling 自动扫描 + Agent 分组 + <b>权限感知</b>，
 * 为意图路由和 Agent 执行提供统一的 Tool 查询服务。</p>
 *
 * <p>职责：</p>
 * <ol>
 *   <li>启动时索引所有 {@link BaseTool} Bean，建立 name→Tool 映射</li>
 *   <li>按 {@code agent} 分组索引（Query/Diag/Deploy/...）</li>
 *   <li>按 {@code intentId} 反查绑定 Tool</li>
 *   <li><b>P1.4 新增：权限感知（预检层）</b> — 判断 Tool/Intent 是否对当前用户可见</li>
 *   <li><b>P1.4 修复：重复检测 bug</b> — 先检测冲突再 put，确保重复报出</li>
 *   <li>健康检查：已注册 Tool 列表、数量、Agent 覆盖度</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-P1.4
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    /** Spring 自动注入所有 BaseTool 子类 */
    private final List<BaseTool> tools;

    /** 用户权限上下文（ThreadLocal 方式获取当前请求用户） */
    private final UserPermissionContext userPermissionContext;

    /** name → Tool */
    private final Map<String, BaseTool> toolByName = new LinkedHashMap<>();

    /** intentId → Tool */
    private final Map<String, BaseTool> toolByIntentId = new LinkedHashMap<>();

    /** agent code → ToolMetadata 列表 */
    private final Map<String, List<ToolMetadata>> agentIndex = new LinkedHashMap<>();

    public ToolRegistry(List<BaseTool> tools, UserPermissionContext userPermissionContext) {
        this.tools = tools != null ? tools : List.of();
        this.userPermissionContext = userPermissionContext;
    }

    @PostConstruct
    public void init() {
        for (BaseTool tool : tools) {
            // 提取注解信息
            AtlasToolMapping mapping = tool.getClass().getAnnotation(AtlasToolMapping.class);
            ToolPermission perm = tool.getClass().getAnnotation(ToolPermission.class);

            String name = mapping != null && !mapping.name().isBlank()
                ? mapping.name() : tool.getToolName();
            String agent = mapping != null && !mapping.agent().isBlank()
                ? mapping.agent() : "query";
            String intentId = mapping != null && !mapping.intentId().isBlank()
                ? mapping.intentId() : tool.getToolName();
            String description = mapping != null && !mapping.description().isBlank()
                ? mapping.description() : tool.getDescription();

            // ═══ P1.4 新增：解析权限策略 ═══
            ToolPermission.Policy policy = (perm != null)
                ? perm.value() : ToolPermission.Policy.PUBLIC;
            Set<String> requiredRoles = (perm != null && perm.roles().length > 0)
                ? Set.of(perm.roles()) : Set.of();
            boolean adminOnly = (policy == ToolPermission.Policy.ADMIN_ONLY);

            // ═══ P1.4 修复：重复检测 — 先检测再 put ═══
            if (toolByName.containsKey(name)) {
                BaseTool existing = toolByName.get(name);
                if (existing != tool) {
                    log.error("Tool名称冲突: '{}' 被多个类注册 (现有={}, 新={})",
                        name, existing.getClass().getName(), tool.getClass().getName());
                }
                // 即使同名同一对象也跳过重复注册（防御性）
                continue;
            }

            // 建立索引
            toolByName.put(name, tool);
            if (!intentId.isBlank()) {
                toolByIntentId.put(intentId, tool);
            }

            ToolMetadata meta = new ToolMetadata(
                name, description, intentId, agent, tool,
                policy, requiredRoles, adminOnly
            );
            agentIndex.computeIfAbsent(agent, k -> new ArrayList<>()).add(meta);
        }

        // 统计权限分布
        long publicCount = agentIndex.values().stream()
            .flatMap(List::stream)
            .filter(m -> m.permissionPolicy() == ToolPermission.Policy.PUBLIC)
            .count();
        long authCount = agentIndex.values().stream()
            .flatMap(List::stream)
            .filter(m -> m.permissionPolicy() == ToolPermission.Policy.AUTHENTICATED)
            .count();
        long adminCount = agentIndex.values().stream()
            .flatMap(List::stream)
            .filter(m -> m.permissionPolicy() == ToolPermission.Policy.ADMIN_ONLY)
            .count();

        log.info("[ToolRegistry] 已注册 {} 个Tool, {} 个Agent分组 | 权限分布: PUBLIC={}, AUTHENTICATED={}, ADMIN_ONLY={}",
            toolByName.size(), agentIndex.size(), publicCount, authCount, adminCount);
    }

    // ═══════════════════════════════════════════
    // 查询接口
    // ═══════════════════════════════════════════

    public Optional<BaseTool> findByName(String toolName) {
        return Optional.ofNullable(toolByName.get(toolName));
    }

    public Optional<BaseTool> findByIntentId(String intentId) {
        return Optional.ofNullable(toolByIntentId.get(intentId));
    }

    public Set<String> getAllToolNames() {
        return Collections.unmodifiableSet(toolByName.keySet());
    }

    public List<BaseTool> getAllTools() {
        return List.copyOf(tools);
    }

    // ═══════════════════════════════════════════
    // Agent 分组查询（供 AtlasAgentBase 使用）
    // ═══════════════════════════════════════════

    /**
     * 按 Agent 类型列出当前用户<b>可见</b>的 ToolMetadata。
     *
     * <p>P1.4 权限感知：已按用户权限过滤，LLM 系统提示词中不会包含越权 Tool。</p>
     *
     * @param agentCode agent代码，如 "query" / "deploy" / "rbac"
     */
    public List<ToolMetadata> listByAgent(String agentCode) {
        List<ToolMetadata> all = agentIndex.getOrDefault(agentCode, List.of());
        Optional<UserPermissionContext.UserPermission> userOpt = userPermissionContext.current();

        return all.stream()
            .filter(meta -> meta.isVisibleTo(userOpt.orElse(null)))
            .toList();
    }

    // ═══════════════════════════════════════════
    // 权限感知（P1.4 核心）
    // ═══════════════════════════════════════════

    /**
     * 判断 Tool 是否对<b>当前请求用户</b>可见。
     *
     * <p>P1.4 新增：接入 {@link UserPermissionContext} ThreadLocal 判断权限。</p>
     */
    public boolean isVisible(String toolName) {
        BaseTool tool = toolByName.get(toolName);
        if (tool == null) return false;

        ToolPermission perm = tool.getClass().getAnnotation(ToolPermission.class);
        ToolPermission.Policy policy = (perm != null)
            ? perm.value() : ToolPermission.Policy.PUBLIC;

        // PUBLIC = 无条件可见
        if (policy == ToolPermission.Policy.PUBLIC) return true;

        Optional<UserPermissionContext.UserPermission> userOpt = userPermissionContext.current();

        if (policy == ToolPermission.Policy.AUTHENTICATED) {
            return userOpt.isPresent();
        }

        if (policy == ToolPermission.Policy.ADMIN_ONLY) {
            return userOpt.map(UserPermissionContext.UserPermission::isAdmin).orElse(false);
        }

        return false;
    }

    /**
     * 解析 Tool 元数据（带权限预检）。
     *
     * <p>P1.4 新增：权限不足时抛出 PermissionDeniedException，携带详细的越权信息。</p>
     */
    public ToolMetadata resolve(String toolName) {
        BaseTool tool = toolByName.get(toolName);
        if (tool == null) {
            throw new IllegalArgumentException("Tool '" + toolName + "' 未注册");
        }

        // 权限预检：越权时抛出 PermissionDeniedException
        ToolPermission perm = tool.getClass().getAnnotation(ToolPermission.class);
        ToolPermission.Policy policy = (perm != null)
            ? perm.value() : ToolPermission.Policy.PUBLIC;

        if (!isVisible(toolName)) {
            String required = policy.name();
            Optional<UserPermissionContext.UserPermission> userOpt = userPermissionContext.current();
            String currentRole = userOpt.map(UserPermissionContext.UserPermission::role).orElse("anonymous");

            throw new PermissionDeniedException(
                "无权调用 Tool '" + toolName + "'，需要权限: " + required,
                toolName, required, currentRole
            );
        }

        // 重建 ToolMetadata
        AtlasToolMapping mapping = tool.getClass().getAnnotation(AtlasToolMapping.class);
        String name = mapping != null && !mapping.name().isBlank() ? mapping.name() : tool.getToolName();
        String agent = mapping != null && !mapping.agent().isBlank() ? mapping.agent() : "query";
        String intentId = mapping != null && !mapping.intentId().isBlank() ? mapping.intentId() : name;
        String desc = mapping != null && !mapping.description().isBlank() ? mapping.description() : tool.getDescription();

        Set<String> roles = (perm != null && perm.roles().length > 0) ? Set.of(perm.roles()) : Set.of();
        boolean adminOnly = (policy == ToolPermission.Policy.ADMIN_ONLY);

        return new ToolMetadata(name, desc, intentId, agent, tool, policy, roles, adminOnly);
    }

    /**
     * 判断意图是否可执行。
     * <p>当前简化：只要存在绑定 Tool 且用户对该 Tool 可见即返回 true。</p>
     */
    public boolean canExecuteIntent(String intentId) {
        BaseTool tool = toolByIntentId.get(intentId);
        if (tool == null) return false;
        return isVisible(intentId) || isVisible(tool.getToolName());
    }

    // ═══════════════════════════════════════════
    // Spring AI Function Calling 集成
    // ═══════════════════════════════════════════

    /**
     * 构建当前用户的 System Prompt（已按权限过滤可见 Tool）。
     *
     * <p>P1.4 关键功能：LLM 的 System Prompt 中只包含当前用户有权调用的 Tool，
     * 从源头上防止 LLM "看到" 越权 Tool 并尝试调用。</p>
     *
     * <p>用法（在 ChatClient#system() 中调用）：</p>
     * <pre>{@code
     * String systemPrompt = toolRegistry.buildSystemPromptForCurrentUser();
     * chatClient.prompt()
     *     .system(systemPrompt)
     *     .user(userQuery)
     *     .call();
     * }</pre>
     *
     * @return 格式化的 System Prompt 字符串
     */
    public String buildSystemPromptForCurrentUser() {
        List<ToolMetadata> visibleTools = agentIndex.values().stream()
            .flatMap(List::stream)
            .filter(meta -> meta.isVisibleTo(userPermissionContext.current().orElse(null)))
            .toList();

        if (visibleTools.isEmpty()) {
            return "你是 Atlas 助手。当前用户无可用的工具。请直接回答用户问题。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("你是 Atlas K8s 集群管理助手。\n\n");
        sb.append("你可以调用以下工具来帮助用户（仅以下工具可用）：\n\n");

        // 按 Agent 分组展示，便于 LLM 理解
        Map<String, List<ToolMetadata>> grouped = new LinkedHashMap<>();
        for (ToolMetadata t : visibleTools) {
            grouped.computeIfAbsent(t.agent(), k -> new ArrayList<>()).add(t);
        }

        for (Map.Entry<String, List<ToolMetadata>> entry : grouped.entrySet()) {
            sb.append("[").append(entry.getKey()).append("]\n");
            for (ToolMetadata t : entry.getValue()) {
                sb.append(String.format("  • %s: %s\n", t.name(), t.description()));
            }
            sb.append("\n");
        }

        sb.append("规则：\n");
        sb.append("1. 只能使用上述列表中的工具\n");
        sb.append("2. 如果用户请求超出可用工具范围，请礼貌拒绝并说明权限限制\n");
        sb.append("3. 调用工具前确保已收集所有必填参数\n");

        return sb.toString();
    }

    /**
     * 获取当前用户可见的 Tool 名称列表（用于 LLM function calling 的 function definitions 过滤）。
     */
    public List<String> getVisibleToolNamesForCurrentUser() {
        return agentIndex.values().stream()
            .flatMap(List::stream)
            .filter(meta -> meta.isVisibleTo(userPermissionContext.current().orElse(null)))
            .map(ToolMetadata::name)
            .toList();
    }

    // ═══════════════════════════════════════════
    // 健康检查
    // ═══════════════════════════════════════════

    public Map<String, Object> health() {
        long publicCount = agentIndex.values().stream()
            .flatMap(List::stream)
            .filter(m -> m.permissionPolicy() == ToolPermission.Policy.PUBLIC)
            .count();
        long authCount = agentIndex.values().stream()
            .flatMap(List::stream)
            .filter(m -> m.permissionPolicy() == ToolPermission.Policy.AUTHENTICATED)
            .count();
        long adminCount = agentIndex.values().stream()
            .flatMap(List::stream)
            .filter(m -> m.permissionPolicy() == ToolPermission.Policy.ADMIN_ONLY)
            .count();

        return Map.of(
            "totalTools", toolByName.size(),
            "tools", toolByName.keySet().stream().sorted().toList(),
            "agentCoverage", agentIndex.keySet(),
            "permissionDistribution", Map.of(
                "public", publicCount,
                "authenticated", authCount,
                "adminOnly", adminCount
            )
        );
    }

    // ═══════════════════════════════════════════
    // 内部类：ToolMetadata — P1.4 新增权限字段
    // ═══════════════════════════════════════════

    /**
     * Tool 元数据 — 供 Agent 层使用，不 exposure 给 Spring AI。
     */
    public static class ToolMetadata {
        private final String name;
        private final String description;
        private final String intentId;
        private final String agent;
        private final AtlasTool instance;

        // ── P1.4 新增：权限字段 ──
        private final ToolPermission.Policy permissionPolicy;
        private final Set<String> requiredRoles;
        private final boolean adminOnly;

        public ToolMetadata(String name, String description, String intentId,
                             String agent, AtlasTool instance,
                             ToolPermission.Policy permissionPolicy,
                             Set<String> requiredRoles,
                             boolean adminOnly) {
            this.name = name;
            this.description = description;
            this.intentId = intentId;
            this.agent = agent;
            this.instance = instance;
            this.permissionPolicy = permissionPolicy;
            this.requiredRoles = requiredRoles != null ? Set.copyOf(requiredRoles) : Set.of();
            this.adminOnly = adminOnly;
        }

        public String name() { return name; }
        public String description() { return description; }
        public String intentId() { return intentId; }
        public String agent() { return agent; }
        public AtlasTool instance() { return instance; }

        // 新增 getters
        public ToolPermission.Policy permissionPolicy() { return permissionPolicy; }
        public Set<String> requiredRoles() { return requiredRoles; }
        public boolean isAdminOnly() { return adminOnly; }

        /**
         * 判断当前 Tool 是否对指定用户可见。
         *
         * @param user 用户权限快照（null = 匿名用户）
         */
        public boolean isVisibleTo(UserPermissionContext.UserPermission user) {
            switch (permissionPolicy) {
                case PUBLIC:
                    return true;
                case AUTHENTICATED:
                    return user != null;
                case ADMIN_ONLY:
                    return user != null && user.isAdmin();
                default:
                    return false;
            }
        }
    }
}
