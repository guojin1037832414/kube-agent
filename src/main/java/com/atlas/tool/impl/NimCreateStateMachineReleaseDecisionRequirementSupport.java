package com.atlas.tool.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * NIM 状态机 release decision gate report 必需项契约。
 *
 * <p>本类只把未来 {@link NimCreateStateMachineSupport} 接入 release decision gate report 前必须满足的证据形状
 * 固化成可测试合同；它不修改状态机真实放行逻辑，不创建真实 release decision，不访问 kube-manager，也不执行任何存储 I/O。</p>
 */
final class NimCreateStateMachineReleaseDecisionRequirementSupport {

    static final String REQUIREMENT_NAME = "NIM_CREATE_STATE_MACHINE_RELEASE_DECISION_REPORT_REQUIREMENT";
    static final String EXECUTION_MODE = "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String TARGET_STATE_MACHINE = "NimCreateStateMachineSupport";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";
    private static final String RELEASE_DENIED = "DENY_UNTIL_SERVER_VALIDATION_RESULT";
    private static final Set<String> FORBIDDEN_SECRET_KEYS = Set.of(
        "apikey",
        "ngcapikey",
        "nvaieapikey",
        "token",
        "secret",
        "password",
        "authorization",
        "authheader",
        "bearertoken"
    );

    private NimCreateStateMachineReleaseDecisionRequirementSupport() {
    }

