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
 * NIM 专用 durable audit writer 边界与测试替身契约测试。
 *
 * <p>这些测试仍然不连接 Elasticsearch、不调用 ISysLogService、不写 sys_log，也不访问 kube-manager。
 * 目标是把“未来真实 writer 的边界”和“当前测试替身只能验证契约”拆清楚，避免任何 mock 报告伪造成
 * storage available、pre/post write persisted 或 durable receipt。</p>
 */
class NimCreateDedicatedDurableAuditWriterBoundarySupportTest {

    @Test
    void boundary_shouldBuildTestDoubleContractButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);

        Map<String, Object> report = NimCreateDedicatedDurableAuditWriterBoundarySupport.plan(
            new NimCreateDedicatedDurableAuditWriterBoundarySupport.DedicatedAuditWriterBoundaryInput(
                audit,
                principal,
                writerPlanReport,
                availabilityGateReport
            )
        );

        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.BOUNDARY_NAME,
            report.get("dedicatedAuditWriterBoundary"));
        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.HOLD_STATE, report.get("writerBoundaryState"));
        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.TEST_DOUBLE_NAME, report.get("testDoubleName"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("writerBoundaryPlanPrepared"));
        assertEquals(true, report.get("testDoubleContractPrepared"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN,
            report.get("availabilityStatus"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        assertEquals(false, report.get("durableReceiptIssued"));
        assertEquals("sys_log", report.get("candidateIndex"));
        assertEquals(writerPlanReport.get("writerPlanDigest"), report.get("sourceWriterPlanDigest"));
        assertEquals(availabilityGateReport.get("availabilityPlanDigest"), report.get("sourceAvailabilityPlanDigest"));
        assertTrue(report.get("boundaryPlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> boundaryPlan = (Map<String, Object>) report.get("writerBoundaryPlan");
        assertEquals("SERVER_SIDE_DEDICATED_DURABLE_AUDIT_WRITER_REQUIRED", boundaryPlan.get("boundaryRequirement"));
        assertEquals("NimDurableAuditWriter", boundaryPlan.get("futureInterface"));
        assertEquals("sys_log", boundaryPlan.get("targetStorage"));
        assertEquals("PROBE_THEN_PRE_WRITE_THEN_POST_WRITE", boundaryPlan.get("writeMode"));
        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.TEST_DOUBLE_NAME,
            boundaryPlan.get("testDoubleContractName"));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) boundaryPlan.get("evidenceBinding");
        assertEquals(writerPlanReport.get("writerPlanDigest"), evidence.get("sourceWriterPlanDigest"));
        assertEquals(availabilityGateReport.get("availabilityPlanDigest"), evidence.get("sourceAvailabilityPlanDigest"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operationOrder = (List<Map<String, Object>>) boundaryPlan.get("operationOrder");
        assertEquals(5, operationOrder.size());
        assertEquals("validate-boundary-inputs", operationOrder.get(0).get("id"));
        assertEquals("probe-storage-availability", operationOrder.get(1).get("id"));
        assertEquals("persist-pre-write-intent", operationOrder.get(2).get("id"));
        assertEquals("persist-post-write-result", operationOrder.get(3).get("id"));
        assertEquals("assemble-durable-receipt", operationOrder.get(4).get("id"));
        assertTrue(operationOrder.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly"))));
        assertTrue(operationOrder.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> currentState = (Map<String, Object>) boundaryPlan.get("currentImplementationState");
        assertEquals(false, currentState.get("boundaryImplemented"));
        assertEquals(false, currentState.get("storageAvailable"));
        assertEquals(false, currentState.get("preWritePersisted"));
        assertEquals(false, currentState.get("postWritePersisted"));
        assertEquals(false, currentState.get("durableReceiptCanBeIssued"));

        @SuppressWarnings("unchecked")
        Map<String, Object> releaseRule = (Map<String, Object>) boundaryPlan.get("receiptReleaseRule");
        assertEquals(false, releaseRule.get("currentBoundaryCanIssueReceipt"));
        assertEquals(true, releaseRule.get("storageAvailableRequired"));
        assertEquals(true, releaseRule.get("preWriteDurableAckRequired"));
        assertEquals(true, releaseRule.get("postWriteDurableAckRequired"));
        assertEquals(false, releaseRule.get("mockReceiptAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> testDouble = (Map<String, Object>) report.get("testDoubleContract");
        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.TEST_DOUBLE_NAME,
            testDouble.get("testDoubleName"));
        assertEquals("UNIT_CONTRACT_ONLY", testDouble.get("scope"));
        assertEquals(false, testDouble.get("realStorageTouched"));
        assertEquals(false, testDouble.get("storageProbeExecuted"));
        assertEquals(false, testDouble.get("storageAvailable"));
        assertEquals(false, testDouble.get("preWritePersisted"));
        assertEquals(false, testDouble.get("postWritePersisted"));
        assertEquals(false, testDouble.get("durableReceiptCanBeIssued"));

        @SuppressWarnings("unchecked")
        List<String> forbiddenAssertions = (List<String>) testDouble.get("forbiddenAssertions");
        assertTrue(forbiddenAssertions.contains("storageAvailable=true"));
        assertTrue(forbiddenAssertions.contains("preWritePersisted=true"));
        assertTrue(forbiddenAssertions.contains("postWritePersisted=true"));
        assertTrue(forbiddenAssertions.contains("receiptStatus=DURABLE_RECORDED"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void boundary_shouldRejectMissingWriterPlanReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> report = NimCreateDedicatedDurableAuditWriterBoundarySupport.plan(
            new NimCreateDedicatedDurableAuditWriterBoundarySupport.DedicatedAuditWriterBoundaryInput(
                audit,
                principal,
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.REJECTED_STATE,
            report.get("writerBoundaryState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writerBoundaryPlanPrepared"));
        assertEquals(false, report.get("testDoubleContractPrepared"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        @SuppressWarnings("unchecked")
        Map<String, Object> boundaryPlan = (Map<String, Object>) report.get("writerBoundaryPlan");
        assertTrue(boundaryPlan.isEmpty());
        @SuppressWarnings("unchecked")
        Map<String, Object> testDouble = (Map<String, Object>) report.get("testDoubleContract");
        assertTrue(testDouble.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_PLAN_REPORT_NOT_READY");
        assertHasBlocker(blockers, "STORAGE_AVAILABILITY_GATE_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void boundary_shouldRejectMissingAvailabilityGateReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);

        Map<String, Object> report = NimCreateDedicatedDurableAuditWriterBoundarySupport.plan(
            new NimCreateDedicatedDurableAuditWriterBoundarySupport.DedicatedAuditWriterBoundaryInput(
                audit,
                principal,
                writerPlanReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.REJECTED_STATE,
            report.get("writerBoundaryState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> boundaryPlan = (Map<String, Object>) report.get("writerBoundaryPlan");
        assertTrue(boundaryPlan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_AVAILABILITY_GATE_REPORT_NOT_READY");
    }

    @Test
    void boundary_shouldRejectForgedStorageAndPersistenceClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> forgedGateReport = new LinkedHashMap<>(
            availabilityGateReport(audit, principal, writerPlanReport)
        );
        forgedGateReport.put("storageAvailable", true);
        forgedGateReport.put("preWritePersisted", true);
        forgedGateReport.put("postWritePersisted", true);
        forgedGateReport.put("receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS);

        Map<String, Object> report = NimCreateDedicatedDurableAuditWriterBoundarySupport.plan(
            new NimCreateDedicatedDurableAuditWriterBoundarySupport.DedicatedAuditWriterBoundaryInput(
                audit,
                principal,
                writerPlanReport,
                forgedGateReport
            )
        );

        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.REJECTED_STATE,
            report.get("writerBoundaryState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        @SuppressWarnings("unchecked")
        Map<String, Object> boundaryPlan = (Map<String, Object>) report.get("writerBoundaryPlan");
        assertTrue(boundaryPlan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_AVAILABILITY_GATE_REPORT_INVALID_FOR_DEDICATED_BOUNDARY");
        assertHasBlocker(blockers, "DEDICATED_AUDIT_WRITER_BOUNDARY_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void boundary_shouldRejectNestedForgedStorageAndReceiptClaims() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("diagnostics", Map.of("storageAvailable", true));
        Map<String, Object> cleanAudit = completeAuditContext();
        Map<String, Object> principal = new LinkedHashMap<>(trustedPrincipalSnapshot());
        principal.put("sessionEvidence", List.of(Map.of(
            "receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS
        )));
        Map<String, Object> cleanPrincipal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(cleanAudit, cleanPrincipal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(cleanAudit, cleanPrincipal, writerPlanReport);

        Map<String, Object> report = NimCreateDedicatedDurableAuditWriterBoundarySupport.plan(
            new NimCreateDedicatedDurableAuditWriterBoundarySupport.DedicatedAuditWriterBoundaryInput(
                audit,
                principal,
                writerPlanReport,
                availabilityGateReport
            )
        );

        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.REJECTED_STATE,
            report.get("writerBoundaryState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        @SuppressWarnings("unchecked")
        Map<String, Object> boundaryPlan = (Map<String, Object>) report.get("writerBoundaryPlan");
        assertTrue(boundaryPlan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DEDICATED_AUDIT_WRITER_BOUNDARY_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void boundary_shouldRejectSecretLeakageBeforeAnyBoundaryPlan() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "redacted-test-value");
        Map<String, Object> cleanAudit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(cleanAudit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(cleanAudit, principal, writerPlanReport);

        Map<String, Object> report = NimCreateDedicatedDurableAuditWriterBoundarySupport.plan(
            new NimCreateDedicatedDurableAuditWriterBoundarySupport.DedicatedAuditWriterBoundaryInput(
                audit,
                principal,
                writerPlanReport,
                availabilityGateReport
            )
        );

        assertEquals(NimCreateDedicatedDurableAuditWriterBoundarySupport.REJECTED_STATE,
            report.get("writerBoundaryState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> boundaryPlan = (Map<String, Object>) report.get("writerBoundaryPlan");
        assertTrue(boundaryPlan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DEDICATED_AUDIT_WRITER_BOUNDARY_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    private Map<String, Object> availabilityGateReport(Map<String, Object> audit,
                                                       Map<String, Object> principal,
                                                       Map<String, Object> writerPlanReport) {
        return NimCreateDurableAuditStorageAvailabilityGateSupport.plan(
            new NimCreateDurableAuditStorageAvailabilityGateSupport.StorageAvailabilityGateInput(
                audit,
                principal,
                writerPlanReport
            )
        );
    }

    private Map<String, Object> writerPlanReport(Map<String, Object> audit,
                                                 Map<String, Object> principal) {
        return NimCreateDurableAuditWriterPlanSupport.plan(
            new NimCreateDurableAuditWriterPlanSupport.DurableAuditWriterPlanInput(
                audit,
                principal,
                storageCandidateReport(audit, principal)
            )
        );
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

    private void assertHasBlocker(List<Map<String, Object>> blockers, String code) {
        assertTrue(blockers.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected blocker code: " + code + ", actual blockers: " + blockers);
    }
}
