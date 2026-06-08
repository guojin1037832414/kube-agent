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
 * NIM durable audit release decision 契约测试。
 *
 * <p>这些测试证明 future release decision 必须绑定 M5.21-70 validation result contract、
 * 未来 server-issued validation result digest、代码级 release switch 和写链路 digest；
 * 当前只生成契约，不签发真实 release decision，也不允许任何写执行。</p>
 */
class NimCreateDurableAuditReleaseDecisionContractSupportTest {

    @Test
    void releaseDecision_shouldBuildContractButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport = validationResultContractReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionContractSupport.plan(
            new NimCreateDurableAuditReleaseDecisionContractSupport.ReleaseDecisionContractInput(
                audit,
                principal,
                validationResultReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionContractSupport.DECISION_CONTRACT_NAME,
            report.get("durableAuditReleaseDecisionContract"));
        assertEquals(NimCreateDurableAuditReleaseDecisionContractSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditReleaseDecisionContractSupport.HOLD_STATE,
            report.get("releaseDecisionState"), report.toString());
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(false, report.get("springBeanRegistered"));
        assertEquals(false, report.get("httpClientBound"));
        assertEquals(false, report.get("storageClientBound"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("releaseDecisionContractPrepared"));
        assertEquals(true, report.get("serverIssuedReleaseDecisionRequired"));
        assertEquals(false, report.get("callerReleaseEvidenceAuthoritative"));
        assertEquals(true, report.get("validationResultContractRequired"));
        assertEquals(true, report.get("serverIssuedValidationResultDigestRequired"));
        assertEquals(false, report.get("legacyValidationResultReportAloneAllowed"));
        assertEquals(false, report.get("legacyAuditReceiptReleaseFlagTrusted"));
        assertReleaseStatesRemainFalse(report);
        assertEquals(validationResultReport.get("validationResultContractDigest"),
            report.get("sourceValidationResultContractDigest"));
        assertEquals(validationResultReport.get("sourceEnhancedMigrationPlanDigest"),
            report.get("sourceEnhancedMigrationPlanDigest"));
        assertEquals(validationResultReport.get("sourceProbeBindingPlanDigest"),
            report.get("sourceProbeBindingPlanDigest"));
        assertEquals(validationResultReport.get("sourceProbeResultContractDigest"),
            report.get("sourceProbeResultContractDigest"));
        assertEquals(validationResultReport.get("sourceProbeExecutorPlanDigest"),
            report.get("sourceProbeExecutorPlanDigest"));
        assertEquals(validationResultReport.get("sourceMigrationPlanDigest"),
            report.get("sourceMigrationPlanDigest"));
        assertEquals(validationResultReport.get("sourceReceiptSchemaDigest"),
            report.get("sourceReceiptSchemaDigest"));
        assertEquals(validationResultReport.get("sourceValidationPlanDigest"),
            report.get("sourceValidationPlanDigest"));
        assertEquals(validationResultReport.get("sourceInterfaceSpecDigest"),
            report.get("sourceInterfaceSpecDigest"));
        assertEquals(validationResultReport.get("sourceBoundaryPlanDigest"),
            report.get("sourceBoundaryPlanDigest"));
        assertEquals(validationResultReport.get("sourceWriterPlanDigest"),
            report.get("sourceWriterPlanDigest"));
        assertEquals(validationResultReport.get("sourceAvailabilityPlanDigest"),
            report.get("sourceAvailabilityPlanDigest"));
        assertEquals(validationResultReport.get("trustedPrincipalDigest"),
            report.get("trustedPrincipalDigest"));
        assertEquals(audit.get("organizationId"), report.get("sourceOrganizationId"));
        assertEquals(audit.get("userId"), report.get("sourceUserId"));
        assertEquals(principal.get("username"), report.get("sourceUsername"));
        assertEquals(NimCreateAuditWriterSupport.DIGEST_ALGORITHM,
            report.get("releaseDecisionContractDigestAlgorithm"));
        assertTrue(report.get("releaseDecisionContractDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("releaseDecisionContract");
        assertEquals(
            NimCreateDurableAuditReleaseDecisionContractSupport.releaseDecisionContractFromReport(report),
            contract
        );
        assertEquals("SERVER_ISSUED_DURABLE_AUDIT_RELEASE_DECISION_REQUIRED",
            contract.get("contractBoundary"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            contract.get("type"));
        assertEquals(NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            contract.get("dependsOn"));
        assertEquals(true, contract.get("futureOnly"));
        assertEquals(false, contract.get("instanceAllowedNow"));
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", contract.get("currentDecision"));
        assertEquals("ALLOW_WRITE_EXECUTION", contract.get("requiredAllowDecision"));
        assertEquals(true, contract.get("serverIssuedRequired"));
        assertEquals(false, contract.get("callerProvidedReleaseDecisionAllowed"));
        assertEquals(validationResultReport.get("validationResultContractDigest"),
            contract.get("sourceValidationResultContractDigest"));
        assertEquals(report.get("trustedPrincipalDigest"), contract.get("trustedPrincipalDigest"));

        @SuppressWarnings("unchecked")
        List<String> requiredFutureFields =
            (List<String>) contract.get("requiredFutureEvidenceDigestFields");
        assertTrue(requiredFutureFields.contains("validationResultContractDigest"));
        assertTrue(requiredFutureFields.contains("validationResultDigest"));
        assertTrue(requiredFutureFields.contains("releaseDecisionDigest"));
        assertTrue(requiredFutureFields.contains("codeReleaseSwitchDigest"));
        assertTrue(requiredFutureFields.contains("bodyDigest"));
        assertTrue(requiredFutureFields.contains("requestSpecDigest"));
        assertTrue(requiredFutureFields.contains("handoffDigest"));
        assertTrue(requiredFutureFields.contains("auditReceiptId"));
        assertTrue(requiredFutureFields.contains("serverDerivedIdempotencyKey"));

        @SuppressWarnings("unchecked")
        Map<String, Object> validationBinding =
            (Map<String, Object>) contract.get("validationResultBinding");
        assertEquals(validationResultReport.get("validationResultContractDigest"),
            validationBinding.get("sourceValidationResultContractDigest"));
        assertEquals(true, validationBinding.get("futureValidationResultDigestRequired"));
        assertEquals(true, validationBinding.get("mustBindValidationResultContractDigest"));
        assertEquals(true, validationBinding.get("mustBindServerIssuedValidationResultDigest"));
        assertEquals(true, validationBinding.get("mustBindValidationPassStatus"));
        assertEquals(true, validationBinding.get("mustBindAllTypedReceiptAckDigests"));
        assertEquals(true, validationBinding.get("mustBindTrustedPrincipalDigest"));
        assertEquals(true, validationBinding.get("mustBindAuditEventDigest"));
        assertEquals(false, validationBinding.get("callerValidationResultAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> stateMachineBinding =
            (Map<String, Object>) contract.get("stateMachineBinding");
        assertEquals(true, stateMachineBinding.get("futureReleaseDecisionDigestRequired"));
        assertEquals(true, stateMachineBinding.get("futureValidationResultDigestRequired"));
        assertEquals(true, stateMachineBinding.get("futureCodeReleaseSwitchRequired"));
        assertEquals(false, stateMachineBinding.get("fallbackToAuditReceiptReleaseEligibleAllowed"));
        assertEquals(false, stateMachineBinding.get("fallbackToValidationResultContractAllowed"));
        assertEquals(false, stateMachineBinding.get("writePermittedCanBeTrueNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> durableExecutorBinding =
            (Map<String, Object>) contract.get("durableExecutorBinding");
        assertEquals(true, durableExecutorBinding.get("futureReleaseDecisionDigestRequired"));
        assertEquals(true, durableExecutorBinding.get("futureValidationResultDigestRequired"));
        assertEquals(true, durableExecutorBinding.get("futureBodyDigestRequired"));
        assertEquals(true, durableExecutorBinding.get("futureRequestSpecDigestRequired"));
        assertEquals(true, durableExecutorBinding.get("futureHandoffDigestRequired"));
        assertEquals(true, durableExecutorBinding.get("futureAuditReceiptIdRequired"));
        assertEquals(true, durableExecutorBinding.get("futureServerDerivedIdempotencyKeyRequired"));
        assertEquals(true, durableExecutorBinding.get("mustRecheckImmediatelyBeforePost"));
        assertEquals(false, durableExecutorBinding.get("fallbackToHandoffOnlyAllowed"));
        assertEquals(false, durableExecutorBinding.get("fallbackToRequestSpecOnlyAllowed"));
        assertEquals(false, durableExecutorBinding.get("writeExecutionAllowedNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) contract.get("currentTemplate");
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", template.get("decision"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", template.get("validationStatus"));
        assertEquals(false, template.get("validationResultDigestVerified"));
        assertEquals(false, template.get("validationResultContractDigestVerified"));
        assertEquals(false, template.get("releaseDecisionDigestVerified"));
        assertEquals(false, template.get("trustedPrincipalValidated"));
        assertEquals(false, template.get("codeReleaseSwitchVerified"));
        assertEquals(false, template.get("stateMachineReleaseBound"));
        assertEquals(false, template.get("durableExecutorReleaseBound"));
        assertEquals(false, template.get("releaseEligible"));
        assertEquals(false, template.get("writePermitted"));
        assertEquals(false, template.get("writeExecutionAllowed"));
        assertEquals(false, template.get("realHttpExecutionAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> prerequisites = (Map<String, Object>) contract.get("allowPrerequisites");
        assertEquals(false, prerequisites.get("currentContractSatisfiesPrerequisites"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) contract.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToValidationResultContractAllowed"));
        assertEquals(false, failure.get("fallbackToCallerReleaseDecisionAllowed"));
        assertEquals(false, failure.get("fallbackToLegacyAuditReceiptFlagAllowed"));
        assertEquals(false, failure.get("fallbackToStateMachineAcceptedBooleanAllowed"));
        assertEquals(false, failure.get("fallbackToExecutorSuccessAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void releaseDecision_shouldRejectMissingValidationResultContractReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionContractSupport.plan(
            new NimCreateDurableAuditReleaseDecisionContractSupport.ReleaseDecisionContractInput(
                audit,
                principal,
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionContractSupport.REJECTED_STATE,
            report.get("releaseDecisionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("releaseDecisionContractPrepared"));
        assertReleaseStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("releaseDecisionContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void releaseDecision_shouldRejectTamperedValidationResultContractDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> tamperedReport =
            new LinkedHashMap<>(validationResultContractReport(audit, principal));
        tamperedReport.put("validationResultContractDigest", "a".repeat(64));

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionContractSupport.plan(
            new NimCreateDurableAuditReleaseDecisionContractSupport.ReleaseDecisionContractInput(
                audit,
                principal,
                tamperedReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionContractSupport.REJECTED_STATE,
            report.get("releaseDecisionState"));
        assertReleaseStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_REPORT_INVALID_FOR_RELEASE_DECISION");
    }

    @Test
    void releaseDecision_shouldRejectDigestConsistentValidationResultExtraFutureEvidenceField() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport =
            withDigestConsistentValidationResultExtraFutureEvidenceField(
                validationResultContractReport(audit, principal)
            );

        assertRejectsDigestConsistentValidationResultContractDrift(audit, principal, validationResultReport);
    }

    @Test
    void releaseDecision_shouldRejectDigestConsistentValidationResultContractTopLevelExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport = withDigestConsistentValidationResultContractMutation(
            validationResultContractReport(audit, principal),
            contract -> contract.put("futureCompatibilityAccepted", false)
        );

        assertRejectsDigestConsistentValidationResultContractDrift(audit, principal, validationResultReport);
    }

    @Test
    void releaseDecision_shouldRejectDigestConsistentValidationResultTrustedIdentityExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport = withDigestConsistentValidationResultContractMutation(
            validationResultContractReport(audit, principal),
            contract -> objectMap(contract.get("trustedIdentityBinding"))
                .put("callerIdentityCanSatisfyValidation", false)
        );

        assertRejectsDigestConsistentValidationResultContractDrift(audit, principal, validationResultReport);
    }

    @Test
    void releaseDecision_shouldRejectDigestConsistentValidationResultEvidenceBindingExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport = withDigestConsistentValidationResultContractMutation(
            validationResultContractReport(audit, principal),
            contract -> objectMap(contract.get("evidenceBinding"))
                .put("legacyAuditReceiptCanSatisfyValidation", false)
        );

        assertRejectsDigestConsistentValidationResultContractDrift(audit, principal, validationResultReport);
    }

    @Test
    void releaseDecision_shouldRejectDigestConsistentValidationResultCurrentTemplateExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport = withDigestConsistentValidationResultContractMutation(
            validationResultContractReport(audit, principal),
            contract -> objectMap(contract.get("currentTemplate"))
                .put("validationPreviewAvailable", false)
        );

        assertRejectsDigestConsistentValidationResultContractDrift(audit, principal, validationResultReport);
    }

    @Test
    void releaseDecision_shouldRejectDigestConsistentValidationResultPassPrerequisitesValueDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport = withDigestConsistentValidationResultContractMutation(
            validationResultContractReport(audit, principal),
            contract -> objectMap(contract.get("passPrerequisites"))
                .put("currentContractSatisfiesPrerequisites", true)
        );

        assertRejectsDigestConsistentValidationResultContractDrift(audit, principal, validationResultReport);
    }

    @Test
    void releaseDecision_shouldRejectDigestConsistentValidationResultFailureContractExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport = withDigestConsistentValidationResultContractMutation(
            validationResultContractReport(audit, principal),
            contract -> objectMap(contract.get("failureContract"))
                .put("fallbackToDigestConsistentContractAllowed", false)
        );

        assertRejectsDigestConsistentValidationResultContractDrift(audit, principal, validationResultReport);
    }

    @Test
    void releaseDecision_shouldRejectDigestConsistentValidationResultForbiddenShortcutsListDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport = withDigestConsistentValidationResultContractMutation(
            validationResultContractReport(audit, principal),
            contract -> objectList(contract.get("forbiddenShortcuts"))
                .add("allowing release decision to accept validation result contract digest without exact maps")
        );

        assertRejectsDigestConsistentValidationResultContractDrift(audit, principal, validationResultReport);
    }

    @Test
    void releaseDecision_shouldRejectForgedValidationResultSuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedReport =
            new LinkedHashMap<>(validationResultContractReport(audit, principal));
        forgedReport.put("realValidationResultAccepted", true);
        forgedReport.put("validationStatus", "PASS");
        forgedReport.put("validationPassed", true);
        forgedReport.put("releaseDecisionAccepted", true);
        forgedReport.put("releaseEligible", true);
        forgedReport.put("releaseDecisionGateReportAccepted", true);
        forgedReport.put("realStateMachineReleaseDecisionGateReportAccepted", true);
        forgedReport.put("releaseDecisionGateDigestVerified", true);
        forgedReport.put("realReleaseDecisionLoaded", true);
        forgedReport.put("realReleaseDecisionAccepted", true);
        forgedReport.put("stateMachineReleaseGateImplemented", true);
        forgedReport.put("stateMachineCanSetWritePermittedNow", true);
        forgedReport.put("writeExecutionAllowed", true);
        forgedReport.put("releaseDecision", Map.of("decision", "ALLOW_WRITE_EXECUTION"));

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionContractSupport.plan(
            new NimCreateDurableAuditReleaseDecisionContractSupport.ReleaseDecisionContractInput(
                audit,
                principal,
                forgedReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionContractSupport.REJECTED_STATE,
            report.get("releaseDecisionState"));
        assertReleaseStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_REPORT_INVALID_FOR_RELEASE_DECISION");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_FORGED_RELEASE_CLAIM");
    }

    @Test
    void releaseDecision_shouldRejectCallerReleaseEvidenceAndSecretLeakage() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        String injectedSecret = "redacted-release-secret";
        Map<String, Object> callerEvidence = Map.of(
            "releaseDecision", Map.of(),
            "validationResult", Map.of("validationStatus", "PASS"),
            "legacyAuditReceipt", Map.of("releaseEligible", true),
            "nested", List.of(Map.of("Authorization", injectedSecret))
        );

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionContractSupport.plan(
            new NimCreateDurableAuditReleaseDecisionContractSupport.ReleaseDecisionContractInput(
                audit,
                principal,
                validationResultContractReport(audit, principal),
                callerEvidence
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionContractSupport.REJECTED_STATE,
            report.get("releaseDecisionState"));
        assertReleaseStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("releaseDecisionContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "CALLER_RELEASE_EVIDENCE_NOT_AUTHORITATIVE");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_FORGED_RELEASE_CLAIM");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void releaseDecision_shouldRejectSecretLeakageFromValidationResultReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        String injectedSecret = "redacted-validation-result-secret";
        Map<String, Object> validationResultReport =
            new LinkedHashMap<>(validationResultContractReport(audit, principal));
        validationResultReport.put("diagnostic", Map.of("token", injectedSecret));

        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionContractSupport.plan(
            new NimCreateDurableAuditReleaseDecisionContractSupport.ReleaseDecisionContractInput(
                audit,
                principal,
                validationResultReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionContractSupport.REJECTED_STATE,
            report.get("releaseDecisionState"));
        assertReleaseStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void releaseDecision_shouldNotDependOnRealNetworkStorageSpringOrWriters() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/atlas/tool/impl/"
                + "NimCreateDurableAuditReleaseDecisionContractSupport.java"
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
        assertFalse(source.contains("result.put(\"realReleaseDecisionCreated\", true)"));
        assertFalse(source.contains("result.put(\"serverIssuedReleaseDecisionAccepted\", true)"));
    }

    private void assertReleaseStatesRemainFalse(Map<String, Object> report) {
        assertEquals(false, report.get("realReleaseDecisionCreated"));
        assertEquals(false, report.get("serverIssuedReleaseDecisionAccepted"));
        assertEquals(false, report.get("realValidationResultAccepted"));
        assertEquals(false, report.get("validationResultDigestVerified"));
        assertEquals(false, report.get("validationResultContractDigestVerified"));
        assertEquals(false, report.get("releaseDecisionDigestVerified"));
        assertEquals(false, report.get("trustedPrincipalValidated"));
        assertEquals(false, report.get("codeReleaseSwitchVerified"));
        assertEquals(false, report.get("stateMachineReleaseBound"));
        assertEquals(false, report.get("durableExecutorReleaseBound"));
        assertEquals(false, report.get("releaseDecisionAccepted"));
        assertEquals(false, report.get("releaseCredentialIssued"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", report.get("validationStatus"));
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", report.get("releaseDecision"));
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

    private Map<String, Object> withDigestConsistentValidationResultExtraFutureEvidenceField(
        Map<String, Object> validationResultReport
    ) {
        return withDigestConsistentValidationResultContractMutation(
            validationResultReport,
            contract -> objectList(contract.get("requiredFutureEvidenceDigestFields"))
                .add("forgedValidationResultFutureEvidenceDigest")
        );
    }

    private Map<String, Object> withDigestConsistentValidationResultContractMutation(
        Map<String, Object> validationResultReport,
        Consumer<Map<String, Object>> mutator
    ) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(validationResultReport);
        Map<String, Object> contract = objectMap(deepMutableCopy(forgedReport.get("validationResultContract")));
        mutator.accept(contract);
        forgedReport.put("validationResultContract", contract);
        forgedReport.put("validationResultContractDigest", digestFor(contract));
        return forgedReport;
    }

    private void assertRejectsDigestConsistentValidationResultContractDrift(Map<String, Object> audit,
                                                                           Map<String, Object> principal,
                                                                           Map<String, Object> validationResultReport) {
        Map<String, Object> report = NimCreateDurableAuditReleaseDecisionContractSupport.plan(
            new NimCreateDurableAuditReleaseDecisionContractSupport.ReleaseDecisionContractInput(
                audit,
                principal,
                validationResultReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReleaseDecisionContractSupport.REJECTED_STATE,
            report.get("releaseDecisionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("releaseDecisionContractPrepared"));
        assertReleaseStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("releaseDecisionContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_REPORT_INVALID_FOR_RELEASE_DECISION");
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

    private String digestFor(Map<String, Object> value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK missing SHA-256 digest algorithm", ex);
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
}
