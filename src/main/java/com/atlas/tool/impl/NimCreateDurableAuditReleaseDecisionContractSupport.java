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
 * NIM durable audit release decision 的未来服务端签发契约。
 *
 * <p>本类只定义 future {@code NimDurableAuditReleaseDecision} 必须如何绑定 M5.21-70
 * validation result contract、未来 server-issued validation result digest、代码级 release switch 和写链路 digest。
 * 它不创建真实 release decision，不修改状态机，不绑定 HTTP/存储客户端，也不允许写执行。</p>
 */
final class NimCreateDurableAuditReleaseDecisionContractSupport {

    static final String DECISION_CONTRACT_NAME =
        "NIM_CREATE_DURABLE_AUDIT_RELEASE_DECISION_CONTRACT";
    static final String EXECUTION_MODE =
        "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String RELEASE_DENIED = "DENY_UNTIL_SERVER_VALIDATION_RESULT";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";

    private NimCreateDurableAuditReleaseDecisionContractSupport() {
    }

    static Map<String, Object> plan(ReleaseDecisionContractInput input) {
        ReleaseDecisionContractInput safeInput = input == null
            ? ReleaseDecisionContractInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> validationResultReport = safeInput.durableAuditReceiptValidationResultContractReport();
        Map<String, Object> callerReleaseEvidence = safeInput.callerReleaseEvidence();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateValidationResultContractReport(auditContext, principal, validationResultReport, blockers);
        validateCallerReleaseEvidence(callerReleaseEvidence, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditReceiptValidationResultContractReport",
            validationResultReport, blockers);
        validateNoSecretMaterial("callerReleaseEvidence", callerReleaseEvidence, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> releaseDecisionContract = inputAccepted
            ? releaseDecisionContract(auditContext, principal, validationResultReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_IMPLEMENTATION_HOLD",
                "server-issued release decision 契约已定义，但真实 release decision 签发、状态机回接和 durable executor 回接尚未实现；当前不能放行写执行。",
                "durable-audit-release-decision-contract"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditReleaseDecisionContract", DECISION_CONTRACT_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("releaseDecisionState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureReleaseDecision",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        result.put("futureValidationResult",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        result.put("futureStateMachineGate",
            NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_STATE_MACHINE_GATE);
        result.put("futureDurableExecutorGate",
            NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_DURABLE_EXECUTOR_GATE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("springBeanRegistered", false);
        result.put("httpClientBound", false);
        result.put("storageClientBound", false);
        result.put("inputAccepted", inputAccepted);
        result.put("releaseDecisionContractPrepared", inputAccepted);
        result.put("serverIssuedReleaseDecisionRequired", true);
        result.put("callerReleaseEvidenceAuthoritative", false);
        result.put("validationResultContractRequired", true);
        result.put("serverIssuedValidationResultDigestRequired", true);
        result.put("legacyValidationResultReportAloneAllowed", false);
        result.put("legacyAuditReceiptReleaseFlagTrusted", false);
        result.put("realReleaseDecisionCreated", false);
        result.put("serverIssuedReleaseDecisionAccepted", false);
        result.put("realValidationResultAccepted", false);
        result.put("validationResultDigestVerified", false);
        result.put("validationResultContractDigestVerified", false);
        result.put("releaseDecisionDigestVerified", false);
        result.put("trustedPrincipalValidated", false);
        result.put("codeReleaseSwitchVerified", false);
        result.put("stateMachineReleaseBound", false);
        result.put("durableExecutorReleaseBound", false);
        result.put("releaseDecisionAccepted", false);
        result.put("releaseCredentialIssued", false);
        result.put("releaseEligible", false);
        result.put("writePermitted", false);
        result.put("writeExecutionAllowed", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("realStorageTouched", false);
        result.put("validationStatus", VALIDATION_NOT_RUN);
        result.put("releaseDecision", RELEASE_DENIED);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("trustedPrincipalDigest", digestFor(principal));
        result.put("sourceValidationResultContractDigest",
            text(validationResultReport.get("validationResultContractDigest")));
        result.put("sourceEnhancedMigrationPlanDigest",
            text(validationResultReport.get("sourceEnhancedMigrationPlanDigest")));
        result.put("sourceProbeBindingPlanDigest",
            text(validationResultReport.get("sourceProbeBindingPlanDigest")));
        result.put("sourceProbeResultContractDigest",
            text(validationResultReport.get("sourceProbeResultContractDigest")));
        result.put("sourceProbeExecutorPlanDigest",
            text(validationResultReport.get("sourceProbeExecutorPlanDigest")));
        result.put("sourceMigrationPlanDigest", text(validationResultReport.get("sourceMigrationPlanDigest")));
        result.put("sourceReceiptSchemaDigest", text(validationResultReport.get("sourceReceiptSchemaDigest")));
        result.put("sourceValidationPlanDigest", text(validationResultReport.get("sourceValidationPlanDigest")));
        result.put("sourceInterfaceSpecDigest", text(validationResultReport.get("sourceInterfaceSpecDigest")));
        result.put("sourceBoundaryPlanDigest", text(validationResultReport.get("sourceBoundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(validationResultReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest",
            text(validationResultReport.get("sourceAvailabilityPlanDigest")));
        result.put("releaseDecisionContractDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("releaseDecisionContractDigest", inputAccepted ? digestFor(releaseDecisionContract) : "");
        result.put("releaseDecisionContract", releaseDecisionContract);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(
            auditContext,
            principal,
            validationResultReport,
            callerReleaseEvidence
        ));
        result.put("nextImplementationRequirements", List.of(
            "implement a reviewed server-side NimDurableAuditReleaseDecision issuer",
            "bind release decision digest to M5.21-70 validationResultContractDigest and server-issued validationResultDigest",
            "bind code release switch, state-machine gate and durable executor re-check before writePermitted can be true",
            "reject caller supplied releaseDecision, validationResult or legacy auditReceipt.releaseEligible as proof",
            "keep write execution held until release decision issuer, state-machine gate and durable executor all pass review"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_RELEASE_DECISION_CONTRACT",
                "release decision contract 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedReleaseClaim(auditContext)) {
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
                "TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY_FOR_RELEASE_DECISION",
                "release decision contract 必须绑定服务端可信 session principal，不能相信 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedReleaseClaim(principal)) {
            blockers.add(forgedClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateValidationResultContractReport(Map<String, Object> auditContext,
                                                               Map<String, Object> principal,
                                                               Map<String, Object> report,
                                                               List<Map<String, Object>> blockers) {
        if (report.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_REPORT_NOT_READY",
                "缺少 M5.21-70 receipt validation result contract report；不能定义 release decision contract。",
                "durable-audit-receipt-validation-result"
            ));
            return;
        }

        Map<String, Object> contract = objectMap(report.get("validationResultContract"));
        boolean valid = NimCreateDurableAuditReceiptValidationResultSupport.RESULT_CONTRACT_NAME.equals(
                text(report.get("durableAuditReceiptValidationResultContract")))
            && NimCreateDurableAuditReceiptValidationResultSupport.EXECUTION_MODE.equals(
                text(report.get("executionMode")))
            && NimCreateDurableAuditReceiptValidationResultSupport.HOLD_STATE.equals(
                text(report.get("validationResultState")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(report.get("futureValidationResult")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(report.get("futureReleaseDecision")))
            && NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.FUTURE_MIGRATION.equals(
                text(report.get("futureMigration")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(report.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(report.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(report.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(report.get("networkAccess")))
            && "NONE".equals(text(report.get("sideEffect")))
            && Boolean.FALSE.equals(report.get("springBeanRegistered"))
            && Boolean.FALSE.equals(report.get("httpClientBound"))
            && Boolean.FALSE.equals(report.get("storageClientBound"))
            && Boolean.TRUE.equals(report.get("inputAccepted"))
            && Boolean.TRUE.equals(report.get("validationResultContractPrepared"))
            && Boolean.TRUE.equals(report.get("serverIssuedValidationResultRequired"))
            && Boolean.FALSE.equals(report.get("callerValidationEvidenceAuthoritative"))
            && Boolean.FALSE.equals(report.get("legacyMigrationReportAloneAllowed"))
            && Boolean.FALSE.equals(report.get("realValidatorCreated"))
            && Boolean.FALSE.equals(report.get("realValidationResultCreated"))
            && Boolean.FALSE.equals(report.get("serverIssuedValidationResultAccepted"))
            && Boolean.FALSE.equals(report.get("realStorageTouched"))
            && Boolean.FALSE.equals(report.get("enhancedMigrationDigestVerified"))
            && Boolean.FALSE.equals(report.get("probeBindingDigestVerified"))
            && Boolean.FALSE.equals(report.get("probeResultContractDigestVerified"))
            && Boolean.FALSE.equals(report.get("storageProbeResultBoundForValidation"))
            && Boolean.FALSE.equals(report.get("storageProbeReceiptValidated"))
            && Boolean.FALSE.equals(report.get("preWriteDurableAckValidated"))
            && Boolean.FALSE.equals(report.get("postWriteDurableAckValidated"))
            && Boolean.FALSE.equals(report.get("digestChainValidated"))
            && Boolean.FALSE.equals(report.get("trustedPrincipalValidated"))
            && Boolean.FALSE.equals(report.get("durableReceiptValidated"))
            && Boolean.FALSE.equals(report.get("durableReceiptValidationPassed"))
            && Boolean.FALSE.equals(report.get("durableReceiptAccepted"))
            && VALIDATION_NOT_RUN.equals(text(report.get("validationStatus")))
            && Boolean.FALSE.equals(report.get("validationPassed"))
            && Boolean.FALSE.equals(report.get("durable"))
            && Boolean.FALSE.equals(report.get("releaseEligible"))
            && Boolean.FALSE.equals(report.get("releaseDecisionAccepted"))
            && Boolean.FALSE.equals(report.get("releaseCredentialIssued"))
            && Boolean.FALSE.equals(report.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(report.get("realHttpExecutionAllowed"))
            && Boolean.FALSE.equals(report.get("legacyAuditReceiptReleaseFlagTrusted"))
            && text(report.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(report.get("trustedPrincipalDigest")).equals(digestFor(principal))
            && text(report.get("sourceEnhancedMigrationPlanDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceProbeBindingPlanDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceProbeResultContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceProbeExecutorPlanDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceMigrationPlanDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceReceiptSchemaDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceValidationPlanDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceInterfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(
                text(report.get("validationResultContractDigestAlgorithm")))
            && text(report.get("validationResultContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("validationResultContractDigest")).equals(digestFor(contract))
            && hasOnlyExpectedValidationResultHold(report.get("blockedBy"))
            && validationResultContractValid(auditContext, principal, report, contract);

        if (!valid) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_REPORT_INVALID_FOR_RELEASE_DECISION",
                "release decision contract 只能消费 M5.21-70 生成的、仍处于 HOLD 且未签发真实 validation result 的 contract report。",
                "durable-audit-receipt-validation-result"
            ));
        }
        if (hasForgedReleaseClaim(report)) {
            blockers.add(forgedClaimBlocker("durableAuditReceiptValidationResultContractReport"));
        }
    }

    private static boolean validationResultContractValid(Map<String, Object> auditContext,
                                                         Map<String, Object> principal,
                                                         Map<String, Object> report,
                                                         Map<String, Object> contract) {
        Map<String, Object> identity = objectMap(contract.get("trustedIdentityBinding"));
        Map<String, Object> evidence = objectMap(contract.get("evidenceBinding"));
        Map<String, Object> template = objectMap(contract.get("currentTemplate"));
        Map<String, Object> failure = objectMap(contract.get("failureContract"));
        List<String> requiredFutureFields = stringList(contract.get("requiredFutureEvidenceDigestFields"));
        return !contract.isEmpty()
            && "SERVER_ISSUED_DURABLE_RECEIPT_VALIDATION_RESULT_REQUIRED".equals(
                text(contract.get("contractBoundary")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(contract.get("type")))
            && NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR.equals(
                text(contract.get("producedBy")))
            && Boolean.TRUE.equals(contract.get("futureOnly"))
            && Boolean.FALSE.equals(contract.get("instanceAllowedNow"))
            && VALIDATION_NOT_RUN.equals(text(contract.get("currentValidationStatus")))
            && "PASS".equals(text(contract.get("requiredPassStatus")))
            && Boolean.TRUE.equals(contract.get("serverIssuedRequired"))
            && Boolean.FALSE.equals(contract.get("callerProvidedValidationResultAllowed"))
            && sourceDigestFieldsMatch(report, contract)
            && digestFor(auditContext).equals(text(contract.get("sourceAuditEventDigest")))
            && digestFor(principal).equals(text(contract.get("trustedPrincipalDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(contract.get("digestAlgorithm")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && evidenceBindingValid(report, evidence)
            && requiredFutureFields.equals(requiredValidationResultDigestFields())
            && VALIDATION_NOT_RUN.equals(text(template.get("validationStatus")))
            && Boolean.FALSE.equals(template.get("enhancedMigrationDigestVerified"))
            && Boolean.FALSE.equals(template.get("probeBindingDigestVerified"))
            && Boolean.FALSE.equals(template.get("probeResultContractDigestVerified"))
            && Boolean.FALSE.equals(template.get("storageProbeReceiptValidated"))
            && Boolean.FALSE.equals(template.get("preWriteDurableAckValidated"))
            && Boolean.FALSE.equals(template.get("postWriteDurableAckValidated"))
            && Boolean.FALSE.equals(template.get("durableReceiptValidated"))
            && Boolean.FALSE.equals(template.get("validationPassed"))
            && Boolean.FALSE.equals(template.get("releaseEligible"))
            && Boolean.FALSE.equals(template.get("writeExecutionAllowed"))
            && Boolean.TRUE.equals(failure.get("failClosed"))
            && Boolean.FALSE.equals(failure.get("fallbackToMigrationPlanOnlyAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToProbeBindingPlanAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToSchemaOnlyAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToCallerValidationResultAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToLegacyAuditReceiptFlagAllowed"));
    }

    private static boolean sourceDigestFieldsMatch(Map<String, Object> report, Map<String, Object> contract) {
        for (String key : List.of(
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
        )) {
            if (!text(report.get(key)).equals(text(contract.get(key)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean evidenceBindingValid(Map<String, Object> report, Map<String, Object> evidence) {
        return sourceDigestFieldsMatch(report, evidence)
            && text(report.get("trustedPrincipalDigest")).equals(text(evidence.get("trustedPrincipalDigest")))
            && Boolean.TRUE.equals(evidence.get("mustBindEnhancedMigrationDigest"))
            && Boolean.TRUE.equals(evidence.get("mustBindProbeResultBindingDigest"))
            && Boolean.TRUE.equals(evidence.get("mustBindProbeResultContractDigest"))
            && Boolean.TRUE.equals(evidence.get("mustBindStorageProbeReceiptDigest"))
            && Boolean.TRUE.equals(evidence.get("mustBindPreWriteDurableAckDigest"))
            && Boolean.TRUE.equals(evidence.get("mustBindPostWriteDurableAckDigest"))
            && Boolean.TRUE.equals(evidence.get("mustBindDurableReceiptDigest"))
            && Boolean.TRUE.equals(evidence.get("mustBindTrustedPrincipalDigest"))
            && Boolean.TRUE.equals(evidence.get("mustBeServerIssued"));
    }

    private static List<String> requiredValidationResultDigestFields() {
        return List.of(
            "sourceEnhancedMigrationPlanDigest",
            "sourceProbeBindingPlanDigest",
            "sourceProbeResultContractDigest",
            "sourceProbeExecutorPlanDigest",
            "storageProbeReceiptDigest",
            "preWriteDurableAckDigest",
            "postWriteDurableAckDigest",
            "durableReceiptDigest",
            "trustedPrincipalDigest",
            "sourceAuditEventDigest"
        );
    }

    private static void validateCallerReleaseEvidence(Map<String, Object> callerReleaseEvidence,
                                                      List<Map<String, Object>> blockers) {
        if (!callerReleaseEvidence.isEmpty()) {
            blockers.add(blocker(
                "CALLER_RELEASE_EVIDENCE_NOT_AUTHORITATIVE",
                "调用方提供的 release decision、validation result、audit receipt 或 executor success 无权参与 release decision 签发。",
                "caller-release-evidence"
            ));
        }
        if (hasForgedReleaseClaim(callerReleaseEvidence)) {
            blockers.add(forgedClaimBlocker("callerReleaseEvidence"));
        }
    }

    private static Map<String, Object> releaseDecisionContract(Map<String, Object> auditContext,
                                                               Map<String, Object> principal,
                                                               Map<String, Object> validationResultReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contractBoundary", "SERVER_ISSUED_DURABLE_AUDIT_RELEASE_DECISION_REQUIRED");
        contract.put("type", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        contract.put("dependsOn", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        contract.put("futureOnly", true);
        contract.put("instanceAllowedNow", false);
        contract.put("currentDecision", RELEASE_DENIED);
        contract.put("requiredAllowDecision", "ALLOW_WRITE_EXECUTION");
        contract.put("serverIssuedRequired", true);
        contract.put("callerProvidedReleaseDecisionAllowed", false);
        contract.put("sourceValidationResultContractDigest",
            text(validationResultReport.get("validationResultContractDigest")));
        putSourceDigests(contract, validationResultReport);
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
        contract.put("validationResultBinding", validationResultBinding(validationResultReport));
        contract.put("stateMachineBinding", stateMachineBinding());
        contract.put("durableExecutorBinding", durableExecutorBinding());
        contract.put("requiredFutureEvidenceDigestFields", requiredFutureReleaseDecisionDigestFields());
        contract.put("currentTemplate", currentReleaseDecisionTemplate());
        contract.put("allowPrerequisites", allowPrerequisites());
        contract.put("failureContract", releaseDecisionFailureContract());
        contract.put("forbiddenShortcuts", forbiddenShortcuts());
        return contract;
    }

    private static void putSourceDigests(Map<String, Object> target, Map<String, Object> source) {
        for (String key : List.of(
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
        )) {
            target.put(key, text(source.get(key)));
        }
    }

    private static Map<String, Object> validationResultBinding(Map<String, Object> validationResultReport) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("sourceValidationResultContractDigest",
            text(validationResultReport.get("validationResultContractDigest")));
        putSourceDigests(binding, validationResultReport);
        binding.put("trustedPrincipalDigest", text(validationResultReport.get("trustedPrincipalDigest")));
        binding.put("futureValidationResultDigestRequired", true);
        binding.put("mustBindValidationResultContractDigest", true);
        binding.put("mustBindServerIssuedValidationResultDigest", true);
        binding.put("mustBindValidationPassStatus", true);
        binding.put("mustBindAllTypedReceiptAckDigests", true);
        binding.put("mustBindTrustedPrincipalDigest", true);
        binding.put("mustBindAuditEventDigest", true);
        binding.put("callerValidationResultAllowed", false);
        return binding;
    }

    private static Map<String, Object> stateMachineBinding() {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("futureStateMachineGate",
            NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_STATE_MACHINE_GATE);
        binding.put("futureReleaseDecisionDigestRequired", true);
        binding.put("futureValidationResultDigestRequired", true);
        binding.put("futureCodeReleaseSwitchRequired", true);
        binding.put("fallbackToAuditReceiptReleaseEligibleAllowed", false);
        binding.put("fallbackToValidationResultContractAllowed", false);
        binding.put("writePermittedCanBeTrueNow", false);
        return binding;
    }

    private static Map<String, Object> durableExecutorBinding() {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("futureDurableExecutorGate",
            NimCreateDurableAuditReleaseDecisionGateSupport.FUTURE_DURABLE_EXECUTOR_GATE);
        binding.put("futureReleaseDecisionDigestRequired", true);
        binding.put("futureValidationResultDigestRequired", true);
        binding.put("futureBodyDigestRequired", true);
        binding.put("futureRequestSpecDigestRequired", true);
        binding.put("futureHandoffDigestRequired", true);
        binding.put("futureAuditReceiptIdRequired", true);
        binding.put("futureServerDerivedIdempotencyKeyRequired", true);
        binding.put("mustRecheckImmediatelyBeforePost", true);
        binding.put("fallbackToHandoffOnlyAllowed", false);
        binding.put("fallbackToRequestSpecOnlyAllowed", false);
        binding.put("writeExecutionAllowedNow", false);
        return binding;
    }

    private static List<String> requiredFutureReleaseDecisionDigestFields() {
        return List.of(
            "validationResultContractDigest",
            "validationResultDigest",
            "releaseDecisionDigest",
            "codeReleaseSwitchDigest",
            "bodyDigest",
            "requestSpecDigest",
            "handoffDigest",
            "auditReceiptId",
            "sourceAuditEventDigest",
            "trustedPrincipalDigest",
            "serverDerivedIdempotencyKey"
        );
    }

    private static Map<String, Object> currentReleaseDecisionTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("decision", RELEASE_DENIED);
        template.put("validationStatus", VALIDATION_NOT_RUN);
        template.put("validationResultDigestVerified", false);
        template.put("validationResultContractDigestVerified", false);
        template.put("releaseDecisionDigestVerified", false);
        template.put("trustedPrincipalValidated", false);
        template.put("codeReleaseSwitchVerified", false);
        template.put("stateMachineReleaseBound", false);
        template.put("durableExecutorReleaseBound", false);
        template.put("releaseEligible", false);
        template.put("writePermitted", false);
        template.put("writeExecutionAllowed", false);
        template.put("realHttpExecutionAllowed", false);
        return template;
    }

    private static Map<String, Object> allowPrerequisites() {
        Map<String, Object> prerequisites = new LinkedHashMap<>();
        prerequisites.put("serverIssuedValidationResultDigestRequired", true);
        prerequisites.put("validationResultContractDigestRequired", true);
        prerequisites.put("releaseDecisionDigestRequired", true);
        prerequisites.put("codeReleaseSwitchRequired", true);
        prerequisites.put("stateMachineRecheckRequired", true);
        prerequisites.put("durableExecutorRecheckRequired", true);
        prerequisites.put("writeChainDigestsRequired", true);
        prerequisites.put("currentContractSatisfiesPrerequisites", false);
        return prerequisites;
    }

    private static Map<String, Object> releaseDecisionFailureContract() {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failClosed", true);
        failure.put("fallbackToValidationResultContractAllowed", false);
        failure.put("fallbackToCallerReleaseDecisionAllowed", false);
        failure.put("fallbackToLegacyAuditReceiptFlagAllowed", false);
        failure.put("fallbackToStateMachineAcceptedBooleanAllowed", false);
        failure.put("fallbackToExecutorSuccessAllowed", false);
        return failure;
    }

    private static List<String> forbiddenShortcuts() {
        return List.of(
            "accepting M5.21-70 validation result contract as release decision",
            "accepting caller-supplied releaseDecision or validationResult",
            "accepting legacy auditReceipt.releaseEligible=true as release evidence",
            "accepting releaseDecisionGateReportAccepted=true as write permission",
            "allowing durable executor success claims to backfill release evidence",
            "allowing release decision before code release switch and validation result digest exist"
        );
    }

    private static boolean hasOnlyExpectedValidationResultHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_IMPLEMENTATION_HOLD".equals(
                text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static boolean hasForgedReleaseClaim(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForgedReleaseClaim(entry.getKey(), value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && hasForgedReleaseClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && hasForgedReleaseClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isForgedReleaseClaim(String key, Object value) {
        return switch (key) {
            case "realValidationResultCreated",
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
                "codeReleaseSwitchVerified",
                "realReleaseDecisionLoaded",
                "realReleaseDecisionAccepted",
                "stateMachineReleaseGateImplemented",
                "stateMachineReleaseBound",
                "stateMachineCanSetWritePermittedNow",
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
            case "validationStatus" -> Set.of("PASS", "VALIDATED", "APPROVED").contains(text(value));
            case "decision",
                "releaseDecision" -> "ALLOW_WRITE_EXECUTION".equals(text(value)) || value instanceof Map<?, ?>;
            case "validationResult",
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
            "DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_FORGED_RELEASE_CLAIM",
            source + " 不得自称 release decision 已创建、ALLOW、release eligible、write permitted 或真实写执行成功。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> validationResultReport,
                                                    Map<String, Object> callerReleaseEvidence) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
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
                || validationResultReport.containsKey(key)
                || callerReleaseEvidence.containsKey(key)) {
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

    record ReleaseDecisionContractInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditReceiptValidationResultContractReport,
        Map<String, Object> callerReleaseEvidence
    ) {
        ReleaseDecisionContractInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditReceiptValidationResultContractReport =
                durableAuditReceiptValidationResultContractReport == null
                    ? Map.of()
                    : objectMap(durableAuditReceiptValidationResultContractReport);
            callerReleaseEvidence = callerReleaseEvidence == null ? Map.of() : objectMap(callerReleaseEvidence);
        }

        static ReleaseDecisionContractInput empty() {
            return new ReleaseDecisionContractInput(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
