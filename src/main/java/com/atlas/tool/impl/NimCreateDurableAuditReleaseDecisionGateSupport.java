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
import java.util.TreeMap;

/**
 * NIM durable audit release decision 回接 state machine / durable executor 的门禁计划契约。
 *
 * <p>本类只描述未来 {@code NimDurableAuditReleaseDecision} 出现后，状态机和 durable write executor
 * 必须如何重新校验 release decision；它不创建真实 release decision，不修改真实状态机放行条件，
 * 不访问 kube-manager，也不执行任何 HTTP 或存储 I/O。</p>
 */
final class NimCreateDurableAuditReleaseDecisionGateSupport {

    static final String GATE_NAME = "NIM_CREATE_DURABLE_AUDIT_RELEASE_DECISION_GATE";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_RELEASE_DECISION_GATE_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String FUTURE_STATE_MACHINE_GATE = "NimCreateStateMachineReleaseDecisionGate";
    static final String FUTURE_DURABLE_EXECUTOR_GATE = "NimDurableWriteExecutorReleaseDecisionGate";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String RELEASE_DENIED = "DENY_UNTIL_SERVER_VALIDATION_RESULT";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";

    private NimCreateDurableAuditReleaseDecisionGateSupport() {
    }

    static Map<String, Object> plan(DurableAuditReleaseDecisionGateInput input) {
        DurableAuditReleaseDecisionGateInput safeInput = input == null
            ? DurableAuditReleaseDecisionGateInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> migrationReport = safeInput.durableAuditValidationResultMigrationReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateMigrationReport(auditContext, principal, migrationReport, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditValidationResultMigrationReport", migrationReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> releaseGatePlan = inputAccepted
            ? releaseGatePlan(auditContext, principal, migrationReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DURABLE_AUDIT_RELEASE_DECISION_GATE_IMPLEMENTATION_HOLD",
                "release decision 回接门禁已定义，但真实 server-issued release decision、状态机回接和 durable executor 回接尚未实现；当前不能放行写执行。",
                "durable-audit-release-decision-gate"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditReleaseDecisionGate", GATE_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("gateState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureValidationResult", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        result.put("futureReleaseDecision", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        result.put("futureStateMachineGate", FUTURE_STATE_MACHINE_GATE);
        result.put("futureDurableExecutorGate", FUTURE_DURABLE_EXECUTOR_GATE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("releaseDecisionGatePlanPrepared", inputAccepted);
        result.put("stateMachineBindingPlanPrepared", inputAccepted);
        result.put("durableExecutorBindingPlanPrepared", inputAccepted);
        result.put("realReleaseDecisionLoaded", false);
        result.put("realReleaseDecisionAccepted", false);
        result.put("validationResultDigestVerified", false);
        result.put("releaseDecisionDigestVerified", false);
        result.put("trustedPrincipalValidated", false);
        result.put("codeReleaseSwitchVerified", false);
        result.put("stateMachineReleaseBound", false);
        result.put("durableExecutorReleaseBound", false);
        result.put("legacyAuditReceiptReleaseFlagTrusted", false);
        result.put("legacyAuditReceiptReleaseFallbackAllowed", false);
        result.put("validationStatus", VALIDATION_NOT_RUN);
        result.put("releaseDecision", RELEASE_DENIED);
        result.put("releaseEligible", false);
        result.put("releaseDecisionAccepted", false);
        result.put("releaseCredentialIssued", false);
        result.put("writePermitted", false);
        result.put("writeExecutionAllowed", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceMigrationPlanDigest", text(migrationReport.get("migrationPlanDigest")));
        result.put("sourceValidationPlanDigest", text(migrationReport.get("sourceValidationPlanDigest")));
        result.put("sourceReceiptSchemaDigest", text(migrationReport.get("sourceReceiptSchemaDigest")));
        result.put("sourceInterfaceSpecDigest", text(migrationReport.get("sourceInterfaceSpecDigest")));
        result.put("sourceBoundaryPlanDigest", text(migrationReport.get("sourceBoundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(migrationReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(migrationReport.get("sourceAvailabilityPlanDigest")));
        result.put("releaseDecisionGatePlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("releaseDecisionGatePlanDigest", inputAccepted ? digestFor(releaseGatePlan) : "");
        result.put("releaseDecisionGatePlan", releaseGatePlan);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(auditContext, principal, migrationReport));
        result.put("nextImplementationRequirements", List.of(
            "implement server-side NimDurableAuditReleaseDecision only after real validation result exists",
            "bind NimCreateStateMachineSupport to release decision digest instead of legacy auditReceipt.releaseEligible",
            "bind NimCreateDurableWriteExecutorSupport to release decision digest before any real POST attempt",
            "require code release switch plus server-issued release decision before writePermitted can ever become true",
            "keep nim_create held until release decision gate, durable audit writer, durable executor and readiness aftercare all pass review"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_RELEASE_DECISION_GATE",
                "release decision 回接门禁只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
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
                "TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY",
                "release decision 回接门禁必须绑定服务端可信 session principal，不能相信 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedReleaseOrWriteClaim(principal)) {
            blockers.add(forgedClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateMigrationReport(Map<String, Object> auditContext,
                                                Map<String, Object> principal,
                                                Map<String, Object> migrationReport,
                                                List<Map<String, Object>> blockers) {
        if (migrationReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_REPORT_NOT_READY",
                "缺少 M5.21-58 validation result / release decision migration report；不能生成 release decision 回接门禁。",
                "durable-audit-validation-result-migration"
            ));
            return;
        }

        Map<String, Object> migrationPlan = objectMap(migrationReport.get("migrationPlan"));
        boolean valid = NimCreateDurableAuditValidationResultMigrationSupport.PLAN_NAME.equals(
                text(migrationReport.get("durableAuditValidationResultMigrationPlan")))
            && NimCreateDurableAuditValidationResultMigrationSupport.EXECUTION_MODE.equals(
                text(migrationReport.get("executionMode")))
            && NimCreateDurableAuditValidationResultMigrationSupport.HOLD_STATE.equals(
                text(migrationReport.get("migrationPlanState")))
            && NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR.equals(
                text(migrationReport.get("futureValidator")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(migrationReport.get("futureValidationResult")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(migrationReport.get("futureReleaseDecision")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(migrationReport.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(migrationReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(migrationReport.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(migrationReport.get("networkAccess")))
            && "NONE".equals(text(migrationReport.get("sideEffect")))
            && Boolean.TRUE.equals(migrationReport.get("inputAccepted"))
            && Boolean.TRUE.equals(migrationReport.get("migrationPlanPrepared"))
            && Boolean.TRUE.equals(migrationReport.get("validationResultContractPrepared"))
            && Boolean.TRUE.equals(migrationReport.get("releaseDecisionContractPrepared"))
            && Boolean.FALSE.equals(migrationReport.get("realValidatorCreated"))
            && Boolean.FALSE.equals(migrationReport.get("realValidationResultCreated"))
            && Boolean.FALSE.equals(migrationReport.get("realReleaseDecisionCreated"))
            && Boolean.FALSE.equals(migrationReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(migrationReport.get("storageProbeReceiptValidated"))
            && Boolean.FALSE.equals(migrationReport.get("preWriteDurableAckValidated"))
            && Boolean.FALSE.equals(migrationReport.get("postWriteDurableAckValidated"))
            && Boolean.FALSE.equals(migrationReport.get("digestChainValidated"))
            && Boolean.FALSE.equals(migrationReport.get("trustedPrincipalValidated"))
            && Boolean.FALSE.equals(migrationReport.get("durableReceiptValidated"))
            && Boolean.FALSE.equals(migrationReport.get("durableReceiptValidationPassed"))
            && Boolean.FALSE.equals(migrationReport.get("durableReceiptAccepted"))
            && VALIDATION_NOT_RUN.equals(text(migrationReport.get("validationStatus")))
            && Boolean.FALSE.equals(migrationReport.get("durable"))
            && Boolean.FALSE.equals(migrationReport.get("releaseEligible"))
            && Boolean.FALSE.equals(migrationReport.get("releaseDecisionAccepted"))
            && Boolean.FALSE.equals(migrationReport.get("releaseCredentialIssued"))
            && Boolean.FALSE.equals(migrationReport.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(migrationReport.get("legacyAuditReceiptReleaseFlagTrusted"))
            && text(migrationReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(migrationReport.get("sourceReceiptSchemaDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceValidationPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceInterfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(
                text(migrationReport.get("migrationPlanDigestAlgorithm")))
            && text(migrationReport.get("migrationPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("migrationPlanDigest")).equals(digestFor(migrationPlan))
            && hasOnlyExpectedMigrationHold(migrationReport.get("blockedBy"))
            && migrationPlanContractValid(auditContext, principal, migrationReport, migrationPlan);

        if (!valid) {
            blockers.add(blocker(
                "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_REPORT_INVALID_FOR_RELEASE_GATE",
                "release decision 回接门禁只能消费 M5.21-58 生成的、仍处于 HOLD 且未声明真实 release decision 的 migration report。",
                "durable-audit-validation-result-migration"
            ));
        }
        if (hasForgedReleaseOrWriteClaim(migrationReport)) {
            blockers.add(forgedClaimBlocker("durableAuditValidationResultMigrationReport"));
        }
    }

    private static boolean migrationPlanContractValid(Map<String, Object> auditContext,
                                                      Map<String, Object> principal,
                                                      Map<String, Object> migrationReport,
                                                      Map<String, Object> migrationPlan) {
        Map<String, Object> identity = objectMap(migrationPlan.get("trustedIdentityBinding"));
        return !migrationPlan.isEmpty()
            && "SERVER_SIDE_VALIDATION_RESULT_AND_RELEASE_DECISION_REQUIRED".equals(
                text(migrationPlan.get("migrationBoundary")))
            && NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR.equals(
                text(migrationPlan.get("futureValidator")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(migrationPlan.get("futureValidationResult")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(migrationPlan.get("futureReleaseDecision")))
            && text(migrationReport.get("sourceReceiptSchemaDigest")).equals(
                text(migrationPlan.get("sourceReceiptSchemaDigest")))
            && text(migrationReport.get("sourceValidationPlanDigest")).equals(
                text(migrationPlan.get("sourceValidationPlanDigest")))
            && text(migrationReport.get("sourceInterfaceSpecDigest")).equals(
                text(migrationPlan.get("sourceInterfaceSpecDigest")))
            && text(migrationReport.get("sourceBoundaryPlanDigest")).equals(
                text(migrationPlan.get("sourceBoundaryPlanDigest")))
            && text(migrationReport.get("sourceWriterPlanDigest")).equals(
                text(migrationPlan.get("sourceWriterPlanDigest")))
            && text(migrationReport.get("sourceAvailabilityPlanDigest")).equals(
                text(migrationPlan.get("sourceAvailabilityPlanDigest")))
            && digestFor(auditContext).equals(text(migrationPlan.get("sourceAuditEventDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(migrationPlan.get("digestAlgorithm")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && migrationSequenceValid(migrationPlan.get("migrationSequence"))
            && validationResultContractValid(migrationReport, objectMap(migrationPlan.get("validationResultContract")))
            && releaseDecisionContractValid(migrationReport, objectMap(migrationPlan.get("releaseDecisionContract")))
            && legacyCompatibilityPolicyValid(objectMap(migrationPlan.get("legacyCompatibilityPolicy")))
            && releaseCredentialRulesValid(objectMap(migrationPlan.get("releaseCredentialRules")))
            && migrationFailureContractValid(objectMap(migrationPlan.get("failureContract")))
            && migrationForbiddenShortcutsValid(migrationPlan.get("forbiddenShortcuts"));
    }

    private static boolean migrationSequenceValid(Object rawSequence) {
        List<Map<String, Object>> sequence = listOfMaps(rawSequence);
        return sequence.size() == 5
            && "keep-validation-gate-contract-only".equals(text(sequence.get(0).get("id")))
            && "introduce-validation-result-value".equals(text(sequence.get(1).get("id")))
            && "introduce-release-decision-value".equals(text(sequence.get(2).get("id")))
            && "migrate-state-machine-release-check".equals(text(sequence.get(3).get("id")))
            && "bind-durable-executor-release-check".equals(text(sequence.get(4).get("id")))
            && sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly")))
            && sequence.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow")))
            && sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed")));
    }

    private static boolean validationResultContractValid(Map<String, Object> migrationReport,
                                                         Map<String, Object> contract) {
        Map<String, Object> template = objectMap(contract.get("currentTemplate"));
        return NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(contract.get("type")))
            && NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR.equals(
                text(contract.get("producedBy")))
            && Boolean.TRUE.equals(contract.get("futureOnly"))
            && Boolean.FALSE.equals(contract.get("instanceAllowedNow"))
            && Boolean.FALSE.equals(contract.get("sideEffectAllowedNow"))
            && VALIDATION_NOT_RUN.equals(text(contract.get("currentValidationStatus")))
            && "PASS".equals(text(contract.get("requiredPassStatus")))
            && text(migrationReport.get("sourceReceiptSchemaDigest")).equals(
                text(contract.get("sourceReceiptSchemaDigest")))
            && text(migrationReport.get("sourceValidationPlanDigest")).equals(
                text(contract.get("sourceValidationPlanDigest")))
            && Boolean.TRUE.equals(contract.get("mustBindAuditEventDigest"))
            && Boolean.TRUE.equals(contract.get("mustBindStorageProbeReceiptDigest"))
            && Boolean.TRUE.equals(contract.get("mustBindPreWriteDurableAckDigest"))
            && Boolean.TRUE.equals(contract.get("mustBindPostWriteDurableAckDigest"))
            && Boolean.TRUE.equals(contract.get("mustBindDurableReceiptDigest"))
            && Boolean.TRUE.equals(contract.get("mustBindTrustedPrincipalDigest"))
            && Boolean.TRUE.equals(contract.get("mustBeServerIssued"))
            && VALIDATION_NOT_RUN.equals(text(template.get("validationStatus")))
            && Boolean.FALSE.equals(template.get("validationPassed"))
            && Boolean.FALSE.equals(template.get("releaseEligible"))
            && Boolean.FALSE.equals(template.get("writeExecutionAllowed"));
    }

    private static boolean releaseDecisionContractValid(Map<String, Object> migrationReport,
                                                        Map<String, Object> contract) {
        Map<String, Object> template = objectMap(contract.get("currentTemplate"));
        return NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(contract.get("type")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(contract.get("dependsOn")))
            && Boolean.TRUE.equals(contract.get("futureOnly"))
            && Boolean.FALSE.equals(contract.get("instanceAllowedNow"))
            && Boolean.FALSE.equals(contract.get("sideEffectAllowedNow"))
            && RELEASE_DENIED.equals(text(contract.get("currentDecision")))
            && "ALLOW_WRITE_EXECUTION".equals(text(contract.get("requiredAllowDecision")))
            && text(migrationReport.get("sourceReceiptSchemaDigest")).equals(
                text(contract.get("sourceReceiptSchemaDigest")))
            && text(migrationReport.get("sourceValidationPlanDigest")).equals(
                text(contract.get("sourceValidationPlanDigest")))
            && Boolean.TRUE.equals(contract.get("mustBindValidationResultDigest"))
            && Boolean.TRUE.equals(contract.get("mustBindAuditEventDigest"))
            && Boolean.TRUE.equals(contract.get("mustBindTrustedPrincipalDigest"))
            && Boolean.TRUE.equals(contract.get("mustBindCodeReleaseSwitch"))
            && Boolean.TRUE.equals(contract.get("mustBeServerIssued"))
            && RELEASE_DENIED.equals(text(template.get("decision")))
            && VALIDATION_NOT_RUN.equals(text(template.get("validationStatus")))
            && Boolean.FALSE.equals(template.get("releaseEligible"))
            && Boolean.FALSE.equals(template.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(template.get("releaseCredentialIssued"))
            && Boolean.FALSE.equals(template.get("fallbackToLegacyAuditReceiptFlagAllowed"));
    }

    private static boolean legacyCompatibilityPolicyValid(Map<String, Object> policy) {
        return Boolean.FALSE.equals(policy.get("legacyAuditReceiptReleaseEligibleTrusted"))
            && Boolean.TRUE.equals(policy.get("legacyAuditReceiptCanOnlyBeInputEvidence"))
            && Boolean.TRUE.equals(policy.get("auditReceiptReleaseEligibleDeprecated"))
            && Boolean.FALSE.equals(policy.get("fallbackToLegacyReleaseFlagAllowed"))
            && Boolean.TRUE.equals(policy.get("stateMachineMigrationRequired"))
            && Boolean.TRUE.equals(policy.get("durableExecutorMigrationRequired"));
    }

    private static boolean releaseCredentialRulesValid(Map<String, Object> rules) {
        return Boolean.FALSE.equals(rules.get("migrationPlanIsReleaseCredential"))
            && Boolean.FALSE.equals(rules.get("validationGateReportIsReleaseCredential"))
            && Boolean.FALSE.equals(rules.get("schemaReportIsReleaseCredential"))
            && Boolean.FALSE.equals(rules.get("legacyAuditReceiptFlagIsReleaseCredential"))
            && Boolean.TRUE.equals(rules.get("futureReleaseDecisionRequired"))
            && Boolean.TRUE.equals(rules.get("serverIssuedDecisionRequired"));
    }

    private static boolean migrationFailureContractValid(Map<String, Object> failureContract) {
        List<String> statuses = stringList(failureContract.get("failureStatuses"));
        return Boolean.TRUE.equals(failureContract.get("failClosed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToValidationGateAllowed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToCallerDecisionAllowed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToLegacyAuditReceiptFlagAllowed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToMigrationPlanAllowed"))
            && statuses.equals(NimCreateDurableAuditValidationResultMigrationSupport.migrationFailureStatuses());
    }

    private static boolean migrationForbiddenShortcutsValid(Object rawShortcuts) {
        return stringList(rawShortcuts).equals(
            NimCreateDurableAuditValidationResultMigrationSupport.migrationForbiddenShortcuts());
    }

    private static Map<String, Object> releaseGatePlan(Map<String, Object> auditContext,
                                                       Map<String, Object> principal,
                                                       Map<String, Object> migrationReport) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("releaseGateBoundary", "SERVER_SIDE_RELEASE_DECISION_GATE_REQUIRED");
        plan.put("futureReleaseDecision", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        plan.put("futureValidationResult", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        plan.put("futureStateMachineGate", FUTURE_STATE_MACHINE_GATE);
        plan.put("futureDurableExecutorGate", FUTURE_DURABLE_EXECUTOR_GATE);
        plan.put("sourceMigrationPlanDigest", text(migrationReport.get("migrationPlanDigest")));
        plan.put("sourceValidationPlanDigest", text(migrationReport.get("sourceValidationPlanDigest")));
        plan.put("sourceReceiptSchemaDigest", text(migrationReport.get("sourceReceiptSchemaDigest")));
        plan.put("sourceInterfaceSpecDigest", text(migrationReport.get("sourceInterfaceSpecDigest")));
        plan.put("sourceBoundaryPlanDigest", text(migrationReport.get("sourceBoundaryPlanDigest")));
        plan.put("sourceWriterPlanDigest", text(migrationReport.get("sourceWriterPlanDigest")));
        plan.put("sourceAvailabilityPlanDigest", text(migrationReport.get("sourceAvailabilityPlanDigest")));
        plan.put("sourceAuditEventDigest", digestFor(auditContext));
        plan.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        plan.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        plan.put("gateSequence", gateSequence());
        plan.put("requiredFutureEvidence", requiredFutureEvidence(migrationReport));
        plan.put("stateMachineBindingPlan", stateMachineBindingPlan());
        plan.put("durableExecutorBindingPlan", durableExecutorBindingPlan());
        plan.put("currentDenyTemplate", currentDenyTemplate());
        plan.put("failureContract", releaseGateFailureContract());
        plan.put("forbiddenShortcuts", forbiddenShortcuts());
        return plan;
    }

    private static List<Map<String, Object>> gateSequence() {
        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(gateStep(
            "validate-migration-plan-digest",
            "Recompute and bind the M5.21-58 migration plan before accepting any release decision"
        ));
        sequence.add(gateStep(
            "validate-server-issued-validation-result",
            "Require a future server-issued validation result bound to the validation plan digest"
        ));
        sequence.add(gateStep(
            "validate-server-issued-release-decision",
            "Require a future server-issued release decision bound to validation result digest and trusted principal"
        ));
        sequence.add(gateStep(
            "bind-state-machine-release-check",
            "State machine must consume release decision digest instead of legacy auditReceipt.releaseEligible"
        ));
        sequence.add(gateStep(
            "bind-durable-executor-release-check",
            "Durable write executor must re-check release decision digest immediately before real POST"
        ));
        sequence.add(gateStep(
            "require-code-release-switch",
            "Code release switch must remain a separate server-side release condition"
        ));
        return sequence;
    }

    private static Map<String, Object> gateStep(String id, String requirement) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("requirement", requirement);
        step.put("futureOnly", true);
        step.put("sideEffectAllowedNow", false);
        step.put("failClosed", true);
        return step;
    }

    private static Map<String, Object> requiredFutureEvidence(Map<String, Object> migrationReport) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sourceMigrationPlanDigest", text(migrationReport.get("migrationPlanDigest")));
        evidence.put("validationResult", Map.of(
            "requiredType", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT,
            "requiredStatus", "PASS",
            "mustBindValidationPlanDigest", true,
            "mustBindAllTypedEvidenceDigests", true,
            "mustBindTrustedPrincipalDigest", true,
            "mustBeServerIssued", true
        ));
        evidence.put("releaseDecision", Map.of(
            "requiredType", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION,
            "requiredDecision", "ALLOW_WRITE_EXECUTION",
            "mustBindValidationResultDigest", true,
            "mustBindAuditEventDigest", true,
            "mustBindTrustedPrincipalDigest", true,
            "mustBindCodeReleaseSwitch", true,
            "mustBeServerIssued", true
        ));
        evidence.put("writeExecutionChain", Map.of(
            "mustBindBodyDigest", true,
            "mustBindRequestSpecDigest", true,
            "mustBindHandoffDigest", true,
            "mustBindAuditReceiptId", true,
            "mustBindAuditEventDigest", true,
            "mustBindServerDerivedIdempotencyKey", true,
            "mustBeRecheckedByStateMachine", true,
            "mustBeRecheckedByDurableExecutor", true
        ));
        return evidence;
    }

    private static Map<String, Object> stateMachineBindingPlan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("target", "NimCreateStateMachineSupport");
        plan.put("currentLegacyAuditReceiptReleaseFlagTrusted", false);
        plan.put("futureReleaseDecisionDigestRequired", true);
        plan.put("futureValidationResultDigestRequired", true);
        plan.put("futureBodyDigestRequired", true);
        plan.put("futureRequestSpecDigestRequired", true);
        plan.put("futureHandoffDigestRequired", true);
        plan.put("futureServerDerivedIdempotencyKeyRequired", true);
        plan.put("futureCodeReleaseSwitchRequired", true);
        plan.put("fallbackToAuditReceiptReleaseEligibleAllowed", false);
        plan.put("fallbackToMigrationPlanAllowed", false);
        plan.put("writePermittedCanBeTrueNow", false);
        return plan;
    }

    private static Map<String, Object> durableExecutorBindingPlan() {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("target", NimCreateDurableWriteExecutorSupport.EXECUTOR_NAME);
        plan.put("futureReleaseDecisionDigestRequired", true);
        plan.put("futureValidationResultDigestRequired", true);
        plan.put("futureBodyDigestRequired", true);
        plan.put("futureRequestSpecDigestRequired", true);
        plan.put("futureHandoffDigestRequired", true);
        plan.put("futureAuditReceiptIdRequired", true);
        plan.put("futureServerDerivedIdempotencyKeyRequired", true);
        plan.put("mustRecheckImmediatelyBeforePost", true);
        plan.put("fallbackToHandoffOnlyAllowed", false);
        plan.put("fallbackToRequestSpecOnlyAllowed", false);
        plan.put("fallbackToMigrationPlanAllowed", false);
        plan.put("realHttpExecutionAllowedNow", false);
        plan.put("writeExecutionAllowedNow", false);
        return plan;
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

    private static Map<String, Object> releaseGateFailureContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("failClosed", true);
        contract.put("fallbackToLegacyAuditReceiptFlagAllowed", false);
        contract.put("fallbackToMigrationPlanAllowed", false);
        contract.put("fallbackToCallerReleaseDecisionAllowed", false);
        contract.put("fallbackToDurableExecutorHandoffAllowed", false);
        contract.put("failureStatuses", List.of(
            "IMPLEMENTATION_HOLD",
            "RELEASE_DECISION_GATE_NOT_IMPLEMENTED",
            "VALIDATION_RESULT_NOT_IMPLEMENTED",
            "RELEASE_DECISION_NOT_IMPLEMENTED",
            "MIGRATION_PLAN_DIGEST_MISMATCH",
            "VALIDATION_RESULT_DIGEST_MISSING",
            "RELEASE_DECISION_DIGEST_MISSING",
            "CODE_RELEASE_SWITCH_NOT_OPEN",
            "LEGACY_AUDIT_RECEIPT_RELEASE_FLAG_NOT_TRUSTED",
            "FORGED_RELEASE_DECISION_CLAIM",
            "SECRET_MATERIAL_REJECTED"
        ));
        return contract;
    }

    private static List<String> forbiddenShortcuts() {
        return List.of(
            "accepting migration plan as release decision gate pass",
            "accepting caller-supplied releaseDecision or validationResult",
            "accepting legacy auditReceipt.releaseEligible=true as write permission",
            "allowing state machine writePermitted=true before release decision digest is verified",
            "allowing durable executor writeExecuted=true before release decision digest is re-checked",
            "treating code release switch as implied by validation result"
        );
    }

    private static boolean hasOnlyExpectedMigrationHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_IMPLEMENTATION_HOLD".equals(
                text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RELEASE_DECISION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        return NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            map,
            NimForbiddenSecretMaterialDetector.receiptSchemaPolicy()
        );
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
            if ("receiptStatus".equals(key)
                && NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(value))) {
                return true;
            }
            if ("storageMode".equals(key)
                && NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(value))) {
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
            "DURABLE_AUDIT_RELEASE_DECISION_GATE_FORGED_RELEASE_CLAIM",
            source + " 不得自称 releaseDecision、validationResult、releaseEligible、writePermitted、writeExecutionAllowed 或真实执行成功。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> migrationReport) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "auditReceipt",
            "legacyAuditReceipt",
            "validationResult",
            "validationPassed",
            "validationStatus",
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
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key) || principal.containsKey(key) || migrationReport.containsKey(key)) {
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

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record DurableAuditReleaseDecisionGateInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditValidationResultMigrationReport
    ) {
        DurableAuditReleaseDecisionGateInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditValidationResultMigrationReport = durableAuditValidationResultMigrationReport == null
                ? Map.of()
                : objectMap(durableAuditValidationResultMigrationReport);
        }

        static DurableAuditReleaseDecisionGateInput empty() {
            return new DurableAuditReleaseDecisionGateInput(Map.of(), Map.of(), Map.of());
        }
    }
}
