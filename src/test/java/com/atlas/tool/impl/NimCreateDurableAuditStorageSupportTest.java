package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM durable audit storage 候选契约测试。
 *
 * <p>这些用例刻意不连接 Elasticsearch，也不调用 mature kube-manager。测试目标是把 M5.21-51
 * 识别出的 sys_log 候选落点、语义缺口和安全替换边界固化下来，防止后续把通用系统日志误当成
 * 已可签发 durable receipt 的 NIM 专用审计 writer。</p>
 */
class NimCreateDurableAuditStorageSupportTest {

    @Test
    void storageCandidate_shouldBuildSanitizedSysLogPlanButKeepImplementationHold() {
        Map<String, Object> report = NimCreateDurableAuditStorageSupport.prepare(
            new NimCreateDurableAuditStorageSupport.DurableAuditStorageInput(
                completeAuditContext(),
                trustedPrincipalSnapshot()
            )
        );

        assertEquals(NimCreateDurableAuditStorageSupport.STORAGE_SUPPORT_NAME, report.get("durableAuditStorage"));
        assertEquals(NimCreateDurableAuditStorageSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals(NimCreateDurableAuditStorageSupport.HOLD_STATE, report.get("storageState"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("storagePlanPrepared"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        assertEquals("sys_log", report.get("candidateIndex"));
        assertEquals("ISysLogService.saveLog(SysLog)", report.get("candidateSaveService"));
        assertEquals("SaveLogAspect", report.get("candidateWriter"));
        assertEquals("GET /api/log", report.get("candidateSearchEndpoint"));
        assertEquals("SYS_ADMIN_ONLY", report.get("candidateSearchIsolation"));
        assertTrue(report.get("storagePlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("storagePlan");
        assertEquals("sys_log", plan.get("targetStorage"));
        assertEquals("DEDICATED_NIM_AUDIT_WRITER_REQUIRED", plan.get("writerBoundary"));
        assertEquals("PRE_WRITE_INTENT_THEN_POST_WRITE_RESULT", plan.get("writeMode"));

        @SuppressWarnings("unchecked")
        Map<String, Object> mapping = (Map<String, Object>) plan.get("sysLogFieldMapping");
        assertEquals(100002, mapping.get("organizationId"));
        assertEquals("alice", mapping.get("username"));
        assertEquals("NIM_CREATE_AUDIT", mapping.get("module"));
        assertEquals("/api/100002/deployment", mapping.get("uri"));
        assertFalse(mapping.toString().contains("token"));
        assertFalse(mapping.toString().contains("must-not-leak"));

        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) mapping.get("params");
        assertEquals("req-1", params.get("requestId"));
        assertEquals("nim_create", params.get("targetTool"));
        assertTrue(params.get("eventDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) mapping.get("body");
        assertEquals("llama-nim", body.get("displayName"));
        assertEquals(NimCreateStateMachineSupport.API_KEY_POLICY, body.get("apiKeyHandling"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DEDICATED_NIM_AUDIT_WRITER_NOT_IMPLEMENTED");
        assertEquals(1, blockers.size());
    }

    @Test
    void storageCandidate_shouldRejectMissingTrustedPrincipal() {
        Map<String, Object> report = NimCreateDurableAuditStorageSupport.prepare(
            new NimCreateDurableAuditStorageSupport.DurableAuditStorageInput(
                completeAuditContext(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageSupport.REJECTED_STATE, report.get("storageState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("storagePlanPrepared"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("storagePlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item -> "DEDICATED_NIM_AUDIT_WRITER_NOT_IMPLEMENTED".equals(item.get("code"))));
    }

    @Test
    void storageCandidate_shouldRejectSecretMaterialBeforeAnyStoragePlan() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "Bearer real-key-material");

        Map<String, Object> report = NimCreateDurableAuditStorageSupport.prepare(
            new NimCreateDurableAuditStorageSupport.DurableAuditStorageInput(
                audit,
                trustedPrincipalSnapshot()
            )
        );

        assertEquals(NimCreateDurableAuditStorageSupport.REJECTED_STATE, report.get("storageState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("storagePlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_STORAGE_INPUT_CONTAINS_FORBIDDEN_SECRET");
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
