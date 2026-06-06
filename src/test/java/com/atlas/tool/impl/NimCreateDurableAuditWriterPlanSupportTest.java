package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 专用 durable audit writer 两阶段计划契约测试。
 *
 * <p>这些用例刻意不写 sys_log，也不 mock Elasticsearch/ISysLogService。目标是把“候选存储证据 ->
 * 专用 writer 计划 -> 仍不能签发 durable receipt”的边界测试化，防止后续把计划层误接成真实放行凭据。</p>
 */
class NimCreateDurableAuditWriterPlanSupportTest {

    @Test
    void writerPlan_shouldBuildTwoPhaseTemplatesButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> storageReport = storageCandidateReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditWriterPlanSupport.plan(
            new NimCreateDurableAuditWriterPlanSupport.DurableAuditWriterPlanInput(
                audit,
                principal,
                storageReport
            )
        );

        assertEquals(NimCreateDurableAuditWriterPlanSupport.WRITER_PLAN_NAME, report.get("durableAuditWriterPlan"));
        assertEquals(NimCreateDurableAuditWriterPlanSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals(NimCreateDurableAuditWriterPlanSupport.HOLD_STATE, report.get("writerState"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("writerPlanPrepared"));
        assertEquals(true, report.get("preWriteRecordRequired"));
        assertEquals(true, report.get("postWriteRecordRequired"));
        assertEquals(true, report.get("storageAvailabilityGateRequired"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        assertEquals("sys_log", report.get("candidateIndex"));
        assertEquals(storageReport.get("storagePlanDigest"), report.get("sourceStoragePlanDigest"));
        assertTrue(report.get("writerPlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("writerPlan");
        assertEquals("DEDICATED_NIM_DURABLE_AUDIT_WRITER_REQUIRED", plan.get("writerBoundary"));
        assertEquals("PRE_WRITE_INTENT_AND_POST_WRITE_RESULT", plan.get("writeMode"));

        @SuppressWarnings("unchecked")
        Map<String, Object> availability = (Map<String, Object>) plan.get("storageAvailabilityGate");
        assertEquals(true, availability.get("required"));
        assertEquals(true, availability.get("failClosedWhenStorageUnavailable"));
        assertEquals(false, availability.get("candidateReportAloneCanIssueReceipt"));

        @SuppressWarnings("unchecked")
        Map<String, Object> preWrite = (Map<String, Object>) plan.get("preWriteRecordTemplate");
        assertEquals(NimCreateDurableAuditWriterPlanSupport.PRE_WRITE_RECORD_TYPE, preWrite.get("recordType"));
        assertEquals("PRE_WRITE", preWrite.get("phase"));
        assertEquals(true, preWrite.get("recordedBeforeWrite"));
        assertEquals(false, preWrite.get("recordedAfterWrite"));
        assertEquals("NIM_CREATE_AUDIT", preWrite.get("module"));
        assertEquals("/api/100002/deployment", preWrite.get("uri"));
        assertEquals(false, preWrite.get("realStorageTouched"));
        assertFalse(preWrite.toString().contains("must-not-leak"));

        @SuppressWarnings("unchecked")
        Map<String, Object> postWrite = (Map<String, Object>) plan.get("postWriteRecordTemplate");
        assertEquals(NimCreateDurableAuditWriterPlanSupport.POST_WRITE_RECORD_TYPE, postWrite.get("recordType"));
        assertEquals("POST_WRITE", postWrite.get("phase"));
        assertEquals(false, postWrite.get("recordedBeforeWrite"));
        assertEquals(true, postWrite.get("recordedAfterWrite"));
        assertEquals("{futurePreWriteSysLogId}", postWrite.get("preWriteRecordId"));
        assertEquals(NimCreateReadinessExecutorSupport.EXECUTOR_NAME,
            ((Map<?, ?>) postWrite.get("body")).get("readinessHandoff"));

        @SuppressWarnings("unchecked")
        Map<String, Object> receiptRule = (Map<String, Object>) plan.get("receiptIssuanceRule");
        assertEquals(false, receiptRule.get("durableReceiptCanBeIssuedNow"));
        assertEquals(true, receiptRule.get("preWriteRecordRequired"));
        assertEquals(true, receiptRule.get("postWriteRecordRequired"));
        assertEquals(true, receiptRule.get("bothRecordsMustBeDurable"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_STORAGE_CANDIDATE_IMPLEMENTATION_HOLD");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_IMPLEMENTATION_HOLD");
        assertEquals(2, blockers.size());
    }

    @Test
    void writerPlan_shouldBindOptionalRequestSpecAndHandoffEvidenceWhenAvailable() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> durableReceipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, durableReceipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, durableReceipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, durableReceipt, bodyReport, requestSpecReport);

        Map<String, Object> report = NimCreateDurableAuditWriterPlanSupport.plan(
            new NimCreateDurableAuditWriterPlanSupport.DurableAuditWriterPlanInput(
                audit,
                principal,
                storageCandidateReport(audit, principal),
                requestSpecReport,
                handoffReport
            )
        );

        assertEquals(NimCreateDurableAuditWriterPlanSupport.HOLD_STATE, report.get("writerState"));
        assertEquals(true, report.get("inputAccepted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("writerPlan");
        @SuppressWarnings("unchecked")
        Map<String, Object> preWrite = (Map<String, Object>) plan.get("preWriteRecordTemplate");
        @SuppressWarnings("unchecked")
        Map<String, Object> postWrite = (Map<String, Object>) plan.get("postWriteRecordTemplate");

        assertEquals(requestSpecReport.get("requestSpecDigest"), preWrite.get("requestSpecDigest"));
        assertEquals(requestSpecReport.get("bodyDigest"), preWrite.get("bodyDigest"));
        assertEquals(requestSpecReport.get("requestSpecDigest"), postWrite.get("requestSpecDigest"));
        assertEquals(requestSpecReport.get("bodyDigest"), postWrite.get("bodyDigest"));
        assertEquals(handoffReport.get("handoffDigest"), postWrite.get("handoffDigest"));
        assertEquals(handoffReport.get("idempotencyKey"), postWrite.get("idempotencyKey"));
        assertFalse(plan.toString().contains("caller-forged"));
        assertFalse(plan.toString().contains("must-not-leak"));
    }

    @Test
    void writerPlan_shouldRejectMissingStorageCandidateReport() {
        Map<String, Object> report = NimCreateDurableAuditWriterPlanSupport.plan(
            new NimCreateDurableAuditWriterPlanSupport.DurableAuditWriterPlanInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditWriterPlanSupport.REJECTED_STATE, report.get("writerState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writerPlanPrepared"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("writerPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_STORAGE_CANDIDATE_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item -> "DURABLE_AUDIT_WRITER_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void writerPlan_shouldRejectForgedDurableReceiptClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedStorageReport = new LinkedHashMap<>(storageCandidateReport(audit, principal));
        forgedStorageReport.put("durableReceiptCanBeIssued", true);
        forgedStorageReport.put("releaseEligible", true);

        Map<String, Object> report = NimCreateDurableAuditWriterPlanSupport.plan(
            new NimCreateDurableAuditWriterPlanSupport.DurableAuditWriterPlanInput(
                audit,
                principal,
                forgedStorageReport
            )
        );

        assertEquals(NimCreateDurableAuditWriterPlanSupport.REJECTED_STATE, report.get("writerState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("writerPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_STORAGE_CANDIDATE_REPORT_INVALID");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_FORGED_RELEASE_CLAIM");
    }

    @Test
    void writerPlan_shouldRejectSecretLeakageBeforeAnyRecordTemplate() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "redacted-test-value");
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditWriterPlanSupport.plan(
            new NimCreateDurableAuditWriterPlanSupport.DurableAuditWriterPlanInput(
                audit,
                principal,
                storageCandidateReport(completeAuditContext(), principal)
            )
        );

        assertEquals(NimCreateDurableAuditWriterPlanSupport.REJECTED_STATE, report.get("writerState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("realStorageTouched"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("writerPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    private Map<String, Object> storageCandidateReport(Map<String, Object> audit,
                                                       Map<String, Object> principal) {
        return NimCreateDurableAuditStorageSupport.prepare(
            new NimCreateDurableAuditStorageSupport.DurableAuditStorageInput(
                audit,
                principal
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
            entry("auditEventType", NimCreateAuditReadinessSupport.AUDIT_EVENT_TYPE),
            entry("requestId", "req-1"),
            entry("conversationId", "conv-1"),
            entry("userId", "user-1"),
            entry("organizationId", "100002"),
            entry("targetTool", "nim_create"),
            entry("targetIntent", "nim_create"),
            entry("operationType", "CREATE"),
            entry("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT),
            entry("writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE),
            entry("displayName", "llama-nim"),
            entry("image", "nvcr.io/nim/llama:1.0"),
            entry("templateId", "88"),
            entry("secretRedactionApplied", true),
            entry("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY)
        );
    }

    private Map<String, Object> trustedPrincipalSnapshot() {
        return Map.of(
            "authoritative", true,
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true,
            "organizationId", "100002",
            "userId", "user-1",
            "username", "alice"
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

    private void assertHasBlocker(List<Map<String, Object>> blockers, String code) {
        assertTrue(blockers.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected blocker code: " + code + ", actual blockers: " + blockers);
    }
}
