package com.atlas.tool.impl;

import com.atlas.tool.core.NimForbiddenSecretMaterialDetector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * NIM code release switch 的状态机 / durable executor 回接契约。
 *
 * <p>本类只把 M5.21-72 产出的 future code release switch contract report 转成下一层运行时回接要求：
 * 状态机必须复算 switch contract digest，durable executor 必须在真实 POST 前再次复核同一个 switch digest。
 * 当前仍不打开真实开关，不接入 HTTP client，不注册 Spring Bean，不执行写入，也不信任任何 caller/runtime 自报放行。</p>
 */
final class NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport {

    static final String BINDING_CONTRACT_NAME =
        "NIM_CREATE_CODE_RELEASE_SWITCH_RUNTIME_BINDING_CONTRACT";
    static final String EXECUTION_MODE =
        "CODE_RELEASE_SWITCH_RUNTIME_BINDING_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String TARGET_STATE_MACHINE = "NimCreateStateMachineSupport";
    static final String TARGET_DURABLE_EXECUTOR = NimCreateDurableWriteExecutorSupport.EXECUTOR_NAME;

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String REQUIRED_SWITCH_STATE = "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION";
    private static final String SWITCH_LOCKED = "LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH";
    private static final String RELEASE_DENIED = "DENY_UNTIL_SERVER_VALIDATION_RESULT";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";

    private NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport() {
    }

