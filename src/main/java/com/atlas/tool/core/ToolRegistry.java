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
import java.util.stream.Collectors;

/**
 * Atlas Tool 注册中心 — v3.1 P1.4 权限感知完整版。
 *
 * <p>中文说明：ToolRegistry 是 Agent 能力目录，不是 Tool 执行器。它负责启动时收集 Tool 元数据、
 * 给 LLM/Prompt 提供“当前用户可见能力”、给审计/MCP 提供系统视角风险元数据，但不直接调用
 * {@link BaseTool#execute(java.util.Map)}。</p>
 *
 * <p>安全边界：Prompt 可见性必须按当前用户权限过滤；系统审计视角可以读取全量元数据，
 * 但调用方需要继续隐藏内部 endpoint、token、租户、审计和写入控制字段。新增方法时不要把内部 endpoint
 * 或 admin-only Tool 直接暴露给普通用户 Prompt。</p>
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

    /** 中文说明：Spring 自动注入所有 BaseTool 子类；这里只建目录，不在 init 阶段执行 Tool。 */
    private final List<BaseTool> tools;

    /** 中文说明：用户权限上下文用于 Prompt 可见性过滤；缺失用户时只能看到 PUBLIC 能力。 */
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
            // 中文说明：Tool 注解是能力治理的主入口，后续 MCP Manifest、HITL、Prompt 风险标签都依赖这些元数据。
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

            // 中文说明：权限策略决定 Tool 是否进入当前用户 Prompt。这里是“可见性预检”，真实执行仍要由 SafeToolExecutor 复核。
            ToolPermission.Policy policy = (perm != null)
                ? perm.value() : ToolPermission.Policy.PUBLIC;
            Set<String> requiredRoles = (perm != null && perm.roles().length > 0)
                ? Set.of(perm.roles()) : Set.of();
            boolean adminOnly = (policy == ToolPermission.Policy.ADMIN_ONLY);

            // 中文说明：重复 Tool 名会让 LLM/Plan 路由到不确定实现，所以启动期必须显式报错并跳过重复注册。
            if (toolByName.containsKey(name)) {
                BaseTool existing = toolByName.get(name);
                if (existing != tool) {
                    log.error("Tool名称冲突: '{}' 被多个类注册 (现有={}, 新={})",
                        name, existing.getClass().getName(), tool.getClass().getName());
                }
                // 即使同名同一对象也跳过重复注册（防御性）
                continue;
            }

            // 中文说明：同时建立 name、intentId、agent 三套索引，分别服务直接查找、意图执行和 Prompt 分组展示。
            toolByName.put(name, tool);
            if (!intentId.isBlank()) {
                toolByIntentId.put(intentId, tool);
            }

            ToolMetadata meta = new ToolMetadata(
                name, description, intentId, agent, tool,
                policy, requiredRoles, adminOnly,
                mapping != null ? mapping.httpMethod() : "",
                mapping != null ? mapping.apiEndpoints() : new String[0],
                mapping != null ? mapping.operationType() : AtlasToolMapping.OperationType.UNKNOWN,
                mapping != null && mapping.requiresConfirmation()
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

    /**
     * 按 intentId 解析 Tool 元数据。
     *
     * <p>M5.13 执行层 fail-closed HITL 使用：Graph 的 {@code tool_call} 节点在真正调用
     * {@link BaseTool#execute(java.util.Map)} 前，必须能根据 BrainDecision.target(intentId)
     * 查到 Tool 的风险契约（operationType / requiresConfirmation）。该方法复用现有
     * {@link #resolve(String)} 的权限预检逻辑，避免绕过可见性检查。</p>
     *
     * @param intentId AtlasBrain 输出的意图 ID / Tool 绑定 ID
     * @return 可见且已注册的 ToolMetadata
     */
    public Optional<ToolMetadata> resolveByIntentId(String intentId) {
        BaseTool tool = toolByIntentId.get(intentId);
        if (tool == null) {
            return Optional.empty();
        }
        return Optional.of(resolve(tool.getToolName()));
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
     * <p>中文说明：该方法用于 Prompt 可见性，不是系统审计。返回结果已经根据当前用户权限过滤，
     * 因此 LLM 不会在系统提示词里看到越权 Tool。</p>
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
     * <p>中文说明：这是 Prompt/菜单/预检层的可见性判断。它不代表 Tool 已经通过 HITL、
     * durable audit、租户上下文和参数净化；真正执行仍必须走 SafeToolExecutor。</p>
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

        return new ToolMetadata(
            name, desc, intentId, agent, tool, policy, roles, adminOnly,
            mapping != null ? mapping.httpMethod() : "",
            mapping != null ? mapping.apiEndpoints() : new String[0],
            mapping != null ? mapping.operationType() : AtlasToolMapping.OperationType.UNKNOWN,
            mapping != null && mapping.requiresConfirmation()
        );
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
     * <p>安全边界：System Prompt 只暴露给 LLM 当前用户可调用的能力摘要和风险标签。
     * 不要把内部 endpoint、原始 token、租户细节、审计回执、HITL marker 或写入放行字段放进 Prompt。</p>
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
                String riskContract = buildRiskContract(t);
                if (!riskContract.isBlank()) {
                    sb.append("    风险标签: ").append(riskContract).append("\n");
                }
                String parameterContract = buildCompactParameterContract(t.instance());
                if (!parameterContract.isBlank()) {
                    sb.append("    参数契约: ").append(parameterContract).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("规则：\n");
        sb.append("1. 只能使用上述列表中的工具\n");
        sb.append("2. 如果用户请求超出可用工具范围，请礼貌拒绝并说明权限限制\n");
        sb.append("3. 调用工具前确保已收集所有必填参数\n");
        sb.append("4. Action.params 必须优先使用参数契约中的 canonical 参数名；历史 alias 仅用于系统兼容归一化，不要主动输出 alias 字段\n");
        sb.append("5. 风险标签用于辅助判断操作影响：READ 为普通只读；SENSITIVE_READ 为敏感读取，虽不改变状态但可能读取日志、用户、权限、订单、配额等敏感数据；CREATE/UPDATE/DELETE/ACTION 可能改变系统状态；requiresConfirmation=true 表示执行前必须人工确认，执行层由 HITL fail-closed 守卫强制拦截。\n");
        sb.append("6. 参数说明中的“默认/可选”只表示表单草稿或前端填充提示，不代表用户已确认、HITL 通过、发布批准、审计成功、写入授权或真实 HTTP 执行许可；requiresConfirmation=false 只表示该 Tool 不需要额外 HITL，不代表绕过登录、RBAC、租户隔离、发布门禁或后端鉴权；不要在 Action.params 主动生成认证、租户、HITL、审计、发布或写入控制字段。\n");

        return sb.toString();
    }

    /**
     * 构建面向 ReAct/LLM 的紧凑参数契约。
     *
     * <p>中文说明：参数契约用于降低 LLM 生成错误参数的概率，但它不是安全边界。
     * LLM 即使输出了未声明字段，也必须由 SafeToolExecutor 按 ToolParameterSpec 再次校验或拒绝。</p>
     *
     * <p>设计目标：</p>
     * <ul>
     *   <li>只展示 canonical 参数名、类型、必填性和极简说明，降低 prompt 膨胀；</li>
     *   <li>不逐项输出 aliases，避免诱导 LLM 生成 alias；</li>
     *   <li>通过全局兼容提示说明 alias 会被归一化，保留旧参数兼容语义。</li>
     * </ul>
     */
    private String buildCompactParameterContract(BaseTool tool) {
        if (tool == null) {
            return "";
        }
        List<ToolParameterSpec> specs = tool.getParameterSpecs();
        if (specs == null || specs.isEmpty()) {
            return "未声明结构化参数；按工具说明传入 JSON 对象";
        }

        return specs.stream()
            .map(this::formatParameterSpec)
            .collect(Collectors.joining("; "));
    }

    /**
     * 构建面向 ReAct/LLM 的紧凑风险契约。
     *
     * <p>M5.12 只暴露“业务风险语义”，不把 {@code apiEndpoints} 明文写入 Prompt，
     * 避免 LLM 被内部路径诱导或向普通用户泄露 kube-manager 攻击面。</p>
     */
    private String buildRiskContract(ToolMetadata meta) {
        if (meta == null) {
            return "";
        }
        String operationType = meta.operationType().name();
        String httpMethod = meta.httpMethod().isBlank() ? "未声明HTTP" : meta.httpMethod();
        String confirmation = meta.requiresConfirmation() ? "requiresConfirmation=true" : "requiresConfirmation=false";
        return String.format("operationType=%s, httpMethod=%s, %s", operationType, httpMethod, confirmation);
    }

    private String formatParameterSpec(ToolParameterSpec spec) {
        String required = spec.required() ? "必填" : "可选";
        String type = spec.type() == null || spec.type().isBlank() ? "string" : spec.type();
        String description = spec.description() == null ? "" : spec.description().strip();
        if (description.isBlank()) {
            return String.format("%s(%s,%s)", spec.name(), type, required);
        }
        return String.format("%s(%s,%s,%s)", spec.name(), type, required, description);
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

    /**
     * 返回系统审计视角的全部 Tool 元数据。
     *
     * <p>中文说明：这是系统审计视角，不受当前用户 Prompt 可见性限制。MCP Manifest、治理报表、
     * 安全测试可以用它统计全量风险，但对外展示时必须继续脱敏并过滤不可导出的能力。</p>
     *
     * <p>M5.20 MCP Manifest / 审计测试使用该方法做全量风险门控统计。该方法不用于普通用户 Prompt，
     * 因此不会改变权限感知的运行时行为；对外输出时仍必须由调用方过滤敏感字段和不可暴露 Tool。</p>
     */
    public List<ToolMetadata> listAllMetadataForSystemAudit() {
        return agentIndex.values().stream()
            .flatMap(List::stream)
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
        private final BaseTool instance;

        // ── P1.4 新增：权限字段 ──
        private final ToolPermission.Policy permissionPolicy;
        private final Set<String> requiredRoles;
        private final boolean adminOnly;

        // ── M5.12 新增：Tool 风险/HTTP 契约字段。Prompt 只暴露摘要，不暴露 apiEndpoints 明细。 ──
        private final String httpMethod;
        private final List<String> apiEndpoints;
        private final AtlasToolMapping.OperationType operationType;
        private final boolean requiresConfirmation;

        public ToolMetadata(String name, String description, String intentId,
                             String agent, BaseTool instance,
                             ToolPermission.Policy permissionPolicy,
                             Set<String> requiredRoles,
                             boolean adminOnly,
                             String httpMethod,
                             String[] apiEndpoints,
                             AtlasToolMapping.OperationType operationType,
                             boolean requiresConfirmation) {
            this.name = name;
            this.description = description;
            this.intentId = intentId;
            this.agent = agent;
            this.instance = instance;
            this.permissionPolicy = permissionPolicy;
            this.requiredRoles = requiredRoles != null ? Set.copyOf(requiredRoles) : Set.of();
            this.adminOnly = adminOnly;
            this.httpMethod = httpMethod != null ? httpMethod : "";
            this.apiEndpoints = apiEndpoints != null ? List.of(apiEndpoints) : List.of();
            this.operationType = operationType != null ? operationType : AtlasToolMapping.OperationType.UNKNOWN;
            this.requiresConfirmation = requiresConfirmation;
        }

        public String name() { return name; }
        public String description() { return description; }
        public String intentId() { return intentId; }
        public String agent() { return agent; }
        public BaseTool instance() { return instance; }

        // 新增 getters
        public ToolPermission.Policy permissionPolicy() { return permissionPolicy; }
        public Set<String> requiredRoles() { return requiredRoles; }
        public boolean isAdminOnly() { return adminOnly; }
        public String httpMethod() { return httpMethod; }
        public List<String> apiEndpoints() { return apiEndpoints; }
        public AtlasToolMapping.OperationType operationType() { return operationType; }
        public boolean requiresConfirmation() { return requiresConfirmation; }

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
