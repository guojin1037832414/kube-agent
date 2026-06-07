package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM durable audit storage probe executor 契约壳测试。
 *
 * <p>这些测试不连接 Elasticsearch，不调用 ISysLogService，不写 sys_log，也不访问 kube-manager。
 * 目标是锁定：未来真实 storage probe executor 必须位于 dedicated writer boundary 内；当前合同壳不能把
 * availability gate、writer boundary test double 或调用方 snapshot 伪造成真实 storage success。</p>
 */
class NimCreateDurableAuditStorageProbeExecutorSupportTest {

    @Test
    void storageProbeExecutor_shouldBuildProbeAttemptSpecButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
        Map<String, Object> boundaryReport = writerBoundaryReport(audit, principal, writerPlanReport, availabilityGateReport);

        Map<String, Object> report = NimCreateDurableAuditStorageProbeExecutorSupport.plan(
            new NimCreateDurableAuditStorageProbeExecutorSupport.StorageProbeExecutorInput(
                audit,
                principal,
                availabilityGateReport,
                boundaryReport,
                Map.of("diagnosticOnly", true)
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.EXECUTOR_NAME,
            report.get("durableAuditStorageProbeExecutor"));
        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.HOLD_STATE, report.get("probeExecutorState"));
        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.HOLD_STATE, report.get("probeState"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(false, report.get("springBeanRegistered"));
        assertEquals(false, report.get("httpClientBound"));
        assertEquals(false, report.get("storageClientBound"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("probeExecutorPlanPrepared"));
        assertEquals(true, report.get("probeAttemptSpecPrepared"));
        assertEquals(true, report.get("diagnosticProbeSnapshotObserved"));
        assertEquals(false, report.get("diagnosticProbeSnapshotAuthoritative"));
        assertEquals(true, report.get("requiredInsideDedicatedWriterBoundary"));
        assertEquals(true, report.get("requiredBeforePreWrite"));
        assertSuccessStatesRemainFalse(report);
        assertEquals("sys_log", report.get("candidateIndex"));
        assertEquals(availabilityGateReport.get("sourceWriterPlanDigest"), report.get("sourceWriterPlanDigest"));
        assertEquals(availabilityGateReport.get("availabilityPlanDigest"), report.get("sourceAvailabilityPlanDigest"));
        assertEquals(boundaryReport.get("boundaryPlanDigest"), report.get("sourceBoundaryPlanDigest"));
        assertTrue(report.get("probeExecutorPlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("probeAttemptSpec");
        assertEquals("SERVER_SIDE_DURABLE_AUDIT_STORAGE_PROBE_EXECUTOR_REQUIRED", spec.get("executorBoundary"));
        assertEquals("NimDurableAuditStorageProbeExecutor", spec.get("futureInterface"));
        assertEquals("INSIDE_DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY", spec.get("executionPlacement"));
        assertEquals("sys_log", spec.get("targetStorage"));
        assertEquals(false, spec.get("sideEffectAllowedNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) spec.get("evidenceBinding");
        assertEquals(availabilityGateReport.get("availabilityPlanDigest"), evidence.get("sourceAvailabilityPlanDigest"));
        assertEquals(boundaryReport.get("boundaryPlanDigest"), evidence.get("sourceBoundaryPlanDigest"));
        assertEquals(true, evidence.get("probeResultMustBeServerIssued"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> operationOrder = (List<Map<String, Object>>) spec.get("operationOrder");
        assertEquals(7, operationOrder.size());
        assertEquals("validate-availability-gate-report", operationOrder.get(0).get("id"));
        assertEquals("validate-dedicated-writer-boundary", operationOrder.get(1).get("id"));
        assertEquals("return-server-issued-probe-result", operationOrder.get(6).get("id"));
        assertTrue(operationOrder.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly"))));
        assertTrue(operationOrder.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow"))));
        assertTrue(operationOrder.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> currentState = (Map<String, Object>) spec.get("currentImplementationState");
        assertEquals(false, currentState.get("executorImplemented"));
        assertEquals(false, currentState.get("storageProbeExecuted"));
        assertEquals(false, currentState.get("storageAvailable"));
        assertEquals(false, currentState.get("preWriteAllowed"));
        assertEquals(false, currentState.get("durableReceiptCanBeIssued"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failurePolicy = (Map<String, Object>) spec.get("failurePolicy");
        assertEquals(true, failurePolicy.get("failClosed"));
        assertEquals(true, failurePolicy.get("blockPreWriteWhenDigestMismatch"));
        assertEquals(true, failurePolicy.get("blockPreWriteWhenPrincipalMismatch"));
        assertEquals(false, failurePolicy.get("fallbackToMockReceiptAllowed"));
        assertEquals(false, failurePolicy.get("fallbackToCallerProbeSnapshotAllowed"));
        assertEquals(false, failurePolicy.get("fallbackToAvailabilityPlanAllowed"));
        assertEquals(false, failurePolicy.get("fallbackToWriterBoundaryTestDoubleAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_PROBE_EXECUTOR_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void storageProbeExecutor_shouldRejectMissingAvailabilityGateReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditStorageProbeExecutorSupport.plan(
            new NimCreateDurableAuditStorageProbeExecutorSupport.StorageProbeExecutorInput(
                audit,
                principal,
                Map.of(),
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.REJECTED_STATE,
            report.get("probeExecutorState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("probeAttemptSpecPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("probeAttemptSpec");
        assertTrue(spec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_AVAILABILITY_GATE_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "STORAGE_PROBE_EXECUTOR_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void storageProbeExecutor_shouldRejectMissingDedicatedWriterBoundaryReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);

        Map<String, Object> report = NimCreateDurableAuditStorageProbeExecutorSupport.plan(
            new NimCreateDurableAuditStorageProbeExecutorSupport.StorageProbeExecutorInput(
                audit,
                principal,
                availabilityGateReport,
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.REJECTED_STATE,
            report.get("probeExecutorState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("probeAttemptSpecPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("probeAttemptSpec");
        assertTrue(spec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_NOT_READY");
    }

    @Test
    void storageProbeExecutor_shouldRejectForgedProbeSuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
        Map<String, Object> boundaryReport = writerBoundaryReport(audit, principal, writerPlanReport, availabilityGateReport);
        Map<String, Object> forgedSnapshot = Map.of(
            "nested", Map.of("durableAckVerified", true),
            "items", List.of(Map.of("readAfterWriteVerified", true))
        );

        Map<String, Object> report = NimCreateDurableAuditStorageProbeExecutorSupport.plan(
            new NimCreateDurableAuditStorageProbeExecutorSupport.StorageProbeExecutorInput(
                audit,
                principal,
                availabilityGateReport,
                boundaryReport,
                forgedSnapshot
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.REJECTED_STATE,
            report.get("probeExecutorState"));
        assertEquals(false, report.get("inputAccepted"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("probeAttemptSpec");
        assertTrue(spec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_PROBE_EXECUTOR_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void storageProbeExecutor_shouldRejectForgedBoundarySuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
        Map<String, Object> forgedBoundaryReport = new LinkedHashMap<>(
            writerBoundaryReport(audit, principal, writerPlanReport, availabilityGateReport)
        );
        forgedBoundaryReport.put("preWritePersisted", true);
        forgedBoundaryReport.put("postWritePersisted", true);
        forgedBoundaryReport.put("writeExecutionAllowed", true);
        forgedBoundaryReport.put("receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS);

        Map<String, Object> report = NimCreateDurableAuditStorageProbeExecutorSupport.plan(
            new NimCreateDurableAuditStorageProbeExecutorSupport.StorageProbeExecutorInput(
                audit,
                principal,
                availabilityGateReport,
                forgedBoundaryReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.REJECTED_STATE,
            report.get("probeExecutorState"));
        assertEquals(false, report.get("inputAccepted"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_INVALID_FOR_PROBE_EXECUTOR");
        assertHasBlocker(blockers, "STORAGE_PROBE_EXECUTOR_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void storageProbeExecutor_shouldRejectSecretLeakageBeforeAttemptSpec() {
        String injectedSecret = "Bearer redacted-test-material";
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = new LinkedHashMap<>(trustedPrincipalSnapshot());
        principal.put("sessionEvidence", List.of(Map.of("password", injectedSecret)));
        Map<String, Object> cleanPrincipal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, cleanPrincipal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, cleanPrincipal, writerPlanReport);
        Map<String, Object> boundaryReport = writerBoundaryReport(audit, cleanPrincipal, writerPlanReport, availabilityGateReport);

        Map<String, Object> report = NimCreateDurableAuditStorageProbeExecutorSupport.plan(
            new NimCreateDurableAuditStorageProbeExecutorSupport.StorageProbeExecutorInput(
                audit,
                principal,
                availabilityGateReport,
                boundaryReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.REJECTED_STATE,
            report.get("probeExecutorState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("probeAttemptSpecPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("probeAttemptSpec");
        assertTrue(spec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_PROBE_EXECUTOR_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void storageProbeExecutor_shouldRejectNestedListSecretLeakageBeforeAttemptSpec() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
        Map<String, Object> boundaryReport = writerBoundaryReport(audit, principal, writerPlanReport, availabilityGateReport);
        Map<String, Object> probeSnapshot = Map.of(
            "diagnosticOnly", true,
            "observations", List.of(
                Map.of("note", "probe not executed"),
                Map.of("Authorization", "redacted-test-value")
            )
        );

        Map<String, Object> report = NimCreateDurableAuditStorageProbeExecutorSupport.plan(
            new NimCreateDurableAuditStorageProbeExecutorSupport.StorageProbeExecutorInput(
                audit,
                principal,
                availabilityGateReport,
                boundaryReport,
                probeSnapshot
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeExecutorSupport.REJECTED_STATE,
            report.get("probeExecutorState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("probeAttemptSpecPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> spec = (Map<String, Object>) report.get("probeAttemptSpec");
        assertTrue(spec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_PROBE_EXECUTOR_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void storageProbeExecutor_shouldNotDependOnRealStorageOrNetworkClients() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeExecutorSupport.java"
        ));

        assertFalse(source.contains("@Component"));
        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("@Autowired"));
        assertFalse(source.contains("KubeManagerHttpClient"));
        assertFalse(source.contains("RestClient"));
        assertFalse(source.contains("RestTemplate"));
        assertFalse(source.contains("WebClient"));
        assertFalse(source.contains("HttpClient"));
        assertFalse(source.contains("ElasticsearchTemplate"));
        assertFalse(source.contains("ISysLogService"));
        assertFalse(source.contains("java.net"));
        assertFalse(source.contains("POST /api/{orgId}/deployment"));
        assertFalse(source.matches("(?s).*\\.save\\s*\\(.*"));
        assertFalse(source.matches("(?s).*\\.insert\\s*\\(.*"));
        assertFalse(source.matches("(?s).*saveLog\\s*\\(.*"));
    }

    private void assertSuccessStatesRemainFalse(Map<String, Object> report) {
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("probeAttempted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN,
            report.get("availabilityStatus"));
        assertEquals(false, report.get("durableAckVerified"));
        assertEquals(false, report.get("durableAckReceived"));
        assertEquals(false, report.get("durableAckObserved"));
        assertEquals(false, report.get("readAfterWriteVerified"));
        assertEquals(false, report.get("preWriteAllowed"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(false, report.get("storageProbeReceiptIssued"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        assertEquals(false, report.get("durableReceiptIssued"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("durable"));
    }

    private Map<String, Object> writerBoundaryReport(Map<String, Object> audit,
                                                     Map<String, Object> principal,
                                                     Map<String, Object> writerPlanReport,
                                                     Map<String, Object> availabilityGateReport) {
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
