package com.atlas.tool.core;

import java.util.Map;

/**
 * Atlas Tool 通用接口。
 *
 * <p>中文说明：这是 kube-agent 内部最小的 Tool 形状，描述“一个工具最终接收普通业务参数
 * {@code Map}，并返回结构化结果 {@code Map}”。它存在的学习价值是把 Tool 的业务执行面
 * 和上层 ReAct / Graph / Spring AI 回调适配层解耦：上层可以用统一方式编排工具，具体工具
 * 仍然只关心自己的 kube-manager 查询或写入语义。</p>
 *
 * <p>安全边界：该接口本身不是执行授权、不是 HITL 确认、不是审计回执，也不是
 * kube-manager token/orgId 的来源。所有来自 LLM、Plan、前端或测试的 {@code params}
 * 都只能被视为不可信候选业务输入；生产路径必须先经过 {@link com.atlas.tool.execution.SafeToolExecutor}
 * 的权限、受保护字段、HITL、审计和 trace 门禁后，才允许调用具体 Tool。</p>
 */
public interface AtlasTool {

    /**
     * 执行 Tool 任务。
     *
     * <p>中文说明：参数通常来自 LLM 提取、用户表单、默认值回填或 Graph 节点整理后的候选字段。
     * 实现类只能消费与自身业务相关的字段；不能相信其中的 {@code token/orgId/userId/sessionId}
     * 等控制平面字段，也不能把它们写入审计、记忆或对外响应。</p>
     *
     * @param params LLM/Plan/前端整理出的候选业务参数 Map
     * @return 结构化结果 Map，最终可能被 SafeToolExecutor、Graph、SSE 或前端投影消费
     */
    Map<String, Object> execute(Map<String, Object> params);
}
