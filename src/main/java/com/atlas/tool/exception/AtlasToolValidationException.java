package com.atlas.tool.exception;

/**
 * Atlas Tool 业务校验异常。
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

    private final String errorCode;
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
