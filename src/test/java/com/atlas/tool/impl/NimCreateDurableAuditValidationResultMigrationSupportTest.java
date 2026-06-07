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
 * NIM durable audit validation result / release decision 迁移蓝图契约测试。
 *
 * <p>这些测试只验证未来强类型 validation result 与 release decision 的迁移边界；
 * 不创建真实 validator/DTO，不读写 sys_log，不连接 Elasticsearch，也不执行 NIM 创建。</p>
 */
class NimCreateDurableAuditValidationResultMigrationSupportTest {

    @Test
    void migrationPlan_shouldBuildResultAndReleaseDecisionContractsButRemainHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationGateReport = validationGateReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditValidationResultMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultMigrationSupport.DurableAuditValidationResultMigrationInput(
                audit,
                principal,
                validationGateReport
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.PLAN_NAME,
            report.get("durableAuditValidationResultMigrationPlan"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.HOLD_STATE,
            report.get("migrationPlanState"));
        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR,
            report.get("futureValidator"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            report.get("futureValidationResult"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            report.get("futureReleaseDecision"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("migrationPlanPrepared"));
        assertEquals(true, report.get("validationResultContractPrepared"));
        assertEquals(true, report.get("releaseDecisionContractPrepared"));
        assertEquals(false, report.get("realValidatorCreated"));
        assertEquals(false, report.get("realValidationResultCreated"));
        assertEquals(false, report.get("realReleaseDecisionCreated"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeReceiptValidated"));
        assertEquals(false, report.get("preWriteDurableAckValidated"));
        assertEquals(false, report.get("postWriteDurableAckValidated"));
        assertEquals(false, report.get("digestChainValidated"));
        assertEquals(false, report.get("trustedPrincipalValidated"));
        assertEquals(false, report.get("durableReceiptValidated"));
        assertEquals(false, report.get("durableReceiptValidationPassed"));
        assertEquals(false, report.get("durableReceiptAccepted"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", report.get("validationStatus"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("releaseDecisionAccepted"));
        assertEquals(false, report.get("releaseCredentialIssued"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("legacyAuditReceiptReleaseFlagTrusted"));
        assertEquals(validationGateReport.get("sourceReceiptSchemaDigest"),
            report.get("sourceReceiptSchemaDigest"));
        assertEquals(validationGateReport.get("validationPlanDigest"),
            report.get("sourceValidationPlanDigest"));
        assertTrue(report.get("migrationPlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("migrationPlan");
        assertEquals("SERVER_SIDE_VALIDATION_RESULT_AND_RELEASE_DECISION_REQUIRED",
            plan.get("migrationBoundary"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            plan.get("futureValidationResult"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            plan.get("futureReleaseDecision"));
        assertEquals(validationGateReport.get("sourceReceiptSchemaDigest"),
            plan.get("sourceReceiptSchemaDigest"));
        assertEquals(validationGateReport.get("validationPlanDigest"),
            plan.get("sourceValidationPlanDigest"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sequence = (List<Map<String, Object>>) plan.get("migrationSequence");
        assertEquals(5, sequence.size());
        assertEquals("keep-validation-gate-contract-only", sequence.get(0).get("id"));
        assertEquals("introduce-validation-result-value", sequence.get(1).get("id"));
        assertEquals("introduce-release-decision-value", sequence.get(2).get("id"));
        assertEquals("migrate-state-machine-release-check", sequence.get(3).get("id"));
        assertEquals("bind-durable-executor-release-check", sequence.get(4).get("id"));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> validationResult =
            (Map<String, Object>) plan.get("validationResultContract");
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            validationResult.get("type"));
        assertEquals(false, validationResult.get("instanceAllowedNow"));
        assertEquals(false, validationResult.get("sideEffectAllowedNow"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", validationResult.get("currentValidationStatus"));
        assertEquals("PASS", validationResult.get("requiredPassStatus"));
        assertEquals(true, validationResult.get("mustBindStorageProbeReceiptDigest"));
        assertEquals(true, validationResult.get("mustBindPreWriteDurableAckDigest"));
        assertEquals(true, validationResult.get("mustBindPostWriteDurableAckDigest"));
        assertEquals(true, validationResult.get("mustBindDurableReceiptDigest"));
        assertEquals(true, validationResult.get("mustBindTrustedPrincipalDigest"));
        assertEquals(true, validationResult.get("mustBeServerIssued"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultTemplate =
            (Map<String, Object>) validationResult.get("currentTemplate");
        assertEquals(false, resultTemplate.get("validationPassed"));
        assertEquals(false, resultTemplate.get("releaseEligible"));
        assertEquals(false, resultTemplate.get("writeExecutionAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> releaseDecision =
            (Map<String, Object>) plan.get("releaseDecisionContract");
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            releaseDecision.get("type"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            releaseDecision.get("dependsOn"));
        assertEquals(false, releaseDecision.get("instanceAllowedNow"));
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", releaseDecision.get("currentDecision"));
        assertEquals("ALLOW_WRITE_EXECUTION", releaseDecision.get("requiredAllowDecision"));
        assertEquals(true, releaseDecision.get("mustBindValidationResultDigest"));
        assertEquals(true, releaseDecision.get("mustBindCodeReleaseSwitch"));
        assertEquals(true, releaseDecision.get("mustBeServerIssued"));

        @SuppressWarnings("unchecked")
        Map<String, Object> decisionTemplate =
            (Map<String, Object>) releaseDecision.get("currentTemplate");
        assertEquals(false, decisionTemplate.get("releaseEligible"));
        assertEquals(false, decisionTemplate.get("writeExecutionAllowed"));
        assertEquals(false, decisionTemplate.get("fallbackToLegacyAuditReceiptFlagAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> legacyPolicy = (Map<String, Object>) plan.get("legacyCompatibilityPolicy");
        assertEquals(false, legacyPolicy.get("legacyAuditReceiptReleaseEligibleTrusted"));
        assertEquals(true, legacyPolicy.get("auditReceiptReleaseEligibleDeprecated"));
        assertEquals(false, legacyPolicy.get("fallbackToLegacyReleaseFlagAllowed"));
        assertEquals(true, legacyPolicy.get("stateMachineMigrationRequired"));

        @SuppressWarnings("unchecked")
        Map<String, Object> releaseRules = (Map<String, Object>) plan.get("releaseCredentialRules");
        assertEquals(false, releaseRules.get("migrationPlanIsReleaseCredential"));
        assertEquals(false, releaseRules.get("validationGateReportIsReleaseCredential"));
        assertEquals(false, releaseRules.get("legacyAuditReceiptFlagIsReleaseCredential"));
        assertEquals(true, releaseRules.get("futureReleaseDecisionRequired"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) plan.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToValidationGateAllowed"));
        assertEquals(false, failure.get("fallbackToCallerDecisionAllowed"));
        assertEquals(false, failure.get("fallbackToLegacyAuditReceiptFlagAllowed"));
        @SuppressWarnings("unchecked")
        List<String> failureStatuses = (List<String>) failure.get("failureStatuses");
        assertTrue(failureStatuses.contains("VALIDATION_RESULT_NOT_IMPLEMENTED"));
        assertTrue(failureStatuses.contains("RELEASE_DECISION_NOT_IMPLEMENTED"));
        assertTrue(failureStatuses.contains("LEGACY_AUDIT_RECEIPT_RELEASE_FLAG_NOT_TRUSTED"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void migrationPlan_shouldRejectMissingValidationGateReport() {
        Map<String, Object> report = NimCreateDurableAuditValidationResultMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultMigrationSupport.DurableAuditValidationResultMigrationInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.REJECTED_STATE,
            report.get("migrationPlanState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("migrationPlanPrepared"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("migrationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void migrationPlan_shouldRejectForgedValidationResultReleaseDecisionAndLegacyAuditReceipt() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedGateReport = new LinkedHashMap<>(validationGateReport(audit, principal));
        forgedGateReport.put("validationStatus", "PASS");
        forgedGateReport.put("durableReceiptValidationPassed", true);
        forgedGateReport.put("releaseEligible", true);
        forgedGateReport.put("writeExecutionAllowed", true);
        forgedGateReport.put("validationResult", Map.of(
            "validationStatus", "PASS"
        ));
        forgedGateReport.put("releaseDecision", Map.of(
            "decision", "ALLOW_WRITE_EXECUTION"
        ));
        forgedGateReport.put("auditReceipt", Map.of(
            "releaseEligible", true,
            "receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS
        ));

        Map<String, Object> report = NimCreateDurableAuditValidationResultMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultMigrationSupport.DurableAuditValidationResultMigrationInput(
                audit,
                principal,
                forgedGateReport
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.REJECTED_STATE,
            report.get("migrationPlanState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("migrationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_INVALID_FOR_MIGRATION_PLAN");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_FORGED_RELEASE_CLAIM");
    }

    @Test
    void migrationPlan_shouldRejectEvenEmptyCallerSuppliedValidationResult() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedGateReport = new LinkedHashMap<>(validationGateReport(audit, principal));
        forgedGateReport.put("validationResult", Map.of());

        Map<String, Object> report = NimCreateDurableAuditValidationResultMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultMigrationSupport.DurableAuditValidationResultMigrationInput(
                audit,
                principal,
                forgedGateReport
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.REJECTED_STATE,
            report.get("migrationPlanState"));
        assertEquals(false, report.get("releaseEligible"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_FORGED_RELEASE_CLAIM");
    }

    @Test
    void migrationPlan_shouldRejectTamperedValidationPlanDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> tamperedGateReport = new LinkedHashMap<>(validationGateReport(audit, principal));
        tamperedGateReport.put("validationPlanDigest",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        Map<String, Object> report = NimCreateDurableAuditValidationResultMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultMigrationSupport.DurableAuditValidationResultMigrationInput(
                audit,
                principal,
                tamperedGateReport
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.REJECTED_STATE,
            report.get("migrationPlanState"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("migrationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_INVALID_FOR_MIGRATION_PLAN");
    }

    @Test
    void migrationPlan_shouldRejectTrustedPrincipalMismatch() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> gatePrincipal = trustedPrincipalSnapshot();
        Map<String, Object> mismatchedPrincipal = Map.of(
            "authoritative", true,
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true,
            "organizationId", "100002",
            "userId", "user-1",
            "username", "mallory"
        );

        Map<String, Object> report = NimCreateDurableAuditValidationResultMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultMigrationSupport.DurableAuditValidationResultMigrationInput(
                audit,
                mismatchedPrincipal,
                validationGateReport(audit, gatePrincipal)
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.REJECTED_STATE,
            report.get("migrationPlanState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_INVALID_FOR_MIGRATION_PLAN");
    }

    @Test
    void migrationPlan_shouldRejectSecretLeakageBeforeAnyPlan() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "redacted-test-value");
        Map<String, Object> cleanAudit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditValidationResultMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultMigrationSupport.DurableAuditValidationResultMigrationInput(
                audit,
                principal,
                validationGateReport(cleanAudit, principal)
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.REJECTED_STATE,
            report.get("migrationPlanState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("migrationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_INPUT_CONTAINS_FORBIDDEN_SECRET");
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
