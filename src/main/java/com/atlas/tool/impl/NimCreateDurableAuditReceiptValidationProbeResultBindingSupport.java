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
 * NIM durable audit receipt validation 对 storage probe result 的绑定契约。
 *
 * <p>本类只定义未来 {@code NimDurableAuditReceiptValidator} 在校验 typed receipt 之前，必须如何消费
 * M5.21-67 的 server-issued storage probe result contract。当前不创建真实 validator，不验证真实 receipt，
 * 不访问 kube-manager，不连接 Elasticsearch，也不写 {@code sys_log}。</p>
 */
final class NimCreateDurableAuditReceiptValidationProbeResultBindingSupport {

    static final String BINDING_NAME =
        "NIM_CREATE_DURABLE_AUDIT_RECEIPT_VALIDATION_PROBE_RESULT_BINDING";
    static final String EXECUTION_MODE =
        "DURABLE_AUDIT_RECEIPT_VALIDATION_PROBE_RESULT_BINDING_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String FUTURE_BINDING =
        "NimDurableAuditReceiptValidationProbeResultBinding";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";

    private NimCreateDurableAuditReceiptValidationProbeResultBindingSupport() {
    }

    static Map<String, Object> plan(ReceiptValidationProbeResultBindingInput input) {
        ReceiptValidationProbeResultBindingInput safeInput = input == null
            ? ReceiptValidationProbeResultBindingInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> probeResultReport = safeInput.durableAuditStorageProbeResultReport();
        Map<String, Object> validationGateReport = safeInput.durableAuditReceiptValidationGateReport();
        Map<String, Object> callerEvidence = safeInput.callerReceiptEvidence();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateProbeResultReport(auditContext, principal, probeResultReport, blockers);
        validateValidationGateReport(auditContext, principal, validationGateReport, blockers);
        validateCrossBinding(probeResultReport, validationGateReport, blockers);
        validateCallerEvidence(callerEvidence, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditStorageProbeResultReport", probeResultReport, blockers);
        validateNoSecretMaterial("durableAuditReceiptValidationGateReport", validationGateReport, blockers);
        validateNoSecretMaterial("callerReceiptEvidence", callerEvidence, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> bindingPlan = inputAccepted
            ? bindingPlan(auditContext, principal, probeResultReport, validationGateReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_IMPLEMENTATION_HOLD",
                "receipt validation 与 storage probe result 的绑定迁移契约已定义，但真实 validator 和 server-issued probe result 尚未实现；当前不能验证 receipt 或放行写执行。",
                "receipt-validation-probe-result-binding"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditReceiptValidationProbeResultBinding", BINDING_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("bindingState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureBinding", FUTURE_BINDING);
        result.put("futureValidator", NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR);
        result.put("futureProbeResultType", NimCreateDurableAuditStorageProbeResultSupport.FUTURE_RESULT_TYPE);
        result.put("futureProbeReceiptType", NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("springBeanRegistered", false);
        result.put("httpClientBound", false);
        result.put("storageClientBound", false);
        result.put("inputAccepted", inputAccepted);
        result.put("bindingPlanPrepared", inputAccepted);
        result.put("probeResultRequiredBeforeReceiptValidation", true);
        result.put("schemaOnlyValidationAllowed", false);
        result.put("callerEvidenceAuthoritative", false);
        result.put("storageProbeResultBoundForValidation", false);
        result.put("serverIssuedProbeResultAccepted", false);
        result.put("validationCanRunNow", false);
        result.put("storageProbeExecuted", false);
        result.put("realStorageTouched", false);
        result.put("storageAvailable", false);
        result.put("storageProbeReceiptIssued", false);
        result.put("storageProbeReceiptValidated", false);
        result.put("preWriteDurableAckValidated", false);
        result.put("postWriteDurableAckValidated", false);
        result.put("digestChainValidated", false);
        result.put("trustedPrincipalValidated", false);
        result.put("durableReceiptValidated", false);
        result.put("durableReceiptValidationPassed", false);
        result.put("durableReceiptAccepted", false);
        result.put("validationStatus", VALIDATION_NOT_RUN);
        result.put("preWriteAllowed", false);
        result.put("writePermitted", false);
        result.put("writeExecutionAllowed", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("sourceOrganizationId", text(auditContext.get("organizationId")));
        result.put("sourceUserId", text(auditContext.get("userId")));
        result.put("sourceUsername", text(principal.get("username")));
        result.put("durableReceiptCanBeIssued", false);
        result.put("durableReceiptIssued", false);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("trustedPrincipalDigest", digestFor(principal));
        result.put("sourceProbeResultContractDigest", text(probeResultReport.get("probeResultContractDigest")));
        result.put("sourceProbeExecutorPlanDigest", text(probeResultReport.get("sourceProbeExecutorPlanDigest")));
        result.put("sourceReceiptSchemaDigest", text(probeResultReport.get("sourceReceiptSchemaDigest")));
        result.put("sourceValidationPlanDigest", text(validationGateReport.get("validationPlanDigest")));
        result.put("sourceInterfaceSpecDigest", text(probeResultReport.get("sourceInterfaceSpecDigest")));
        result.put("sourceBoundaryPlanDigest", text(probeResultReport.get("sourceBoundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(probeResultReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(probeResultReport.get("sourceAvailabilityPlanDigest")));
        result.put("bindingPlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("bindingPlanDigest", inputAccepted ? digestFor(bindingPlan) : "");
        result.put("bindingPlan", bindingPlan);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(
            auditContext,
            principal,
            probeResultReport,
            validationGateReport,
            callerEvidence
        ));
        result.put("nextImplementationRequirements", List.of(
            "make the future receipt validator consume a reviewed server-issued NimDurableAuditStorageProbeResult",
            "bind storage probe result contract digest before validating StorageAvailabilityProbeReceipt",
            "reject schema-only validation plans and caller supplied probe result or receipt evidence",
            "recompute audit event, principal, probe result, schema and validation plan digests before any pass",
            "keep write execution held until real probe result, typed ack, final receipt and release decision all pass review"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_PROBE_RESULT_VALIDATION_BINDING",
                "probe result validation binding 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedBindingClaim(auditContext)) {
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
                "probe result validation binding 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedBindingClaim(principal)) {
            blockers.add(forgedClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateProbeResultReport(Map<String, Object> auditContext,
                                                  Map<String, Object> principal,
                                                  Map<String, Object> probeResultReport,
                                                  List<Map<String, Object>> blockers) {
        if (probeResultReport.isEmpty()) {
            blockers.add(blocker(
                "STORAGE_PROBE_RESULT_REPORT_NOT_READY",
                "缺少 M5.21-67 storage probe result contract report；receipt validation 不能只依赖 schema 或 validation gate plan。",
                "storage-probe-result"
            ));
            return;
        }

        Map<String, Object> contract = objectMap(probeResultReport.get("probeResultContract"));
        boolean valid = NimCreateDurableAuditStorageProbeResultSupport.RESULT_CONTRACT_NAME.equals(
                text(probeResultReport.get("durableAuditStorageProbeResultContract")))
            && NimCreateDurableAuditStorageProbeResultSupport.EXECUTION_MODE.equals(
                text(probeResultReport.get("executionMode")))
            && NimCreateDurableAuditStorageProbeResultSupport.HOLD_STATE.equals(
                text(probeResultReport.get("probeResultState")))
            && NimCreateDurableAuditStorageProbeResultSupport.FUTURE_RESULT_TYPE.equals(
                text(probeResultReport.get("futureResultType")))
            && NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE.equals(
                text(probeResultReport.get("futureProbeReceiptType")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(probeResultReport.get("targetTool")))
            && "NOT_PERFORMED".equals(text(probeResultReport.get("networkAccess")))
            && "NONE".equals(text(probeResultReport.get("sideEffect")))
            && Boolean.FALSE.equals(probeResultReport.get("springBeanRegistered"))
            && Boolean.FALSE.equals(probeResultReport.get("httpClientBound"))
            && Boolean.FALSE.equals(probeResultReport.get("storageClientBound"))
            && Boolean.TRUE.equals(probeResultReport.get("inputAccepted"))
            && Boolean.TRUE.equals(probeResultReport.get("probeResultContractPrepared"))
            && Boolean.FALSE.equals(probeResultReport.get("resultIssued"))
            && Boolean.FALSE.equals(probeResultReport.get("serverIssuedProbeResultAccepted"))
            && Boolean.FALSE.equals(probeResultReport.get("callerProbeResultAuthoritative"))
            && Boolean.FALSE.equals(probeResultReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(probeResultReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(probeResultReport.get("storageAvailable"))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN.equals(
                text(probeResultReport.get("availabilityStatus")))
            && NimCreateDurableAuditStorageProbeResultSupport.CURRENT_STATUS.equals(
                text(probeResultReport.get("probeStatus")))
            && Boolean.FALSE.equals(probeResultReport.get("durableAckVerified"))
            && Boolean.FALSE.equals(probeResultReport.get("readAfterWriteVerified"))
            && Boolean.FALSE.equals(probeResultReport.get("storageProbeReceiptIssued"))
            && Boolean.FALSE.equals(probeResultReport.get("preWriteAllowed"))
            && Boolean.FALSE.equals(probeResultReport.get("writePermitted"))
            && Boolean.FALSE.equals(probeResultReport.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(probeResultReport.get("realHttpExecutionAllowed"))
            && Boolean.FALSE.equals(probeResultReport.get("durable"))
            && Boolean.FALSE.equals(probeResultReport.get("releaseEligible"))
            && Boolean.FALSE.equals(probeResultReport.get("durableReceiptCanBeIssued"))
            && Boolean.FALSE.equals(probeResultReport.get("durableReceiptIssued"))
            && text(probeResultReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(probeResultReport.get("trustedPrincipalDigest")).equals(digestFor(principal))
            && text(auditContext.get("organizationId")).equals(text(probeResultReport.get("sourceOrganizationId")))
            && text(auditContext.get("userId")).equals(text(probeResultReport.get("sourceUserId")))
            && text(principal.get("username")).equals(text(probeResultReport.get("sourceUsername")))
            && text(probeResultReport.get("sourceProbeExecutorPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeResultReport.get("sourceReceiptSchemaDigest")).matches("[a-f0-9]{64}")
            && text(probeResultReport.get("sourceInterfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(probeResultReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeResultReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeResultReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(
                text(probeResultReport.get("probeResultContractDigestAlgorithm")))
            && text(probeResultReport.get("probeResultContractDigest")).matches("[a-f0-9]{64}")
            && text(probeResultReport.get("probeResultContractDigest")).equals(digestFor(contract))
            && hasOnlyExpectedProbeResultHold(probeResultReport.get("blockedBy"))
            && probeResultContractValid(auditContext, principal, probeResultReport, contract);

        if (!valid) {
            blockers.add(blocker(
                "STORAGE_PROBE_RESULT_REPORT_INVALID_FOR_RECEIPT_VALIDATION_BINDING",
                "probe result validation binding 只能消费 M5.21-67 产生的、仍为 HOLD 且未签发真实 probe result 的 contract report。",
                "storage-probe-result"
            ));
        }
        if (hasForgedBindingClaim(probeResultReport)) {
            blockers.add(forgedClaimBlocker("durableAuditStorageProbeResultReport"));
        }
    }

    private static boolean probeResultContractValid(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> probeResultReport,
                                                    Map<String, Object> contract) {
        return !contract.isEmpty()
            && probeResultContractSourceDigestsMatch(probeResultReport, contract)
            && digestFor(auditContext).equals(text(objectMap(contract.get("evidenceBinding"))
                .get("sourceAuditEventDigest")))
            && text(auditContext.get("organizationId")).equals(text(probeResultReport.get("sourceOrganizationId")))
            && text(auditContext.get("userId")).equals(text(probeResultReport.get("sourceUserId")))
            && text(principal.get("username")).equals(text(probeResultReport.get("sourceUsername")))
            && contract.equals(
                NimCreateDurableAuditStorageProbeResultSupport.probeResultContractFromReport(probeResultReport));
    }

    private static boolean probeResultContractSourceDigestsMatch(Map<String, Object> probeResultReport,
                                                                 Map<String, Object> contract) {
        Map<String, Object> evidence = objectMap(contract.get("evidenceBinding"));
        for (String field : List.of(
            "sourceProbeExecutorPlanDigest",
            "sourceReceiptSchemaDigest",
            "sourceInterfaceSpecDigest",
            "sourceBoundaryPlanDigest",
            "sourceWriterPlanDigest",
            "sourceAvailabilityPlanDigest"
        )) {
            if (!text(probeResultReport.get(field)).equals(text(evidence.get(field)))) {
                return false;
            }
        }
        return true;
    }

    private static void validateValidationGateReport(Map<String, Object> auditContext,
                                                     Map<String, Object> principal,
                                                     Map<String, Object> validationGateReport,
                                                     List<Map<String, Object>> blockers) {
        if (validationGateReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_NOT_READY",
                "缺少 M5.21-57 receipt validation gate report；不能定义 probe result 与 receipt validation 的绑定迁移。",
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
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(validationGateReport.get("backendEndpoint")))
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
                "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_INVALID_FOR_PROBE_RESULT_BINDING",
                "probe result validation binding 只能消费 M5.21-57 产生的、仍为 HOLD 且未声明真实 validation pass 的 gate report。",
                "durable-audit-receipt-validation-gate"
            ));
        }
        if (hasForgedBindingClaim(validationGateReport)) {
            blockers.add(forgedClaimBlocker("durableAuditReceiptValidationGateReport"));
        }
    }

    private static boolean validationPlanContractValid(Map<String, Object> auditContext,
                                                       Map<String, Object> principal,
                                                       Map<String, Object> validationGateReport,
                                                       Map<String, Object> validationPlan) {
        return !validationPlan.isEmpty()
            && validationPlanSourceDigestsMatch(validationGateReport, validationPlan)
            && digestFor(auditContext).equals(text(validationPlan.get("sourceAuditEventDigest")))
            && text(auditContext.get("organizationId")).equals(text(validationGateReport.get("sourceOrganizationId")))
            && text(auditContext.get("userId")).equals(text(validationGateReport.get("sourceUserId")))
            && text(principal.get("username")).equals(text(validationGateReport.get("sourceUsername")))
            && validationPlan.equals(
                NimCreateDurableAuditReceiptValidationGateSupport.validationPlanFromReport(validationGateReport));
    }

    private static boolean validationPlanSourceDigestsMatch(Map<String, Object> validationGateReport,
                                                            Map<String, Object> validationPlan) {
        for (String field : List.of(
            "sourceReceiptSchemaDigest",
            "sourceInterfaceSpecDigest",
            "sourceBoundaryPlanDigest",
            "sourceWriterPlanDigest",
            "sourceAvailabilityPlanDigest"
        )) {
            if (!text(validationGateReport.get(field)).equals(text(validationPlan.get(field)))) {
                return false;
            }
        }
        return true;
    }

    private static void validateCrossBinding(Map<String, Object> probeResultReport,
                                             Map<String, Object> validationGateReport,
                                             List<Map<String, Object>> blockers) {
        if (probeResultReport.isEmpty() || validationGateReport.isEmpty()) {
            return;
        }
        boolean valid = text(probeResultReport.get("sourceAuditEventDigest")).equals(
                text(validationGateReport.get("sourceAuditEventDigest")))
            && text(probeResultReport.get("sourceReceiptSchemaDigest")).equals(
                text(validationGateReport.get("sourceReceiptSchemaDigest")))
            && text(probeResultReport.get("sourceInterfaceSpecDigest")).equals(
                text(validationGateReport.get("sourceInterfaceSpecDigest")))
            && text(probeResultReport.get("sourceBoundaryPlanDigest")).equals(
                text(validationGateReport.get("sourceBoundaryPlanDigest")))
            && text(probeResultReport.get("sourceWriterPlanDigest")).equals(
                text(validationGateReport.get("sourceWriterPlanDigest")))
            && text(probeResultReport.get("sourceAvailabilityPlanDigest")).equals(
                text(validationGateReport.get("sourceAvailabilityPlanDigest")));
        if (!valid) {
            blockers.add(blocker(
                "RECEIPT_VALIDATION_PROBE_RESULT_DIGEST_CHAIN_MISMATCH",
                "storage probe result report 与 receipt validation gate report 必须绑定同一 audit event、schema、writer plan、availability plan 和 writer boundary。",
                "upstream-digest-chain"
            ));
        }
    }

    private static void validateCallerEvidence(Map<String, Object> callerEvidence,
                                               List<Map<String, Object>> blockers) {
        if (!callerEvidence.isEmpty()) {
            blockers.add(blocker(
                "CALLER_RECEIPT_EVIDENCE_NOT_AUTHORITATIVE_FOR_PROBE_RESULT_BINDING",
                "调用方提供的 probe result、storage receipt、ack 或 validation evidence 无权参与 receipt validation 绑定。",
                "caller-receipt-evidence"
            ));
        }
        if (hasForgedBindingClaim(callerEvidence)) {
            blockers.add(forgedClaimBlocker("callerReceiptEvidence"));
        }
    }

    private static Map<String, Object> bindingPlan(Map<String, Object> auditContext,
                                                   Map<String, Object> principal,
                                                   Map<String, Object> probeResultReport,
                                                   Map<String, Object> validationGateReport) {
        return bindingPlanForDigests(
            bindingSourceDigests(probeResultReport, validationGateReport),
            digestFor(auditContext),
            text(auditContext.get("organizationId")),
            text(auditContext.get("userId")),
            text(principal.get("username"))
        );
    }

    static Map<String, Object> bindingPlanFromReport(Map<String, Object> bindingReport) {
        return bindingPlanForDigests(
            bindingReport,
            text(bindingReport.get("sourceAuditEventDigest")),
            text(bindingReport.get("sourceOrganizationId")),
            text(bindingReport.get("sourceUserId")),
            text(bindingReport.get("sourceUsername"))
        );
    }

    private static Map<String, Object> bindingPlanForDigests(Map<String, Object> sourceDigests,
                                                             String sourceAuditEventDigest,
                                                             String organizationId,
                                                             String userId,
                                                             String username) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("bindingBoundary", "SERVER_SIDE_RECEIPT_VALIDATION_REQUIRES_STORAGE_PROBE_RESULT");
        plan.put("futureBinding", FUTURE_BINDING);
        plan.put("futureValidator", NimCreateDurableAuditReceiptValidationGateSupport.FUTURE_VALIDATOR);
        plan.put("futureProbeResultType", NimCreateDurableAuditStorageProbeResultSupport.FUTURE_RESULT_TYPE);
        plan.put("futureProbeReceiptType", NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE);
        putSourceDigests(plan, sourceDigests);
        plan.put("sourceAuditEventDigest", sourceAuditEventDigest);
        plan.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        plan.put("trustedIdentityBinding", Map.of(
            "organizationId", organizationId,
            "userId", userId,
            "username", username,
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        plan.put("requiredBindingEvidence", requiredBindingEvidence(sourceDigests));
        plan.put("validationSequencePatch", validationSequencePatch());
        plan.put("currentDecisionTemplate", currentDecisionTemplate());
        plan.put("failureContract", failureContract());
        plan.put("forbiddenShortcuts", forbiddenShortcuts());
        return plan;
    }

    private static Map<String, Object> bindingSourceDigests(Map<String, Object> probeResultReport,
                                                            Map<String, Object> validationGateReport) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("sourceProbeResultContractDigest", text(probeResultReport.get("probeResultContractDigest")));
        source.put("sourceProbeExecutorPlanDigest", text(probeResultReport.get("sourceProbeExecutorPlanDigest")));
        source.put("sourceReceiptSchemaDigest", text(probeResultReport.get("sourceReceiptSchemaDigest")));
        source.put("sourceValidationPlanDigest", text(validationGateReport.get("validationPlanDigest")));
        source.put("sourceInterfaceSpecDigest", text(probeResultReport.get("sourceInterfaceSpecDigest")));
        source.put("sourceBoundaryPlanDigest", text(probeResultReport.get("sourceBoundaryPlanDigest")));
        source.put("sourceWriterPlanDigest", text(probeResultReport.get("sourceWriterPlanDigest")));
        source.put("sourceAvailabilityPlanDigest", text(probeResultReport.get("sourceAvailabilityPlanDigest")));
        return source;
    }

    private static void putSourceDigests(Map<String, Object> target, Map<String, Object> sourceDigests) {
        target.put("sourceProbeResultContractDigest", text(sourceDigests.get("sourceProbeResultContractDigest")));
        target.put("sourceProbeExecutorPlanDigest", text(sourceDigests.get("sourceProbeExecutorPlanDigest")));
        target.put("sourceReceiptSchemaDigest", text(sourceDigests.get("sourceReceiptSchemaDigest")));
        target.put("sourceValidationPlanDigest", text(sourceDigests.get("sourceValidationPlanDigest")));
        target.put("sourceInterfaceSpecDigest", text(sourceDigests.get("sourceInterfaceSpecDigest")));
        target.put("sourceBoundaryPlanDigest", text(sourceDigests.get("sourceBoundaryPlanDigest")));
        target.put("sourceWriterPlanDigest", text(sourceDigests.get("sourceWriterPlanDigest")));
        target.put("sourceAvailabilityPlanDigest", text(sourceDigests.get("sourceAvailabilityPlanDigest")));
    }

    private static Map<String, Object> requiredBindingEvidence(Map<String, Object> sourceDigests) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("storageProbeResultContract", Map.of(
            "requiredReportName", NimCreateDurableAuditStorageProbeResultSupport.RESULT_CONTRACT_NAME,
            "requiredStateNow", HOLD_STATE,
            "sourceProbeResultContractDigest", text(sourceDigests.get("sourceProbeResultContractDigest")),
            "serverIssuedProbeResultRequiredFuture", true,
            "serverIssuedProbeResultAcceptedNow", false,
            "callerProbeResultAllowed", false
        ));
        evidence.put("receiptValidationGate", Map.of(
            "requiredReportName", NimCreateDurableAuditReceiptValidationGateSupport.GATE_NAME,
            "requiredStateNow", HOLD_STATE,
            "sourceValidationPlanDigest", text(sourceDigests.get("sourceValidationPlanDigest")),
            "schemaOnlyValidationAllowed", false,
            "validationCanRunNow", false
        ));
        evidence.put("futureStorageProbeReceipt", Map.of(
            "requiredType", NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE,
            "requiredStatus", NimCreateDurableAuditStorageProbeResultSupport.FUTURE_AVAILABLE_STATUS,
            "mustBindProbeResultContractDigest", true,
            "mustBindProbeExecutorPlanDigest", true,
            "mustBindReceiptSchemaDigest", true,
            "mustBindAuditEventDigest", true,
            "mustBeServerIssued", true
        ));
        return evidence;
    }

    private static List<Map<String, Object>> validationSequencePatch() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(validationStep(
            "bind-storage-probe-result-contract",
            "Bind M5.21-67 storage probe result contract before any storage probe receipt validation",
            NimCreateDurableAuditStorageProbeResultSupport.FUTURE_RESULT_TYPE
        ));
        steps.add(validationStep(
            "reject-schema-only-validation",
            "Reject attempts to run receipt validation from schema or validation gate plan alone",
            "SCHEMA_ONLY_SHORTCUT"
        ));
        steps.add(validationStep(
            "defer-real-receipt-validation",
            "Keep real receipt validation held until a reviewed server-issued probe result exists",
            NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE
        ));
        return steps;
    }

    private static Map<String, Object> validationStep(String id, String requirement, String evidenceType) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("requirement", requirement);
        step.put("evidenceType", evidenceType);
        step.put("futureOnly", true);
        step.put("sideEffectAllowedNow", false);
        step.put("failClosed", true);
        return step;
    }

    private static Map<String, Object> currentDecisionTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("storageProbeResultBoundForValidation", false);
        template.put("serverIssuedProbeResultAccepted", false);
        template.put("storageProbeReceiptValidated", false);
        template.put("durableReceiptValidationPassed", false);
        template.put("validationStatus", VALIDATION_NOT_RUN);
        template.put("releaseEligible", false);
        template.put("writeExecutionAllowed", false);
        return template;
    }

    private static Map<String, Object> failureContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("failClosed", true);
        contract.put("fallbackToSchemaOnlyAllowed", false);
        contract.put("fallbackToValidationGateOnlyAllowed", false);
        contract.put("fallbackToCallerProbeResultAllowed", false);
        contract.put("fallbackToCallerReceiptAllowed", false);
        contract.put("failureStatuses", List.of(
            "IMPLEMENTATION_HOLD",
            "STORAGE_PROBE_RESULT_REPORT_NOT_READY",
            "STORAGE_PROBE_RESULT_DIGEST_MISMATCH",
            "RECEIPT_VALIDATION_GATE_REPORT_NOT_READY",
            "PROBE_RESULT_VALIDATION_GATE_CHAIN_MISMATCH",
            "SERVER_ISSUED_PROBE_RESULT_MISSING",
            "CALLER_EVIDENCE_REJECTED",
            "SECRET_MATERIAL_REJECTED"
        ));
        return contract;
    }

    private static List<String> forbiddenShortcuts() {
        return List.of(
            "accepting receipt schema report as storage probe evidence",
            "accepting receipt validation gate report without binding storage probe result contract digest",
            "accepting caller-supplied probe result or storage probe receipt",
            "accepting storageAvailable=true before a reviewed server-issued probe result exists",
            "allowing receipt validation or write execution before storage probe result binding"
        );
    }

    private static boolean hasOnlyExpectedProbeResultHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "STORAGE_PROBE_RESULT_IMPLEMENTATION_HOLD".equals(text(blockers.get(0).get("code")));
    }

    private static boolean hasOnlyExpectedValidationGateHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_IMPLEMENTATION_HOLD".equals(text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "PROBE_RESULT_VALIDATION_BINDING_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static boolean hasForgedBindingClaim(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForgedBindingClaim(entry.getKey(), value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && hasForgedBindingClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && hasForgedBindingClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isForgedBindingClaim(String key, Object value) {
        return switch (key) {
            case "resultIssued",
                "serverIssuedProbeResultAccepted",
                "storageProbeResultBoundForValidation",
                "validationCanRunNow",
                "storageProbeExecuted",
                "realStorageTouched",
                "storageAvailable",
                "storageProbeReceiptIssued",
                "storageProbeReceiptValidated",
                "durableAckVerified",
                "readAfterWriteVerified",
                "preWriteDurableAckValidated",
                "postWriteDurableAckValidated",
                "digestChainValidated",
                "trustedPrincipalValidated",
                "durableReceiptValidated",
                "durableReceiptValidationPassed",
                "durableReceiptAccepted",
                "preWriteAllowed",
                "writePermitted",
                "writeExecutionAllowed",
                "realHttpExecutionAllowed",
                "durableReceiptCanBeIssued",
                "durableReceiptIssued",
                "releaseEligible",
                "durable" -> Boolean.TRUE.equals(value);
            case "validationStatus" -> Set.of("PASS", "VALIDATED").contains(text(value));
            case "probeStatus" -> Set.of(
                "SUCCESS",
                NimCreateDurableAuditStorageProbeResultSupport.FUTURE_AVAILABLE_STATUS
            ).contains(text(value));
            case "availabilityStatus" -> "AVAILABLE".equals(text(value));
            case "receiptStatus" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(value));
            case "storageMode" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(value));
            case "probeResult",
                "storageProbeResult",
                "nimDurableAuditStorageProbeResult",
                "validationResult",
                "releaseDecision" -> value != null;
            default -> false;
        };
    }

    private static Map<String, Object> forgedClaimBlocker(String source) {
        return blocker(
            "PROBE_RESULT_VALIDATION_BINDING_FORGED_SUCCESS_CLAIM",
            source + " 不得自称 probe result 已绑定、receipt validation 已通过、durable receipt 已签发或写执行可放行。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> probeResultReport,
                                                    Map<String, Object> validationGateReport,
                                                    Map<String, Object> callerEvidence) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "probeResult",
            "storageProbeResult",
            "storageProbeReceipt",
            "validationResult",
            "durableReceipt",
            "storageProbeResultBoundForValidation",
            "serverIssuedProbeResultAccepted",
            "validationCanRunNow",
            "storageAvailable",
            "storageProbeReceiptValidated",
            "durableReceiptValidationPassed",
            "releaseEligible",
            "writeExecutionAllowed",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key)
                || principal.containsKey(key)
                || probeResultReport.containsKey(key)
                || validationGateReport.containsKey(key)
                || callerEvidence.containsKey(key)) {
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

    record ReceiptValidationProbeResultBindingInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditStorageProbeResultReport,
        Map<String, Object> durableAuditReceiptValidationGateReport,
        Map<String, Object> callerReceiptEvidence
    ) {
        ReceiptValidationProbeResultBindingInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditStorageProbeResultReport = durableAuditStorageProbeResultReport == null
                ? Map.of()
                : objectMap(durableAuditStorageProbeResultReport);
            durableAuditReceiptValidationGateReport = durableAuditReceiptValidationGateReport == null
                ? Map.of()
                : objectMap(durableAuditReceiptValidationGateReport);
            callerReceiptEvidence = callerReceiptEvidence == null ? Map.of() : objectMap(callerReceiptEvidence);
        }

        static ReceiptValidationProbeResultBindingInput empty() {
            return new ReceiptValidationProbeResultBindingInput(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
