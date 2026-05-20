package com.atlas.react;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Set;

/**
 * ReAct 记忆体 — 单次 ReAct 请求的中枢记忆。
 *
 * <p>负责按顺序维护 Thought → Action → Observation 循环的每一步记录，
 * 并提供重复动作检测、历史格式化等能力。</p>
 *
 * <p>线程安全：当前设计为单线程内使用（由 {@link ReActEngine} 串行驱动），
 * 未做显式同步。如需并发访问，需外部加锁。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M3.2
 */
public class ReActMemory {

    private static final Logger log = LoggerFactory.getLogger(ReActMemory.class);

    /** 步骤序号递增器 */
    private int counter = 0;

    /** 按序存储的 ReAct 步骤 */
    private final List<Step> stepList = new ArrayList<>();

    /** 已访问动作集合（去重用），格式：toolName + ':' + 规范化 JSON 参数 */
    private final Set<String> visitedKeys = new LinkedHashSet<>();

    private final ObjectMapper objectMapper;

    public ReActMemory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * 添加一步 ReAct 记录。
     *
     * @param thought        LLM 本轮思考内容
     * @param toolName       本次调用的工具名（Final Answer 时可传空）
     * @param params         本次调用参数（Final Answer 时可传空）
     * @param observation    工具返回的观测结果文本 / JSON
     * @param success        工具执行是否成功
     * @param executionTimeMs 工具执行耗时（毫秒）
     */
    public void addStep(String thought,
                        String toolName,
                        Map<String, Object> params,
                        String observation,
                        boolean success,
                        long executionTimeMs) {
        int stepNum = ++counter;
        Step s = new Step(stepNum, thought, toolName, params, observation, success, executionTimeMs);
        stepList.add(s);

        // 只有 Action 步骤才生成重复检测 key
        if (toolName != null && !toolName.isBlank() && params != null) {
            String key = buildActionKey(toolName, params);
            visitedKeys.add(key);
            log.debug("[ReActMemory] Step {} added, actionKey={}", stepNum, key);
        }
    }

    /**
     * 获取全部步骤（不可变视图）。
     */
    public List<Step> steps() {
        return Collections.unmodifiableList(stepList);
    }

    /**
     * 返回已访问的动作 key 集合（不可变视图）。
     * 格式：{@code toolName:{"param1":"val1",...}}，用于检测重复 Action。
     */
    public Set<String> visitedActionKeys() {
        return Collections.unmodifiableSet(visitedKeys);
    }

    /**
     * 判断指定工具+参数是否已被执行过。
     */
    public boolean isDuplicateAction(String toolName, Map<String, Object> params) {
        String key = buildActionKey(toolName, params);
        return visitedKeys.contains(key);
    }

    /**
     * 将历史 Observation 格式化为文本，供 Prompt 注入。
     *
     * <p>会自动截断至 maxChars，超出时在末尾附加截断标记。</p>
     *
     * @param maxChars 最大字符数（含截断标记），建议 3000
     * @return 格式化后的历史文本
     */
    public String toObservationHistory(int maxChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 历史执行记录 ===\n");
        for (Step s : stepList) {
            sb.append("【第 ").append(s.step()).append(" 轮】\n");
            sb.append("Thought: ").append(s.thought()).append("\n");
            if (s.toolName() != null && !s.toolName().isBlank()) {
                sb.append("Action: ").append(s.toolName());
                if (s.params() != null) {
                    sb.append(" params=").append(jsonEncode(s.params()));
                }
                sb.append("\n");
            }
            sb.append("Observation: ").append(s.observation()).append("\n");
            sb.append("success=").append(s.success())
              .append(", costMs=").append(s.executionTimeMs()).append("\n\n");
        }

        String result = sb.toString();
        if (result.length() > maxChars) {
            String truncated = result.substring(0, maxChars - 50);
            return truncated + "\n... [历史记录过长，已截断，原始长度 " + result.length() + " 字符]";
        }
        return result;
    }

    /**
     * 生成简单的执行摘要（用于兜底返回或日志）。
     *
     * @return 包含每步工具名、是否成功、最终 Observation 的摘要文本
     */
    public String formatSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("ReAct 执行摘要：共 ").append(stepList.size()).append(" 轮\n");
        for (Step s : stepList) {
            sb.append("  轮次 ").append(s.step());
            if (s.toolName() != null && !s.toolName().isBlank()) {
                sb.append(" 工具=").append(s.toolName())
                  .append(" 成功=").append(s.success());
            } else {
                sb.append(" [FinalAnswer]");
            }
            // 只摘 Observation 前 120 字符
            String obs = s.observation() != null ? s.observation() : "";
            if (obs.length() > 120) {
                obs = obs.substring(0, 120) + "...";
            }
            sb.append(" 观测=").append(obs).append("\n");
        }
        return sb.toString();
    }

    // ── 内部辅助 ──

    /**
     * 构建重复检测用的规范化 key。
     * 先用 Jackson 将参数序列化为 JSON，失败则退化为 String.valueOf。
     */
    private String buildActionKey(String toolName, Map<String, Object> params) {
        String json = jsonEncode(params);
        return toolName + ":" + json;
    }

    private String jsonEncode(Map<String, Object> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            log.warn("[ReActMemory] Jackson 序列化失败，退化为 toString: {}", e.getMessage());
            return String.valueOf(params);
        }
    }

    /**
     * ReAct 单步记录 — 内部 record。
     *
     * @param step             轮次序号（从 1 开始）
     * @param thought          LLM 思考内容
     * @param toolName         工具名（Final Answer 时可为 null/blank）
     * @param params           调用参数（可为 null）
     * @param observation      工具返回 / 最终答案文本
     * @param success          工具执行是否成功（Final Answer 可忽略）
     * @param executionTimeMs  执行耗时（毫秒）
     */
    public record Step(
        int step,
        String thought,
        String toolName,
        Map<String, Object> params,
        String observation,
        boolean success,
        long executionTimeMs
    ) {}
}
