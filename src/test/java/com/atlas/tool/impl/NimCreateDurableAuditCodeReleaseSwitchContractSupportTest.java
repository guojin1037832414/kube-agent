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
 * NIM create code release switch 契约测试。
 *
 * <p>这些测试证明 future code release switch 必须绑定 M5.21-71 release decision contract、
 * 未来 release/validation digest、代码审查证据和写链路 digest；当前不会打开真实开关，也不会允许写执行。</p>
 */
class NimCreateDurableAuditCodeReleaseSwitchContractSupportTest {

    @Test
    void codeReleaseSwitch_shouldBuildContractButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> releaseDecisionReport = releaseDecisionContractReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchContractSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchContractSupport.CodeReleaseSwitchContractInput(
                audit,
                principal,
                releaseDecisionReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.SWITCH_CONTRACT_NAME,
            report.get("durableAuditCodeReleaseSwitchContract"));
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.HOLD_STATE,
            report.get("switchState"), report.toString());
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(false, report.get("springBeanRegistered"));
        assertEquals(false, report.get("httpClientBound"));
        assertEquals(false, report.get("storageClientBound"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("codeReleaseSwitchContractPrepared"));
        assertEquals(true, report.get("releaseDecisionContractRequired"));
        assertEquals(true, report.get("serverOwnedCodeReleaseSwitchRequired"));
        assertEquals(true, report.get("reviewedCodeSwitchDigestRequired"));
        assertEquals(true, report.get("serverIssuedReleaseDecisionDigestRequired"));
        assertEquals(true, report.get("serverIssuedValidationResultDigestRequired"));
        assertEquals(false, report.get("callerSwitchEvidenceAuthoritative"));
        assertEquals(false, report.get("legacyConfigFlagAllowed"));
        assertEquals(false, report.get("environmentVariableOverrideAllowed"));
        assertEquals(false, report.get("runtimeToggleOverrideAllowed"));
        assertSwitchStatesRemainFalse(report);
        assertEquals(releaseDecisionReport.get("releaseDecisionContractDigest"),
            report.get("sourceReleaseDecisionContractDigest"));
        assertEquals(releaseDecisionReport.get("sourceValidationResultContractDigest"),
            report.get("sourceValidationResultContractDigest"));
        assertEquals(releaseDecisionReport.get("sourceEnhancedMigrationPlanDigest"),
            report.get("sourceEnhancedMigrationPlanDigest"));
        assertEquals(releaseDecisionReport.get("sourceProbeBindingPlanDigest"),
            report.get("sourceProbeBindingPlanDigest"));
        assertEquals(releaseDecisionReport.get("sourceProbeResultContractDigest"),
            report.get("sourceProbeResultContractDigest"));
        assertEquals(releaseDecisionReport.get("sourceProbeExecutorPlanDigest"),
            report.get("sourceProbeExecutorPlanDigest"));
        assertEquals(releaseDecisionReport.get("sourceMigrationPlanDigest"),
            report.get("sourceMigrationPlanDigest"));
        assertEquals(releaseDecisionReport.get("sourceReceiptSchemaDigest"),
            report.get("sourceReceiptSchemaDigest"));
        assertEquals(releaseDecisionReport.get("sourceValidationPlanDigest"),
            report.get("sourceValidationPlanDigest"));
        assertEquals(releaseDecisionReport.get("sourceInterfaceSpecDigest"),
            report.get("sourceInterfaceSpecDigest"));
        assertEquals(releaseDecisionReport.get("sourceBoundaryPlanDigest"),
            report.get("sourceBoundaryPlanDigest"));
        assertEquals(releaseDecisionReport.get("sourceWriterPlanDigest"),
            report.get("sourceWriterPlanDigest"));
        assertEquals(releaseDecisionReport.get("sourceAvailabilityPlanDigest"),
            report.get("sourceAvailabilityPlanDigest"));
        assertEquals(releaseDecisionReport.get("trustedPrincipalDigest"),
            report.get("trustedPrincipalDigest"));
        assertEquals(NimCreateAuditWriterSupport.DIGEST_ALGORITHM,
            report.get("codeReleaseSwitchContractDigestAlgorithm"));
        assertTrue(report.get("codeReleaseSwitchContractDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("codeReleaseSwitchContract");
        assertEquals("REVIEWED_SERVER_OWNED_CODE_RELEASE_SWITCH_REQUIRED",
            contract.get("contractBoundary"));
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.FUTURE_CODE_RELEASE_SWITCH,
            contract.get("type"));
        assertEquals(NimCreateStateMachineSupport.TARGET_TOOL, contract.get("targetTool"));
        assertEquals(true, contract.get("futureOnly"));
        assertEquals(false, contract.get("instanceAllowedNow"));
        assertEquals("LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH", contract.get("currentSwitchState"));
        assertEquals("OPEN_FOR_NIM_CREATE_WRITE_EXECUTION", contract.get("requiredSwitchState"));
        assertEquals(true, contract.get("serverOwnedRequired"));
        assertEquals(false, contract.get("callerProvidedSwitchAllowed"));
        assertEquals(false, contract.get("environmentOverrideAllowed"));
        assertEquals(false, contract.get("runtimeFlagFallbackAllowed"));
        assertEquals(releaseDecisionReport.get("releaseDecisionContractDigest"),
            contract.get("sourceReleaseDecisionContractDigest"));
        assertEquals(releaseDecisionReport.get("sourceValidationResultContractDigest"),
            contract.get("sourceValidationResultContractDigest"));

        @SuppressWarnings("unchecked")
        Map<String, Object> releaseBinding = (Map<String, Object>) contract.get("releaseDecisionBinding");
        assertEquals(releaseDecisionReport.get("releaseDecisionContractDigest"),
            releaseBinding.get("sourceReleaseDecisionContractDigest"));
        assertEquals(releaseDecisionReport.get("sourceValidationResultContractDigest"),
            releaseBinding.get("sourceValidationResultContractDigest"));
        assertEquals(true, releaseBinding.get("futureReleaseDecisionDigestRequired"));
        assertEquals(true, releaseBinding.get("futureValidationResultDigestRequired"));
        assertEquals(true, releaseBinding.get("mustBindServerIssuedReleaseDecisionDigest"));
        assertEquals(true, releaseBinding.get("mustBindServerIssuedValidationResultDigest"));
        assertEquals(false, releaseBinding.get("callerReleaseDecisionAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> writeChain = (Map<String, Object>) contract.get("writeChainBinding");
        assertEquals(true, writeChain.get("futureBodyDigestRequired"));
        assertEquals(true, writeChain.get("futureRequestSpecDigestRequired"));
        assertEquals(true, writeChain.get("futureHandoffDigestRequired"));
        assertEquals(true, writeChain.get("futureAuditReceiptIdRequired"));
        assertEquals(true, writeChain.get("futureServerDerivedIdempotencyKeyRequired"));
        assertEquals(true, writeChain.get("mustBeRecheckedByStateMachine"));
        assertEquals(true, writeChain.get("mustBeRecheckedByDurableExecutor"));
        assertEquals(false, writeChain.get("fallbackToHandoffOnlyAllowed"));
        assertEquals(false, writeChain.get("fallbackToRequestSpecOnlyAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> review = (Map<String, Object>) contract.get("reviewBinding");
        assertEquals(true, review.get("codeReviewDigestRequired"));
        assertEquals(true, review.get("testEvidenceDigestRequired"));
        assertEquals(true, review.get("securityApprovalDigestRequired"));
        assertEquals(true, review.get("rollbackPlanDigestRequired"));
        assertEquals(true, review.get("changeWindowDigestRequired"));
        assertEquals(false, review.get("currentReviewSatisfied"));

        @SuppressWarnings("unchecked")
        Map<String, Object> stateMachine = (Map<String, Object>) contract.get("stateMachineBinding");
        assertEquals(true, stateMachine.get("futureCodeReleaseSwitchDigestRequired"));
        assertEquals(true, stateMachine.get("mustRecomputeSwitchDigestBeforeWritePermitted"));
        assertEquals(false, stateMachine.get("fallbackToRuntimeFlagAllowed"));
        assertEquals(false, stateMachine.get("fallbackToEnvironmentVariableAllowed"));
        assertEquals(false, stateMachine.get("writePermittedCanBeTrueNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> executor = (Map<String, Object>) contract.get("durableExecutorBinding");
        assertEquals(true, executor.get("futureCodeReleaseSwitchDigestRequired"));
        assertEquals(true, executor.get("mustRecheckImmediatelyBeforePost"));
        assertEquals(false, executor.get("fallbackToStateMachineFlagOnlyAllowed"));
        assertEquals(false, executor.get("writeExecutionAllowedNow"));

        @SuppressWarnings("unchecked")
        List<String> requiredFutureFields =
            (List<String>) contract.get("requiredFutureEvidenceDigestFields");
        assertTrue(requiredFutureFields.contains("releaseDecisionContractDigest"));
        assertTrue(requiredFutureFields.contains("validationResultDigest"));
        assertTrue(requiredFutureFields.contains("releaseDecisionDigest"));
        assertTrue(requiredFutureFields.contains("codeReleaseSwitchDigest"));
        assertTrue(requiredFutureFields.contains("codeReviewDigest"));
        assertTrue(requiredFutureFields.contains("testEvidenceDigest"));
        assertTrue(requiredFutureFields.contains("securityApprovalDigest"));
        assertTrue(requiredFutureFields.contains("rollbackPlanDigest"));
        assertTrue(requiredFutureFields.contains("changeWindowDigest"));
        assertTrue(requiredFutureFields.contains("serverDerivedIdempotencyKey"));

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) contract.get("currentTemplate");
        assertEquals("LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH", template.get("switchState"));
        assertEquals(false, template.get("codeReleaseSwitchDigestVerified"));
        assertEquals(false, template.get("codeReviewDigestVerified"));
        assertEquals(false, template.get("testEvidenceDigestVerified"));
        assertEquals(false, template.get("securityApprovalDigestVerified"));
        assertEquals(false, template.get("releaseDecisionDigestVerified"));
        assertEquals(false, template.get("writePermitted"));
        assertEquals(false, template.get("writeExecutionAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> prerequisites = (Map<String, Object>) contract.get("openPrerequisites");
        assertEquals(false, prerequisites.get("currentContractSatisfiesPrerequisites"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) contract.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToCallerSwitchAllowed"));
        assertEquals(false, failure.get("fallbackToEnvironmentVariableAllowed"));
        assertEquals(false, failure.get("fallbackToRuntimeFlagAllowed"));
        assertEquals(false, failure.get("fallbackToReleaseDecisionContractAllowed"));
        assertEquals(false, failure.get("fallbackToStateMachineBooleanAllowed"));
        assertEquals(false, failure.get("fallbackToExecutorSuccessAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void codeReleaseSwitch_shouldRejectMissingReleaseDecisionContractReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchContractSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchContractSupport.CodeReleaseSwitchContractInput(
                audit,
                principal,
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.REJECTED_STATE,
            report.get("switchState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("codeReleaseSwitchContractPrepared"));
        assertSwitchStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("codeReleaseSwitchContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void codeReleaseSwitch_shouldRejectTamperedReleaseDecisionContractDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> tamperedReport = new LinkedHashMap<>(releaseDecisionContractReport(audit, principal));
        tamperedReport.put("releaseDecisionContractDigest", "b".repeat(64));

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchContractSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchContractSupport.CodeReleaseSwitchContractInput(
                audit,
                principal,
                tamperedReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.REJECTED_STATE,
            report.get("switchState"));
        assertSwitchStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_REPORT_INVALID_FOR_CODE_SWITCH");
    }

    @Test
    void codeReleaseSwitch_shouldRejectForgedSwitchOpenClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedReport = new LinkedHashMap<>(releaseDecisionContractReport(audit, principal));
        forgedReport.put("realCodeReleaseSwitchCreated", true);
        forgedReport.put("realCodeReleaseSwitchOpened", true);
        forgedReport.put("serverOwnedCodeReleaseSwitchAccepted", true);
        forgedReport.put("codeReleaseSwitchDigestVerified", true);
        forgedReport.put("codeReviewDigestVerified", true);
        forgedReport.put("testEvidenceDigestVerified", true);
        forgedReport.put("switchState", "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION");
        forgedReport.put("releaseEligible", true);
        forgedReport.put("writePermitted", true);
        forgedReport.put("writeExecutionAllowed", true);

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchContractSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchContractSupport.CodeReleaseSwitchContractInput(
                audit,
                principal,
                forgedReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.REJECTED_STATE,
            report.get("switchState"));
        assertSwitchStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_REPORT_INVALID_FOR_CODE_SWITCH");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_CODE_RELEASE_SWITCH_FORGED_OPEN_CLAIM");
    }

    @Test
    void codeReleaseSwitch_shouldRejectCallerSwitchEvidenceAndSecretLeakage() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        String injectedSecret = "redacted-switch-secret";
        Map<String, Object> callerEvidence = Map.of(
            "codeReleaseSwitch", Map.of("switchState", "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION"),
            "environmentReleaseOverride", true,
            "runtimeReleaseFlag", true,
            "nested", List.of(Map.of("Authorization", injectedSecret))
        );

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchContractSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchContractSupport.CodeReleaseSwitchContractInput(
                audit,
                principal,
                releaseDecisionContractReport(audit, principal),
                callerEvidence
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.REJECTED_STATE,
            report.get("switchState"));
        assertSwitchStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("codeReleaseSwitchContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "CALLER_CODE_RELEASE_SWITCH_EVIDENCE_NOT_AUTHORITATIVE");
        assertHasBlocker(blockers, "DURABLE_AUDIT_CODE_RELEASE_SWITCH_FORGED_OPEN_CLAIM");
        assertHasBlocker(blockers, "DURABLE_AUDIT_CODE_RELEASE_SWITCH_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void codeReleaseSwitch_shouldRejectSecretLeakageFromReleaseDecisionReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        String injectedSecret = "redacted-release-report-secret";
        Map<String, Object> releaseDecisionReport =
            new LinkedHashMap<>(releaseDecisionContractReport(audit, principal));
        releaseDecisionReport.put("diagnostic", Map.of("token", injectedSecret));

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchContractSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchContractSupport.CodeReleaseSwitchContractInput(
                audit,
                principal,
                releaseDecisionReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.REJECTED_STATE,
            report.get("switchState"));
        assertSwitchStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_CODE_RELEASE_SWITCH_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void codeReleaseSwitch_shouldNotDependOnRealNetworkStorageSpringOrWriters() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/atlas/tool/impl/"
                + "NimCreateDurableAuditCodeReleaseSwitchContractSupport.java"
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
        assertFalse(source.contains("result.put(\"writePermitted\", true)"));
        assertFalse(source.contains("result.put(\"writeExecutionAllowed\", true)"));
        assertFalse(source.contains("result.put(\"releaseEligible\", true)"));
        assertFalse(source.contains("result.put(\"realCodeReleaseSwitchOpened\", true)"));
    }

    private void assertSwitchStatesRemainFalse(Map<String, Object> report) {
        assertEquals(false, report.get("realCodeReleaseSwitchCreated"));
        assertEquals(false, report.get("realCodeReleaseSwitchOpened"));
        assertEquals(false, report.get("serverOwnedCodeReleaseSwitchAccepted"));
        assertEquals(false, report.get("codeReleaseSwitchDigestVerified"));
        assertEquals(false, report.get("codeReviewDigestVerified"));
        assertEquals(false, report.get("testEvidenceDigestVerified"));
        assertEquals(false, report.get("releaseDecisionDigestVerified"));
        assertEquals(false, report.get("validationResultDigestVerified"));
        assertEquals(false, report.get("trustedPrincipalValidated"));
        assertEquals(false, report.get("stateMachineReleaseBound"));
        assertEquals(false, report.get("durableExecutorReleaseBound"));
        assertEquals(false, report.get("releaseDecisionAccepted"));
        assertEquals(false, report.get("releaseCredentialIssued"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals("LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH", report.get("codeReleaseSwitchStatus"));
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", report.get("releaseDecision"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", report.get("validationStatus"));
    }

    private Map<String, Object> releaseDecisionContractReport(Map<String, Object> audit,
                                                              Map<String, Object> principal) {
        return NimCreateDurableAuditReleaseDecisionContractSupport.plan(
            new NimCreateDurableAuditReleaseDecisionContractSupport.ReleaseDecisionContractInput(
                audit,
                principal,
                validationResultContractReport(audit, principal),
                Map.of()
            )
        );
    }

    private Map<String, Object> validationResultContractReport(Map<String, Object> audit,
                                                               Map<String, Object> principal) {
        return NimCreateDurableAuditReceiptValidationResultSupport.plan(
            new NimCreateDurableAuditReceiptValidationResultSupport.ReceiptValidationResultInput(
                audit,
                principal,
                probeBindingMigrationReport(audit, principal),
                Map.of()
            )
        );
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
