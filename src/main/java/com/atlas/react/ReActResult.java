package com.atlas.react;

import java.util.List;

/**
 * ReAct 引擎执行结果。
 *
 * <p>包含最终答案、全部执行步骤、耗时及停止原因，
 * 供上层编排器（AtlasOrchestrator）消费。</p>
 *
 * @param success         整体是否成功（至少未出异常且产出了 finalAnswer）
 * @param finalAnswer     最终面向用户的回答文本
 * @param steps           全部 ReAct 步骤列表
 * @param executionTimeMs 总执行耗时（毫秒）
 * @param stopReason      停止原因，如 "final_answer", "max_steps", "timeout", "duplicate_action"
 * @author Atlas Team
 * @since 3.1.0-M3.2
 */
public record ReActResult(
    boolean success,
    String finalAnswer,
    List<ReActMemory.Step> steps,
    long executionTimeMs,
    String stopReason
) {

    /**
     * 成功结果工厂。
     *
     * @param finalAnswer 最终答案
     * @param steps       步骤列表
     * @param totalMs     总耗时
     * @param stopReason  停止原因
     */
    public static ReActResult ok(String finalAnswer,
                                  List<ReActMemory.Step> steps,
                                  long totalMs,
                                  String stopReason) {
        return new ReActResult(true, finalAnswer, steps, totalMs, stopReason);
    }

    /**
     * 失败结果工厂。
     *
     * @param failMessage 失败说明
     * @param steps       已执行的步骤（可为空列表）
     * @param totalMs     总耗时
     * @param stopReason  停止原因
     */
    public static ReActResult fail(String failMessage,
                                    List<ReActMemory.Step> steps,
                                    long totalMs,
                                    String stopReason) {
        return new ReActResult(false, failMessage, steps, totalMs, stopReason);
    }
}
