package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 可信策略提供器纯契约测试。
 *
 * <p>这些用例证明：只有后端可信链路给出的 license、角色和组织事实才能生成
 * {@code TRUSTED_PASSED}；Tool 入参里的伪造字段只会被记录为 ignored caller claims。</p>
 */
class NimTrustedPolicyProviderSupportTest {

    @Test
    void provider_shouldBuildTrustedPassedSnapshotForNormalOrganizationWithValidLicense() {
        NimTrustedPolicySnapshot snapshot = NimTrustedPolicyProviderSupport.buildSnapshot(validFacts(
            "100002",
            List.of("ORG_ADMIN"),
            true
        ));

        Map<String, Object> map = snapshot.toMap();
        assertEquals("TRUSTED_PASSED", map.get("snapshotState"));
        assertEquals(true, map.get("authoritative"));
        assertEquals("KUBE_MANAGER_LICENSE_AND_SESSION", map.get("source"));
        assertEquals(true, map.get("protectedFromCallerParams"));

        @SuppressWarnings("unchecked")
        Map<String, Object> license = (Map<String, Object>) map.get("nvaieLicense");
        assertEquals("VALID", license.get("status"));
        assertEquals(true, license.get("valid"));

        @SuppressWarnings("unchecked")
        Map<String, Object> callerOrgPolicy = (Map<String, Object>) map.get("callerOrgPolicy");
        assertEquals("ALLOWED", callerOrgPolicy.get("status"));
        assertEquals(false, callerOrgPolicy.get("callerSysAdmin"));
        assertEquals(false, callerOrgPolicy.get("systemOrganization"));
        assertEquals("100002", callerOrgPolicy.get("organizationId"));
    }

    @Test
    void provider_shouldBlockSystemOrganizationEvenWhenLicenseIsValid() {
        NimTrustedPolicySnapshot snapshot = NimTrustedPolicyProviderSupport.buildSnapshot(validFacts(
            "100001",
            List.of("ORG_ADMIN"),
            true
        ));

        Map<String, Object> map = snapshot.toMap();
        assertEquals("TRUSTED_BLOCKED", map.get("snapshotState"));
        @SuppressWarnings("unchecked")
        Map<String, Object> callerOrgPolicy = (Map<String, Object>) map.get("callerOrgPolicy");
        assertEquals("BLOCKED", callerOrgPolicy.get("status"));
        assertEquals(true, callerOrgPolicy.get("systemOrganization"));
    }

    @Test
    void provider_shouldBlockSysAdminRoleWithoutTreatingOrgAdminAsSysAdmin() {
        NimTrustedPolicySnapshot orgAdminSnapshot = NimTrustedPolicyProviderSupport.buildSnapshot(validFacts(
            "100002",
            List.of("ORG_ADMIN"),
            true
        ));
        NimTrustedPolicySnapshot sysAdminSnapshot = NimTrustedPolicyProviderSupport.buildSnapshot(validFacts(
            "100002",
            List.of("sys-admin", "USER"),
            true
        ));

        @SuppressWarnings("unchecked")
        Map<String, Object> orgAdminPolicy =
            (Map<String, Object>) orgAdminSnapshot.toMap().get("callerOrgPolicy");
        assertEquals(false, orgAdminPolicy.get("callerSysAdmin"));
        assertEquals("TRUSTED_PASSED", orgAdminSnapshot.toMap().get("snapshotState"));

        Map<String, Object> sysAdminMap = sysAdminSnapshot.toMap();
        assertEquals("TRUSTED_BLOCKED", sysAdminMap.get("snapshotState"));
        @SuppressWarnings("unchecked")
        Map<String, Object> sysAdminPolicy = (Map<String, Object>) sysAdminMap.get("callerOrgPolicy");
        assertEquals(true, sysAdminPolicy.get("callerSysAdmin"));
        assertEquals("SYS_ADMIN,USER", sysAdminPolicy.get("callerRole"));
    }

    @Test
    void provider_shouldBlockInvalidOrExpiredLicense() {
        NimTrustedPolicySnapshot snapshot = NimTrustedPolicyProviderSupport.buildSnapshot(validFacts(
            "100002",
            List.of("USER"),
            false
        ));

        Map<String, Object> map = snapshot.toMap();
        assertEquals("TRUSTED_BLOCKED", map.get("snapshotState"));
        @SuppressWarnings("unchecked")
        Map<String, Object> license = (Map<String, Object>) map.get("nvaieLicense");
        assertEquals("INVALID", license.get("status"));
        assertEquals(false, license.get("valid"));
    }

