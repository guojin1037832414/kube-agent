package com.atlas.tool.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NIM 创建前的可信策略快照。
 *
 * <p>该对象表达的是“Agent 后端执行链已经可信读取到的 license/RBAC 事实”，
 * 而不是 Tool 入参里的自报字段。公开的 NIM preflight 不会从 params 构造通过态；
 * 未来只有受控编排在完成后端 license、当前用户角色、当前组织类型校验后，
 * 才能把该快照传给 creation gate。</p>
 */
final class NimTrustedPolicySnapshot {

    private static final String STATE_UNVERIFIED = "UNVERIFIED";
    private static final String STATE_TRUSTED_PASSED = "TRUSTED_PASSED";
    private static final String STATE_TRUSTED_BLOCKED = "TRUSTED_BLOCKED";

    private static final String STATUS_UNVERIFIED = "UNVERIFIED";
    private static final String STATUS_VALID = "VALID";
    private static final String STATUS_INVALID = "INVALID";
    private static final String STATUS_ALLOWED = "ALLOWED";
    private static final String STATUS_BLOCKED = "BLOCKED";

    private static final String SOURCE_UNVERIFIED = "UNVERIFIED_PUBLIC_PREFLIGHT";

    private final boolean authoritative;
    private final String source;
    private final String organizationId;
    private final String callerRole;
    private final boolean nvaieLicenseVerified;
    private final boolean nvaieLicenseValid;
    private final boolean callerOrgPolicyVerified;
    private final boolean callerSysAdmin;
    private final boolean systemOrganization;
    private final List<String> evidence;

    private NimTrustedPolicySnapshot(boolean authoritative,
                                     String source,
                                     String organizationId,
                                     String callerRole,
                                     boolean nvaieLicenseVerified,
                                     boolean nvaieLicenseValid,
                                     boolean callerOrgPolicyVerified,
                                     boolean callerSysAdmin,
                                     boolean systemOrganization,
                                     List<String> evidence) {
        this.authoritative = authoritative;
        this.source = hasText(source) ? source : SOURCE_UNVERIFIED;
        this.organizationId = valueOrEmpty(organizationId);
        this.callerRole = valueOrEmpty(callerRole);
        this.nvaieLicenseVerified = nvaieLicenseVerified;
        this.nvaieLicenseValid = nvaieLicenseValid;
        this.callerOrgPolicyVerified = callerOrgPolicyVerified;
        this.callerSysAdmin = callerSysAdmin;
        this.systemOrganization = systemOrganization;
        this.evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }

    static NimTrustedPolicySnapshot unverified() {
        return new NimTrustedPolicySnapshot(
            false,
            SOURCE_UNVERIFIED,
            "",
            "",
            false,
            false,
            false,
            false,
            false,
            List.of("公开 nim_deployment_preflight 不读取 license/RBAC 可信事实")
        );
    }

    static NimTrustedPolicySnapshot fromTrustedChecks(boolean nvaieLicenseValid,
                                                      boolean callerSysAdmin,
                                                      boolean systemOrganization,
                                                      String callerRole,
                                                      String organizationId,
                                                      String source,
                                                      List<String> evidence) {
        return new NimTrustedPolicySnapshot(
            true,
            source,
            organizationId,
            callerRole,
            true,
            nvaieLicenseValid,
            true,
            callerSysAdmin,
            systemOrganization,
            evidence
        );
    }

    boolean nvaieLicenseVerified() {
        return nvaieLicenseVerified;
    }

    boolean nvaieLicenseValid() {
        return nvaieLicenseVerified && nvaieLicenseValid;
    }

    boolean callerOrgPolicyVerified() {
        return callerOrgPolicyVerified;
    }

    boolean callerOrgPolicyAllowed() {
        return callerOrgPolicyVerified && !callerSysAdmin && !systemOrganization;
    }

    Map<String, Object> toMap() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("snapshotState", snapshotState());
        snapshot.put("authoritative", authoritative);
        snapshot.put("source", source);
        snapshot.put("protectedFromCallerParams", true);
        snapshot.put("nvaieLicense", licenseMap());
        snapshot.put("callerOrgPolicy", callerOrgPolicyMap());
        snapshot.put("evidence", evidence);
        return snapshot;
    }

    private String snapshotState() {
        if (!authoritative || !nvaieLicenseVerified || !callerOrgPolicyVerified) {
            return STATE_UNVERIFIED;
        }
        if (nvaieLicenseValid() && callerOrgPolicyAllowed()) {
            return STATE_TRUSTED_PASSED;
        }
        return STATE_TRUSTED_BLOCKED;
    }

    private Map<String, Object> licenseMap() {
        Map<String, Object> license = new LinkedHashMap<>();
        license.put("check", "NVAIE_LICENSE");
        license.put("verified", nvaieLicenseVerified);
        license.put("status", nvaieLicenseVerified
            ? (nvaieLicenseValid ? STATUS_VALID : STATUS_INVALID)
            : STATUS_UNVERIFIED);
        license.put("valid", nvaieLicenseValid());
        license.put("source", nvaieLicenseVerified ? source : SOURCE_UNVERIFIED);
        return license;
    }

    private Map<String, Object> callerOrgPolicyMap() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("check", "CALLER_ORG_POLICY");
        policy.put("verified", callerOrgPolicyVerified);
        policy.put("status", callerOrgPolicyVerified
            ? (callerOrgPolicyAllowed() ? STATUS_ALLOWED : STATUS_BLOCKED)
            : STATUS_UNVERIFIED);
        policy.put("allowed", callerOrgPolicyAllowed());
        policy.put("callerRole", callerRole);
        policy.put("callerSysAdmin", callerSysAdmin);
        policy.put("systemOrganization", systemOrganization);
        policy.put("organizationId", organizationId);
        policy.put("source", callerOrgPolicyVerified ? source : SOURCE_UNVERIFIED);
        return policy;
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
