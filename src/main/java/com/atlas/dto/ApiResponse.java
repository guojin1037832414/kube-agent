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
 * <p>中文说明：这个类是“前端展示契约”，不是权限契约。{@code success=true} 只代表当前
 * HTTP/API 调用在业务层成功返回，不代表 Tool 已授权、不代表 HITL 已确认、不代表 eval/release
 * 已通过，也不代表 kube-manager 写操作已经具备执行权。</p>
 *
 * <p>安全边界：Controller 不应把 token、password、secret、raw audit、raw prompt 或原始
 * kube-manager 响应体直接塞进 {@code data}。需要返回诊断材料时，应优先返回脱敏 read model
 * 或 summary DTO。</p>
 *
 * @param <T> 业务数据类型
 * @author Atlas Team
 * @since 3.1.0-M2.5
 */
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private String message;

    /** 私有构造 — 强制使用工厂方法，避免调用方忘记设置 success 语义。 */
    private ApiResponse() {}

    // ═══════════════════════════════════════════════════════════
    //  工厂方法
    // ═══════════════════════════════════════════════════════════

    /**
     * 成功响应（无数据）。
     *
     * <p>中文说明：适合删除、登出、轻量确认等接口；不要用它表达 Tool/发布/权限门已经通过。</p>
     */
    public static <T> ApiResponse<T> ok() {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        return r;
    }

    /**
     * 成功响应（带数据）。
     *
     * <p>安全边界：data 必须由 Controller/Service 先做权限收敛和脱敏，ApiResponse 不负责二次清洗。</p>
     */
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    /**
     * 成功响应（自定义消息）。
     *
     * <p>中文说明：message 是给前端显示的人类提示，不应被其他服务当作机器可验证证据。</p>
     */
    public static <T> ApiResponse<T> ok(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.message = message;
        return r;
    }

    /**
     * 失败响应。
     *
     * <p>中文说明：失败 message 要便于排障，但不能泄露 token、密码、完整 endpoint 或内部堆栈。</p>
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
