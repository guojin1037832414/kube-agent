package com.atlas.orchestrator.polish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.*;

/**
 * Tool 结果格式化与截断工具 — v3.1 B方案。
 *
 * <p><b>目标：</b>在保留业务关键信息的前提下，将 Tool 输出压缩至 LLM 上下文窗口可接受范围，
 * 同时降低 Token 开销和 LLM 代理延迟。</p>
 *
 * <p><b>处理策略：</b></p>
 * <ul>
 *   <li>列表数据：截断至 {@link #MAX_LIST_ITEMS} 条，标注总数</li>
 *   <li>嵌套 Map：扁平化一级，减少 JSON 嵌套深度</li>
 *   <li>超大 JSON：字符级硬截断 + 标注</li>
 *   <li>异常时 fallback 到 toString()，绝不抛出异常</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-P3
 */
public final class ToolResultFormatter {

    private static final ObjectMapper PRETTY_MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** 放入 Prompt 的 Tool 结果最大字符数（约对应 2000~3000 tokens） */
    public static final int MAX_CONTEXT_LENGTH = 8000;

    /** 列表最大展示条数 */
    public static final int MAX_LIST_ITEMS = 20;

    /** 嵌套 Map 最大深度（超过则转为 String） */
    private static final int MAX_NESTING_DEPTH = 3;

    private ToolResultFormatter() {}

    /**
     * 将 Tool 结果格式化为适合放入 Prompt 的 JSON 文本。
     *
     * @param toolResult ToolRegistry.execute() 返回的 Map
     * @return JSON 字符串（已截断/压缩）
     */
    public static String format(Map<String, Object> toolResult) {
        if (toolResult == null) return "{}";
        try {
            Map<String, Object> normalized = normalize(toolResult);
            return PRETTY_MAPPER.writeValueAsString(normalized);
        } catch (Exception e) {
            // 任何序列化异常都 fallback
            return fallbackFormat(toolResult);
        }
    }

    /**
     * 字符级截断 — 最后防线。
     *
     * @param json       JSON 字符串
     * @param maxLength  最大字符数
     * @return 截断后的字符串，末尾带标注
     */
    public static String truncate(String json, int maxLength) {
        if (json == null || json.length() <= maxLength) {
            return json;
        }
        // 尝试在最后一个完整JSON结构处截断（逗号或右括号）
        String truncated = json.substring(0, maxLength - 100);
        int lastBreak = findLastJsonBreak(truncated);
        if (lastBreak > maxLength * 0.7) {
            truncated = truncated.substring(0, lastBreak + 1);
        }
        return truncated + "\n... [内容过长已截断，原长度：" + json.length() + "字符，共约"
            + estimateTokens(json) + " tokens]";
    }

    // ═══════════════════════════════════════════
    // 内部处理
    // ═══════════════════════════════════════════

    /**
     * 归一化 Tool 结果：截断列表、压缩嵌套、保留关键字段。
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalize(Map<String, Object> toolResult) {
        Map<String, Object> normalized = new LinkedHashMap<>();

        // 保留元信息
        if (toolResult.containsKey("success")) {
            normalized.put("success", toolResult.get("success"));
        }
        if (toolResult.containsKey("message")) {
            normalized.put("message", toolResult.get("message"));
        }

        Object data = toolResult.get("data");
        if (data != null) {
            normalized.put("data", normalizeData(data, 0));
        }

        return normalized;
    }

    @SuppressWarnings("unchecked")
    private static Object normalizeData(Object data, int depth) {
        if (depth > MAX_NESTING_DEPTH) {
            return data.toString();
        }

        if (data instanceof List<?> list) {
            int total = list.size();
            if (total > MAX_LIST_ITEMS) {
                List<Object> truncated = new ArrayList<>(list.subList(0, MAX_LIST_ITEMS));
                Map<String, Object> wrapper = new LinkedHashMap<>();
                wrapper.put("items", truncated);
                wrapper.put("_total", total);
                wrapper.put("_note", "结果过长，仅展示前" + MAX_LIST_ITEMS + "条");
                return wrapper;
            }
            return list.stream()
                .map(item -> normalizeData(item, depth + 1))
                .toList();
        }

        if (data instanceof Map<?, ?> map) {
            Map<String, Object> flat = new LinkedHashMap<>();
            map.forEach((k, v) -> flat.put(k.toString(), normalizeData(v, depth + 1)));
            return flat;
        }

        return data;
    }

    /**
     * 寻找 JSON 最后一个可安全截断的位置。
     */
    private static int findLastJsonBreak(String text) {
        int comma = text.lastIndexOf(',');
        int brace = text.lastIndexOf('}');
        int bracket = text.lastIndexOf(']');
        return Math.max(comma, Math.max(brace, bracket));
    }

    /**
     * 粗略估算 Token 数（中文字符 ≈ 1 token，英文 ≈ 0.25 token）。
     */
    private static int estimateTokens(String text) {
        int chinese = 0, other = 0;
        for (char c : text.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) chinese++;
            else other++;
        }
        return chinese + (other / 4);
    }

    private static String fallbackFormat(Map<String, Object> toolResult) {
        return "{success=" + toolResult.get("success")
            + ", message=" + toolResult.get("message")
            + ", data=" + toolResult.get("data") + "}";
    }
}
