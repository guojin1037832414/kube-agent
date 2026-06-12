package com.atlas.config;

import com.atlas.intent.EmbeddingMatcher;
import com.atlas.intent.IntentRouter;
import com.atlas.audit.AgentAuditProperties;
import com.atlas.intent.config.IntentsLoader;
import com.atlas.intent.embedding.*;
import com.atlas.intent.llm.L3IntentClassifier;
import com.atlas.intent.rule.RuleMatcher;
import com.atlas.orchestrator.StreamingEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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
 * <p>中文说明：这里是 Agent 意图识别链路的装配点。L1 embedding 和 L3 LLM 都是增强能力，
 * L2/L4 规则层才是必须可用的稳定底座；因此可选 AI 能力失败时应降级，而不是阻断服务启动。</p>
 *
 * <p>安全边界：配置装配不等于运行时授权。即使 LLM/Embedding 可用，也只能用于意图分类或预筛，
 * 不能绕过 SafeToolExecutor、HITL、审计、kube-manager 权限、Memory/RAG source custody 或
 * release gate。</p>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
@Configuration
@EnableConfigurationProperties({EmbeddingConfig.class, AgentAuditProperties.class})
public class AtlasConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AtlasConfiguration.class);

    /**
     * Intent 加载器。
     *
     * <p>中文说明：从静态意图定义加载路由知识，给规则层和 LLM 分类层共享同一份基础语义。</p>
     */
    @Bean
    public IntentsLoader intentsLoader() {
        return new IntentsLoader();
    }

    /**
     * L2/L4 规则匹配器 — 永不失败。
     *
     * <p>安全边界：规则层是启动底线；不能因为模型、embedding 或外部配置缺失而让 Agent 完全失明。</p>
     */
    @Bean
    public RuleMatcher ruleMatcher(IntentsLoader intentsLoader) {
        return new RuleMatcher(intentsLoader);
    }

    /**
     * L1 语义预筛 — 条件创建。
     * 内部完整创建 Embedding 链，任何环节失败 → 返回 null。
     *
     * <p>中文说明：Embedding 是“更聪明的召回”，不是权限来源；初始化失败只关闭 L1，
     * 不影响 L2/L4 规则和后续 fail-closed 执行边界。</p>
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
     * <p>要求同时满足：1) api-key 已配置 2) Spring AI 自动配置成功创建了 ChatClient.Builder。
     * 任一不满足则禁用 L3，回退到 L2/L4 规则匹配。不会阻断服务启动。</p>
     *
     * <p>安全边界：L3 只做分类建议，不调用 Tool、不写 Memory/RAG、不访问 kube-manager；
     * API key 缺失时必须显式禁用，不能用占位符触发远端调用。</p>
     */
    @Bean
    public L3IntentClassifier l3IntentClassifier(
            Environment env,
            IntentsLoader intentsLoader,
            @Autowired(required = false) ChatClient.Builder chatClientBuilder) {
        
        String apiKey = env.getProperty("spring.ai.openai.api-key");
        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("${")) {
            log.warn("[AtlasConfiguration] spring.ai.openai.api-key 未配置，L3 LLM 分类器禁用");
            return null;
        }
        if (chatClientBuilder == null) {
            log.warn("[AtlasConfiguration] ChatClient.Builder 不可用（Spring AI 自动配置未生效），L3 禁用");
            return null;
        }
        try {
            return new L3IntentClassifier(chatClientBuilder, intentsLoader, 0.70);
        } catch (Exception e) {
            log.warn("[AtlasConfiguration] L3 LLM 分类器初始化失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 意图路由器 — 所有可选依赖用 required=false，确保任意层失效都能启动。
     *
     * <p>中文说明：IntentRouter 汇总 L1/L2/L3/L4 的候选结论，后续仍要进入 Orchestrator、
     * Graph 和 Tool 安全边界，不能把意图命中直接当作执行许可。</p>
     */
    @Bean
    public IntentRouter intentRouter(
            @Autowired(required = false) EmbeddingMatcher embeddingMatcher,
            RuleMatcher ruleMatcher,
            @Autowired(required = false) L3IntentClassifier l3IntentClassifier,
            EmbeddingConfig config) {
        return new IntentRouter(embeddingMatcher, ruleMatcher, l3IntentClassifier, config);
    }

    /**
     * SSE 发射器。
     *
     * <p>安全边界：SSE 只负责事件推送和前端展示，不代表 Tool 执行已经发生，也不携带 token。</p>
     */
    @Bean
    public StreamingEmitter streamingEmitter() {
        return new StreamingEmitter();
    }
}
