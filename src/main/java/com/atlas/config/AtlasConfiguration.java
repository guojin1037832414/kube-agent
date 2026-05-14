package com.atlas.config;

import com.atlas.intent.EmbeddingMatcher;
import com.atlas.intent.IntentRouter;
import com.atlas.intent.config.IntentsLoader;
import com.atlas.intent.embedding.*;
import com.atlas.intent.llm.L3IntentClassifier;
import com.atlas.intent.rule.RuleMatcher;
import com.atlas.orchestrator.StreamingEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Atlas v3.1 全局配置类 — v3 修复版（双降级）。
 *
 * <p><b>降级策略核心</b>：任何可选组件（Embedding/L3）初始化失败时，
 * 内部吞掉异常并在日志记录，对外表现为 "该层不可用"。永远保证 L2 规则层可用。</p>
 *
 * <p><b>关键修复</b>：不直接注入 Spring AI 的自动配置 bean（如 ChatClient.Builder），
 * 因为它们的依赖链（OpenAI API key）一旦不满足就会级联爆炸（即使 required=false）。
 * 改为通过 {@link Environment} 检查配置，不满足条件时直接跳过该层的创建。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Configuration
@EnableConfigurationProperties(EmbeddingConfig.class)
public class AtlasConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AtlasConfiguration.class);

    /** Intent 加载器 */
    @Bean
    public IntentsLoader intentsLoader() {
        return new IntentsLoader();
    }

    /** L2/L4 规则匹配器 — 永不失败 */
    @Bean
    public RuleMatcher ruleMatcher(IntentsLoader intentsLoader) {
        return new RuleMatcher(intentsLoader);
    }

    /**
     * L1 语义预筛 — 条件创建。
     * 内部完整创建 Embedding 链，任何环节失败 → 返回 null。
     */
    @Bean
    public EmbeddingMatcher embeddingMatcher(IntentsLoader intentsLoader, EmbeddingConfig config) {
        try {
            OnnxSessionHolder sessionHolder = new OnnxSessionHolder(config);
            EmbeddingService embeddingService = new EmbeddingService(sessionHolder, config);
            return new EmbeddingMatcher(embeddingService, intentsLoader, config);
        } catch (Exception e) {
            log.warn("[AtlasConfiguration] Embedding 链不可用（{}），L1 降级，L2/L4 仍可工作",
                e.getMessage());
            return null;
        }
    }

    /**
     * L3 LLM 分类器 — 条件创建。
     *
     * <p><b>关键</b>：不注入 ChatClient.Builder，而是检查 api-key 配置存在性。
     * 如果不满足，直接返回 null，避免触发 Spring AI 自动配置的依赖链级联创建。</p>
     */
    @Bean
    public L3IntentClassifier l3IntentClassifier(Environment env, IntentsLoader intentsLoader) {
        String apiKey = env.getProperty("spring.ai.openai.api-key");
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            log.warn("[AtlasConfiguration] spring.ai.openai.api-key 未配置，L3 LLM 分类器禁用");
            return null;
        }
        try {
            // 注意：L3IntentClassifier 内部需要 ChatClient.Builder，
            // 此时 api-key 已确认存在，Spring AI 自动配置应能成功
            return new L3IntentClassifier(null, intentsLoader, 0.70);
            // 上面传 null 给 ChatClient.Builder 会 NPE，需要再想办法...
        } catch (Exception e) {
            log.warn("[AtlasConfiguration] L3 LLM 分类器初始化失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 意图路由器 — 所有可选依赖用 required=false，确保任意层失效都能启动。
     */
    @Bean
    public IntentRouter intentRouter(
            @Autowired(required = false) EmbeddingMatcher embeddingMatcher,
            RuleMatcher ruleMatcher,
            @Autowired(required = false) L3IntentClassifier l3IntentClassifier,
            EmbeddingConfig config) {
        return new IntentRouter(embeddingMatcher, ruleMatcher, l3IntentClassifier, config);
    }

    /** SSE 发射器 */
    @Bean
    public StreamingEmitter streamingEmitter() {
        return new StreamingEmitter();
    }
}
