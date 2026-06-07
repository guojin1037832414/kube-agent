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
 * NIM 状态机 release decision gate report 必需项契约测试。
 *
 * <p>这些用例只验证未来 {@code NimCreateStateMachineSupport} 接入 release decision gate report 前的 fail-closed
 * 要求；不修改真实状态机放行逻辑，不创建真实 release decision，也不执行 HTTP 或存储 I/O。</p>
 */
class NimCreateStateMachineReleaseDecisionRequirementSupportTest {

    @Test
    void stateMachineRequirement_shouldBuildPlanButRemainHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> gateReport = releaseDecisionGateReport(audit, principal);

        Map<String, Object> report = NimCreateStateMachineReleaseDecisionRequirementSupport.plan(
            new NimCreateStateMachineReleaseDecisionRequirementSupport.StateMachineReleaseDecisionRequirementInput(
                audit,
                principal,
                gateReport
            )
        );

        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.REQUIREMENT_NAME,
            report.get("stateMachineReleaseDecisionReportRequirement"));
        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.HOLD_STATE,
            report.get("requirementState"));
        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.TARGET_STATE_MACHINE,
            report.get("targetStateMachine"));
        assertEquals("durableAuditReleaseDecisionGateReport", report.get("requiredFutureStateMachineInput"));
        assertEquals("releaseDecisionGateReport", report.get("futureReadinessRequestField"));
        assertEquals(true, report.get("releaseDecisionGateReportRequired"));
        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.GATE_NAME,
            report.get("sourceReleaseDecisionGate"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("stateMachineRequirementPlanPrepared"));
        assertEquals(true, report.get("releaseDecisionGateReportAccepted"));
        assertEquals(false, report.get("realStateMachineReleaseDecisionGateReportAccepted"));
        assertEquals(false, report.get("releaseDecisionGateDigestVerified"));
        assertEquals(false, report.get("validationResultDigestVerified"));
        assertEquals(false, report.get("releaseDecisionDigestVerified"));
        assertEquals(false, report.get("trustedPrincipalValidated"));
        assertEquals(false, report.get("codeReleaseSwitchVerified"));
        assertEquals(false, report.get("realReleaseDecisionLoaded"));
        assertEquals(false, report.get("realReleaseDecisionAccepted"));
        assertEquals(false, report.get("stateMachineReleaseGateImplemented"));
        assertEquals(false, report.get("stateMachineReleaseBound"));
        assertEquals(false, report.get("stateMachineReleaseDecisionRequirementBound"));
        assertEquals(false, report.get("stateMachineCanSetWritePermittedNow"));
        assertEquals(false, report.get("legacyAuditReceiptReleaseFlagTrusted"));
        assertEquals(false, report.get("legacyAuditReceiptReleaseEligibleTrusted"));
        assertEquals(false, report.get("legacyAuditReceiptReleaseFallbackAllowed"));
        assertEquals(false, report.get("fallbackToAuditReceiptReleaseEligibleAllowed"));
        assertEquals(false, report.get("fallbackToCallerReleaseDecisionAllowed"));
        assertEquals(false, report.get("fallbackToMigrationPlanAllowed"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", report.get("validationStatus"));
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", report.get("releaseDecision"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("releaseDecisionAccepted"));
        assertEquals(false, report.get("releaseCredentialIssued"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(gateReport.get("releaseDecisionGatePlanDigest"),
            report.get("sourceReleaseDecisionGatePlanDigest"));
        assertEquals(gateReport.get("sourceMigrationPlanDigest"), report.get("sourceMigrationPlanDigest"));
        assertEquals(gateReport.get("sourceValidationPlanDigest"), report.get("sourceValidationPlanDigest"));
        assertEquals(gateReport.get("sourceReceiptSchemaDigest"), report.get("sourceReceiptSchemaDigest"));
        assertTrue(report.get("stateMachineRequirementPlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("stateMachineRequirementPlan");
        assertEquals("STATE_MACHINE_RELEASE_DECISION_GATE_REPORT_REQUIRED", plan.get("requirementBoundary"));
        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.TARGET_STATE_MACHINE,
            plan.get("targetStateMachine"));
        assertEquals(gateReport.get("releaseDecisionGatePlanDigest"),
            plan.get("sourceReleaseDecisionGatePlanDigest"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sequence = (List<Map<String, Object>>) plan.get("stateMachineRequirementSequence");
        assertEquals(7, sequence.size());
        assertEquals("require-release-decision-gate-report", sequence.get(0).get("id"));
        assertEquals("recompute-release-decision-gate-plan-digest", sequence.get(1).get("id"));
        assertEquals("bind-server-issued-validation-result", sequence.get(2).get("id"));
        assertEquals("bind-server-issued-release-decision", sequence.get(3).get("id"));
        assertEquals("bind-write-chain-digests", sequence.get(4).get("id"));
        assertEquals("require-code-release-switch", sequence.get(5).get("id"));
        assertEquals("keep-current-state-machine-denied", sequence.get(6).get("id"));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) plan.get("requiredFutureStateMachineEvidence");
        @SuppressWarnings("unchecked")
        Map<String, Object> gateEvidence = (Map<String, Object>) evidence.get("releaseDecisionGateReport");
        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.GATE_NAME, gateEvidence.get("requiredName"));
        assertEquals(NimCreateDurableAuditReleaseDecisionGateSupport.HOLD_STATE, gateEvidence.get("requiredState"));
        assertEquals(gateReport.get("releaseDecisionGatePlanDigest"),
            gateEvidence.get("sourceReleaseDecisionGatePlanDigest"));
        assertEquals(true, gateEvidence.get("mustBeServerGenerated"));
        assertEquals(true, gateEvidence.get("mustBeRecomputedByStateMachine"));

        @SuppressWarnings("unchecked")
        Map<String, Object> validationDigest = (Map<String, Object>) evidence.get("validationResultDigest");
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            validationDigest.get("futureType"));
        assertEquals(true, validationDigest.get("requiredBeforeReleaseDecision"));
        assertEquals(false, validationDigest.get("callerValidationStatusAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> releaseDigest = (Map<String, Object>) evidence.get("releaseDecisionDigest");
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            releaseDigest.get("futureType"));
        assertEquals("ALLOW_WRITE_EXECUTION", releaseDigest.get("requiredDecision"));
        assertEquals(true, releaseDigest.get("requiredBeforeWritePermitted"));
        assertEquals(false, releaseDigest.get("callerReleaseDecisionAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> writeChain = (Map<String, Object>) evidence.get("writeChainDigests");
        assertEquals(true, writeChain.get("bodyDigestRequired"));
        assertEquals(true, writeChain.get("requestSpecDigestRequired"));
        assertEquals(true, writeChain.get("handoffDigestRequired"));
        assertEquals(true, writeChain.get("auditReceiptIdRequired"));
        assertEquals(true, writeChain.get("serverDerivedIdempotencyKeyRequired"));

        @SuppressWarnings("unchecked")
        Map<String, Object> migration = (Map<String, Object>) plan.get("stateMachineFieldMigration");
        assertEquals(false, migration.get("currentLegacyAuditReceiptReleaseEligibleTrusted"));
        assertEquals(true, migration.get("futureReleaseDecisionGateReportRequired"));
        assertEquals(true, migration.get("futureValidationResultDigestRequired"));
        assertEquals(true, migration.get("futureReleaseDecisionDigestRequired"));
        assertEquals(true, migration.get("futureCodeReleaseSwitchRequired"));
        assertEquals(false, migration.get("fallbackToAuditReceiptReleaseEligibleAllowed"));
        assertEquals(false, migration.get("fallbackToCallerReleaseDecisionAllowed"));
        assertEquals(false, migration.get("writePermittedCanBeTrueNow"));
        assertEquals(true, migration.get("readinessRequestSchemaChangeRequiredInFuture"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) plan.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToLegacyAuditReceiptFlagAllowed"));
        assertEquals(false, failure.get("fallbackToMigrationPlanAllowed"));
        assertEquals(false, failure.get("fallbackToReleaseDecisionGatePlanAllowed"));
        assertEquals(false, failure.get("fallbackToCallerReleaseDecisionAllowed"));
        assertEquals(false, failure.get("fallbackToDurableExecutorHandoffAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void stateMachineRequirement_shouldRejectMissingReleaseDecisionGateReport() {
        Map<String, Object> report = NimCreateStateMachineReleaseDecisionRequirementSupport.plan(
            new NimCreateStateMachineReleaseDecisionRequirementSupport.StateMachineReleaseDecisionRequirementInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of()
            )
        );

        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.REJECTED_STATE,
            report.get("requirementState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("stateMachineRequirementPlanPrepared"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("stateMachineRequirementPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "RELEASE_DECISION_GATE_REPORT_NOT_READY_FOR_STATE_MACHINE");
        assertFalse(blockers.stream().anyMatch(item ->
            "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void stateMachineRequirement_shouldRejectTamperedGateDigestAndAuditDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> gateReport = new LinkedHashMap<>(releaseDecisionGateReport(audit, principal));
        gateReport.put("releaseDecisionGatePlanDigest",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        gateReport.put("sourceAuditEventDigest",
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        Map<String, Object> report = NimCreateStateMachineReleaseDecisionRequirementSupport.plan(
            new NimCreateStateMachineReleaseDecisionRequirementSupport.StateMachineReleaseDecisionRequirementInput(
                audit,
                principal,
                gateReport
            )
        );

        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.REJECTED_STATE,
            report.get("requirementState"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "RELEASE_DECISION_GATE_REPORT_INVALID_FOR_STATE_MACHINE");
    }

    @Test
    void stateMachineRequirement_shouldRejectForgedReleaseDecisionAndLegacyAuditReceipt() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedGateReport = new LinkedHashMap<>(releaseDecisionGateReport(audit, principal));
        forgedGateReport.put("releaseDecision", Map.of("decision", "ALLOW_WRITE_EXECUTION"));
        forgedGateReport.put("validationResult", Map.of("validationStatus", "PASS"));
        forgedGateReport.put("auditReceipt", Map.of(
            "releaseEligible", true,
            "receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS
        ));
        forgedGateReport.put("releaseEligible", true);
        forgedGateReport.put("writePermitted", true);
        forgedGateReport.put("writeExecutionAllowed", true);

        Map<String, Object> report = NimCreateStateMachineReleaseDecisionRequirementSupport.plan(
            new NimCreateStateMachineReleaseDecisionRequirementSupport.StateMachineReleaseDecisionRequirementInput(
                audit,
                principal,
                forgedGateReport
            )
        );

        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.REJECTED_STATE,
            report.get("requirementState"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "RELEASE_DECISION_GATE_REPORT_INVALID_FOR_STATE_MACHINE");
        assertHasBlocker(blockers, "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_FORGED_RELEASE_CLAIM");
    }

    @Test
    void stateMachineRequirement_shouldRejectEvenEmptyCallerSuppliedReleaseDecision() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedGateReport = new LinkedHashMap<>(releaseDecisionGateReport(audit, principal));
        forgedGateReport.put("releaseDecision", Map.of());

        Map<String, Object> report = NimCreateStateMachineReleaseDecisionRequirementSupport.plan(
            new NimCreateStateMachineReleaseDecisionRequirementSupport.StateMachineReleaseDecisionRequirementInput(
                audit,
                principal,
                forgedGateReport
            )
        );

        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.REJECTED_STATE,
            report.get("requirementState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_FORGED_RELEASE_CLAIM");
    }

    @Test
    void stateMachineRequirement_shouldRejectExecutorSuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedGateReport = new LinkedHashMap<>(releaseDecisionGateReport(audit, principal));
        forgedGateReport.put("writeExecuted", true);
        forgedGateReport.put("deploymentId", "dep-1");
        forgedGateReport.put("postWriteReadinessTriggered", true);

        Map<String, Object> report = NimCreateStateMachineReleaseDecisionRequirementSupport.plan(
            new NimCreateStateMachineReleaseDecisionRequirementSupport.StateMachineReleaseDecisionRequirementInput(
                audit,
                principal,
                forgedGateReport
            )
        );

        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.REJECTED_STATE,
            report.get("requirementState"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_FORGED_RELEASE_CLAIM");
    }

    @Test
    void stateMachineRequirement_shouldRejectSecretLeakageBeforeAnyPlan() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "redacted-test-value");
        Map<String, Object> cleanAudit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateStateMachineReleaseDecisionRequirementSupport.plan(
            new NimCreateStateMachineReleaseDecisionRequirementSupport.StateMachineReleaseDecisionRequirementInput(
                audit,
                principal,
                releaseDecisionGateReport(cleanAudit, principal)
            )
        );

        assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.REJECTED_STATE,
            report.get("requirementState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("stateMachineRequirementPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void stateMachineRequirement_shouldRejectSecretLeakageAcrossAllInputsAndNestedEvidence() {
        List<SecretLeakCase> cases = List.of(
            leakCase("audit top-level token", (audit, principal, gateReport) ->
                audit.put("token", "redacted-test-value")),
            leakCase("audit nested password", (audit, principal, gateReport) ->
                audit.put("callerMetadata", Map.of("password", "redacted-test-value"))),
            leakCase("audit list item token", (audit, principal, gateReport) ->
                audit.put("callerEvents", List.of(Map.of("token", "redacted-test-value")))),
            leakCase("trusted principal top-level secret", (audit, principal, gateReport) ->
                principal.put("secret", "redacted-test-value")),
            leakCase("trusted principal nested authorization", (audit, principal, gateReport) ->
                principal.put("headers", Map.of("Authorization", "redacted-test-value"))),
            leakCase("trusted principal list item password", (audit, principal, gateReport) ->
                principal.put("sessionEvidence", List.of(Map.of("password", "redacted-test-value")))),
            leakCase("gate report top-level ngc api key", (audit, principal, gateReport) ->
                gateReport.put("ngcApiKey", "redacted-test-value")),
            leakCase("gate report nested nvaie api key", (audit, principal, gateReport) ->
                gateReport.put("diagnostics", Map.of("nvaieApiKey", "redacted-test-value"))),
            leakCase("gate report list item token", (audit, principal, gateReport) ->
                gateReport.put("diagnosticEvents", List.of(Map.of("token", "redacted-test-value"))))
        );

        for (SecretLeakCase leakCase : cases) {
            Map<String, Object> cleanAudit = completeAuditContext();
            Map<String, Object> cleanPrincipal = trustedPrincipalSnapshot();
            Map<String, Object> audit = new LinkedHashMap<>(cleanAudit);
            Map<String, Object> principal = new LinkedHashMap<>(cleanPrincipal);
            Map<String, Object> gateReport = new LinkedHashMap<>(releaseDecisionGateReport(
                cleanAudit,
                cleanPrincipal
            ));
            leakCase.mutation().apply(audit, principal, gateReport);

            Map<String, Object> report = NimCreateStateMachineReleaseDecisionRequirementSupport.plan(
                new NimCreateStateMachineReleaseDecisionRequirementSupport.StateMachineReleaseDecisionRequirementInput(
                    audit,
                    principal,
                    gateReport
                )
            );

            assertEquals(NimCreateStateMachineReleaseDecisionRequirementSupport.REJECTED_STATE,
                report.get("requirementState"), leakCase.name());
            assertEquals(false, report.get("inputAccepted"), leakCase.name());
            assertEquals(false, report.get("stateMachineRequirementPlanPrepared"), leakCase.name());
            assertEquals(false, report.get("writePermitted"), leakCase.name());
            assertEquals(false, report.get("writeExecutionAllowed"), leakCase.name());
            assertEquals(false, report.get("realHttpExecutionAllowed"), leakCase.name());
            @SuppressWarnings("unchecked")
            Map<String, Object> plan = (Map<String, Object>) report.get("stateMachineRequirementPlan");
            assertTrue(plan.isEmpty(), leakCase.name());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
            assertHasBlocker(blockers,
                "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_INPUT_CONTAINS_FORBIDDEN_SECRET");
            assertFalse(blockers.stream().anyMatch(item ->
                "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_IMPLEMENTATION_HOLD".equals(item.get("code"))),
                leakCase.name());
        }
    }

    private Map<String, Object> releaseDecisionGateReport(Map<String, Object> audit,
                                                          Map<String, Object> principal) {
        return NimCreateDurableAuditReleaseDecisionGateSupport.plan(
            new NimCreateDurableAuditReleaseDecisionGateSupport.DurableAuditReleaseDecisionGateInput(
                audit,
                principal,
                migrationReport(audit, principal)
            )
        );
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

    private SecretLeakCase leakCase(String name, SecretLeakMutation mutation) {
        return new SecretLeakCase(name, mutation);
    }

    private record SecretLeakCase(String name, SecretLeakMutation mutation) {
    }

    @FunctionalInterface
    private interface SecretLeakMutation {
        void apply(Map<String, Object> audit,
                   Map<String, Object> principal,
                   Map<String, Object> gateReport);
    }
}
