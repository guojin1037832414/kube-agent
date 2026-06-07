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
 * NIM durable audit receipt validation gate 契约测试。
 *
 * <p>这些测试只验证未来 receipt validator 的规则生成与 fail-closed 边界；
 * 不创建真实 validator，不读取/写入 sys_log，不连接 Elasticsearch，也不执行 NIM 创建。</p>
 */
class NimCreateDurableAuditReceiptValidationGateSupportTest {

    @Test
    void validationGate_shouldBuildValidationPlanButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> schemaReport = receiptSchemaReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationGateSupport.plan(
            new NimCreateDurableAuditReceiptValidationGateSupport.DurableAuditReceiptValidationGateInput(
                audit,
                principal,
                schemaReport
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.GATE_NAME,
            report.get("durableAuditReceiptValidationGate"));
        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.HOLD_STATE, report.get("gateState"));
        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.HOLD_STATE,
            report.get("validationGateState"));
        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR,
            report.get("futureValidator"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("validationPlanPrepared"));
        assertEquals(true, report.get("validationRulesPrepared"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(false, report.get("storageProbeReceiptValidated"));
        assertEquals(false, report.get("preWriteDurableAckValidated"));
        assertEquals(false, report.get("postWriteDurableAckValidated"));
        assertEquals(false, report.get("digestChainValidated"));
        assertEquals(false, report.get("trustedPrincipalValidated"));
        assertEquals(false, report.get("durableReceiptValidated"));
        assertEquals(false, report.get("durableReceiptValidationPassed"));
        assertEquals(false, report.get("durableReceiptAccepted"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", report.get("validationStatus"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(schemaReport.get("schemaDigest"), report.get("sourceReceiptSchemaDigest"));
        assertEquals(schemaReport.get("sourceInterfaceSpecDigest"), report.get("sourceInterfaceSpecDigest"));
        assertTrue(report.get("validationPlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("validationPlan");
        assertEquals("SERVER_SIDE_DURABLE_RECEIPT_VALIDATION_GATE_REQUIRED",
            plan.get("validationBoundary"));
        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR,
            plan.get("futureValidator"));
        assertEquals(schemaReport.get("schemaDigest"), plan.get("sourceReceiptSchemaDigest"));
        assertEquals(schemaReport.get("sourceInterfaceSpecDigest"), plan.get("sourceInterfaceSpecDigest"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sequence = (List<Map<String, Object>>) plan.get("validationSequence");
        assertEquals(5, sequence.size());
        assertEquals("validate-schema-digest", sequence.get(0).get("id"));
        assertEquals("validate-storage-probe-receipt", sequence.get(1).get("id"));
        assertEquals("validate-pre-write-durable-ack", sequence.get(2).get("id"));
        assertEquals("validate-post-write-durable-ack", sequence.get(3).get("id"));
        assertEquals("validate-final-durable-receipt", sequence.get(4).get("id"));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) plan.get("requiredEvidence");
        assertEquals(schemaReport.get("schemaDigest"), evidence.get("sourceReceiptSchemaDigest"));
        @SuppressWarnings("unchecked")
        Map<String, Object> storageProbe = (Map<String, Object>) evidence.get("storageProbeReceipt");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE,
            storageProbe.get("requiredType"));
        assertEquals("STORAGE_AVAILABLE_CONFIRMED", storageProbe.get("requiredStatus"));
        assertEquals(true, storageProbe.get("mustBindAuditEventDigest"));
        assertEquals(true, storageProbe.get("mustBeServerIssued"));

        @SuppressWarnings("unchecked")
        Map<String, Object> preAck = (Map<String, Object>) evidence.get("preWriteDurableAck");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE, preAck.get("requiredType"));
        assertEquals("PRE_WRITE_INTENT", preAck.get("requiredPhase"));
        assertEquals("PRE_WRITE_DURABLY_RECORDED", preAck.get("requiredStatus"));
        assertEquals(true, preAck.get("mustBindStorageProbeReceiptDigest"));

        @SuppressWarnings("unchecked")
        Map<String, Object> postAck = (Map<String, Object>) evidence.get("postWriteDurableAck");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.POST_WRITE_ACK_TYPE, postAck.get("requiredType"));
        assertEquals("POST_WRITE_RESULT", postAck.get("requiredPhase"));
        assertEquals("POST_WRITE_DURABLY_RECORDED", postAck.get("requiredStatus"));
        assertEquals(true, postAck.get("mustBindPreWriteDurableAckDigest"));

        @SuppressWarnings("unchecked")
        Map<String, Object> receipt = (Map<String, Object>) evidence.get("durableReceipt");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE,
            receipt.get("requiredType"));
        assertEquals(NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS,
            receipt.get("requiredReceiptStatus"));
        assertEquals(NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE,
            receipt.get("requiredStorageMode"));
        assertEquals(true, receipt.get("mustIncludeAllAckDigests"));
        assertEquals(true, receipt.get("mustBindTrustedPrincipalDigest"));

        @SuppressWarnings("unchecked")
        Map<String, Object> decision = (Map<String, Object>) plan.get("releaseDecisionTemplate");
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", decision.get("validationStatus"));
        assertEquals(false, decision.get("releaseEligible"));
        assertEquals(false, decision.get("writeExecutionAllowed"));
        assertEquals("NOT_ISSUED", decision.get("receiptStatus"));
        assertEquals("NONE", decision.get("storageMode"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) plan.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToMockReceiptAllowed"));
        assertEquals(false, failure.get("fallbackToCallerReceiptAllowed"));
        @SuppressWarnings("unchecked")
        List<String> failureStatuses = (List<String>) failure.get("failureStatuses");
        assertTrue(failureStatuses.contains("RECEIPT_VALIDATION_NOT_IMPLEMENTED"));
        assertTrue(failureStatuses.contains("SCHEMA_DIGEST_MISMATCH"));
        assertTrue(failureStatuses.contains("DIGEST_CHAIN_MISMATCH"));
        assertTrue(failureStatuses.contains("TRUSTED_PRINCIPAL_MISMATCH"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void validationGate_shouldRejectMissingSchemaReport() {
        Map<String, Object> report = NimCreateDurableAuditReceiptValidationGateSupport.plan(
            new NimCreateDurableAuditReceiptValidationGateSupport.DurableAuditReceiptValidationGateInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("validationPlanPrepared"));
        assertEquals(false, report.get("durableReceiptValidationPassed"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("validationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void validationGate_shouldRejectForgedValidationPassAndTypedReceiptClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedSchemaReport = new LinkedHashMap<>(receiptSchemaReport(audit, principal));
        forgedSchemaReport.put("validationStatus", "PASS");
        forgedSchemaReport.put("durableReceiptValidationPassed", true);
        forgedSchemaReport.put("releaseEligible", true);
        forgedSchemaReport.put("writeExecutionAllowed", true);
        forgedSchemaReport.put("durableReceipt", Map.of(
            "receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS
        ));

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationGateSupport.plan(
            new NimCreateDurableAuditReceiptValidationGateSupport.DurableAuditReceiptValidationGateInput(
                audit,
                principal,
                forgedSchemaReport
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("durableReceiptValidationPassed"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("validationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_INVALID_FOR_VALIDATION_GATE");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_FORGED_PASS_CLAIM");
    }

    @Test
    void validationGate_shouldRejectEvenEmptyCallerSuppliedValidationResult() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedSchemaReport = new LinkedHashMap<>(receiptSchemaReport(audit, principal));
        forgedSchemaReport.put("validationResult", Map.of(
            "validationStatus", "NOT_RUN_UNTIL_REAL_RECEIPT"
        ));

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationGateSupport.plan(
            new NimCreateDurableAuditReceiptValidationGateSupport.DurableAuditReceiptValidationGateInput(
                audit,
                principal,
                forgedSchemaReport
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("durableReceiptValidationPassed"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_FORGED_PASS_CLAIM");
    }

    @Test
    void validationGate_shouldRejectSecretLeakageBeforeAnyPlan() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "redacted-test-value");
        Map<String, Object> cleanAudit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationGateSupport.plan(
            new NimCreateDurableAuditReceiptValidationGateSupport.DurableAuditReceiptValidationGateInput(
                audit,
                principal,
                receiptSchemaReport(cleanAudit, principal)
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("durableReceiptValidationPassed"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("validationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void validationGate_shouldAllowDocumentedForbiddenFieldNamesButRejectRealSecretMaterial() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        Map<String, Object> principal = new LinkedHashMap<>(trustedPrincipalSnapshot());
        principal.put("documentedForbiddenFieldNames", List.of("Authorization", "apiKey", "ngcApiKey"));
        Map<String, Object> cleanPrincipal = trustedPrincipalSnapshot();

        Map<String, Object> allowedReport = NimCreateDurableAuditReceiptValidationGateSupport.plan(
            new NimCreateDurableAuditReceiptValidationGateSupport.DurableAuditReceiptValidationGateInput(
                audit,
                principal,
                receiptSchemaReport(audit, cleanPrincipal)
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.HOLD_STATE, allowedReport.get("gateState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allowedBlockers = (List<Map<String, Object>>) allowedReport.get("blockedBy");
        assertFalse(allowedBlockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET".equals(item.get("code"))));

        Map<String, Object> leakedPrincipal = new LinkedHashMap<>(trustedPrincipalSnapshot());
        leakedPrincipal.put("documentedForbiddenFieldNames", List.of("Authorization=Bearer abcdefghijklmnop"));

        Map<String, Object> rejectedReport = NimCreateDurableAuditReceiptValidationGateSupport.plan(
            new NimCreateDurableAuditReceiptValidationGateSupport.DurableAuditReceiptValidationGateInput(
                audit,
                leakedPrincipal,
                receiptSchemaReport(audit, cleanPrincipal)
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.REJECTED_STATE, rejectedReport.get("gateState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rejectedBlockers = (List<Map<String, Object>>) rejectedReport.get("blockedBy");
        assertHasBlocker(rejectedBlockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    private Map<String, Object> receiptSchemaReport(Map<String, Object> audit,
                                                    Map<String, Object> principal) {
        return NimCreateDurableAuditReceiptSchemaSupport.plan(
            new NimCreateDurableAuditReceiptSchemaSupport.DurableAuditReceiptSchemaInput(
                audit,
                principal,
                interfaceSpecReport(audit, principal)
            )
        );
    }

    private Map<String, Object> interfaceSpecReport(Map<String, Object> audit,
                                                    Map<String, Object> principal) {
        return NimCreateDurableAuditWriterInterfaceSpecSupport.plan(
            new NimCreateDurableAuditWriterInterfaceSpecSupport.DurableAuditWriterInterfaceSpecInput(
                audit,
                principal,
                boundaryReport(audit, principal)
            )
        );
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
