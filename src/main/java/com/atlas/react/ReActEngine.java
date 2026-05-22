package com.atlas.react;

import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.ToolParameterNormalizer;
import com.atlas.tool.core.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final ToolParameterNormalizer parameterNormalizer;

    /** LLM 超时调用专用后台线程池（daemon，避免阻塞 JVM 退出） */
    private final ExecutorService llmExecutor;

    public ReActEngine(ChatModel chatModel,
                       ObjectMapper objectMapper,
                       ToolRegistry toolRegistry,
                       ReActPromptBuilder promptBuilder) {
        this(chatModel, objectMapper, toolRegistry, promptBuilder, new ToolParameterNormalizer());
    }

    @Autowired
    public ReActEngine(ChatModel chatModel,
                       ObjectMapper objectMapper,
                       ToolRegistry toolRegistry,
                       ReActPromptBuilder promptBuilder,
                       ToolParameterNormalizer parameterNormalizer) {
        this.chatClient = ChatClient.builder(chatModel).build();
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        this.promptBuilder = promptBuilder;
        this.parameterNormalizer = parameterNormalizer != null ? parameterNormalizer : new ToolParameterNormalizer();
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
     * @param initialParams 初始参数（会与每轮 Action params 合并，并透传至工具调用）
     * @return {@link ReActResult} 包含最终答案或失败信息
     */
    public ReActResult run(String userQuery, Map<String, Object> initialParams) {
        return runWithEvents(userQuery, initialParams, ReActEventSink.NOOP);
    }

    /**
     * 执行 ReAct 推理循环，并在关键节点向外发送过程事件。
     *
     * <p>该方法仍然是同步执行，只是把原本黑盒的 Thought/Action/Observation
     * 生命周期通过 {@link ReActEventSink} 暴露给上层编排器。这样既不破坏原有
     * {@link #run(String, Map)} 调用，又能让 SSE 前端实时看到工具调用进度。</p>
     *
     * @param userQuery     用户原始查询
     * @param initialParams 初始上下文参数
     * @param eventSink     事件接收器；为空时自动降级为 NOOP
     * @return ReAct 最终聚合结果
     */
    public ReActResult runWithEvents(String userQuery,
                                     Map<String, Object> initialParams,
                                     ReActEventSink eventSink) {
        ReActEventSink sink = eventSink != null ? eventSink : ReActEventSink.NOOP;
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
                emitEvent(sink, ReActEvent.thinking(steps, "步骤 " + steps + "：正在分析问题并规划下一步..."));

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
                    emitEvent(sink, ReActEvent.error(steps, "LLM 调用超时，已基于现有信息生成兜底摘要"));
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
                    emitEvent(sink, ReActEvent.content(steps, finalAnswer));
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
                    emitEvent(sink, ReActEvent.content(steps, finalAnswer));
                    break;
                }

                String toolName = action.toolName();
                Map<String, Object> params = mergeInitialAndActionParams(toolName, initialParams, action.params());
                emitEvent(sink, ReActEvent.thinking(steps, thought));

                // 5. 工具可见性检查
                if (!toolRegistry.isVisible(toolName)) {
                    String obs = "工具 '" + toolName + "' 对当前用户不可见或不存在。请只调用可见工具列表中的工具。";
                    memory.addStep(thought, toolName, params, obs, false, 0);
                    emitEvent(sink, ReActEvent.error(steps, obs));
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
                    emitEvent(sink, ReActEvent.error(steps, "检测到重复工具调用，已终止循环并基于现有结果作答"));
                    break;
                }

                // 7. 执行工具
                long toolStartMs = System.currentTimeMillis();
                String observation;
                boolean toolSuccess;
                try {
                    emitEvent(sink, ReActEvent.toolStart(steps, toolName, params));
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
                String rawObservation = observation;
                observation = truncateObservation(observation, MAX_OBSERVATION_CHARS);
                boolean observationTruncated = rawObservation != null && observation != null
                    && rawObservation.length() > observation.length();
                emitEvent(sink, ReActEvent.toolDone(steps, toolName, toolSuccess, toolCostMs));
                emitEvent(sink, ReActEvent.observation(steps, toolName, observation, observationTruncated));

                // 9. 记录记忆
                memory.addStep(thought, toolName, params, observation, toolSuccess, toolCostMs);
                log.info("[ReActEngine] 第 {} 轮完成，tool={}, success={}, costMs={}",
                    steps, toolName, toolSuccess, toolCostMs);

                // 10. 目标资源未找到时提前收敛。
                // K8s 诊断中，如果用户明确指定了资源，而工具已经返回“未找到 Pod/资源”，
                // 继续把全量列表喂给 LLM 做第三轮推理只会放大 token 和超时风险，还可能误诊其他资源。
                // 因此这里直接生成清晰的用户回复，并建议核对名称/namespace/集群。
                if (toolSuccess && isTargetResourceNotFoundObservation(rawObservation)) {
                    stopReason = "target_not_found";
                    finalAnswer = generateTargetNotFoundSummary(rawObservation, userQuery);
                    // 不在引擎内部额外发送 content，避免与 Orchestrator 统一 answer 输出重复。
                    // finalAnswer 会通过 ReActResult 返回，由上层统一以一次 content 事件推送给前端。
                    log.info("[ReActEngine] 目标资源未找到，提前终止 ReAct。tool={}, steps={}", toolName, steps);
                    break;
                }
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

        if (!"final_answer".equals(stopReason) && finalAnswer != null && !finalAnswer.isBlank()) {
            emitEvent(sink, ReActEvent.content(steps, finalAnswer));
        }
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
     * 安全发送 ReAct 事件。
     *
     * <p>事件发送失败不能影响主推理流程，因此这里捕获所有异常并仅记录 warn。
     * 这保证了 SSE 断开、前端刷新等传输层问题不会打断正在执行的诊断逻辑。</p>
     */
    private void emitEvent(ReActEventSink sink, ReActEvent event) {
        try {
            sink.accept(event);
        } catch (Exception e) {
            log.warn("[ReActEngine] ReAct 事件发送失败: type={}, error={}",
                event != null ? event.type() : "null", e.getMessage());
        }
    }

    /**
     * 合并初始上下文参数与本轮 Action 参数，并调用统一参数归一化器。
     *
     * <p><b>M5.5 多租户安全治理：</b>Action 参数来自 LLM，不可信；initialParams
     * 来自会话/Graph/认证链路，是 token、organizationId、conversationId、userId 等上下文
     * 的权威来源。因此合并时只允许 Action 补充业务参数，不允许覆盖或新增受保护上下文字段。</p>
     *
     * <p>参数别名归一化已经从 ReActEngine 内联逻辑抽离到 {@link ToolParameterNormalizer}。
     * ReActEngine 只负责传入当前 toolName，让 normalizer 可以按工具处理 {@code name}
     * 这类高歧义字段，避免全局误映射。</p>
     */
    private Map<String, Object> mergeInitialAndActionParams(String toolName,
                                                            Map<String, Object> initialParams,
                                                            Map<String, Object> actionParams) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (initialParams != null && !initialParams.isEmpty()) {
            merged.putAll(initialParams);
        }
        if (actionParams != null && !actionParams.isEmpty()) {
            actionParams.forEach((key, value) -> {
                if (!isProtectedContextParam(key)) {
                    merged.put(key, value);
                }
            });
        }
        return parameterNormalizer.normalize(toolName, merged);
    }

    /**
     * 判断参数名是否属于会话/认证上下文字段。
     *
     * <p>这些字段不是业务查询条件，不能由 LLM Action 或用户自然语言改写。
     * 尤其 {@code organizationId/orgId} 决定租户 path，必须只来自认证上下文。</p>
     */
    private boolean isProtectedContextParam(String key) {
        if (key == null) {
            return false;
        }
        return PROTECTED_CONTEXT_PARAMS.contains(key);
    }

    private static final Set<String> PROTECTED_CONTEXT_PARAMS = Set.of(
        "token",
        "organizationId",
        "orgId",
        "conversationId",
        "conversation_id",
        "userId",
        "user_id"
    );

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

        // 头尾保留：K8s/日志类 Observation 通常头部包含概要，尾部可能包含最近错误或列表末尾线索。
        // 只保留头部会丢失后续判断依据；因此按 2/3 头部 + 1/3 尾部分配预算。
        String marker = "\n... [截断/不完整，原始长度 " + text.length() + " 字符，已省略中间部分] ...\n";
        int bodyBudget = Math.max(0, maxChars - marker.length());
        if (bodyBudget <= 0) {
            return marker;
        }
        int headLength = Math.max(1, bodyBudget * 2 / 3);
        int tailLength = Math.max(1, bodyBudget - headLength);
        if (headLength + tailLength >= text.length()) {
            return text;
        }
        return text.substring(0, headLength) + marker + text.substring(text.length() - tailLength);
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
     * 判断 Observation 是否表示“用户指定的目标资源不存在”。
     *
     * <p>当前 kube-manager 兼容接口在精确资源未命中时，常返回类似
     * “未找到 Pod nginx-1，返回 Pod 列表”的成功响应。该响应虽然 success=true，
     * 但语义上已经无法继续诊断指定目标；若继续进入下一轮 LLM，模型会拿全量列表猜测，
     * 既浪费 token，也容易误诊。因此需要在引擎层做收敛。</p>
     *
     * @param observation 原始工具返回字符串
     * @return true 表示应提前停止并请用户确认资源信息
     */
    private boolean isTargetResourceNotFoundObservation(String observation) {
        if (observation == null || observation.isBlank()) {
            return false;
        }
        String normalized = observation.toLowerCase();
        return (observation.contains("未找到") || normalized.contains("not found"))
            && (observation.contains("返回") || normalized.contains("return"))
            && (observation.contains("列表") || normalized.contains("list"));
    }

    /**
     * 生成目标资源未找到时的用户友好回复。
     *
     * <p>这里不再调用 LLM 做额外总结，保证低延迟、低 token、稳定可控。
     * 后续若工具层返回结构化 candidates，可在此追加 Top 3~5 候选资源。</p>
     */
    private String generateTargetNotFoundSummary(String observation, String userQuery) {
        String shortObservation = truncate(observation, 500);
        return "没有找到你指定的目标资源，所以我先停止继续自动诊断，避免基于其它资源误判。\n\n"
            + "**当前结论**：工具返回了“未找到目标资源”的结果。\n"
            + "**原始问题**：" + userQuery + "\n"
            + "**工具提示**：" + shortObservation + "\n\n"
            + "建议哥哥核对下面几项后再继续：\n"
            + "1. 资源名称是否拼写正确；\n"
            + "2. namespace 是否正确；\n"
            + "3. 当前集群/登录账号是否是预期环境；\n"
            + "4. 资源是否已经被删除，或还没有创建成功。\n\n"
            + "你可以直接告诉我正确的资源名和 namespace，我会继续接着诊断。";
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
