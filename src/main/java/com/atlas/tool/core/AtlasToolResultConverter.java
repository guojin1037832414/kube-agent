package com.atlas.tool.core;

import org.springframework.ai.tool.execution.ToolCallResultConverter;

import java.lang.reflect.Type;

/**
 * Atlas 专用 Tool 结果转换器 — 注入到 {@code @Tool(resultConverter = ...)}。
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
        // 可在此添加截断/脱敏逻辑
        return delegate.convert(result, returnType);
    }
}
