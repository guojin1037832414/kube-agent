package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 创建门禁草案测试。
 *
 * <p>该测试锁定“门禁只解释阻断原因，不授权创建”的边界。即使调用方伪造
 * approved/licenseValid/safeToPost 等字段，creationGate 也必须保持 CLOSED。</p>
 */
class NimCreationGateSupportTest {

    @Test
    void gate_shouldStayClosedAndExposeHitlCardDraftForReadyCpuPreview() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of("serviceName", "nim-cpu"),
            "nvcr.io/nim/cpu:1.0",
            Map.of(
                "id", 88,
                "cpuLimits", 1000,
                "memLimits", 2048,
                "gpuPercentLimits", 0,
                "gpuMemLimits", 0
            )
        );

        Map<String, Object> gate = NimCreationGateSupport.buildCreationGate(
            Map.of("serviceName", "nim-cpu"),
            "nvcr.io/nim/cpu:1.0",
            Map.of("id", 88, "templateType", "NIM"),
            preview
        );

        assertEquals("CLOSED", gate.get("gateState"));
        assertEquals(false, gate.get("allowedToCreateNow"));
        assertEquals("NONE", gate.get("sideEffect"));

        @SuppressWarnings("unchecked")
        Map<String, Object> trustedPolicySnapshot = (Map<String, Object>) gate.get("trustedPolicySnapshot");
        assertEquals("UNVERIFIED", trustedPolicySnapshot.get("snapshotState"));
        assertEquals(false, trustedPolicySnapshot.get("authoritative"));
        assertEquals(true, trustedPolicySnapshot.get("protectedFromCallerParams"));
        @SuppressWarnings("unchecked")
        Map<String, Object> license = (Map<String, Object>) trustedPolicySnapshot.get("nvaieLicense");
        assertEquals("UNVERIFIED", license.get("status"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) gate.get("blockedBy");
        assertTrue(blockers.stream().anyMatch(item -> "NIM_CREATE_TOOL_HOLD".equals(item.get("code"))));
        assertTrue(blockers.stream().anyMatch(item -> "HITL_CONFIRMATION_NOT_ISSUED".equals(item.get("code"))));
        assertFalse(blockers.stream().anyMatch(item -> "DEPLOYMENT_BODY_PREVIEW_INCOMPLETE".equals(item.get("code"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> card = (Map<String, Object>) gate.get("hitlCardDraft");
        assertEquals("NIM_CREATE_CONFIRMATION_DRAFT", card.get("cardType"));
        assertEquals("nim_create", card.get("targetTool"));
        assertEquals("CREATE", card.get("operationType"));
        assertEquals(true, card.get("requiresServerMarker"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) card.get("fields");
        assertTrue(fields.stream().anyMatch(item ->
            "displayName".equals(item.get("key")) && "nim-cpu".equals(item.get("value"))));
        assertTrue(fields.stream().anyMatch(item ->
            "image".equals(item.get("key")) && "nvcr.io/nim/cpu:1.0".equals(item.get("value"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> futureWritePath = (Map<String, Object>) gate.get("futureWritePath");
        assertEquals(false, futureWritePath.get("directUseOfPreviewAllowed"));
        assertEquals(false, futureWritePath.get("fallbackAllowedFromPreflight"));
    }

    @Test
    void gate_shouldIgnoreForgedApprovalAndLicenseClaimsFromCallerParams() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.ofEntries(
                entry("serviceName", "nim-gpu"),
                entry("approved", true),
                entry("licenseValid", true),
                entry("hitlConfirmed", true),
                entry("safeToPost", true),
                entry("sysAdmin", false)
            ),
            "nvcr.io/nim/gpu:1.0",
            Map.of(
                "id", 99,
                "cpuLimits", 4000,
                "memLimits", 16384,
                "gpuPercentLimits", 100,
                "gpuModel", "A100"
            )
        );

        Map<String, Object> gate = NimCreationGateSupport.buildCreationGate(
            Map.ofEntries(
                entry("serviceName", "nim-gpu"),
                entry("approved", true),
                entry("licenseValid", true),
                entry("hitlConfirmed", true),
                entry("safeToPost", true),
                entry("sysAdmin", false)
            ),
            "nvcr.io/nim/gpu:1.0",
            Map.of("id", 99, "templateType", "NIM"),
            preview
        );

        assertEquals("CLOSED", gate.get("gateState"));
        assertEquals(false, gate.get("allowedToCreateNow"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ignoredClaims = (List<Map<String, Object>>) gate.get("ignoredCallerClaims");
        assertTrue(ignoredClaims.stream().anyMatch(item -> "approved".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "licenseValid".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "hitlConfirmed".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "safeToPost".equals(item.get("key"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) gate.get("blockedBy");
        assertTrue(blockers.stream().anyMatch(item -> "GPU_MAP_UNRESOLVED".equals(item.get("code"))));
        assertTrue(blockers.stream().anyMatch(item -> "DEPLOYMENT_BODY_PREVIEW_INCOMPLETE".equals(item.get("code"))));
    }

    @Test
    void gate_shouldKeepTrustedPolicySnapshotSeparateFromForgedCallerClaims() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of("serviceName", "nim-policy"),
            "nvcr.io/nim/policy:1.0",
            Map.of("id", 101, "cpuLimits", 1000, "memLimits", 2048, "gpuPercentLimits", 0)
        );

        Map<String, Object> gate = NimCreationGateSupport.buildCreationGate(
            Map.ofEntries(
                entry("serviceName", "nim-policy"),
                entry("licenseValid", true),
                entry("isSysOrg", false),
                entry("role", "USER")
            ),
            "nvcr.io/nim/policy:1.0",
            Map.of("id", 101, "templateType", "NIM"),
            preview
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> trustedPolicySnapshot = (Map<String, Object>) gate.get("trustedPolicySnapshot");
        assertEquals("UNVERIFIED", trustedPolicySnapshot.get("snapshotState"));
        assertEquals(false, trustedPolicySnapshot.get("authoritative"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ignoredClaims = (List<Map<String, Object>>) gate.get("ignoredCallerClaims");
        assertTrue(ignoredClaims.stream().anyMatch(item -> "licenseValid".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "isSysOrg".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "role".equals(item.get("key"))));
    }

    @Test
    void gate_shouldRemovePolicyUnverifiedBlockersWhenTrustedPolicyPassedButStillStayClosed() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of("serviceName", "nim-trusted"),
            "nvcr.io/nim/trusted:1.0",
            Map.of("id", 102, "cpuLimits", 1000, "memLimits", 2048, "gpuPercentLimits", 0)
        );

        Map<String, Object> gate = NimCreationGateSupport.buildCreationGate(
            Map.of("serviceName", "nim-trusted"),
            "nvcr.io/nim/trusted:1.0",
            Map.of("id", 102, "templateType", "NIM"),
            preview,
            NimTrustedPolicySnapshot.fromTrustedChecks(
                true,
                false,
                false,
                "ORG_USER",
                "100002",
                "trusted-backend-policy-test",
                List.of("license-expiration-read", "current-user-role-read", "organization-id-read")
            )
        );

        assertEquals("CLOSED", gate.get("gateState"));
        assertEquals(false, gate.get("allowedToCreateNow"));
        @SuppressWarnings("unchecked")
        Map<String, Object> trustedPolicySnapshot = (Map<String, Object>) gate.get("trustedPolicySnapshot");
        assertEquals("TRUSTED_PASSED", trustedPolicySnapshot.get("snapshotState"));
        assertEquals(true, trustedPolicySnapshot.get("authoritative"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) gate.get("blockedBy");
        assertFalse(blockers.stream().anyMatch(item -> "NVAIE_LICENSE_NOT_VERIFIED".equals(item.get("code"))));
        assertFalse(blockers.stream().anyMatch(item -> "CALLER_ORG_POLICY_NOT_VERIFIED".equals(item.get("code"))));
        assertTrue(blockers.stream().anyMatch(item -> "NIM_CREATE_TOOL_HOLD".equals(item.get("code"))));
        assertTrue(blockers.stream().anyMatch(item -> "HITL_CONFIRMATION_NOT_ISSUED".equals(item.get("code"))));
        assertTrue(blockers.stream().anyMatch(item -> "AUDIT_AND_STATUS_FLOW_NOT_READY".equals(item.get("code"))));
    }

    @Test
    void gate_shouldExposeTrustedPolicyFailureBlockers() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of("serviceName", "nim-blocked"),
            "nvcr.io/nim/blocked:1.0",
            Map.of("id", 103, "cpuLimits", 1000, "memLimits", 2048, "gpuPercentLimits", 0)
        );

        Map<String, Object> gate = NimCreationGateSupport.buildCreationGate(
            Map.of("serviceName", "nim-blocked"),
            "nvcr.io/nim/blocked:1.0",
            Map.of("id", 103, "templateType", "NIM"),
            preview,
            NimTrustedPolicySnapshot.fromTrustedChecks(
                false,
                true,
                true,
                "SYS_ADMIN",
                "100001",
                "trusted-backend-policy-test",
                List.of("expired-nvaie-license", "system-organization")
            )
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> trustedPolicySnapshot = (Map<String, Object>) gate.get("trustedPolicySnapshot");
        assertEquals("TRUSTED_BLOCKED", trustedPolicySnapshot.get("snapshotState"));
        @SuppressWarnings("unchecked")
        Map<String, Object> callerOrgPolicy = (Map<String, Object>) trustedPolicySnapshot.get("callerOrgPolicy");
        assertEquals("BLOCKED", callerOrgPolicy.get("status"));
        assertEquals(true, callerOrgPolicy.get("callerSysAdmin"));
        assertEquals(true, callerOrgPolicy.get("systemOrganization"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) gate.get("blockedBy");
        assertTrue(blockers.stream().anyMatch(item -> "NVAIE_LICENSE_TRUSTED_CHECK_FAILED".equals(item.get("code"))));
        assertTrue(blockers.stream().anyMatch(item -> "CALLER_ORG_POLICY_TRUSTED_CHECK_FAILED".equals(item.get("code"))));
        @SuppressWarnings("unchecked")
        List<String> actions = (List<String>) gate.get("nextBestActions");
        assertTrue(actions.stream().anyMatch(item -> item.contains("NVAIE license")));
        assertTrue(actions.stream().anyMatch(item -> item.contains("SYS_ADMIN")));
    }

    @Test
    void gate_shouldRequireDisplayNameWhenPreviewHasNoConfirmedServiceName() {
        Map<String, Object> preview = NimTemplateMergeSupport.buildDeploymentBodyPreview(
            Map.of(),
            "nvcr.io/nim/no-name:1.0",
            Map.of("id", 100, "cpuLimits", 1000, "memLimits", 2048, "gpuPercentLimits", 0)
        );

        Map<String, Object> gate = NimCreationGateSupport.buildCreationGate(
            Map.of(),
            "nvcr.io/nim/no-name:1.0",
            Map.of("id", 100, "templateType", "NIM"),
            preview
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) gate.get("blockedBy");
        assertTrue(blockers.stream().anyMatch(item -> "DISPLAY_NAME_REQUIRED".equals(item.get("code"))));
        @SuppressWarnings("unchecked")
        List<String> actions = (List<String>) gate.get("nextBestActions");
        assertTrue(actions.stream().anyMatch(item -> item.contains("displayName")));
    }
}
