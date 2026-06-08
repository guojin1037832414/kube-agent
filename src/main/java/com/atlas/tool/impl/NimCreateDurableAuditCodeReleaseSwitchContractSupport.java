package com.atlas.tool.impl;

import com.atlas.tool.core.NimForbiddenSecretMaterialDetector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * NIM create 写链路的代码级 release switch 契约。
 *
 * <p>本类只定义 future code release switch 必须如何绑定 M5.21-71 release decision contract、
 * 未来 server-issued release/validation digests、代码审查证据和写链路 digest。它不打开真实开关，
 * 不修改状态机，不绑定 HTTP/存储客户端，也不允许写执行。</p>
 */
final class NimCreateDurableAuditCodeReleaseSwitchContractSupport {

    static final String SWITCH_CONTRACT_NAME =
        "NIM_CREATE_DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT";
    static final String EXECUTION_MODE =
        "DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String FUTURE_CODE_RELEASE_SWITCH = "NimCreateDurableAuditCodeReleaseSwitch";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String SWITCH_LOCKED = "LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH";
    private static final String REQUIRED_SWITCH_STATE = "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION";
    private static final String RELEASE_DENIED = "DENY_UNTIL_SERVER_VALIDATION_RESULT";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";

    private NimCreateDurableAuditCodeReleaseSwitchContractSupport() {
    }

    static List<String> codeReleaseSwitchFailureStatuses() {
        return List.of(
            "CODE_RELEASE_SWITCH_NOT_IMPLEMENTED",
            "CALLER_SWITCH_EVIDENCE_REJECTED",
            "ENVIRONMENT_VARIABLE_OVERRIDE_REJECTED",
            "RUNTIME_FLAG_FALLBACK_REJECTED",
            "RELEASE_DECISION_CONTRACT_NOT_AUTHORITY",
            "STATE_MACHINE_BOOLEAN_NOT_AUTHORITY",
            "DURABLE_EXECUTOR_SUCCESS_NOT_AUTHORITY",
            "SECRET_MATERIAL_REJECTED"
        );
    }

    static List<String> codeReleaseSwitchForbiddenShortcuts() {
        return List.of(
            "treating M5.21-71 release decision contract as an open code switch",
            "accepting caller-supplied codeReleaseSwitch or runtime flag",
            "accepting environment variable override as release approval",
            "opening writePermitted before code review and test evidence digests exist",
            "allowing durable executor write success to backfill code switch evidence",
            "treating code release switch as implied by validation result or release decision"
        );
    }

