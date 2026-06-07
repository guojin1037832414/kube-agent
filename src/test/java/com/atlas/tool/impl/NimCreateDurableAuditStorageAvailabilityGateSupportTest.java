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
 * NIM durable audit storage 可用性门禁计划契约测试。
 *
 * <p>这些测试不连接 Elasticsearch，不调用 ISysLogService，也不写 sys_log。目标是锁定:
 * writer plan 之后还必须有独立 storage availability gate；计划本身不能证明存储可用。</p>
 */
class NimCreateDurableAuditStorageAvailabilityGateSupportTest {

    @Test
    void availabilityGate_shouldBuildProbePlanButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditStorageAvailabilityGateSupport.plan(
            new NimCreateDurableAuditStorageAvailabilityGateSupport.StorageAvailabilityGateInput(
                audit,
                principal,
                writerPlanReport
            )
        );

        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.GATE_NAME,
            report.get("durableAuditStorageAvailabilityGate"));
        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.HOLD_STATE, report.get("gateState"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("availabilityPlanPrepared"));
        assertEquals(true, report.get("requiredBeforePreWrite"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN,
            report.get("availabilityStatus"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        assertEquals("sys_log", report.get("candidateIndex"));
        assertEquals(writerPlanReport.get("writerPlanDigest"), report.get("sourceWriterPlanDigest"));
        assertTrue(report.get("availabilityPlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("availabilityPlan");
        assertEquals("DEDICATED_NIM_STORAGE_AVAILABILITY_GATE_REQUIRED", plan.get("gateBoundary"));
        assertEquals("sys_log", plan.get("targetStorage"));
        assertEquals("FUTURE_SERVER_SIDE_PROBE_ONLY", plan.get("probeMode"));
        assertEquals(true, plan.get("requiredBeforePreWrite"));
        assertEquals(writerPlanReport.get("writerPlanDigest"), plan.get("sourceWriterPlanDigest"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) plan.get("probeSteps");
        assertEquals(4, steps.size());
        assertEquals("verify-storage-client-enabled", steps.get(0).get("id"));
        assertEquals(false, steps.get(0).get("sideEffectAllowedNow"));
        assertTrue(steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("required"))));
        assertTrue(steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> failurePolicy = (Map<String, Object>) plan.get("failurePolicy");
        assertEquals(true, failurePolicy.get("failClosed"));
        assertEquals(false, failurePolicy.get("fallbackToMockReceiptAllowed"));
        assertEquals(false, failurePolicy.get("fallbackToCandidateReportAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> prerequisites = (Map<String, Object>) plan.get("receiptPrerequisites");
        assertEquals(true, prerequisites.get("storageAvailableRequired"));
        assertEquals(true, prerequisites.get("preWriteDurableAckRequired"));
        assertEquals(true, prerequisites.get("postWriteDurableAckRequired"));
        assertEquals(false, prerequisites.get("currentPlanSatisfiesPrerequisites"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_AVAILABILITY_PROBE_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void availabilityGate_shouldRejectMissingWriterPlanReport() {
        Map<String, Object> report = NimCreateDurableAuditStorageAvailabilityGateSupport.plan(
            new NimCreateDurableAuditStorageAvailabilityGateSupport.StorageAvailabilityGateInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("availabilityPlanPrepared"));
        assertEquals(false, report.get("storageAvailable"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("availabilityPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_PLAN_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item -> "STORAGE_AVAILABILITY_PROBE_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void availabilityGate_shouldRejectForgedAvailableClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedWriterPlanReport = new LinkedHashMap<>(writerPlanReport(audit, principal));
        forgedWriterPlanReport.put("storageAvailable", true);
        forgedWriterPlanReport.put("availabilityStatus", "AVAILABLE");

        Map<String, Object> report = NimCreateDurableAuditStorageAvailabilityGateSupport.plan(
            new NimCreateDurableAuditStorageAvailabilityGateSupport.StorageAvailabilityGateInput(
                audit,
                principal,
                forgedWriterPlanReport
            )
        );

        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("storageAvailable"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("availabilityPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_AVAILABILITY_GATE_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void availabilityGate_shouldRejectSecretLeakageBeforeProbePlan() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "redacted-test-value");
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditStorageAvailabilityGateSupport.plan(
            new NimCreateDurableAuditStorageAvailabilityGateSupport.StorageAvailabilityGateInput(
                audit,
                principal,
                writerPlanReport(completeAuditContext(), principal)
            )
        );

        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("realStorageTouched"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("availabilityPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_AVAILABILITY_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void availabilityGate_shouldRejectNestedListSecretLeakageBeforeProbePlan() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = new LinkedHashMap<>(writerPlanReport(audit, principal));
        writerPlanReport.put("diagnostics", List.of(
            Map.of("note", "writer plan remains hold"),
            Map.of("token", "redacted-test-value")
        ));

        Map<String, Object> report = NimCreateDurableAuditStorageAvailabilityGateSupport.plan(
            new NimCreateDurableAuditStorageAvailabilityGateSupport.StorageAvailabilityGateInput(
                audit,
                principal,
                writerPlanReport
            )
        );

        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("realStorageTouched"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("availabilityPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_AVAILABILITY_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET");
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
