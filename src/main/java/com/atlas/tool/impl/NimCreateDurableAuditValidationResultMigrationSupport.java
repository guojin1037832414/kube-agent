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
 * NIM durable audit validation result / release decision 的迁移蓝图契约。
 *
 * <p>本类只把未来 {@code NimDurableAuditReceiptValidator} 应返回的强类型 validation result
 * 与 release decision 形状固化为可测试的合同；它不创建真实 DTO/Bean，不调用 kube-manager，
 * 不连接 Elasticsearch，也不把 migration plan 当成写执行放行凭证。</p>
 */
final class NimCreateDurableAuditValidationResultMigrationSupport {

    static final String PLAN_NAME = "NIM_CREATE_DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_PLAN";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String FUTURE_VALIDATION_RESULT = "NimDurableAuditReceiptValidationResult";
    static final String FUTURE_RELEASE_DECISION = "NimDurableAuditReleaseDecision";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";
    private static final String RELEASE_DENIED = "DENY_UNTIL_SERVER_VALIDATION_RESULT";

    private NimCreateDurableAuditValidationResultMigrationSupport() {
    }

    static List<String> migrationFailureStatuses() {
        return List.of(
            "IMPLEMENTATION_HOLD",
            "VALIDATION_RESULT_NOT_IMPLEMENTED",
            "RELEASE_DECISION_NOT_IMPLEMENTED",
            "VALIDATION_GATE_REPORT_INVALID",
            "VALIDATION_PLAN_DIGEST_MISMATCH",
            "VALIDATION_RESULT_DIGEST_MISSING",
            "RELEASE_DECISION_DIGEST_MISSING",
            "LEGACY_AUDIT_RECEIPT_RELEASE_FLAG_NOT_TRUSTED",
            "FORGED_RELEASE_DECISION_CLAIM",
            "SECRET_MATERIAL_REJECTED"
        );
    }

    static List<String> migrationForbiddenShortcuts() {
        return List.of(
            "accepting validation gate report as validation result",
            "accepting migration plan as release decision",
            "accepting caller-supplied validationResult or releaseDecision",
            "accepting legacy auditReceipt.releaseEligible=true as write permission",
            "accepting validationStatus=PASS without recomputing all typed evidence digests",
            "allowing durable write executor to POST before a server-issued release decision exists"
        );
    }

