package com.atlas.tool.exception;

/**
 * Tool 权限拒绝异常 — 当用户试图调用越权 Tool 时抛出。
 *
 * <p>中文说明：该异常表达“服务端权限证据不足，因此拒绝进入 Tool 或拒绝继续执行”。输入来自
 * ToolRegistry/ToolPermission/SafeToolExecutor 对当前 Principal、角色和 Tool 元数据的比较；
 * 输出给上层 Graph/SSE/审计链路，帮助用户理解为什么不能执行该能力。</p>
 *
 * <p>安全边界：权限拒绝必须 fail-closed，不能被 LLM 重新解释、被前端按钮覆盖、被 HITL clarify
 * 自动解除，也不能因为 Tool 来源是 ReAct/Plan/ToolCallback 就放宽。异常字段用于审计和安全提示，
 * 不应包含 token、raw principal、未脱敏租户信息或 kube-manager 内部响应。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
public class PermissionDeniedException extends RuntimeException {

    /** 被拒绝的 Tool 名称或 intentId，用于审计定位；不是可由用户再次提交的执行凭证。 */
    private final String deniedTool;
    /** Tool 需要的角色/权限摘要；用于提示和测试，不泄露完整策略实现。 */
    private final String requiredRole;
    /** 当前用户角色摘要；来自服务端 Principal，不应由前端或 LLM 自报。 */
    private final String currentRole;

    public PermissionDeniedException(String message, String deniedTool,
                                      String requiredRole, String currentRole) {
        super(message);
        this.deniedTool = deniedTool;
        this.requiredRole = requiredRole;
        this.currentRole = currentRole;
    }

    public String getDeniedTool() { return deniedTool; }
    public String getRequiredRole() { return requiredRole; }
    public String getCurrentRole() { return currentRole; }
}
