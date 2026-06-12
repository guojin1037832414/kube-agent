package com.atlas.tool.exception;

/**
 * Atlas Tool 业务校验异常。
 *
 * <p>中文说明：该异常用于 Tool 已进入服务端执行边界后，对候选业务参数做 fail-fast 校验。
 * 典型输入来自 LLM Action JSON、PlanStep、前端表单或规则路由归一化后的参数；输出会被 BaseTool
 * 转换成结构化失败结果，再由 SafeToolExecutionResult 暴露给 Graph/SSE/前端澄清 UI。</p>
 *
 * <p>安全边界：这是参数/业务规则失败，不是权限系统。抛出或捕获该异常不能绕过 ToolPermission、
 * HITL、durable audit、kube-manager 权限、受保护参数过滤或 release gate。错误消息、errorCode
 * 和 suggestions 面向用户/前端展示，不能包含 token、raw endpoint、内部栈或未脱敏参数值。</p>
 *
 * <p>触发条件：</p>
 * <ul>
 *   <li>必填参数缺失</li>
 *   <li>参数类型不合法（如将字符串传给期望 Integer 的字段）</li>
 *   <li>业务规则校验失败（如 CPU 限制 < 0）</li>
 * </ul>
 *
 * <p>异常会被 {@link com.atlas.tool.core.BaseTool} 捕获并转为
 * {@link com.atlas.tool.core.AtlasToolResult#fail} 结构返回给 LLM。</p>
 */
public class AtlasToolValidationException extends RuntimeException {

    /** 结构化错误码，用于前端补参/澄清；不是内部异常类型或权限拒绝原因的泄露通道。 */
    private final String errorCode;
    /** 面向用户的补参建议；只描述安全的业务选项，不能包含敏感参数或自动执行指令。 */
    private final java.util.List<String> suggestions;

    public AtlasToolValidationException(String message) {
        super(message);
        this.errorCode = null;
        this.suggestions = java.util.List.of();
    }

    public AtlasToolValidationException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = java.util.List.of();
    }

    public AtlasToolValidationException(String message, String errorCode,
                                        java.util.List<String> suggestions) {
        super(message);
        this.errorCode = errorCode;
        this.suggestions = suggestions != null ? suggestions : java.util.List.of();
    }

    public String getErrorCode() {
        return errorCode;
    }

    public java.util.List<String> getSuggestions() {
        return suggestions;
    }
}
