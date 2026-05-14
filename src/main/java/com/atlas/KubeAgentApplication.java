package com.atlas;

import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Atlas v3.1 — AI Agent for kube-manager 主入口
 *
 * <p><b>自动配置排除说明</b>：Spring AI 1.1.6 的 OpenAI 自动配置类
 * 在缺少 api-key 时会强制抛出异常（即使该 bean 从未被使用）。
 * P1 阶段不依赖 LLM（L3 降级关闭），因此排除所有 OpenAI 自动配置，
 * 避免启动时级联爆炸。L3 启用时再解除排除。</p>
 *
 * @author Atlas Team
 * @version 3.1.0-SNAPSHOT
 * @since 2026-05-14
 */
@SpringBootApplication(
    scanBasePackages = "com.atlas",
    exclude = {
        OpenAiChatAutoConfiguration.class,
        OpenAiEmbeddingAutoConfiguration.class,
        OpenAiAudioSpeechAutoConfiguration.class,
        OpenAiAudioTranscriptionAutoConfiguration.class,
        OpenAiImageAutoConfiguration.class,
        OpenAiModerationAutoConfiguration.class
    }
)
public class KubeAgentApplication {

    /**
     * 应用主入口
     *
     * @param args 命令行参数，支持覆盖配置如 --spring.ai.openai.api-key=xxx
     */
    public static void main(String[] args) {
        SpringApplication.run(KubeAgentApplication.class, args);
    }
}
