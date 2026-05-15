package com.atlas.brain;

import java.time.Instant;

public record AtlasMessage(
    String role,
    String content,
    String toolName,
    Instant timestamp
) {
    public static AtlasMessage user(String content) {
        return new AtlasMessage("user", content, null, Instant.now());
    }
    public static AtlasMessage assistant(String content) {
        return new AtlasMessage("assistant", content, null, Instant.now());
    }
    public static AtlasMessage tool(String content, String toolName) {
        return new AtlasMessage("tool", content, toolName, Instant.now());
    }
}
