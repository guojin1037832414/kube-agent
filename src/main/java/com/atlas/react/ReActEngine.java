package com.atlas.react;

import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct 推理引擎 — 手写 ReAct 循环核心。
 *
 * <p><b>设计决策：</b>不使用 Spring AI Alibaba 的 ReactAgent，
 * 而是手写 Thought → Action → Observation 循环，
 * 避免框架 ReactAgent 的 outputKey 污染 AssistantMessage 并破坏结构化路由。</p>
 *
 * <p>执行模型：</p>
 * <ol>
 *   <li>初始化 {@link ReActMemory} 与 {@link ReActPromptBuilder} 系统提示词</li>
 *   <li>while 循环驱动 LLM 推理</li>
 *   <li>每轮 LLM 输出通过正则解析为 Thought +（Action 或 Final Answer）</li>
 *   <li>若 Action：调用 {@link ToolRegistry#resolve} 执行工具，结果 JSON 序列化后作为 Observation 回注</li>
 *   <li>若 Final Answer：直接返回 {@link ReActResult}</li>
 *   <li>循环终止条件：Final Answer / 最大步数 / 重复动作 / LLM 超时</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-M3.2
 */
@Component
public class ReActEngine {

    private static final Logger log = LoggerFactory.getLogger(ReActEngine.class);

    // ── MVP 常量 ──

    /** 最大 ReAct 步数，防止无限循环 */
    public static final int DEFAULT_MAX_STEPS = 6;

    /** Observation 文本最大字符数，超长时截断 */
    public static final int MAX_OBSERVATION_CHARS = 3000;

    /** LLM 单次调用超时秒数 */
    public static final long LLM_TIMEOUT_SECONDS = 30L;

    // ── 正则解析器（预编译，线程安全） ──

    /** 捕获 Thought 行，兼容中英文冒号 */
    private static final Pattern THOUGHT_PATTERN = Pattern.compile(
        "(?:^|\\n)\\s*Thought\\s*[:：]\\s*(.+?)(?=\\n(?:Action|Final Answer)\\s*[:：]|\\Z)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 捕获 Action JSON：Action: {"tool":"...","params":{...}} */
    private static final Pattern ACTION_JSON_PATTERN = Pattern.compile(
        "(?:^|\\n)\\s*Action\\s*[:：]\\s*(\\{.+?\\})\\s*(?=\\n|$)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 备选格式：Action: toolname {"key":"val"} */
    private static final Pattern ACTION_FALLBACK_PATTERN = Pattern.compile(
        "(?:^|\\n)\\s*Action\\s*[:：]\\s*(\\S+)\\s*(\\{.+?\\})?\\s*(?=\\n|$)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 捕获 Final Answer */
    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile(
        "(?:^|\\n)\\s*Final Answer\\s*[:：]\\s*(.+?)(?=\\Z)",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    // ── 依赖 ──

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;
    private final ReActPromptBuilder promptBuilder;

    /** LLM 超时调用专用后台线程池（daemon，避免阻塞 JVM 退出） */
    private final ExecutorService llmExecutor;

    public ReActEngine(ChatModel chatModel,
                       ObjectMapper objectMapper,
                       ToolRegistry toolRegistry,
                       ReActPromptBuilder promptBuilder) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = promptBuilder;
        this.llmExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("react-llm-" + t.getId());
            return t;
        });
    }

    /**
     * 执行 ReAct 推理循环。
     *
     * @param userQuery    用户原始查询
     * @param initialParams 初始参数（MVP 暂不深度使用，透传至工具调用）
     * @return {@link ReActResult} 包含最终答案或失败信息
     */
    public ReActResult run(String userQuery, Map<String, Object> initialParams) {
        long totalStartMs = System.currentTimeMillis();
        ReActMemory memory = new ReActMemory(objectMapper);

        log.info("[ReActEngine] 开始推理，query={}", userQuery);

        String finalAnswer = null;
        String stopReason = null;
        int steps = 0;

        try {
            while (steps < DEFAULT_MAX_STEPS) {
                steps++;
                log.debug("[ReActEngine] ===== 第 {} 轮开始 =====", steps);

                // 1. 构建当前轮次系统提示词
                String systemPrompt = promptBuilder.buildSystemPrompt(userQuery, memory);

                // 2. 带超时调用 LLM
                String llmOutput;
                try {
                    llmOutput = callLlmWithTimeout(systemPrompt, userQuery, LLM_TIMEOUT_SECONDS);
                } catch (TimeoutException te) {
                    log.warn("[ReActEngine] 第 {} 轮 LLM 超时", steps);
                    stopReason = "timeout";
                    finalAnswer = generateTimeoutSummary(memory, userQuery);
                    break;
                }

                log.debug("[ReActEngine] LLM raw output: {}", truncate(llmOutput, 500));

                // 3. 解析输出
                String thought = extractThought(llmOutput).orElse("（未解析到 Thought）");
                Optional<String> finalAnswerOpt = extractFinalAnswer(llmOutput);

                if (finalAnswerOpt.isPresent()) {
                    // ── 模式B：Final Answer ──
                    finalAnswer = finalAnswerOpt.get().trim();
                    memory.addStep(thought, null, null, "[Final Answer]", true, 0);
                    stopReason = "final_answer";
                    log.info("[ReActEngine] 收到 Final Answer，停止。steps={}", steps);
                    break;
                }

                // 4. 解析 Action
                ActionParseResult action = parseAction(llmOutput);
                if (action == null || action.toolName() == null) {
                    log.warn("[ReActEngine] 第 {} 轮未解析到有效 Action，视为 Final Answer", steps);
                    finalAnswer = thought; // 退化为用 Thought 作为答案
                    memory.addStep(thought, null, null, finalAnswer, true, 0);
                    stopReason = "no_action_parsed";
                    break;
                }

                String toolName = action.toolName();
                Map<String, Object> params = action.params();

                // 5. 工具可见性检查
                if (!toolRegistry.isVisible(toolName)) {
                    String obs = "工具 '" + toolName + "' 对当前用户不可见或不存在。请只调用可见工具列表中的工具。";
                    memory.addStep(thought, toolName, params, obs, false, 0);
                    log.warn("[ReActEngine] 调用不可见工具: {}", toolName);
                    continue;
                }

                // 6. 重复动作检测
                if (memory.isDuplicateAction(toolName, params)) {
                    log.warn("[ReActEngine] 检测到重复 Action: {}，强制终止循环", toolName);
                    stopReason = "duplicate_action";
                    finalAnswer = "检测到重复调用工具 '" + toolName + "'，基于已有观测结果作答。\n\n"
                        + memory.formatSummary();
                    memory.addStep(thought, toolName, params,
                        "[重复动作，循环终止]", false, 0);
                    break;
                }

                // 7. 执行工具
                long toolStartMs = System.currentTimeMillis();
                String observation;
                boolean toolSuccess;
                try {
                    ToolRegistry.ToolMetadata meta = toolRegistry.resolve(toolName);
                    Map<String, Object> toolResult = meta.instance().execute(params);
                    observation = serializeToolResult(toolResult);
                    toolSuccess = isToolResultSuccess(toolResult);
                } catch (Exception e) {
                    log.error("[ReActEngine] 工具执行异常: tool={}, error={}", toolName, e.getMessage(), e);
                    observation = "工具执行异常: " + e.getMessage();
                    toolSuccess = false;
                }
                long toolCostMs = System.currentTimeMillis() - toolStartMs;

                // 8. Observation 截断
                observation = truncateObservation(observation, MAX_OBSERVATION_CHARS);

                // 9. 记录记忆
                memory.addStep(thought, toolName, params, observation, toolSuccess, toolCostMs);
                log.info("[ReActEngine] 第 {} 轮完成，tool={}, success={}, costMs={}",
                    steps, toolName, toolSuccess, toolCostMs);
            }

            // 达到最大步数仍未结束
            if (stopReason == null) {
                stopReason = "max_steps";
                if (finalAnswer == null) {
                    finalAnswer = generateMaxStepsSummary(memory, userQuery);
                }
                log.warn("[ReActEngine] 达到最大步数 {}，强制返回", DEFAULT_MAX_STEPS);
            }
        } catch (Exception e) {
            log.error("[ReActEngine] 循环异常终止", e);
            stopReason = "exception";
            if (finalAnswer == null) {
                finalAnswer = "ReAct 执行过程中发生异常: " + e.getMessage();
            }
        }

        long totalMs = System.currentTimeMillis() - totalStartMs;
        boolean success = finalAnswer != null && !finalAnswer.isBlank();
        if (!success) {
            finalAnswer = "未能生成有效回答，请稍后重试。";
        }

        log.info("[ReActEngine] 推理结束, stopReason={}, steps={}, totalMs={}, success={}",
            stopReason, steps, totalMs, success);

        return new ReActResult(success, finalAnswer, memory.steps(), totalMs, stopReason);
    }

    // ═══════════════════════════════════════════
    // 内部方法区
    // ═══════════════════════════════════════════

    /**
     * 带超时调用 LLM。
     *
     * @param systemPrompt 系统提示词
     * @param userQuery    用户查询
     * @param timeoutSec   超时秒数
     * @return LLM 输出文本
     * @throws TimeoutException 超时抛出
     */
    private String callLlmWithTimeout(String systemPrompt, String userQuery, long timeoutSec)
        throws TimeoutException {

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userQuery)
                    .call()
                    .content();
            } catch (Exception e) {
                throw new RuntimeException("LLM 调用失败: " + e.getMessage(), e);
            }
        }, llmExecutor);

        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM 调用被中断", ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("LLM 调用异常", cause);
        }
    }

    /**
     * 提取 Thought 内容。
     */
    private Optional<String> extractThought(String text) {
        Matcher m = THOUGHT_PATTERN.matcher(text);
        if (m.find()) {
            return Optional.of(m.group(1).trim());
        }
        return Optional.empty();
    }

    /**
     * 提取 Final Answer 内容。
     */
    private Optional<String> extractFinalAnswer(String text) {
        Matcher m = FINAL_ANSWER_PATTERN.matcher(text);
        if (m.find()) {
            return Optional.of(m.group(1).trim());
        }
        return Optional.empty();
    }

    /**
     * 解析 Action：优先 JSON 格式，回退到 fallback 格式。
     *
     * @return 解析结果，若未解析到则返回 null
     */
    private ActionParseResult parseAction(String text) {
        // 先尝试 JSON 格式：Action: {"tool":"...","params":{...}}
        Matcher mJson = ACTION_JSON_PATTERN.matcher(text);
        if (mJson.find()) {
            String json = mJson.group(1).trim();
            try {
                Map<String, Object> map = objectMapper.readValue(json,
                    new TypeReference<Map<String, Object>>() {});
                Object toolObj = map.get("tool");
                Object paramsObj = map.get("params");
                String toolName = toolObj != null ? toolObj.toString() : null;
                @SuppressWarnings("unchecked")
                Map<String, Object> params = (paramsObj instanceof Map)
                    ? (Map<String, Object>) paramsObj : Map.of();
                return new ActionParseResult(toolName, params);
            } catch (Exception e) {
                log.warn("[ReActEngine] JSON Action 解析失败: {}, 尝试 fallback", e.getMessage());
            }
        }

        // fallback：Action: toolName { ...json... }
        Matcher mFallback = ACTION_FALLBACK_PATTERN.matcher(text);
        if (mFallback.find()) {
            String toolName = mFallback.group(1).trim();
            String paramsJson = mFallback.group(2);
            Map<String, Object> params = Map.of();
            if (paramsJson != null && !paramsJson.isBlank()) {
                try {
                    params = objectMapper.readValue(paramsJson.trim(),
                        new TypeReference<Map<String, Object>>() {});
                } catch (Exception e) {
                    log.warn("[ReActEngine] fallback 参数解析失败: {}", e.getMessage());
                }
            }
            return new ActionParseResult(toolName, params);
        }

        return null;
    }

    /**
     * 将工具返回结果序列化为 JSON 字符串。
     */
    private String serializeToolResult(Map<String, Object> result) {
        if (result == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.warn("[ReActEngine] 工具结果序列化失败: {}", e.getMessage());
            return String.valueOf(result);
        }
    }

    /**
     * 判断工具返回是否成功。
     */
    private boolean isToolResultSuccess(Map<String, Object> result) {
        if (result == null) return false;
        Object success = result.get(AtlasToolResult.KEY_SUCCESS);
        return Boolean.TRUE.equals(success);
    }

    /**
     * 截断 Observation 文本。
     *
     * @param text     原始文本
     * @param maxChars 最大字符数
     * @return 截断后文本（若超长附加截断标记）
     */
    private String truncateObservation(String text, int maxChars) {
        if (text == null) return "";
        if (text.length() <= maxChars) return text;
        return text.substring(0, maxChars - 50)
            + "... [截断/不完整，原始长度 " + text.length() + " 字符]";
    }

    /**
     * 截断任意文本（用于日志）。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }

    /**
     * 超时兜底摘要 — 基于已有 Observation 生成简要回答。
     */
    private String generateTimeoutSummary(ReActMemory memory, String userQuery) {
        String summary = memory.formatSummary();
        return "由于 LLM 调用超时，ReAct 推理未能完整完成。以下是已执行步骤的摘要：\n\n"
            + summary
            + "\n\n用户查询：" + userQuery
            + "\n建议：请稍后重试，或简化查询后再次提问。";
    }

    /**
     * 达到最大步数兜底摘要。
     */
    private String generateMaxStepsSummary(ReActMemory memory, String userQuery) {
        String summary = memory.formatSummary();
        return "ReAct 推理达到最大步数限制，基于当前已有信息作答。\n\n"
            + "已执行步骤摘要：\n" + summary
            + "\n\n用户查询：" + userQuery;
    }

    /**
     * Action 解析中间结果（内部 record）。
     */
    private record ActionParseResult(String toolName, Map<String, Object> params) {}
}