    static Map<String, Object> plan(CodeReleaseSwitchRuntimeBindingInput input) {
        CodeReleaseSwitchRuntimeBindingInput safeInput = input == null
            ? CodeReleaseSwitchRuntimeBindingInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> switchReport = safeInput.durableAuditCodeReleaseSwitchContractReport();
        Map<String, Object> stateMachineEvidence = safeInput.stateMachineReleaseEvidence();
        Map<String, Object> durableExecutorEvidence = safeInput.durableExecutorReleaseEvidence();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateCodeReleaseSwitchContractReport(auditContext, principal, switchReport, blockers);
        validateRuntimeEvidence("stateMachineReleaseEvidence", stateMachineEvidence, blockers);
        validateRuntimeEvidence("durableExecutorReleaseEvidence", durableExecutorEvidence, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditCodeReleaseSwitchContractReport", switchReport, blockers);
        validateNoSecretMaterial("stateMachineReleaseEvidence", stateMachineEvidence, blockers);
        validateNoSecretMaterial("durableExecutorReleaseEvidence", durableExecutorEvidence, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> bindingContract = inputAccepted
            ? runtimeBindingContract(auditContext, principal, switchReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_BINDING_IMPLEMENTATION_HOLD",
                "code release switch runtime binding 契约已定义，但真实状态机回接、durable executor 回接和 reviewed open switch 尚未实现；当前不能放行写执行。",
                "code-release-switch-runtime-binding"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("codeReleaseSwitchRuntimeBindingContract", BINDING_CONTRACT_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("bindingState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("targetStateMachine", TARGET_STATE_MACHINE);
        result.put("targetDurableExecutor", TARGET_DURABLE_EXECUTOR);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("springBeanRegistered", false);
        result.put("httpClientBound", false);
        result.put("storageClientBound", false);
        result.put("toolRegistered", false);
        result.put("controllerRegistered", false);
        result.put("inputAccepted", inputAccepted);
        result.put("runtimeBindingContractPrepared", inputAccepted);
        result.put("codeReleaseSwitchContractReportRequired", true);
        result.put("stateMachineRuntimeBindingRequired", true);
        result.put("durableExecutorRuntimeBindingRequired", true);
        result.put("stateMachineReleaseEvidenceAuthoritative", false);
        result.put("durableExecutorReleaseEvidenceAuthoritative", false);
        result.put("serverOwnedOpenSwitchRequired", true);
        result.put("reviewedCodeSwitchDigestRequired", true);
        result.put("legacyNimCreateReleasedBooleanAuthoritative", false);
        result.put("runtimeFlagOverrideAllowed", false);
        result.put("environmentVariableOverrideAllowed", false);
        result.put("codeReleaseSwitchDigestVerified", false);
        result.put("codeReviewDigestVerified", false);
        result.put("testEvidenceDigestVerified", false);
        result.put("releaseDecisionDigestVerified", false);
        result.put("validationResultDigestVerified", false);
        result.put("trustedPrincipalValidated", false);
        result.put("runtimeBindingInstalled", false);
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
        result.put("requiredSwitchState", REQUIRED_SWITCH_STATE);
        result.put("releaseDecision", RELEASE_DENIED);
        result.put("validationStatus", VALIDATION_NOT_RUN);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("trustedPrincipalDigest", digestFor(principal));
        result.put("sourceCodeReleaseSwitchContractDigest",
            text(switchReport.get("codeReleaseSwitchContractDigest")));
        result.put("sourceReleaseDecisionContractDigest",
            text(switchReport.get("sourceReleaseDecisionContractDigest")));
        result.put("sourceValidationResultContractDigest",
            text(switchReport.get("sourceValidationResultContractDigest")));
        putSourceDigests(result, switchReport);
        result.put("runtimeBindingContractDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("runtimeBindingContractDigest", inputAccepted ? digestFor(bindingContract) : "");
        result.put("runtimeBindingContract", bindingContract);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(
            auditContext,
            principal,
            switchReport,
            stateMachineEvidence,
            durableExecutorEvidence
        ));
        result.put("nextImplementationRequirements", List.of(
            "add reviewed codeReleaseSwitchContractReport input to NimCreateStateMachineSupport",
            "make state machine recompute M5.21-72 codeReleaseSwitchContractDigest before writePermitted",
            "make durable executor re-check codeReleaseSwitchDigest immediately before any real POST",
            "replace legacy nimCreateReleased boolean with reviewed server-owned open switch evidence",
            "keep write execution held until switch digest, release decision digest and validation result digest are all verified"
        ));
        return result;
    }

    private static Map<String, Object> runtimeBindingContract(Map<String, Object> auditContext,
                                                              Map<String, Object> principal,
                                                              Map<String, Object> switchReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contractBoundary", "CODE_RELEASE_SWITCH_RUNTIME_BINDING_REQUIRED");
        contract.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        contract.put("futureOnly", true);
        contract.put("instanceAllowedNow", false);
        contract.put("sourceCodeReleaseSwitchContract",
            NimCreateDurableAuditCodeReleaseSwitchContractSupport.SWITCH_CONTRACT_NAME);
        contract.put("sourceCodeReleaseSwitchContractDigest",
            text(switchReport.get("codeReleaseSwitchContractDigest")));
        contract.put("sourceReleaseDecisionContractDigest",
            text(switchReport.get("sourceReleaseDecisionContractDigest")));
        contract.put("sourceValidationResultContractDigest",
            text(switchReport.get("sourceValidationResultContractDigest")));
        putSourceDigests(contract, switchReport);
        contract.put("sourceAuditEventDigest", digestFor(auditContext));
        contract.put("trustedPrincipalDigest", digestFor(principal));
        contract.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        contract.put("stateMachineRuntimeBinding", stateMachineRuntimeBinding(switchReport));
        contract.put("durableExecutorRuntimeBinding", durableExecutorRuntimeBinding(switchReport));
        contract.put("requiredFutureRuntimeEvidenceDigestFields", requiredFutureRuntimeDigestFields());
        contract.put("currentRuntimeTemplate", currentRuntimeTemplate());
        contract.put("failureContract", failureContract());
        contract.put("forbiddenShortcuts", forbiddenShortcuts());
        return contract;
    }

    private static Map<String, Object> stateMachineRuntimeBinding(Map<String, Object> switchReport) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("target", TARGET_STATE_MACHINE);
        binding.put("futureReadinessRequestField", "codeReleaseSwitchContractReport");
        binding.put("sourceCodeReleaseSwitchContractDigest",
            text(switchReport.get("codeReleaseSwitchContractDigest")));
        binding.put("codeReleaseSwitchContractReportRequired", true);
        binding.put("codeReleaseSwitchContractDigestRequired", true);
        binding.put("mustRecomputeCodeReleaseSwitchContractDigest", true);
        binding.put("mustRequireServerOwnedOpenSwitch", true);
        binding.put("mustBindServerIssuedReleaseDecisionDigest", true);
        binding.put("mustBindServerIssuedValidationResultDigest", true);
        binding.put("mustBindWriteChainDigests", true);
        binding.put("legacyNimCreateReleasedBooleanAuthoritative", false);
        binding.put("fallbackToRuntimeFlagAllowed", false);
        binding.put("fallbackToEnvironmentVariableAllowed", false);
        binding.put("fallbackToReleaseDecisionContractAllowed", false);
        binding.put("writePermittedCanBeTrueNow", false);
        return binding;
    }

    private static Map<String, Object> durableExecutorRuntimeBinding(Map<String, Object> switchReport) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("target", TARGET_DURABLE_EXECUTOR);
        binding.put("sourceCodeReleaseSwitchContractDigest",
            text(switchReport.get("codeReleaseSwitchContractDigest")));
        binding.put("codeReleaseSwitchDigestRequired", true);
        binding.put("mustRecheckImmediatelyBeforePost", true);
        binding.put("mustBindSameHandoffDigest", true);
        binding.put("mustBindSameRequestSpecDigest", true);
        binding.put("mustBindSameBodyDigest", true);
        binding.put("mustBindServerDerivedIdempotencyKey", true);
        binding.put("fallbackToStateMachineWritePermittedAllowed", false);
        binding.put("fallbackToStateMachineFlagOnlyAllowed", false);
        binding.put("fallbackToExecutorSuccessAllowed", false);
        binding.put("writeExecutionAllowedNow", false);
        return binding;
    }

    private static Map<String, Object> currentRuntimeTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("switchState", SWITCH_LOCKED);
        template.put("requiredSwitchState", REQUIRED_SWITCH_STATE);
        template.put("codeReleaseSwitchContractReportAccepted", false);
        template.put("codeReleaseSwitchDigestVerified", false);
        template.put("codeReviewDigestVerified", false);
        template.put("testEvidenceDigestVerified", false);
        template.put("releaseDecisionDigestVerified", false);
        template.put("validationResultDigestVerified", false);
        template.put("trustedPrincipalValidated", false);
        template.put("stateMachineRuntimeBindingInstalled", false);
        template.put("durableExecutorRuntimeBindingInstalled", false);
        template.put("stateMachineReleaseBound", false);
        template.put("durableExecutorReleaseBound", false);
        template.put("releaseEligible", false);
        template.put("writePermitted", false);
        template.put("writeExecutionAllowed", false);
        template.put("realHttpExecutionAllowed", false);
        return template;
    }

    private static Map<String, Object> failureContract() {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failClosed", true);
        failure.put("fallbackToCodeReleaseSwitchContractReportAllowed", false);
        failure.put("fallbackToCallerRuntimeEvidenceAllowed", false);
        failure.put("fallbackToEnvironmentVariableAllowed", false);
        failure.put("fallbackToRuntimeFlagAllowed", false);
        failure.put("fallbackToNimCreateReleasedBooleanAllowed", false);
        failure.put("fallbackToStateMachineWritePermittedAllowed", false);
        failure.put("fallbackToDurableExecutorSuccessAllowed", false);
        return failure;
    }

    private static List<String> requiredFutureRuntimeDigestFields() {
        return List.of(
            "codeReleaseSwitchContractDigest",
            "codeReleaseSwitchDigest",
            "codeReviewDigest",
            "testEvidenceDigest",
            "securityApprovalDigest",
            "rollbackPlanDigest",
            "changeWindowDigest",
            "releaseDecisionDigest",
            "validationResultDigest",
            "bodyDigest",
            "requestSpecDigest",
            "handoffDigest",
            "auditReceiptId",
            "serverDerivedIdempotencyKey",
            "sourceAuditEventDigest",
            "trustedPrincipalDigest"
        );
    }

    private static List<String> forbiddenShortcuts() {
        return List.of(
            "treating nimCreateReleased=true as the reviewed code release switch",
            "treating M5.21-72 contract report as an already-open switch",
            "accepting caller or runtime evidence as server-owned switch binding",
            "allowing state machine writePermitted without recomputing switch digest",
            "allowing durable executor POST without rechecking the same switch digest",
            "backfilling code release switch evidence from executor success"
        );
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_CODE_RELEASE_SWITCH_RUNTIME_BINDING",
                "code release switch runtime binding 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedRuntimeReleaseClaim(auditContext)) {
            blockers.add(forgedRuntimeClaimBlocker("auditContext"));
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
                "TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY_FOR_CODE_RELEASE_SWITCH_RUNTIME_BINDING",
                "runtime binding 必须绑定服务端可信 session principal，不能相信 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedRuntimeReleaseClaim(principal)) {
            blockers.add(forgedRuntimeClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateCodeReleaseSwitchContractReport(Map<String, Object> auditContext,
                                                                Map<String, Object> principal,
                                                                Map<String, Object> report,
                                                                List<Map<String, Object>> blockers) {
        if (report.isEmpty()) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_NOT_READY_FOR_RUNTIME_BINDING",
                "缺少 M5.21-72 code release switch contract report；状态机/执行器不能定义回接契约。",
                "code-release-switch-contract"
            ));
            return;
        }

        Map<String, Object> contract = objectMap(report.get("codeReleaseSwitchContract"));
        Map<String, Object> releaseDecisionBinding = objectMap(contract.get("releaseDecisionBinding"));
        Map<String, Object> stateMachineBinding = objectMap(contract.get("stateMachineBinding"));
        Map<String, Object> durableExecutorBinding = objectMap(contract.get("durableExecutorBinding"));
        Map<String, Object> template = objectMap(contract.get("currentTemplate"));
        Map<String, Object> prerequisites = objectMap(contract.get("openPrerequisites"));
        Map<String, Object> failure = objectMap(contract.get("failureContract"));
        List<String> requiredFields = stringList(contract.get("requiredFutureEvidenceDigestFields"));
        boolean valid = NimCreateDurableAuditCodeReleaseSwitchContractSupport.SWITCH_CONTRACT_NAME.equals(
                text(report.get("durableAuditCodeReleaseSwitchContract")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.EXECUTION_MODE.equals(
                text(report.get("executionMode")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.HOLD_STATE.equals(
                text(report.get("switchState")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.FUTURE_CODE_RELEASE_SWITCH.equals(
                text(report.get("futureCodeReleaseSwitch")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(report.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(report.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(report.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(report.get("networkAccess")))
            && "NONE".equals(text(report.get("sideEffect")))
            && Boolean.TRUE.equals(report.get("inputAccepted"))
            && Boolean.TRUE.equals(report.get("codeReleaseSwitchContractPrepared"))
            && Boolean.TRUE.equals(report.get("serverOwnedCodeReleaseSwitchRequired"))
            && Boolean.TRUE.equals(report.get("reviewedCodeSwitchDigestRequired"))
            && Boolean.FALSE.equals(report.get("callerSwitchEvidenceAuthoritative"))
            && switchStatesRemainFalse(report)
            && text(report.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(report.get("trustedPrincipalDigest")).equals(digestFor(principal))
            && text(report.get("codeReleaseSwitchContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("codeReleaseSwitchContractDigest")).equals(digestFor(contract))
            && hasOnlyExpectedSwitchHold(report.get("blockedBy"))
            && "REVIEWED_SERVER_OWNED_CODE_RELEASE_SWITCH_REQUIRED".equals(
                text(contract.get("contractBoundary")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.FUTURE_CODE_RELEASE_SWITCH.equals(
                text(contract.get("type")))
            && Boolean.TRUE.equals(contract.get("futureOnly"))
            && Boolean.FALSE.equals(contract.get("instanceAllowedNow"))
            && SWITCH_LOCKED.equals(text(contract.get("currentSwitchState")))
            && REQUIRED_SWITCH_STATE.equals(text(contract.get("requiredSwitchState")))
            && Boolean.TRUE.equals(contract.get("serverOwnedRequired"))
            && Boolean.FALSE.equals(contract.get("callerProvidedSwitchAllowed"))
            && Boolean.FALSE.equals(contract.get("environmentOverrideAllowed"))
            && Boolean.FALSE.equals(contract.get("runtimeFlagFallbackAllowed"))
            && text(report.get("sourceReleaseDecisionContractDigest")).equals(
                text(contract.get("sourceReleaseDecisionContractDigest")))
            && text(report.get("sourceValidationResultContractDigest")).equals(
                text(contract.get("sourceValidationResultContractDigest")))
            && sourceDigestFieldsMatch(report, contract)
            && digestFor(auditContext).equals(text(contract.get("sourceAuditEventDigest")))
            && digestFor(principal).equals(text(contract.get("trustedPrincipalDigest")))
            && releaseDecisionBindingValid(report, releaseDecisionBinding)
            && stateMachineBindingValid(report, stateMachineBinding)
            && durableExecutorBindingValid(durableExecutorBinding)
            && requiredFields.equals(requiredSwitchEvidenceFields())
            && template.equals(
                NimCreateDurableAuditCodeReleaseSwitchContractSupport.codeReleaseSwitchCurrentTemplate())
            && prerequisites.equals(
                NimCreateDurableAuditCodeReleaseSwitchContractSupport.codeReleaseSwitchOpenPrerequisites())
            && Boolean.TRUE.equals(failure.get("failClosed"))
            && Boolean.FALSE.equals(failure.get("fallbackToCallerSwitchAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToEnvironmentVariableAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToRuntimeFlagAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToReleaseDecisionContractAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToStateMachineBooleanAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToExecutorSuccessAllowed"));

        if (!valid) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_INVALID_FOR_RUNTIME_BINDING",
                "runtime binding 只能消费 M5.21-72 产出的 HOLD、digest 可复算、未打开真实开关的 code release switch contract report。",
                "code-release-switch-contract"
            ));
        }
        if (hasForgedRuntimeReleaseClaim(report)) {
            blockers.add(forgedRuntimeClaimBlocker("durableAuditCodeReleaseSwitchContractReport"));
        }
    }

    private static boolean releaseDecisionBindingValid(Map<String, Object> report,
                                                       Map<String, Object> binding) {
        return text(report.get("sourceReleaseDecisionContractDigest")).equals(
                text(binding.get("sourceReleaseDecisionContractDigest")))
            && text(report.get("sourceValidationResultContractDigest")).equals(
                text(binding.get("sourceValidationResultContractDigest")))
            && sourceDigestFieldsMatch(report, binding)
            && text(report.get("trustedPrincipalDigest")).equals(text(binding.get("trustedPrincipalDigest")))
            && Boolean.TRUE.equals(binding.get("futureReleaseDecisionDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureValidationResultDigestRequired"))
            && Boolean.TRUE.equals(binding.get("mustBindServerIssuedReleaseDecisionDigest"))
            && Boolean.TRUE.equals(binding.get("mustBindServerIssuedValidationResultDigest"))
            && Boolean.TRUE.equals(binding.get("mustBindTrustedPrincipalDigest"))
            && Boolean.TRUE.equals(binding.get("mustBindAuditEventDigest"))
            && Boolean.FALSE.equals(binding.get("callerReleaseDecisionAllowed"));
    }

    private static boolean stateMachineBindingValid(Map<String, Object> report,
                                                    Map<String, Object> binding) {
        return TARGET_STATE_MACHINE.equals(text(binding.get("target")))
            && Boolean.TRUE.equals(binding.get("futureCodeReleaseSwitchDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureReleaseDecisionDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureValidationResultDigestRequired"))
            && Boolean.TRUE.equals(binding.get("mustRecomputeSwitchDigestBeforeWritePermitted"))
            && Boolean.FALSE.equals(binding.get("fallbackToRuntimeFlagAllowed"))
            && Boolean.FALSE.equals(binding.get("fallbackToEnvironmentVariableAllowed"))
            && Boolean.FALSE.equals(binding.get("writePermittedCanBeTrueNow"))
            && hasText(report.get("codeReleaseSwitchContractDigest"));
    }

    private static boolean durableExecutorBindingValid(Map<String, Object> binding) {
        return TARGET_DURABLE_EXECUTOR.equals(text(binding.get("target")))
            && Boolean.TRUE.equals(binding.get("futureCodeReleaseSwitchDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureReleaseDecisionDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureValidationResultDigestRequired"))
            && Boolean.TRUE.equals(binding.get("mustRecheckImmediatelyBeforePost"))
            && Boolean.FALSE.equals(binding.get("fallbackToStateMachineFlagOnlyAllowed"))
            && Boolean.FALSE.equals(binding.get("writeExecutionAllowedNow"));
    }

    private static boolean switchStatesRemainFalse(Map<String, Object> report) {
        return Boolean.FALSE.equals(report.get("realCodeReleaseSwitchCreated"))
            && Boolean.FALSE.equals(report.get("realCodeReleaseSwitchOpened"))
            && Boolean.FALSE.equals(report.get("serverOwnedCodeReleaseSwitchAccepted"))
            && Boolean.FALSE.equals(report.get("codeReleaseSwitchDigestVerified"))
            && Boolean.FALSE.equals(report.get("codeReviewDigestVerified"))
            && Boolean.FALSE.equals(report.get("testEvidenceDigestVerified"))
            && Boolean.FALSE.equals(report.get("releaseDecisionDigestVerified"))
            && Boolean.FALSE.equals(report.get("validationResultDigestVerified"))
            && Boolean.FALSE.equals(report.get("trustedPrincipalValidated"))
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

    private static List<String> requiredSwitchEvidenceFields() {
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

    private static void validateRuntimeEvidence(String source,
                                                Map<String, Object> evidence,
                                                List<Map<String, Object>> blockers) {
        if (!evidence.isEmpty()) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_EVIDENCE_NOT_AUTHORITATIVE",
                source + " 当前不能作为 code release switch 回接放行依据；必须等待 reviewed server-owned runtime binding。",
                source
            ));
        }
        if (hasForgedRuntimeReleaseClaim(evidence)) {
            blockers.add(forgedRuntimeClaimBlocker(source));
        }
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_BINDING_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean hasForgedRuntimeReleaseClaim(Map<String, Object> map) {
        if (map.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (forgedBooleanClaim(key, value) || forgedTextClaim(key, value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && hasForgedRuntimeReleaseClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem
                        && hasForgedRuntimeReleaseClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean forgedBooleanClaim(String key, Object value) {
        if (!Boolean.TRUE.equals(value)) {
            return false;
        }
        String normalized = normalizeKey(key);
        return List.of(
            "serverownedcodereleaseswitchaccepted",
            "codereleaseswitchdigestverified",
            "codereviewdigestverified",
            "testevidencedigestverified",
            "releasedecisiondigestverified",
            "validationresultdigestverified",
            "trustedprincipalvalidated",
            "runtimebindinginstalled",
            "statemachinereleasebound",
            "durableexecutorreleasebound",
            "releasedecisionaccepted",
            "releasecredentialissued",
            "releaseeligible",
            "writepermitted",
            "writeexecutionallowed",
            "realhttpexecutionallowed",
            "writeattempted",
            "writeexecuted",
            "postwritereadinesstriggered"
        ).contains(normalized);
    }

    private static boolean forgedTextClaim(String key, Object value) {
        String normalized = normalizeKey(key);
        String textValue = text(value);
        return ("switchstate".equals(normalized) || "codereleaseswitchstatus".equals(normalized))
            && REQUIRED_SWITCH_STATE.equals(textValue);
    }

    private static Map<String, Object> forgedRuntimeClaimBlocker(String source) {
        return blocker(
            "CODE_RELEASE_SWITCH_RUNTIME_BINDING_FORGED_RELEASE_CLAIM",
            source + " 声称 code switch 已打开、release/write 已放行或 executor 已成功；当前契约必须 fail-closed。",
            source
        );
    }

    private static List<Map<String, Object>> ignoredCallerClaims(Map<String, Object> auditContext,
                                                                 Map<String, Object> principal,
                                                                 Map<String, Object> switchReport,
                                                                 Map<String, Object> stateMachineEvidence,
                                                                 Map<String, Object> durableExecutorEvidence) {
        List<Map<String, Object>> ignored = new ArrayList<>();
        collectIgnored("auditContext", auditContext, ignored);
        collectIgnored("trustedPrincipalSnapshot", principal, ignored);
        collectIgnored("durableAuditCodeReleaseSwitchContractReport", switchReport, ignored);
        collectIgnored("stateMachineReleaseEvidence", stateMachineEvidence, ignored);
        collectIgnored("durableExecutorReleaseEvidence", durableExecutorEvidence, ignored);
        return ignored;
    }

    private static void collectIgnored(String source,
                                       Map<String, Object> map,
                                       List<Map<String, Object>> ignored) {
        for (String key : List.of(
            "nimCreateReleased",
            "codeReleaseSwitch",
            "codeReleaseSwitchDigest",
            "codeReleaseSwitchDigestVerified",
            "switchState",
            "runtimeFlag",
            "environmentOverride",
            "writePermitted",
            "writeExecutionAllowed",
            "writeExecuted",
            "releaseEligible",
            "releaseCredentialIssued"
        )) {
            if (map.containsKey(key)) {
                Map<String, Object> claim = new LinkedHashMap<>();
                claim.put("source", source);
                claim.put("key", key);
                claim.put("ignored", true);
                claim.put("reason", "该字段不能替代 reviewed server-owned code release switch runtime binding。");
                ignored.add(claim);
            }
        }
    }

    private static void putSourceDigests(Map<String, Object> target, Map<String, Object> source) {
        for (String key : sourceDigestKeys()) {
            target.put(key, text(source.get(key)));
        }
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

    private static boolean sourceDigestFieldsMatch(Map<String, Object> left, Map<String, Object> right) {
        for (String key : sourceDigestKeys()) {
            if (!text(left.get(key)).equals(text(right.get(key)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOnlyExpectedSwitchHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT_IMPLEMENTATION_HOLD".equals(
                text(blockers.get(0).get("code")));
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        return NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            map,
            NimForbiddenSecretMaterialDetector.strictRecursivePolicy()
        );
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

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (Object item : list) {
            items.add(text(item));
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
        return value.matches("[0-9]{1,18}");
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record CodeReleaseSwitchRuntimeBindingInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditCodeReleaseSwitchContractReport,
        Map<String, Object> stateMachineReleaseEvidence,
        Map<String, Object> durableExecutorReleaseEvidence
    ) {
        CodeReleaseSwitchRuntimeBindingInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null
                ? Map.of()
                : objectMap(trustedPrincipalSnapshot);
            durableAuditCodeReleaseSwitchContractReport = durableAuditCodeReleaseSwitchContractReport == null
                ? Map.of()
                : objectMap(durableAuditCodeReleaseSwitchContractReport);
            stateMachineReleaseEvidence = stateMachineReleaseEvidence == null
                ? Map.of()
                : objectMap(stateMachineReleaseEvidence);
            durableExecutorReleaseEvidence = durableExecutorReleaseEvidence == null
                ? Map.of()
                : objectMap(durableExecutorReleaseEvidence);
        }

        static CodeReleaseSwitchRuntimeBindingInput empty() {
            return new CodeReleaseSwitchRuntimeBindingInput(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
            );
        }
    }
}
