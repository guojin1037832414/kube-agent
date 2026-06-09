package com.atlas.observability;

import java.util.List;
import java.util.Map;

/**
 * One frontend-consumable capability in the Agent eval workbench.
 */
public record AgentEvalWorkbenchCapability(
    String id,
    String title,
    String stage,
    String httpMethod,
    String pathTemplate,
    String requestSchema,
    String responseSchema,
    boolean adminOnly,
    boolean readOnly,
    boolean mutatesCatalog,
    boolean toolExecution,
    boolean kubeManagerCalls,
    List<String> consumes,
    List<String> produces,
    Map<String, Object> policy
) {

    public AgentEvalWorkbenchCapability {
        id = safeText(id);
        title = safeText(title);
        stage = safeText(stage);
        httpMethod = safeText(httpMethod);
        pathTemplate = safeText(pathTemplate);
        requestSchema = safeText(requestSchema);
        responseSchema = safeText(responseSchema);
        consumes = consumes != null ? List.copyOf(consumes) : List.of();
        produces = produces != null ? List.copyOf(produces) : List.of();
        policy = policy != null ? Map.copyOf(policy) : Map.of();
    }

    private static String safeText(String value) {
        return value != null ? value : "";
    }
}