    @Test
    void provider_shouldReturnUnverifiedWhenTrustedEvidenceIsIncomplete() {
        NimTrustedPolicySnapshot snapshot = NimTrustedPolicyProviderSupport.buildSnapshot(
            new NimTrustedPolicyProviderSupport.TrustedPolicyFacts(
                "100002",
                List.of("USER"),
                "user-1",
                true,
                true,
                NimTrustedPolicyProviderSupport.TrustedFactSource.KUBE_MANAGER_LICENSE_AND_SESSION,
                List.of("license-expiration-read-only")
            )
        );

        Map<String, Object> map = snapshot.toMap();
        assertEquals("UNVERIFIED", map.get("snapshotState"));
        assertEquals(false, map.get("authoritative"));
        @SuppressWarnings("unchecked")
        List<String> evidence = (List<String>) map.get("evidence");
        assertTrue(evidence.stream().anyMatch(item -> item.contains("公开 nim_deployment_preflight")));
    }

    @Test
    void providerReport_shouldIgnoreForgedCallerClaimsAndKeepTrustedFactsAuthoritative() {
        Map<String, Object> report = NimTrustedPolicyProviderSupport.buildProviderReport(
            validFacts("100002", List.of("USER"), true),
            Map.ofEntries(
                entry("organizationId", "100001"),
                entry("role", "SYS_ADMIN"),
                entry("roles", List.of("SYS_ADMIN")),
                entry("licenseValid", false),
                entry("nvaieLicenseValid", false),
                entry("nvaieLicenseVerified", false),
                entry("trustedPolicySource", "caller-forged"),
                entry("authoritative", false)
            )
        );

        assertEquals("NIM_TRUSTED_POLICY_PROVIDER", report.get("provider"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("protectedFromCallerParams"));
        assertEquals(true, report.get("trustedFactsComplete"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ignoredClaims = (List<Map<String, Object>>) report.get("ignoredCallerClaims");
        assertTrue(ignoredClaims.stream().anyMatch(item -> "organizationId".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "role".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "licenseValid".equals(item.get("key"))));
        assertTrue(ignoredClaims.stream().anyMatch(item -> "trustedPolicySource".equals(item.get("key"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) report.get("trustedPolicySnapshot");
        assertEquals("TRUSTED_PASSED", snapshot.get("snapshotState"));
        @SuppressWarnings("unchecked")
        Map<String, Object> callerOrgPolicy = (Map<String, Object>) snapshot.get("callerOrgPolicy");
        assertEquals("100002", callerOrgPolicy.get("organizationId"));
        assertEquals("USER", callerOrgPolicy.get("callerRole"));
    }

    @Test
    void providerReport_shouldRemainUnverifiedWhenOrgIdOrUserIsMissing() {
        Map<String, Object> missingOrgReport = NimTrustedPolicyProviderSupport.buildProviderReport(
            new NimTrustedPolicyProviderSupport.TrustedPolicyFacts(
                "",
                List.of("USER"),
                "user-1",
                true,
                true,
                NimTrustedPolicyProviderSupport.TrustedFactSource.KUBE_MANAGER_LICENSE_AND_SESSION,
                trustedEvidence()
            ),
            Map.of()
        );
        Map<String, Object> missingUserReport = NimTrustedPolicyProviderSupport.buildProviderReport(
            new NimTrustedPolicyProviderSupport.TrustedPolicyFacts(
                "100002",
                List.of("USER"),
                "",
                true,
                true,
                NimTrustedPolicyProviderSupport.TrustedFactSource.KUBE_MANAGER_LICENSE_AND_SESSION,
                trustedEvidence()
            ),
            Map.of()
        );

        assertEquals(false, missingOrgReport.get("trustedFactsComplete"));
        assertEquals(false, missingUserReport.get("trustedFactsComplete"));
        @SuppressWarnings("unchecked")
        Map<String, Object> missingOrgSnapshot = (Map<String, Object>) missingOrgReport.get("trustedPolicySnapshot");
        @SuppressWarnings("unchecked")
        Map<String, Object> missingUserSnapshot = (Map<String, Object>) missingUserReport.get("trustedPolicySnapshot");
        assertEquals("UNVERIFIED", missingOrgSnapshot.get("snapshotState"));
        assertEquals("UNVERIFIED", missingUserSnapshot.get("snapshotState"));
    }

    private NimTrustedPolicyProviderSupport.TrustedPolicyFacts validFacts(String organizationId,
                                                                          List<String> roles,
                                                                          boolean licenseValid) {
        return new NimTrustedPolicyProviderSupport.TrustedPolicyFacts(
            organizationId,
            roles,
            "user-1",
            true,
            licenseValid,
            NimTrustedPolicyProviderSupport.TrustedFactSource.KUBE_MANAGER_LICENSE_AND_SESSION,
            trustedEvidence()
        );
    }

    private List<String> trustedEvidence() {
        return List.of(
            "kube-manager SysLicenseServiceImpl.checkNavieLicense validates NVAIE license token/until",
            "UserPermissionContext current user role read from authenticated session",
            "organization orgId read from authenticated session and compared with ORGANIZATION_ID_SYS=100001"
        );
    }
}