    static Map<String, Object> plan(StateMachineReleaseDecisionRequirementInput input) {
        StateMachineReleaseDecisionRequirementInput safeInput = input == null
            ? StateMachineReleaseDecisionRequirementInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> gateReport = safeInput.durableAuditReleaseDecisionGateReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateReleaseDecisionGateReport(auditContext, principal, gateReport, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditReleaseDecisionGateReport", gateReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> requirementPlan = inputAccepted
            ? stateMachineRequirementPlan(auditContext, principal, gateReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_IMPLEMENTATION_HOLD",
                "状态机 release decision gate report 必需项已定义，但真实状态机回接、server-issued release decision 和代码级 release switch 尚未实现；当前不能放行写执行。",
                "state-machine-release-decision"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stateMachineReleaseDecisionReportRequirement", REQUIREMENT_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("requirementState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("targetStateMachine", TARGET_STATE_MACHINE);
        result.put("requiredFutureStateMachineInput", "durableAuditReleaseDecisionGateReport");
        result.put("futureReadinessRequestField", "releaseDecisionGateReport");
        result.put("releaseDecisionGateReportRequired", true);
        result.put("futureStateMachineGate",
            NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_STATE_MACHINE_GATE);
        result.put("sourceReleaseDecisionGate",
            NimCreateDurableAuditReleaseDecisionGateSupport.GATE_NAME);
        result.put("futureValidationResult",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        result.put("futureReleaseDecision",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("stateMachineRequirementPlanPrepared", inputAccepted);
        result.put("releaseDecisionGateReportAccepted", inputAccepted);
        result.put("realStateMachineReleaseDecisionGateReportAccepted", false);
        result.put("releaseDecisionGateDigestVerified", false);
        result.put("validationResultDigestVerified", false);
        result.put("releaseDecisionDigestVerified", false);
        result.put("trustedPrincipalValidated", false);
        result.put("codeReleaseSwitchVerified", false);
        result.put("realReleaseDecisionLoaded", false);
        result.put("realReleaseDecisionAccepted", false);
        result.put("stateMachineReleaseGateImplemented", false);
        result.put("stateMachineReleaseBound", false);
        result.put("stateMachineReleaseDecisionRequirementBound", false);
        result.put("stateMachineCanSetWritePermittedNow", false);
        result.put("legacyAuditReceiptReleaseFlagTrusted", false);
        result.put("legacyAuditReceiptReleaseEligibleTrusted", false);
        result.put("legacyAuditReceiptReleaseFallbackAllowed", false);
        result.put("fallbackToAuditReceiptReleaseEligibleAllowed", false);
        result.put("fallbackToCallerReleaseDecisionAllowed", false);
        result.put("fallbackToMigrationPlanAllowed", false);
        result.put("validationStatus", VALIDATION_NOT_RUN);
        result.put("releaseDecision", RELEASE_DENIED);
        result.put("releaseEligible", false);
        result.put("releaseDecisionAccepted", false);
        result.put("releaseCredentialIssued", false);
        result.put("writePermitted", false);
        result.put("writeExecutionAllowed", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceReleaseDecisionGatePlanDigest", text(gateReport.get("releaseDecisionGatePlanDigest")));
        result.put("sourceMigrationPlanDigest", text(gateReport.get("sourceMigrationPlanDigest")));
        result.put("sourceValidationPlanDigest", text(gateReport.get("sourceValidationPlanDigest")));
        result.put("sourceReceiptSchemaDigest", text(gateReport.get("sourceReceiptSchemaDigest")));
        result.put("stateMachineRequirementPlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("stateMachineRequirementPlanDigest", inputAccepted ? digestFor(requirementPlan) : "");
        result.put("stateMachineRequirementPlan", requirementPlan);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(auditContext, principal, gateReport));
        result.put("nextImplementationRequirements", List.of(
            "add a reviewed state-machine input field for the server-issued release decision gate report",
            "verify release decision gate plan digest inside NimCreateStateMachineSupport before writePermitted can be true",
            "replace legacy auditReceipt.releaseEligible trust with release decision digest and code release switch checks",
            "keep durable executor re-check as a second independent guard before any real POST"
        ));
        return result;
    }

    private static void validateAuditContext(Map<String, Object> auditContext,
                                             List<Map<String, Object>> blockers) {
        if (auditContext.isEmpty()
            || !Boolean.TRUE.equals(auditContext.get("auditPrepared"))
            || !NimCreateAuditReadinessSupport.AUDIT_EVENT_TYPE.equals(text(auditContext.get("auditEventType")))
            || !NimCreateStateMachineSupport.TARGET_TOOL.equals(text(auditContext.get("targetTool")))
            || !NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE.equals(text(auditContext.get("writeBodyProvenance")))
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_STATE_MACHINE_RELEASE_DECISION_REQUIREMENT",
                "状态机 release decision 必需项只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedReleaseOrWriteClaim(auditContext)) {
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
                "TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY_FOR_STATE_MACHINE_RELEASE_DECISION",
                "状态机 release decision 必需项必须绑定服务端可信 session principal，不能相信 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedReleaseOrWriteClaim(principal)) {
            blockers.add(forgedClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateReleaseDecisionGateReport(Map<String, Object> auditContext,
                                                          Map<String, Object> principal,
                                                          Map<String, Object> gateReport,
                                                          List<Map<String, Object>> blockers) {
        if (gateReport.isEmpty()) {
            blockers.add(blocker(
                "RELEASE_DECISION_GATE_REPORT_NOT_READY_FOR_STATE_MACHINE",
                "缺少 M5.21-59 release decision gate report；状态机不能生成未来放行必需项。",
                "release-decision-gate"
            ));
            return;
        }

        Map<String, Object> gatePlan = objectMap(gateReport.get("releaseDecisionGatePlan"));
        boolean valid = NimCreateDurableAuditReleaseDecisionGateSupport.GATE_NAME.equals(
                text(gateReport.get("durableAuditReleaseDecisionGate")))
            && NimCreateDurableAuditReleaseDecisionGateSupport.EXECUTION_MODE.equals(
                text(gateReport.get("executionMode")))
            && NimCreateDurableAuditReleaseDecisionGateSupport.HOLD_STATE.equals(
                text(gateReport.get("gateState")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(gateReport.get("futureValidationResult")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(gateReport.get("futureReleaseDecision")))
            && NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_STATE_MACHINE_GATE.equals(
                text(gateReport.get("futureStateMachineGate")))
            && NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_DURABLE_EXECUTOR_GATE.equals(
                text(gateReport.get("futureDurableExecutorGate")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(gateReport.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(gateReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(gateReport.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(gateReport.get("networkAccess")))
            && "NONE".equals(text(gateReport.get("sideEffect")))
            && Boolean.TRUE.equals(gateReport.get("inputAccepted"))
            && Boolean.TRUE.equals(gateReport.get("releaseDecisionGatePlanPrepared"))
            && Boolean.TRUE.equals(gateReport.get("stateMachineBindingPlanPrepared"))
            && Boolean.TRUE.equals(gateReport.get("durableExecutorBindingPlanPrepared"))
            && Boolean.FALSE.equals(gateReport.get("realReleaseDecisionLoaded"))
            && Boolean.FALSE.equals(gateReport.get("realReleaseDecisionAccepted"))
            && Boolean.FALSE.equals(gateReport.get("validationResultDigestVerified"))
            && Boolean.FALSE.equals(gateReport.get("releaseDecisionDigestVerified"))
            && Boolean.FALSE.equals(gateReport.get("trustedPrincipalValidated"))
            && Boolean.FALSE.equals(gateReport.get("codeReleaseSwitchVerified"))
            && Boolean.FALSE.equals(gateReport.get("stateMachineReleaseBound"))
            && Boolean.FALSE.equals(gateReport.get("durableExecutorReleaseBound"))
            && Boolean.FALSE.equals(gateReport.get("legacyAuditReceiptReleaseFlagTrusted"))
            && Boolean.FALSE.equals(gateReport.get("legacyAuditReceiptReleaseFallbackAllowed"))
            && VALIDATION_NOT_RUN.equals(text(gateReport.get("validationStatus")))
            && RELEASE_DENIED.equals(text(gateReport.get("releaseDecision")))
            && Boolean.FALSE.equals(gateReport.get("releaseEligible"))
            && Boolean.FALSE.equals(gateReport.get("releaseDecisionAccepted"))
            && Boolean.FALSE.equals(gateReport.get("releaseCredentialIssued"))
            && Boolean.FALSE.equals(gateReport.get("writePermitted"))
            && Boolean.FALSE.equals(gateReport.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(gateReport.get("realHttpExecutionAllowed"))
            && text(gateReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(gateReport.get("sourceMigrationPlanDigest")).matches("[a-f0-9]{64}")
            && text(gateReport.get("sourceValidationPlanDigest")).matches("[a-f0-9]{64}")
            && text(gateReport.get("sourceReceiptSchemaDigest")).matches("[a-f0-9]{64}")
            && text(gateReport.get("sourceInterfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(gateReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(gateReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(gateReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(
                text(gateReport.get("releaseDecisionGatePlanDigestAlgorithm")))
            && text(gateReport.get("releaseDecisionGatePlanDigest")).matches("[a-f0-9]{64}")
            && text(gateReport.get("releaseDecisionGatePlanDigest")).equals(digestFor(gatePlan))
            && hasOnlyExpectedReleaseGateHold(gateReport.get("blockedBy"))
            && gatePlanContractValid(auditContext, principal, gateReport, gatePlan);

        if (!valid) {
            blockers.add(blocker(
                "RELEASE_DECISION_GATE_REPORT_INVALID_FOR_STATE_MACHINE",
                "状态机只能消费 M5.21-59 生成的、仍处于 HOLD 且未声明真实 release decision 的 gate report。",
                "release-decision-gate"
            ));
        }
        if (hasForgedReleaseOrWriteClaimInGateReport(gateReport)) {
            blockers.add(forgedClaimBlocker("durableAuditReleaseDecisionGateReport"));
        }
    }

    private static boolean gatePlanContractValid(Map<String, Object> auditContext,
                                                 Map<String, Object> principal,
                                                 Map<String, Object> gateReport,
                                                 Map<String, Object> gatePlan) {
        Map<String, Object> identity = objectMap(gatePlan.get("trustedIdentityBinding"));
        Map<String, Object> stateMachineBinding = objectMap(gatePlan.get("stateMachineBindingPlan"));
        Map<String, Object> durableExecutorBinding = objectMap(gatePlan.get("durableExecutorBindingPlan"));
        Map<String, Object> currentDeny = objectMap(gatePlan.get("currentDenyTemplate"));
        Map<String, Object> failureContract = objectMap(gatePlan.get("failureContract"));
        return !gatePlan.isEmpty()
            && "SERVER_SIDE_RELEASE_DECISION_GATE_REQUIRED".equals(text(gatePlan.get("releaseGateBoundary")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(gatePlan.get("futureReleaseDecision")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(gatePlan.get("futureValidationResult")))
            && NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_STATE_MACHINE_GATE.equals(
                text(gatePlan.get("futureStateMachineGate")))
            && NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_DURABLE_EXECUTOR_GATE.equals(
                text(gatePlan.get("futureDurableExecutorGate")))
            && text(gateReport.get("sourceMigrationPlanDigest")).equals(
                text(gatePlan.get("sourceMigrationPlanDigest")))
            && text(gateReport.get("sourceValidationPlanDigest")).equals(
                text(gatePlan.get("sourceValidationPlanDigest")))
            && text(gateReport.get("sourceReceiptSchemaDigest")).equals(
                text(gatePlan.get("sourceReceiptSchemaDigest")))
            && text(gateReport.get("sourceInterfaceSpecDigest")).equals(
                text(gatePlan.get("sourceInterfaceSpecDigest")))
            && text(gateReport.get("sourceBoundaryPlanDigest")).equals(
                text(gatePlan.get("sourceBoundaryPlanDigest")))
            && text(gateReport.get("sourceWriterPlanDigest")).equals(
                text(gatePlan.get("sourceWriterPlanDigest")))
            && text(gateReport.get("sourceAvailabilityPlanDigest")).equals(
                text(gatePlan.get("sourceAvailabilityPlanDigest")))
            && digestFor(auditContext).equals(text(gatePlan.get("sourceAuditEventDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(gatePlan.get("digestAlgorithm")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && gateSequenceValid(gatePlan.get("gateSequence"))
            && requiredFutureEvidenceValid(gateReport, objectMap(gatePlan.get("requiredFutureEvidence")))
            && stateMachineBindingPlanValid(stateMachineBinding)
            && durableExecutorBindingPlanValid(durableExecutorBinding)
            && currentDenyTemplateValid(currentDeny)
            && releaseGateFailureContractValid(failureContract)
            && forbiddenShortcutsValid(gatePlan.get("forbiddenShortcuts"));
    }

    private static boolean gateSequenceValid(Object rawSequence) {
        List<Map<String, Object>> sequence = listOfMaps(rawSequence);
        List<String> expected = List.of(
            "validate-migration-plan-digest",
            "validate-server-issued-validation-result",
            "validate-server-issued-release-decision",
            "bind-state-machine-release-check",
            "bind-durable-executor-release-check",
            "require-code-release-switch"
        );
        if (sequence.size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < expected.size(); i++) {
            Map<String, Object> step = sequence.get(i);
            if (!expected.get(i).equals(text(step.get("id")))
                || !Boolean.TRUE.equals(step.get("futureOnly"))
                || !Boolean.FALSE.equals(step.get("sideEffectAllowedNow"))
                || !Boolean.TRUE.equals(step.get("failClosed"))) {
                return false;
            }
        }
        return true;
    }

    private static boolean requiredFutureEvidenceValid(Map<String, Object> gateReport,
                                                       Map<String, Object> evidence) {
        Map<String, Object> validationResult = objectMap(evidence.get("validationResult"));
        Map<String, Object> releaseDecision = objectMap(evidence.get("releaseDecision"));
        Map<String, Object> writeChain = objectMap(evidence.get("writeExecutionChain"));
        return text(gateReport.get("sourceMigrationPlanDigest")).equals(
                text(evidence.get("sourceMigrationPlanDigest")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(validationResult.get("requiredType")))
            && "PASS".equals(text(validationResult.get("requiredStatus")))
            && Boolean.TRUE.equals(validationResult.get("mustBindValidationPlanDigest"))
            && Boolean.TRUE.equals(validationResult.get("mustBindAllTypedEvidenceDigests"))
            && Boolean.TRUE.equals(validationResult.get("mustBindTrustedPrincipalDigest"))
            && Boolean.TRUE.equals(validationResult.get("mustBeServerIssued"))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(releaseDecision.get("requiredType")))
            && "ALLOW_WRITE_EXECUTION".equals(text(releaseDecision.get("requiredDecision")))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindValidationResultDigest"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindAuditEventDigest"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindTrustedPrincipalDigest"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindCodeReleaseSwitch"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBeServerIssued"))
            && Boolean.TRUE.equals(writeChain.get("mustBindBodyDigest"))
            && Boolean.TRUE.equals(writeChain.get("mustBindRequestSpecDigest"))
            && Boolean.TRUE.equals(writeChain.get("mustBindHandoffDigest"))
            && Boolean.TRUE.equals(writeChain.get("mustBindAuditReceiptId"))
            && Boolean.TRUE.equals(writeChain.get("mustBindAuditEventDigest"))
            && Boolean.TRUE.equals(writeChain.get("mustBindServerDerivedIdempotencyKey"))
            && Boolean.TRUE.equals(writeChain.get("mustBeRecheckedByStateMachine"))
            && Boolean.TRUE.equals(writeChain.get("mustBeRecheckedByDurableExecutor"));
    }

    private static boolean stateMachineBindingPlanValid(Map<String, Object> plan) {
        return TARGET_STATE_MACHINE.equals(text(plan.get("target")))
            && Boolean.FALSE.equals(plan.get("currentLegacyAuditReceiptReleaseFlagTrusted"))
            && Boolean.TRUE.equals(plan.get("futureReleaseDecisionDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureValidationResultDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureBodyDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureRequestSpecDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureHandoffDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureServerDerivedIdempotencyKeyRequired"))
            && Boolean.TRUE.equals(plan.get("futureCodeReleaseSwitchRequired"))
            && Boolean.FALSE.equals(plan.get("fallbackToAuditReceiptReleaseEligibleAllowed"))
            && Boolean.FALSE.equals(plan.get("fallbackToMigrationPlanAllowed"))
            && Boolean.FALSE.equals(plan.get("writePermittedCanBeTrueNow"));
    }

    private static boolean durableExecutorBindingPlanValid(Map<String, Object> plan) {
        return NimCreateDurableWriteExecutorSupport.EXECUTOR_NAME.equals(text(plan.get("target")))
            && Boolean.TRUE.equals(plan.get("futureReleaseDecisionDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureValidationResultDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureBodyDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureRequestSpecDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureHandoffDigestRequired"))
            && Boolean.TRUE.equals(plan.get("futureAuditReceiptIdRequired"))
            && Boolean.TRUE.equals(plan.get("futureServerDerivedIdempotencyKeyRequired"))
            && Boolean.TRUE.equals(plan.get("mustRecheckImmediatelyBeforePost"))
            && Boolean.FALSE.equals(plan.get("fallbackToHandoffOnlyAllowed"))
            && Boolean.FALSE.equals(plan.get("fallbackToRequestSpecOnlyAllowed"))
            && Boolean.FALSE.equals(plan.get("fallbackToMigrationPlanAllowed"))
            && Boolean.FALSE.equals(plan.get("realHttpExecutionAllowedNow"))
            && Boolean.FALSE.equals(plan.get("writeExecutionAllowedNow"));
    }

    private static boolean currentDenyTemplateValid(Map<String, Object> template) {
        return RELEASE_DENIED.equals(text(template.get("decision")))
            && VALIDATION_NOT_RUN.equals(text(template.get("validationStatus")))
            && Boolean.FALSE.equals(template.get("releaseDecisionAccepted"))
            && Boolean.FALSE.equals(template.get("releaseCredentialIssued"))
            && Boolean.FALSE.equals(template.get("releaseEligible"))
            && Boolean.FALSE.equals(template.get("writePermitted"))
            && Boolean.FALSE.equals(template.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(template.get("realHttpExecutionAllowed"));
    }

    private static boolean releaseGateFailureContractValid(Map<String, Object> contract) {
        List<String> statuses = stringList(contract.get("failureStatuses"));
        return Boolean.TRUE.equals(contract.get("failClosed"))
            && Boolean.FALSE.equals(contract.get("fallbackToLegacyAuditReceiptFlagAllowed"))
            && Boolean.FALSE.equals(contract.get("fallbackToMigrationPlanAllowed"))
            && Boolean.FALSE.equals(contract.get("fallbackToCallerReleaseDecisionAllowed"))
            && Boolean.FALSE.equals(contract.get("fallbackToDurableExecutorHandoffAllowed"))
            && statuses.contains("RELEASE_DECISION_GATE_NOT_IMPLEMENTED")
            && statuses.contains("VALIDATION_RESULT_NOT_IMPLEMENTED")
            && statuses.contains("RELEASE_DECISION_NOT_IMPLEMENTED")
            && statuses.contains("CODE_RELEASE_SWITCH_NOT_OPEN")
            && statuses.contains("LEGACY_AUDIT_RECEIPT_RELEASE_FLAG_NOT_TRUSTED");
    }

    private static boolean forbiddenShortcutsValid(Object rawShortcuts) {
        List<String> shortcuts = stringList(rawShortcuts);
        return shortcuts.contains("accepting migration plan as release decision gate pass")
            && shortcuts.contains("accepting caller-supplied releaseDecision or validationResult")
            && shortcuts.contains("accepting legacy auditReceipt.releaseEligible=true as write permission")
            && shortcuts.contains("allowing state machine writePermitted=true before release decision digest is verified")
            && shortcuts.contains("allowing durable executor writeExecuted=true before release decision digest is re-checked");
    }

    private static Map<String, Object> stateMachineRequirementPlan(Map<String, Object> auditContext,
                                                                   Map<String, Object> principal,
                                                                   Map<String, Object> gateReport) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("requirementBoundary", "STATE_MACHINE_RELEASE_DECISION_GATE_REPORT_REQUIRED");
        plan.put("targetStateMachine", TARGET_STATE_MACHINE);
        plan.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        plan.put("futureStateMachineGate",
            NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_STATE_MACHINE_GATE);
        plan.put("sourceReleaseDecisionGate",
            NimCreateDurableAuditReleaseDecisionGateSupport.GATE_NAME);
        plan.put("futureReleaseDecision",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        plan.put("futureValidationResult",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        plan.put("sourceReleaseDecisionGatePlanDigest", text(gateReport.get("releaseDecisionGatePlanDigest")));
        plan.put("sourceMigrationPlanDigest", text(gateReport.get("sourceMigrationPlanDigest")));
        plan.put("sourceValidationPlanDigest", text(gateReport.get("sourceValidationPlanDigest")));
        plan.put("sourceReceiptSchemaDigest", text(gateReport.get("sourceReceiptSchemaDigest")));
        plan.put("sourceAuditEventDigest", digestFor(auditContext));
        plan.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        plan.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        plan.put("stateMachineRequirementSequence", stateMachineRequirementSequence());
        plan.put("requiredFutureStateMachineEvidence", requiredFutureStateMachineEvidence(gateReport));
        plan.put("stateMachineFieldMigration", stateMachineFieldMigration());
        plan.put("currentDenyTemplate", currentDenyTemplate());
        plan.put("failureContract", stateMachineFailureContract());
        plan.put("forbiddenShortcuts", stateMachineForbiddenShortcuts());
        return plan;
    }

    private static List<Map<String, Object>> stateMachineRequirementSequence() {
        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(requirementStep(
            "require-release-decision-gate-report",
            "State machine input must include the reviewed release decision gate report"
        ));
        sequence.add(requirementStep(
            "recompute-release-decision-gate-plan-digest",
            "State machine must recompute the gate plan digest before trusting any release fields"
        ));
        sequence.add(requirementStep(
            "bind-server-issued-validation-result",
            "State machine must require validation result digest rather than caller supplied validationStatus"
        ));
        sequence.add(requirementStep(
            "bind-server-issued-release-decision",
            "State machine must require release decision digest before writePermitted can be true"
        ));
        sequence.add(requirementStep(
            "bind-write-chain-digests",
            "State machine must bind body, request spec, handoff, audit receipt and idempotency evidence"
        ));
        sequence.add(requirementStep(
            "require-code-release-switch",
            "Code release switch remains an independent server-side condition"
        ));
        sequence.add(requirementStep(
            "keep-current-state-machine-denied",
            "Current implementation remains fail-closed until the real state-machine gate is reviewed"
        ));
        return sequence;
    }

    private static Map<String, Object> requirementStep(String id, String requirement) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("requirement", requirement);
        step.put("futureOnly", true);
        step.put("sideEffectAllowedNow", false);
        step.put("failClosed", true);
        return step;
    }

    private static Map<String, Object> requiredFutureStateMachineEvidence(Map<String, Object> gateReport) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("releaseDecisionGateReport", Map.of(
            "requiredName", NimCreateDurableAuditReleaseDecisionGateSupport.GATE_NAME,
            "requiredState", NimCreateDurableAuditReleaseDecisionGateSupport.HOLD_STATE,
            "sourceReleaseDecisionGatePlanDigest", text(gateReport.get("releaseDecisionGatePlanDigest")),
            "mustBeServerGenerated", true,
            "mustBeRecomputedByStateMachine", true
        ));
        evidence.put("validationResultDigest", Map.of(
            "futureType", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            "requiredBeforeReleaseDecision", true,
            "callerValidationStatusAllowed", false
        ));
        evidence.put("releaseDecisionDigest", Map.of(
            "futureType", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            "requiredDecision", "ALLOW_WRITE_EXECUTION",
            "requiredBeforeWritePermitted", true,
            "callerReleaseDecisionAllowed", false
        ));
        evidence.put("writeChainDigests", Map.of(
            "bodyDigestRequired", true,
            "requestSpecDigestRequired", true,
            "handoffDigestRequired", true,
            "auditReceiptIdRequired", true,
            "serverDerivedIdempotencyKeyRequired", true
        ));
        evidence.put("codeReleaseSwitch", Map.of(
            "required", true,
            "separateFromValidationResult", true,
            "separateFromLegacyAuditReceiptFlag", true
        ));
        return evidence;
    }

    private static Map<String, Object> stateMachineFieldMigration() {
        Map<String, Object> migration = new LinkedHashMap<>();
        migration.put("currentLegacyAuditReceiptReleaseEligibleTrusted", false);
        migration.put("futureReleaseDecisionGateReportRequired", true);
        migration.put("futureValidationResultDigestRequired", true);
        migration.put("futureReleaseDecisionDigestRequired", true);
        migration.put("futureCodeReleaseSwitchRequired", true);
        migration.put("fallbackToAuditReceiptReleaseEligibleAllowed", false);
        migration.put("fallbackToCallerReleaseDecisionAllowed", false);
        migration.put("writePermittedCanBeTrueNow", false);
        migration.put("readinessRequestSchemaChangeRequiredInFuture", true);
        return migration;
    }

    private static Map<String, Object> currentDenyTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("decision", RELEASE_DENIED);
        template.put("validationStatus", VALIDATION_NOT_RUN);
        template.put("releaseDecisionAccepted", false);
        template.put("releaseCredentialIssued", false);
        template.put("releaseEligible", false);
        template.put("writePermitted", false);
        template.put("writeExecutionAllowed", false);
        template.put("realHttpExecutionAllowed", false);
        return template;
    }

    private static Map<String, Object> stateMachineFailureContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("failClosed", true);
        contract.put("fallbackToLegacyAuditReceiptFlagAllowed", false);
        contract.put("fallbackToMigrationPlanAllowed", false);
        contract.put("fallbackToReleaseDecisionGatePlanAllowed", false);
        contract.put("fallbackToCallerReleaseDecisionAllowed", false);
        contract.put("fallbackToDurableExecutorHandoffAllowed", false);
        contract.put("failureStatuses", List.of(
            "STATE_MACHINE_RELEASE_DECISION_GATE_NOT_IMPLEMENTED",
            "RELEASE_DECISION_GATE_REPORT_MISSING",
            "RELEASE_DECISION_GATE_REPORT_DIGEST_MISMATCH",
            "VALIDATION_RESULT_DIGEST_MISSING",
            "RELEASE_DECISION_DIGEST_MISSING",
            "CODE_RELEASE_SWITCH_NOT_OPEN",
            "LEGACY_AUDIT_RECEIPT_RELEASE_FLAG_NOT_TRUSTED",
            "FORGED_RELEASE_DECISION_CLAIM",
            "SECRET_MATERIAL_REJECTED"
        ));
        return contract;
    }

    private static List<String> stateMachineForbiddenShortcuts() {
        return List.of(
            "adding releaseDecision to ReadinessRequest without a server-issued digest contract",
            "accepting release decision gate plan as a release credential",
            "accepting caller-supplied validationResult or releaseDecision",
            "trusting legacy auditReceipt.releaseEligible=true as writePermitted",
            "setting writePermitted=true before release decision digest and code release switch are verified",
            "letting durable executor success claims backfill state-machine release evidence"
        );
    }

    private static boolean hasOnlyExpectedReleaseGateHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DURABLE_AUDIT_RELEASE_DECISION_GATE_IMPLEMENTATION_HOLD".equals(
                text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForbiddenSecretKey(entry.getKey()) && secretBearingValue(value)) {
                return true;
            }
            if (value instanceof String textValue
                && looksLikeSecretValue(textValue)
                && !isDocumentedForbiddenFieldName(textValue)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && containsForbiddenSecretMaterial(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && containsForbiddenSecretMaterial(objectMap(nestedItem))) {
                        return true;
                    }
                    if (item instanceof String textItem
                        && looksLikeSecretValue(textItem)
                        && !isDocumentedForbiddenFieldName(textItem)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean secretBearingValue(Object value) {
        if (value instanceof Boolean || value instanceof Number) {
            return false;
        }
        return hasText(value);
    }

    private static boolean hasForgedReleaseOrWriteClaim(Map<String, Object> map) {
        return containsForgedReleaseOrWriteClaim(map);
    }

    private static boolean containsForgedReleaseOrWriteClaim(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isCallerEvidenceOrDecisionKey(key) && value != null) {
                return true;
            }
            if (isSuccessBooleanKey(key) && Boolean.TRUE.equals(value)) {
                return true;
            }
            if ("validationStatus".equals(key)
                && ("PASS".equals(text(value)) || "VALIDATED".equals(text(value)) || "APPROVED".equals(text(value)))) {
                return true;
            }
            if ("decision".equals(key) && "ALLOW_WRITE_EXECUTION".equals(text(value))) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && containsForgedReleaseOrWriteClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem
                        && containsForgedReleaseOrWriteClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasForgedReleaseOrWriteClaimInGateReport(Map<String, Object> gateReport) {
        if (isForbiddenGateReleaseDecisionValue(gateReport.get("releaseDecision"))) {
            return true;
        }
        if (hasTopLevelGateSuccessClaim(gateReport)) {
            return true;
        }
        for (String key : List.of(
            "auditReceipt",
            "legacyAuditReceipt",
            "validationResult",
            "releaseCredential",
            "releaseDecisionGatePass",
            "callerReleaseDecision",
            "callerValidationResult"
        )) {
            if (gateReport.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isForbiddenGateReleaseDecisionValue(Object value) {
        if (value instanceof Map<?, ?> || value instanceof List<?>) {
            return true;
        }
        String decision = text(value);
        return hasText(value) && !RELEASE_DENIED.equals(decision);
    }

    private static boolean hasTopLevelGateSuccessClaim(Map<String, Object> gateReport) {
        for (String key : List.of(
            "validationPassed",
            "validationResultAccepted",
            "releaseDecisionAccepted",
            "releaseDecisionVerified",
            "releaseCredentialIssued",
            "releaseEligible",
            "writePermitted",
            "writeExecutionAllowed",
            "realHttpExecutionAllowed",
            "realReleaseDecisionLoaded",
            "realReleaseDecisionAccepted",
            "stateMachineReleaseBound",
            "durableExecutorReleaseBound",
            "executorImplementationAvailable",
            "writeAttempted",
            "writeExecuted",
            "postWriteReadinessTriggered",
            "durableReceiptValidationPassed",
            "durableReceiptAccepted",
            "realStorageTouched",
            "durable"
        )) {
            if (Boolean.TRUE.equals(gateReport.get(key))) {
                return true;
            }
        }
        return hasText(gateReport.get("deploymentId"))
            || hasText(gateReport.get("deploymentUid"))
            || !objectMap(gateReport.get("writeResult")).isEmpty()
            || "PASS".equals(text(gateReport.get("validationStatus")))
            || "ALLOW_WRITE_EXECUTION".equals(text(gateReport.get("decision")));
    }

    private static boolean isCallerEvidenceOrDecisionKey(String key) {
        return "auditReceipt".equals(key)
            || "legacyAuditReceipt".equals(key)
            || "validationResult".equals(key)
            || "releaseDecision".equals(key)
            || "releaseDecisionGatePass".equals(key)
            || "releaseCredential".equals(key);
    }

    private static boolean isSuccessBooleanKey(String key) {
        return "validationPassed".equals(key)
            || "validationResultAccepted".equals(key)
            || "releaseDecisionAccepted".equals(key)
            || "releaseDecisionVerified".equals(key)
            || "releaseCredentialIssued".equals(key)
            || "releaseEligible".equals(key)
            || "writePermitted".equals(key)
            || "writeExecutionAllowed".equals(key)
            || "realHttpExecutionAllowed".equals(key)
            || "realReleaseDecisionLoaded".equals(key)
            || "realReleaseDecisionAccepted".equals(key)
            || "stateMachineReleaseBound".equals(key)
            || "durableExecutorReleaseBound".equals(key)
            || "executorImplementationAvailable".equals(key)
            || "writeAttempted".equals(key)
            || "writeExecuted".equals(key)
            || "postWriteReadinessTriggered".equals(key)
            || "durableReceiptValidationPassed".equals(key)
            || "durableReceiptAccepted".equals(key)
            || "realStorageTouched".equals(key)
            || "durable".equals(key);
    }

    private static Map<String, Object> forgedClaimBlocker(String source) {
        return blocker(
            "STATE_MACHINE_RELEASE_DECISION_REQUIREMENT_FORGED_RELEASE_CLAIM",
            source + " 不得自称 releaseDecision、validationResult、releaseEligible、writePermitted、writeExecutionAllowed 或真实执行成功。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> gateReport) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "auditReceipt",
            "legacyAuditReceipt",
            "validationResult",
            "validationPassed",
            "releaseDecision",
            "releaseDecisionAccepted",
            "releaseDecisionVerified",
            "releaseDecisionGatePass",
            "releaseCredential",
            "releaseCredentialIssued",
            "releaseEligible",
            "writePermitted",
            "writeExecutionAllowed",
            "realHttpExecutionAllowed",
            "realReleaseDecisionLoaded",
            "realReleaseDecisionAccepted",
            "stateMachineReleaseBound",
            "durableExecutorReleaseBound",
            "executorImplementationAvailable",
            "writeAttempted",
            "writeExecuted",
            "postWriteReadinessTriggered",
            "deploymentId",
            "deploymentUid",
            "writeResult"
        )) {
            if (auditContext.containsKey(key) || principal.containsKey(key) || gateReport.containsKey(key)) {
                ignored.add(key);
            }
        }
        return ignored;
    }

    private static boolean isForbiddenSecretKey(String key) {
        String normalized = normalizeKey(key);
        return FORBIDDEN_SECRET_KEYS.contains(normalized)
            || normalized.endsWith("apikey")
            || normalized.endsWith("token")
            || normalized.endsWith("secret")
            || normalized.endsWith("password")
            || normalized.endsWith("authorization");
    }

    private static boolean looksLikeSecretValue(String value) {
        String trimmed = value.trim();
        String normalized = normalizeKey(trimmed);
        if (trimmed.startsWith("Bearer ") && trimmed.length() > "Bearer ".length()) {
            return true;
        }
        return normalized.contains("ngcapikey")
            || normalized.contains("nvaieapikey")
            || normalized.contains("authorizationbearer")
            || normalized.contains("apikey=")
            || normalized.contains("token=")
            || normalized.contains("secret=")
            || normalized.contains("password=")
            || normalized.contains("authorization=")
            || trimmed.matches("sk-[A-Za-z0-9]{20,}")
            || trimmed.matches("AKIA[0-9A-Z]{16}")
            || trimmed.matches("AIza[0-9A-Za-z_-]{35}")
            || trimmed.matches("ghp_[A-Za-z0-9]{36}")
            || trimmed.matches("xox[baprs]-[A-Za-z0-9-]{10,}");
    }

    private static boolean isDocumentedForbiddenFieldName(String value) {
        return Set.of(
            "authorization",
            "token",
            "apikey",
            "ngcapikey",
            "nvaieapikey",
            "password",
            "secret",
            "callerprovidedusername",
            "callerprovidedorganizationid"
        ).contains(normalizeKey(value));
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
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
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

    record StateMachineReleaseDecisionRequirementInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditReleaseDecisionGateReport
    ) {
        StateMachineReleaseDecisionRequirementInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditReleaseDecisionGateReport = durableAuditReleaseDecisionGateReport == null
                ? Map.of()
                : objectMap(durableAuditReleaseDecisionGateReport);
        }

        static StateMachineReleaseDecisionRequirementInput empty() {
            return new StateMachineReleaseDecisionRequirementInput(Map.of(), Map.of(), Map.of());
        }
    }
}
