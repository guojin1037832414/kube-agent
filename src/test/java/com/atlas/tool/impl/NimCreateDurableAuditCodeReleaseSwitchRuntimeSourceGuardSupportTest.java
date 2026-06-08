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
 * Tests for the NIM runtime switch source guard matrix.
 *
 * <p>The matrix is a safety teaching artifact and a regression contract: current reports can only
 * define planning/shape evidence, while caller JSON, flags, state-machine booleans, executor
 * success, readback data, and storage backfills remain non-authoritative.</p>
 */
class NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupportTest {

    @Test
    void sourceGuard_shouldBuildMatrixButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> runtimeBindingReport = runtimeBindingReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                audit,
                principal,
                runtimeBindingReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.SOURCE_GUARD_CONTRACT_NAME,
            report.get("codeReleaseSwitchRuntimeSourceGuard"));
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.HOLD_STATE,
            report.get("guardState"), report.toString());
        assertEquals(NimCreateStateMachineSupport.TARGET_TOOL, report.get("targetTool"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(false, report.get("springBeanRegistered"));
        assertEquals(false, report.get("httpClientBound"));
        assertEquals(false, report.get("storageClientBound"));
        assertEquals(false, report.get("toolRegistered"));
        assertEquals(false, report.get("controllerRegistered"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("sourceGuardMatrixPrepared"));
        assertEquals(true, report.get("runtimeBindingReportRequired"));
        assertEquals(true, report.get("runtimeBindingDigestRecomputed"));
        assertEquals(false, report.get("sourceGuardInstalled"));
        assertEquals(false, report.get("candidateSourceEvidenceAuthoritative"));
        assertForbiddenSourceFlagsRemainFalse(report);
        assertEquals(true, report.get("serverOwnedOpenSwitchRequired"));
        assertEquals(true, report.get("reviewedCodeSwitchDigestRequired"));
        assertEquals(true, report.get("stateMachineDigestRecheckRequired"));
        assertEquals(true, report.get("durableExecutorDigestRecheckRequired"));
        assertEquals(List.of(), report.get("acceptedSourcesForCurrentRelease"));
        @SuppressWarnings("unchecked")
        List<String> dangerousFields = (List<String>) report.get("dangerousReleaseCredentialFieldNames");
        assertTrue(dangerousFields.contains("nimCreateReleased"));
        assertTrue(dangerousFields.contains("codeReleaseSwitchContractReportAcceptedForRelease"));
        assertTrue(dangerousFields.contains("writePermitted"));
        assertTrue(dangerousFields.contains("writeExecuted"));
        assertTrue(dangerousFields.contains("deploymentId"));
        assertEquals(runtimeBindingReport.get("runtimeBindingContractDigest"),
            report.get("sourceRuntimeBindingContractDigest"));
        assertEquals(runtimeBindingReport.get("sourceCodeReleaseSwitchContractDigest"),
            report.get("sourceCodeReleaseSwitchContractDigest"));
        assertEquals(NimCreateAuditWriterSupport.DIGEST_ALGORITHM,
            report.get("sourceGuardMatrixDigestAlgorithm"));
        assertTrue(report.get("sourceGuardMatrixDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("sourceGuardContract");
        assertEquals("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REQUIRED",
            contract.get("contractBoundary"));
        assertEquals(true, contract.get("futureOnly"));
        assertEquals(false, contract.get("instanceAllowedNow"));
        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.BINDING_CONTRACT_NAME,
            contract.get("sourceRuntimeBindingContract"));
        assertEquals(runtimeBindingReport.get("runtimeBindingContractDigest"),
            contract.get("sourceRuntimeBindingContractDigest"));
        assertEquals("PLANNING_AND_GUARD_ONLY", contract.get("currentAcceptedSourceScope"));
        assertEquals(List.of(), contract.get("acceptedSourcesForCurrentRelease"));
        assertEquals(dangerousFields, contract.get("dangerousReleaseCredentialFieldNames"));

        @SuppressWarnings("unchecked")
        List<String> planningSources =
            (List<String>) contract.get("contractShapeSourcesAcceptedForPlanning");
        assertTrue(planningSources.contains("M5.21-72_CODE_RELEASE_SWITCH_CONTRACT_REPORT"));
        assertTrue(planningSources.contains("M5.21-73_RUNTIME_BINDING_REPORT"));

        @SuppressWarnings("unchecked")
        Map<String, Object> acceptanceRules = (Map<String, Object>) contract.get("acceptanceRules");
        assertEquals(true, acceptanceRules.get("failClosed"));
        assertEquals(0, acceptanceRules.get("currentReleaseSourceCount"));
        assertEquals(false, acceptanceRules.get("contractReportAcceptedForRelease"));
        assertEquals(false, acceptanceRules.get("runtimeBindingReportAcceptedForRelease"));
        assertEquals(false, acceptanceRules.get("legacyNimCreateReleasedBooleanAuthoritative"));
        assertEquals(false, acceptanceRules.get("stateMachineWritePermittedAuthoritativeForExecutor"));
        assertEquals(false, acceptanceRules.get("executorSuccessAuthoritativeForSwitch"));
        assertEquals(false, acceptanceRules.get("environmentOrRuntimeOverrideAllowed"));
        assertEquals(false, acceptanceRules.get("backendReadbackAllowedAsReleaseSource"));
        assertEquals(true, acceptanceRules.get("realOpenSwitchIssuerRequired"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) contract.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToCallerParamAllowed"));
        assertEquals(false, failure.get("fallbackToLlmJsonAllowed"));
        assertEquals(false, failure.get("fallbackToEnvironmentVariableAllowed"));
        assertEquals(false, failure.get("fallbackToRuntimeFlagAllowed"));
        assertEquals(false, failure.get("fallbackToStateMachineWritePermittedAllowed"));
        assertEquals(false, failure.get("fallbackToDurableExecutorSuccessAllowed"));
        assertEquals(false, failure.get("fallbackToBackendQueryResultAllowed"));
        assertEquals(false, failure.get("fallbackToStorageBackfillAllowed"));
        assertEquals(false, failure.get("fallbackToContractReportOnlyAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) report.get("sourceGuardMatrix");
        assertSourceRow(matrix, "M5.21-72_CODE_RELEASE_SWITCH_CONTRACT_REPORT", true, false, false);
        assertSourceRow(matrix, "M5.21-73_RUNTIME_BINDING_REPORT", true, false, false);
        assertSourceRow(matrix, "REVIEWED_SERVER_OWNED_OPEN_SWITCH", false, false, true);
        assertSourceRow(matrix, "CALLER_PARAMS_OR_LLM_JSON", false, false, false);
        assertSourceRow(matrix, "ENVIRONMENT_VARIABLE_OR_RUNTIME_FLAG", false, false, false);
        assertSourceRow(matrix, "STATE_MACHINE_WRITE_PERMITTED_BOOLEAN", false, false, false);
        assertSourceRow(matrix, "DURABLE_EXECUTOR_SUCCESS_OR_DEPLOYMENT_ID", false, false, false);
        assertSourceRow(matrix, "BACKEND_QUERY_OR_READBACK_RESULT", false, false, false);
        assertSourceRow(matrix, "SYS_LOG_OR_ELASTICSEARCH_BACKFILL", false, false, false);
        assertSourceRow(matrix, "RELEASE_DECISION_OR_VALIDATION_CONTRACT_REPORT_ONLY", false, false, false);
        assertTrue(matrix.stream().allMatch(item -> Boolean.FALSE.equals(item.get("writePermittedAllowedNow"))));
        assertTrue(matrix.stream().allMatch(item -> Boolean.FALSE.equals(item.get("writeExecutionAllowedNow"))));

        @SuppressWarnings("unchecked")
        List<String> forbiddenShortcuts = (List<String>) contract.get("forbiddenShortcuts");
        assertTrue(forbiddenShortcuts.contains("treating nimCreateReleased=true as an open code switch"));
        assertTrue(forbiddenShortcuts.contains("treating backend readback or storage rows as switch-open evidence"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void sourceGuard_shouldRejectMissingRuntimeBindingReport() {
        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.REJECTED_STATE,
            report.get("guardState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("sourceGuardMatrixPrepared"));
        assertForbiddenSourceFlagsRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("sourceGuardContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_REPORT_NOT_READY_FOR_SOURCE_GUARD");
        assertFalse(blockers.stream().anyMatch(item ->
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void sourceGuard_shouldRejectTamperedRuntimeBindingDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> tampered = new LinkedHashMap<>(runtimeBindingReport(audit, principal));
        tampered.put("runtimeBindingContractDigest", "c".repeat(64));

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                audit,
                principal,
                tampered,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.REJECTED_STATE,
            report.get("guardState"));
        assertEquals(false, report.get("inputAccepted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_REPORT_INVALID_FOR_SOURCE_GUARD");
    }

    @Test
    void sourceGuard_shouldRejectDigestConsistentNestedRuntimeSwitchDigestDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> driftedReport = withDriftedNestedRuntimeSwitchDigest(
            runtimeBindingReport(audit, principal),
            "d".repeat(64)
        );

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                audit,
                principal,
                driftedReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.REJECTED_STATE,
            report.get("guardState"));
        assertEquals(false, report.get("inputAccepted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_REPORT_INVALID_FOR_SOURCE_GUARD");
    }

    @Test
    void sourceGuard_shouldRejectDigestConsistentRuntimeBindingExtraFutureEvidenceField() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> driftedReport = withDigestConsistentRuntimeBindingExtraFutureEvidenceField(
            runtimeBindingReport(audit, principal)
        );

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                audit,
                principal,
                driftedReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.REJECTED_STATE,
            report.get("guardState"));
        assertEquals(false, report.get("inputAccepted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_REPORT_INVALID_FOR_SOURCE_GUARD");
    }

    @Test
    void sourceGuard_shouldRejectForgedCandidateSourceEvidence() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> candidate = Map.ofEntries(
            entry("source", "ENVIRONMENT_VARIABLE_OR_RUNTIME_FLAG"),
            entry("nimCreateReleased", true),
            entry("codeReleaseSwitchContractReportAcceptedForRelease", true),
            entry("codeReleaseSwitchDigestVerified", true),
            entry("switchState", "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION"),
            entry("writePermitted", true),
            entry("writeExecutionAllowed", true),
            entry("deploymentId", "dep-1")
        );

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                audit,
                principal,
                runtimeBindingReport(audit, principal),
                candidate
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.REJECTED_STATE,
            report.get("guardState"));
        assertEquals(false, report.get("inputAccepted"));
        assertForbiddenSourceFlagsRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_CANDIDATE_NOT_AUTHORIZED");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_FORGED_RELEASE_CLAIM");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ignored = (List<Map<String, Object>>) report.get("ignoredCandidateClaims");
        assertTrue(ignored.stream().anyMatch(item -> "nimCreateReleased".equals(item.get("key"))));
        assertTrue(ignored.stream().anyMatch(item ->
            "codeReleaseSwitchContractReportAcceptedForRelease".equals(item.get("key"))));
        assertTrue(ignored.stream().anyMatch(item -> "writePermitted".equals(item.get("key"))));
        assertTrue(ignored.stream().anyMatch(item -> "writeExecutionAllowed".equals(item.get("key"))));
        assertTrue(ignored.stream().anyMatch(item -> "deploymentId".equals(item.get("key"))));
    }

    @Test
    void sourceGuard_shouldRejectBackendReadbackAndStorageBackfillCandidates() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> candidate = Map.of(
            "backendQueryResult", Map.of(
                "source", "GET /api/{orgId}/deployment",
                "deploymentId", "dep-1",
                "status", "RUNNING"
            ),
            "sysLogBackfill", Map.of(
                "source", "sys_log",
                "releaseCredentialIssued", true
            )
        );

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                audit,
                principal,
                runtimeBindingReport(audit, principal),
                candidate
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.REJECTED_STATE,
            report.get("guardState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_CANDIDATE_NOT_AUTHORIZED");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_FORGED_RELEASE_CLAIM");
    }

    @Test
    void sourceGuard_shouldRejectSecretLeakageAndNotEchoSecretValue() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        String injectedSecret = "source-guard-secret";
        Map<String, Object> candidate = Map.of(
            "source", "CALLER_PARAMS_OR_LLM_JSON",
            "nested", List.of(Map.of("Authorization", injectedSecret))
        );

        Map<String, Object> report = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                audit,
                principal,
                runtimeBindingReport(audit, principal),
                candidate
            )
        );

        assertEquals(NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.REJECTED_STATE,
            report.get("guardState"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void sourceGuardSupport_shouldNotImportRealNetworkStorageSpringOrWriterDependencies()
        throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/atlas/tool/impl/"
                + "NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.java"
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

    private void assertForbiddenSourceFlagsRemainFalse(Map<String, Object> report) {
        assertEquals(false, report.get("callerParamSourceAllowed"));
        assertEquals(false, report.get("llmJsonSourceAllowed"));
        assertEquals(false, report.get("environmentVariableSourceAllowed"));
        assertEquals(false, report.get("runtimeFlagSourceAllowed"));
        assertEquals(false, report.get("stateMachineBooleanSourceAllowed"));
        assertEquals(false, report.get("durableExecutorSuccessSourceAllowed"));
        assertEquals(false, report.get("backendQuerySourceAllowedForRelease"));
        assertEquals(false, report.get("sysLogBackfillSourceAllowed"));
        assertEquals(false, report.get("releaseDecisionContractReportSourceAllowed"));
        assertEquals(false, report.get("validationResultContractReportSourceAllowed"));
    }

    private void assertSourceRow(List<Map<String, Object>> matrix,
                                 String source,
                                 boolean acceptedForPlanningNow,
                                 boolean authoritativeForReleaseNow,
                                 boolean futureAuthoritativeCandidate) {
        Map<String, Object> row = matrix.stream()
            .filter(item -> source.equals(item.get("source")))
            .findFirst()
            .orElseThrow(() -> new AssertionError("missing source row: " + source));
        assertEquals(acceptedForPlanningNow, row.get("acceptedForPlanningNow"));
        assertEquals(authoritativeForReleaseNow, row.get("authoritativeForReleaseNow"));
        assertEquals(futureAuthoritativeCandidate, row.get("futureAuthoritativeCandidate"));
        assertEquals(false, row.get("writePermittedAllowedNow"));
        assertEquals(false, row.get("writeExecutionAllowedNow"));
    }

    private Map<String, Object> runtimeBindingReport(Map<String, Object> audit,
                                                     Map<String, Object> principal) {
        return NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                audit,
                principal,
                codeReleaseSwitchContractReport(audit, principal),
                Map.of(),
                Map.of()
            )
        );
    }

    private Map<String, Object> withDriftedNestedRuntimeSwitchDigest(Map<String, Object> runtimeBindingReport,
                                                                    String sourceCodeReleaseSwitchContractDigest) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(runtimeBindingReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("runtimeBindingContract")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> stateMachineBinding = new LinkedHashMap<>(
            (Map<String, Object>) contract.get("stateMachineRuntimeBinding")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> durableExecutorBinding = new LinkedHashMap<>(
            (Map<String, Object>) contract.get("durableExecutorRuntimeBinding")
        );
        stateMachineBinding.put("sourceCodeReleaseSwitchContractDigest", sourceCodeReleaseSwitchContractDigest);
        durableExecutorBinding.put("sourceCodeReleaseSwitchContractDigest", sourceCodeReleaseSwitchContractDigest);
        contract.put("stateMachineRuntimeBinding", stateMachineBinding);
        contract.put("durableExecutorRuntimeBinding", durableExecutorBinding);
        forgedReport.put("runtimeBindingContract", contract);
        forgedReport.put("runtimeBindingContractDigest", digestFor(contract));
        return forgedReport;
    }

    private Map<String, Object> withDigestConsistentRuntimeBindingExtraFutureEvidenceField(
        Map<String, Object> runtimeBindingReport
    ) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(runtimeBindingReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("runtimeBindingContract")
        );
        @SuppressWarnings("unchecked")
        List<String> requiredFields = new java.util.ArrayList<>(
            (List<String>) contract.get("requiredFutureRuntimeEvidenceDigestFields")
        );
        requiredFields.add("forgedFutureRuntimeEvidenceDigest");
        contract.put("requiredFutureRuntimeEvidenceDigestFields", requiredFields);
        forgedReport.put("runtimeBindingContract", contract);
        forgedReport.put("runtimeBindingContractDigest", digestFor(contract));
        return forgedReport;
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

    private String digestFor(Object value) {
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
