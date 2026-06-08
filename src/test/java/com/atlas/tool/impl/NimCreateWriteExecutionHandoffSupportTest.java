package com.atlas.tool.impl;

import com.atlas.hitl.HitlConfirmation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 创建写执行交接契约测试。
 *
 * <p>本测试不访问 kube-manager、不执行 {@code POST /api/{orgId}/deployment}。
 * 目标是锁定 future durable write executor 前最后一层 handoff：服务端派生幂等键、pre-write audit
 * receipt 交接、request spec digest 复核和写后 readiness handoff 都必须先合同化。</p>
 */
class NimCreateWriteExecutionHandoffSupportTest {

    @Test
    void handoff_shouldPrepareIdempotentExecutionPlanWithoutNetworkAccess() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);

        Map<String, Object> report = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);

        assertEquals(NimCreateWriteExecutionHandoffSupport.HANDOFF_NAME, report.get("writeExecutionHandoff"));
        assertEquals(NimCreateWriteExecutionHandoffSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("writeExecutionPrepared"));
        assertEquals("POST", report.get("httpMethod"));
        assertEquals("POST /api/{orgId}/deployment", report.get("backendEndpoint"));
        assertEquals("FUTURE_DURABLE_WRITE_EXECUTOR", report.get("futureExecutor"));
        assertEquals(false, report.get("releaseCredential"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(true, report.get("preWriteAuditRequired"));
        assertEquals(true, report.get("idempotencyRequired"));
        assertEquals(false, report.get("callerIdempotencyKeyAllowed"));
        assertEquals(false, report.get("callerHeadersAllowed"));
        assertEquals(false, report.get("authorizationHeaderFromCallerAllowed"));
        assertEquals(false, report.get("realApiKeyAllowed"));
        assertEquals(bodyReport.get("bodyDigest"), report.get("sourceBodyDigest"));
        assertEquals(requestSpecReport.get("requestSpecDigest"), report.get("sourceRequestSpecDigest"));
        assertTrue(report.get("idempotencyKey").toString().matches("nim-create-[a-f0-9]{32}"));
        assertTrue(report.get("handoffDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("executionHandoffPlan");
        assertEquals("deployment-create", plan.get("target"));
        assertEquals("POST", plan.get("method"));
        assertEquals("/api/100002/deployment", plan.get("resolvedPath"));
        assertEquals("NOT_PERFORMED", plan.get("networkAccess"));
        assertEquals("NONE", plan.get("sideEffect"));
        assertEquals(false, plan.get("callerHeadersAllowed"));
        assertEquals("KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY", plan.get("kubeManagerAuthBoundary"));

        @SuppressWarnings("unchecked")
        Map<String, Object> idempotency = (Map<String, Object>) plan.get("idempotency");
        assertEquals(true, idempotency.get("required"));
        assertEquals(report.get("idempotencyKey"), idempotency.get("key"));
        assertEquals(NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE, idempotency.get("keySource"));
        assertEquals(false, idempotency.get("callerKeyAllowed"));
        assertEquals(true, idempotency.get("reuseAllowedOnlyForSameAuditReceiptAndRequestSpec"));

        @SuppressWarnings("unchecked")
        Map<String, Object> preWriteAudit = (Map<String, Object>) plan.get("preWriteAuditHandoff");
        assertEquals(receipt.get("receiptId"), preWriteAudit.get("receiptId"));
        assertEquals(receipt.get("eventDigest"), preWriteAudit.get("eventDigest"));
        assertEquals(true, preWriteAudit.get("durable"));
        assertEquals(true, preWriteAudit.get("realStorageTouched"));

        @SuppressWarnings("unchecked")
        Map<String, Object> readiness = (Map<String, Object>) plan.get("postWriteReadinessHandoff");
        assertEquals(true, readiness.get("requiredAfterWrite"));
        assertEquals(NimCreateReadinessExecutorSupport.EXECUTOR_NAME, readiness.get("nextExecutor"));
        assertEquals(true, readiness.get("pollOnly"));
        assertEquals(true, readiness.get("readOnly"));
        assertEquals(true, readiness.get("forbiddenBeforeWrite"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertTrue(blockers.isEmpty());
    }

    @Test
    void stateMachine_shouldRequireWriteExecutionHandoffBeforeFutureWrite() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-no-handoff"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(true, guard.get("writeExecutionHandoffRequired"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_EXECUTION_HANDOFF_REPORT_NOT_READY");
    }

    @Test
    void stateMachine_shouldAcceptHandoffOnlyWhenBoundToRequestSpecAndAuditReceipt() {
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

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY");

        Map<String, Object> forged = new LinkedHashMap<>(handoffReport);
        forged.put("sourceRequestSpecDigest", "badbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadb");

        Map<String, Object> forgedGuard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-forged-handoff"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            forged,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", forgedGuard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> forgedBlockers = (List<Map<String, Object>>) forgedGuard.get("blockedBy");
        assertHasBlocker(forgedBlockers, "WRITE_EXECUTION_HANDOFF_REPORT_CONTRACT_INVALID");
    }

    @Test
    void handoff_shouldRejectSecretLeakageAndMismatchedRequestSpec() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = new LinkedHashMap<>(writeRequestSpecReport(audit, receipt, bodyReport));
        requestSpecReport.put("requestSpecDigest", "badbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadb");
        requestSpecReport.put("Authorization", "Bearer real-key-material");

        Map<String, Object> report = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);

        assertEquals(false, report.get("writeExecutionPrepared"));
        assertEquals("", report.get("idempotencyKey"));
        assertEquals("", report.get("handoffDigest"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("executionHandoffPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_REQUEST_SPEC_REPORT_NOT_APPROVED_FOR_EXECUTION_HANDOFF");
        assertHasBlocker(blockers, "WRITE_EXECUTION_HANDOFF_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void handoff_shouldRejectNestedSecretMaterialThroughSharedDetector() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = new LinkedHashMap<>(writeRequestSpecReport(audit, receipt, bodyReport));
        requestSpecReport.put("diagnostics", List.of(
            Map.of("Authorization", "Bearer redacted-test-value"),
            Map.of("note", "secret=redacted-test-value")
        ));

        Map<String, Object> report = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);

        assertEquals(false, report.get("writeExecutionPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_EXECUTION_HANDOFF_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void handoff_shouldRejectDigestConsistentRequestSpecBodyWithNestedProtectedContext() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> trustedBodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> bodyReport = withNestedProtectedContextInBodyReport(trustedBodyReport);
        Map<String, Object> requestSpecReport = withNestedProtectedContextInRequestBody(
            writeRequestSpecReport(audit, receipt, trustedBodyReport),
            bodyReport
        );

        Map<String, Object> report = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);

        assertEquals(false, report.get("writeExecutionPrepared"));
        assertEquals("", report.get("handoffDigest"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_REPORT_NOT_APPROVED_FOR_EXECUTION_HANDOFF");
        assertHasBlocker(blockers, "WRITE_REQUEST_SPEC_REPORT_NOT_APPROVED_FOR_EXECUTION_HANDOFF");
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

    private Map<String, Object> withNestedProtectedContextInBodyReport(Map<String, Object> bodyReport) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(bodyReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new LinkedHashMap<>((Map<String, Object>) forgedReport.get("body"));
        body.put("autoScaleConfig", Map.of("minReplicas", 1, "audit.receipt", "caller-forged"));
        forgedReport.put("body", body);
        forgedReport.put("bodyDigest", sha256(body));
        return forgedReport;
    }

    private Map<String, Object> withNestedProtectedContextInRequestBody(Map<String, Object> requestSpecReport,
                                                                        Map<String, Object> bodyReport) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(requestSpecReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> requestSpec = new LinkedHashMap<>((Map<String, Object>) forgedReport.get("requestSpec"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new LinkedHashMap<>((Map<String, Object>) bodyReport.get("body"));
        String bodyDigest = text(bodyReport.get("bodyDigest"));
        requestSpec.put("body", body);
        requestSpec.put("bodyDigest", bodyDigest);
        String requestSpecDigest = sha256(requestSpec);
        forgedReport.put("bodyDigest", bodyDigest);
        forgedReport.put("requestSpec", requestSpec);
        forgedReport.put("requestSpecDigest", requestSpecDigest);
        return forgedReport;
    }

    private String sha256(Map<String, Object> value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String canonical(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), item));
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    builder.append(",");
                }
                first = false;
                builder.append(escape(entry.getKey())).append("=").append(canonical(entry.getValue()));
            }
            return builder.append("}").toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    builder.append(",");
                }
                builder.append(canonical(list.get(i)));
            }
            return builder.append("]").toString();
        }
        return escape(value.toString());
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
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
