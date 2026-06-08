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
 * NIM durable audit receipt validation result 的未来服务端签发契约。
 *
 * <p>本类只定义 future {@code NimDurableAuditReceiptValidationResult} 必须如何绑定
 * M5.21-69 enhanced migration plan、M5.21-68 probe binding、typed receipt/ack 证据和可信身份。
 * 它不创建真实 validation result，不运行 validator，不注册 Spring Bean，不访问 kube-manager、
 * Elasticsearch 或 sys_log，也不让任何写执行状态变为 true。</p>
 */
final class NimCreateDurableAuditReceiptValidationResultSupport {

    static final String RESULT_CONTRACT_NAME =
        "NIM_CREATE_DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT";
    static final String EXECUTION_MODE =
        "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";

    private NimCreateDurableAuditReceiptValidationResultSupport() {
    }

    static Map<String, Object> plan(ReceiptValidationResultInput input) {
        ReceiptValidationResultInput safeInput = input == null
            ? ReceiptValidationResultInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> migrationReport = safeInput.validationResultProbeBindingMigrationReport();
        Map<String, Object> callerValidationEvidence = safeInput.callerValidationEvidence();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateEnhancedMigrationReport(auditContext, principal, migrationReport, blockers);
        validateCallerValidationEvidence(callerValidationEvidence, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("validationResultProbeBindingMigrationReport", migrationReport, blockers);
        validateNoSecretMaterial("callerValidationEvidence", callerValidationEvidence, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> resultContract = inputAccepted
            ? validationResultContract(auditContext, principal, migrationReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_IMPLEMENTATION_HOLD",
                "server-issued receipt validation result 契约已定义，但真实 validator 和结果签发尚未实现；当前不能产生 PASS 或放行写执行。",
                "durable-audit-receipt-validation-result"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditReceiptValidationResultContract", RESULT_CONTRACT_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("validationResultState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureValidationResult",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        result.put("futureReleaseDecision",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        result.put("futureMigration",
            NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.FUTURE_MIGRATION);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("springBeanRegistered", false);
        result.put("httpClientBound", false);
        result.put("storageClientBound", false);
        result.put("inputAccepted", inputAccepted);
        result.put("validationResultContractPrepared", inputAccepted);
        result.put("serverIssuedValidationResultRequired", true);
        result.put("callerValidationEvidenceAuthoritative", false);
        result.put("legacyMigrationReportAloneAllowed", false);
        result.put("realValidatorCreated", false);
        result.put("realValidationResultCreated", false);
        result.put("serverIssuedValidationResultAccepted", false);
        result.put("realStorageTouched", false);
        result.put("enhancedMigrationDigestVerified", false);
        result.put("probeBindingDigestVerified", false);
        result.put("probeResultContractDigestVerified", false);
        result.put("storageProbeResultBoundForValidation", false);
        result.put("storageProbeReceiptValidated", false);
        result.put("preWriteDurableAckValidated", false);
        result.put("postWriteDurableAckValidated", false);
        result.put("digestChainValidated", false);
        result.put("trustedPrincipalValidated", false);
        result.put("durableReceiptValidated", false);
        result.put("durableReceiptValidationPassed", false);
        result.put("durableReceiptAccepted", false);
        result.put("validationStatus", VALIDATION_NOT_RUN);
        result.put("validationPassed", false);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("releaseDecisionAccepted", false);
        result.put("releaseCredentialIssued", false);
        result.put("writeExecutionAllowed", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("legacyAuditReceiptReleaseFlagTrusted", false);
        result.put("sourceOrganizationId", text(auditContext.get("organizationId")));
        result.put("sourceUserId", text(auditContext.get("userId")));
        result.put("sourceUsername", text(principal.get("username")));
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("trustedPrincipalDigest", digestFor(principal));
        result.put("sourceEnhancedMigrationPlanDigest", text(migrationReport.get("enhancedMigrationPlanDigest")));
        result.put("sourceProbeBindingPlanDigest", text(migrationReport.get("sourceProbeBindingPlanDigest")));
        result.put("sourceProbeResultContractDigest", text(migrationReport.get("sourceProbeResultContractDigest")));
        result.put("sourceProbeExecutorPlanDigest", text(migrationReport.get("sourceProbeExecutorPlanDigest")));
        result.put("sourceMigrationPlanDigest", text(migrationReport.get("sourceMigrationPlanDigest")));
        result.put("sourceReceiptSchemaDigest", text(migrationReport.get("sourceReceiptSchemaDigest")));
        result.put("sourceValidationPlanDigest", text(migrationReport.get("sourceValidationPlanDigest")));
        result.put("sourceInterfaceSpecDigest", text(migrationReport.get("sourceInterfaceSpecDigest")));
        result.put("sourceBoundaryPlanDigest", text(migrationReport.get("sourceBoundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(migrationReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(migrationReport.get("sourceAvailabilityPlanDigest")));
        result.put("validationResultContractDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("validationResultContractDigest", inputAccepted ? digestFor(resultContract) : "");
        result.put("validationResultContract", resultContract);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(
            auditContext,
            principal,
            migrationReport,
            callerValidationEvidence
        ));
        result.put("nextImplementationRequirements", List.of(
            "implement a reviewed server-side NimDurableAuditReceiptValidationResult issuer",
            "bind validation result digest to M5.21-69 enhanced migration digest and M5.21-68 probe binding digest",
            "bind typed storage probe receipt, pre-write durable ack, post-write durable ack and final durable receipt digests",
            "reject caller supplied validationResult, releaseDecision or legacy auditReceipt.releaseEligible as proof",
            "keep release decision and write execution held until a reviewed server-issued validation result exists"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_RECEIPT_VALIDATION_RESULT",
                "receipt validation result 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedValidationResultClaim(auditContext)) {
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
                "receipt validation result 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedValidationResultClaim(principal)) {
            blockers.add(forgedClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateEnhancedMigrationReport(Map<String, Object> auditContext,
                                                        Map<String, Object> principal,
                                                        Map<String, Object> migrationReport,
                                                        List<Map<String, Object>> blockers) {
        if (migrationReport.isEmpty()) {
            blockers.add(blocker(
                "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_NOT_READY",
                "缺少 M5.21-69 probe-binding-aware migration report；不能签发 validation result。",
                "validation-result-probe-binding-migration"
            ));
            return;
        }

        Map<String, Object> enhancedPlan = objectMap(migrationReport.get("enhancedMigrationPlan"));
        boolean valid = NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.PLAN_NAME.equals(
                text(migrationReport.get("durableAuditValidationResultProbeBindingMigrationPlan")))
            && NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.EXECUTION_MODE.equals(
                text(migrationReport.get("executionMode")))
            && NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.HOLD_STATE.equals(
                text(migrationReport.get("migrationState")))
            && NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.FUTURE_MIGRATION.equals(
                text(migrationReport.get("futureMigration")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(migrationReport.get("futureValidationResult")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(migrationReport.get("futureReleaseDecision")))
            && NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.FUTURE_BINDING.equals(
                text(migrationReport.get("futureProbeBinding")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(migrationReport.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(migrationReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(migrationReport.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(migrationReport.get("networkAccess")))
            && "NONE".equals(text(migrationReport.get("sideEffect")))
            && Boolean.FALSE.equals(migrationReport.get("springBeanRegistered"))
            && Boolean.FALSE.equals(migrationReport.get("httpClientBound"))
            && Boolean.FALSE.equals(migrationReport.get("storageClientBound"))
            && Boolean.TRUE.equals(migrationReport.get("inputAccepted"))
            && Boolean.TRUE.equals(migrationReport.get("enhancedMigrationPlanPrepared"))
            && Boolean.TRUE.equals(migrationReport.get("probeBindingRequiredBeforeValidationResult"))
            && Boolean.FALSE.equals(migrationReport.get("legacyMigrationReportAloneAllowed"))
            && Boolean.FALSE.equals(migrationReport.get("callerReleaseEvidenceAuthoritative"))
            && Boolean.FALSE.equals(migrationReport.get("probeBindingBoundToValidationResultMigration"))
            && Boolean.FALSE.equals(migrationReport.get("realValidatorCreated"))
            && Boolean.FALSE.equals(migrationReport.get("realValidationResultCreated"))
            && Boolean.FALSE.equals(migrationReport.get("realReleaseDecisionCreated"))
            && Boolean.FALSE.equals(migrationReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(migrationReport.get("storageProbeResultBoundForValidation"))
            && Boolean.FALSE.equals(migrationReport.get("serverIssuedProbeResultAccepted"))
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
            && Boolean.FALSE.equals(migrationReport.get("realHttpExecutionAllowed"))
            && Boolean.FALSE.equals(migrationReport.get("legacyAuditReceiptReleaseFlagTrusted"))
            && text(migrationReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(migrationReport.get("trustedPrincipalDigest")).equals(digestFor(principal))
            && text(migrationReport.get("sourceProbeBindingPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceProbeResultContractDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceProbeExecutorPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceMigrationPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceReceiptSchemaDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceValidationPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceInterfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(
                text(migrationReport.get("enhancedMigrationPlanDigestAlgorithm")))
            && text(migrationReport.get("enhancedMigrationPlanDigest")).matches("[a-f0-9]{64}")
            && text(migrationReport.get("enhancedMigrationPlanDigest")).equals(digestFor(enhancedPlan))
            && hasOnlyExpectedMigrationHold(migrationReport.get("blockedBy"))
            && enhancedMigrationPlanValid(auditContext, principal, migrationReport, enhancedPlan);

        if (!valid) {
            blockers.add(blocker(
                "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_REPORT_INVALID_FOR_RESULT",
                "receipt validation result 只能消费 M5.21-69 产生的、仍为 HOLD 且绑定 probe result contract 的 enhanced migration report。",
                "validation-result-probe-binding-migration"
            ));
        }
        if (hasForgedValidationResultClaim(migrationReport)) {
            blockers.add(forgedClaimBlocker("validationResultProbeBindingMigrationReport"));
        }
    }

    private static boolean enhancedMigrationPlanValid(Map<String, Object> auditContext,
                                                      Map<String, Object> principal,
                                                      Map<String, Object> report,
                                                      Map<String, Object> plan) {
        Map<String, Object> identity = objectMap(plan.get("trustedIdentityBinding"));
        Map<String, Object> requirement = objectMap(plan.get("probeBindingRequirement"));
        Map<String, Object> validationResult = objectMap(plan.get("enhancedValidationResultContract"));
        Map<String, Object> releaseDecision = objectMap(plan.get("enhancedReleaseDecisionContract"));
        Map<String, Object> failure = objectMap(plan.get("failureContract"));
        return !plan.isEmpty()
            && "SERVER_SIDE_VALIDATION_RESULT_AND_RELEASE_DECISION_REQUIRE_PROBE_BINDING".equals(
                text(plan.get("migrationBoundary")))
            && NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.FUTURE_MIGRATION.equals(
                text(plan.get("futureMigration")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(plan.get("futureValidationResult")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(plan.get("futureReleaseDecision")))
            && text(report.get("sourceProbeBindingPlanDigest")).equals(
                text(plan.get("sourceProbeBindingPlanDigest")))
            && text(report.get("sourceProbeResultContractDigest")).equals(
                text(plan.get("sourceProbeResultContractDigest")))
            && text(report.get("sourceProbeExecutorPlanDigest")).equals(
                text(plan.get("sourceProbeExecutorPlanDigest")))
            && text(report.get("sourceMigrationPlanDigest")).equals(text(plan.get("sourceMigrationPlanDigest")))
            && text(report.get("sourceReceiptSchemaDigest")).equals(text(plan.get("sourceReceiptSchemaDigest")))
            && text(report.get("sourceValidationPlanDigest")).equals(text(plan.get("sourceValidationPlanDigest")))
            && text(report.get("sourceInterfaceSpecDigest")).equals(text(plan.get("sourceInterfaceSpecDigest")))
            && text(report.get("sourceBoundaryPlanDigest")).equals(text(plan.get("sourceBoundaryPlanDigest")))
            && text(report.get("sourceWriterPlanDigest")).equals(text(plan.get("sourceWriterPlanDigest")))
            && text(report.get("sourceAvailabilityPlanDigest")).equals(
                text(plan.get("sourceAvailabilityPlanDigest")))
            && digestFor(auditContext).equals(text(plan.get("sourceAuditEventDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(plan.get("digestAlgorithm")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && probeBindingRequirementValid(report, requirement)
            && enhancedValidationResultContractValid(report, validationResult)
            && enhancedReleaseDecisionContractValid(report, releaseDecision)
            && Boolean.TRUE.equals(failure.get("failClosed"))
            && Boolean.FALSE.equals(failure.get("fallbackToValidationGateOnlyAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToSchemaOnlyAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToMigrationPlanOnlyAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToCallerReleaseEvidenceAllowed"));
    }

    private static boolean probeBindingRequirementValid(Map<String, Object> report,
                                                        Map<String, Object> requirement) {
        return NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.BINDING_NAME.equals(
                text(requirement.get("requiredReportName")))
            && HOLD_STATE.equals(text(requirement.get("requiredStateNow")))
            && text(report.get("sourceProbeBindingPlanDigest")).equals(
                text(requirement.get("sourceProbeBindingPlanDigest")))
            && text(report.get("sourceProbeResultContractDigest")).equals(
                text(requirement.get("sourceProbeResultContractDigest")))
            && Boolean.TRUE.equals(requirement.get("mustBindProbeResultBindingDigest"))
            && Boolean.FALSE.equals(requirement.get("probeBindingReportCanPassNow"))
            && Boolean.FALSE.equals(requirement.get("fallbackToValidationGateOnlyAllowed"))
            && Boolean.FALSE.equals(requirement.get("fallbackToMigrationPlanOnlyAllowed"));
    }

    private static boolean enhancedValidationResultContractValid(Map<String, Object> report,
                                                                 Map<String, Object> validationResult) {
        Map<String, Object> template = objectMap(validationResult.get("currentTemplate"));
        return NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(validationResult.get("type")))
            && NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR.equals(
                text(validationResult.get("producedBy")))
            && Boolean.TRUE.equals(validationResult.get("futureOnly"))
            && Boolean.FALSE.equals(validationResult.get("instanceAllowedNow"))
            && VALIDATION_NOT_RUN.equals(text(validationResult.get("currentValidationStatus")))
            && "PASS".equals(text(validationResult.get("requiredPassStatus")))
            && text(report.get("sourceProbeBindingPlanDigest")).equals(
                text(validationResult.get("sourceProbeBindingPlanDigest")))
            && text(report.get("sourceProbeResultContractDigest")).equals(
                text(validationResult.get("sourceProbeResultContractDigest")))
            && text(report.get("sourceMigrationPlanDigest")).equals(
                text(validationResult.get("sourceMigrationPlanDigest")))
            && text(report.get("sourceReceiptSchemaDigest")).equals(
                text(validationResult.get("sourceReceiptSchemaDigest")))
            && text(report.get("sourceValidationPlanDigest")).equals(
                text(validationResult.get("sourceValidationPlanDigest")))
            && Boolean.TRUE.equals(validationResult.get("mustBindProbeResultBindingDigest"))
            && Boolean.TRUE.equals(validationResult.get("mustBindProbeResultContractDigest"))
            && Boolean.TRUE.equals(validationResult.get("mustBindStorageProbeReceiptDigest"))
            && Boolean.TRUE.equals(validationResult.get("mustBindPreWriteDurableAckDigest"))
            && Boolean.TRUE.equals(validationResult.get("mustBindPostWriteDurableAckDigest"))
            && Boolean.TRUE.equals(validationResult.get("mustBindDurableReceiptDigest"))
            && Boolean.TRUE.equals(validationResult.get("mustBindTrustedPrincipalDigest"))
            && Boolean.TRUE.equals(validationResult.get("mustBeServerIssued"))
            && VALIDATION_NOT_RUN.equals(text(template.get("validationStatus")))
            && Boolean.FALSE.equals(template.get("probeBindingDigestVerified"))
            && Boolean.FALSE.equals(template.get("storageProbeResultBoundForValidation"))
            && Boolean.FALSE.equals(template.get("validationPassed"))
            && Boolean.FALSE.equals(template.get("releaseEligible"))
            && Boolean.FALSE.equals(template.get("writeExecutionAllowed"));
    }

    private static boolean enhancedReleaseDecisionContractValid(Map<String, Object> report,
                                                                Map<String, Object> releaseDecision) {
        Map<String, Object> template = objectMap(releaseDecision.get("currentTemplate"));
        return NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION.equals(
                text(releaseDecision.get("type")))
            && NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT.equals(
                text(releaseDecision.get("dependsOn")))
            && Boolean.TRUE.equals(releaseDecision.get("futureOnly"))
            && Boolean.FALSE.equals(releaseDecision.get("instanceAllowedNow"))
            && text(report.get("sourceProbeBindingPlanDigest")).equals(
                text(releaseDecision.get("sourceProbeBindingPlanDigest")))
            && text(report.get("sourceProbeResultContractDigest")).equals(
                text(releaseDecision.get("sourceProbeResultContractDigest")))
            && text(report.get("sourceMigrationPlanDigest")).equals(
                text(releaseDecision.get("sourceMigrationPlanDigest")))
            && text(report.get("sourceReceiptSchemaDigest")).equals(
                text(releaseDecision.get("sourceReceiptSchemaDigest")))
            && text(report.get("sourceValidationPlanDigest")).equals(
                text(releaseDecision.get("sourceValidationPlanDigest")))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindProbeResultBindingDigest"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindProbeResultContractDigest"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindValidationResultDigest"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindAuditEventDigest"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindTrustedPrincipalDigest"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBindCodeReleaseSwitch"))
            && Boolean.TRUE.equals(releaseDecision.get("mustBeServerIssued"))
            && "DENY_UNTIL_SERVER_VALIDATION_RESULT".equals(text(template.get("decision")))
            && Boolean.FALSE.equals(template.get("probeBindingDigestVerified"))
            && Boolean.FALSE.equals(template.get("releaseEligible"))
            && Boolean.FALSE.equals(template.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(template.get("releaseCredentialIssued"));
    }

    private static void validateCallerValidationEvidence(Map<String, Object> callerValidationEvidence,
                                                         List<Map<String, Object>> blockers) {
        if (!callerValidationEvidence.isEmpty()) {
            blockers.add(blocker(
                "CALLER_VALIDATION_EVIDENCE_NOT_AUTHORITATIVE",
                "调用方提供的 validation result、release decision、receipt 或 legacy release flag 无权参与 validation result 签发。",
                "caller-validation-evidence"
            ));
        }
        if (hasForgedValidationResultClaim(callerValidationEvidence)) {
            blockers.add(forgedClaimBlocker("callerValidationEvidence"));
        }
    }

    private static Map<String, Object> validationResultContract(Map<String, Object> auditContext,
                                                                Map<String, Object> principal,
                                                                Map<String, Object> migrationReport) {
        return validationResultContractForDigests(
            migrationReport,
            digestFor(auditContext),
            digestFor(principal),
            text(auditContext.get("organizationId")),
            text(auditContext.get("userId")),
            text(principal.get("username"))
        );
    }

    static Map<String, Object> validationResultContractFromReport(Map<String, Object> validationResultReport) {
        return validationResultContractForDigests(
            validationResultReport,
            text(validationResultReport.get("sourceAuditEventDigest")),
            text(validationResultReport.get("trustedPrincipalDigest")),
            text(validationResultReport.get("sourceOrganizationId")),
            text(validationResultReport.get("sourceUserId")),
            text(validationResultReport.get("sourceUsername"))
        );
    }

    private static Map<String, Object> validationResultContractForDigests(Map<String, Object> sourceDigests,
                                                                          String sourceAuditEventDigest,
                                                                          String trustedPrincipalDigest,
                                                                          String organizationId,
                                                                          String userId,
                                                                          String username) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contractBoundary", "SERVER_ISSUED_DURABLE_RECEIPT_VALIDATION_RESULT_REQUIRED");
        contract.put("type", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        contract.put("producedBy", NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR);
        contract.put("futureOnly", true);
        contract.put("instanceAllowedNow", false);
        contract.put("currentValidationStatus", VALIDATION_NOT_RUN);
        contract.put("requiredPassStatus", "PASS");
        contract.put("serverIssuedRequired", true);
        contract.put("callerProvidedValidationResultAllowed", false);
        putSourceDigests(contract, sourceDigests);
        contract.put("sourceAuditEventDigest", sourceAuditEventDigest);
        contract.put("trustedPrincipalDigest", trustedPrincipalDigest);
        contract.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        contract.put("trustedIdentityBinding", Map.of(
            "organizationId", organizationId,
            "userId", userId,
            "username", username,
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        contract.put("evidenceBinding", validationEvidenceBinding(sourceDigests, trustedPrincipalDigest));
        contract.put("requiredFutureEvidenceDigestFields", requiredFutureEvidenceDigestFields());
        contract.put("currentTemplate", currentValidationResultTemplate());
        contract.put("passPrerequisites", passPrerequisites());
        contract.put("failureContract", failureContract());
        contract.put("forbiddenShortcuts", forbiddenShortcuts());
        return contract;
    }

    private static void putSourceDigests(Map<String, Object> target, Map<String, Object> source) {
        target.put("sourceEnhancedMigrationPlanDigest",
            firstText(source.get("sourceEnhancedMigrationPlanDigest"), source.get("enhancedMigrationPlanDigest")));
        target.put("sourceProbeBindingPlanDigest", text(source.get("sourceProbeBindingPlanDigest")));
        target.put("sourceProbeResultContractDigest", text(source.get("sourceProbeResultContractDigest")));
        target.put("sourceProbeExecutorPlanDigest", text(source.get("sourceProbeExecutorPlanDigest")));
        target.put("sourceMigrationPlanDigest", text(source.get("sourceMigrationPlanDigest")));
        target.put("sourceReceiptSchemaDigest", text(source.get("sourceReceiptSchemaDigest")));
        target.put("sourceValidationPlanDigest", text(source.get("sourceValidationPlanDigest")));
        target.put("sourceInterfaceSpecDigest", text(source.get("sourceInterfaceSpecDigest")));
        target.put("sourceBoundaryPlanDigest", text(source.get("sourceBoundaryPlanDigest")));
        target.put("sourceWriterPlanDigest", text(source.get("sourceWriterPlanDigest")));
        target.put("sourceAvailabilityPlanDigest", text(source.get("sourceAvailabilityPlanDigest")));
    }

    private static Map<String, Object> validationEvidenceBinding(Map<String, Object> sourceDigests,
                                                                 String trustedPrincipalDigest) {
        Map<String, Object> binding = new LinkedHashMap<>();
        putSourceDigests(binding, sourceDigests);
        binding.put("trustedPrincipalDigest", trustedPrincipalDigest);
        binding.put("mustBindEnhancedMigrationDigest", true);
        binding.put("mustBindProbeResultBindingDigest", true);
        binding.put("mustBindProbeResultContractDigest", true);
        binding.put("mustBindStorageProbeReceiptDigest", true);
        binding.put("mustBindPreWriteDurableAckDigest", true);
        binding.put("mustBindPostWriteDurableAckDigest", true);
        binding.put("mustBindDurableReceiptDigest", true);
        binding.put("mustBindTrustedPrincipalDigest", true);
        binding.put("mustBeServerIssued", true);
        return binding;
    }

    private static String firstText(Object primary, Object fallback) {
        String value = text(primary);
        return value.isEmpty() ? text(fallback) : value;
    }

    private static List<String> requiredFutureEvidenceDigestFields() {
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

    private static Map<String, Object> currentValidationResultTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("validationStatus", VALIDATION_NOT_RUN);
        template.put("enhancedMigrationDigestVerified", false);
        template.put("probeBindingDigestVerified", false);
        template.put("probeResultContractDigestVerified", false);
        template.put("storageProbeReceiptValidated", false);
        template.put("preWriteDurableAckValidated", false);
        template.put("postWriteDurableAckValidated", false);
        template.put("durableReceiptValidated", false);
        template.put("validationPassed", false);
        template.put("releaseEligible", false);
        template.put("writeExecutionAllowed", false);
        return template;
    }

    private static Map<String, Object> passPrerequisites() {
        Map<String, Object> prerequisites = new LinkedHashMap<>();
        prerequisites.put("serverIssuedProbeBindingRequired", true);
        prerequisites.put("serverIssuedProbeResultContractRequired", true);
        prerequisites.put("storageProbeReceiptRequired", true);
        prerequisites.put("preWriteDurableAckRequired", true);
        prerequisites.put("postWriteDurableAckRequired", true);
        prerequisites.put("durableReceiptRequired", true);
        prerequisites.put("trustedPrincipalRequired", true);
        prerequisites.put("currentContractSatisfiesPrerequisites", false);
        return prerequisites;
    }

    private static Map<String, Object> failureContract() {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failClosed", true);
        failure.put("fallbackToMigrationPlanOnlyAllowed", false);
        failure.put("fallbackToProbeBindingPlanAllowed", false);
        failure.put("fallbackToSchemaOnlyAllowed", false);
        failure.put("fallbackToCallerValidationResultAllowed", false);
        failure.put("fallbackToLegacyAuditReceiptFlagAllowed", false);
        return failure;
    }

    private static List<String> forbiddenShortcuts() {
        return List.of(
            "accepting M5.21-58 migration report as validation PASS",
            "accepting M5.21-69 enhanced migration plan as validation PASS",
            "accepting caller-supplied validationResult or releaseDecision",
            "accepting legacy auditReceipt.releaseEligible=true as validation evidence",
            "allowing release decision before server-issued validation result digest exists"
        );
    }

    private static boolean hasOnlyExpectedMigrationHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_IMPLEMENTATION_HOLD".equals(
                text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static boolean hasForgedValidationResultClaim(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForgedValidationResultClaim(entry.getKey(), value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && hasForgedValidationResultClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem
                        && hasForgedValidationResultClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isForgedValidationResultClaim(String key, Object value) {
        return switch (key) {
            case "realValidationResultCreated",
                "serverIssuedValidationResultAccepted",
                "enhancedMigrationDigestVerified",
                "probeBindingDigestVerified",
                "probeResultContractDigestVerified",
                "probeBindingBoundToValidationResultMigration",
                "storageProbeResultBoundForValidation",
                "serverIssuedProbeResultAccepted",
                "storageProbeReceiptValidated",
                "preWriteDurableAckValidated",
                "postWriteDurableAckValidated",
                "digestChainValidated",
                "trustedPrincipalValidated",
                "durableReceiptValidated",
                "durableReceiptValidationPassed",
                "durableReceiptAccepted",
                "validationPassed",
                "validationResultAccepted",
                "releaseDecisionAccepted",
                "releaseCredentialIssued",
                "writeExecutionAllowed",
                "realHttpExecutionAllowed",
                "releaseEligible",
                "realStorageTouched",
                "durable" -> Boolean.TRUE.equals(value);
            case "validationStatus" -> Set.of("PASS", "VALIDATED").contains(text(value));
            case "decision" -> "ALLOW_WRITE_EXECUTION".equals(text(value));
            case "receiptStatus" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(value));
            case "storageMode" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(value));
            case "probeResult",
                "storageProbeResult",
                "validationResult",
                "releaseDecision",
                "auditReceipt",
                "legacyAuditReceipt" -> value != null;
            default -> false;
        };
    }

    private static Map<String, Object> forgedClaimBlocker(String source) {
        return blocker(
            "DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_FORGED_PASS_CLAIM",
            source + " 不得自称 validation result 已创建、PASS、release eligible 或 write execution 可放行。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> migrationReport,
                                                    Map<String, Object> callerValidationEvidence) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "probeResult",
            "storageProbeResult",
            "validationResult",
            "releaseDecision",
            "auditReceipt",
            "legacyAuditReceipt",
            "validationStatus",
            "validationPassed",
            "releaseDecisionAccepted",
            "releaseEligible",
            "writeExecutionAllowed",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key)
                || principal.containsKey(key)
                || migrationReport.containsKey(key)
                || callerValidationEvidence.containsKey(key)) {
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

    record ReceiptValidationResultInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> validationResultProbeBindingMigrationReport,
        Map<String, Object> callerValidationEvidence
    ) {
        ReceiptValidationResultInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            validationResultProbeBindingMigrationReport = validationResultProbeBindingMigrationReport == null
                ? Map.of()
                : objectMap(validationResultProbeBindingMigrationReport);
            callerValidationEvidence = callerValidationEvidence == null
                ? Map.of()
                : objectMap(callerValidationEvidence);
        }

        static ReceiptValidationResultInput empty() {
            return new ReceiptValidationResultInput(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
            );
        }
    }
}
