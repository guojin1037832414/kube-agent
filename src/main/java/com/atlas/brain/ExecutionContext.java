package com.atlas.brain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExecutionContext(
    String sessionId,
    String userId,
    String userQuery,
    List<AtlasMessage> history,
    Map<String, Object> env,
    String conversationId,
    Instant createdAt
) {}
