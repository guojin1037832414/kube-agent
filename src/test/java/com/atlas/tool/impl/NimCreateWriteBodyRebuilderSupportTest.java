package com.atlas.tool.impl;

import com.atlas.hitl.HitlConfirmation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 创建受控写入 body 重建契约测试。
 *
 * <p>本测试不调用 kube-manager、不执行 POST，只锁定未来写链的 DeploymentDTO 必须从已审计状态重建，
 * 且必须绑定 durable audit receipt，不能直接复用 preview bodyDraft 或携带密钥/上下文字段。</p>
 */
class NimCreateWriteBodyRebuilderSupportTest {

    @Test
    void rebuilder_shouldBuildWhitelistedBodyFromAuditedStateWithoutNetworkAccess() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);

        Map<String, Object> report = NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                completePreview(),
                audit,
                receipt
            )
        );

        assertEquals(NimCreateWriteBodyRebuilderSupport.REBUILDER_NAME, report.get("writeBodyRebuilder"));
        assertEquals(NimCreateWriteBodyRebuilderSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("writeBodyPrepared"));
        assertEquals("POST", report.get("httpMethod"));
        assertEquals("POST /api/{orgId}/deployment", report.get("backendEndpoint"));
        assertEquals("100002", report.get("organizationId"));
        assertEquals(false, report.get("directPreviewReuseAllowed"));
        assertEquals(false, report.get("previewBodyReferenceUsed"));
        assertEquals(true, report.get("fieldWhitelistApplied"));
        assertEquals(true, report.get("protectedContextStripped"));
        assertEquals(false, report.get("releaseCredential"));
        assertEquals(receipt.get("receiptId"), report.get("sourceAuditReceiptId"));
        assertEquals(receipt.get("eventDigest"), report.get("sourceAuditEventDigest"));
        assertEquals(NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM, report.get("bodyDigestAlgorithm"));
        assertTrue(report.get("bodyDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) report.get("body");
        assertEquals("llama-nim", body.get("name"));
        assertEquals("llama-nim", body.get("displayName"));
        assertEquals("nvcr.io/nim/llama:1.0", body.get("image"));
        assertEquals(88, body.get("templateId"));
        assertEquals(2500, body.get("cpuLimits"));
        assertEquals(2500, body.get("cpuRequests"));
        assertEquals(12288, body.get("memLimits"));
        assertEquals(12288, body.get("memRequests"));
        assertEquals(1, body.get("replicas"));
        assertEquals(true, body.get("enableSecondNetwork"));
        assertFalse(body.containsKey("organizationId"));
        assertFalse(body.containsKey("orgId"));
        assertFalse(body.containsKey("token"));
        assertFalse(body.containsKey("ngcApiKey"));
        assertFalse(body.containsKey("ignoredCallerField"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertTrue(blockers.isEmpty());
    }

    @Test
    void stateMachine_shouldRequireWriteBodyRebuildReportBeforeFutureWrite() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-no-body-report"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            durableAuditReceipt(audit),
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        assertEquals(true, guard.get("writeBodyRebuildRequired"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_REPORT_NOT_READY");
    }

    @Test
    void stateMachine_shouldAcceptBodyRebuildReportOnlyWhenBoundToAuditReceipt() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> receipt = durableAuditReceipt(audit);
        Map<String, Object> bodyReport = NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                completePreview(),
                audit,
                receipt
            )
        );
        Map<String, Object> requestSpecReport = writeRequestSpecReport(audit, receipt, bodyReport);
        Map<String, Object> handoffReport = writeExecutionHandoffReport(audit, receipt, bodyReport, requestSpecReport);

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
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY");

        Map<String, Object> mismatchedBodyReport = new java.util.LinkedHashMap<>(bodyReport);
        mismatchedBodyReport.put("sourceAuditReceiptId", "nim-audit-durable-other");
        Map<String, Object> mismatchGuard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-mismatch"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            receipt,
            mismatchedBodyReport,
            requestSpecReport,
            handoffReport,
            completeReadinessPlan(),
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", mismatchGuard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mismatchBlockers = (List<Map<String, Object>>) mismatchGuard.get("blockedBy");
        assertHasBlocker(mismatchBlockers, "WRITE_BODY_REBUILD_REPORT_CONTRACT_INVALID");
    }

    @Test
    void rebuilder_shouldRejectPreviewDirectPostSecretLeakageAndNonDurableReceipt() {
        Map<String, Object> preview = new java.util.LinkedHashMap<>(completePreview());
        preview.put("safeToPost", true);
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyDraft = new java.util.LinkedHashMap<>((Map<String, Object>) preview.get("bodyDraft"));
        bodyDraft.put("ngcApiKey", "must-not-leak");
        preview.put("bodyDraft", bodyDraft);

        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> report = NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                preview,
                audit,
                NimCreateAuditWriterSupport.buildMockReceipt(audit)
            )
        );

        assertEquals(false, report.get("writeBodyPrepared"));
        assertEquals("", report.get("bodyDigest"));
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) report.get("body");
        assertTrue(body.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DEPLOYMENT_BODY_PREVIEW_NOT_REBUILDABLE");
        assertHasBlocker(blockers, "AUDIT_RECEIPT_NOT_BOUND_FOR_BODY_REBUILD");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void rebuilder_shouldRejectAuthorizationKeyEvenWithPlainTextValue() {
        Map<String, Object> preview = new java.util.LinkedHashMap<>(completePreview());
        preview.put("Authorization", "present");
        Map<String, Object> audit = completeAuditContext();

        Map<String, Object> report = NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                preview,
                audit,
                durableAuditReceipt(audit)
            )
        );

        assertEquals(false, report.get("writeBodyPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void rebuilder_shouldRejectTokenBooleanToLockTextValuePolicy() {
        Map<String, Object> preview = new java.util.LinkedHashMap<>(completePreview());
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyDraft = new java.util.LinkedHashMap<>((Map<String, Object>) preview.get("bodyDraft"));
        bodyDraft.put("token", false);
        preview.put("bodyDraft", bodyDraft);
        Map<String, Object> audit = completeAuditContext();

        Map<String, Object> report = NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                preview,
                audit,
                durableAuditReceipt(audit)
            )
        );

        assertEquals(false, report.get("writeBodyPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void rebuilder_shouldRejectForbiddenKeyCollectionToAvoidStrictPolicyRegression() {
        Map<String, Object> preview = new java.util.LinkedHashMap<>(completePreview());
        preview.put("apiKey", Map.of("source", "caller"));
        Map<String, Object> audit = completeAuditContext();

        Map<String, Object> report = NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                preview,
                audit,
                durableAuditReceipt(audit)
            )
        );

        assertEquals(false, report.get("writeBodyPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void rebuilder_shouldRejectListCarriedSecretLikeBodyMetadata() {
        Map<String, Object> preview = new java.util.LinkedHashMap<>(completePreview());
        preview.put("diagnostics", List.of("Authorization=Bearer real-key-material"));
        Map<String, Object> audit = completeAuditContext();

        Map<String, Object> report = NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                preview,
                audit,
                durableAuditReceipt(audit)
            )
        );

        assertEquals(false, report.get("writeBodyPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void rebuilder_shouldRejectAllowlistedBodyCommandSecretLikeString() {
        Map<String, Object> preview = new java.util.LinkedHashMap<>(completePreview());
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyDraft = new java.util.LinkedHashMap<>((Map<String, Object>) preview.get("bodyDraft"));
        bodyDraft.put("commands", List.of("Authorization=Bearer real-key-material"));
        preview.put("bodyDraft", bodyDraft);
        Map<String, Object> audit = completeAuditContext();

        Map<String, Object> report = NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                preview,
                audit,
                durableAuditReceipt(audit)
            )
        );

        assertEquals(false, report.get("writeBodyPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_REBUILD_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void rebuilder_shouldRejectUnsafeBodyIdentityFields() {
        Map<String, Object> preview = new java.util.LinkedHashMap<>(completePreview());
        @SuppressWarnings("unchecked")
        Map<String, Object> bodyDraft = new java.util.LinkedHashMap<>((Map<String, Object>) preview.get("bodyDraft"));
        bodyDraft.put("name", "../admin");
        bodyDraft.put("image", "nvcr.io/nim/llama:1.0?debug=true");
        preview.put("bodyDraft", bodyDraft);

        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> report = NimCreateWriteBodyRebuilderSupport.rebuild(
            new NimCreateWriteBodyRebuilderSupport.WriteBodyRebuildInput(
                openGate(),
                preview,
                audit,
                durableAuditReceipt(audit)
            )
        );

        assertEquals(false, report.get("writeBodyPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "WRITE_BODY_NAME_UNSAFE");
        assertHasBlocker(blockers, "WRITE_BODY_IDENTITY_FIELDS_UNSAFE");
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
        Map<String, Object> bodyDraft = new java.util.LinkedHashMap<>();
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
        bodyDraft.put("enableWebSsh", true);
        bodyDraft.put("enableSecondNetwork", true);
        bodyDraft.put("autoScaleConfig", null);
        bodyDraft.put("organizationId", "caller-forged");
        bodyDraft.put("token", "");
        bodyDraft.put("ignoredCallerField", "ignored");
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
            entry("deployment", Map.of("state", "MATCHED", "matched", true, "matchCount", 1)),
            entry("service", Map.of("state", "SERVICE_URL_READY", "serviceUrlReady", true, "entranceSource", "http", "nimApiBasePath", "/nim")),
            entry("health", Map.of("state", "LIVE", "live", true)),
            entry("models", Map.of("state", "MODEL_FOUND", "modelName", "llama")),
            entry("blockedBy", List.of()),
            entry("pendingBy", List.of()),
            entry("nextPoll", Map.of("prepared", false, "pollOnly", true, "afterSeconds", 0, "nextAttempt", 1, "maxAttempts", 120)),
            entry("forbiddenActionsEnforced", true)
        );
    }

    private void assertHasBlocker(List<Map<String, Object>> blockers, String code) {
        assertTrue(blockers.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected blocker code: " + code + ", actual blockers: " + blockers);
    }
}