    static Map<String, Object> codeReleaseSwitchCurrentTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("switchState", SWITCH_LOCKED);
        template.put("requiredSwitchState", REQUIRED_SWITCH_STATE);
        template.put("codeReleaseSwitchDigestVerified", false);
        template.put("codeReviewDigestVerified", false);
        template.put("testEvidenceDigestVerified", false);
        template.put("securityApprovalDigestVerified", false);
        template.put("rollbackPlanDigestVerified", false);
        template.put("changeWindowDigestVerified", false);
        template.put("releaseDecisionDigestVerified", false);
        template.put("validationResultDigestVerified", false);
        template.put("trustedPrincipalValidated", false);
        template.put("stateMachineReleaseBound", false);
        template.put("durableExecutorReleaseBound", false);
        template.put("releaseEligible", false);
        template.put("writePermitted", false);
        template.put("writeExecutionAllowed", false);
        template.put("realHttpExecutionAllowed", false);
        return template;
    }

    static Map<String, Object> codeReleaseSwitchOpenPrerequisites() {
        Map<String, Object> prerequisites = new LinkedHashMap<>();
        prerequisites.put("releaseDecisionContractDigestRequired", true);
        prerequisites.put("serverIssuedReleaseDecisionDigestRequired", true);
        prerequisites.put("serverIssuedValidationResultDigestRequired", true);
        prerequisites.put("codeReviewDigestRequired", true);
        prerequisites.put("testEvidenceDigestRequired", true);
        prerequisites.put("securityApprovalDigestRequired", true);
        prerequisites.put("rollbackPlanDigestRequired", true);
        prerequisites.put("changeWindowDigestRequired", true);
        prerequisites.put("stateMachineRecheckRequired", true);
        prerequisites.put("durableExecutorRecheckRequired", true);
        prerequisites.put("currentContractSatisfiesPrerequisites", false);
        return prerequisites;
    }

    static Map<String, Object> codeReleaseSwitchReleaseDecisionBinding(Map<String, Object> releaseDecisionReport) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("sourceReleaseDecisionContractDigest",
            text(releaseDecisionReport.get("releaseDecisionContractDigest")));
        binding.put("sourceValidationResultContractDigest",
            text(releaseDecisionReport.get("sourceValidationResultContractDigest")));
        putSourceDigests(binding, releaseDecisionReport);
        binding.put("trustedPrincipalDigest", text(releaseDecisionReport.get("trustedPrincipalDigest")));
        binding.put("futureReleaseDecisionDigestRequired", true);
        binding.put("futureValidationResultDigestRequired", true);
        binding.put("mustBindServerIssuedReleaseDecisionDigest", true);
        binding.put("mustBindServerIssuedValidationResultDigest", true);
        binding.put("mustBindTrustedPrincipalDigest", true);
        binding.put("mustBindAuditEventDigest", true);
        binding.put("callerReleaseDecisionAllowed", false);
        return binding;
    }

    static Map<String, Object> codeReleaseSwitchReleaseDecisionBindingFromSwitchReport(
        Map<String, Object> switchReport
    ) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("sourceReleaseDecisionContractDigest",
            text(switchReport.get("sourceReleaseDecisionContractDigest")));
        binding.put("sourceValidationResultContractDigest",
            text(switchReport.get("sourceValidationResultContractDigest")));
        putSourceDigests(binding, switchReport);
        binding.put("trustedPrincipalDigest", text(switchReport.get("trustedPrincipalDigest")));
        binding.put("futureReleaseDecisionDigestRequired", true);
        binding.put("futureValidationResultDigestRequired", true);
        binding.put("mustBindServerIssuedReleaseDecisionDigest", true);
        binding.put("mustBindServerIssuedValidationResultDigest", true);
        binding.put("mustBindTrustedPrincipalDigest", true);
        binding.put("mustBindAuditEventDigest", true);
        binding.put("callerReleaseDecisionAllowed", false);
        return binding;
    }

    static Map<String, Object> codeReleaseSwitchStateMachineBinding() {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("target", "NimCreateStateMachineSupport");
        binding.put("futureCodeReleaseSwitchDigestRequired", true);
        binding.put("futureReleaseDecisionDigestRequired", true);
        binding.put("futureValidationResultDigestRequired", true);
        binding.put("mustRecomputeSwitchDigestBeforeWritePermitted", true);
        binding.put("fallbackToRuntimeFlagAllowed", false);
        binding.put("fallbackToEnvironmentVariableAllowed", false);
        binding.put("writePermittedCanBeTrueNow", false);
        return binding;
    }

    static Map<String, Object> codeReleaseSwitchDurableExecutorBinding() {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("target", NimCreateDurableWriteExecutorSupport.EXECUTOR_NAME);
        binding.put("futureCodeReleaseSwitchDigestRequired", true);
        binding.put("futureReleaseDecisionDigestRequired", true);
        binding.put("futureValidationResultDigestRequired", true);
        binding.put("mustRecheckImmediatelyBeforePost", true);
        binding.put("fallbackToStateMachineFlagOnlyAllowed", false);
        binding.put("writeExecutionAllowedNow", false);
        return binding;
    }

    static Map<String, Object> plan(CodeReleaseSwitchContractInput input) {
        CodeReleaseSwitchContractInput safeInput = input == null
            ? CodeReleaseSwitchContractInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> releaseDecisionReport = safeInput.durableAuditReleaseDecisionContractReport();
        Map<String, Object> callerSwitchEvidence = safeInput.callerSwitchEvidence();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateReleaseDecisionContractReport(auditContext, principal, releaseDecisionReport, blockers);
        validateCallerSwitchEvidence(callerSwitchEvidence, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditReleaseDecisionContractReport", releaseDecisionReport, blockers);
        validateNoSecretMaterial("callerSwitchEvidence", callerSwitchEvidence, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> codeReleaseSwitchContract = inputAccepted
            ? codeReleaseSwitchContract(auditContext, principal, releaseDecisionReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT_IMPLEMENTATION_HOLD",
                "code release switch 契约已定义，但真实代码审查、开关签发、状态机回接和 durable executor 回接尚未实现；当前不能放行写执行。",
                "durable-audit-code-release-switch-contract"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditCodeReleaseSwitchContract", SWITCH_CONTRACT_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("switchState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureCodeReleaseSwitch", FUTURE_CODE_RELEASE_SWITCH);
        result.put("futureReleaseDecision",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        result.put("futureValidationResult",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("springBeanRegistered", false);
        result.put("httpClientBound", false);
        result.put("storageClientBound", false);
        result.put("inputAccepted", inputAccepted);
        result.put("codeReleaseSwitchContractPrepared", inputAccepted);
        result.put("releaseDecisionContractRequired", true);
        result.put("serverOwnedCodeReleaseSwitchRequired", true);
        result.put("reviewedCodeSwitchDigestRequired", true);
        result.put("serverIssuedReleaseDecisionDigestRequired", true);
        result.put("serverIssuedValidationResultDigestRequired", true);
        result.put("callerSwitchEvidenceAuthoritative", false);
        result.put("legacyConfigFlagAllowed", false);
        result.put("environmentVariableOverrideAllowed", false);
        result.put("runtimeToggleOverrideAllowed", false);
        result.put("realCodeReleaseSwitchCreated", false);
        result.put("realCodeReleaseSwitchOpened", false);
        result.put("serverOwnedCodeReleaseSwitchAccepted", false);
        result.put("codeReleaseSwitchDigestVerified", false);
        result.put("codeReviewDigestVerified", false);
        result.put("testEvidenceDigestVerified", false);
        result.put("releaseDecisionDigestVerified", false);
        result.put("validationResultDigestVerified", false);
        result.put("trustedPrincipalValidated", false);
        result.put("stateMachineReleaseBound", false);
        result.put("durableExecutorReleaseBound", false);
        result.put("releaseDecisionAccepted", false);
        result.put("releaseCredentialIssued", false);
        result.put("releaseEligible", false);
        result.put("writePermitted", false);
        result.put("writeExecutionAllowed", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("realStorageTouched", false);
        result.put("codeReleaseSwitchStatus", SWITCH_LOCKED);
        result.put("releaseDecision", RELEASE_DENIED);
        result.put("validationStatus", VALIDATION_NOT_RUN);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("trustedPrincipalDigest", digestFor(principal));
        result.put("sourceReleaseDecisionContractDigest",
            text(releaseDecisionReport.get("releaseDecisionContractDigest")));
        result.put("sourceValidationResultContractDigest",
            text(releaseDecisionReport.get("sourceValidationResultContractDigest")));
        putSourceDigests(result, releaseDecisionReport);
        result.put("codeReleaseSwitchContractDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("codeReleaseSwitchContractDigest", inputAccepted ? digestFor(codeReleaseSwitchContract) : "");
        result.put("codeReleaseSwitchContract", codeReleaseSwitchContract);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(
            auditContext,
            principal,
            releaseDecisionReport,
            callerSwitchEvidence
        ));
        result.put("nextImplementationRequirements", List.of(
            "implement a reviewed server-owned NimCreateDurableAuditCodeReleaseSwitch issuer",
            "bind codeReleaseSwitchDigest to M5.21-71 releaseDecisionContractDigest and future releaseDecisionDigest",
            "require code review, test evidence, rollback plan and change-window digests before opening the switch",
            "reject caller supplied switch overrides, environment flags and legacy config flags",
            "keep write execution held until state machine and durable executor re-check the code switch digest"
        ));
        return result;
    }

    private static void validateAuditContext(Map<String, Object> auditContext,
                                             List<Map<String, Object>> blockers) {
        if (auditContext.isEmpty()
            || !Boolean.TRUE.equals(auditContext.get("auditPrepared"))
            || !NimCreateAuditReadinessSupport.AUDIT_EVENT_TYPE.equals(text(auditContext.get("auditEventType")))
            || !NimCreateStateMachineSupport.TARGET_TOOL.equals(text(auditContext.get("targetTool")))
            || !NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE.equals(
                text(auditContext.get("writeBodyProvenance")))
            || !Boolean.TRUE.equals(auditContext.get("secretRedactionApplied"))
            || !NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(auditContext.get("apiKeyHandling")))
            || !hasText(auditContext.get("requestId"))
            || !hasText(auditContext.get("conversationId"))
            || !hasText(auditContext.get("userId"))
            || !integerOrgId(text(auditContext.get("organizationId")))
            || !hasText(auditContext.get("displayName"))
            || !hasText(auditContext.get("image"))
            || !hasText(auditContext.get("templateId"))) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_CODE_RELEASE_SWITCH",
                "code release switch contract 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedSwitchClaim(auditContext)) {
            blockers.add(forgedClaimBlocker("auditContext"));
        }
    }

    private static void validateTrustedPrincipal(Map<String, Object> auditContext,
                                                 Map<String, Object> principal,
                                                 List<Map<String, Object>> blockers) {
        if (principal.isEmpty()
            || !Boolean.TRUE.equals(principal.get("authoritative"))
            || !"SERVER_SESSION_CONTEXT".equals(text(principal.get("source")))
            || !Boolean.TRUE.equals(principal.get("protectedFromCallerParams"))
            || !text(auditContext.get("organizationId")).equals(text(principal.get("organizationId")))
            || !text(auditContext.get("userId")).equals(text(principal.get("userId")))
            || !hasText(principal.get("username"))) {
            blockers.add(blocker(
                "TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY_FOR_CODE_RELEASE_SWITCH",
                "code release switch contract 必须绑定服务端可信 session principal，不能相信 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedSwitchClaim(principal)) {
            blockers.add(forgedClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateReleaseDecisionContractReport(Map<String, Object> auditContext,
                                                              Map<String, Object> principal,
                                                              Map<String, Object> report,
                                                              List<Map<String, Object>> blockers) {
        if (report.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_REPORT_NOT_READY",
                "缺少 M5.21-71 release decision contract report；不能定义 code release switch contract。",
                "durable-audit-release-decision-contract"
            ));
            return;
        }

        Map<String, Object> contract = objectMap(report.get("releaseDecisionContract"));
        boolean valid = NimCreateDurableAuditReleaseDecisionContractSupport.DECISION_CONTRACT_NAME.equals(
                text(report.get("durableAuditReleaseDecisionContract")))
            && NimCreateDurableAuditReleaseDecisionContractSupport.EXECUTION_MODE.equals(
                text(report.get("executionMode")))
            && NimCreateDurableAuditReleaseDecisionContractSupport.HOLD_STATE.equals(
                text(report.get("releaseDecisionState")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(report.get("futureReleaseDecision")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(report.get("futureValidationResult")))
            && NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_STATE_MACHINE_GATE.equals(
                text(report.get("futureStateMachineGate")))
            && NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_DURABLE_EXECUTOR_GATE.equals(
                text(report.get("futureDurableExecutorGate")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(report.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(report.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(report.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(report.get("networkAccess")))
            && "NONE".equals(text(report.get("sideEffect")))
            && Boolean.FALSE.equals(report.get("springBeanRegistered"))
            && Boolean.FALSE.equals(report.get("httpClientBound"))
            && Boolean.FALSE.equals(report.get("storageClientBound"))
            && Boolean.TRUE.equals(report.get("inputAccepted"))
            && Boolean.TRUE.equals(report.get("releaseDecisionContractPrepared"))
            && Boolean.TRUE.equals(report.get("serverIssuedReleaseDecisionRequired"))
            && Boolean.FALSE.equals(report.get("callerReleaseEvidenceAuthoritative"))
            && Boolean.TRUE.equals(report.get("validationResultContractRequired"))
            && Boolean.TRUE.equals(report.get("serverIssuedValidationResultDigestRequired"))
            && Boolean.FALSE.equals(report.get("legacyValidationResultReportAloneAllowed"))
            && Boolean.FALSE.equals(report.get("legacyAuditReceiptReleaseFlagTrusted"))
            && allReleaseDecisionReportSuccessStatesFalse(report)
            && VALIDATION_NOT_RUN.equals(text(report.get("validationStatus")))
            && RELEASE_DENIED.equals(text(report.get("releaseDecision")))
            && text(report.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(report.get("trustedPrincipalDigest")).equals(digestFor(principal))
            && text(report.get("sourceReleaseDecisionContractDigest")).isEmpty()
            && text(report.get("sourceValidationResultContractDigest")).matches("[a-f0-9]{64}")
            && sourceDigestFieldsAreHex(report)
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(
                text(report.get("releaseDecisionContractDigestAlgorithm")))
            && text(report.get("releaseDecisionContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("releaseDecisionContractDigest")).equals(digestFor(contract))
            && hasOnlyExpectedReleaseDecisionHold(report.get("blockedBy"))
            && releaseDecisionContractValid(auditContext, principal, report, contract);

        if (!valid) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_REPORT_INVALID_FOR_CODE_SWITCH",
                "code release switch contract 只能消费 M5.21-71 生成的、仍处于 HOLD 且未签发真实 release decision 的 contract report。",
                "durable-audit-release-decision-contract"
            ));
        }
        if (hasForgedSwitchClaim(report)) {
            blockers.add(forgedClaimBlocker("durableAuditReleaseDecisionContractReport"));
        }
    }

    private static boolean allReleaseDecisionReportSuccessStatesFalse(Map<String, Object> report) {
        return Boolean.FALSE.equals(report.get("realReleaseDecisionCreated"))
            && Boolean.FALSE.equals(report.get("serverIssuedReleaseDecisionAccepted"))
            && Boolean.FALSE.equals(report.get("realValidationResultAccepted"))
            && Boolean.FALSE.equals(report.get("validationResultDigestVerified"))
            && Boolean.FALSE.equals(report.get("validationResultContractDigestVerified"))
            && Boolean.FALSE.equals(report.get("releaseDecisionDigestVerified"))
            && Boolean.FALSE.equals(report.get("trustedPrincipalValidated"))
            && Boolean.FALSE.equals(report.get("codeReleaseSwitchVerified"))
            && Boolean.FALSE.equals(report.get("stateMachineReleaseBound"))
            && Boolean.FALSE.equals(report.get("durableExecutorReleaseBound"))
            && Boolean.FALSE.equals(report.get("releaseDecisionAccepted"))
            && Boolean.FALSE.equals(report.get("releaseCredentialIssued"))
            && Boolean.FALSE.equals(report.get("releaseEligible"))
            && Boolean.FALSE.equals(report.get("writePermitted"))
            && Boolean.FALSE.equals(report.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(report.get("realHttpExecutionAllowed"))
            && Boolean.FALSE.equals(report.get("realStorageTouched"));
    }

    private static boolean releaseDecisionContractValid(Map<String, Object> auditContext,
                                                        Map<String, Object> principal,
                                                        Map<String, Object> report,
                                                        Map<String, Object> contract) {
        return !contract.isEmpty()
            && digestFor(auditContext).equals(text(contract.get("sourceAuditEventDigest")))
            && digestFor(principal).equals(text(contract.get("trustedPrincipalDigest")))
            && text(auditContext.get("organizationId")).equals(text(report.get("sourceOrganizationId")))
            && text(auditContext.get("userId")).equals(text(report.get("sourceUserId")))
            && text(principal.get("username")).equals(text(report.get("sourceUsername")))
            && sourceDigestFieldsMatch(report, contract)
            && contract.equals(
                NimCreateDurableAuditReleaseDecisionContractSupport
                    .releaseDecisionContractFromReport(report));
    }

    private static boolean sourceDigestFieldsAreHex(Map<String, Object> report) {
        for (String key : sourceDigestKeys()) {
            if (!text(report.get(key)).matches("[a-f0-9]{64}")) {
                return false;
            }
        }
        return true;
    }

    private static boolean sourceDigestFieldsMatch(Map<String, Object> report, Map<String, Object> contract) {
        for (String key : sourceDigestKeys()) {
            if (!text(report.get(key)).equals(text(contract.get(key)))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> sourceDigestKeys() {
        return List.of(
            "sourceEnhancedMigrationPlanDigest",
            "sourceProbeBindingPlanDigest",
            "sourceProbeResultContractDigest",
            "sourceProbeExecutorPlanDigest",
            "sourceMigrationPlanDigest",
            "sourceReceiptSchemaDigest",
            "sourceValidationPlanDigest",
            "sourceInterfaceSpecDigest",
            "sourceBoundaryPlanDigest",
            "sourceWriterPlanDigest",
            "sourceAvailabilityPlanDigest"
        );
    }

    private static void validateCallerSwitchEvidence(Map<String, Object> callerSwitchEvidence,
                                                     List<Map<String, Object>> blockers) {
        if (!callerSwitchEvidence.isEmpty()) {
            blockers.add(blocker(
                "CALLER_CODE_RELEASE_SWITCH_EVIDENCE_NOT_AUTHORITATIVE",
                "调用方提供的 code release switch、环境变量、配置开关或审查记录无权参与 release switch 签发。",
                "caller-switch-evidence"
            ));
        }
        if (hasForgedSwitchClaim(callerSwitchEvidence)) {
            blockers.add(forgedClaimBlocker("callerSwitchEvidence"));
        }
    }

    private static Map<String, Object> codeReleaseSwitchContract(Map<String, Object> auditContext,
                                                                 Map<String, Object> principal,
                                                                 Map<String, Object> releaseDecisionReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contractBoundary", "REVIEWED_SERVER_OWNED_CODE_RELEASE_SWITCH_REQUIRED");
        contract.put("type", FUTURE_CODE_RELEASE_SWITCH);
        contract.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        contract.put("futureOnly", true);
        contract.put("instanceAllowedNow", false);
        contract.put("currentSwitchState", SWITCH_LOCKED);
        contract.put("requiredSwitchState", REQUIRED_SWITCH_STATE);
        contract.put("serverOwnedRequired", true);
        contract.put("callerProvidedSwitchAllowed", false);
        contract.put("environmentOverrideAllowed", false);
        contract.put("runtimeFlagFallbackAllowed", false);
        contract.put("sourceReleaseDecisionContractDigest",
            text(releaseDecisionReport.get("releaseDecisionContractDigest")));
        contract.put("sourceValidationResultContractDigest",
            text(releaseDecisionReport.get("sourceValidationResultContractDigest")));
        putSourceDigests(contract, releaseDecisionReport);
        contract.put("sourceAuditEventDigest", digestFor(auditContext));
        contract.put("trustedPrincipalDigest", digestFor(principal));
        contract.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        contract.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        contract.put("releaseDecisionBinding", releaseDecisionBinding(releaseDecisionReport));
        contract.put("writeChainBinding", writeChainBinding());
        contract.put("reviewBinding", reviewBinding());
        contract.put("stateMachineBinding", stateMachineBinding());
        contract.put("durableExecutorBinding", durableExecutorBinding());
        contract.put("requiredFutureEvidenceDigestFields", requiredFutureSwitchDigestFields());
        contract.put("currentTemplate", currentSwitchTemplate());
        contract.put("openPrerequisites", openPrerequisites());
        contract.put("failureContract", switchFailureContract());
        contract.put("forbiddenShortcuts", forbiddenShortcuts());
        return contract;
    }

    private static void putSourceDigests(Map<String, Object> target, Map<String, Object> source) {
        for (String key : sourceDigestKeys()) {
            target.put(key, text(source.get(key)));
        }
    }

    private static Map<String, Object> releaseDecisionBinding(Map<String, Object> releaseDecisionReport) {
        return codeReleaseSwitchReleaseDecisionBinding(releaseDecisionReport);
    }

    private static Map<String, Object> writeChainBinding() {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("futureBodyDigestRequired", true);
        binding.put("futureRequestSpecDigestRequired", true);
        binding.put("futureHandoffDigestRequired", true);
        binding.put("futureAuditReceiptIdRequired", true);
        binding.put("futureServerDerivedIdempotencyKeyRequired", true);
        binding.put("mustBeRecheckedByStateMachine", true);
        binding.put("mustBeRecheckedByDurableExecutor", true);
        binding.put("fallbackToHandoffOnlyAllowed", false);
        binding.put("fallbackToRequestSpecOnlyAllowed", false);
        return binding;
    }

    private static Map<String, Object> reviewBinding() {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("codeReviewDigestRequired", true);
        binding.put("testEvidenceDigestRequired", true);
        binding.put("securityApprovalDigestRequired", true);
        binding.put("rollbackPlanDigestRequired", true);
        binding.put("changeWindowDigestRequired", true);
        binding.put("reviewedByMaintainerRequired", true);
        binding.put("currentReviewSatisfied", false);
        return binding;
    }

    private static Map<String, Object> stateMachineBinding() {
        return codeReleaseSwitchStateMachineBinding();
    }

    private static Map<String, Object> durableExecutorBinding() {
        return codeReleaseSwitchDurableExecutorBinding();
    }

    private static List<String> requiredFutureSwitchDigestFields() {
        return List.of(
            "releaseDecisionContractDigest",
            "validationResultContractDigest",
            "validationResultDigest",
            "releaseDecisionDigest",
            "codeReleaseSwitchDigest",
            "codeReviewDigest",
            "testEvidenceDigest",
            "securityApprovalDigest",
            "rollbackPlanDigest",
            "changeWindowDigest",
            "bodyDigest",
            "requestSpecDigest",
            "handoffDigest",
            "auditReceiptId",
            "sourceAuditEventDigest",
            "trustedPrincipalDigest",
            "serverDerivedIdempotencyKey"
        );
    }

    private static Map<String, Object> currentSwitchTemplate() {
        return codeReleaseSwitchCurrentTemplate();
    }

    private static Map<String, Object> openPrerequisites() {
        return codeReleaseSwitchOpenPrerequisites();
    }

    private static Map<String, Object> switchFailureContract() {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failClosed", true);
        failure.put("fallbackToCallerSwitchAllowed", false);
        failure.put("fallbackToEnvironmentVariableAllowed", false);
        failure.put("fallbackToRuntimeFlagAllowed", false);
        failure.put("fallbackToReleaseDecisionContractAllowed", false);
        failure.put("fallbackToStateMachineBooleanAllowed", false);
        failure.put("fallbackToExecutorSuccessAllowed", false);
        failure.put("failureStatuses", codeReleaseSwitchFailureStatuses());
        return failure;
    }

    private static List<String> forbiddenShortcuts() {
        return codeReleaseSwitchForbiddenShortcuts();
    }

    private static boolean hasOnlyExpectedReleaseDecisionHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_IMPLEMENTATION_HOLD".equals(
                text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_CODE_RELEASE_SWITCH_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        return NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            map,
            NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()
        );
    }

    private static boolean hasForgedSwitchClaim(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForgedSwitchClaim(entry.getKey(), value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && hasForgedSwitchClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && hasForgedSwitchClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isForgedSwitchClaim(String key, Object value) {
        return switch (key) {
            case "realCodeReleaseSwitchCreated",
                "realCodeReleaseSwitchOpened",
                "serverOwnedCodeReleaseSwitchAccepted",
                "codeReleaseSwitchDigestVerified",
                "codeReviewDigestVerified",
                "testEvidenceDigestVerified",
                "securityApprovalDigestVerified",
                "rollbackPlanDigestVerified",
                "changeWindowDigestVerified",
                "realValidationResultCreated",
                "serverIssuedValidationResultAccepted",
                "realValidationResultAccepted",
                "realReleaseDecisionCreated",
                "serverIssuedReleaseDecisionAccepted",
                "releaseDecisionGateReportAccepted",
                "realStateMachineReleaseDecisionGateReportAccepted",
                "releaseDecisionGateDigestVerified",
                "validationResultDigestVerified",
                "validationResultContractDigestVerified",
                "releaseDecisionDigestVerified",
                "trustedPrincipalValidated",
                "stateMachineReleaseBound",
                "durableExecutorReleaseBound",
                "validationPassed",
                "releaseDecisionAccepted",
                "releaseCredentialIssued",
                "releaseEligible",
                "writePermitted",
                "writeExecutionAllowed",
                "realHttpExecutionAllowed",
                "writeExecuted",
                "postWriteReadinessTriggered",
                "realStorageTouched",
                "durable" -> Boolean.TRUE.equals(value);
            case "switchState",
                "codeReleaseSwitchStatus" -> Set.of("OPEN", REQUIRED_SWITCH_STATE, "ENABLED", "APPROVED")
                .contains(text(value));
            case "decision",
                "releaseDecision" -> "ALLOW_WRITE_EXECUTION".equals(text(value)) || value instanceof Map<?, ?>;
            case "codeReleaseSwitch",
                "runtimeReleaseFlag",
                "environmentReleaseOverride",
                "validationResult",
                "auditReceipt",
                "legacyAuditReceipt",
                "releaseCredential",
                "writeResult" -> value != null;
            case "deploymentId",
                "deploymentUid" -> hasText(value);
            default -> false;
        };
    }

    private static Map<String, Object> forgedClaimBlocker(String source) {
        return blocker(
            "DURABLE_AUDIT_CODE_RELEASE_SWITCH_FORGED_OPEN_CLAIM",
            source + " 不得自称 code release switch 已打开、release eligible、write permitted 或真实写执行成功。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> releaseDecisionReport,
                                                    Map<String, Object> callerSwitchEvidence) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "codeReleaseSwitch",
            "runtimeReleaseFlag",
            "environmentReleaseOverride",
            "switchState",
            "codeReleaseSwitchStatus",
            "validationResult",
            "releaseDecision",
            "auditReceipt",
            "legacyAuditReceipt",
            "releaseCredential",
            "validationStatus",
            "validationPassed",
            "releaseDecisionAccepted",
            "releaseEligible",
            "writePermitted",
            "writeExecutionAllowed",
            "writeExecuted",
            "deploymentId",
            "deploymentUid",
            "writeResult"
        )) {
            if (auditContext.containsKey(key)
                || principal.containsKey(key)
                || releaseDecisionReport.containsKey(key)
                || callerSwitchEvidence.containsKey(key)) {
                ignored.add(key);
            }
        }
        return ignored;
    }

    private static String digestFor(Map<String, Object> value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", ex);
        }
    }

    private static String canonical(Object value) {
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

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = objectMap(item);
            if (!map.isEmpty()) {
                items.add(map);
            }
        }
        return items;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }

    private static Map<String, Object> blocker(String code, String message, String source) {
        Map<String, Object> blocker = new LinkedHashMap<>();
        blocker.put("code", code);
        blocker.put("message", message);
        blocker.put("source", source);
        return blocker;
    }

    private static boolean integerOrgId(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record CodeReleaseSwitchContractInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditReleaseDecisionContractReport,
        Map<String, Object> callerSwitchEvidence
    ) {
        CodeReleaseSwitchContractInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditReleaseDecisionContractReport =
                durableAuditReleaseDecisionContractReport == null
                    ? Map.of()
                    : objectMap(durableAuditReleaseDecisionContractReport);
            callerSwitchEvidence = callerSwitchEvidence == null ? Map.of() : objectMap(callerSwitchEvidence);
        }

        static CodeReleaseSwitchContractInput empty() {
            return new CodeReleaseSwitchContractInput(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
