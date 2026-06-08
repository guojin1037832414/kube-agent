package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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
 * NIM durable write executor 合同壳测试。
 *
 * <p>本测试刻意不 mock HTTP client，也不访问 kube-manager。当前 executor 只验证 handoff 入场证据，
 * 然后停在 IMPLEMENTATION_HOLD；这样未来实现真实 POST 前，任何“已经写入”的声明都会被测试挡住。</p>
 */
class NimCreateDurableWriteExecutorSupportTest {

    @Test
    void executorShell_shouldAcceptTrustedHandoffButNotExecuteNetworkWrite() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.EXECUTOR_NAME, report.get("durableWriteExecutor"));
        assertEquals(NimCreateDurableWriteExecutorSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals(NimCreateDurableWriteExecutorSupport.HOLD_STATE, report.get("executionState"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(false, report.get("executorImplementationAvailable"));
        assertEquals(false, report.get("releaseCredential"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(false, report.get("writeAttempted"));
        assertEquals(false, report.get("writeExecuted"));
        assertEquals(false, report.get("postWriteReadinessTriggered"));
        assertEquals(true, report.get("codeReleaseSwitchContractReportRequired"));
        assertEquals(true, report.get("codeReleaseSwitchRuntimeBindingRequired"));
        assertEquals(true, report.get("codeReleaseSwitchRuntimeSourceGuardReportRequired"));
        assertEquals(codeSwitchReport.get("codeReleaseSwitchContractDigest"),
            report.get("sourceCodeReleaseSwitchContractDigest"));
        assertEquals(sourceGuardReport.get("sourceGuardMatrixDigest"),
            report.get("sourceGuardMatrixDigest"));
        assertEquals(sourceGuardReport.get("sourceRuntimeBindingContractDigest"),
            report.get("sourceRuntimeBindingContractDigest"));
        assertEquals(false, report.get("sourceGuardInstalled"));
        assertEquals(false, report.get("candidateSourceEvidenceAuthoritative"));
        assertEquals(false, report.get("backendQuerySourceAllowedForRelease"));
        assertEquals(false, report.get("sysLogBackfillSourceAllowed"));
        assertEquals(false, report.get("codeReleaseSwitchDigestVerified"));
        assertEquals(false, report.get("releaseDecisionDigestVerified"));
        assertEquals(false, report.get("validationResultDigestVerified"));
        assertEquals(false, report.get("fallbackToStateMachineWritePermittedAllowed"));
        assertEquals(handoffReport.get("handoffDigest"), report.get("sourceHandoffDigest"));
        assertEquals(requestSpecReport.get("requestSpecDigest"), report.get("sourceRequestSpecDigest"));
        assertEquals(handoffReport.get("idempotencyKey"), report.get("idempotencyKey"));

        @SuppressWarnings("unchecked")
        Map<String, Object> attemptSpec = (Map<String, Object>) report.get("executionAttemptSpec");
        assertEquals("deployment-create", attemptSpec.get("target"));
        assertEquals("POST", attemptSpec.get("method"));
        assertEquals("/api/100002/deployment", attemptSpec.get("resolvedPath"));
        assertEquals(NimCreateDurableWriteExecutorSupport.EXECUTION_ATTEMPT_SPEC_DIGEST_ALGORITHM,
            report.get("executionAttemptSpecDigestAlgorithm"));
        assertEquals(sha256(attemptSpec), report.get("executionAttemptSpecDigest"));
        assertEquals(true, attemptSpec.get("requestSpecCopiedByValue"));
        assertEquals(NimCreateWriteRequestSpecAdapterSupport.REQUEST_SPEC_DIGEST_ALGORITHM,
            attemptSpec.get("requestSpecDigestAlgorithm"));
        assertEquals(handoffReport.get("handoffDigest"), attemptSpec.get("handoffDigest"));
        assertEquals(requestSpecReport.get("requestSpecDigest"), attemptSpec.get("requestSpecDigest"));
        assertEquals(requestSpecReport.get("requestSpec"), attemptSpec.get("requestSpec"));
        assertEquals(true, attemptSpec.get("bodyCopiedByValue"));
        assertEquals(NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM,
            attemptSpec.get("bodyDigestAlgorithm"));
        @SuppressWarnings("unchecked")
        Map<String, Object> requestSpec = (Map<String, Object>) requestSpecReport.get("requestSpec");
        assertEquals(requestSpec.get("body"), attemptSpec.get("body"));
        assertEquals(true, attemptSpec.get("executionHandoffPlanCopiedByValue"));
        assertEquals(NimCreateWriteExecutionHandoffSupport.HANDOFF_DIGEST_ALGORITHM,
            attemptSpec.get("handoffDigestAlgorithm"));
        assertEquals(handoffReport.get("executionHandoffPlan"), attemptSpec.get("executionHandoffPlan"));
        assertEquals(handoffReport.get("idempotencyKey"), attemptSpec.get("idempotencyKey"));
        assertEquals("KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY", attemptSpec.get("kubeManagerAuthBoundary"));
        assertEquals(false, attemptSpec.get("callerHeadersAllowed"));
        assertEquals(false, attemptSpec.get("authorizationHeaderFromCallerAllowed"));
        assertEquals(false, attemptSpec.get("realApiKeyAllowed"));
        assertEquals(false, attemptSpec.get("writeWillBeAttempted"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD");
        assertEquals(2, blockers.size());
    }

    @Test
    void executorShell_shouldCopyAttemptSpecInputsByValue() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> attemptSpec = (Map<String, Object>) report.get("executionAttemptSpec");
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptRequestSpec = (Map<String, Object>) attemptSpec.get("requestSpec");
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptBody = (Map<String, Object>) attemptSpec.get("body");
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptHandoffPlan = (Map<String, Object>) attemptSpec.get("executionHandoffPlan");

        @SuppressWarnings("unchecked")
        Map<String, Object> mutableRequestSpec = (Map<String, Object>) requestSpecReport.get("requestSpec");
        @SuppressWarnings("unchecked")
        Map<String, Object> mutableRequestBody = (Map<String, Object>) mutableRequestSpec.get("body");
        @SuppressWarnings("unchecked")
        Map<String, Object> mutableHandoffPlan = (Map<String, Object>) handoffReport.get("executionHandoffPlan");
        mutableRequestSpec.put("resolvedPath", "/api/mutated/deployment");
        mutableRequestBody.put("displayName", "mutated-after-prepare");
        mutableHandoffPlan.put("resolvedPath", "/api/mutated/deployment");

        assertEquals("/api/100002/deployment", attemptRequestSpec.get("resolvedPath"));
        assertEquals("llama-nim", attemptBody.get("displayName"));
        assertEquals("/api/100002/deployment", attemptHandoffPlan.get("resolvedPath"));
        assertEquals(sha256(attemptSpec), report.get("executionAttemptSpecDigest"));
    }

    @Test
    void executorShell_shouldRejectMissingCodeReleaseSwitchContractReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeAttempted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_CONTRACT_REPORT_NOT_READY_FOR_DURABLE_EXECUTOR");
        assertFalse(blockers.stream().anyMatch(item -> "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void executorShell_shouldRejectMissingCodeReleaseSwitchRuntimeSourceGuardReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeAttempted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_READY_FOR_DURABLE_EXECUTOR");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void executorShell_shouldRejectMismatchedHandoffOrRequestSpec() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = new LinkedHashMap<>(writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport));
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);
        handoffReport.put("sourceRequestSpecDigest", "badbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadbadb");

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptSpec = (Map<String, Object>) report.get("executionAttemptSpec");
        assertTrue(attemptSpec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_EXECUTION_HANDOFF_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
        assertFalse(blockers.stream().anyMatch(item -> "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void executorShell_shouldRejectTamperedCodeReleaseSwitchContractDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = new LinkedHashMap<>(codeReleaseSwitchContractReport(audit));
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);
        codeSwitchReport.put("codeReleaseSwitchContractDigest", "a".repeat(64));

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_CONTRACT_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
    }

    @Test
    void executorShell_shouldRejectForgedOpenCodeReleaseSwitchClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = new LinkedHashMap<>(codeReleaseSwitchContractReport(audit));
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);
        codeSwitchReport.put("realCodeReleaseSwitchOpened", true);
        codeSwitchReport.put("writeExecutionAllowed", true);
        codeSwitchReport.put("codeReleaseSwitchDigestVerified", true);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeAttempted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_CONTRACT_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_CONTRACT_RELEASE_CLAIM_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
    }

    @Test
    void executorShell_shouldRejectSecretLeakageBeforeAnyWriteAttempt() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = new LinkedHashMap<>(writeRequestSpecReport(audit, receipt, bodyReport));
        requestSpecReport.put("Authorization", "Bearer real-key-material");
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, writeRequestSpecReport(audit, receipt, bodyReport));
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeAttempted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void executorShell_shouldRejectTamperedRuntimeSourceGuardDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = new LinkedHashMap<>(codeReleaseSwitchRuntimeSourceGuardReport(audit));
        sourceGuardReport.put("sourceGuardMatrixDigest", "b".repeat(64));

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
    }

    @Test
    void executorShell_shouldRejectForgedRuntimeSourceGuardReleaseClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = new LinkedHashMap<>(codeReleaseSwitchRuntimeSourceGuardReport(audit));
        sourceGuardReport.put("sourceGuardInstalled", true);
        sourceGuardReport.put("llmJsonSourceAllowed", true);
        sourceGuardReport.put("backendQuerySourceAllowedForRelease", true);
        sourceGuardReport.put("deploymentId", "dep-forged");

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeAttempted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
        assertHasBlocker(blockers,
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_RELEASE_CLAIM_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
    }

    @Test
    void executorShell_shouldRejectRuntimeSourceGuardSecretLeakage() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = new LinkedHashMap<>(codeReleaseSwitchRuntimeSourceGuardReport(audit));
        sourceGuardReport.put("Authorization", "Bearer real-key-material");

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void executorShell_shouldRejectNestedListSecretLeakageBeforeAnyWriteAttempt() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = new LinkedHashMap<>(codeReleaseSwitchRuntimeSourceGuardReport(audit));
        sourceGuardReport.put("diagnostics", List.of(
            Map.of("note", "still planning only"),
            Map.of("ngcApiKey", "redacted-test-value")
        ));

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeAttempted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void executorShell_shouldRejectDigestConsistentRequestSpecBodyWithNestedProtectedContext() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> trustedBodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> bodyReport = withNestedProtectedContextInBodyReport(trustedBodyReport);
        Map<String, Object> trustedRequestSpecReport = writeRequestSpecReport(audit, receipt, trustedBodyReport);
        Map<String, Object> requestSpecReport = withNestedProtectedContextInRequestBody(
            trustedRequestSpecReport,
            bodyReport
        );
        Map<String, Object> trustedHandoffReport = writeExecutionHandoffReport(
            audit,
            receipt,
            trustedBodyReport,
            trustedRequestSpecReport
        );
        Map<String, Object> handoffReport = withForgedDigestConsistentHandoff(
            trustedHandoffReport,
            bodyReport,
            requestSpecReport
        );
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("writeAttempted"));
        assertEquals(false, report.get("writeExecuted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_REQUEST_SPEC_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
    }

    @Test
    void executorShell_shouldRejectDigestConsistentRequestSpecWithExtraProtectedContextOutsideBody() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> trustedRequestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> requestSpecReport = withExtraRequestSpecField(
            trustedRequestSpecReport,
            "write_request_spec_report",
            "caller-forged"
        );
        Map<String, Object> trustedHandoffReport = writeExecutionHandoffReport(
            audit,
            receipt,
            bodyReport,
            trustedRequestSpecReport
        );
        Map<String, Object> handoffReport = withForgedDigestConsistentHandoff(
            trustedHandoffReport,
            bodyReport,
            requestSpecReport
        );
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptSpec = (Map<String, Object>) report.get("executionAttemptSpec");
        assertTrue(attemptSpec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_REQUEST_SPEC_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
    }

    @Test
    void executorShell_shouldRejectDigestConsistentHandoffPlanWithExtraProtectedContext() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> trustedHandoffReport = writeExecutionHandoffReport(
            audit,
            receipt,
            bodyReport,
            requestSpecReport
        );
        Map<String, Object> handoffReport = withExtraHandoffPlanField(
            trustedHandoffReport,
            "write_request_spec_report",
            "caller-forged"
        );
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptSpec = (Map<String, Object>) report.get("executionAttemptSpec");
        assertTrue(attemptSpec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_EXECUTION_HANDOFF_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
    }

    @Test
    void executorShell_shouldRejectDigestConsistentForgedServerDerivedIdempotencyKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = writeBodyReport(audit, receipt);
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> trustedHandoffReport = writeExecutionHandoffReport(
            audit,
            receipt,
            bodyReport,
            requestSpecReport
        );
        Map<String, Object> handoffReport = withForgedIdempotencyKey(
            trustedHandoffReport,
            "nim-create-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
        Map<String, Object> codeSwitchReport = codeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport(audit);

        Map<String, Object> report = NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );

        assertEquals(NimCreateDurableWriteExecutorSupport.REJECTED_STATE, report.get("executionState"));
        assertEquals(false, report.get("inputAccepted"));
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptSpec = (Map<String, Object>) report.get("executionAttemptSpec");
        assertTrue(attemptSpec.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_EXECUTION_HANDOFF_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR");
    }

    private Map<String, Object> writeExecutionHandoffReport(Map<String, Object> audit,
                                                            Map<String, Object> receipt,
                                                            Map<String, Object> bodyReport,
                                                            Map<String, Object> requestSpecReport) {
        return NimCreateWriteExecutionHandoffSupport.prepare(
            new NimCreateWriteExecutionHandoffSupport.WriteExecutionHandoffInput(
                openGate(),
                audit,
                receipt,
                bodyReport,
                requestSpecReport
            )
        );
    }

    private Map<String, Object> writeRequestSpecReport(Map<String, Object> audit,
                                                       Map<String, Object> receipt,
                                                       Map<String, Object> bodyReport) {
        return NimCreateWriteRequestSpecAdapterSupport.compile(
            new NimCreateWriteRequestSpecAdapterSupport.WriteRequestSpecInput(
                openGate(),
                audit,
                receipt,
                bodyReport
            )
        );
    }

    private Map<String, Object> writeBodyReport(Map<String, Object> audit,
                                                Map<String, Object> receipt) {
        return NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                completePreview(),
                audit,
                receipt
            )
        );
    }

    private Map<String, Object> codeReleaseSwitchContractReport(Map<String, Object> audit) {
        Map<String, Object> principal = trustedPrincipalSnapshot(audit);
        return NimCreateDurableAuditCodeReleaseSwitchContractSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchContractSupport.CodeReleaseSwitchContractInput(
                audit,
                principal,
                releaseDecisionContractReport(audit, principal),
                Map.of()
            )
        );
    }

    private Map<String, Object> withNestedProtectedContextInBodyReport(Map<String, Object> bodyReport) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(bodyReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new LinkedHashMap<>((Map<String, Object>) forgedReport.get("body"));
        body.put("commands", List.of(Map.of("write_request_spec_report", "caller-forged")));
        forgedReport.put("body", body);
        forgedReport.put("bodyDigest", sha256(body));
        return forgedReport;
    }

    private Map<String, Object> withExtraRequestSpecField(Map<String, Object> requestSpecReport,
                                                          String key,
                                                          Object value) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(requestSpecReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> requestSpec = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("requestSpec")
        );
        requestSpec.put(key, value);
        forgedReport.put("requestSpec", requestSpec);
        forgedReport.put("requestSpecDigest", sha256(requestSpec));
        return forgedReport;
    }

    private Map<String, Object> withNestedProtectedContextInRequestBody(Map<String, Object> requestSpecReport,
                                                                        Map<String, Object> bodyReport) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(requestSpecReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> requestSpec = new LinkedHashMap<>((Map<String, Object>) forgedReport.get("requestSpec"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new LinkedHashMap<>((Map<String, Object>) bodyReport.get("body"));
        String bodyDigest = text(bodyReport.get("bodyDigest"));
        requestSpec.put("body", body);
        requestSpec.put("bodyDigest", bodyDigest);
        String requestSpecDigest = sha256(requestSpec);
        forgedReport.put("bodyDigest", bodyDigest);
        forgedReport.put("requestSpec", requestSpec);
        forgedReport.put("requestSpecDigest", requestSpecDigest);
        return forgedReport;
    }

    private Map<String, Object> withForgedDigestConsistentHandoff(Map<String, Object> handoffReport,
                                                                  Map<String, Object> bodyReport,
                                                                  Map<String, Object> requestSpecReport) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(handoffReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> handoffPlan = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("executionHandoffPlan")
        );
        handoffPlan.put("bodyDigest", text(bodyReport.get("bodyDigest")));
        handoffPlan.put("requestSpecDigest", text(requestSpecReport.get("requestSpecDigest")));
        forgedReport.put("sourceBodyDigest", text(bodyReport.get("bodyDigest")));
        forgedReport.put("sourceRequestSpecDigest", text(requestSpecReport.get("requestSpecDigest")));
        forgedReport.put("executionHandoffPlan", handoffPlan);
        forgedReport.put("handoffDigest", sha256(handoffPlan));
        return forgedReport;
    }

    private Map<String, Object> withForgedIdempotencyKey(Map<String, Object> handoffReport,
                                                         String forgedIdempotencyKey) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(handoffReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> handoffPlan = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("executionHandoffPlan")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> idempotency = new LinkedHashMap<>(
            (Map<String, Object>) handoffPlan.get("idempotency")
        );
        idempotency.put("key", forgedIdempotencyKey);
        handoffPlan.put("idempotency", idempotency);
        forgedReport.put("idempotencyKey", forgedIdempotencyKey);
        forgedReport.put("executionHandoffPlan", handoffPlan);
        forgedReport.put("handoffDigest", sha256(handoffPlan));
        return forgedReport;
    }

    private Map<String, Object> withExtraHandoffPlanField(Map<String, Object> handoffReport,
                                                          String key,
                                                          Object value) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(handoffReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> handoffPlan = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("executionHandoffPlan")
        );
        handoffPlan.put(key, value);
        forgedReport.put("executionHandoffPlan", handoffPlan);
        forgedReport.put("handoffDigest", sha256(handoffPlan));
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

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Map<String, Object> codeReleaseSwitchRuntimeSourceGuardReport(Map<String, Object> audit) {
        Map<String, Object> principal = trustedPrincipalSnapshot(audit);
        return NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                audit,
                principal,
                codeReleaseSwitchRuntimeBindingReport(audit, principal),
                Map.of()
            )
        );
    }

    private Map<String, Object> codeReleaseSwitchRuntimeBindingReport(Map<String, Object> audit,
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

    private Map<String, Object> trustedPrincipalSnapshot(Map<String, Object> audit) {
        return Map.of(
            "authoritative", true,
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true,
            "organizationId", audit.get("organizationId"),
            "userId", audit.get("userId"),
            "username", "alice"
        );
    }

    private Map<String, Object> openGate() {
        return Map.of(
            "gateState", NimCreateStateMachineSupport.READY_GATE_STATE,
            "allowedToCreateNow", true,
            "trustedPolicySnapshot", Map.of(
                "snapshotState", NimCreateStateMachineSupport.TRUSTED_POLICY_PASSED,
                "authoritative", true,
                "protectedFromCallerParams", true
            ),
            "futureWritePath", Map.of(
                "directUseOfPreviewAllowed", false,
                "fallbackAllowedFromPreflight", false
            )
        );
    }

    private Map<String, Object> completePreview() {
        Map<String, Object> bodyDraft = new LinkedHashMap<>();
        bodyDraft.put("name", "llama-nim");
        bodyDraft.put("displayName", "llama-nim");
        bodyDraft.put("image", "nvcr.io/nim/llama:1.0");
        bodyDraft.put("templateId", 88);
        bodyDraft.put("cpuLimits", 2500);
        bodyDraft.put("cpuRequests", 2500);
        bodyDraft.put("memLimits", 12288);
        bodyDraft.put("memRequests", 12288);
        bodyDraft.put("gpuPercentLimits", 0);
        bodyDraft.put("gpuMemLimits", 0);
        bodyDraft.put("replicas", 1);
        bodyDraft.put("enableSecondNetwork", true);
        bodyDraft.put("organizationId", "caller-forged");
        bodyDraft.put("token", "");
        return Map.of(
            "safeToPost", false,
            "previewOnly", true,
            "bodyComplete", true,
            "bodyDraft", bodyDraft
        );
    }

    private Map<String, Object> completeAuditContext() {
        return Map.ofEntries(
            entry("auditPrepared", true),
            entry("auditEventType", "NIM_CREATE_REQUEST"),
            entry("requestId", "req-1"),
            entry("conversationId", "conv-1"),
            entry("userId", "user-1"),
            entry("organizationId", "100002"),
            entry("targetTool", "nim_create"),
            entry("writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE),
            entry("displayName", "llama-nim"),
            entry("image", "nvcr.io/nim/llama:1.0"),
            entry("templateId", "88"),
            entry("secretRedactionApplied", true),
            entry("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY)
        );
    }

    private Map<String, Object> durableAuditReceipt(Map<String, Object> audit) {
        return Map.ofEntries(
            entry("auditReceiptPrepared", true),
            entry("receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS),
            entry("storageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE),
            entry("durable", true),
            entry("realStorageTouched", true),
            entry("releaseEligible", true),
            entry("eventDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM),
            entry("eventDigest", "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"),
            entry("receiptId", "nim-audit-durable-req-1"),
            entry("auditEventType", audit.get("auditEventType")),
            entry("requestId", audit.get("requestId")),
            entry("conversationId", audit.get("conversationId")),
            entry("userId", audit.get("userId")),
            entry("organizationId", audit.get("organizationId")),
            entry("targetTool", audit.get("targetTool")),
            entry("writeBodyProvenance", audit.get("writeBodyProvenance"))
        );
    }

    private void assertHasBlocker(List<Map<String, Object>> blockers, String code) {
        assertTrue(blockers.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected blocker code: " + code + ", actual blockers: " + blockers);
    }
}
