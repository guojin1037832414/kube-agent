package com.atlas.brain;

/**
 * AtlasBrain 解析异常 — 当 LLM 结构化输出无法解析时抛出
 */
public class BrainParseException extends RuntimeException {
    public BrainParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
