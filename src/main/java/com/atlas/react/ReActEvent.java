package com.atlas.react;

import java.util.Map;

/**
 * ReAct 执行过程事件。
 *
 * <p>该事件是 ReAct 引擎内部领域事件，不直接依赖 Spring MVC / SSE。
 * 上层编排器可以把它转换为 SSE、日志、审计记录或调试面板事件。</p>
 *
 * @param type      事件类型，例如 thinking/tool_start/tool_done/observation/content/error
 * @param step      当前 ReAct 步骤序号；无步骤上下文时为 0
 * @param content   面向用户或调试面板展示的简短文本
 * @param tool      当前工具名称；非工具事件可为空字符串
 * @param success   工具或步骤是否成功；无明确成功失败语义时可为 true
 * @param metadata  扩展元数据，避免后续新增字段破坏构造器
 */
public record ReActEvent(
    String type,
    int step,
    String content,
    String tool,
    boolean success,
    Map<String, Object> metadata
) {

    /** 创建思考事件。 */
    public static ReActEvent thinking(int step, String content) {
        return new ReActEvent("thinking", step, content, "", true, Map.of());
    }

    /** 创建工具开始事件。 */
    public static ReActEvent toolStart(int step, String tool, Map<String, Object> params) {
        return new ReActEvent("tool_start", step, "正在调用工具: " + tool, tool, true,
            Map.of("params", params != null ? params : Map.of()));
    }

    /** 创建工具完成事件。 */
    public static ReActEvent toolDone(int step, String tool, boolean success, long costMs) {
        return new ReActEvent("tool_done", step,
            (success ? "工具执行成功" : "工具执行失败") + "，耗时 " + costMs + "ms",
            tool, success, Map.of("costMs", costMs));
    }

    /** 创建 Observation 事件。 */
    public static ReActEvent observation(int step, String tool, String observation, boolean truncated) {
        String preview = observation == null ? "" : observation;
        if (preview.length() > 500) {
            preview = preview.substring(0, 500) + "...";
        }
        return new ReActEvent("observation", step, preview, tool, true,
            Map.of("truncated", truncated));
    }

    /** 创建最终内容事件。 */
    public static ReActEvent content(int step, String content) {
        return new ReActEvent("content", step, content != null ? content : "", "", true, Map.of());
    }

    /** 创建错误事件。 */
    public static ReActEvent error(int step, String content) {
        return new ReActEvent("error", step, content != null ? content : "", "", false, Map.of());
    }
}
