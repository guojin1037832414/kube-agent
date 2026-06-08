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
 * NIM durable audit validation result / release decision 对 probe binding 的迁移契约测试。
 *
 * <p>这些测试只证明未来 validation result / release decision migration 必须绑定 M5.21-68
 * bindingPlanDigest；当前不创建真实 result / decision，不修改状态机放行逻辑。</p>
 */
class NimCreateDurableAuditValidationResultProbeBindingMigrationSupportTest {

    @Test
    void migration_shouldBuildEnhancedPlanButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> bindingReport = probeBindingReport(audit, principal);
        Map<String, Object> migrationReport = validationResultMigrationReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                bindingReport,
                migrationReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.PLAN_NAME,
            report.get("durableAuditValidationResultProbeBindingMigrationPlan"));
        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.HOLD_STATE,
            report.get("migrationState"), report.toString());
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("enhancedMigrationPlanPrepared"));
        assertEquals(true, report.get("probeBindingRequiredBeforeValidationResult"));
        assertEquals(false, report.get("legacyMigrationReportAloneAllowed"));
        assertEquals(false, report.get("callerReleaseEvidenceAuthoritative"));
        assertSuccessStatesRemainFalse(report);
        assertEquals(bindingReport.get("bindingPlanDigest"), report.get("sourceProbeBindingPlanDigest"));
        assertEquals(bindingReport.get("sourceProbeResultContractDigest"),
            report.get("sourceProbeResultContractDigest"));
        assertEquals(migrationReport.get("migrationPlanDigest"), report.get("sourceMigrationPlanDigest"));
        assertEquals(migrationReport.get("sourceValidationPlanDigest"),
            report.get("sourceValidationPlanDigest"));
        assertTrue(report.get("enhancedMigrationPlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("enhancedMigrationPlan");
        assertEquals("SERVER_SIDE_VALIDATION_RESULT_AND_RELEASE_DECISION_REQUIRE_PROBE_BINDING",
            plan.get("migrationBoundary"));
        assertEquals(bindingReport.get("bindingPlanDigest"), plan.get("sourceProbeBindingPlanDigest"));
        assertEquals(migrationReport.get("migrationPlanDigest"), plan.get("sourceMigrationPlanDigest"));

        @SuppressWarnings("unchecked")
        Map<String, Object> requirement = (Map<String, Object>) plan.get("probeBindingRequirement");
        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.BINDING_NAME,
            requirement.get("requiredReportName"));
        assertEquals(true, requirement.get("mustBindProbeResultBindingDigest"));
        assertEquals(false, requirement.get("probeBindingReportCanPassNow"));
        assertEquals(false, requirement.get("fallbackToValidationGateOnlyAllowed"));
        assertEquals(false, requirement.get("fallbackToMigrationPlanOnlyAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> validationResult =
            (Map<String, Object>) plan.get("enhancedValidationResultContract");
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            validationResult.get("type"));
        assertEquals(bindingReport.get("bindingPlanDigest"),
            validationResult.get("sourceProbeBindingPlanDigest"));
        assertEquals(true, validationResult.get("mustBindProbeResultBindingDigest"));
        assertEquals(true, validationResult.get("mustBindProbeResultContractDigest"));
        assertEquals(true, validationResult.get("mustBindPreWriteDurableAckDigest"));
        assertEquals(true, validationResult.get("mustBindPostWriteDurableAckDigest"));
        assertEquals(false, validationResult.get("instanceAllowedNow"));
        @SuppressWarnings("unchecked")
        Map<String, Object> validationTemplate =
            (Map<String, Object>) validationResult.get("currentTemplate");
        assertEquals(false, validationTemplate.get("probeBindingDigestVerified"));
        assertEquals(false, validationTemplate.get("validationPassed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> releaseDecision =
            (Map<String, Object>) plan.get("enhancedReleaseDecisionContract");
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            releaseDecision.get("type"));
        assertEquals(migrationReport.get("sourceReceiptSchemaDigest"),
            releaseDecision.get("sourceReceiptSchemaDigest"));
        assertEquals(migrationReport.get("sourceValidationPlanDigest"),
            releaseDecision.get("sourceValidationPlanDigest"));
        assertEquals(true, releaseDecision.get("mustBindProbeResultBindingDigest"));
        assertEquals(true, releaseDecision.get("mustBindProbeResultContractDigest"));
        assertEquals(true, releaseDecision.get("mustBindValidationResultDigest"));
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", releaseDecision.get("currentDecision"));
        @SuppressWarnings("unchecked")
        Map<String, Object> releaseTemplate =
            (Map<String, Object>) releaseDecision.get("currentTemplate");
        assertEquals(false, releaseTemplate.get("releaseEligible"));
        assertEquals(false, releaseTemplate.get("writeExecutionAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sequence = (List<Map<String, Object>>) plan.get("migrationSequencePatch");
        assertEquals(3, sequence.size());
        assertEquals("bind-probe-result-binding-plan", sequence.get(0).get("id"));
        assertEquals("reject-validation-plan-only-migration", sequence.get(1).get("id"));
        assertEquals("bind-release-decision-to-enhanced-migration", sequence.get(2).get("id"));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) plan.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToValidationGateOnlyAllowed"));
        assertEquals(false, failure.get("fallbackToSchemaOnlyAllowed"));
        assertEquals(false, failure.get("fallbackToMigrationPlanOnlyAllowed"));
        assertEquals(false, failure.get("fallbackToCallerReleaseEvidenceAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void migration_shouldRejectMissingProbeBindingReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                Map.of(),
                validationResultMigrationReport(audit, principal),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.REJECTED_STATE,
            report.get("migrationState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("enhancedMigrationPlanPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void migration_shouldRejectMissingValidationResultMigrationReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                probeBindingReport(audit, principal),
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.REJECTED_STATE,
            report.get("migrationState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("enhancedMigrationPlanPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "VALIDATION_RESULT_MIGRATION_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void migration_shouldRejectTamperedProbeBindingDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> tamperedBinding = new LinkedHashMap<>(probeBindingReport(audit, principal));
        tamperedBinding.put("bindingPlanDigest", "a".repeat(64));

        Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                tamperedBinding,
                validationResultMigrationReport(audit, principal),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.REJECTED_STATE,
            report.get("migrationState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_REPORT_INVALID_FOR_MIGRATION");
    }

    @Test
    void migration_shouldRejectCrossReportDigestMismatch() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> tamperedMigration = new LinkedHashMap<>(validationResultMigrationReport(audit, principal));
        tamperedMigration.put("sourceValidationPlanDigest", "b".repeat(64));

        Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                probeBindingReport(audit, principal),
                tamperedMigration,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.REJECTED_STATE,
            report.get("migrationState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "VALIDATION_RESULT_MIGRATION_REPORT_INVALID_FOR_PROBE_BINDING_MIGRATION");
        assertHasBlocker(blockers,
            "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_DIGEST_CHAIN_MISMATCH");
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanExtraFailureOrShortcutLists() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        for (ListMutation mutation : List.of(
            new ListMutation("failureContract", "failureStatuses", "FUTURE_RELEASE_SIGNER_NOT_READY"),
            new ListMutation(null, "forbiddenShortcuts", "accepting migrationPlanDigest as release credential")
        )) {
            Map<String, Object> forgedMigrationReport = withDigestConsistentExtraMigrationPlanListField(
                validationResultMigrationReport(audit, principal),
                mutation.contractKey(),
                mutation.listKey(),
                mutation.forgedValue()
            );

            Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
                new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                    .ValidationResultProbeBindingMigrationInput(
                    audit,
                    principal,
                    probeBindingReport(audit, principal),
                    forgedMigrationReport,
                    Map.of()
                )
            );

            String scenario = mutation.contractKey() == null
                ? mutation.listKey()
                : mutation.contractKey() + "." + mutation.listKey();
            assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.REJECTED_STATE,
                report.get("migrationState"), scenario);
            assertEquals(false, report.get("inputAccepted"), scenario);
            assertSuccessStatesRemainFalse(report);
            @SuppressWarnings("unchecked")
            Map<String, Object> plan = (Map<String, Object>) report.get("enhancedMigrationPlan");
            assertTrue(plan.isEmpty(), scenario);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
            assertHasBlocker(blockers,
                "VALIDATION_RESULT_MIGRATION_REPORT_INVALID_FOR_PROBE_BINDING_MIGRATION");
        }
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanTopLevelExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> migrationPlan.put("futureCompatibilityAccepted", false)
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanIdentityExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> objectMap(migrationPlan.get("trustedIdentityBinding"))
                .put("callerIdentityCanSatisfyMigration", false)
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanSequenceDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> objectList(migrationPlan.get("migrationSequence")).add(Map.of(
                "id", "skip-server-issued-release-decision",
                "requirement", "Never accept migrationPlanDigest as write release evidence",
                "futureOnly", true,
                "sideEffectAllowedNow", false,
                "failClosed", true
            ))
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanValidationResultContractExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> objectMap(migrationPlan.get("validationResultContract"))
                .put("callerValidationResultCanSatisfyContract", false)
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanValidationResultTemplateExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> objectMap(objectMap(migrationPlan.get("validationResultContract"))
                .get("currentTemplate")).put("shadowValidationFlag", false)
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanReleaseDecisionContractExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> objectMap(migrationPlan.get("releaseDecisionContract"))
                .put("callerReleaseDecisionCanSatisfyContract", false)
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanReleaseDecisionTemplateExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> objectMap(objectMap(migrationPlan.get("releaseDecisionContract"))
                .get("currentTemplate")).put("shadowReleaseFlag", false)
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanLegacyPolicyExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> objectMap(migrationPlan.get("legacyCompatibilityPolicy"))
                .put("legacyCompatibilityAutoAccepted", false)
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanReleaseCredentialRulesExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> objectMap(migrationPlan.get("releaseCredentialRules"))
                .put("migrationDigestCanBecomeCredentialLater", false)
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentMigrationPlanFailureContractExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedMigrationReport = withDigestConsistentMigrationPlanMutation(
            validationResultMigrationReport(audit, principal),
            migrationPlan -> objectMap(migrationPlan.get("failureContract"))
                .put("fallbackToUnknownFutureGateAllowed", false)
        );

        assertRejectsDigestConsistentMigrationPlanDrift(audit, principal, forgedMigrationReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentProbeBindingPlanTopLevelExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedBindingReport = withDigestConsistentBindingPlanMutation(
            probeBindingReport(audit, principal),
            bindingPlan -> bindingPlan.put("futureCompatibilityAccepted", false)
        );

        assertRejectsDigestConsistentProbeBindingPlanDrift(audit, principal, forgedBindingReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentProbeBindingPlanIdentityExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedBindingReport = withDigestConsistentBindingPlanMutation(
            probeBindingReport(audit, principal),
            bindingPlan -> objectMap(bindingPlan.get("trustedIdentityBinding"))
                .put("callerIdentityCanSatisfyBinding", false)
        );

        assertRejectsDigestConsistentProbeBindingPlanDrift(audit, principal, forgedBindingReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentProbeBindingPlanEvidenceExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedBindingReport = withDigestConsistentBindingPlanMutation(
            probeBindingReport(audit, principal),
            bindingPlan -> objectMap(bindingPlan.get("requiredBindingEvidence"))
                .put("legacyReceiptCanSatisfyBinding", false)
        );

        assertRejectsDigestConsistentProbeBindingPlanDrift(audit, principal, forgedBindingReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentProbeBindingPlanNestedEvidenceExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedBindingReport = withDigestConsistentBindingPlanMutation(
            probeBindingReport(audit, principal),
            bindingPlan -> objectMap(objectMap(bindingPlan.get("requiredBindingEvidence"))
                .get("storageProbeResultContract"))
                .put("probeResultDigestAloneCanSatisfyBinding", false)
        );

        assertRejectsDigestConsistentProbeBindingPlanDrift(audit, principal, forgedBindingReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentProbeBindingPlanTemplateExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedBindingReport = withDigestConsistentBindingPlanMutation(
            probeBindingReport(audit, principal),
            bindingPlan -> objectMap(bindingPlan.get("currentDecisionTemplate"))
                .put("probeBindingPreviewAvailable", false)
        );

        assertRejectsDigestConsistentProbeBindingPlanDrift(audit, principal, forgedBindingReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentProbeBindingPlanFailureContractExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedBindingReport = withDigestConsistentBindingPlanMutation(
            probeBindingReport(audit, principal),
            bindingPlan -> objectMap(bindingPlan.get("failureContract"))
                .put("fallbackToDigestConsistentBindingPlanAllowed", false)
        );

        assertRejectsDigestConsistentProbeBindingPlanDrift(audit, principal, forgedBindingReport);
    }

    @Test
    void migration_shouldRejectDigestConsistentProbeBindingPlanForbiddenShortcutsListDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedBindingReport = withDigestConsistentBindingPlanMutation(
            probeBindingReport(audit, principal),
            bindingPlan -> objectList(bindingPlan.get("forbiddenShortcuts"))
                .add("accepting bindingPlanDigest without exact binding plan maps")
        );

        assertRejectsDigestConsistentProbeBindingPlanDrift(audit, principal, forgedBindingReport);
    }

    @Test
    void migration_shouldRejectForgedProbeBindingSuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedBinding = new LinkedHashMap<>(probeBindingReport(audit, principal));
        forgedBinding.put("storageProbeResultBoundForValidation", true);
        forgedBinding.put("serverIssuedProbeResultAccepted", true);
        forgedBinding.put("validationCanRunNow", true);

        Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                forgedBinding,
                validationResultMigrationReport(audit, principal),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.REJECTED_STATE,
            report.get("migrationState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_REPORT_INVALID_FOR_MIGRATION");
        assertHasBlocker(blockers,
            "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_FORGED_RELEASE_CLAIM");
    }

    @Test
    void migration_shouldRejectCallerReleaseEvidenceAndSecretLeakage() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        String injectedSecret = "redacted-test-value";
        Map<String, Object> callerEvidence = Map.of(
            "validationResult", Map.of("validationStatus", "PASS"),
            "releaseDecision", Map.of("decision", "ALLOW_WRITE_EXECUTION"),
            "nested", List.of(Map.of("Authorization", injectedSecret))
        );

        Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                probeBindingReport(audit, principal),
                validationResultMigrationReport(audit, principal),
                callerEvidence
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.REJECTED_STATE,
            report.get("migrationState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("enhancedMigrationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CALLER_RELEASE_EVIDENCE_NOT_AUTHORITATIVE_FOR_PROBE_BINDING_MIGRATION");
        assertHasBlocker(blockers,
            "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_FORGED_RELEASE_CLAIM");
        assertHasBlocker(blockers,
            "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void migration_shouldNotDependOnRealNetworkStorageSpringOrWriters() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/atlas/tool/impl/"
                + "NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.java"
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
        assertFalse(source.matches("(?s).*\\.save\\s*\\(.*"));
        assertFalse(source.matches("(?s).*\\.insert\\s*\\(.*"));
        assertFalse(source.matches("(?s).*saveLog\\s*\\(.*"));
        assertFalse(source.contains("result.put(\"writeExecutionAllowed\", true)"));
        assertFalse(source.contains("result.put(\"releaseEligible\", true)"));
        assertFalse(source.contains("result.put(\"realValidationResultCreated\", true)"));
    }

    private void assertSuccessStatesRemainFalse(Map<String, Object> report) {
        assertEquals(false, report.get("probeBindingBoundToValidationResultMigration"));
        assertEquals(false, report.get("realValidationResultCreated"));
        assertEquals(false, report.get("realReleaseDecisionCreated"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeResultBoundForValidation"));
        assertEquals(false, report.get("serverIssuedProbeResultAccepted"));
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
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(false, report.get("legacyAuditReceiptReleaseFlagTrusted"));
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

    private Map<String, Object> withDigestConsistentBindingPlanMutation(Map<String, Object> bindingReport,
                                                                       Consumer<Map<String, Object>> mutator) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(bindingReport);
        Map<String, Object> bindingPlan = objectMap(deepMutableCopy(forgedReport.get("bindingPlan")));
        mutator.accept(bindingPlan);
        forgedReport.put("bindingPlan", bindingPlan);
        forgedReport.put("bindingPlanDigest", sha256(bindingPlan));
        return forgedReport;
    }

    private Map<String, Object> withDigestConsistentMigrationPlanMutation(Map<String, Object> migrationReport,
                                                                         Consumer<Map<String, Object>> mutator) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(migrationReport);
        Map<String, Object> migrationPlan = objectMap(deepMutableCopy(forgedReport.get("migrationPlan")));
        mutator.accept(migrationPlan);
        forgedReport.put("migrationPlan", migrationPlan);
        forgedReport.put("migrationPlanDigest", sha256(migrationPlan));
        return forgedReport;
    }

    private void assertRejectsDigestConsistentMigrationPlanDrift(Map<String, Object> audit,
                                                                Map<String, Object> principal,
                                                                Map<String, Object> migrationReport) {
        Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                probeBindingReport(audit, principal),
                migrationReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.REJECTED_STATE,
            report.get("migrationState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("enhancedMigrationPlanPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("enhancedMigrationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "VALIDATION_RESULT_MIGRATION_REPORT_INVALID_FOR_PROBE_BINDING_MIGRATION");
    }

    private void assertRejectsDigestConsistentProbeBindingPlanDrift(Map<String, Object> audit,
                                                                   Map<String, Object> principal,
                                                                   Map<String, Object> bindingReport) {
        Map<String, Object> report = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.plan(
            new NimCreateDurableAuditValidationResultProbeBindingMigrationSupport
                .ValidationResultProbeBindingMigrationInput(
                audit,
                principal,
                bindingReport,
                validationResultMigrationReport(audit, principal),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.REJECTED_STATE,
            report.get("migrationState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("enhancedMigrationPlanPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("enhancedMigrationPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_REPORT_INVALID_FOR_MIGRATION");
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

    private Map<String, Object> withDigestConsistentExtraMigrationPlanListField(Map<String, Object> migrationReport,
                                                                               String contractKey,
                                                                               String listKey,
                                                                               String forgedValue) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(migrationReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> migrationPlan =
            new LinkedHashMap<>((Map<String, Object>) forgedReport.get("migrationPlan"));
        if (contractKey == null) {
            @SuppressWarnings("unchecked")
            List<String> list = new ArrayList<>((List<String>) migrationPlan.get(listKey));
            list.add(forgedValue);
            migrationPlan.put(listKey, list);
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> contract = new LinkedHashMap<>((Map<String, Object>) migrationPlan.get(contractKey));
            @SuppressWarnings("unchecked")
            List<String> list = new ArrayList<>((List<String>) contract.get(listKey));
            list.add(forgedValue);
            contract.put(listKey, list);
            migrationPlan.put(contractKey, contract);
        }
        forgedReport.put("migrationPlan", migrationPlan);
        forgedReport.put("migrationPlanDigest", sha256(migrationPlan));
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

    private record ListMutation(String contractKey, String listKey, String forgedValue) {
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
