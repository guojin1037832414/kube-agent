package com.atlas.tool.impl;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NIM 创建可信策略提供器的纯契约支持。
 *
 * <p>本类只接收“后端可信执行链已经读取并校验过的事实”，不调用 kube-manager，不读取 Tool
 * 入参来判断 license、角色或组织。它把这些事实转换成 {@link NimTrustedPolicySnapshot}，
 * 供未来受控 {@code nim_create} 编排消费。</p>
 */
final class NimTrustedPolicyProviderSupport {

    static final String SYSTEM_ORGANIZATION_ID = "100001";

    private NimTrustedPolicyProviderSupport() {
    }

    static NimTrustedPolicySnapshot buildSnapshot(TrustedPolicyFacts facts) {
        TrustedPolicyFacts safeFacts = facts == null ? TrustedPolicyFacts.empty() : facts;
        if (!hasTrustedSourceAndEvidence(safeFacts)
            || !hasText(safeFacts.callerUserId())
            || !hasText(safeFacts.organizationId())
            || safeFacts.callerRoles().isEmpty()
            || !safeFacts.nvaieLicenseVerified()) {
            return NimTrustedPolicySnapshot.unverified();
        }

        boolean callerSysAdmin = hasSysAdminRole(safeFacts.callerRoles());
        boolean systemOrganization = SYSTEM_ORGANIZATION_ID.equals(safeFacts.organizationId());
        return NimTrustedPolicySnapshot.fromTrustedChecks(
            safeFacts.nvaieLicenseValid(),
            callerSysAdmin,
            systemOrganization,
            String.join(",", safeFacts.callerRoles()),
            safeFacts.organizationId(),
            safeFacts.source().snapshotSource(),
            safeFacts.evidence()
        );
    }

    static Map<String, Object> buildProviderReport(TrustedPolicyFacts facts,
                                                   Map<String, Object> callerParams) {
        TrustedPolicyFacts safeFacts = facts == null ? TrustedPolicyFacts.empty() : facts;
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("provider", "NIM_TRUSTED_POLICY_PROVIDER");
        report.put("sideEffect", "NONE");
        report.put("protectedFromCallerParams", true);
        report.put("trustedFactSource", safeFacts.source() == null ? "" : safeFacts.source().snapshotSource());
        report.put("trustedFactsComplete", hasTrustedSourceAndEvidence(safeFacts)
            && hasText(safeFacts.callerUserId())
            && hasText(safeFacts.organizationId())
            && !safeFacts.callerRoles().isEmpty()
            && safeFacts.nvaieLicenseVerified());
        report.put("ignoredCallerClaims", detectIgnoredCallerClaims(callerParams));
        report.put("trustedPolicySnapshot", buildSnapshot(safeFacts).toMap());
        report.put("requiredTrustedFacts", List.of(
            "后端可信读取并校验 NVAIE license token/until，而不是读取 Tool 入参 licenseValid",
            "后端可信读取当前用户 ID 与角色，精确拒绝 SYS_ADMIN",
            "后端可信读取当前 organizationId，并拒绝 system organization 100001",
            "保留 license、RBAC、organization 三类证据，供审计和学习复盘"
        ));
        return report;
    }

    private static boolean hasTrustedSourceAndEvidence(TrustedPolicyFacts facts) {
        if (facts.source() == null || facts.evidence().isEmpty()) {
            return false;
        }
        String evidenceText = String.join(" ", facts.evidence()).toUpperCase(Locale.ROOT);
        boolean hasLicenseEvidence = evidenceText.contains("LICENSE") || evidenceText.contains("NVAIE");
        boolean hasRoleEvidence = evidenceText.contains("ROLE")
            || evidenceText.contains("RBAC")
            || evidenceText.contains("USERPERMISSIONCONTEXT")
            || evidenceText.contains("CURRENT USER");
        boolean hasOrganizationEvidence = evidenceText.contains("ORGANIZATION")
            || evidenceText.contains("ORGID")
            || evidenceText.contains("ORG ID")
            || evidenceText.contains("ORGANIZATION_ID_SYS");
        return hasLicenseEvidence && hasRoleEvidence && hasOrganizationEvidence;
    }

    private static boolean hasSysAdminRole(List<String> roles) {
        for (String role : roles) {
            String normalized = normalizeRole(role);
            if ("SYS_ADMIN".equals(normalized)) {
                return true;
            }
        }
        return false;
    }

    private static List<Map<String, Object>> detectIgnoredCallerClaims(Map<String, Object> callerParams) {
        Map<String, Object> safeParams = callerParams == null ? Map.of() : callerParams;
        List<String> riskyKeys = List.of(
            "organizationId",
            "orgId",
            "userId",
            "role",
            "roles",
            "callerRole",
            "callerRoles",
            "sysAdmin",
            "isSysOrg",
            "systemOrganization",
            "licenseValid",
            "isLicenseValid",
            "nvaieLicenseValid",
            "nvaieLicenseVerified",
            "trustedPolicySnapshot",
            "trustedPolicySource",
            "policySource",
            "authoritative"
        );
        List<Map<String, Object>> ignored = new ArrayList<>();
        for (String key : riskyKeys) {
            if (safeParams.containsKey(key)) {
                Map<String, Object> claim = new LinkedHashMap<>();
                claim.put("key", key);
                claim.put("ignored", true);
                claim.put("reason", "该字段来自 Tool 入参，不能作为 NIM license/RBAC/组织可信策略依据。");
                ignored.add(claim);
            }
        }
        return ignored;
    }

    private static String normalizeOrganizationId(String organizationId) {
        if (!hasText(organizationId)) {
            return "";
        }
        String trimmed = organizationId.trim();
        if (!trimmed.matches("\\d{1,10}")) {
            return "";
        }
        try {
            return String.valueOf(Integer.parseInt(trimmed));
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    private static String normalizeRole(String role) {
        if (!hasText(role)) {
            return "";
        }
        return role.trim()
            .replace('-', '_')
            .toUpperCase(Locale.ROOT);
    }

    private static List<String> normalizeRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String role : roles) {
            String value = normalizeRole(role);
            if (hasText(value) && !normalized.contains(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private static List<String> normalizeEvidence(List<String> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String item : evidence) {
            if (hasText(item)) {
                normalized.add(item.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    enum TrustedFactSource {
        KUBE_MANAGER_LICENSE_AND_SESSION("KUBE_MANAGER_LICENSE_AND_SESSION");

        private final String snapshotSource;

        TrustedFactSource(String snapshotSource) {
            this.snapshotSource = snapshotSource;
        }

        String snapshotSource() {
            return snapshotSource;
        }
    }

    record TrustedPolicyFacts(
        String organizationId,
        List<String> callerRoles,
        String callerUserId,
        boolean nvaieLicenseVerified,
        boolean nvaieLicenseValid,
        TrustedFactSource source,
        List<String> evidence
    ) {
        TrustedPolicyFacts {
            organizationId = normalizeOrganizationId(organizationId);
            callerRoles = normalizeRoles(callerRoles);
            callerUserId = valueOrEmpty(callerUserId);
            evidence = normalizeEvidence(evidence);
        }

        static TrustedPolicyFacts empty() {
            return new TrustedPolicyFacts(
                "",
                List.of(),
                "",
                false,
                false,
                null,
                List.of()
            );
        }
    }
}
