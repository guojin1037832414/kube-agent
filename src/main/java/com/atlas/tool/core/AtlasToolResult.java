package com.atlas.tool.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Atlas Tool 统一返回结构 — 专门为 LLM 消费设计。
 *
 * <p>中文说明：这是具体 Tool 执行后返回给 {@link com.atlas.tool.execution.SafeToolExecutor}、
 * Graph、ReAct 和前端投影层的统一数据容器。它把“执行是否成功、给人的说明、业务数据、
 * 错误码和澄清建议”整理成稳定 Map，便于 LLM 继续推理，也便于前端用相同字段渲染结果。</p>
 *
 * <p>安全边界：{@code success=true} 只表示某个 Tool 在当前执行链路中返回了成功结构，
 * 不反向证明 HITL 已确认、audit prewrite 已落盘、release gate 已通过，也不能成为后续
 * 写操作的授权来源。{@code data/message/suggestions} 都必须保持脱敏，不能包含 token、
 * password、raw prompt、raw audit、内部 endpoint 或未审阅的 kube-manager 响应原文。</p>
 *
 * <p><b>强制字段：</b></p>
 * <ul>
 *   <li><b>success</b>：操作是否成功（布尔，LLM 可据此决定下一步）</li>
 *   <li><b>message</b>：人类可读状态描述（失败时放错误信息，成功时放摘要）</li>
 *   <li><b>data</b>：实际业务数据（成功时有效，失败时可为 null 或错误码表）</li>
 * </ul>
 *
 * <p>额外可填充字段（由各 Tool 按需添加）：</p>
 * <ul>
 *   <li>{@code toolName}、{@code executionTimeMs}、{@code retryCount} 等元数据</li>
 *   <li>{@code suggestions}：失败时的修复建议，让 LLM 引导用户操作</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
public class AtlasToolResult extends LinkedHashMap<String, Object> {

    private static final long serialVersionUID = 1L;

    // ═══════════════════════════════════════════
    // 核心字段 Key 常量
    // ═══════════════════════════════════════════
    public static final String KEY_SUCCESS       = "success";
    public static final String KEY_MESSAGE       = "message";
    public static final String KEY_DATA          = "data";
    public static final String KEY_TOOL_NAME     = "toolName";
    public static final String KEY_ERROR_CODE    = "errorCode";
    public static final String KEY_SUGGESTIONS   = "suggestions";
    public static final String KEY_EXECUTION_MS  = "executionTimeMs";

    private AtlasToolResult() {
        // 确保 JSON 序列化顺序：success → message → data → ...
    }

    // ═══════════════════════════════════════════
    // 工厂方法
    // ═══════════════════════════════════════════

    /**
     * 成功返回。
     *
     * <p>中文说明：成功结构只表达“当前 Tool 的业务处理结果”。调用方若要展示给前端或喂给 LLM，
     * 仍应确认 data 已经是脱敏后的业务摘要，而不是原始凭证、原始审计或内部 HTTP 细节。</p>
     *
     * @param message 简短摘要（如 "查询到 3 个节点"）
     * @param data    业务数据对象（任意 POJO / List / Map）
     */
    public static AtlasToolResult ok(String message, Object data) {
        AtlasToolResult r = new AtlasToolResult();
        r.put(KEY_SUCCESS, true);
        r.put(KEY_MESSAGE, message);
        r.put(KEY_DATA, data == null ? Map.of() : data);
        return r;
    }

    /**
     * 成功返回（无业务数据）。
     */
    public static AtlasToolResult ok(String message) {
        return ok(message, Map.of());
    }

    /**
     * 失败返回（业务异常）。
     *
     * <p>中文说明：失败结果用于让 LLM/前端知道下一步该澄清什么，例如补参数或重新登录；
     * 它不是权限拒绝的替代系统，也不能让模型把失败原因重新解释成“可以继续执行”。</p>
     *
     * @param message    人类可读错误信息
     * @param errorCode  可选内部错误码（如 "PVC_NAME_CONFLICT"）
     * @param suggestions LLM 引导用户的修复建议列表
     */
    public static AtlasToolResult fail(String message, String errorCode, java.util.List<String> suggestions) {
        AtlasToolResult r = new AtlasToolResult();
        r.put(KEY_SUCCESS, false);
        r.put(KEY_MESSAGE, message);
        r.put(KEY_DATA, Map.of());
        if (errorCode != null)   r.put(KEY_ERROR_CODE, errorCode);
        if (suggestions != null) r.put(KEY_SUGGESTIONS, suggestions);
        return r;
    }

    /**
     * 失败返回（简化版）。
     */
    public static AtlasToolResult fail(String message) {
        return fail(message, null, null);
    }

    /**
     * 失败返回（带修复建议）。
     */
    public static AtlasToolResult fail(String message, String suggestion) {
        return fail(message, null,
            suggestion == null ? null : java.util.List.of(suggestion));
    }

    // ═══════════════════════════════════════════
    // 链式增强方法（构建后可直接 .withXxx() 追加）
    // ═══════════════════════════════════════════

    public AtlasToolResult withToolName(String toolName) {
        put(KEY_TOOL_NAME, toolName);
        return this;
    }

    public AtlasToolResult withExecutionTimeMs(long ms) {
        put(KEY_EXECUTION_MS, ms);
        return this;
    }

    // ═══════════════════════════════════════════
    // 便捷读取方法
    // ═══════════════════════════════════════════

    public boolean isSuccess() {
        Object v = get(KEY_SUCCESS);
        return Boolean.TRUE.equals(v);
    }

    public String getMessage() {
        Object v = get(KEY_MESSAGE);
        return v != null ? v.toString() : "";
    }

    @SuppressWarnings("unchecked")
    public <T> T getData() {
        return (T) get(KEY_DATA);
    }
}
