package com.atlas.tool.defaults;

import java.util.Collections;
import java.util.Map;

/**
 * 单个意图的默认值封装。
 *
 * <p>Immutable，构造后不可变。</p>
 */
public record IntentDefaults(
    String intentId,
    Map<String, Object> parameters
) {
    public IntentDefaults {
        parameters = Collections.unmodifiableMap(
            DefaultValueSafety.sanitizeParameters(intentId, parameters)
        );
    }

    public Object getDefault(String paramName) {
        return parameters.get(paramName);
    }

    public boolean isEmpty() {
        return parameters.isEmpty();
    }
}
