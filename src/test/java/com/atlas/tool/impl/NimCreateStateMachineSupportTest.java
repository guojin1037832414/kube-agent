package com.atlas.tool.impl;

import com.atlas.hitl.HitlConfirmation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 创建写入状态机契约测试。
 *
 * <p>这些用例不访问 kube-manager，也不会调用真实 POST。测试目标是把未来 {@code nim_create}
 * 的安全前置条件固化下来：没有可信策略、服务端 HITL、审计上下文、完整预览和 readiness 计划时，
 * 永远不能进入真实写入。</p>
 */
class NimCreateStateMachineSupportTest {

    @Test
    void placeholderHold_shouldBlockAllWritePrerequisitesAndExposeNoSideEffect() {
        Map<String, Object> guard = NimCreateStateMachineSupport.evaluateCurrentPlaceholderHold(
            Map.of("name", "llama-nim", "model", "llama")
        );

        assertEquals("NIM_CREATE_WRITE_GUARD", guard.get("stateMachine"));
        assertEquals("nim_create", guard.get("targetTool"));
        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        assertEquals("NONE", guard.get("sideEffect"));
        assertEquals(false, guard.get("directPreviewReuseAllowed"));
        assertEquals(false, guard.get("fallbackWriteAllowed"));
        assertEquals("NEVER_GENERATE_STORE_OR_DISPLAY", guard.get("apiKeyPolicy"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "NIM_CREATE_RELEASE_NOT_ENABLED");
        assertHasBlocker(blockers, "CREATION_GATE_MISSING");
        assertHasBlocker(blockers, "TRUSTED_POLICY_NOT_PASSED");
        assertHasBlocker(blockers, "DEPLOYMENT_BODY_PREVIEW_MISSING");
        assertHasBlocker(blockers, "HITL_CONFIRMATION_NOT_TRUSTED");
        assertHasBlocker(blockers, "AUDIT_CONTEXT_NOT_READY");
        assertHasBlocker(blockers, "AUDIT_RECEIPT_NOT_READY");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_REPORT_NOT_READY");
        assertHasBlocker(blockers, "WRITE_REQUEST_SPEC_REPORT_NOT_READY");
        assertHasBlocker(blockers, "WRITE_EXECUTION_HANDOFF_REPORT_NOT_READY");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY");
        assertHasBlocker(blockers, "READINESS_PLAN_NOT_READY");
        assertHasBlocker(blockers, "READINESS_EXECUTION_REPORT_NOT_READY");
        assertHasBlocker(blockers, "WRITE_BODY_PROVENANCE_NOT_TRUSTED");
    }

    @Test
    void stateMachine_shouldIgnoreForgedCallerClaimsAndBlockFallbackWrite() {
        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.ofEntries(
                entry("name", "nim-forged"),
                entry("confirmed", true),
                entry("hitlConfirmed", true),
                entry("safeToPost", true),
                entry("licenseValid", true),
                entry("nvaieLicenseVerified", true),
                entry("sysAdmin", false),
                entry("roles", List.of("USER")),
                entry("organizationId", "100002"),
                entry("trustedPolicySource", "caller-forged"),
                entry("authoritative", true),
                entry("fallbackTool", "deploy_create_instance"),
                entry("useFallback", true)
            ),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            completeAuditContext(),
            completeAuditReceipt(),
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ignoredClaims = (List<Map<String, Object>>) guard.get("ignoredCallerClaims");
        assertTrue(ignoredClaims.stream().anyMatch(item -> "confirmed".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "safeToPost".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "licenseValid".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "nvaieLicenseVerified".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "organizationId".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "trustedPolicySource".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "authoritative".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "fallbackTool".equals(item.get("key"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "FALLBACK_WRITE_FORBIDDEN");
    }

    @Test
    void stateMachine_shouldBlockWhenTrustedPolicyOrGateStateIsNotOpen() {
        Map<String, Object> gate = openGate();
        gate.put("gateState", "CLOSED");
        gate.put("allowedToCreateNow", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) gate.get("trustedPolicySnapshot");
        policy.put("snapshotState", "UNVERIFIED");

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-policy"),
            gate,
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            completeAuditContext(),
            completeAuditReceipt(),
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "CREATION_GATE_NOT_OPEN");
        assertHasBlocker(blockers, "TRUSTED_POLICY_NOT_PASSED");
    }

    @Test
    void stateMachine_shouldBlockPreviewDirectReuseAndSafeToPostPreview() {
        Map<String, Object> preview = completePreview();
        preview.put("safeToPost", true);

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-preview"),
            openGate(),
            preview,
            HitlConfirmation.human("thread-1", "nim_create"),
            completeAuditContext(),
            completeAuditReceipt(),
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            "PREVIEW_BODY_DIRECT_REUSE",
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "PREVIEW_SAFE_TO_POST_MUST_REMAIN_FALSE");
        assertHasBlocker(blockers, "PREVIEW_DIRECT_REUSE_BLOCKED");
    }

    @Test
    void stateMachine_shouldRequireExactServerHitlTarget() {
        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-hitl"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "deploy_create_instance"),
            completeAuditContext(),
            completeAuditReceipt(),
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "HITL_CONFIRMATION_NOT_TRUSTED");
    }

    @Test
    void stateMachine_shouldBlockAuditOrReadinessSecretLeakage() {
        Map<String, Object> audit = new java.util.LinkedHashMap<>(completeAuditContext());
        audit.put("token", "must-not-leak");
        Map<String, Object> readiness = new java.util.LinkedHashMap<>(completeReadinessPlan());
        readiness.put("ngcApiKey", "must-not-leak");

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-secret"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            completeAuditReceipt(),
            readiness,
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET");
        assertHasBlocker(blockers, "READINESS_PLAN_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void stateMachine_shouldAllowDocumentedReadinessApiKeyPlaceholder() {
        Map<String, Object> readiness = new java.util.LinkedHashMap<>(completeReadinessPlan());
        readiness.put("operatorHint", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER);
        Map<String, Object> readinessReport = new java.util.LinkedHashMap<>(completeReadinessExecutionReport());

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-placeholder"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            completeAuditContext(),
            completeAuditReceipt(),
            readiness,
            readinessReport,
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertNoBlocker(blockers, "READINESS_PLAN_CONTAINS_FORBIDDEN_SECRET");
        assertNoBlocker(blockers, "READINESS_EXECUTION_REPORT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void stateMachine_shouldRejectSharedDetectorHardenedSecretShapes() {
        Map<String, Object> audit = new java.util.LinkedHashMap<>(completeAuditContext());
        audit.put("refreshToken", "present");
        Map<String, Object> readiness = new java.util.LinkedHashMap<>(completeReadinessPlan());
        readiness.put("token", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER);
        readiness.put("diagnostics", List.of("Authorization=Bearer real-key-material"));
        readiness.put("placeholderAssignment", "token=" + NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER);

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-hardened-secret-shapes"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            completeAuditReceipt(),
            readiness,
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET");
        assertHasBlocker(blockers, "READINESS_PLAN_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void stateMachine_shouldRejectBooleanValuesUnderForbiddenSecretKeys() {
        Map<String, Object> audit = new java.util.LinkedHashMap<>(completeAuditContext());
        audit.put("token", false);

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-boolean-token"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            completeAuditReceipt(),
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void stateMachine_shouldRejectSecretMaterialAcrossWriteAndReleaseReports() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> baseReceipt = completeAuditReceipt();
        Map<String, Object> baseBodyReport = completeWriteBodyRebuildReport(audit, baseReceipt);
        Map<String, Object> baseRequestSpecReport = completeWriteRequestSpecReport(audit, baseReceipt, baseBodyReport);
        Map<String, Object> baseHandoffReport = completeWriteExecutionHandoffReport(
            audit,
            baseReceipt,
            baseBodyReport,
            baseRequestSpecReport
        );
        Map<String, Object> baseCodeSwitchReport = completeCodeReleaseSwitchContractReport(audit);
        Map<String, Object> baseSourceGuardReport = completeCodeReleaseSwitchRuntimeSourceGuardReport(audit);
        Map<String, Object> baseExecutorReport = completeDurableWriteExecutorReport(
            baseHandoffReport,
            baseRequestSpecReport,
            baseCodeSwitchReport,
            baseSourceGuardReport
        );

        Map<String, Object> receipt = new java.util.LinkedHashMap<>(baseReceipt);
        receipt.put("Authorization", "Bearer real-key-material");
        Map<String, Object> bodyReport = new java.util.LinkedHashMap<>(baseBodyReport);
        bodyReport.put("diagnostics", List.of("Authorization=Bearer real-key-material"));
        Map<String, Object> requestSpecReport = new java.util.LinkedHashMap<>(baseRequestSpecReport);
        requestSpecReport.put("refreshToken", "present");
        Map<String, Object> handoffReport = new java.util.LinkedHashMap<>(baseHandoffReport);
        handoffReport.put("token", false);
        Map<String, Object> codeSwitchReport = new java.util.LinkedHashMap<>(baseCodeSwitchReport);
        codeSwitchReport.put("placeholderAssignment", "token=" + NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER);
        Map<String, Object> sourceGuardReport = new java.util.LinkedHashMap<>(baseSourceGuardReport);
        sourceGuardReport.put("customAuthorization", "present");
        Map<String, Object> executorReport = new java.util.LinkedHashMap<>(baseExecutorReport);
        executorReport.put("registry.password", "present");

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-report-secret-coverage"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "AUDIT_RECEIPT_CONTAINS_FORBIDDEN_SECRET");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_REPORT_CONTAINS_FORBIDDEN_SECRET");
        assertHasBlocker(blockers, "WRITE_REQUEST_SPEC_REPORT_CONTAINS_FORBIDDEN_SECRET");
        assertHasBlocker(blockers, "WRITE_EXECUTION_HANDOFF_REPORT_CONTAINS_FORBIDDEN_SECRET");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_CONTRACT_REPORT_CONTAINS_FORBIDDEN_SECRET");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_CONTAINS_FORBIDDEN_SECRET");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void stateMachine_shouldRequireDurableWriteExecutorReportAfterHandoffBeforeFutureWrite() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-ready"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        assertEquals(true, guard.get("durableWriteExecutorReportRequired"));
        assertEquals(true, guard.get("codeReleaseSwitchRuntimeBindingRequired"));
        assertEquals(false, guard.get("codeReleaseSwitchRuntimeBindingInstalled"));
        assertEquals(false, guard.get("legacyNimCreateReleasedBooleanAuthoritative"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY");
    }

    @Test
    void stateMachine_shouldRejectDigestConsistentRequestSpecBodyWithNestedProtectedContext() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> trustedBodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> bodyReport = withNestedProtectedContextInBodyReport(trustedBodyReport);
        Map<String, Object> requestSpecReport = withNestedProtectedContextInRequestBody(
            completeWriteRequestSpecReport(audit, receipt, trustedBodyReport),
            bodyReport
        );

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-protected-context-request-spec"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_REPORT_CONTRACT_INVALID");
        assertHasBlocker(blockers, "WRITE_REQUEST_SPEC_REPORT_CONTRACT_INVALID");
    }

    @Test
    void stateMachine_shouldAcceptExecutorShellShapeButKeepImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = completeCodeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = completeCodeReleaseSwitchRuntimeSourceGuardReport(audit);
        Map<String, Object> executorReport = completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            codeSwitchReport,
            sourceGuardReport
        );

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-executor-shell"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        assertEquals(true, guard.get("codeReleaseSwitchRuntimeBindingRequired"));
        assertEquals(true, guard.get("codeReleaseSwitchContractReportRequired"));
        assertEquals(true, guard.get("codeReleaseSwitchRuntimeSourceGuardReportRequired"));
        assertEquals(false, guard.get("codeReleaseSwitchRuntimeBindingInstalled"));
        assertEquals(false, guard.get("codeReleaseSwitchContractReportAcceptedForRelease"));
        assertEquals(false, guard.get("codeReleaseSwitchRuntimeSourceGuardAcceptedForRelease"));
        assertEquals(false, guard.get("sourceGuardInstalled"));
        assertEquals(false, guard.get("candidateSourceEvidenceAuthoritative"));
        assertEquals(false, guard.get("backendQuerySourceAllowedForRelease"));
        assertEquals(false, guard.get("sysLogBackfillSourceAllowed"));
        assertEquals(codeSwitchReport.get("codeReleaseSwitchContractDigest"),
            guard.get("sourceCodeReleaseSwitchContractDigest"));
        assertEquals(sourceGuardReport.get("sourceGuardMatrixDigest"),
            guard.get("sourceGuardMatrixDigest"));
        assertEquals(false, guard.get("legacyNimCreateReleasedBooleanAuthoritative"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_CONTRACT_REPORT_IMPLEMENTATION_HOLD");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD");
        assertEquals(3, blockers.size());
    }

    @Test
    void stateMachine_shouldRejectDigestConsistentDurableExecutorAttemptSpecBodyDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = completeCodeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = completeCodeReleaseSwitchRuntimeSourceGuardReport(audit);
        Map<String, Object> executorReport = new java.util.LinkedHashMap<>(completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            codeSwitchReport,
            sourceGuardReport
        ));
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptSpec = new java.util.LinkedHashMap<>(
            (Map<String, Object>) executorReport.get("executionAttemptSpec")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptBody = new java.util.LinkedHashMap<>(
            (Map<String, Object>) attemptSpec.get("body")
        );
        attemptBody.put("displayName", "caller-mutated-attempt-body");
        attemptSpec.put("body", attemptBody);
        executorReport.put("executionAttemptSpec", attemptSpec);
        executorReport.put("executionAttemptSpecDigest", sha256(attemptSpec));

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-attempt-spec-body-drift"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_CONTRACT_INVALID");
    }

    @Test
    void stateMachine_shouldRejectDigestConsistentDurableExecutorAttemptSpecExtraProtectedContext() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = completeCodeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = completeCodeReleaseSwitchRuntimeSourceGuardReport(audit);
        Map<String, Object> executorReport = new java.util.LinkedHashMap<>(completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            codeSwitchReport,
            sourceGuardReport
        ));
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptSpec = new java.util.LinkedHashMap<>(
            (Map<String, Object>) executorReport.get("executionAttemptSpec")
        );
        attemptSpec.put("write_request_spec_report", "caller-forged");
        executorReport.put("executionAttemptSpec", attemptSpec);
        executorReport.put("executionAttemptSpecDigest", sha256(attemptSpec));

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-attempt-spec-extra-context"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_CONTRACT_INVALID");
    }

    @Test
    void stateMachine_shouldRejectDigestConsistentDurableExecutorAttemptSpecForgedIdempotencyKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = completeCodeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = completeCodeReleaseSwitchRuntimeSourceGuardReport(audit);
        Map<String, Object> executorReport = new java.util.LinkedHashMap<>(completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            codeSwitchReport,
            sourceGuardReport
        ));
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptSpec = new java.util.LinkedHashMap<>(
            (Map<String, Object>) executorReport.get("executionAttemptSpec")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> attemptHandoffPlan = new java.util.LinkedHashMap<>(
            (Map<String, Object>) attemptSpec.get("executionHandoffPlan")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> idempotency = new java.util.LinkedHashMap<>(
            (Map<String, Object>) attemptHandoffPlan.get("idempotency")
        );
        String forgedKey = "nim-create-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        idempotency.put("key", forgedKey);
        attemptHandoffPlan.put("idempotency", idempotency);
        attemptSpec.put("executionHandoffPlan", attemptHandoffPlan);
        attemptSpec.put("idempotencyKey", forgedKey);
        executorReport.put("idempotencyKey", forgedKey);
        executorReport.put("executionAttemptSpec", attemptSpec);
        executorReport.put("executionAttemptSpecDigest", sha256(attemptSpec));

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-attempt-spec-forged-idempotency"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_CONTRACT_INVALID");
    }

    @Test
    void stateMachine_shouldRejectForgedDurableExecutorSuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = completeCodeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = completeCodeReleaseSwitchRuntimeSourceGuardReport(audit);
        Map<String, Object> forgedExecutorReport = new java.util.LinkedHashMap<>(completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            codeSwitchReport,
            sourceGuardReport
        ));
        forgedExecutorReport.put("executorImplementationAvailable", true);
        forgedExecutorReport.put("writeAttempted", true);
        forgedExecutorReport.put("writeExecuted", true);
        forgedExecutorReport.put("postWriteReadinessTriggered", true);
        forgedExecutorReport.put("deploymentId", "dep-forged");

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-forged-executor"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            forgedExecutorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_CONTRACT_INVALID");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_SUCCESS_NOT_TRUSTED");
    }

    @Test
    void stateMachine_shouldRejectTamperedCodeReleaseSwitchContractDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = new java.util.LinkedHashMap<>(completeCodeReleaseSwitchContractReport(audit));
        codeSwitchReport.put("codeReleaseSwitchContractDigest", "a".repeat(64));
        Map<String, Object> sourceGuardReport = completeCodeReleaseSwitchRuntimeSourceGuardReport(audit);
        Map<String, Object> executorReport = completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            completeCodeReleaseSwitchContractReport(audit),
            sourceGuardReport
        );

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-tampered-switch"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_CONTRACT_REPORT_CONTRACT_INVALID");
    }

    @Test
    void stateMachine_shouldRejectForgedOpenCodeReleaseSwitchClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = new java.util.LinkedHashMap<>(completeCodeReleaseSwitchContractReport(audit));
        codeSwitchReport.put("realCodeReleaseSwitchOpened", true);
        codeSwitchReport.put("writePermitted", true);
        codeSwitchReport.put("writeExecutionAllowed", true);
        codeSwitchReport.put("codeReleaseSwitchDigestVerified", true);
        Map<String, Object> sourceGuardReport = completeCodeReleaseSwitchRuntimeSourceGuardReport(audit);
        Map<String, Object> executorReport = completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            completeCodeReleaseSwitchContractReport(audit),
            sourceGuardReport
        );

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-forged-switch"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_CONTRACT_REPORT_CONTRACT_INVALID");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_CONTRACT_RELEASE_CLAIM_NOT_TRUSTED");
    }

    @Test
    void stateMachine_shouldRequireCodeReleaseSwitchRuntimeSourceGuardReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = completeCodeReleaseSwitchContractReport(audit);

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-missing-source-guard"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            Map.of(),
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        assertEquals(true, guard.get("codeReleaseSwitchRuntimeSourceGuardReportRequired"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_READY");
    }

    @Test
    void stateMachine_shouldRejectTamperedRuntimeSourceGuardDigest() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = completeCodeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = new java.util.LinkedHashMap<>(
            completeCodeReleaseSwitchRuntimeSourceGuardReport(audit)
        );
        sourceGuardReport.put("sourceGuardMatrixDigest", "b".repeat(64));
        Map<String, Object> executorReport = completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            codeSwitchReport,
            completeCodeReleaseSwitchRuntimeSourceGuardReport(audit)
        );

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-tampered-source-guard"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_CONTRACT_INVALID");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_CONTRACT_INVALID");
    }

    @Test
    void stateMachine_shouldRejectForgedRuntimeSourceGuardReleaseClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = completeCodeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = new java.util.LinkedHashMap<>(
            completeCodeReleaseSwitchRuntimeSourceGuardReport(audit)
        );
        sourceGuardReport.put("sourceGuardInstalled", true);
        sourceGuardReport.put("llmJsonSourceAllowed", true);
        sourceGuardReport.put("backendQuerySourceAllowedForRelease", true);
        sourceGuardReport.put("deploymentId", "dep-forged");
        Map<String, Object> executorReport = completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            codeSwitchReport,
            completeCodeReleaseSwitchRuntimeSourceGuardReport(audit)
        );

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-forged-source-guard"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_CONTRACT_INVALID");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_RELEASE_CLAIM_NOT_TRUSTED");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD");
    }

    @Test
    void stateMachine_shouldRejectRuntimeSourceGuardSecretLeakage() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = completeAuditReceipt();
        Map<String, Object> bodyReport = completeWriteBodyRebuildReport(audit, receipt);
        Map<String, Object> requestSpecReport = completeWriteRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = completeWriteExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);
        Map<String, Object> codeSwitchReport = completeCodeReleaseSwitchContractReport(audit);
        Map<String, Object> sourceGuardReport = new java.util.LinkedHashMap<>(
            completeCodeReleaseSwitchRuntimeSourceGuardReport(audit)
        );
        sourceGuardReport.put("Authorization", "Bearer real-key-material");
        Map<String, Object> executorReport = completeDurableWriteExecutorReport(
            handoffReport,
            requestSpecReport,
            codeSwitchReport,
            completeCodeReleaseSwitchRuntimeSourceGuardReport(audit)
        );

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-secret-source-guard"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            bodyReport,
            requestSpecReport,
            handoffReport,
            codeSwitchReport,
            sourceGuardReport,
            executorReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(false, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_CONTAINS_FORBIDDEN_SECRET");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD");
    }

    @Test
    void stateMachine_shouldRejectMockOrMismatchedAuditReceipt() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> mockReceipt = NimCreateAuditWriterSupport.buildMockReceipt(audit);

        Map<String, Object> mockGuard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-mock-receipt"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            mockReceipt,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", mockGuard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mockBlockers = (List<Map<String, Object>>) mockGuard.get("blockedBy");
        assertHasBlocker(mockBlockers, "AUDIT_RECEIPT_NOT_DURABLE");

        Map<String, Object> mismatchedReceipt = new java.util.LinkedHashMap<>(completeAuditReceipt());
        mismatchedReceipt.put("organizationId", "100003");
        Map<String, Object> mismatchedGuard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-mismatch-receipt"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            mismatchedReceipt,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", mismatchedGuard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mismatchedBlockers = (List<Map<String, Object>>) mismatchedGuard.get("blockedBy");
        assertHasBlocker(mismatchedBlockers, "AUDIT_RECEIPT_NOT_DURABLE");
    }

    @Test
    void stateMachine_shouldRequireReadyReadinessExecutionReportForFutureWrite() {
        Map<String, Object> pendingReport = new java.util.LinkedHashMap<>(completeReadinessExecutionReport());
        pendingReport.put("state", "PENDING");
        pendingReport.put("ready", false);
        pendingReport.put("pendingBy", List.of(Map.of("code", "NIM_HEALTH_NOT_LIVE")));
        @SuppressWarnings("unchecked")
        Map<String, Object> health = (Map<String, Object>) pendingReport.get("health");
        health.put("state", "PENDING_NOT_LIVE");
        health.put("live", false);
        @SuppressWarnings("unchecked")
        Map<String, Object> nextPoll = (Map<String, Object>) pendingReport.get("nextPoll");
        nextPoll.put("prepared", true);

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-pending-readiness"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            completeAuditContext(),
            completeAuditReceipt(),
            completeReadinessPlan(),
            pendingReport,
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "READINESS_EXECUTION_REPORT_CONTRACT_INVALID");
        assertHasBlocker(blockers, "READINESS_EXECUTION_REPORT_NOT_READY");
    }

    @Test
    void stateMachine_shouldRejectReadinessExecutionReportWithSecretsOrBlockingState() {
        Map<String, Object> rejectedReport = new java.util.LinkedHashMap<>(completeReadinessExecutionReport());
        rejectedReport.put("state", "REJECTED");
        rejectedReport.put("ready", false);
        rejectedReport.put("Authorization", "Bearer real-key-material");
        rejectedReport.put("blockedBy", List.of(Map.of("code", "READINESS_CONTAINS_FORBIDDEN_SECRET")));

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-secret-readiness"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            completeAuditContext(),
            completeAuditReceipt(),
            completeReadinessPlan(),
            rejectedReport,
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "READINESS_EXECUTION_REPORT_NOT_READY");
        assertHasBlocker(blockers, "READINESS_EXECUTION_REPORT_BLOCKED");
        assertHasBlocker(blockers, "READINESS_EXECUTION_REPORT_CONTAINS_FORBIDDEN_SECRET");
    }

    private Map<String, Object> openGate() {
        return new java.util.LinkedHashMap<>(Map.of(
            "gateState", NimCreateStateMachineSupport.READY_GATE_STATE,
            "allowedToCreateNow", true,
            "trustedPolicySnapshot", new java.util.LinkedHashMap<>(Map.of(
                "snapshotState", NimCreateStateMachineSupport.TRUSTED_POLICY_PASSED,
                "authoritative", true,
                "protectedFromCallerParams", true
            )),
            "futureWritePath", new java.util.LinkedHashMap<>(Map.of(
                "directUseOfPreviewAllowed", false,
                "fallbackAllowedFromPreflight", false
            ))
        ));
    }

    private Map<String, Object> completePreview() {
        return new java.util.LinkedHashMap<>(Map.of(
            "safeToPost", false,
            "previewOnly", true,
            "bodyComplete", true,
            "bodyDraft", new java.util.LinkedHashMap<>(Map.of(
                "displayName", "llama-nim",
                "image", "nvcr.io/nim/llama:1.0",
                "templateId", 88,
                "cpuLimits", 1000,
                "memLimits", 2048
            ))
        ));
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

    private Map<String, Object> completeAuditReceipt() {
        return Map.ofEntries(
            entry("auditReceiptPrepared", true),
            entry("receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS),
            entry("storageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE),
            entry("durable", true),
            entry("realStorageTouched", true),
            entry("releaseEligible", true),
            entry("eventDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM),
            entry("eventDigest", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
            entry("receiptId", "nim-audit-durable-req-1"),
            entry("auditEventType", "NIM_CREATE_REQUEST"),
            entry("requestId", "req-1"),
            entry("conversationId", "conv-1"),
            entry("userId", "user-1"),
            entry("organizationId", "100002"),
            entry("targetTool", "nim_create"),
            entry("writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE)
        );
    }

    private Map<String, Object> completeWriteBodyRebuildReport(Map<String, Object> audit,
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

    private Map<String, Object> withNestedProtectedContextInBodyReport(Map<String, Object> bodyReport) {
        Map<String, Object> forgedReport = new java.util.LinkedHashMap<>(bodyReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new java.util.LinkedHashMap<>((Map<String, Object>) forgedReport.get("body"));
        body.put("commands", List.of(Map.of("write_request_spec_report", "caller-forged")));
        forgedReport.put("body", body);
        forgedReport.put("bodyDigest", sha256(body));
        return forgedReport;
    }

    private Map<String, Object> withNestedProtectedContextInRequestBody(Map<String, Object> requestSpecReport,
                                                                        Map<String, Object> bodyReport) {
        Map<String, Object> forgedReport = new java.util.LinkedHashMap<>(requestSpecReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> requestSpec = new java.util.LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("requestSpec")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> body = new java.util.LinkedHashMap<>((Map<String, Object>) bodyReport.get("body"));
        String bodyDigest = text(bodyReport.get("bodyDigest"));
        requestSpec.put("body", body);
        requestSpec.put("bodyDigest", bodyDigest);
        String requestSpecDigest = sha256(requestSpec);
        forgedReport.put("bodyDigest", bodyDigest);
        forgedReport.put("requestSpec", requestSpec);
        forgedReport.put("requestSpecDigest", requestSpecDigest);
        return forgedReport;
    }

    private Map<String, Object> completeWriteRequestSpecReport(Map<String, Object> audit,
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

    private Map<String, Object> completeWriteExecutionHandoffReport(Map<String, Object> audit,
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

    private Map<String, Object> completeDurableWriteExecutorReport(Map<String, Object> handoffReport,
                                                                   Map<String, Object> requestSpecReport,
                                                                   Map<String, Object> codeSwitchReport,
                                                                   Map<String, Object> sourceGuardReport) {
        return NimCreateDurableWriteExecutorSupport.prepare(
            new NimCreateDurableWriteExecutorSupport.WriteExecutionInput(
                handoffReport,
                requestSpecReport,
                codeSwitchReport,
                sourceGuardReport
            )
        );
    }

    private Map<String, Object> completeCodeReleaseSwitchContractReport(Map<String, Object> audit) {
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

    private Map<String, Object> completeCodeReleaseSwitchRuntimeSourceGuardReport(Map<String, Object> audit) {
        Map<String, Object> principal = trustedPrincipalSnapshot(audit);
        return NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.RuntimeSourceGuardInput(
                audit,
                principal,
                completeCodeReleaseSwitchRuntimeBindingReport(audit, principal),
                Map.of()
            )
        );
    }

    private Map<String, Object> completeCodeReleaseSwitchRuntimeBindingReport(Map<String, Object> audit,
                                                                              Map<String, Object> principal) {
        return NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.plan(
            new NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport
                .CodeReleaseSwitchRuntimeBindingInput(
                audit,
                principal,
                completeCodeReleaseSwitchContractReport(audit, principal),
                Map.of(),
                Map.of()
            )
        );
    }

    private Map<String, Object> completeCodeReleaseSwitchContractReport(Map<String, Object> audit,
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

    private Map<String, Object> completeReadinessPlan() {
        return Map.of(
            "readinessPollingPrepared", true,
            "pollOnly", true,
            "apiKeyPlaceholderOnly", true,
            "apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY,
            "targets", List.of("deployment", "service", "nim-health", "nim-models"),
            "steps", List.of(
                Map.of("target", "deployment", "method", "GET", "endpoint", "/api/{orgId}/deployment"),
                Map.of("target", "service", "method", "EXTRACT_FROM_DEPLOYMENT_RESPONSE", "endpoint", "deployment.entranceMap.http|http1"),
                Map.of("target", "nim-health", "method", "GET", "endpoint", "{nimApiBasePath}/v1/health/live"),
                Map.of("target", "nim-models", "method", "GET", "endpoint", "{nimApiBasePath}/v1/models")
            )
        );
    }

    private Map<String, Object> completeReadinessExecutionReport() {
        return Map.ofEntries(
            entry("readinessExecutor", NimCreateReadinessExecutorSupport.EXECUTOR_NAME),
            entry("executionMode", "OFFLINE_CONTRACT_EVALUATION"),
            entry("sideEffect", "NONE"),
            entry("readOnly", true),
            entry("pollOnly", true),
            entry("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY),
            entry("apiKeyPlaceholderOnly", true),
            entry("apiKeyPlaceholder", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER),
            entry("state", "READY"),
            entry("ready", true),
            entry("deployment", new java.util.LinkedHashMap<>(Map.of(
                "state", "MATCHED",
                "matched", true,
                "matchCount", 1
            ))),
            entry("service", new java.util.LinkedHashMap<>(Map.of(
                "state", "SERVICE_URL_READY",
                "serviceUrlReady", true,
                "entranceSource", "http",
                "nimApiBasePath", "/nim"
            ))),
            entry("health", new java.util.LinkedHashMap<>(Map.of(
                "state", "LIVE",
                "live", true
            ))),
            entry("models", Map.of(
                "state", "MODEL_FOUND",
                "modelName", "llama"
            )),
            entry("blockedBy", List.of()),
            entry("pendingBy", List.of()),
            entry("nextPoll", new java.util.LinkedHashMap<>(Map.of(
                "prepared", false,
                "pollOnly", true,
                "afterSeconds", 0,
                "nextAttempt", 3,
                "maxAttempts", 120
            ))),
            entry("forbiddenActionsEnforced", true)
        );
    }

    private void assertHasBlocker(List<Map<String, Object>> blockers, String code) {
        assertTrue(blockers.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected blocker code: " + code + ", actual blockers: " + blockers);
    }

    private void assertNoBlocker(List<Map<String, Object>> blockers, String code) {
        assertFalse(blockers.stream().anyMatch(item -> code.equals(item.get("code"))),
            "unexpected blocker code: " + code + ", actual blockers: " + blockers);
    }
}
