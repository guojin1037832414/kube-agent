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
 * NIM durable audit validation result / release decision 对 probe result binding 的迁移契约。
 *
 * <p>本类只定义未来 validation result / release decision 迁移必须如何消费 M5.21-68 的
 * probe-result-binding report。它不创建真实 validator、validation result、release decision、Spring Bean
 * 或写入凭证，也不访问 kube-manager / Elasticsearch / sys_log。</p>
 */
final class NimCreateDurableAuditValidationResultProbeBindingMigrationSupport {

    static final String PLAN_NAME =
        "NIM_CREATE_DURABLE_AUDIT_VALIDATION_RESULT_PROBE_BINDING_MIGRATION_PLAN";
    static final String EXECUTION_MODE =
        "DURABLE_AUDIT_VALIDATION_RESULT_PROBE_BINDING_MIGRATION_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String FUTURE_MIGRATION =
        "NimDurableAuditValidationResultProbeBindingMigration";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";
    private static final String RELEASE_DENIED = "DENY_UNTIL_SERVER_VALIDATION_RESULT";

    private NimCreateDurableAuditValidationResultProbeBindingMigrationSupport() {
    }

    static Map<String, Object> plan(ValidationResultProbeBindingMigrationInput input) {
        ValidationResultProbeBindingMigrationInput safeInput = input == null
            ? ValidationResultProbeBindingMigrationInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> probeBindingReport = safeInput.receiptValidationProbeResultBindingReport();
        Map<String, Object> migrationReport = safeInput.durableAuditValidationResultMigrationReport();
        Map<String, Object> callerReleaseEvidence = safeInput.callerReleaseEvidence();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateProbeBindingReport(auditContext, principal, probeBindingReport, blockers);
        validateMigrationReport(auditContext, principal, migrationReport, blockers);
        validateCrossBinding(probeBindingReport, migrationReport, blockers);
        validateCallerReleaseEvidence(callerReleaseEvidence, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("receiptValidationProbeResultBindingReport", probeBindingReport, blockers);
        validateNoSecretMaterial("durableAuditValidationResultMigrationReport", migrationReport, blockers);
        validateNoSecretMaterial("callerReleaseEvidence", callerReleaseEvidence, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> enhancedMigrationPlan = inputAccepted
            ? enhancedMigrationPlan(auditContext, principal, probeBindingReport, migrationReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_IMPLEMENTATION_HOLD",
                "validation result / release decision 对 probe-result-binding 的迁移契约已定义，但真实 result、decision 和 release gate 尚未实现；当前不能放行写执行。",
                "validation-result-probe-binding-migration"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditValidationResultProbeBindingMigrationPlan", PLAN_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("migrationState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureMigration", FUTURE_MIGRATION);
        result.put("futureValidationResult", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        result.put("futureReleaseDecision", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        result.put("futureProbeBinding",
            NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.FUTURE_BINDING);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("springBeanRegistered", false);
        result.put("httpClientBound", false);
        result.put("storageClientBound", false);
        result.put("inputAccepted", inputAccepted);
        result.put("enhancedMigrationPlanPrepared", inputAccepted);
        result.put("probeBindingRequiredBeforeValidationResult", true);
        result.put("legacyMigrationReportAloneAllowed", false);
        result.put("callerReleaseEvidenceAuthoritative", false);
        result.put("probeBindingBoundToValidationResultMigration", false);
        result.put("realValidatorCreated", false);
        result.put("realValidationResultCreated", false);
        result.put("realReleaseDecisionCreated", false);
        result.put("realStorageTouched", false);
        result.put("storageProbeResultBoundForValidation", false);
        result.put("serverIssuedProbeResultAccepted", false);
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
        result.put("realHttpExecutionAllowed", false);
        result.put("legacyAuditReceiptReleaseFlagTrusted", false);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("trustedPrincipalDigest", digestFor(principal));
        result.put("sourceProbeBindingPlanDigest", text(probeBindingReport.get("bindingPlanDigest")));
        result.put("sourceProbeResultContractDigest", text(probeBindingReport.get("sourceProbeResultContractDigest")));
        result.put("sourceProbeExecutorPlanDigest", text(probeBindingReport.get("sourceProbeExecutorPlanDigest")));
        result.put("sourceReceiptSchemaDigest", text(migrationReport.get("sourceReceiptSchemaDigest")));
        result.put("sourceValidationPlanDigest", text(migrationReport.get("sourceValidationPlanDigest")));
        result.put("sourceMigrationPlanDigest", text(migrationReport.get("migrationPlanDigest")));
        result.put("sourceInterfaceSpecDigest", text(migrationReport.get("sourceInterfaceSpecDigest")));
        result.put("sourceBoundaryPlanDigest", text(migrationReport.get("sourceBoundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(migrationReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(migrationReport.get("sourceAvailabilityPlanDigest")));
        result.put("enhancedMigrationPlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("enhancedMigrationPlanDigest", inputAccepted ? digestFor(enhancedMigrationPlan) : "");
        result.put("enhancedMigrationPlan", enhancedMigrationPlan);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(
            auditContext,
            principal,
            probeBindingReport,
            migrationReport,
            callerReleaseEvidence
        ));
        result.put("nextImplementationRequirements", List.of(
            "make validation result migration consume M5.21-68 bindingPlanDigest before any PASS result can exist",
            "bind future NimDurableAuditReceiptValidationResult to probe result contract and probe binding digests",
            "bind future NimDurableAuditReleaseDecision to the enhanced migration plan digest",
            "reject legacy M5.21-58 migration report alone as release evidence",
            "keep write execution held until binding, validation result, release decision and state-machine gate all pass review"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_VALIDATION_RESULT_PROBE_BINDING_MIGRATION",
                "validation result probe binding migration 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedMigrationClaim(auditContext)) {
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
                "validation result probe binding migration 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedMigrationClaim(principal)) {
            blockers.add(forgedClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateProbeBindingReport(Map<String, Object> auditContext,
                                                   Map<String, Object> principal,
                                                   Map<String, Object> probeBindingReport,
                                                   List<Map<String, Object>> blockers) {
        if (probeBindingReport.isEmpty()) {
            blockers.add(blocker(
                "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_REPORT_NOT_READY",
                "缺少 M5.21-68 probe result binding report；validation result migration 不能只依赖 M5.21-58 migration plan。",
                "receipt-validation-probe-result-binding"
            ));
            return;
        }

        Map<String, Object> bindingPlan = objectMap(probeBindingReport.get("bindingPlan"));
        boolean valid = NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.BINDING_NAME.equals(
                text(probeBindingReport.get("durableAuditReceiptValidationProbeResultBinding")))
            && NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.EXECUTION_MODE.equals(
                text(probeBindingReport.get("executionMode")))
            && NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.HOLD_STATE.equals(
                text(probeBindingReport.get("bindingState")))
            && NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.FUTURE_BINDING.equals(
                text(probeBindingReport.get("futureBinding")))
            && NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR.equals(
                text(probeBindingReport.get("futureValidator")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(probeBindingReport.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(probeBindingReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(probeBindingReport.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(probeBindingReport.get("networkAccess")))
            && "NONE".equals(text(probeBindingReport.get("sideEffect")))
            && Boolean.FALSE.equals(probeBindingReport.get("springBeanRegistered"))
            && Boolean.FALSE.equals(probeBindingReport.get("httpClientBound"))
            && Boolean.FALSE.equals(probeBindingReport.get("storageClientBound"))
            && Boolean.TRUE.equals(probeBindingReport.get("inputAccepted"))
            && Boolean.TRUE.equals(probeBindingReport.get("bindingPlanPrepared"))
            && Boolean.TRUE.equals(probeBindingReport.get("probeResultRequiredBeforeReceiptValidation"))
            && Boolean.FALSE.equals(probeBindingReport.get("schemaOnlyValidationAllowed"))
            && Boolean.FALSE.equals(probeBindingReport.get("callerEvidenceAuthoritative"))
            && Boolean.FALSE.equals(probeBindingReport.get("storageProbeResultBoundForValidation"))
            && Boolean.FALSE.equals(probeBindingReport.get("serverIssuedProbeResultAccepted"))
            && Boolean.FALSE.equals(probeBindingReport.get("validationCanRunNow"))
            && Boolean.FALSE.equals(probeBindingReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(probeBindingReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(probeBindingReport.get("storageAvailable"))
            && Boolean.FALSE.equals(probeBindingReport.get("storageProbeReceiptValidated"))
            && Boolean.FALSE.equals(probeBindingReport.get("preWriteDurableAckValidated"))
            && Boolean.FALSE.equals(probeBindingReport.get("postWriteDurableAckValidated"))
            && Boolean.FALSE.equals(probeBindingReport.get("digestChainValidated"))
            && Boolean.FALSE.equals(probeBindingReport.get("trustedPrincipalValidated"))
            && Boolean.FALSE.equals(probeBindingReport.get("durableReceiptValidated"))
            && Boolean.FALSE.equals(probeBindingReport.get("durableReceiptValidationPassed"))
            && Boolean.FALSE.equals(probeBindingReport.get("releaseEligible"))
            && Boolean.FALSE.equals(probeBindingReport.get("writeExecutionAllowed"))
            && text(probeBindingReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(probeBindingReport.get("trustedPrincipalDigest")).equals(digestFor(principal))
            && text(probeBindingReport.get("sourceProbeResultContractDigest")).matches("[a-f0-9]{64}")
            && text(probeBindingReport.get("sourceProbeExecutorPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeBindingReport.get("sourceReceiptSchemaDigest")).matches("[a-f0-9]{64}")
            && text(probeBindingReport.get("sourceValidationPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeBindingReport.get("sourceInterfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(probeBindingReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeBindingReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeBindingReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(
                text(probeBindingReport.get("bindingPlanDigestAlgorithm")))
            && text(probeBindingReport.get("bindingPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeBindingReport.get("bindingPlanDigest")).equals(digestFor(bindingPlan))
            && hasOnlyExpectedProbeBindingHold(probeBindingReport.get("blockedBy"))
            && bindingPlanContractValid(auditContext, principal, probeBindingReport, bindingPlan);

        if (!valid) {
            blockers.add(blocker(
                "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_REPORT_INVALID_FOR_MIGRATION",
                "validation result probe binding migration 只能消费 M5.21-68 产生的、仍为 HOLD 且未声明真实 validation pass 的 binding report。",
                "receipt-validation-probe-result-binding"
            ));
        }
        if (hasForgedMigrationClaim(probeBindingReport)) {
            blockers.add(forgedClaimBlocker("receiptValidationProbeResultBindingReport"));
        }
    }

    private static boolean bindingPlanContractValid(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> report,
                                                    Map<String, Object> bindingPlan) {
        return !bindingPlan.isEmpty()
            && bindingPlanSourceDigestsMatch(report, bindingPlan)
            && digestFor(auditContext).equals(text(bindingPlan.get("sourceAuditEventDigest")))
            && text(auditContext.get("organizationId")).equals(text(report.get("sourceOrganizationId")))
            && text(auditContext.get("userId")).equals(text(report.get("sourceUserId")))
            && text(principal.get("username")).equals(text(report.get("sourceUsername")))
            && bindingPlan.equals(
                NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.bindingPlanFromReport(report));
    }

    private static boolean bindingPlanSourceDigestsMatch(Map<String, Object> report,
                                                         Map<String, Object> bindingPlan) {
        for (String field : List.of(
            "sourceProbeResultContractDigest",
            "sourceProbeExecutorPlanDigest",
            "sourceReceiptSchemaDigest",
            "sourceValidationPlanDigest",
            "sourceInterfaceSpecDigest",
            "sourceBoundaryPlanDigest",
            "sourceWriterPlanDigest",
            "sourceAvailabilityPlanDigest"
        )) {
            if (!text(report.get(field)).equals(text(bindingPlan.get(field)))) {
                return false;
            }
        }
        return true;
    }

    private static void validateMigrationReport(Map<String, Object> auditContext,
                                                Map<String, Object> principal,
                                                Map<String, Object> migrationReport,
                                                List<Map<String, Object>> blockers) {
        if (migrationReport.isEmpty()) {
            blockers.add(blocker(
                "VALIDATION_RESULT_MIGRATION_REPORT_NOT_READY",
                "缺少 M5.21-58 validation result migration report；不能定义 probe-binding-aware migration。",
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
            && text(auditContext.get("organizationId")).equals(text(migrationReport.get("sourceOrganizationId")))
            && text(auditContext.get("userId")).equals(text(migrationReport.get("sourceUserId")))
            && text(principal.get("username")).equals(text(migrationReport.get("sourceUsername")))
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
                "VALIDATION_RESULT_MIGRATION_REPORT_INVALID_FOR_PROBE_BINDING_MIGRATION",
                "probe-binding-aware migration 只能消费 M5.21-58 产生的、仍为 HOLD 且未声明真实 validation result / release decision 的 migration report。",
                "durable-audit-validation-result-migration"
            ));
        }
        if (hasForgedMigrationClaim(migrationReport)) {
            blockers.add(forgedClaimBlocker("durableAuditValidationResultMigrationReport"));
        }
    }

    private static boolean migrationPlanContractValid(Map<String, Object> auditContext,
                                                      Map<String, Object> principal,
                                                      Map<String, Object> migrationReport,
                                                      Map<String, Object> migrationPlan) {
        return !migrationPlan.isEmpty()
            && migrationPlanSourceDigestsMatch(migrationReport, migrationPlan)
            && digestFor(auditContext).equals(text(migrationPlan.get("sourceAuditEventDigest")))
            && sourceIdentityMatchesMigrationPlan(auditContext, principal, migrationReport, migrationPlan)
            && migrationPlan.equals(
                NimCreateDurableAuditValidationResultMigrationSupport.migrationPlanFromReport(migrationReport));
    }

    private static boolean migrationPlanSourceDigestsMatch(Map<String, Object> migrationReport,
                                                           Map<String, Object> migrationPlan) {
        for (String field : List.of(
            "sourceReceiptSchemaDigest",
            "sourceValidationPlanDigest",
            "sourceInterfaceSpecDigest",
            "sourceBoundaryPlanDigest",
            "sourceWriterPlanDigest",
            "sourceAvailabilityPlanDigest"
        )) {
            if (!text(migrationReport.get(field)).equals(text(migrationPlan.get(field)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sourceIdentityMatchesMigrationPlan(Map<String, Object> auditContext,
                                                              Map<String, Object> principal,
                                                              Map<String, Object> migrationReport,
                                                              Map<String, Object> migrationPlan) {
        Map<String, Object> identity = objectMap(migrationPlan.get("trustedIdentityBinding"));
        return text(auditContext.get("organizationId")).equals(text(migrationReport.get("sourceOrganizationId")))
            && text(auditContext.get("userId")).equals(text(migrationReport.get("sourceUserId")))
            && text(principal.get("username")).equals(text(migrationReport.get("sourceUsername")))
            && text(migrationReport.get("sourceOrganizationId")).equals(text(identity.get("organizationId")))
            && text(migrationReport.get("sourceUserId")).equals(text(identity.get("userId")))
            && text(migrationReport.get("sourceUsername")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"));
    }

    private static void validateCrossBinding(Map<String, Object> probeBindingReport,
                                             Map<String, Object> migrationReport,
                                             List<Map<String, Object>> blockers) {
        if (probeBindingReport.isEmpty() || migrationReport.isEmpty()) {
            return;
        }
        boolean valid = text(probeBindingReport.get("sourceAuditEventDigest")).equals(
                text(migrationReport.get("sourceAuditEventDigest")))
            && text(probeBindingReport.get("sourceReceiptSchemaDigest")).equals(
                text(migrationReport.get("sourceReceiptSchemaDigest")))
            && text(probeBindingReport.get("sourceValidationPlanDigest")).equals(
                text(migrationReport.get("sourceValidationPlanDigest")))
            && text(probeBindingReport.get("sourceInterfaceSpecDigest")).equals(
                text(migrationReport.get("sourceInterfaceSpecDigest")))
            && text(probeBindingReport.get("sourceBoundaryPlanDigest")).equals(
                text(migrationReport.get("sourceBoundaryPlanDigest")))
            && text(probeBindingReport.get("sourceWriterPlanDigest")).equals(
                text(migrationReport.get("sourceWriterPlanDigest")))
            && text(probeBindingReport.get("sourceAvailabilityPlanDigest")).equals(
                text(migrationReport.get("sourceAvailabilityPlanDigest")));
        if (!valid) {
            blockers.add(blocker(
                "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_DIGEST_CHAIN_MISMATCH",
                "M5.21-68 probe binding report 与 M5.21-58 migration report 必须绑定同一 audit event、schema、validation plan、writer plan、availability plan 和 writer boundary。",
                "upstream-digest-chain"
            ));
        }
    }

    private static void validateCallerReleaseEvidence(Map<String, Object> callerReleaseEvidence,
                                                      List<Map<String, Object>> blockers) {
        if (!callerReleaseEvidence.isEmpty()) {
            blockers.add(blocker(
                "CALLER_RELEASE_EVIDENCE_NOT_AUTHORITATIVE_FOR_PROBE_BINDING_MIGRATION",
                "调用方提供的 validation result、release decision、probe result 或 legacy audit receipt 无权参与 probe-binding-aware migration。",
                "caller-release-evidence"
            ));
        }
        if (hasForgedMigrationClaim(callerReleaseEvidence)) {
            blockers.add(forgedClaimBlocker("callerReleaseEvidence"));
        }
    }

    private static Map<String, Object> enhancedMigrationPlan(Map<String, Object> auditContext,
                                                             Map<String, Object> principal,
                                                             Map<String, Object> probeBindingReport,
                                                             Map<String, Object> migrationReport) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("migrationBoundary",
            "SERVER_SIDE_VALIDATION_RESULT_AND_RELEASE_DECISION_REQUIRE_PROBE_BINDING");
        plan.put("futureMigration", FUTURE_MIGRATION);
        plan.put("futureProbeBinding",
            NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.FUTURE_BINDING);
        plan.put("futureValidationResult",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        plan.put("futureReleaseDecision",
            NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        plan.put("sourceProbeBindingPlanDigest", text(probeBindingReport.get("bindingPlanDigest")));
        plan.put("sourceProbeResultContractDigest", text(probeBindingReport.get("sourceProbeResultContractDigest")));
        plan.put("sourceProbeExecutorPlanDigest", text(probeBindingReport.get("sourceProbeExecutorPlanDigest")));
        plan.put("sourceReceiptSchemaDigest", text(migrationReport.get("sourceReceiptSchemaDigest")));
        plan.put("sourceValidationPlanDigest", text(migrationReport.get("sourceValidationPlanDigest")));
        plan.put("sourceMigrationPlanDigest", text(migrationReport.get("migrationPlanDigest")));
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
        plan.put("probeBindingRequirement", probeBindingRequirement(probeBindingReport));
        plan.put("enhancedValidationResultContract",
            enhancedValidationResultContract(probeBindingReport, migrationReport));
        plan.put("enhancedReleaseDecisionContract",
            enhancedReleaseDecisionContract(probeBindingReport, migrationReport));
        plan.put("migrationSequencePatch", migrationSequencePatch());
        plan.put("currentDecisionTemplate", currentDecisionTemplate());
        plan.put("failureContract", enhancedFailureContract());
        plan.put("forbiddenShortcuts", forbiddenShortcuts());
        return plan;
    }

    private static Map<String, Object> probeBindingRequirement(Map<String, Object> probeBindingReport) {
        Map<String, Object> requirement = new LinkedHashMap<>();
        requirement.put("requiredReportName",
            NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.BINDING_NAME);
        requirement.put("requiredStateNow", HOLD_STATE);
        requirement.put("sourceProbeBindingPlanDigest", text(probeBindingReport.get("bindingPlanDigest")));
        requirement.put("sourceProbeResultContractDigest",
            text(probeBindingReport.get("sourceProbeResultContractDigest")));
        requirement.put("mustBindProbeResultBindingDigest", true);
        requirement.put("probeBindingReportCanPassNow", false);
        requirement.put("fallbackToValidationGateOnlyAllowed", false);
        requirement.put("fallbackToMigrationPlanOnlyAllowed", false);
        return requirement;
    }

    private static Map<String, Object> enhancedValidationResultContract(Map<String, Object> probeBindingReport,
                                                                        Map<String, Object> migrationReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("type", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        contract.put("producedBy", NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR);
        contract.put("futureOnly", true);
        contract.put("instanceAllowedNow", false);
        contract.put("currentValidationStatus", VALIDATION_NOT_RUN);
        contract.put("requiredPassStatus", "PASS");
        contract.put("sourceProbeBindingPlanDigest", text(probeBindingReport.get("bindingPlanDigest")));
        contract.put("sourceProbeResultContractDigest",
            text(probeBindingReport.get("sourceProbeResultContractDigest")));
        contract.put("sourceMigrationPlanDigest", text(migrationReport.get("migrationPlanDigest")));
        contract.put("sourceReceiptSchemaDigest", text(migrationReport.get("sourceReceiptSchemaDigest")));
        contract.put("sourceValidationPlanDigest", text(migrationReport.get("sourceValidationPlanDigest")));
        contract.put("mustBindProbeResultBindingDigest", true);
        contract.put("mustBindProbeResultContractDigest", true);
        contract.put("mustBindStorageProbeReceiptDigest", true);
        contract.put("mustBindPreWriteDurableAckDigest", true);
        contract.put("mustBindPostWriteDurableAckDigest", true);
        contract.put("mustBindDurableReceiptDigest", true);
        contract.put("mustBindTrustedPrincipalDigest", true);
        contract.put("mustBeServerIssued", true);
        contract.put("currentTemplate", currentValidationResultTemplate());
        return contract;
    }

    private static Map<String, Object> currentValidationResultTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("validationStatus", VALIDATION_NOT_RUN);
        template.put("probeBindingDigestVerified", false);
        template.put("storageProbeResultBoundForValidation", false);
        template.put("validationPassed", false);
        template.put("releaseEligible", false);
        template.put("writeExecutionAllowed", false);
        return template;
    }

    private static Map<String, Object> enhancedReleaseDecisionContract(Map<String, Object> probeBindingReport,
                                                                       Map<String, Object> migrationReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("type", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_RELEASE_DECISION);
        contract.put("dependsOn", NimCreateDurableAuditValidationResultMigrationSupport.FUTURE_VALIDATION_RESULT);
        contract.put("futureOnly", true);
        contract.put("instanceAllowedNow", false);
        contract.put("currentDecision", RELEASE_DENIED);
        contract.put("requiredAllowDecision", "ALLOW_WRITE_EXECUTION");
        contract.put("sourceProbeBindingPlanDigest", text(probeBindingReport.get("bindingPlanDigest")));
        contract.put("sourceProbeResultContractDigest",
            text(probeBindingReport.get("sourceProbeResultContractDigest")));
        contract.put("sourceMigrationPlanDigest", text(migrationReport.get("migrationPlanDigest")));
        contract.put("sourceReceiptSchemaDigest", text(migrationReport.get("sourceReceiptSchemaDigest")));
        contract.put("sourceValidationPlanDigest", text(migrationReport.get("sourceValidationPlanDigest")));
        contract.put("mustBindProbeResultBindingDigest", true);
        contract.put("mustBindProbeResultContractDigest", true);
        contract.put("mustBindValidationResultDigest", true);
        contract.put("mustBindAuditEventDigest", true);
        contract.put("mustBindTrustedPrincipalDigest", true);
        contract.put("mustBindCodeReleaseSwitch", true);
        contract.put("mustBeServerIssued", true);
        contract.put("currentTemplate", currentReleaseDecisionTemplate());
        return contract;
    }

    private static Map<String, Object> currentReleaseDecisionTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("decision", RELEASE_DENIED);
        template.put("probeBindingDigestVerified", false);
        template.put("validationStatus", VALIDATION_NOT_RUN);
        template.put("releaseEligible", false);
        template.put("writeExecutionAllowed", false);
        template.put("releaseCredentialIssued", false);
        template.put("fallbackToMigrationPlanOnlyAllowed", false);
        return template;
    }

    private static List<Map<String, Object>> migrationSequencePatch() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(migrationStep(
            "bind-probe-result-binding-plan",
            "Require M5.21-68 bindingPlanDigest before constructing any future validation result"
        ));
        steps.add(migrationStep(
            "reject-validation-plan-only-migration",
            "Reject M5.21-58 migration report when no probe-result-binding report is bound"
        ));
        steps.add(migrationStep(
            "bind-release-decision-to-enhanced-migration",
            "Require future release decision to bind validation result and probe binding digests"
        ));
        return steps;
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

    private static Map<String, Object> currentDecisionTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("probeBindingBoundToValidationResultMigration", false);
        template.put("realValidationResultCreated", false);
        template.put("realReleaseDecisionCreated", false);
        template.put("validationStatus", VALIDATION_NOT_RUN);
        template.put("releaseEligible", false);
        template.put("writeExecutionAllowed", false);
        template.put("releaseCredentialIssued", false);
        return template;
    }

    private static Map<String, Object> enhancedFailureContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("failClosed", true);
        contract.put("fallbackToValidationGateOnlyAllowed", false);
        contract.put("fallbackToSchemaOnlyAllowed", false);
        contract.put("fallbackToMigrationPlanOnlyAllowed", false);
        contract.put("fallbackToCallerReleaseEvidenceAllowed", false);
        contract.put("fallbackToLegacyAuditReceiptFlagAllowed", false);
        contract.put("failureStatuses", List.of(
            "IMPLEMENTATION_HOLD",
            "PROBE_RESULT_BINDING_REPORT_NOT_READY",
            "PROBE_RESULT_BINDING_DIGEST_MISMATCH",
            "VALIDATION_RESULT_MIGRATION_REPORT_NOT_READY",
            "MIGRATION_REPORT_DIGEST_MISMATCH",
            "UPSTREAM_DIGEST_CHAIN_MISMATCH",
            "CALLER_RELEASE_EVIDENCE_REJECTED",
            "SECRET_MATERIAL_REJECTED"
        ));
        return contract;
    }

    private static List<String> forbiddenShortcuts() {
        return List.of(
            "accepting M5.21-58 migration report without M5.21-68 bindingPlanDigest",
            "accepting validationPlanDigest without probe-result-binding digest",
            "accepting bindingPlan as a validation pass",
            "accepting caller-supplied validationResult or releaseDecision",
            "accepting legacy auditReceipt.releaseEligible=true as release credential",
            "allowing write execution before enhanced migration receives a server-issued release decision"
        );
    }

    private static boolean hasOnlyExpectedProbeBindingHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_IMPLEMENTATION_HOLD".equals(
                text(blockers.get(0).get("code")));
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
                "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static boolean hasForgedMigrationClaim(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForgedMigrationClaim(entry.getKey(), value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && hasForgedMigrationClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && hasForgedMigrationClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isForgedMigrationClaim(String key, Object value) {
        return switch (key) {
            case "probeBindingBoundToValidationResultMigration",
                "storageProbeResultBoundForValidation",
                "serverIssuedProbeResultAccepted",
                "validationCanRunNow",
                "realValidationResultCreated",
                "realReleaseDecisionCreated",
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
                "durableReceiptCanBeIssued",
                "durableReceiptIssued",
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
            "VALIDATION_RESULT_PROBE_BINDING_MIGRATION_FORGED_RELEASE_CLAIM",
            source + " 不得自称 probe binding 已通过、validation result 已创建、release decision 已接受或写执行可放行。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> probeBindingReport,
                                                    Map<String, Object> migrationReport,
                                                    Map<String, Object> callerReleaseEvidence) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "probeResult",
            "storageProbeResult",
            "validationResult",
            "releaseDecision",
            "auditReceipt",
            "legacyAuditReceipt",
            "probeBindingBoundToValidationResultMigration",
            "storageProbeResultBoundForValidation",
            "validationStatus",
            "validationPassed",
            "releaseDecisionAccepted",
            "releaseCredentialIssued",
            "releaseEligible",
            "writeExecutionAllowed",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key)
                || principal.containsKey(key)
                || probeBindingReport.containsKey(key)
                || migrationReport.containsKey(key)
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

    record ValidationResultProbeBindingMigrationInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> receiptValidationProbeResultBindingReport,
        Map<String, Object> durableAuditValidationResultMigrationReport,
        Map<String, Object> callerReleaseEvidence
    ) {
        ValidationResultProbeBindingMigrationInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            receiptValidationProbeResultBindingReport = receiptValidationProbeResultBindingReport == null
                ? Map.of()
                : objectMap(receiptValidationProbeResultBindingReport);
            durableAuditValidationResultMigrationReport = durableAuditValidationResultMigrationReport == null
                ? Map.of()
                : objectMap(durableAuditValidationResultMigrationReport);
            callerReleaseEvidence = callerReleaseEvidence == null ? Map.of() : objectMap(callerReleaseEvidence);
        }

        static ValidationResultProbeBindingMigrationInput empty() {
            return new ValidationResultProbeBindingMigrationInput(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
            );
        }
    }
}
