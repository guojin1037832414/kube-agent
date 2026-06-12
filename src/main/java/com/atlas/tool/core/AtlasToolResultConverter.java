package com.atlas.tool.core;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

/**
 * Atlas 专用 Tool 结果转换器 — 注入到 {@code @Tool(resultConverter = ...)}。
 *
 * <p>中文说明：这是 Spring AI Tool 返回值到模型可读字符串之间的最后一层转换器。
 * 当前实现保持默认委托，目的是先稳定兼容 Spring AI 的 Map/POJO JSON 输出；未来如果要做
 * token 截断、字段脱敏或大结果摘要，应优先在这里集中处理，而不是让每个 Tool 自己复制逻辑。</p>
 *
 * <p>安全边界：结果转换不是新的安全闸门。它不能把失败结果改成成功，不能补充 HITL、
 * audit、release 或 kube-manager 写入证据，也不能把 raw token、raw prompt、内部 endpoint
 * 或未脱敏审计透传给 LLM/前端。真正的执行授权仍然只属于 SafeToolExecutor 和上游治理链路。</p>
 *
 * <p>Spring AI 默认 {@link org.springframework.ai.tool.execution.DefaultToolCallResultConverter}
 * 对 {@code Map} / POJO 使用 {@code JsonParser.toJson()}，已能满足 Atlas 需求。</p>
 *
 * <p>自定义此转换器的场景（预留）：</p>
 * <ul>
 *   <li>截断超过 Token 限制的超大返回</li>
 *   <li>敏感字段脱敏（如 secret、token）</li>
 *   <li>统一 wrap message 格式</li>
 * </ul>
 *
 * <p>当前实现：透传默认行为。</p>
 */
public class AtlasToolResultConverter implements ToolCallResultConverter {

    private final ToolCallResultConverter delegate =
        new org.springframework.ai.tool.execution.DefaultToolCallResultConverter();

    @Override
    public String convert(Object result, Type returnType) {
        // 中文说明：目前只透传 Spring AI 默认转换，避免在注释清理批次改变运行时行为。
        // 未来若接入截断/脱敏，也必须保持 redacted-only，不得伪造 Tool 成功或授权证据。
        return delegate.convert(result, returnType);
    }
}
