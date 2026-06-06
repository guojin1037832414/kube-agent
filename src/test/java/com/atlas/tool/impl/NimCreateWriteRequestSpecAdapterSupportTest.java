package com.atlas.tool.impl;

import com.atlas.hitl.HitlConfirmation;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 创建受控 POST request spec 适配器契约测试。
 *
 * <p>本测试不访问 kube-manager、不执行 {@code POST /api/{orgId}/deployment}。
 * 目标是把未来真实写入前的最后一段 HTTP 请求规格也先合同化：只能从 durable audit receipt
 * 绑定的 body 重建报告生成，并且不能携带调用方 header、Authorization 或真实 NGC/NIM API Key。</p>
 */
class NimCreateWriteRequestSpecAdapterSupportTest {

    @Test
    void adapter_shouldCompilePostRequestSpecWithoutNetworkOrCallerHeaders() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);

        Map<String, Object> report = writeRequestSpecReport(audit, receipt, bodyReport);

        assertEquals(NimCreateWriteRequestSpecAdapterSupport.ADAPTER_NAME, report.get("writeRequestSpecAdapter"));
        assertEquals(NimCreateWriteRequestSpecAdapterSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("writeRequestPrepared"));
        assertEquals("POST", report.get("httpMethod"));
        assertEquals("POST /api/{orgId}/deployment", report.get("backendEndpoint"));
        assertEquals("/api/{orgId}/deployment", report.get("pathTemplate"));
        assertEquals("100002", report.get("organizationId"));
        assertEquals(NimCreateWriteRequestSpecAdapterSupport.CLIENT_BOUNDARY, report.get("clientBoundary"));
        assertEquals(NimCreateStateMachineSupport.API_KEY_POLICY, report.get("apiKeyHandling"));
        assertEquals(false, report.get("releaseCredential"));
        assertEquals(false, report.get("callerHeadersAllowed"));
        assertEquals(false, report.get("authorizationHeaderFromCallerAllowed"));
        assertEquals(false, report.get("realApiKeyAllowed"));
        assertEquals(true, report.get("bodyCopiedByValue"));
        assertEquals(false, report.get("bodyMutationAllowed"));
        assertEquals(bodyReport.get("bodyDigest"), report.get("bodyDigest"));
        assertEquals(receipt.get("receiptId"), report.get("sourceAuditReceiptId"));
        assertEquals(receipt.get("eventDigest"), report.get("sourceAuditEventDigest"));
        assertTrue(report.get("requestSpecDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("requestSpec");
        assertEquals("deployment-create", spec.get("target"));
        assertEquals("POST", spec.get("method"));
        assertEquals("/api/{orgId}/deployment", spec.get("endpoint"));
        assertEquals("/api/100002/deployment", spec.get("resolvedPath"));
        assertEquals(false, spec.get("queryAllowed"));
        assertEquals(Map.of(), spec.get("query"));
        assertEquals(true, spec.get("bodyAllowed"));
        assertEquals(true, spec.get("bodyRequired"));
        assertEquals(false, spec.get("callerHeadersAllowed"));
        assertEquals(false, spec.get("authorizationHeaderFromCallerAllowed"));
        assertEquals("KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY", spec.get("kubeManagerAuthBoundary"));
        assertEquals(false, spec.get("realApiKeyAllowed"));
        assertEquals("FUTURE_DURABLE_WRITE_EXECUTOR", spec.get("executionAdapterRequired"));
        assertEquals("NONE", spec.get("sideEffect"));
        assertEquals("POST /api/{orgId}/deployment", spec.get("futureSideEffectIfExecuted"));
        assertFalse(spec.containsKey("headers"));
        assertFalse(spec.toString().contains("Authorization"));
        assertFalse(spec.toString().contains("8100"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) spec.get("body");
        assertEquals("llama-nim", body.get("name"));
        assertEquals("nvcr.io/nim/llama:1.0", body.get("image"));
        assertFalse(body.containsKey("organizationId"));
        assertFalse(body.containsKey("token"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertTrue(blockers.isEmpty());
    }

    @Test
    void stateMachine_shouldRequireWriteRequestSpecReportBeforeFutureWrite() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-no-request-spec"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(true, guard.get("writeRequestSpecRequired"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_REQUEST_SPEC_REPORT_NOT_READY");
    }

    @Test
    void stateMachine_shouldAcceptWriteRequestSpecOnlyWhenBoundToBodyAndAuditReceipt() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-ready"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("READY_FOR_CONTROLLED_WRITE", guard.get("state"));
        assertEquals(true, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertTrue(blockers.isEmpty());

        Map<String, Object> forgedReport = new LinkedHashMap<>(requestSpecReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> forgedSpec = new LinkedHashMap<>((Map<String, Object>) forgedReport.get("requestSpec"));
        @SuppressWarnings("unchecked")
        Map<String, Object> forgedBody = new LinkedHashMap<>((Map<String, Object>) forgedSpec.get("body"));
        forgedBody.put("image", "nvcr.io/nim/other:2.0");
        forgedSpec.put("body", forgedBody);
        forgedReport.put("requestSpec", forgedSpec);

        Map<String, Object> forgedGuard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-forged"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            forgedReport,
            handoffReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", forgedGuard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> forgedBlockers = (List<Map<String, Object>>) forgedGuard.get("blockedBy");
        assertHasBlocker(forgedBlockers, "WRITE_REQUEST_SPEC_REPORT_CONTRACT_INVALID");
    }

    @Test
    void adapter_shouldRejectMismatchedReceiptAndSecretMaterial() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = new LinkedHashMap<>(writeBodyReport(audit, receipt));
        bodyReport.put("sourceAuditReceiptId", "nim-audit-durable-other");
        bodyReport.put("Authorization", "Bearer real-key-material");

        Map<String, Object> report = writeRequestSpecReport(audit, receipt, bodyReport);

        assertEquals(false, report.get("writeRequestPrepared"));
        assertEquals("", report.get("requestSpecDigest"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("requestSpec");
        assertTrue(spec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_REPORT_NOT_APPROVED_FOR_REQUEST_SPEC");
        assertHasBlocker(blockers, "WRITE_REQUEST_SPEC_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    private Map<String, Object> writeRequestSpecReport(Map<String, Object> audit,
                                                       Map<String, Object> receipt,
                                                       Map<String, Object> bodyReport) {
        return NimCreateWriteRequestSpecAdapterSupport.compile(
            new NimCreateWriteRequestSpecAdapterSupport.WriteRequestSpecInput(
                openGate(),
                audit,
                receipt,
                bodyReport
            )
        );
    }

    private Map<String, Object> writeExecutionHandoffReport(Map<String, Object> audit,
                                                            Map<String, Object> receipt,
                                                            Map<String, Object> bodyReport,
                                                            Map<String, Object> requestSpecReport) {
        return NimCreateWriteExecutionHandoffSupport.prepare(
            new NimCreateWriteExecutionHandoffSupport.WriteExecutionHandoffInput(
                openGate(),
                audit,
                receipt,
                bodyReport,
                requestSpecReport
            )
        );
    }

    private Map<String, Object> writeBodyReport(Map<String, Object> audit,
                                                Map<String, Object> receipt) {
        return NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                completePreview(),
                audit,
                receipt
            )
        );
    }

    private Map<String, Object> openGate() {
        return Map.of(
            "gateState", NimCreateStateMachineSupport.READY_GATE_STATE,
            "allowedToCreateNow", true,
            "trustedPolicySnapshot", Map.of(
                "snapshotState", NimCreateStateMachineSupport.TRUSTED_POLICY_PASSED,
                "authoritative", true,
                "protectedFromCallerParams", true
            ),
            "futureWritePath", Map.of(
                "directUseOfPreviewAllowed", false,
                "fallbackAllowedFromPreflight", false
            )
        );
    }

    private Map<String, Object> completePreview() {
        Map<String, Object> bodyDraft = new LinkedHashMap<>();
        bodyDraft.put("name", "llama-nim");
        bodyDraft.put("displayName", "llama-nim");
        bodyDraft.put("image", "nvcr.io/nim/llama:1.0");
        bodyDraft.put("templateId", 88);
        bodyDraft.put("cpuLimits", 2500);
        bodyDraft.put("cpuRequests", 2500);
        bodyDraft.put("memLimits", 12288);
        bodyDraft.put("memRequests", 12288);
        bodyDraft.put("gpuPercentLimits", 0);
        bodyDraft.put("gpuMemLimits", 0);
        bodyDraft.put("replicas", 1);
        bodyDraft.put("enableSecondNetwork", true);
        bodyDraft.put("organizationId", "caller-forged");
        bodyDraft.put("token", "");
        return Map.of(
            "safeToPost", false,
            "previewOnly", true,
            "bodyComplete", true,
            "bodyDraft", bodyDraft
        );
    }

    private Map<String, Object> completeAuditContext() {
        return Map.ofEntries(
            entry("auditPrepared", true),
            entry("auditEventType", "NIM_CREATE_REQUEST"),
            entry("requestId", "req-1"),
            entry("conversationId", "conv-1"),
            entry("userId", "user-1"),
            entry("organizationId", "100002"),
            entry("targetTool", "nim_create"),
            entry("writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE),
            entry("displayName", "llama-nim"),
            entry("image", "nvcr.io/nim/llama:1.0"),
            entry("templateId", "88"),
            entry("secretRedactionApplied", true),
            entry("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY)
        );
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

    private Map<String, Object> completeReadinessPlan() {
        return Map.of(
            "readinessPollingPrepared", true,
            "pollOnly", true,
            "apiKeyPlaceholderOnly", true,
            "apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY,
            "targets", List.of("deployment", "service", "nim-health", "nim-models"),
            "steps", List.of(
                Map.of("target", "deployment", "method", "GET", "endpoint", "/api/{orgId}/deployment"),
                Map.of("target", "service", "method", "EXTRACT_FROM_DEPLOYMENT_RESPONSE", "endpoint", "deployment.entranceMap.http|http1"),
                Map.of("target", "nim-health", "method", "GET", "endpoint", "{nimApiBasePath}/v1/health/live"),
                Map.of("target", "nim-models", "method", "GET", "endpoint", "{nimApiBasePath}/v1/models")
            )
        );
    }

    private Map<String, Object> completeReadinessExecutionReport() {
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

    private void assertHasBlocker(List<Map<String, Object>> blockers, String code) {
        assertTrue(blockers.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected blocker code: " + code + ", actual blockers: " + blockers);
    }
}