    static Map<String, Object> plan(DurableAuditValidationResultMigrationInput input) {
        DurableAuditValidationResultMigrationInput safeInput = input == null
            ? DurableAuditValidationResultMigrationInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> validationGateReport = safeInput.durableAuditReceiptValidationGateReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateValidationGateReport(auditContext, principal, validationGateReport, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditReceiptValidationGateReport", validationGateReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> migrationPlan = inputAccepted
            ? migrationPlan(auditContext, principal, validationGateReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_IMPLEMENTATION_HOLD",
                "validation result / release decision 迁移蓝图已定义，但真实 validator、强类型结果和 release gate 尚未实现；当前不能放行写执行。",
                "durable-audit-validation-result-migration"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditValidationResultMigrationPlan", PLAN_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("migrationPlanState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureValidator", NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR);
        result.put("futureValidationResult", FUTURE_VALIDATION_RESULT);
        result.put("futureReleaseDecision", FUTURE_RELEASE_DECISION);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("migrationPlanPrepared", inputAccepted);
        result.put("validationResultContractPrepared", inputAccepted);
        result.put("releaseDecisionContractPrepared", inputAccepted);
        result.put("realValidatorCreated", false);
        result.put("realValidationResultCreated", false);
        result.put("realReleaseDecisionCreated", false);
        result.put("realStorageTouched", false);
        result.put("storageProbeReceiptValidated", false);
        result.put("preWriteDurableAckValidated", false);
        result.put("postWriteDurableAckValidated", false);
        result.put("digestChainValidated", false);
        result.put("trustedPrincipalValidated", false);
        result.put("durableReceiptValidated", false);
        result.put("durableReceiptValidationPassed", false);
        result.put("durableReceiptAccepted", false);
        result.put("validationStatus", VALIDATION_NOT_RUN);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("releaseDecisionAccepted", false);
        result.put("releaseCredentialIssued", false);
        result.put("writeExecutionAllowed", false);
        result.put("legacyAuditReceiptReleaseFlagTrusted", false);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceReceiptSchemaDigest", text(validationGateReport.get("sourceReceiptSchemaDigest")));
        result.put("sourceValidationPlanDigest", text(validationGateReport.get("validationPlanDigest")));
        result.put("sourceInterfaceSpecDigest", text(validationGateReport.get("sourceInterfaceSpecDigest")));
        result.put("sourceBoundaryPlanDigest", text(validationGateReport.get("sourceBoundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(validationGateReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(validationGateReport.get("sourceAvailabilityPlanDigest")));
        result.put("migrationPlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("migrationPlanDigest", inputAccepted ? digestFor(migrationPlan) : "");
        result.put("migrationPlan", migrationPlan);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(auditContext, principal, validationGateReport));
        result.put("nextImplementationRequirements", List.of(
            "create reviewed server-side NimDurableAuditReceiptValidationResult after real typed evidence exists",
            "create reviewed server-side NimDurableAuditReleaseDecision that depends on the validation result digest",
            "migrate state machine release checks away from legacy auditReceipt.releaseEligible",
            "bind durable write executor release to the release decision digest instead of caller supplied flags",
            "keep nim_create held until validator, release decision gate, durable writer and readiness aftercare all pass review"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_VALIDATION_RESULT_MIGRATION",
                "validation result 迁移蓝图只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedValidationOrReleaseClaim(auditContext)) {
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
                "validation result 迁移蓝图必须绑定服务端可信 session principal，不能相信 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedValidationOrReleaseClaim(principal)) {
            blockers.add(forgedClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateValidationGateReport(Map<String, Object> auditContext,
                                                     Map<String, Object> principal,
                                                     Map<String, Object> validationGateReport,
                                                     List<Map<String, Object>> blockers) {
        if (validationGateReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_NOT_READY",
                "缺少 M5.21-57 receipt validation gate report；不能生成 validation result 迁移蓝图。",
                "durable-audit-receipt-validation-gate"
            ));
            return;
        }

        Map<String, Object> validationPlan = objectMap(validationGateReport.get("validationPlan"));
        boolean valid = NimCreateDurableAuditReceiptValidationGateSupport.GATE_NAME.equals(
                text(validationGateReport.get("durableAuditReceiptValidationGate")))
            && NimCreateDurableAuditReceiptValidationGateSupport.EXECUTION_MODE.equals(
                text(validationGateReport.get("executionMode")))
            && NimCreateDurableAuditReceiptValidationGateSupport.HOLD_STATE.equals(
                text(validationGateReport.get("gateState")))
            && NimCreateDurableAuditReceiptValidationGateSupport.HOLD_STATE.equals(
                text(validationGateReport.get("validationGateState")))
            && NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR.equals(
                text(validationGateReport.get("futureValidator")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(validationGateReport.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(
                text(validationGateReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(validationGateReport.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(validationGateReport.get("networkAccess")))
            && "NONE".equals(text(validationGateReport.get("sideEffect")))
            && Boolean.TRUE.equals(validationGateReport.get("inputAccepted"))
            && Boolean.TRUE.equals(validationGateReport.get("validationPlanPrepared"))
            && Boolean.TRUE.equals(validationGateReport.get("validationRulesPrepared"))
            && Boolean.FALSE.equals(validationGateReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(validationGateReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(validationGateReport.get("storageAvailable"))
            && Boolean.FALSE.equals(validationGateReport.get("storageProbeReceiptValidated"))
            && Boolean.FALSE.equals(validationGateReport.get("preWriteDurableAckValidated"))
            && Boolean.FALSE.equals(validationGateReport.get("postWriteDurableAckValidated"))
            && Boolean.FALSE.equals(validationGateReport.get("digestChainValidated"))
            && Boolean.FALSE.equals(validationGateReport.get("trustedPrincipalValidated"))
            && Boolean.FALSE.equals(validationGateReport.get("durableReceiptValidated"))
            && Boolean.FALSE.equals(validationGateReport.get("durableReceiptValidationPassed"))
            && Boolean.FALSE.equals(validationGateReport.get("durableReceiptAccepted"))
            && VALIDATION_NOT_RUN.equals(text(validationGateReport.get("validationStatus")))
            && Boolean.FALSE.equals(validationGateReport.get("durable"))
            && Boolean.FALSE.equals(validationGateReport.get("releaseEligible"))
            && Boolean.FALSE.equals(validationGateReport.get("durableReceiptCanBeIssued"))
            && Boolean.FALSE.equals(validationGateReport.get("durableReceiptIssued"))
            && Boolean.FALSE.equals(validationGateReport.get("writeExecutionAllowed"))
            && text(validationGateReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(validationGateReport.get("sourceReceiptSchemaDigest")).matches("[a-f0-9]{64}")
            && text(validationGateReport.get("sourceInterfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(validationGateReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(validationGateReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(validationGateReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(
                text(validationGateReport.get("validationPlanDigestAlgorithm")))
            && text(validationGateReport.get("validationPlanDigest")).matches("[a-f0-9]{64}")
            && text(validationGateReport.get("validationPlanDigest")).equals(digestFor(validationPlan))
            && hasOnlyExpectedValidationGateHold(validationGateReport.get("blockedBy"))
            && validationPlanContractValid(auditContext, principal, validationGateReport, validationPlan);

        if (!valid) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_INVALID_FOR_MIGRATION_PLAN",
                "validation result 迁移蓝图只能消费 M5.21-57 生成的、仍处于 HOLD 且未声明真实 pass 的 validation gate report。",
                "durable-audit-receipt-validation-gate"
            ));
        }
        if (hasForgedValidationOrReleaseClaim(validationGateReport)) {
            blockers.add(forgedClaimBlocker("durableAuditReceiptValidationGateReport"));
        }
    }

    private static boolean validationPlanContractValid(Map<String, Object> auditContext,
                                                       Map<String, Object> principal,
                                                       Map<String, Object> validationGateReport,
                                                       Map<String, Object> validationPlan) {
        Map<String, Object> identity = objectMap(validationPlan.get("trustedIdentityBinding"));
        return !validationPlan.isEmpty()
            && "SERVER_SIDE_DURABLE_RECEIPT_VALIDATION_GATE_REQUIRED".equals(
                text(validationPlan.get("validationBoundary")))
            && NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR.equals(
                text(validationPlan.get("futureValidator")))
            && text(validationGateReport.get("sourceReceiptSchemaDigest")).equals(
                text(validationPlan.get("sourceReceiptSchemaDigest")))
            && text(validationGateReport.get("sourceInterfaceSpecDigest")).equals(
                text(validationPlan.get("sourceInterfaceSpecDigest")))
            && text(validationGateReport.get("sourceBoundaryPlanDigest")).equals(
                text(validationPlan.get("sourceBoundaryPlanDigest")))
            && text(validationGateReport.get("sourceWriterPlanDigest")).equals(
                text(validationPlan.get("sourceWriterPlanDigest")))
            && text(validationGateReport.get("sourceAvailabilityPlanDigest")).equals(
                text(validationPlan.get("sourceAvailabilityPlanDigest")))
            && digestFor(auditContext).equals(text(validationPlan.get("sourceAuditEventDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(validationPlan.get("digestAlgorithm")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && validationSequenceValid(validationPlan.get("validationSequence"))
            && requiredEvidenceValid(validationGateReport, objectMap(validationPlan.get("requiredEvidence")))
            && releaseDecisionTemplateValid(objectMap(validationPlan.get("releaseDecisionTemplate")))
            && validationFailureContractValid(objectMap(validationPlan.get("failureContract")))
            && stringList(validationPlan.get("forbiddenShortcuts")).equals(
                NimCreateDurableAuditReceiptValidationGateSupport.validationForbiddenShortcuts());
    }

    private static boolean validationSequenceValid(Object rawSequence) {
        List<Map<String, Object>> sequence = listOfMaps(rawSequence);
        return sequence.size() == 5
            && "validate-schema-digest".equals(text(sequence.get(0).get("id")))
            && "validate-storage-probe-receipt".equals(text(sequence.get(1).get("id")))
            && "validate-pre-write-durable-ack".equals(text(sequence.get(2).get("id")))
            && "validate-post-write-durable-ack".equals(text(sequence.get(3).get("id")))
            && "validate-final-durable-receipt".equals(text(sequence.get(4).get("id")))
            && sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly")))
            && sequence.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow")))
            && sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed")));
    }

    private static boolean requiredEvidenceValid(Map<String, Object> validationGateReport,
                                                 Map<String, Object> requiredEvidence) {
        Map<String, Object> storageProbe = objectMap(requiredEvidence.get("storageProbeReceipt"));
        Map<String, Object> preAck = objectMap(requiredEvidence.get("preWriteDurableAck"));
        Map<String, Object> postAck = objectMap(requiredEvidence.get("postWriteDurableAck"));
        Map<String, Object> receipt = objectMap(requiredEvidence.get("durableReceipt"));
        return text(validationGateReport.get("sourceReceiptSchemaDigest")).equals(
                text(requiredEvidence.get("sourceReceiptSchemaDigest")))
            && NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE.equals(
                text(storageProbe.get("requiredType")))
            && Boolean.TRUE.equals(storageProbe.get("mustBindAuditEventDigest"))
            && Boolean.TRUE.equals(storageProbe.get("mustBeServerIssued"))
            && NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE.equals(
                text(preAck.get("requiredType")))
            && "PRE_WRITE_INTENT".equals(text(preAck.get("requiredPhase")))
            && Boolean.TRUE.equals(preAck.get("mustBindStorageProbeReceiptDigest"))
            && NimCreateDurableAuditReceiptSchemaSupport.POST_WRITE_ACK_TYPE.equals(
                text(postAck.get("requiredType")))
            && "POST_WRITE_RESULT".equals(text(postAck.get("requiredPhase")))
            && Boolean.TRUE.equals(postAck.get("mustBindPreWriteDurableAckDigest"))
            && NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE.equals(
                text(receipt.get("requiredType")))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(
                text(receipt.get("requiredReceiptStatus")))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(
                text(receipt.get("requiredStorageMode")))
            && Boolean.TRUE.equals(receipt.get("mustIncludeAllAckDigests"))
            && Boolean.TRUE.equals(receipt.get("mustBindTrustedPrincipalDigest"));
    }

    private static boolean releaseDecisionTemplateValid(Map<String, Object> decision) {
        return !decision.isEmpty()
            && VALIDATION_NOT_RUN.equals(text(decision.get("validationStatus")))
            && Boolean.FALSE.equals(decision.get("storageProbeReceiptValidated"))
            && Boolean.FALSE.equals(decision.get("preWriteDurableAckValidated"))
            && Boolean.FALSE.equals(decision.get("postWriteDurableAckValidated"))
            && Boolean.FALSE.equals(decision.get("digestChainValidated"))
            && Boolean.FALSE.equals(decision.get("trustedPrincipalValidated"))
            && Boolean.FALSE.equals(decision.get("durableReceiptValidated"))
            && Boolean.FALSE.equals(decision.get("releaseEligible"))
            && Boolean.FALSE.equals(decision.get("writeExecutionAllowed"))
            && "NOT_ISSUED".equals(text(decision.get("receiptStatus")))
            && "NONE".equals(text(decision.get("storageMode")));
    }

    private static boolean validationFailureContractValid(Map<String, Object> failureContract) {
        List<String> statuses = stringList(failureContract.get("failureStatuses"));
        return Boolean.TRUE.equals(failureContract.get("failClosed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToMockReceiptAllowed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToSchemaOnlyAllowed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToCallerReceiptAllowed"))
            && statuses.equals(NimCreateDurableAuditReceiptValidationGateSupport.validationFailureStatuses());
    }

    private static Map<String, Object> migrationPlan(Map<String, Object> auditContext,
                                                     Map<String, Object> principal,
                                                     Map<String, Object> validationGateReport) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("migrationBoundary", "SERVER_SIDE_VALIDATION_RESULT_AND_RELEASE_DECISION_REQUIRED");
        plan.put("futureValidator", NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR);
        plan.put("futureValidationResult", FUTURE_VALIDATION_RESULT);
        plan.put("futureReleaseDecision", FUTURE_RELEASE_DECISION);
        plan.put("sourceReceiptSchemaDigest", text(validationGateReport.get("sourceReceiptSchemaDigest")));
        plan.put("sourceValidationPlanDigest", text(validationGateReport.get("validationPlanDigest")));
        plan.put("sourceInterfaceSpecDigest", text(validationGateReport.get("sourceInterfaceSpecDigest")));
        plan.put("sourceBoundaryPlanDigest", text(validationGateReport.get("sourceBoundaryPlanDigest")));
        plan.put("sourceWriterPlanDigest", text(validationGateReport.get("sourceWriterPlanDigest")));
        plan.put("sourceAvailabilityPlanDigest", text(validationGateReport.get("sourceAvailabilityPlanDigest")));
        plan.put("sourceAuditEventDigest", digestFor(auditContext));
        plan.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        plan.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        plan.put("migrationSequence", migrationSequence());
        plan.put("validationResultContract", validationResultContract(validationGateReport));
        plan.put("releaseDecisionContract", releaseDecisionContract(validationGateReport));
        plan.put("legacyCompatibilityPolicy", legacyCompatibilityPolicy());
        plan.put("releaseCredentialRules", releaseCredentialRules());
        plan.put("failureContract", migrationFailureContract());
        plan.put("forbiddenShortcuts", forbiddenShortcuts());
        return plan;
    }

    private static List<Map<String, Object>> migrationSequence() {
        List<Map<String, Object>> sequence = new ArrayList<>();
        sequence.add(migrationStep(
            "keep-validation-gate-contract-only",
            "Treat the current validation gate plan as rules only, never as validation pass"
        ));
        sequence.add(migrationStep(
            "introduce-validation-result-value",
            "Future validator returns a server-issued validation result bound to all typed evidence digests"
        ));
        sequence.add(migrationStep(
            "introduce-release-decision-value",
            "Future release decision depends on the validation result digest and trusted principal binding"
        ));
        sequence.add(migrationStep(
            "migrate-state-machine-release-check",
            "State machine release must depend on release decision instead of legacy auditReceipt.releaseEligible"
        ));
        sequence.add(migrationStep(
            "bind-durable-executor-release-check",
            "Durable write executor must re-check release decision before any real POST attempt"
        ));
        return sequence;
    }

    private static Map<String, Object> migrationStep(String id, String requirement) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("requirement", requirement);
        step.put("futureOnly", true);
        step.put("sideEffectAllowedNow", false);
        step.put("failClosed", true);
        return step;
    }

    private static Map<String, Object> validationResultContract(Map<String, Object> validationGateReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("type", FUTURE_VALIDATION_RESULT);
        contract.put("producedBy", NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR);
        contract.put("futureOnly", true);
        contract.put("instanceAllowedNow", false);
        contract.put("sideEffectAllowedNow", false);
        contract.put("currentValidationStatus", VALIDATION_NOT_RUN);
        contract.put("requiredPassStatus", "PASS");
        contract.put("sourceReceiptSchemaDigest", text(validationGateReport.get("sourceReceiptSchemaDigest")));
        contract.put("sourceValidationPlanDigest", text(validationGateReport.get("validationPlanDigest")));
        contract.put("mustBindAuditEventDigest", true);
        contract.put("mustBindStorageProbeReceiptDigest", true);
        contract.put("mustBindPreWriteDurableAckDigest", true);
        contract.put("mustBindPostWriteDurableAckDigest", true);
        contract.put("mustBindDurableReceiptDigest", true);
        contract.put("mustBindTrustedPrincipalDigest", true);
        contract.put("mustBeServerIssued", true);
        contract.put("currentTemplate", failClosedValidationResultTemplate());
        return contract;
    }

    private static Map<String, Object> failClosedValidationResultTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("validationStatus", VALIDATION_NOT_RUN);
        template.put("storageProbeReceiptValidated", false);
        template.put("preWriteDurableAckValidated", false);
        template.put("postWriteDurableAckValidated", false);
        template.put("digestChainValidated", false);
        template.put("trustedPrincipalValidated", false);
        template.put("durableReceiptValidated", false);
        template.put("validationPassed", false);
        template.put("releaseEligible", false);
        template.put("writeExecutionAllowed", false);
        return template;
    }

    private static Map<String, Object> releaseDecisionContract(Map<String, Object> validationGateReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("type", FUTURE_RELEASE_DECISION);
        contract.put("dependsOn", FUTURE_VALIDATION_RESULT);
        contract.put("futureOnly", true);
        contract.put("instanceAllowedNow", false);
        contract.put("sideEffectAllowedNow", false);
        contract.put("currentDecision", RELEASE_DENIED);
        contract.put("requiredAllowDecision", "ALLOW_WRITE_EXECUTION");
        contract.put("sourceReceiptSchemaDigest", text(validationGateReport.get("sourceReceiptSchemaDigest")));
        contract.put("sourceValidationPlanDigest", text(validationGateReport.get("validationPlanDigest")));
        contract.put("mustBindValidationResultDigest", true);
        contract.put("mustBindAuditEventDigest", true);
        contract.put("mustBindTrustedPrincipalDigest", true);
        contract.put("mustBindCodeReleaseSwitch", true);
        contract.put("mustBeServerIssued", true);
        contract.put("currentTemplate", failClosedReleaseDecisionTemplate());
        return contract;
    }

    private static Map<String, Object> failClosedReleaseDecisionTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("decision", RELEASE_DENIED);
        template.put("validationStatus", VALIDATION_NOT_RUN);
        template.put("releaseEligible", false);
        template.put("writeExecutionAllowed", false);
        template.put("releaseCredentialIssued", false);
        template.put("fallbackToLegacyAuditReceiptFlagAllowed", false);
        return template;
    }

    private static Map<String, Object> legacyCompatibilityPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("legacyAuditReceiptReleaseEligibleTrusted", false);
        policy.put("legacyAuditReceiptCanOnlyBeInputEvidence", true);
        policy.put("auditReceiptReleaseEligibleDeprecated", true);
        policy.put("fallbackToLegacyReleaseFlagAllowed", false);
        policy.put("stateMachineMigrationRequired", true);
        policy.put("durableExecutorMigrationRequired", true);
        return policy;
    }

    private static Map<String, Object> releaseCredentialRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("migrationPlanIsReleaseCredential", false);
        rules.put("validationGateReportIsReleaseCredential", false);
        rules.put("schemaReportIsReleaseCredential", false);
        rules.put("legacyAuditReceiptFlagIsReleaseCredential", false);
        rules.put("futureReleaseDecisionRequired", true);
        rules.put("serverIssuedDecisionRequired", true);
        return rules;
    }

    private static Map<String, Object> migrationFailureContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("failClosed", true);
        contract.put("fallbackToValidationGateAllowed", false);
        contract.put("fallbackToCallerDecisionAllowed", false);
        contract.put("fallbackToLegacyAuditReceiptFlagAllowed", false);
        contract.put("fallbackToMigrationPlanAllowed", false);
        contract.put("failureStatuses", migrationFailureStatuses());
        return contract;
    }

    private static List<String> forbiddenShortcuts() {
        return migrationForbiddenShortcuts();
    }

    private static boolean hasOnlyExpectedValidationGateHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_IMPLEMENTATION_HOLD".equals(
                text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static boolean hasForgedValidationOrReleaseClaim(Map<String, Object> map) {
        return containsForgedValidationOrReleaseClaim(map);
    }

    private static boolean containsForgedValidationOrReleaseClaim(Map<String, Object> map) {
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
                && ("PASS".equals(text(value)) || "VALIDATED".equals(text(value)))) {
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
            if (isDocumentationContainerKey(key)) {
                continue;
            }
            if (value instanceof Map<?, ?> nested && containsForgedValidationOrReleaseClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem
                        && containsForgedValidationOrReleaseClaim(objectMap(nestedItem))) {
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
            || "storageProbeReceipt".equals(key)
            || "preWriteDurableAck".equals(key)
            || "postWriteDurableAck".equals(key)
            || "durableAuditReceipt".equals(key)
            || "durableReceipt".equals(key)
            || "validationResult".equals(key)
            || "releaseDecision".equals(key);
    }

    private static boolean isSuccessBooleanKey(String key) {
        return "storageProbeExecuted".equals(key)
            || "storageAvailable".equals(key)
            || "storageProbeReceiptIssued".equals(key)
            || "storageProbeReceiptValidated".equals(key)
            || "preWritePersisted".equals(key)
            || "postWritePersisted".equals(key)
            || "preWriteDurable".equals(key)
            || "postWriteDurable".equals(key)
            || "preWriteDurableAckIssued".equals(key)
            || "postWriteDurableAckIssued".equals(key)
            || "preWriteDurableAckValidated".equals(key)
            || "postWriteDurableAckValidated".equals(key)
            || "digestChainValidated".equals(key)
            || "trustedPrincipalValidated".equals(key)
            || "durableReceiptValidated".equals(key)
            || "durableReceiptValidationPassed".equals(key)
            || "durableReceiptAccepted".equals(key)
            || "validationPassed".equals(key)
            || "validationResultAccepted".equals(key)
            || "releaseDecisionAccepted".equals(key)
            || "releaseCredentialIssued".equals(key)
            || "writeExecutionAllowed".equals(key)
            || "durableReceiptCanBeIssued".equals(key)
            || "durableReceiptIssued".equals(key)
            || "releaseEligible".equals(key)
            || "realStorageTouched".equals(key)
            || "durable".equals(key);
    }

    private static boolean isDocumentationContainerKey(String key) {
        return "validationPlan".equals(key)
            || "validationSequence".equals(key)
            || "requiredEvidence".equals(key)
            || "releaseDecisionTemplate".equals(key)
            || "failureContract".equals(key)
            || "forbiddenShortcuts".equals(key);
    }

    private static Map<String, Object> forgedClaimBlocker(String source) {
        return blocker(
            "DURABLE_AUDIT_VALIDATION_RESULT_MIGRATION_FORGED_RELEASE_CLAIM",
            source + " 不得自称 validation PASS、validationResult、releaseDecision、legacy auditReceipt release flag 或 writeExecutionAllowed。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> validationGateReport) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "auditReceipt",
            "legacyAuditReceipt",
            "storageProbeReceipt",
            "storageProbeReceiptValidated",
            "preWriteDurableAck",
            "postWriteDurableAck",
            "preWriteDurableAckValidated",
            "postWriteDurableAckValidated",
            "durableAuditReceipt",
            "durableReceipt",
            "durableReceiptValidated",
            "digestChainValidated",
            "trustedPrincipalValidated",
            "validationResult",
            "validationPassed",
            "validationStatus",
            "releaseDecision",
            "releaseDecisionAccepted",
            "releaseCredentialIssued",
            "durableReceiptCanBeIssued",
            "durableReceiptIssued",
            "releaseEligible",
            "writeExecutionAllowed",
            "realStorageTouched",
            "durable",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key) || principal.containsKey(key) || validationGateReport.containsKey(key)) {
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

    record DurableAuditValidationResultMigrationInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditReceiptValidationGateReport
    ) {
        DurableAuditValidationResultMigrationInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditReceiptValidationGateReport = durableAuditReceiptValidationGateReport == null
                ? Map.of()
                : objectMap(durableAuditReceiptValidationGateReport);
        }

        static DurableAuditValidationResultMigrationInput empty() {
            return new DurableAuditValidationResultMigrationInput(Map.of(), Map.of(), Map.of());
        }
    }
}
