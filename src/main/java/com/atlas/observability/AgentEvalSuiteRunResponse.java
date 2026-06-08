package com.atlas.observability;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * 命名 Suite 的一次运行结果。
 *
 * <p>真实评测仍委托给 {@link AgentEvalReportService#evaluateSuite}，因此保持脱敏、
 * 确定性、非执行和无外部调用的边界。</p>
 */
public record AgentEvalSuiteRunResponse(
    String schemaVersion,
    Instant generatedAt,
    String suiteId,
    AgentEvalSuiteDefinition definition,
    AgentEvalSuiteResponse report,
    Map<String, Object> runPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION = "agent-eval-suite-run.v1";

    public static AgentEvalSuiteRunResponse of(AgentEvalSuiteDefinition definition,
                                               AgentEvalSuiteResponse report,
                                               Map<String, Object> runPolicy,
                                               Map<String, Object> privacy) {
        return new AgentEvalSuiteRunResponse(
            SCHEMA_VERSION,
            Instant.now(Clock.systemUTC()),
            definition != null ? definition.id() : "",
            definition,
            report,
            runPolicy != null ? Map.copyOf(runPolicy) : Map.of(),
            privacy != null ? Map.copyOf(privacy) : Map.of()
        );
    }
}
