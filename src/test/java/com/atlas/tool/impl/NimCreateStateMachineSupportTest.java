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
        assertHasBlocker(blockers, "READINESS_PLAN_NOT_READY");
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
    void stateMachine_shouldPermitOnlyWhenEveryFuturePrerequisiteIsPresent() {
        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-ready"),
            openGate(),
            completePreview(),
            HitlConfirmation.human("thread-1", "nim_create"),
            completeAuditContext(),
            completeAuditReceipt(),
            completeReadinessPlan(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("READY_FOR_CONTROLLED_WRITE", guard.get("state"));
        assertEquals(true, guard.get("writePermitted"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertTrue(blockers.isEmpty());
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
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", mismatchedGuard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> mismatchedBlockers = (List<Map<String, Object>>) mismatchedGuard.get("blockedBy");
        assertHasBlocker(mismatchedBlockers, "AUDIT_RECEIPT_NOT_DURABLE");
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
        return Map.of(
            "auditPrepared", true,
            "auditEventType", "NIM_CREATE_REQUEST",
            "requestId", "req-1",
            "conversationId", "conv-1",
            "userId", "user-1",
            "organizationId", "100002",
            "targetTool", "nim_create",
            "writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            "secretRedactionApplied", true,
            "apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY
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

    private void assertHasBlocker(List<Map<String, Object>> blockers, String code) {
        assertTrue(blockers.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected blocker code: " + code + ", actual blockers: " + blockers);
    }
}
