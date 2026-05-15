package com.atlas.brain;

import java.util.Map;
import java.util.List;

/**
 * AtlasBrain 单次决策输出结构
 */
public record BrainDecision(
    ActionType actionType,
    String target,
    Map<String, Object> parameters,
    String reasoning,
    double confidence,
    List<String> requiredContext
) {
    public enum ActionType {
        CALL_TOOL,
        DELEGATE_AGENT,
        DIRECT_ANSWER,
        ASK_CLARIFY,
        HITL_CONFIRM
    }
}
