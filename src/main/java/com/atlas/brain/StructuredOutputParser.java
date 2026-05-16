package com.atlas.brain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

/**
 * 结构化输出解析器 — 带容错和重试的 LLM JSON 解析。
 * <p>不持有 ChatClient（避免启动依赖），由调用方传入。</p>
 */
@Component
public class StructuredOutputParser {
    private static final Logger log = LoggerFactory.getLogger(StructuredOutputParser.class);
    private static final int MAX_RETRIES = 3;

    private final ObjectMapper objectMapper;

    public StructuredOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T parse(ChatClient chatClient, String userQuery, Class<T> clazz, String systemPrompt) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(clazz);
        String schema = converter.getFormat();

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                // 构造 user prompt：用显式标记分割用户问题和格式要求
                StringBuilder userPrompt = new StringBuilder();
                userPrompt.append("=== 用户问题 ===\n")
                         .append(userQuery).append("\n")
                         .append("=== 用户问题结束 ===\n\n")
                         .append("根据上面的用户问题，严格输出以下 JSON 格式，不要 markdown 代码块：\n")
                         .append(schema);

                if (i > 0) {
                    userPrompt.append("\n\n之前输出解析失败，请严格按 JSON Schema 修正。");
                }

                String raw = chatClient.prompt()
                        .system(systemPrompt)
                        .user(userPrompt.toString())
                        .call().content();

                String cleaned = sanitize(raw);
                T result = objectMapper.readValue(cleaned, clazz);
                log.info("Parsed {} after {} attempt(s)", clazz.getSimpleName(), i + 1);
                return result;
            } catch (Exception e) {
                log.warn("Parse attempt {}/{} failed: {}", i + 1, MAX_RETRIES, e.getMessage());
                if (i == MAX_RETRIES - 1) {
                    throw new BrainParseException("Failed after " + MAX_RETRIES + " retries", e);
                }
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private String sanitize(String raw) {
        if (raw == null) return "{}";
        return raw.replaceAll("(?s)^\\s*```json\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
    }
}
