package com.atlas.tool.impl;

import com.atlas.hitl.HitlConfirmation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 创建审计上下文与 readiness 计划测试。
 *
 * <p>本测试只验证纯数据结构，不写审计日志、不轮询服务、不调用 kube-manager。目标是让未来
 * {@code nim_create} 的审计/轮询前置条件从“随手 Map”变成可复用契约。</p>
 */
class NimCreateAuditReadinessSupportTest {

    @Test
    void support_shouldBuildAuditContextAndReadinessPlanAcceptedByStateMachine() {
        Map<String, Object> gate = openGate();
        Map<String, Object> preview = completePreview();
        HitlConfirmation confirmation = HitlConfirmation.human("thread-1", "nim_create");
        NimCreateAuditReadinessSupport.AuditReadinessInput input = input(gate, preview, confirmation);

        Map<String, Object> audit = NimCreateAuditReadinessSupport.buildAuditContext(input);
        Map<String, Object> readiness = NimCreateAuditReadinessSupport.buildReadinessPlan(input);
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyRebuildReport(gate, preview, audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(gate, audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(gate, audit, receipt, bodyReport, requestSpecReport);

        assertEquals(true, audit.get("auditPrepared"));
        assertEquals("NIM_CREATE_REQUEST", audit.get("auditEventType"));
        assertEquals("nim_create", audit.get("targetTool"));
        assertEquals(NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE, audit.get("writeBodyProvenance"));
        assertEquals(true, audit.get("secretRedactionApplied"));
        assertEquals(NimCreateStateMachineSupport.API_KEY_POLICY, audit.get("apiKeyHandling"));
        assertFalse(audit.containsKey("token"));
        assertFalse(audit.containsKey("apiKey"));

        assertEquals(true, readiness.get("readinessPollingPrepared"));
        assertEquals(true, readiness.get("pollOnly"));
        assertEquals(true, readiness.get("apiKeyPlaceholderOnly"));
        assertEquals("Bearer {input your NGC_API_KEY here}", readiness.get("apiKeyPlaceholder"));
        @SuppressWarnings("unchecked")
        List<String> targets = (List<String>) readiness.get("targets");
        assertTrue(targets.contains("deployment"));
        assertTrue(targets.contains("service"));
        assertTrue(targets.contains("nim-health"));
        assertTrue(targets.contains("nim-models"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) readiness.get("steps");
        assertTrue(steps.stream().anyMatch(step -> "deployment".equals(step.get("target"))
            && "GET".equals(step.get("method"))
            && "/api/{orgId}/deployment".equals(step.get("endpoint"))));
        assertTrue(steps.stream().anyMatch(step -> "nim-health".equals(step.get("target"))
            && "GET".equals(step.get("method"))
            && "{nimApiBasePath}/v1/health/live".equals(step.get("endpoint"))));
        assertFalse(steps.stream().anyMatch(step -> "POST".equals(step.get("method"))));

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-ready"),
            gate,
            preview,
            confirmation,
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            readiness,
            readinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> guardBlockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertTrue(guardBlockers.stream().anyMatch(item -> "DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY".equals(item.get("code"))));
    }

    @Test
    void support_shouldKeepCallerSecretsOutOfAuditAndMarkForgedClaimsIgnored() {
        Map<String, Object> gate = openGate();
        Map<String, Object> preview = completePreview();
        NimCreateAuditReadinessSupport.AuditReadinessInput input = new NimCreateAuditReadinessSupport.AuditReadinessInput(
            "req-2",
            "conv-2",
            "user-2",
            "100002",
            Map.ofEntries(
                entry("serviceName", "llama-nim"),
                entry("confirmed", true),
                entry("safeToPost", true),
                entry("token", "must-not-leak"),
                entry("ngcApiKey", "must-not-leak")
            ),
            gate,
            preview,
            HitlConfirmation.human("thread-2", "nim_create")
        );

        Map<String, Object> audit = NimCreateAuditReadinessSupport.buildAuditContext(input);

        assertFalse(audit.containsKey("token"));
        assertFalse(audit.containsKey("ngcApiKey"));
        @SuppressWarnings("unchecked")
        List<String> ignored = (List<String>) audit.get("ignoredCallerClaimKeys");
        assertTrue(ignored.contains("confirmed"));
        assertTrue(ignored.contains("safeToPost"));
        assertTrue(ignored.contains("token"));
        assertTrue(ignored.contains("ngcApiKey"));
    }

    @Test
    void stateMachine_shouldRejectReadinessPlanWithWriteStepOrMissingTargets() {
        Map<String, Object> gate = openGate();
        Map<String, Object> preview = completePreview();
        HitlConfirmation confirmation = HitlConfirmation.human("thread-1", "nim_create");
        NimCreateAuditReadinessSupport.AuditReadinessInput input = input(gate, preview, confirmation);
        Map<String, Object> audit = NimCreateAuditReadinessSupport.buildAuditContext(input);
        Map<String, Object> readiness = new java.util.LinkedHashMap<>(NimCreateAuditReadinessSupport.buildReadinessPlan(input));
        readiness.put("targets", List.of("deployment", "service"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = new java.util.ArrayList<>((List<Map<String, Object>>) readiness.get("steps"));
        steps.add(Map.of(
            "target", "nim-chat",
            "method", "POST",
            "endpoint", "{nimApiBasePath}/v1/chat/completions"
        ));
        readiness.put("steps", steps);

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-bad-readiness"),
            gate,
            preview,
            confirmation,
            audit,
            durableAuditReceipt(audit),
            readiness,
            readinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertTrue(blockers.stream().anyMatch(item -> "READINESS_PLAN_NOT_READY".equals(item.get("code"))));
    }

    @Test
    void auditContext_shouldNotBePreparedWhenHitlTargetOrRequiredIdentityIsMissing() {
        Map<String, Object> audit = NimCreateAuditReadinessSupport.buildAuditContext(new NimCreateAuditReadinessSupport.AuditReadinessInput(
            "req-3",
            "",
            "user-3",
            "100002",
            Map.of("serviceName", "llama-nim"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-3", "deploy_create_instance")
        ));

        assertEquals(false, audit.get("auditPrepared"));
        assertEquals(false, audit.get("hitlAccepted"));
    }

    private NimCreateAuditReadinessSupport.AuditReadinessInput input(Map<String, Object> gate,
                                                                     Map<String, Object> preview,
                                                                     HitlConfirmation confirmation) {
        return new NimCreateAuditReadinessSupport.AuditReadinessInput(
            "req-1",
            "conv-1",
            "user-1",
            "100002",
            Map.of("serviceName", "llama-nim"),
            gate,
            preview,
            confirmation
        );
    }

    private Map<String, Object> openGate() {
        return new java.util.LinkedHashMap<>(Map.of(
            "gateState", NimCreateStateMachineSupport.READY_GATE_STATE,
            "allowedToCreateNow", true,
            "trustedPolicySnapshot", new java.util.LinkedHashMap<>(Map.of(
                "snapshotState", NimCreateStateMachineSupport.TRUSTED_POLICY_PASSED,
                "authoritative", true,
                "protectedFromCallerParams", true
            )),
            "futureWritePath", new java.util.LinkedHashMap<>(Map.of(
                "directUseOfPreviewAllowed", false,
                "fallbackAllowedFromPreflight", false
            ))
        ));
    }

    private Map<String, Object> completePreview() {
        return new java.util.LinkedHashMap<>(Map.of(
            "safeToPost", false,
            "previewOnly", true,
            "bodyComplete", true,
            "bodyDraft", new java.util.LinkedHashMap<>(Map.of(
                "name", "llama-nim",
                "displayName", "llama-nim",
                "image", "nvcr.io/nim/llama:1.0",
                "templateId", 88,
                "cpuLimits", 1000,
                "memLimits", 2048
            ))
        ));
    }

    private Map<String, Object> durableAuditReceipt(Map<String, Object> audit) {
        return Map.ofEntries(
            entry("auditReceiptPrepared", true),
            entry("receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS),
            entry("storageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE),
            entry("durable", true),
            entry("realStorageTouched", true),
            entry("releaseEligible", true),
            entry("eventDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM),
            entry("eventDigest", "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"),
            entry("receiptId", "nim-audit-durable-req-1"),
            entry("auditEventType", audit.get("auditEventType")),
            entry("requestId", audit.get("requestId")),
            entry("conversationId", audit.get("conversationId")),
            entry("userId", audit.get("userId")),
            entry("organizationId", audit.get("organizationId")),
            entry("targetTool", audit.get("targetTool")),
            entry("writeBodyProvenance", audit.get("writeBodyProvenance"))
        );
    }

    private Map<String, Object> writeBodyRebuildReport(Map<String, Object> gate,
                                                       Map<String, Object> preview,
                                                       Map<String, Object> audit,
                                                       Map<String, Object> receipt) {
        return NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                gate,
                preview,
                audit,
                receipt
            )
        );
    }

    private Map<String, Object> writeRequestSpecReport(Map<String, Object> gate,
                                                       Map<String, Object> audit,
                                                       Map<String, Object> receipt,
                                                       Map<String, Object> bodyReport) {
        return NimCreateWriteRequestSpecAdapterSupport.compile(
            new NimCreateWriteRequestSpecAdapterSupport.WriteRequestSpecInput(
                gate,
                audit,
                receipt,
                bodyReport
            )
        );
    }

    private Map<String, Object> writeExecutionHandoffReport(Map<String, Object> gate,
                                                            Map<String, Object> audit,
                                                            Map<String, Object> receipt,
                                                            Map<String, Object> bodyReport,
                                                            Map<String, Object> requestSpecReport) {
        return NimCreateWriteExecutionHandoffSupport.prepare(
            new NimCreateWriteExecutionHandoffSupport.WriteExecutionHandoffInput(
                gate,
                audit,
                receipt,
                bodyReport,
                requestSpecReport
            )
        );
    }

    private Map<String, Object> readinessExecutionReport() {
        return Map.ofEntries(
            entry("readinessExecutor", NimCreateReadinessExecutorSupport.EXECUTOR_NAME),
            entry("executionMode", "OFFLINE_CONTRACT_EVALUATION"),
            entry("sideEffect", "NONE"),
            entry("readOnly", true),
            entry("pollOnly", true),
            entry("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY),
            entry("apiKeyPlaceholderOnly", true),
            entry("apiKeyPlaceholder", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER),
            entry("state", "READY"),
            entry("ready", true),
            entry("deployment", Map.of("state", "MATCHED", "matched", true, "matchCount", 1)),
            entry("service", Map.of("state", "SERVICE_URL_READY", "serviceUrlReady", true, "entranceSource", "http", "nimApiBasePath", "/nim")),
            entry("health", Map.of("state", "LIVE", "live", true)),
            entry("models", Map.of("state", "MODEL_FOUND", "modelName", "llama")),
            entry("blockedBy", List.of()),
            entry("pendingBy", List.of()),
            entry("nextPoll", Map.of("prepared", false, "pollOnly", true, "afterSeconds", 0, "nextAttempt", 1, "maxAttempts", 120)),
            entry("forbiddenActionsEnforced", true)
        );
    }
}
