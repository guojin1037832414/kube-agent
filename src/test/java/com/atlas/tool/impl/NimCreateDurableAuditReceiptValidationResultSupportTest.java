package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM durable audit receipt validation result 契约测试。
 *
 * <p>这些测试证明 future validation result 必须绑定 M5.21-69 enhanced migration digest、
 * M5.21-68 probe binding digest 与 typed receipt/ack 证据；当前不创建真实 result，不产生 PASS，
 * 不允许 release 或 write execution。</p>
 */
class NimCreateDurableAuditReceiptValidationResultSupportTest {

    @Test
    void validationResult_shouldBuildContractButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> migrationReport = probeBindingMigrationReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                migrationReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.RESULT_CONTRACT_NAME,
            report.get("durableAuditReceiptValidationResultContract"));
        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.HOLD_STATE,
            report.get("validationResultState"), report.toString());
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(false, report.get("springBeanRegistered"));
        assertEquals(false, report.get("httpClientBound"));
        assertEquals(false, report.get("storageClientBound"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("validationResultContractPrepared"));
        assertEquals(true, report.get("serverIssuedValidationResultRequired"));
        assertEquals(false, report.get("callerValidationEvidenceAuthoritative"));
        assertEquals(false, report.get("legacyMigrationReportAloneAllowed"));
        assertSuccessStatesRemainFalse(report);
        assertEquals(migrationReport.get("enhancedMigrationPlanDigest"),
            report.get("sourceEnhancedMigrationPlanDigest"));
        assertEquals(migrationReport.get("sourceProbeBindingPlanDigest"),
            report.get("sourceProbeBindingPlanDigest"));
        assertEquals(migrationReport.get("sourceProbeResultContractDigest"),
            report.get("sourceProbeResultContractDigest"));
        assertEquals(migrationReport.get("sourceProbeExecutorPlanDigest"),
            report.get("sourceProbeExecutorPlanDigest"));
        assertEquals(migrationReport.get("sourceMigrationPlanDigest"),
            report.get("sourceMigrationPlanDigest"));
        assertEquals(migrationReport.get("sourceReceiptSchemaDigest"),
            report.get("sourceReceiptSchemaDigest"));
        assertEquals(migrationReport.get("sourceValidationPlanDigest"),
            report.get("sourceValidationPlanDigest"));
        assertEquals(migrationReport.get("sourceInterfaceSpecDigest"),
            report.get("sourceInterfaceSpecDigest"));
        assertEquals(migrationReport.get("sourceBoundaryPlanDigest"),
            report.get("sourceBoundaryPlanDigest"));
        assertEquals(migrationReport.get("sourceWriterPlanDigest"),
            report.get("sourceWriterPlanDigest"));
        assertEquals(migrationReport.get("sourceAvailabilityPlanDigest"),
            report.get("sourceAvailabilityPlanDigest"));
        assertEquals(migrationReport.get("trustedPrincipalDigest"), report.get("trustedPrincipalDigest"));
        assertEquals(audit.get("organizationId"), report.get("sourceOrganizationId"));
        assertEquals(audit.get("userId"), report.get("sourceUserId"));
        assertEquals(principal.get("username"), report.get("sourceUsername"));
        assertEquals(NimCreateAuditWriterSupport.DIGEST_ALGORITHM,
            report.get("validationResultContractDigestAlgorithm"));
        assertTrue(report.get("validationResultContractDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("validationResultContract");
        assertEquals(
            NimCreateDurableAuditReceiptValidationResultSupport.validationResultContractFromReport(report),
            contract
        );
        assertEquals("SERVER_ISSUED_DURABLE_RECEIPT_VALIDATION_RESULT_REQUIRED",
            contract.get("contractBoundary"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            contract.get("type"));
        assertEquals(false, contract.get("instanceAllowedNow"));
        assertEquals(true, contract.get("serverIssuedRequired"));
        assertEquals(false, contract.get("callerProvidedValidationResultAllowed"));
        assertEquals(migrationReport.get("enhancedMigrationPlanDigest"),
            contract.get("sourceEnhancedMigrationPlanDigest"));
        assertEquals(migrationReport.get("sourceProbeExecutorPlanDigest"),
            contract.get("sourceProbeExecutorPlanDigest"));
        assertEquals(report.get("trustedPrincipalDigest"), contract.get("trustedPrincipalDigest"));
        @SuppressWarnings("unchecked")
        List<String> requiredFutureFields = (List<String>) contract.get("requiredFutureEvidenceDigestFields");
        assertTrue(requiredFutureFields.contains("storageProbeReceiptDigest"));
        assertTrue(requiredFutureFields.contains("preWriteDurableAckDigest"));
        assertTrue(requiredFutureFields.contains("postWriteDurableAckDigest"));
        assertTrue(requiredFutureFields.contains("durableReceiptDigest"));
        assertTrue(requiredFutureFields.contains("sourceProbeExecutorPlanDigest"));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) contract.get("evidenceBinding");
        assertEquals(migrationReport.get("enhancedMigrationPlanDigest"),
            evidence.get("sourceEnhancedMigrationPlanDigest"));
        assertEquals(migrationReport.get("sourceProbeBindingPlanDigest"),
            evidence.get("sourceProbeBindingPlanDigest"));
        assertEquals(migrationReport.get("sourceProbeResultContractDigest"),
            evidence.get("sourceProbeResultContractDigest"));
        assertEquals(migrationReport.get("sourceProbeExecutorPlanDigest"),
            evidence.get("sourceProbeExecutorPlanDigest"));
        assertEquals(migrationReport.get("sourceMigrationPlanDigest"),
            evidence.get("sourceMigrationPlanDigest"));
        assertEquals(migrationReport.get("sourceReceiptSchemaDigest"),
            evidence.get("sourceReceiptSchemaDigest"));
        assertEquals(migrationReport.get("sourceValidationPlanDigest"),
            evidence.get("sourceValidationPlanDigest"));
        assertEquals(migrationReport.get("sourceInterfaceSpecDigest"),
            evidence.get("sourceInterfaceSpecDigest"));
        assertEquals(migrationReport.get("sourceBoundaryPlanDigest"),
            evidence.get("sourceBoundaryPlanDigest"));
        assertEquals(migrationReport.get("sourceWriterPlanDigest"),
            evidence.get("sourceWriterPlanDigest"));
        assertEquals(migrationReport.get("sourceAvailabilityPlanDigest"),
            evidence.get("sourceAvailabilityPlanDigest"));
        assertEquals(migrationReport.get("trustedPrincipalDigest"),
            evidence.get("trustedPrincipalDigest"));
        assertEquals(true, evidence.get("mustBindEnhancedMigrationDigest"));
        assertEquals(true, evidence.get("mustBindProbeResultBindingDigest"));
        assertEquals(true, evidence.get("mustBindProbeResultContractDigest"));
        assertEquals(true, evidence.get("mustBindStorageProbeReceiptDigest"));
        assertEquals(true, evidence.get("mustBindPreWriteDurableAckDigest"));
        assertEquals(true, evidence.get("mustBindPostWriteDurableAckDigest"));
        assertEquals(true, evidence.get("mustBindDurableReceiptDigest"));
        assertEquals(true, evidence.get("mustBindTrustedPrincipalDigest"));
        assertEquals(true, evidence.get("mustBeServerIssued"));

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) contract.get("currentTemplate");
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", template.get("validationStatus"));
        assertEquals(false, template.get("enhancedMigrationDigestVerified"));
        assertEquals(false, template.get("probeBindingDigestVerified"));
        assertEquals(false, template.get("probeResultContractDigestVerified"));
        assertEquals(false, template.get("storageProbeReceiptValidated"));
        assertEquals(false, template.get("preWriteDurableAckValidated"));
        assertEquals(false, template.get("postWriteDurableAckValidated"));
        assertEquals(false, template.get("durableReceiptValidated"));
        assertEquals(false, template.get("validationPassed"));
        assertEquals(false, template.get("releaseEligible"));
        assertEquals(false, template.get("writeExecutionAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> prerequisites = (Map<String, Object>) contract.get("passPrerequisites");
        assertEquals(false, prerequisites.get("currentContractSatisfiesPrerequisites"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) contract.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToMigrationPlanOnlyAllowed"));
        assertEquals(false, failure.get("fallbackToProbeBindingPlanAllowed"));
        assertEquals(false, failure.get("fallbackToSchemaOnlyAllowed"));
        assertEquals(false, failure.get("fallbackToCallerValidationResultAllowed"));
        assertEquals(false, failure.get("fallbackToLegacyAuditReceiptFlagAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void validationResult_shouldRejectMissingEnhancedMigrationReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.REJECTED_STATE,
            report.get("validationResultState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("validationResultContractPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("validationResultContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void validationResult_shouldRejectTamperedEnhancedMigrationDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> tamperedMigration = new LinkedHashMap<>(probeBindingMigrationReport(audit, principal));
        tamperedMigration.put("enhancedMigrationPlanDigest", "a".repeat(64));

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                tamperedMigration,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.REJECTED_STATE,
            report.get("validationResultState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_INVALID_FOR_RESULT");
    }

    @Test
    void validationResult_shouldRejectLegacyMigrationReportAlone() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                validationResultMigrationReport(audit, principal),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.REJECTED_STATE,
            report.get("validationResultState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("validationResultContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_INVALID_FOR_RESULT");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void validationResult_shouldRejectProbeExecutorDigestTampering() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> tamperedMigration = new LinkedHashMap<>(probeBindingMigrationReport(audit, principal));
        tamperedMigration.put("sourceProbeExecutorPlanDigest", "b".repeat(64));

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                tamperedMigration,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.REJECTED_STATE,
            report.get("validationResultState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_INVALID_FOR_RESULT");
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedMigrationPlanTopLevelExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> plan.put("futureCompatibilityAccepted", false)
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedMigrationPlanIdentityExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectMap(plan.get("trustedIdentityBinding"))
                .put("callerIdentityCanSatisfyEnhancedMigration", false)
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedMigrationPlanProbeRequirementExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectMap(plan.get("probeBindingRequirement"))
                .put("bindingDigestOnlyCanSatisfyRequirement", false)
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedValidationResultContractExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectMap(plan.get("enhancedValidationResultContract"))
                .put("callerValidationResultCanSatisfyContract", false)
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedValidationResultTemplateExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectMap(objectMap(plan.get("enhancedValidationResultContract"))
                .get("currentTemplate")).put("shadowValidationFlag", false)
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedReleaseDecisionContractExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectMap(plan.get("enhancedReleaseDecisionContract"))
                .put("callerReleaseDecisionCanSatisfyContract", false)
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedReleaseDecisionTemplateExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectMap(objectMap(plan.get("enhancedReleaseDecisionContract"))
                .get("currentTemplate")).put("shadowReleaseFlag", false)
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedMigrationSequenceDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectList(plan.get("migrationSequencePatch")).add(Map.of(
                "id", "skip-server-issued-validation-result",
                "requirement", "Never accept enhancedMigrationPlanDigest as validation PASS",
                "futureOnly", true,
                "sideEffectAllowedNow", false,
                "failClosed", true
            ))
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedMigrationCurrentDecisionTemplateExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectMap(plan.get("currentDecisionTemplate"))
                .put("validationResultPreviewAccepted", false)
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedMigrationFailureContractExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectMap(plan.get("failureContract"))
                .put("fallbackToDigestConsistentEnhancedPlanAllowed", false)
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedMigrationFailureStatusListDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectList(objectMap(plan.get("failureContract")).get("failureStatuses"))
                .add("FUTURE_VALIDATION_RESULT_SIGNER_NOT_READY")
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectDigestConsistentEnhancedMigrationForbiddenShortcutsListDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = withDigestConsistentEnhancedMigrationPlanMutation(
            probeBindingMigrationReport(audit, principal),
            plan -> objectList(plan.get("forbiddenShortcuts"))
                .add("accepting enhancedMigrationPlanDigest as validation result")
        );

        assertRejectsDigestConsistentEnhancedMigrationPlanDrift(audit, principal, forgedMigration);
    }

    @Test
    void validationResult_shouldRejectUpstreamRealStorageTouchedClaim() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = new LinkedHashMap<>(probeBindingMigrationReport(audit, principal));
        forgedMigration.put("realStorageTouched", true);

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                forgedMigration,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.REJECTED_STATE,
            report.get("validationResultState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_INVALID_FOR_RESULT");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_FORGED_PASS_CLAIM");
    }

    @Test
    void validationResult_shouldRejectForgedMigrationSuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigration = new LinkedHashMap<>(probeBindingMigrationReport(audit, principal));
        forgedMigration.put("realValidationResultCreated", true);
        forgedMigration.put("serverIssuedValidationResultAccepted", true);
        forgedMigration.put("durableReceiptValidationPassed", true);
        forgedMigration.put("validationStatus", "PASS");
        forgedMigration.put("releaseDecisionAccepted", true);
        forgedMigration.put("releaseCredentialIssued", true);
        forgedMigration.put("releaseEligible", true);
        forgedMigration.put("writeExecutionAllowed", true);
        forgedMigration.put("releaseDecision", Map.of("decision", "ALLOW_WRITE_EXECUTION"));

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                forgedMigration,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.REJECTED_STATE,
            report.get("validationResultState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_INVALID_FOR_RESULT");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_FORGED_PASS_CLAIM");
    }

    @Test
    void validationResult_shouldRejectCallerValidationEvidenceAndSecretLeakage() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        String injectedSecret = "redacted-test-value";
        Map<String, Object> callerEvidence = Map.of(
            "validationResult", Map.of("validationStatus", "PASS"),
            "releaseDecision", Map.of("decision", "ALLOW_WRITE_EXECUTION"),
            "legacyAuditReceipt", Map.of("releaseEligible", true),
            "nested", List.of(Map.of("Authorization", injectedSecret))
        );

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                probeBindingMigrationReport(audit, principal),
                callerEvidence
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.REJECTED_STATE,
            report.get("validationResultState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("validationResultContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "CALLER_VALIDATION_EVIDENCE_NOT_AUTHORITATIVE");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_FORGED_PASS_CLAIM");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void validationResult_shouldRejectSecretLeakageFromEnhancedMigrationReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        String injectedSecret = "redacted-report-secret";
        Map<String, Object> migrationReport = new LinkedHashMap<>(probeBindingMigrationReport(audit, principal));
        migrationReport.put("diagnostic", Map.of("token", injectedSecret));

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                migrationReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.REJECTED_STATE,
            report.get("validationResultState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void validationResult_shouldNotDependOnRealNetworkStorageSpringOrWriters() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/atlas/tool/impl/"
                + "NimCreateDurableAuditReceiptValidationResultSupport.java"
        ));

        assertFalse(source.contains("@Component"));
        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("@Controller"));
        assertFalse(source.contains("@RestController"));
        assertFalse(source.contains("@Autowired"));
        assertFalse(source.contains("@Bean"));
        assertFalse(source.contains("KubeManagerHttpClient"));
        assertFalse(source.contains("RestClient"));
        assertFalse(source.contains("RestTemplate"));
        assertFalse(source.contains("WebClient"));
        assertFalse(source.contains("HttpClient"));
        assertFalse(source.contains("java.net"));
        assertFalse(source.contains("ElasticsearchTemplate"));
        assertFalse(source.contains("ISysLogService"));
        assertFalse(source.contains("ToolRegistry"));
        assertFalse(source.contains("ToolRegistration"));
        assertFalse(source.contains("POST /api/{orgId}/deployment"));
        assertFalse(source.contains("8100"));
        assertFalse(source.matches("(?s).*\\.save\\s*\\(.*"));
        assertFalse(source.matches("(?s).*\\.insert\\s*\\(.*"));
        assertFalse(source.matches("(?s).*saveLog\\s*\\(.*"));
        assertFalse(source.contains("result.put(\"writeExecutionAllowed\", true)"));
        assertFalse(source.contains("result.put(\"releaseEligible\", true)"));
        assertFalse(source.contains("result.put(\"realValidationResultCreated\", true)"));
        assertFalse(source.contains("result.put(\"validationPassed\", true)"));
    }

    private void assertSuccessStatesRemainFalse(Map<String, Object> report) {
        assertEquals(false, report.get("realValidatorCreated"));
        assertEquals(false, report.get("realValidationResultCreated"));
        assertEquals(false, report.get("serverIssuedValidationResultAccepted"));
        assertEquals(false, report.get("enhancedMigrationDigestVerified"));
        assertEquals(false, report.get("probeBindingDigestVerified"));
        assertEquals(false, report.get("probeResultContractDigestVerified"));
        assertEquals(false, report.get("storageProbeResultBoundForValidation"));
        assertEquals(false, report.get("storageProbeReceiptValidated"));
        assertEquals(false, report.get("preWriteDurableAckValidated"));
        assertEquals(false, report.get("postWriteDurableAckValidated"));
        assertEquals(false, report.get("digestChainValidated"));
        assertEquals(false, report.get("trustedPrincipalValidated"));
        assertEquals(false, report.get("durableReceiptValidated"));
        assertEquals(false, report.get("durableReceiptValidationPassed"));
        assertEquals(false, report.get("durableReceiptAccepted"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", report.get("validationStatus"));
        assertEquals(false, report.get("validationPassed"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("releaseDecisionAccepted"));
        assertEquals(false, report.get("releaseCredentialIssued"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(false, report.get("legacyAuditReceiptReleaseFlagTrusted"));
    }

    private Map<String, Object> probeBindingMigrationReport(Map<String, Object> audit,
                                                            Map<String, Object> principal) {
        return NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                probeBindingReport(audit, principal),
                validationResultMigrationReport(audit, principal),
                Map.of()
            )
        );
    }

    private Map<String, Object> probeBindingReport(Map<String, Object> audit,
                                                   Map<String, Object> principal) {
        return NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.plan(
            new NimCreateDurableAuditReceiptValidationProbeResultBindingSupport
                .ReceiptValidationProbeResultBindingInput(
                audit,
                principal,
                storageProbeResultReport(audit, principal),
                validationGateReport(audit, principal),
                Map.of()
            )
        );
    }

    private Map<String, Object> validationResultMigrationReport(Map<String, Object> audit,
                                                                Map<String, Object> principal) {
        return NimCreateDurableAuditValidationResultMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultMigrationSupport.DurableAuditValidationResultMigrationInput(
                audit,
                principal,
                validationGateReport(audit, principal)
            )
        );
    }

    private Map<String, Object> storageProbeResultReport(Map<String, Object> audit,
                                                         Map<String, Object> principal) {
        return NimCreateDurableAuditStorageProbeResultSupport.plan(
            new NimCreateDurableAuditStorageProbeResultSupport.StorageProbeResultInput(
                audit,
                principal,
                probeExecutorReport(audit, principal),
                receiptSchemaReport(audit, principal),
                Map.of()
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

    private Map<String, Object> probeExecutorReport(Map<String, Object> audit,
                                                    Map<String, Object> principal) {
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
        Map<String, Object> boundaryReport = writerBoundaryReport(
            audit,
            principal,
            writerPlanReport,
            availabilityGateReport
        );
        return NimCreateDurableAuditStorageProbeExecutorSupport.plan(
            new NimCreateDurableAuditStorageProbeExecutorSupport.StorageProbeExecutorInput(
                audit,
                principal,
                availabilityGateReport,
                boundaryReport,
                Map.of()
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
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
        return NimCreateDurableAuditWriterInterfaceSpecSupport.plan(
            new NimCreateDurableAuditWriterInterfaceSpecSupport.DurableAuditWriterInterfaceSpecInput(
                audit,
                principal,
                writerBoundaryReport(audit, principal, writerPlanReport, availabilityGateReport)
            )
        );
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

    private Map<String, Object> withDigestConsistentEnhancedMigrationPlanMutation(
        Map<String, Object> migrationReport,
        Consumer<Map<String, Object>> mutator) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(migrationReport);
        Map<String, Object> enhancedPlan = objectMap(deepMutableCopy(forgedReport.get("enhancedMigrationPlan")));
        mutator.accept(enhancedPlan);
        forgedReport.put("enhancedMigrationPlan", enhancedPlan);
        forgedReport.put("enhancedMigrationPlanDigest", sha256(enhancedPlan));
        return forgedReport;
    }

    private void assertRejectsDigestConsistentEnhancedMigrationPlanDrift(Map<String, Object> audit,
                                                                        Map<String, Object> principal,
                                                                        Map<String, Object> migrationReport) {
        Map<String, Object> report = NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                migrationReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationResultSupport.REJECTED_STATE,
            report.get("validationResultState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("validationResultContractPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("validationResultContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_INVALID_FOR_RESULT");
    }

    private Object deepMutableCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), deepMutableCopy(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(deepMutableCopy(item));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> objectList(Object value) {
        return (List<Object>) value;
    }

    private String sha256(Map<String, Object> value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", ex);
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
