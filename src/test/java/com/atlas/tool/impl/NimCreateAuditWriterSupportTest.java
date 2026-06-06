package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 创建审计 writer 的 mock-first 契约测试。
 *
 * <p>本测试不连接真实持久化。它只证明未来 writer 的 receipt 形状、脱敏边界和 fail-closed
 * 行为已经被代码锁定；真实放行仍必须替换为 durable audit log receipt。</p>
 */
class NimCreateAuditWriterSupportTest {

    @Test
    void writer_shouldBuildMockReceiptWithoutTouchingDurableStorage() {
        Map<String, Object> receipt = NimCreateAuditWriterSupport.buildMockReceipt(completeAuditContext());

        assertEquals(true, receipt.get("auditReceiptPrepared"));
        assertEquals("MOCK_PREPARED", receipt.get("receiptStatus"));
        assertEquals("NONE", receipt.get("sideEffect"));
        assertEquals(NimCreateAuditWriterSupport.STORAGE_MODE_MOCK_CONTRACT, receipt.get("storageMode"));
        assertEquals(false, receipt.get("durable"));
        assertEquals(false, receipt.get("realStorageTouched"));
        assertEquals(false, receipt.get("releaseEligible"));
        assertEquals(NimCreateAuditWriterSupport.STORAGE_MODE_DURABLE_AUDIT_LOG, receipt.get("requiredFutureStorage"));
        assertEquals(NimCreateAuditWriterSupport.DIGEST_ALGORITHM, receipt.get("eventDigestAlgorithm"));
        assertTrue(receipt.get("eventDigest").toString().matches("[a-f0-9]{64}"));
        assertTrue(receipt.get("receiptId").toString().startsWith("nim-audit-"));
        assertEquals("NIM_CREATE_REQUEST", receipt.get("auditEventType"));
        assertEquals("req-1", receipt.get("requestId"));
        assertEquals("conv-1", receipt.get("conversationId"));
        assertEquals("user-1", receipt.get("userId"));
        assertEquals("100002", receipt.get("organizationId"));
        assertEquals("nim_create", receipt.get("targetTool"));
        assertFalse(receipt.containsKey("token"));
        assertFalse(receipt.containsKey("apiKey"));
    }

    @Test
    void writer_shouldProduceStableDigestForSameSanitizedAuditContext() {
        Map<String, Object> first = NimCreateAuditWriterSupport.buildMockReceipt(completeAuditContext());
        Map<String, Object> second = NimCreateAuditWriterSupport.buildMockReceipt(completeAuditContext());

        assertEquals(first.get("eventDigest"), second.get("eventDigest"));
        assertEquals(first.get("receiptId"), second.get("receiptId"));
    }

    @Test
    void writer_shouldRejectUnpreparedAuditContext() {
        Map<String, Object> audit = new java.util.LinkedHashMap<>(completeAuditContext());
        audit.put("auditPrepared", false);

        Map<String, Object> receipt = NimCreateAuditWriterSupport.buildMockReceipt(audit);

        assertEquals(false, receipt.get("auditReceiptPrepared"));
        assertEquals("REJECTED", receipt.get("receiptStatus"));
        assertEquals(false, receipt.get("durable"));
        assertEquals("", receipt.get("receiptId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) receipt.get("blockedBy");
        assertTrue(blockers.stream().anyMatch(item -> "AUDIT_CONTEXT_NOT_PREPARED".equals(item.get("code"))));
    }

    @Test
    void writer_shouldRejectAuditContextContainingSecrets() {
        Map<String, Object> audit = new java.util.LinkedHashMap<>(completeAuditContext());
        audit.put("ngcApiKey", "must-not-leak");

        Map<String, Object> receipt = NimCreateAuditWriterSupport.buildMockReceipt(audit);

        assertEquals(false, receipt.get("auditReceiptPrepared"));
        assertEquals("REJECTED", receipt.get("receiptStatus"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) receipt.get("blockedBy");
        assertTrue(blockers.stream().anyMatch(item -> "AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET".equals(item.get("code"))));
    }

    @Test
    void writerMockReceipt_shouldNotSatisfyStateMachineDurableAuditRequirement() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-mock-audit"),
            openGate(),
            completePreview(),
            com.atlas.hitl.HitlConfirmation.human("thread-1", "nim_create"),
            audit,
            NimCreateAuditWriterSupport.buildMockReceipt(audit),
            completeReadinessPlan(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertTrue(blockers.stream().anyMatch(item -> "AUDIT_RECEIPT_NOT_DURABLE".equals(item.get("code"))));
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
        return Map.of(
            "safeToPost", false,
            "previewOnly", true,
            "bodyComplete", true,
            "bodyDraft", Map.of(
                "displayName", "llama-nim",
                "image", "nvcr.io/nim/llama:1.0",
                "templateId", 88
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
}
