package com.atlas.intent.llm;

import com.atlas.intent.config.IntentDefinition;
import com.atlas.intent.config.IntentsLoader;
import com.atlas.intent.core.IntentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

/**
 * L3 层 — LLM 语义意图分类器。
 *
 * <p>仅当 L1 Embedding（高置信度短路已跳过）和 L2 规则匹配均未命中时，
 * 由 {@link com.atlas.intent.IntentRouter} 调用本组件进行 LLM 语义分类。</p>
 *
 * <p><b>关键设计决策：</b></p>
 * <ol>
 *   <li><b>BeanOutputConverter</b>：强类型 POJO 输出，自动注入 JSON Schema 到 prompt，
 *       避免 {@code Map<String, Object>} 的运行时 cast 风险。</li>
 *   <li><b>Prompt 分离</b>：System 放指令 + 意图定义（可缓存），User 只放 query，
 *       减少重复 Token，利于代理侧 prompt cache 命中。</li>
 *   <li><b>异常即降级</b>：任何网络、解析、超时异常均返回 {@code null}，
 *       由 L4 模糊兜底接手，确保路由链不中断。</li>
 *   <li><b>非法 ID 二次校验</b>：LLM 偶发幻觉可能输出不在候选列表中的 intentId，
 *       通过 {@link IntentsLoader#getIntent(String)} 二次过滤。</li>
 * </ol>
 *
 * @author Atlas Team
 * @since 3.1.0-P1
 */
/**
 * L3 层 — LLM 意图分类器。
 *
 * <p><b>注意</b>：无 {@code @Component}，由 {@link com.atlas.config.AtlasConfiguration} 条件创建。</p>
 */
public class L3IntentClassifier {

    private static final Logger log = LoggerFactory.getLogger(L3IntentClassifier.class);

    private final ChatClient chatClient;
    private final IntentsLoader intentsLoader;
    private final double confidenceThreshold;
    private final BeanOutputConverter<L3ClassificationResult> outputConverter;

    // Prompt 预编译模板（System 部分），运行时仅需替换 {{intents}} 占位符
    private String systemPromptTemplate;

    // 意图列表静态快照（若 intents.yml 热重载可刷新）
    private volatile String intentsSnapshot;

    public L3IntentClassifier(
            ChatClient.Builder chatClientBuilder,
            IntentsLoader intentsLoader,
            @Value("${atlas.intent.l3-confidence-threshold:0.70}") double confidenceThreshold) {

        this.intentsLoader = intentsLoader;
        this.confidenceThreshold = confidenceThreshold;

        // 1. ChatClient 初始化，附加日志 Advisor（DEBUG 级别输出 request/response）
        this.chatClient = chatClientBuilder
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();

        // 2. BeanOutputConverter 初始化：自动生成 JSON Schema 并用于格式化输出
        this.outputConverter = new BeanOutputConverter<>(L3ClassificationResult.class);
    }

    @PostConstruct
    public void init() {
        // 3. 预编译 System Prompt 模板（不含意图列表）
        this.systemPromptTemplate = buildSystemPromptTemplate();
        // 4. 预渲染意图列表快照（首次启动时）
        refreshIntentsSnapshot();
    }

    /**
     * 执行 L3 LLM 语义分类。
     *
     * @param query 用户原始输入（非空）
     * @return 命中且置信度达标的 {@link IntentResult}；异常/未命中/置信度不足均返回 {@code null}
     */
    public IntentResult classify(String query) {
        if (query == null || query.isBlank()) {
            log.warn("[L3] 收到空 query，直接返回 null");
            return null;
        }

        log.debug("[L3] 进入 LLM 分类: {}", query);

        try {
            // 动态组装 System Prompt：模板 + 意图列表快照 + JSON Schema 格式指令
            String systemPrompt = systemPromptTemplate
                    .replace("{{intents}}", intentsSnapshot)
                    + "\n\n" + outputConverter.getFormat();

            // 调用 LLM
            L3ClassificationResult result = chatClient.prompt()
                    .system(systemPrompt)
                    .user("用户输入：" + query)
                    .call()
                    .entity(outputConverter);

            if (result == null) {
                log.warn("[L3] LLM 返回空结果");
                return null;
            }

            log.info("[L3] 原始分类结果: intentId={}, confidence={}, reasoning={}",
                    result.intentId(), result.confidence(), result.reasoning());

            // 置信度阈值判断
            if (!result.isConfident(confidenceThreshold)) {
                log.warn("[L3] 置信度不足 ({})，低于阈值 {}，回退到 L4",
                        result.confidence(), confidenceThreshold);
                return null;
            }

            // 未知意图兜底
            if (result.isUnknown()) {
                log.warn("[L3] LLM 返回 unknown 或低置信度，回退到 L4");
                return null;
            }

            // 二次校验：防止 LLM 幻觉出非法 intentId
            var def = intentsLoader.getIntent(result.intentId());
            if (def == null) {
                log.error("[L3] LLM 返回了不在候选列表中的 intentId: '{}', 降级到 L4", result.intentId());
                return null;
            }

            // 封装为统一 IntentResult
            return new IntentResult(
                    def.intentId(),
                    def.description(),
                    result.confidence(),
                    "L3",
                    def.agent(),
                    def.level(),
                    query
            );

        } catch (Exception e) {
            // 异常降级：网络超时、JSON 解析失败、代理故障等，一律不抛异常，由 L4 兜底
            log.error("[L3] LLM 调用异常，降级到 L4: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 刷新意图列表快照。若后续支持 intents.yml 热重载，可调用此方法。
     */
    public void refreshIntentsSnapshot() {
        this.intentsSnapshot = renderIntentsSnapshot(intentsLoader.getAllIntents());
        log.info("[L3] 意图列表快照已刷新，共 {} 个意图", intentsLoader.getAllIntents().size());
    }

    // ==================== Prompt 构建辅助 ====================

    private String buildSystemPromptTemplate() {
        return """
            你是一名意图分类专家。你的任务是根据用户的输入，从预定义的意图列表中选择最匹配的一个。

            ## 输出格式要求
            你必须严格按照以下 JSON 格式输出，不要包含任何 markdown 代码块或其他说明文字：

            {
              "intentId": "意图ID",
              "confidence": 0.85,
              "reasoning": "简短的选择理由"
            }

            规则：
            1. intentId：必须从下方的"候选意图列表"中选择 exact match。如果完全无法匹配，使用 "unknown"。
            2. confidence：0.0~1.0。≥0.85 高置信度，0.70~0.85 中置信度，<0.70 低置信度。
            3. reasoning：不超过 30 个汉字，解释为什么选这个意图。

            ## 候选意图列表
            {{intents}}
            """;
    }

    /**
     * 将意图定义压缩为 prompt 友好的文本块。
     *
     * <p>只保留 id + description + 最多 2 个 examples，极大节省 Token。</p>
     */
    private String renderIntentsSnapshot(java.util.Collection<IntentDefinition> intents) {
        return intents.stream()
                .map(def -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("- ").append(def.intentId()).append("：").append(def.description());
                    if (def.examples() != null && !def.examples().isEmpty()) {
                        List<String> ex = def.examples().stream()
                                .limit(2)
                                .collect(Collectors.toList());
                        sb.append("（示例：").append(String.join("；", ex)).append("）");
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));
    }
}
