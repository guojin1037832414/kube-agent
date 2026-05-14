package com.atlas.orchestrator;

/**
 * SSE 事件类型定义。
 *
 * <p>标准化的7种事件类型：</p>
 * <ul>
 *   <li>{@code thinking} — Agent 思考中</li>
 *   <li>{@code tool_call} — 工具调用卡片</li>
 *   <li>{@code tool_result} — 工具返回结果</li>
 *   <li>{@code content} — 实际回复内容</li>
 *   <li>{@code hitl_request} — 人机回环确认请求</li>
 *   <li>{@code done} — 流结束</li>
 *   <li>{@code error} — 错误事件</li>
 * </ul>
 *
 * @param type      事件类型
 * @param content   内容（JSON字符串或纯文本）
 * @param timestamp 时间戳（毫秒）
 */
public record SseEvent(String type, String content, long timestamp) {

    /**
     * 便捷构造方法，自动填充当前时间戳。
     */
    public SseEvent(String type, String content) {
        this(type, content, System.currentTimeMillis());
    }

    /**
     * 转换为 SSE 协议所需的 data: 行格式。
     */
    public String toSseData() {
        return "data: " + content + "\n\n";
    }
}
