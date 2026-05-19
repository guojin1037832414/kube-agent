package com.atlas.orchestrator.polish;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Tool执行结果LLM润色服务 — v3.1 B方案实现。
 *
 * <p><b>核心职责：</b></p>
 * <ul>
 *   <li>接收 ToolRegistry 返回的原始结构化数据（Map&lt;String,Object&gt;）</li>
 *   <li>按数据类型路由到对应的 PromptTemplate</li>
 *   <li>通过 ChatClient.stream() 异步流式生成自然语言表述</li>
 *   <li>异常时 fallback 到硬编码兜底格式（与原 M2.7 兼容）</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 在 AtlasOrchestrator 中：
 * polishingService.polishStream(toolResult, request.userQuery())
 *     .subscribe(
 *         chunk -> emit(emitter, "content", Map.of("content", chunk)),
 *         err  -> emit(emitter, "error", Map.of("content", err.getMessage())),
 *         ()   -> emit(emitter, "done", Map.of())
 *     );
 * }</pre>
 *
 * @author Atlas Team
 * @since 3.1.0-P3
 */
@Service
public class ToolResultPolishingService {

    private final ChatClient chatClient;
    private final PolishMetrics metrics;

    public ToolResultPolishingService(ChatModel chatModel, PolishMetrics metrics) {
        this.chatClient = ChatClient.builder(chatModel)
            .defaultAdvisors(new SimpleLoggerAdvisor())
            .build();
        this.metrics = metrics;
    }

    /**
     * 【同步润色】返回完整润色文本。
     *
     * <p>适用场景：</p>
     * <ul>
     *   <li>Tool 结果极短（&lt;100字），流式无收益</li>
     *   <li>Graph 模式（node_async 同步节点内）</li>
     *   <li>流式初始化失败后的 fallback</li>
     * </ul>
     *
     * @param toolResult ToolRegistry.execute() 返回的 Map
     * @param userQuery  用户原始问题（用于增强上下文，提升润色相关性）
     * @return 润色后的自然语言文本
     */
    public String polishSync(Map<String, Object> toolResult, String userQuery) {
        long start = System.currentTimeMillis();
        try {
            String resultJson = ToolResultFormatter.format(toolResult);
            String template = PolishPromptTemplate.select(toolResult);

            String polished = chatClient.prompt()
                .system(template)
                .user(buildUserContent(resultJson, userQuery))
                .call()
                .content();

            metrics.recordSync(System.currentTimeMillis() - start, resultJson.length());
            return polished;

        } catch (Exception e) {
            metrics.recordFailure("sync", e);
            return fallbackPolish(toolResult);
        }
    }

    /**
     * 【流式润色】返回 Flux&lt;String&gt;，逐 token 输出。
     *
     * <p><b>推荐用法：</b>与 SSE emitter 订阅，实现"实时打字"效果。</p>
     *
     * @param toolResult ToolRegistry.execute() 返回的 Map
     * @param userQuery  用户原始问题
     * @return 逐字流：从 LLM 第一个 token 到达即开始 emit
     */
    public Flux<String> polishStream(Map<String, Object> toolResult, String userQuery) {
        long start = System.currentTimeMillis();
        try {
            String resultJson = ToolResultFormatter.format(toolResult);

            // Token保护：超长截断
            if (resultJson.length() > ToolResultFormatter.MAX_CONTEXT_LENGTH) {
                resultJson = ToolResultFormatter.truncate(
                    resultJson, ToolResultFormatter.MAX_CONTEXT_LENGTH);
            }

            String template = PolishPromptTemplate.select(toolResult);

            return chatClient.prompt()
                .system(template)
                .user(buildUserContent(resultJson, userQuery))
                .stream()
                .content()
                // 超时保护：15秒无数据则降级
                .timeout(java.time.Duration.ofSeconds(15),
                    Flux.just("[润色超时，以下为原始结果]\n" + fallbackPolish(toolResult)))
                .doOnNext(chunk -> metrics.recordChunk(chunk.length()))
                .doOnComplete(() -> metrics.recordStreamComplete(
                    System.currentTimeMillis() - start))
                .doOnError(err -> metrics.recordFailure("stream", err))
                .onErrorResume(err -> Flux.just(fallbackPolish(toolResult)))
                .subscribeOn(Schedulers.boundedElastic());

        } catch (Exception e) {
            metrics.recordFailure("stream_init", e);
            return Flux.just(fallbackPolish(toolResult));
        }
    }

    // ═══════════════════════════════════════════
    // 私有辅助
    // ═══════════════════════════════════════════

    private String buildUserContent(String resultJson, String userQuery) {
        return """
            用户原始问题：%s

            工具返回的原始数据（JSON格式）：
            %s
            """.formatted(userQuery != null ? userQuery : "", resultJson);
    }

    /**
     * Fallback 兜底格式化 — 保证任何场景都有输出。
     *
     * <p>与原 AtlasOrchestrator M2.7 硬编码逻辑保持一致，
     * 确保润色失败时前端仍能看到结构化结果。</p>
     */
    private String fallbackPolish(Map<String, Object> toolResult) {
        if (toolResult == null) {
            return "❌ 工具返回结果为空";
        }
        boolean success = Boolean.TRUE.equals(toolResult.get("success"));
        String message = toolResult.get("message") != null
            ? toolResult.get("message").toString() : "";
        Object data = toolResult.get("data");

        StringBuilder sb = new StringBuilder();
        sb.append(success ? "✅ " : "❌ ").append(message);
        if (data != null) {
            sb.append("\n\n```\n").append(data).append("\n```");
        }
        return sb.toString();
    }
}
