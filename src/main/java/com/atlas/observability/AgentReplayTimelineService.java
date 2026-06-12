package com.atlas.observability;

import com.atlas.audit.AgentAuditOutcome;
import com.atlas.audit.AgentAuditQueryEvent;
import com.atlas.audit.AgentAuditQueryResponse;
import com.atlas.audit.AgentAuditQueryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 从脱敏审计查询模型构建前端可渲染的 replay timeline。
 *
 * <p>中文说明：Replay 在这里不是“重新播放执行”，而是把 {@link AgentAuditQueryService}
 * 返回的 redacted audit events 转成前端 timeline DTO。它帮助人和 eval 看清一次 trace
 * 经历了 PRE_EXECUTION、FINAL、BLOCKED、ERROR 等阶段。</p>
 *
 * <p>安全边界：本服务只读脱敏审计视图，不读取 raw audit，不重新执行 Tool、不调用 MCP、
 * 不访问 kube-manager、不调用 LLM，也不恢复原始 prompt、reason 或参数值。timeline
 * 只能作为诊断/评测证据，不是 prompt 权威，也不是运行时授权。</p>
 */
@Service
public class AgentReplayTimelineService {

    private final AgentAuditQueryService auditQueryService;

    public AgentReplayTimelineService(AgentAuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    /**
     * 构建指定 trace 的 oldest-first timeline。
     *
     * <p>中文说明：审计查询通常按 newest-first 返回；前端回放更适合 oldest-first，因此这里反转顺序。
     * 反转只改变展示顺序，不改变审计事实，也不会触发任何运行时动作。</p>
     */
    public AgentReplayTimelineResponse traceTimeline(String traceId, int maxResults) {
        AgentAuditQueryResponse auditResponse = auditQueryService.findByTraceId(traceId, maxResults);
        List<AgentAuditQueryEvent> chronologicalEvents = new ArrayList<>(auditResponse.events());
        java.util.Collections.reverse(chronologicalEvents);

        List<AgentReplayTimelineStep> steps = new ArrayList<>();
        for (int i = 0; i < chronologicalEvents.size(); i++) {
            steps.add(toStep(chronologicalEvents.get(i), i + 1));
        }
        return AgentReplayTimelineResponse.of(
            auditResponse.query(),
            auditResponse.maxResults(),
            auditResponse.truncated(),
            auditResponse.index(),
            steps
        );
    }

    /**
     * 把单条审计读模型投影成 timeline step。
     *
     * <p>安全边界：step 只携带 reasonSummary、parameterSummary 和 telemetry 这类脱敏字段；
     * 不输出 endpoint 列表、原始主体、完整原因文本或参数值。</p>
     */
    private AgentReplayTimelineStep toStep(AgentAuditQueryEvent event, int position) {
        String outcome = safeText(event.outcome()).toUpperCase(Locale.ROOT);
        String operationType = safeText(event.operationType());
        String phase = phase(event, outcome);
        return new AgentReplayTimelineStep(
            stepId(event, position),
            position,
            event.occurredAt(),
            phase,
            phase,
            kind(outcome),
            status(outcome),
            safeText(event.auditId()),
            safeText(event.traceId()),
            safeText(event.intentId()),
            safeText(event.toolName()),
            safeText(event.source()),
            operationType,
            safeText(event.httpMethod()),
            event.requiresConfirmation(),
            event.executed(),
            event.success(),
            Math.max(0, event.apiEndpointCount()),
            safeMap(event.reasonSummary()),
            safeMap(event.parameterSummary()),
            safeMap(event.telemetry()),
            labels(event, outcome, operationType)
        );
    }

    private String stepId(AgentAuditQueryEvent event, int position) {
        String auditId = safeText(event.auditId());
        return !auditId.isBlank() ? auditId + ":" + position : "step-" + position;
    }

    /**
     * 推导阶段标签。
     *
     * <p>中文说明：durable audit 可能包含 PRE_EXECUTION 和 FINAL 两种相位；如果旧事件缺少相位，
     * 则用 outcome 做兼容推断，保证前端和 eval 仍能解释时间线。</p>
     */
    private String phase(AgentAuditQueryEvent event, String outcome) {
        String recordPhase = safeText(event.recordPhase()).toUpperCase(Locale.ROOT);
        if (!recordPhase.isBlank()) {
            return recordPhase;
        }
        return AgentAuditOutcome.PREPARED.name().equals(outcome) ? "PRE_EXECUTION" : "FINAL";
    }

    private String kind(String outcome) {
        if (AgentAuditOutcome.PREPARED.name().equals(outcome)) {
            return "TOOL_PREPARED";
        }
        if (AgentAuditOutcome.BLOCKED.name().equals(outcome)) {
            return "TOOL_BLOCKED";
        }
        if (AgentAuditOutcome.ERROR.name().equals(outcome)) {
            return "TOOL_ERROR";
        }
        if (AgentAuditOutcome.BUSINESS_FAILURE.name().equals(outcome)) {
            return "TOOL_BUSINESS_FAILURE";
        }
        return "TOOL_RESULT";
    }

    private String status(String outcome) {
        if (AgentAuditOutcome.PREPARED.name().equals(outcome)) {
            return "prepared";
        }
        if (AgentAuditOutcome.SUCCESS.name().equals(outcome)) {
            return "success";
        }
        if (AgentAuditOutcome.BLOCKED.name().equals(outcome)) {
            return "blocked";
        }
        if (AgentAuditOutcome.ERROR.name().equals(outcome)) {
            return "error";
        }
        if (AgentAuditOutcome.BUSINESS_FAILURE.name().equals(outcome)) {
            return "business_failure";
        }
        return "unknown";
    }

    /**
     * 生成前端筛选和学习用标签。
     *
     * <p>安全边界：标签只表达 outcome、operation 和执行/确认状态，不包含原始参数或敏感业务值。</p>
     */
    private List<String> labels(AgentAuditQueryEvent event, String outcome, String operationType) {
        List<String> labels = new ArrayList<>();
        if (!outcome.isBlank()) {
            labels.add("outcome:" + outcome);
        }
        if (operationType != null && !operationType.isBlank()) {
            labels.add("operation:" + operationType);
        }
        labels.add(event.requiresConfirmation() ? "confirmation:required" : "confirmation:not_required");
        labels.add(event.executed() ? "execution:executed" : "execution:not_executed");
        labels.add(event.success() ? "result:success" : "result:not_success");
        return List.copyOf(labels);
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value != null ? Map.copyOf(value) : Map.of();
    }

    private String safeText(String value) {
        return value != null ? value : "";
    }
}
