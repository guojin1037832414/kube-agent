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
 * NIM 专用 durable audit writer 未来接口规格契约测试。
 *
 * <p>这些测试不创建真实 writer 接口、不注入 Spring Bean、不连接 Elasticsearch，也不写 sys_log。
 * 目标是先把未来接口的请求/响应/失败语义和 test double 限制固定下来，防止后续把接口规格当成真实 durable writer 结果。</p>
 */
class NimCreateDurableAuditWriterInterfaceSpecSupportTest {

    @Test
    void interfaceSpec_shouldBuildFutureWriterContractButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> boundaryReport = boundaryReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditWriterInterfaceSpecSupport.plan(
            new NimCreateDurableAuditWriterInterfaceSpecSupport.DurableAuditWriterInterfaceSpecInput(
                audit,
                principal,
                boundaryReport
            )
        );

        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.INTERFACE_SPEC_NAME,
            report.get("durableAuditWriterInterfaceSpec"));
        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.HOLD_STATE, report.get("interfaceSpecState"));
        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.FUTURE_INTERFACE, report.get("futureInterface"));
        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.REQUEST_TYPE, report.get("requestType"));
        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.RESPONSE_TYPE, report.get("responseType"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("interfaceSpecPrepared"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        assertEquals(false, report.get("durableReceiptIssued"));
        assertEquals(boundaryReport.get("boundaryPlanDigest"), report.get("sourceBoundaryPlanDigest"));
        assertTrue(report.get("interfaceSpecDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("interfaceSpec");
        assertEquals("NimDurableAuditWriter", spec.get("futureInterface"));
        assertEquals("SERVER_SIDE_ONLY", spec.get("interfaceBoundary"));
        assertEquals("FUTURE_REVIEWED_IMPLEMENTATION_REQUIRED", spec.get("implementationMode"));
        assertEquals("sys_log", spec.get("targetStorage"));

        @SuppressWarnings("unchecked")
        Map<String, Object> request = (Map<String, Object>) spec.get("requestContract");
        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.REQUEST_TYPE, request.get("requestType"));
        assertEquals(true, request.get("trustedInputsOnly"));
        assertEquals(false, request.get("callerSuppliedIdentityAllowed"));
        assertEquals(false, request.get("callerHeadersAllowed"));
        assertEquals(false, request.get("authorizationHeaderFromCallerAllowed"));
        assertEquals(false, request.get("realApiKeyAllowed"));
        assertEquals(boundaryReport.get("boundaryPlanDigest"), request.get("sourceBoundaryPlanDigest"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> phases = (List<Map<String, Object>>) request.get("phaseContracts");
        assertEquals(4, phases.size());
        assertEquals("PROBE_STORAGE", phases.get(0).get("phase"));
        assertEquals("PRE_WRITE_INTENT", phases.get(1).get("phase"));
        assertEquals("POST_WRITE_RESULT", phases.get(2).get("phase"));
        assertEquals("ASSEMBLE_RECEIPT", phases.get(3).get("phase"));
        assertTrue(phases.stream().allMatch(phase -> Boolean.TRUE.equals(phase.get("futureOnly"))));
        assertTrue(phases.stream().allMatch(phase -> Boolean.FALSE.equals(phase.get("sideEffectAllowedNow"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) spec.get("responseContract");
        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.RESPONSE_TYPE, response.get("responseType"));
        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.HOLD_STATE,
            response.get("currentImplementationStatus"));
        assertEquals(false, response.get("successAllowedNow"));
        assertEquals(false, response.get("durableReceiptAllowedNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> currentResponse = (Map<String, Object>) response.get("currentResponseTemplate");
        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.HOLD_STATE, currentResponse.get("status"));
        assertEquals(false, currentResponse.get("storageAvailable"));
        assertEquals(false, currentResponse.get("preWritePersisted"));
        assertEquals(false, currentResponse.get("postWritePersisted"));
        assertEquals(false, currentResponse.get("durableReceiptCanBeIssued"));
        assertEquals("NOT_ISSUED", currentResponse.get("receiptStatus"));
        assertEquals("NONE", currentResponse.get("storageMode"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> methods = (List<Map<String, Object>>) spec.get("operationMethods");
        assertEquals(4, methods.size());
        assertEquals("probeStorageAvailability", methods.get(0).get("name"));
        assertEquals("persistPreWriteIntent", methods.get(1).get("name"));
        assertEquals("persistPostWriteResult", methods.get(2).get("name"));
        assertEquals("assembleDurableReceipt", methods.get(3).get("name"));
        assertTrue(methods.stream().allMatch(method -> Boolean.FALSE.equals(method.get("sideEffectAllowedNow"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) spec.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToMockReceiptAllowed"));
        assertEquals(false, failure.get("fallbackToBoundaryPlanAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> testDoubleRules = (Map<String, Object>) spec.get("testDoubleRules");
        assertEquals("UNIT_CONTRACT_ONLY", testDoubleRules.get("testDoubleScope"));
        assertEquals(boundaryReport.get("boundaryPlanDigest"), testDoubleRules.get("sourceBoundaryPlanDigest"));
        assertEquals(true, testDoubleRules.get("mustKeepStorageAvailableFalse"));
        assertEquals(true, testDoubleRules.get("mustKeepPreWritePersistedFalse"));
        assertEquals(true, testDoubleRules.get("mustKeepPostWritePersistedFalse"));
        assertEquals(true, testDoubleRules.get("mustKeepDurableReceiptNotIssued"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_INTERFACE_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void interfaceSpec_shouldRejectMissingBoundaryReport() {
        Map<String, Object> report = NimCreateDurableAuditWriterInterfaceSpecSupport.plan(
            new NimCreateDurableAuditWriterInterfaceSpecSupport.DurableAuditWriterInterfaceSpecInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.REJECTED_STATE,
            report.get("interfaceSpecState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("interfaceSpecPrepared"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("interfaceSpec");
        assertTrue(spec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_WRITER_INTERFACE_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void interfaceSpec_shouldRejectForgedBoundarySuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedBoundaryReport = new LinkedHashMap<>(boundaryReport(audit, principal));
        forgedBoundaryReport.put("storageAvailable", true);
        forgedBoundaryReport.put("preWritePersisted", true);
        forgedBoundaryReport.put("postWritePersisted", true);
        forgedBoundaryReport.put("storageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE);

        Map<String, Object> report = NimCreateDurableAuditWriterInterfaceSpecSupport.plan(
            new NimCreateDurableAuditWriterInterfaceSpecSupport.DurableAuditWriterInterfaceSpecInput(
                audit,
                principal,
                forgedBoundaryReport
            )
        );

        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.REJECTED_STATE,
            report.get("interfaceSpecState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("interfaceSpec");
        assertTrue(spec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_INVALID_FOR_INTERFACE_SPEC");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_INTERFACE_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void interfaceSpec_shouldRejectSecretLeakageBeforeAnySpec() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "redacted-test-value");
        Map<String, Object> cleanAudit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditWriterInterfaceSpecSupport.plan(
            new NimCreateDurableAuditWriterInterfaceSpecSupport.DurableAuditWriterInterfaceSpecInput(
                audit,
                principal,
                boundaryReport(cleanAudit, principal)
            )
        );

        assertEquals(NimCreateDurableAuditWriterInterfaceSpecSupport.REJECTED_STATE,
            report.get("interfaceSpecState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("interfaceSpec");
        assertTrue(spec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_INTERFACE_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    private Map<String, Object> boundaryReport(Map<String, Object> audit,
                                               Map<String, Object> principal) {
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
        return NimCreateDedicatedDurableAuditWriterBoundarySupport.plan(
            new NimCreateDedicatedDurableAuditWriterBoundarySupport.DedicatedAuditWriterBoundaryInput(
                audit,
                principal,
                writerPlanReport,
                availabilityGateReport
            )
        );
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
