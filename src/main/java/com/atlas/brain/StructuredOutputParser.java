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
        String prompt = systemPrompt + "\n\n用户问题：" + userQuery
            + "\n\n必须严格输出以下 JSON 格式，不要 markdown 代码块：\n" + schema;

        for (int i = 0; i < MAX_RETRIES; i++) {
            try {
                String raw = chatClient.prompt().user(prompt).call().content();
                String cleaned = sanitize(raw);
                T result = objectMapper.readValue(cleaned, clazz);
                log.info("Parsed {} after {} attempt(s)", clazz.getSimpleName(), i + 1);
                return result;
            } catch (Exception e) {
                log.warn("Parse attempt {}/{} failed: {}", i + 1, MAX_RETRIES, e.getMessage());
                if (i == MAX_RETRIES - 1) {
                    throw new BrainParseException("Failed after " + MAX_RETRIES + " retries", e);
                }
                prompt += "\n\n之前输出解析失败: " + e.getMessage() + "，请严格按 JSON Schema 修正。";
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private String sanitize(String raw) {
        if (raw == null) return "{}";
        return raw.replaceAll("(?s)^\\s*```json\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
    }
}
