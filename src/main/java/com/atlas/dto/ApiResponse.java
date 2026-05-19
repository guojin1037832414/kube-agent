package com.atlas.dto;

/**
 * 统一 API 响应包装体。
 *
 * <p>所有 Controller 返回的统一格式，前端通过 {@code success} 字段判断业务成败，
 * 通过 {@code data} 获取业务数据，通过 {@code message} 获取错误提示。</p>
 *
 * <p>兼容模式：前端同时支持 {@code data} 嵌套和根级字段，因此登录响应可直接将
 * sessionId 等字段放在根级。</p>
 *
 * @param <T> 业务数据类型
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;

    /** 私有构造 — 强制使用工厂方法 */
    private ApiResponse() {}

    // ═══════════════════════════════════════════════════════════
    //  工厂方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 成功响应（无数据）。
     */
    public static <T> ApiResponse<T> ok() {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        return r;
    }

    /**
     * 成功响应（带数据）。
     */
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    /**
     * 成功响应（自定义消息）。
     */
    public static <T> ApiResponse<T> ok(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        return r;
    }

    /**
     * 失败响应。
     */
    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }

    // ═══════════════════════════════════════════════════════════
    //  Getter / Setter
    // ═══════════════════════════════════════════════════════════

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
