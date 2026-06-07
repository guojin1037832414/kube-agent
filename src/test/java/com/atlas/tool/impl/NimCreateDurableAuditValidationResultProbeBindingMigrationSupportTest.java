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
