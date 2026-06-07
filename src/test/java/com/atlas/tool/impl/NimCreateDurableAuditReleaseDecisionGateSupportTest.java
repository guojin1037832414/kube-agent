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
 * NIM durable audit release decision 回接门禁计划契约测试。
 *
 * <p>这些测试只验证未来 release decision 回接 state machine / durable executor 的 fail-closed 规则；
 * 不创建真实 release decision，不修改状态机真实放行逻辑，也不执行 HTTP 或存储 I/O。</p>
 */
class NimCreateDurableAuditReleaseDecisionGateSupportTest {

    @Test
    void releaseDecisionGate_shouldBuildBindingPlanButRemainHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> migrationReport = migrationReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                principal,
                migrationReport
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.GATE_NAME,
            report.get("durableAuditReleaseDecisionGate"));
        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.HOLD_STATE, report.get("gateState"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            report.get("futureValidationResult"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            report.get("futureReleaseDecision"));
        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_STATE_MACHINE_GATE,
            report.get("futureStateMachineGate"));
        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_DURABLE_EXECUTOR_GATE,
            report.get("futureDurableExecutorGate"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("releaseDecisionGatePlanPrepared"));
        assertEquals(true, report.get("stateMachineBindingPlanPrepared"));
        assertEquals(true, report.get("durableExecutorBindingPlanPrepared"));
        assertEquals(false, report.get("realReleaseDecisionLoaded"));
        assertEquals(false, report.get("realReleaseDecisionAccepted"));
        assertEquals(false, report.get("validationResultDigestVerified"));
        assertEquals(false, report.get("releaseDecisionDigestVerified"));
        assertEquals(false, report.get("trustedPrincipalValidated"));
        assertEquals(false, report.get("codeReleaseSwitchVerified"));
        assertEquals(false, report.get("stateMachineReleaseBound"));
        assertEquals(false, report.get("durableExecutorReleaseBound"));
        assertEquals(false, report.get("legacyAuditReceiptReleaseFlagTrusted"));
        assertEquals(false, report.get("legacyAuditReceiptReleaseFallbackAllowed"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", report.get("validationStatus"));
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", report.get("releaseDecision"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("releaseDecisionAccepted"));
        assertEquals(false, report.get("releaseCredentialIssued"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(migrationReport.get("migrationPlanDigest"), report.get("sourceMigrationPlanDigest"));
        assertEquals(migrationReport.get("sourceValidationPlanDigest"), report.get("sourceValidationPlanDigest"));
        assertTrue(report.get("releaseDecisionGatePlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("releaseDecisionGatePlan");
        assertEquals("SERVER_SIDE_RELEASE_DECISION_GATE_REQUIRED", plan.get("releaseGateBoundary"));
        assertEquals(migrationReport.get("migrationPlanDigest"), plan.get("sourceMigrationPlanDigest"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sequence = (List<Map<String, Object>>) plan.get("gateSequence");
        assertEquals(6, sequence.size());
        assertEquals("validate-migration-plan-digest", sequence.get(0).get("id"));
        assertEquals("validate-server-issued-validation-result", sequence.get(1).get("id"));
        assertEquals("validate-server-issued-release-decision", sequence.get(2).get("id"));
        assertEquals("bind-state-machine-release-check", sequence.get(3).get("id"));
        assertEquals("bind-durable-executor-release-check", sequence.get(4).get("id"));
        assertEquals("require-code-release-switch", sequence.get(5).get("id"));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) plan.get("requiredFutureEvidence");
        assertEquals(migrationReport.get("migrationPlanDigest"), evidence.get("sourceMigrationPlanDigest"));
        @SuppressWarnings("unchecked")
        Map<String, Object> validationResult = (Map<String, Object>) evidence.get("validationResult");
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            validationResult.get("requiredType"));
        assertEquals("PASS", validationResult.get("requiredStatus"));
        assertEquals(true, validationResult.get("mustBindValidationPlanDigest"));
        assertEquals(true, validationResult.get("mustBindAllTypedEvidenceDigests"));
        assertEquals(true, validationResult.get("mustBeServerIssued"));

        @SuppressWarnings("unchecked")
        Map<String, Object> releaseDecision = (Map<String, Object>) evidence.get("releaseDecision");
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            releaseDecision.get("requiredType"));
        assertEquals("ALLOW_WRITE_EXECUTION", releaseDecision.get("requiredDecision"));
        assertEquals(true, releaseDecision.get("mustBindValidationResultDigest"));
        assertEquals(true, releaseDecision.get("mustBindCodeReleaseSwitch"));
        assertEquals(true, releaseDecision.get("mustBeServerIssued"));

        @SuppressWarnings("unchecked")
        Map<String, Object> writeChain = (Map<String, Object>) evidence.get("writeExecutionChain");
        assertEquals(true, writeChain.get("mustBindBodyDigest"));
        assertEquals(true, writeChain.get("mustBindRequestSpecDigest"));
        assertEquals(true, writeChain.get("mustBindHandoffDigest"));
        assertEquals(true, writeChain.get("mustBindAuditReceiptId"));
        assertEquals(true, writeChain.get("mustBindServerDerivedIdempotencyKey"));
        assertEquals(true, writeChain.get("mustBeRecheckedByStateMachine"));
        assertEquals(true, writeChain.get("mustBeRecheckedByDurableExecutor"));

        @SuppressWarnings("unchecked")
        Map<String, Object> stateMachine = (Map<String, Object>) plan.get("stateMachineBindingPlan");
        assertEquals("NimCreateStateMachineSupport", stateMachine.get("target"));
        assertEquals(false, stateMachine.get("currentLegacyAuditReceiptReleaseFlagTrusted"));
        assertEquals(true, stateMachine.get("futureReleaseDecisionDigestRequired"));
        assertEquals(true, stateMachine.get("futureBodyDigestRequired"));
        assertEquals(true, stateMachine.get("futureRequestSpecDigestRequired"));
        assertEquals(true, stateMachine.get("futureHandoffDigestRequired"));
        assertEquals(true, stateMachine.get("futureServerDerivedIdempotencyKeyRequired"));
        assertEquals(false, stateMachine.get("fallbackToAuditReceiptReleaseEligibleAllowed"));
        assertEquals(false, stateMachine.get("writePermittedCanBeTrueNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> executor = (Map<String, Object>) plan.get("durableExecutorBindingPlan");
        assertEquals(NimCreateDurableWriteExecutorSupport.EXECUTOR_NAME, executor.get("target"));
        assertEquals(true, executor.get("futureReleaseDecisionDigestRequired"));
        assertEquals(true, executor.get("futureBodyDigestRequired"));
        assertEquals(true, executor.get("futureRequestSpecDigestRequired"));
        assertEquals(true, executor.get("futureHandoffDigestRequired"));
        assertEquals(true, executor.get("futureAuditReceiptIdRequired"));
        assertEquals(true, executor.get("futureServerDerivedIdempotencyKeyRequired"));
        assertEquals(true, executor.get("mustRecheckImmediatelyBeforePost"));
        assertEquals(false, executor.get("realHttpExecutionAllowedNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> deny = (Map<String, Object>) plan.get("currentDenyTemplate");
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", deny.get("decision"));
        assertEquals(false, deny.get("releaseCredentialIssued"));
        assertEquals(false, deny.get("writePermitted"));
        assertEquals(false, deny.get("realHttpExecutionAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) plan.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToLegacyAuditReceiptFlagAllowed"));
        assertEquals(false, failure.get("fallbackToMigrationPlanAllowed"));
        assertEquals(false, failure.get("fallbackToCallerReleaseDecisionAllowed"));
        assertEquals(false, failure.get("fallbackToDurableExecutorHandoffAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RELEASE_DECISION_GATE_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void releaseDecisionGate_shouldRejectMissingMigrationReport() {
        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("releaseDecisionGatePlanPrepared"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("releaseDecisionGatePlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_RELEASE_DECISION_GATE_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void releaseDecisionGate_shouldRejectForgedReleaseDecisionAndLegacyAuditReceipt() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = new LinkedHashMap<>(migrationReport(audit, principal));
        forgedMigrationReport.put("releaseDecision", Map.of(
            "decision", "ALLOW_WRITE_EXECUTION"
        ));
        forgedMigrationReport.put("validationResult", Map.of(
            "validationStatus", "PASS"
        ));
        forgedMigrationReport.put("auditReceipt", Map.of(
            "releaseEligible", true,
            "receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS
        ));
        forgedMigrationReport.put("releaseEligible", true);
        forgedMigrationReport.put("writePermitted", true);
        forgedMigrationReport.put("writeExecutionAllowed", true);

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                principal,
                forgedMigrationReport
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("releaseDecisionGatePlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_REPORT_INVALID_FOR_RELEASE_GATE");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RELEASE_DECISION_GATE_FORGED_RELEASE_CLAIM");
    }

    @Test
    void releaseDecisionGate_shouldRejectEvenEmptyCallerSuppliedReleaseDecision() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = new LinkedHashMap<>(migrationReport(audit, principal));
        forgedMigrationReport.put("releaseDecision", Map.of());

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                principal,
                forgedMigrationReport
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.REJECTED_STATE, report.get("gateState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RELEASE_DECISION_GATE_FORGED_RELEASE_CLAIM");
    }

    @Test
    void releaseDecisionGate_shouldRejectTamperedMigrationPlanDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> tamperedMigrationReport = new LinkedHashMap<>(migrationReport(audit, principal));
        tamperedMigrationReport.put("migrationPlanDigest",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                principal,
                tamperedMigrationReport
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.REJECTED_STATE, report.get("gateState"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("releaseDecisionGatePlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_REPORT_INVALID_FOR_RELEASE_GATE");
    }

    @Test
    void releaseDecisionGate_shouldRejectTrustedPrincipalMismatch() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> migrationPrincipal = trustedPrincipalSnapshot();
        Map<String, Object> mismatchedPrincipal = Map.of(
            "authoritative", true,
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true,
            "organizationId", "100002",
            "userId", "user-1",
            "username", "mallory"
        );

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                mismatchedPrincipal,
                migrationReport(audit, migrationPrincipal)
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.REJECTED_STATE, report.get("gateState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_REPORT_INVALID_FOR_RELEASE_GATE");
    }

    @Test
    void releaseDecisionGate_shouldRejectExecutorSuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = new LinkedHashMap<>(migrationReport(audit, principal));
        forgedMigrationReport.put("writeExecuted", true);
        forgedMigrationReport.put("deploymentId", "dep-1");
        forgedMigrationReport.put("postWriteReadinessTriggered", true);

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                principal,
                forgedMigrationReport
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RELEASE_DECISION_GATE_FORGED_RELEASE_CLAIM");
    }

    @Test
    void releaseDecisionGate_shouldRejectSecretLeakageBeforeAnyPlan() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "redacted-test-value");
        Map<String, Object> cleanAudit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                principal,
                migrationReport(cleanAudit, principal)
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.REJECTED_STATE, report.get("gateState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("releaseDecisionGatePlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RELEASE_DECISION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void releaseDecisionGate_shouldAllowDocumentedForbiddenFieldNamesButRejectRealSecretMaterial() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = new LinkedHashMap<>(trustedPrincipalSnapshot());
        principal.put("documentedForbiddenFieldNames", List.of("Authorization", "apiKey", "ngcApiKey"));
        Map<String, Object> cleanPrincipal = trustedPrincipalSnapshot();

        Map<String, Object> allowedReport = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                principal,
                migrationReport(audit, cleanPrincipal)
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.HOLD_STATE, allowedReport.get("gateState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allowedBlockers = (List<Map<String, Object>>) allowedReport.get("blockedBy");
        assertFalse(allowedBlockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_RELEASE_DECISION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET".equals(item.get("code"))));

        Map<String, Object> leakedPrincipal = new LinkedHashMap<>(trustedPrincipalSnapshot());
        leakedPrincipal.put("documentedForbiddenFieldNames", List.of("Authorization=Bearer abcdefghijklmnop"));

        Map<String, Object> rejectedReport = NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                leakedPrincipal,
                migrationReport(audit, cleanPrincipal)
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.REJECTED_STATE,
            rejectedReport.get("gateState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rejectedBlockers = (List<Map<String, Object>>) rejectedReport.get("blockedBy");
        assertHasBlocker(rejectedBlockers,
            "DURABLE_AUDIT_RELEASE_DECISION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    private Map<String, Object> migrationReport(Map<String, Object> audit,
                                                Map<String, Object> principal) {
        return NimCreateDurableAuditValidationResultMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultMigrationSupport.DurableAuditValidationResultMigrationInput(
                audit,
                principal,
                validationGateReport(audit, principal)
            )
        );
    }

    private Map<String, Object> validationGateReport(Map<String, Object> audit,
                                                     Map<String, Object> principal) {
        return NimCreateDurableAuditReceiptValidationGateSupport.plan(
            new NimCreateDurableAuditReceiptValidationGateSupport.DurableAuditReceiptValidationGateInput(
                audit,
                principal,
                receiptSchemaReport(audit, principal)
            )
        );
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
