package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM code release switch runtime binding 契约测试。
 *
 * <p>这些用例证明 M5.21-72 的 code release switch contract report 还不能直接放行写入；
 * 未来必须由状态机复算 switch contract digest，并由 durable executor 在真实 POST 前再次复核同一个 digest。</p>
 */
class NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupportTest {

    @Test
    void runtimeBinding_shouldBuildContractButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> switchReport = codeReleaseSwitchContractReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                audit,
                principal,
                switchReport,
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.BINDING_CONTRACT_NAME,
            report.get("codeReleaseSwitchRuntimeBindingContract"));
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.HOLD_STATE,
            report.get("bindingState"), report.toString());
        assertEquals(NimCreateStateMachineSupport.TARGET_TOOL, report.get("targetTool"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(false, report.get("springBeanRegistered"));
        assertEquals(false, report.get("httpClientBound"));
        assertEquals(false, report.get("storageClientBound"));
        assertEquals(false, report.get("toolRegistered"));
        assertEquals(false, report.get("controllerRegistered"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("runtimeBindingContractPrepared"));
        assertEquals(true, report.get("codeReleaseSwitchContractReportRequired"));
        assertEquals(true, report.get("stateMachineRuntimeBindingRequired"));
        assertEquals(true, report.get("durableExecutorRuntimeBindingRequired"));
        assertEquals(false, report.get("stateMachineReleaseEvidenceAuthoritative"));
        assertEquals(false, report.get("durableExecutorReleaseEvidenceAuthoritative"));
        assertEquals(true, report.get("serverOwnedOpenSwitchRequired"));
        assertEquals(true, report.get("reviewedCodeSwitchDigestRequired"));
        assertEquals(false, report.get("legacyNimCreateReleasedBooleanAuthoritative"));
        assertEquals(false, report.get("runtimeFlagOverrideAllowed"));
        assertEquals(false, report.get("environmentVariableOverrideAllowed"));
        assertRuntimeStatesRemainFalse(report);
        assertEquals(switchReport.get("codeReleaseSwitchContractDigest"),
            report.get("sourceCodeReleaseSwitchContractDigest"));
        assertEquals(switchReport.get("sourceReleaseDecisionContractDigest"),
            report.get("sourceReleaseDecisionContractDigest"));
        assertEquals(switchReport.get("sourceValidationResultContractDigest"),
            report.get("sourceValidationResultContractDigest"));
        assertEquals(switchReport.get("sourceEnhancedMigrationPlanDigest"),
            report.get("sourceEnhancedMigrationPlanDigest"));
        assertEquals(switchReport.get("sourceProbeBindingPlanDigest"),
            report.get("sourceProbeBindingPlanDigest"));
        assertEquals(switchReport.get("sourceProbeResultContractDigest"),
            report.get("sourceProbeResultContractDigest"));
        assertEquals(switchReport.get("sourceProbeExecutorPlanDigest"),
            report.get("sourceProbeExecutorPlanDigest"));
        assertEquals(switchReport.get("sourceMigrationPlanDigest"),
            report.get("sourceMigrationPlanDigest"));
        assertEquals(switchReport.get("sourceReceiptSchemaDigest"),
            report.get("sourceReceiptSchemaDigest"));
        assertEquals(switchReport.get("sourceValidationPlanDigest"),
            report.get("sourceValidationPlanDigest"));
        assertEquals(switchReport.get("sourceInterfaceSpecDigest"),
            report.get("sourceInterfaceSpecDigest"));
        assertEquals(switchReport.get("sourceBoundaryPlanDigest"),
            report.get("sourceBoundaryPlanDigest"));
        assertEquals(switchReport.get("sourceWriterPlanDigest"),
            report.get("sourceWriterPlanDigest"));
        assertEquals(switchReport.get("sourceAvailabilityPlanDigest"),
            report.get("sourceAvailabilityPlanDigest"));
        assertEquals(NimCreateAuditWriterSupport.DIGEST_ALGORITHM,
            report.get("runtimeBindingContractDigestAlgorithm"));
        assertTrue(report.get("runtimeBindingContractDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("runtimeBindingContract");
        assertEquals("CODE_RELEASE_SWITCH_RUNTIME_BINDING_REQUIRED",
            contract.get("contractBoundary"));
        assertEquals(true, contract.get("futureOnly"));
        assertEquals(false, contract.get("instanceAllowedNow"));
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchContractSupport.SWITCH_CONTRACT_NAME,
            contract.get("sourceCodeReleaseSwitchContract"));
        assertEquals(switchReport.get("codeReleaseSwitchContractDigest"),
            contract.get("sourceCodeReleaseSwitchContractDigest"));

        @SuppressWarnings("unchecked")
        Map<String, Object> stateMachine =
            (Map<String, Object>) contract.get("stateMachineRuntimeBinding");
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.TARGET_STATE_MACHINE,
            stateMachine.get("target"));
        assertEquals("codeReleaseSwitchContractReport",
            stateMachine.get("futureReadinessRequestField"));
        assertEquals(true, stateMachine.get("codeReleaseSwitchContractReportRequired"));
        assertEquals(true, stateMachine.get("codeReleaseSwitchContractDigestRequired"));
        assertEquals(true, stateMachine.get("mustRecomputeCodeReleaseSwitchContractDigest"));
        assertEquals(true, stateMachine.get("mustRequireServerOwnedOpenSwitch"));
        assertEquals(true, stateMachine.get("mustBindServerIssuedReleaseDecisionDigest"));
        assertEquals(true, stateMachine.get("mustBindServerIssuedValidationResultDigest"));
        assertEquals(true, stateMachine.get("mustBindWriteChainDigests"));
        assertEquals(false, stateMachine.get("legacyNimCreateReleasedBooleanAuthoritative"));
        assertEquals(false, stateMachine.get("fallbackToRuntimeFlagAllowed"));
        assertEquals(false, stateMachine.get("fallbackToEnvironmentVariableAllowed"));
        assertEquals(false, stateMachine.get("fallbackToReleaseDecisionContractAllowed"));
        assertEquals(false, stateMachine.get("writePermittedCanBeTrueNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> executor =
            (Map<String, Object>) contract.get("durableExecutorRuntimeBinding");
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.TARGET_DURABLE_EXECUTOR,
            executor.get("target"));
        assertEquals(true, executor.get("codeReleaseSwitchDigestRequired"));
        assertEquals(true, executor.get("mustRecheckImmediatelyBeforePost"));
        assertEquals(true, executor.get("mustBindSameHandoffDigest"));
        assertEquals(true, executor.get("mustBindSameRequestSpecDigest"));
        assertEquals(true, executor.get("mustBindSameBodyDigest"));
        assertEquals(true, executor.get("mustBindServerDerivedIdempotencyKey"));
        assertEquals(false, executor.get("fallbackToStateMachineWritePermittedAllowed"));
        assertEquals(false, executor.get("fallbackToStateMachineFlagOnlyAllowed"));
        assertEquals(false, executor.get("fallbackToExecutorSuccessAllowed"));
        assertEquals(false, executor.get("writeExecutionAllowedNow"));

        @SuppressWarnings("unchecked")
        List<String> requiredFields =
            (List<String>) contract.get("requiredFutureRuntimeEvidenceDigestFields");
        assertTrue(requiredFields.contains("codeReleaseSwitchContractDigest"));
        assertTrue(requiredFields.contains("codeReleaseSwitchDigest"));
        assertTrue(requiredFields.contains("releaseDecisionDigest"));
        assertTrue(requiredFields.contains("validationResultDigest"));
        assertTrue(requiredFields.contains("bodyDigest"));
        assertTrue(requiredFields.contains("requestSpecDigest"));
        assertTrue(requiredFields.contains("handoffDigest"));
        assertTrue(requiredFields.contains("serverDerivedIdempotencyKey"));

        @SuppressWarnings("unchecked")
        Map<String, Object> template = (Map<String, Object>) contract.get("currentRuntimeTemplate");
        assertEquals("LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH", template.get("switchState"));
        assertEquals(false, template.get("codeReleaseSwitchContractReportAccepted"));
        assertEquals(false, template.get("codeReleaseSwitchDigestVerified"));
        assertEquals(false, template.get("stateMachineRuntimeBindingInstalled"));
        assertEquals(false, template.get("durableExecutorRuntimeBindingInstalled"));
        assertEquals(false, template.get("writePermitted"));
        assertEquals(false, template.get("writeExecutionAllowed"));
        assertEquals(false, template.get("realHttpExecutionAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) contract.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToCodeReleaseSwitchContractReportAllowed"));
        assertEquals(false, failure.get("fallbackToCallerRuntimeEvidenceAllowed"));
        assertEquals(false, failure.get("fallbackToEnvironmentVariableAllowed"));
        assertEquals(false, failure.get("fallbackToRuntimeFlagAllowed"));
        assertEquals(false, failure.get("fallbackToNimCreateReleasedBooleanAllowed"));
        assertEquals(false, failure.get("fallbackToStateMachineWritePermittedAllowed"));
        assertEquals(false, failure.get("fallbackToDurableExecutorSuccessAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void runtimeBinding_shouldRejectMissingCodeReleaseSwitchContractReport() {
        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of(),
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("runtimeBindingContractPrepared"));
        assertRuntimeStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("runtimeBindingContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_CONTRACT_REPORT_NOT_READY_FOR_RUNTIME_BINDING");
        assertFalse(blockers.stream().anyMatch(item ->
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void runtimeBinding_shouldRejectTamperedSwitchContractDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> switchReport = new LinkedHashMap<>(codeReleaseSwitchContractReport(audit, principal));
        switchReport.put("codeReleaseSwitchContractDigest", "a".repeat(64));

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                audit,
                principal,
                switchReport,
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertEquals(false, report.get("inputAccepted"));
        assertRuntimeStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_CONTRACT_REPORT_INVALID_FOR_RUNTIME_BINDING");
    }

    @Test
    void runtimeBinding_shouldRejectDigestConsistentReleaseDecisionBindingDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> switchReport = withDriftedReleaseDecisionBinding(
            codeReleaseSwitchContractReport(audit, principal),
            "b".repeat(64)
        );

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                audit,
                principal,
                switchReport,
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertEquals(false, report.get("inputAccepted"));
        assertRuntimeStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_CONTRACT_REPORT_INVALID_FOR_RUNTIME_BINDING");
    }

    @Test
    void runtimeBinding_shouldRejectDigestConsistentSwitchContractExtraFutureEvidenceField() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> switchReport = withDigestConsistentSwitchContractExtraFutureEvidenceField(
            codeReleaseSwitchContractReport(audit, principal)
        );

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                audit,
                principal,
                switchReport,
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertEquals(false, report.get("inputAccepted"));
        assertRuntimeStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_CONTRACT_REPORT_INVALID_FOR_RUNTIME_BINDING");
    }

    @Test
    void runtimeBinding_shouldRejectDigestConsistentSwitchContractTemplateOrPrerequisiteExtensions() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        for (Map<String, Object> switchReport : List.of(
            withDigestConsistentSwitchContractNestedMapField(
                codeReleaseSwitchContractReport(audit, principal),
                "currentTemplate",
                "forgedRuntimeBindingInstalled",
                true
            ),
            withDigestConsistentSwitchContractNestedMapField(
                codeReleaseSwitchContractReport(audit, principal),
                "openPrerequisites",
                "forgedRuntimeBindingCanSkipStateMachineRecheck",
                true
            )
        )) {
            Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
                new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                    .CodeReleaseSwitchRuntimeBindingInput(
                    audit,
                    principal,
                    switchReport,
                    Map.of(),
                    Map.of()
                )
            );

            assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.REJECTED_STATE,
                report.get("bindingState"), switchReport.toString());
            assertEquals(false, report.get("inputAccepted"), switchReport.toString());
            assertRuntimeStatesRemainFalse(report);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
            assertHasBlocker(blockers,
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_INVALID_FOR_RUNTIME_BINDING");
        }
    }

    @Test
    void runtimeBinding_shouldRejectDigestConsistentSwitchContractBindingMapExtensions() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        for (Map<String, Object> switchReport : List.of(
            withDigestConsistentSwitchContractNestedMapField(
                codeReleaseSwitchContractReport(audit, principal),
                "releaseDecisionBinding",
                "fallbackToReleaseDecisionContractAllowed",
                false
            ),
            withDigestConsistentSwitchContractNestedMapField(
                codeReleaseSwitchContractReport(audit, principal),
                "stateMachineBinding",
                "fallbackToRuntimeBindingAcceptedAllowed",
                false
            ),
            withDigestConsistentSwitchContractNestedMapField(
                codeReleaseSwitchContractReport(audit, principal),
                "durableExecutorBinding",
                "fallbackToStateMachineWritePermittedAllowed",
                false
            )
        )) {
            Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
                new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                    .CodeReleaseSwitchRuntimeBindingInput(
                    audit,
                    principal,
                    switchReport,
                    Map.of(),
                    Map.of()
                )
            );

            assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.REJECTED_STATE,
                report.get("bindingState"), switchReport.toString());
            assertEquals(false, report.get("inputAccepted"), switchReport.toString());
            assertRuntimeStatesRemainFalse(report);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
            assertHasBlocker(blockers,
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_INVALID_FOR_RUNTIME_BINDING");
        }
    }

    @Test
    void runtimeBinding_shouldRejectForgedRuntimeReleaseEvidence() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> switchReport = codeReleaseSwitchContractReport(audit, principal);
        Map<String, Object> stateMachineEvidence = Map.of(
            "nimCreateReleased", true,
            "writePermitted", true,
            "switchState", "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION"
        );
        Map<String, Object> durableExecutorEvidence = Map.of(
            "writeExecutionAllowed", true,
            "writeExecuted", true,
            "postWriteReadinessTriggered", true
        );

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                audit,
                principal,
                switchReport,
                stateMachineEvidence,
                durableExecutorEvidence
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertEquals(false, report.get("inputAccepted"));
        assertRuntimeStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_RUNTIME_EVIDENCE_NOT_AUTHORITATIVE");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_FORGED_RELEASE_CLAIM");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ignored =
            (List<Map<String, Object>>) report.get("ignoredCallerClaims");
        assertTrue(ignored.stream().anyMatch(item -> "nimCreateReleased".equals(item.get("key"))));
        assertTrue(ignored.stream().anyMatch(item -> "writePermitted".equals(item.get("key"))));
        assertTrue(ignored.stream().anyMatch(item -> "writeExecuted".equals(item.get("key"))));
    }

    @Test
    void runtimeBinding_shouldRejectSecretLeakageAndNotEchoSecretValue() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        String injectedSecret = "redacted-runtime-secret";
        audit.put("nested", List.of(Map.of("Authorization", injectedSecret)));

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                audit,
                trustedPrincipalSnapshot(),
                Map.of(),
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertRuntimeStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void runtimeBinding_shouldRejectNonNullForbiddenKeyValuesEvenWhenScalarStateLike() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("nested", List.of(Map.of("token", false, "secret", 0)));

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                audit,
                trustedPrincipalSnapshot(),
                Map.of(),
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertRuntimeStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void runtimeBindingSupport_shouldNotImportRealNetworkStorageSpringOrWriterDependencies()
        throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/atlas/tool/impl/"
                + "NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.java"
        ));

        List<String> forbidden = List.of(
            "KubeManagerHttpClient",
            "RestClient",
            "RestTemplate",
            "WebClient",
            "HttpClient",
            "ElasticsearchTemplate",
            "ISysLogService",
            "java.net",
            "@Component",
            "@Service",
            "@Controller",
            "@RestController",
            "@Autowired",
            "@Bean",
            "ToolRegistry",
            "ToolRegistration",
            ".save(",
            ".insert(",
            "saveLog(",
            "POST /api/{orgId}/deployment",
            "8100"
        );

        for (String token : forbidden) {
            assertFalse(source.contains(token), "forbidden dependency/path found: " + token);
        }
        assertFalse(source.contains("result.put(\"writePermitted\", true)"));
        assertFalse(source.contains("result.put(\"writeExecutionAllowed\", true)"));
        assertFalse(source.contains("result.put(\"realHttpExecutionAllowed\", true)"));
        assertFalse(source.contains("result.put(\"realStorageTouched\", true)"));
    }

    private void assertRuntimeStatesRemainFalse(Map<String, Object> report) {
        assertEquals(false, report.get("codeReleaseSwitchDigestVerified"));
        assertEquals(false, report.get("codeReviewDigestVerified"));
        assertEquals(false, report.get("testEvidenceDigestVerified"));
        assertEquals(false, report.get("releaseDecisionDigestVerified"));
        assertEquals(false, report.get("validationResultDigestVerified"));
        assertEquals(false, report.get("trustedPrincipalValidated"));
        assertEquals(false, report.get("runtimeBindingInstalled"));
        assertEquals(false, report.get("stateMachineReleaseBound"));
        assertEquals(false, report.get("durableExecutorReleaseBound"));
        assertEquals(false, report.get("releaseDecisionAccepted"));
        assertEquals(false, report.get("releaseCredentialIssued"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals("LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH",
            report.get("codeReleaseSwitchStatus"));
        assertEquals("OPEN_FOR_NIM_CREATE_WRITE_EXECUTION",
            report.get("requiredSwitchState"));
        assertEquals("DENY_UNTIL_SERVER_VALIDATION_RESULT", report.get("releaseDecision"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", report.get("validationStatus"));
    }

    private Map<String, Object> codeReleaseSwitchContractReport(Map<String, Object> audit,
                                                                Map<String, Object> principal) {
        return NimCreateDurableAuditCodeReleaseSwitchContractSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchContractSupport.CodeReleaseSwitchContractInput(
                audit,
                principal,
                releaseDecisionContractReport(audit, principal),
                Map.of()
            )
        );
    }

    private Map<String, Object> withDriftedReleaseDecisionBinding(Map<String, Object> switchReport,
                                                                  String releaseDecisionContractDigest) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(switchReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("codeReleaseSwitchContract")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> releaseDecisionBinding = new LinkedHashMap<>(
            (Map<String, Object>) contract.get("releaseDecisionBinding")
        );
        releaseDecisionBinding.put("sourceReleaseDecisionContractDigest", releaseDecisionContractDigest);
        contract.put("releaseDecisionBinding", releaseDecisionBinding);
        forgedReport.put("codeReleaseSwitchContract", contract);
        forgedReport.put("codeReleaseSwitchContractDigest", digestFor(contract));
        return forgedReport;
    }

    private Map<String, Object> withDigestConsistentSwitchContractExtraFutureEvidenceField(
        Map<String, Object> switchReport
    ) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(switchReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("codeReleaseSwitchContract")
        );
        @SuppressWarnings("unchecked")
        List<String> requiredFields = new java.util.ArrayList<>(
            (List<String>) contract.get("requiredFutureEvidenceDigestFields")
        );
        requiredFields.add("forgedSwitchFutureEvidenceDigest");
        contract.put("requiredFutureEvidenceDigestFields", requiredFields);
        forgedReport.put("codeReleaseSwitchContract", contract);
        forgedReport.put("codeReleaseSwitchContractDigest", digestFor(contract));
        return forgedReport;
    }

    private Map<String, Object> withDigestConsistentSwitchContractNestedMapField(
        Map<String, Object> switchReport,
        String nestedKey,
        String forgedKey,
        Object forgedValue
    ) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(switchReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("codeReleaseSwitchContract")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = new LinkedHashMap<>(
            (Map<String, Object>) contract.get(nestedKey)
        );
        nested.put(forgedKey, forgedValue);
        contract.put(nestedKey, nested);
        forgedReport.put("codeReleaseSwitchContract", contract);
        forgedReport.put("codeReleaseSwitchContractDigest", digestFor(contract));
        return forgedReport;
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
