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
 * NIM durable audit receipt 的未来校验门契约。
 *
 * <p>本类只定义未来真实 {@code DurableAuditReceipt} 出现后，服务端必须如何校验 storage probe、
 * pre-write ack、post-write ack、digest chain 和 trusted principal binding。它不接收真实 receipt
 * 作为成功证据，不访问 kube-manager，不连接 Elasticsearch，也不写 {@code sys_log}。</p>
 */
final class NimCreateDurableAuditReceiptValidationGateSupport {

    static final String GATE_NAME = "NIM_CREATE_DURABLE_AUDIT_RECEIPT_VALIDATION_GATE";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String FUTURE_VALIDATOR = "NimDurableAuditReceiptValidator";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String VALIDATION_NOT_RUN = "NOT_RUN_UNTIL_REAL_RECEIPT";
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

    private NimCreateDurableAuditReceiptValidationGateSupport() {
    }

    static Map<String, Object> plan(DurableAuditReceiptValidationGateInput input) {
        DurableAuditReceiptValidationGateInput safeInput = input == null
            ? DurableAuditReceiptValidationGateInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> schemaReport = safeInput.durableAuditReceiptAckSchemaReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateSchemaReport(auditContext, principal, schemaReport, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditReceiptAckSchemaReport", schemaReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> validationPlan = inputAccepted
            ? validationPlan(auditContext, principal, schemaReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_IMPLEMENTATION_HOLD",
                "receipt validation gate 已定义，但真实 typed ack/receipt 校验器尚未实现；当前不能验证或放行任何 durable receipt。",
                "durable-audit-receipt-validation"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditReceiptValidationGate", GATE_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("gateState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("validationGateState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureValidator", FUTURE_VALIDATOR);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("validationPlanPrepared", inputAccepted);
        result.put("validationRulesPrepared", inputAccepted);
        result.put("realStorageTouched", false);
        result.put("storageProbeExecuted", false);
        result.put("storageAvailable", false);
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
        result.put("durableReceiptCanBeIssued", false);
        result.put("durableReceiptIssued", false);
        result.put("writeExecutionAllowed", false);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceReceiptSchemaDigest", text(schemaReport.get("schemaDigest")));
        result.put("sourceInterfaceSpecDigest", text(schemaReport.get("sourceInterfaceSpecDigest")));
        result.put("sourceBoundaryPlanDigest", text(schemaReport.get("sourceBoundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(schemaReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(schemaReport.get("sourceAvailabilityPlanDigest")));
        result.put("validationPlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("validationPlanDigest", inputAccepted ? digestFor(validationPlan) : "");
        result.put("validationPlan", validationPlan);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(auditContext, principal, schemaReport));
        result.put("nextImplementationRequirements", List.of(
            "implement a reviewed server-side NimDurableAuditReceiptValidator after real typed ack/receipt classes exist",
            "validate storage probe receipt before accepting any pre-write ack",
            "validate pre-write and post-write ack phases, statuses, record digests and durable evidence",
            "validate the final durable receipt digest chain and trusted principal binding",
            "allow write execution only after the validator returns a real pass decision from server-side evidence"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_DURABLE_RECEIPT_VALIDATION_GATE",
                "receipt validation gate 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedValidationOrSuccessClaim(auditContext)) {
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
                "receipt validation gate 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedValidationOrSuccessClaim(principal)) {
            blockers.add(forgedClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateSchemaReport(Map<String, Object> auditContext,
                                             Map<String, Object> principal,
                                             Map<String, Object> schemaReport,
                                             List<Map<String, Object>> blockers) {
        if (schemaReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_NOT_READY",
                "缺少 M5.21-56 typed ack/receipt schema 报告；不能定义 receipt validation gate。",
                "durable-audit-receipt-schema"
            ));
            return;
        }

        Map<String, Object> typedSchema = objectMap(schemaReport.get("typedSchema"));
        boolean valid = NimCreateDurableAuditReceiptSchemaSupport.SCHEMA_NAME.equals(text(schemaReport.get("durableAuditReceiptAckSchema")))
            && NimCreateDurableAuditReceiptSchemaSupport.EXECUTION_MODE.equals(text(schemaReport.get("executionMode")))
            && NimCreateDurableAuditReceiptSchemaSupport.HOLD_STATE.equals(text(schemaReport.get("schemaState")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.FUTURE_INTERFACE.equals(text(schemaReport.get("futureInterface")))
            && NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE.equals(text(schemaReport.get("storageProbeReceiptType")))
            && NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE.equals(text(schemaReport.get("preWriteAckType")))
            && NimCreateDurableAuditReceiptSchemaSupport.POST_WRITE_ACK_TYPE.equals(text(schemaReport.get("postWriteAckType")))
            && NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE.equals(text(schemaReport.get("durableReceiptType")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(schemaReport.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(schemaReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(schemaReport.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(schemaReport.get("networkAccess")))
            && "NONE".equals(text(schemaReport.get("sideEffect")))
            && Boolean.TRUE.equals(schemaReport.get("inputAccepted"))
            && Boolean.TRUE.equals(schemaReport.get("typedSchemaPrepared"))
            && Boolean.FALSE.equals(schemaReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(schemaReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(schemaReport.get("storageAvailable"))
            && Boolean.FALSE.equals(schemaReport.get("storageProbeReceiptIssued"))
            && Boolean.FALSE.equals(schemaReport.get("preWritePersisted"))
            && Boolean.FALSE.equals(schemaReport.get("postWritePersisted"))
            && Boolean.FALSE.equals(schemaReport.get("preWriteDurableAckIssued"))
            && Boolean.FALSE.equals(schemaReport.get("postWriteDurableAckIssued"))
            && Boolean.FALSE.equals(schemaReport.get("durable"))
            && Boolean.FALSE.equals(schemaReport.get("releaseEligible"))
            && Boolean.FALSE.equals(schemaReport.get("durableReceiptCanBeIssued"))
            && Boolean.FALSE.equals(schemaReport.get("durableReceiptIssued"))
            && text(schemaReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(schemaReport.get("sourceInterfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(schemaReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(schemaReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(schemaReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(schemaReport.get("schemaDigestAlgorithm")))
            && text(schemaReport.get("schemaDigest")).matches("[a-f0-9]{64}")
            && text(schemaReport.get("schemaDigest")).equals(digestFor(typedSchema))
            && hasOnlyExpectedSchemaHold(schemaReport.get("blockedBy"))
            && typedSchemaContractValid(auditContext, principal, schemaReport, typedSchema);

        if (!valid) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_INVALID_FOR_VALIDATION_GATE",
                "receipt validation gate 只能消费 M5.21-56 产生的、仍处于 HOLD 且未声明真实 ack/receipt 的 typed schema report。",
                "durable-audit-receipt-schema"
            ));
        }
        if (hasForgedValidationOrSuccessClaim(schemaReport)) {
            blockers.add(forgedClaimBlocker("durableAuditReceiptAckSchemaReport"));
        }
    }

    private static boolean typedSchemaContractValid(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> schemaReport,
                                                    Map<String, Object> typedSchema) {
        Map<String, Object> identity = objectMap(typedSchema.get("trustedIdentityBinding"));
        return !typedSchema.isEmpty()
            && "FUTURE_TYPED_DURABLE_ACK_RECEIPT_ONLY".equals(text(typedSchema.get("schemaBoundary")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.FUTURE_INTERFACE.equals(text(typedSchema.get("futureInterface")))
            && text(schemaReport.get("schemaDigest")).matches("[a-f0-9]{64}")
            && text(schemaReport.get("sourceInterfaceSpecDigest")).equals(text(typedSchema.get("sourceInterfaceSpecDigest")))
            && text(schemaReport.get("sourceBoundaryPlanDigest")).equals(text(typedSchema.get("sourceBoundaryPlanDigest")))
            && text(schemaReport.get("sourceWriterPlanDigest")).equals(text(typedSchema.get("sourceWriterPlanDigest")))
            && text(schemaReport.get("sourceAvailabilityPlanDigest")).equals(text(typedSchema.get("sourceAvailabilityPlanDigest")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(typedSchema.get("targetStorage")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY.equals(text(typedSchema.get("targetEntity")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(typedSchema.get("saveService")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(typedSchema.get("digestAlgorithm")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && storageProbeSchemaValid(auditContext, schemaReport, objectMap(typedSchema.get("storageAvailabilityProbeReceiptSchema")))
            && durableAckSchemaValid(objectMap(typedSchema.get("preWriteDurableAckSchema")),
                NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE,
                "PRE_WRITE_INTENT",
                NimCreateDurableAuditWriterPlanSupport.PRE_WRITE_RECORD_TYPE,
                "PRE_WRITE_DURABLY_RECORDED",
                "storageProbeReceiptDigest")
            && durableAckSchemaValid(objectMap(typedSchema.get("postWriteDurableAckSchema")),
                NimCreateDurableAuditReceiptSchemaSupport.POST_WRITE_ACK_TYPE,
                "POST_WRITE_RESULT",
                NimCreateDurableAuditWriterPlanSupport.POST_WRITE_RECORD_TYPE,
                "POST_WRITE_DURABLY_RECORDED",
                "preWriteDurableAckDigest")
            && durableReceiptSchemaValid(objectMap(typedSchema.get("durableAuditReceiptSchema")))
            && digestChainRulesValid(auditContext, schemaReport, objectMap(typedSchema.get("digestChainRules")))
            && currentResponseTemplateValid(objectMap(typedSchema.get("currentResponseTemplate")))
            && failureContractValid(objectMap(typedSchema.get("failureContract")))
            && testDoubleRulesValid(schemaReport, objectMap(typedSchema.get("testDoubleRules")));
    }

    private static boolean storageProbeSchemaValid(Map<String, Object> auditContext,
                                                   Map<String, Object> schemaReport,
                                                   Map<String, Object> schema) {
        Map<String, Object> currentTemplate = objectMap(schema.get("currentTemplate"));
        List<String> requiredFields = stringList(schema.get("requiredFields"));
        return !schema.isEmpty()
            && NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE.equals(text(schema.get("type")))
            && "PROBE_STORAGE".equals(text(schema.get("phase")))
            && Boolean.TRUE.equals(schema.get("futureOnly"))
            && Boolean.FALSE.equals(schema.get("instanceAllowedNow"))
            && Boolean.FALSE.equals(schema.get("sideEffectAllowedNow"))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(schema.get("targetStorage")))
            && digestFor(auditContext).equals(text(schema.get("sourceAuditEventDigest")))
            && text(schemaReport.get("sourceInterfaceSpecDigest")).equals(text(schema.get("sourceInterfaceSpecDigest")))
            && "STORAGE_AVAILABLE_CONFIRMED".equals(text(schema.get("requiredFutureStatus")))
            && requiredFields.contains("auditEventDigest")
            && requiredFields.contains("interfaceSpecDigest")
            && requiredFields.contains("probeStatus")
            && requiredFields.contains("available")
            && NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE.equals(text(currentTemplate.get("receiptType")))
            && Boolean.FALSE.equals(currentTemplate.get("probeExecuted"))
            && Boolean.FALSE.equals(currentTemplate.get("available"))
            && Boolean.FALSE.equals(currentTemplate.get("receiptIssued"));
    }

    private static boolean durableAckSchemaValid(Map<String, Object> schema,
                                                 String type,
                                                 String phase,
                                                 String recordType,
                                                 String status,
                                                 String previousDigestField) {
        Map<String, Object> currentTemplate = objectMap(schema.get("currentTemplate"));
        List<String> requiredFields = stringList(schema.get("requiredFields"));
        return !schema.isEmpty()
            && type.equals(text(schema.get("type")))
            && phase.equals(text(schema.get("phase")))
            && recordType.equals(text(schema.get("recordType")))
            && Boolean.TRUE.equals(schema.get("futureOnly"))
            && Boolean.FALSE.equals(schema.get("instanceAllowedNow"))
            && Boolean.FALSE.equals(schema.get("sideEffectAllowedNow"))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(schema.get("targetStorage")))
            && status.equals(text(schema.get("requiredFutureAckStatus")))
            && previousDigestField.equals(text(schema.get("requiredPreviousDigestField")))
            && requiredFields.contains("auditEventDigest")
            && requiredFields.contains("interfaceSpecDigest")
            && requiredFields.contains("recordDigest")
            && requiredFields.contains(previousDigestField)
            && requiredFields.contains("ackStatus")
            && requiredFields.contains("durable")
            && type.equals(text(currentTemplate.get("ackType")))
            && Boolean.FALSE.equals(currentTemplate.get("ackIssued"))
            && "NOT_ISSUED".equals(text(currentTemplate.get("ackStatus")))
            && Boolean.FALSE.equals(currentTemplate.get("durable"));
    }

    private static boolean durableReceiptSchemaValid(Map<String, Object> schema) {
        Map<String, Object> prerequisites = objectMap(schema.get("prerequisites"));
        Map<String, Object> currentTemplate = objectMap(schema.get("currentTemplate"));
        List<String> requiredFields = stringList(schema.get("requiredFields"));
        return !schema.isEmpty()
            && NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE.equals(text(schema.get("type")))
            && "ASSEMBLE_RECEIPT".equals(text(schema.get("phase")))
            && Boolean.TRUE.equals(schema.get("futureOnly"))
            && Boolean.FALSE.equals(schema.get("instanceAllowedNow"))
            && Boolean.FALSE.equals(schema.get("sideEffectAllowedNow"))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(schema.get("requiredFutureReceiptStatus")))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(schema.get("requiredFutureStorageMode")))
            && requiredFields.contains("auditEventDigest")
            && requiredFields.contains("interfaceSpecDigest")
            && requiredFields.contains("storageProbeReceiptDigest")
            && requiredFields.contains("preWriteDurableAckDigest")
            && requiredFields.contains("postWriteDurableAckDigest")
            && requiredFields.contains("trustedPrincipalDigest")
            && Boolean.TRUE.equals(prerequisites.get("storageAvailableRequired"))
            && Boolean.TRUE.equals(prerequisites.get("preWriteDurableAckRequired"))
            && Boolean.TRUE.equals(prerequisites.get("postWriteDurableAckRequired"))
            && Boolean.TRUE.equals(prerequisites.get("sameAuditEventDigestRequired"))
            && Boolean.TRUE.equals(prerequisites.get("sameTrustedPrincipalRequired"))
            && Boolean.FALSE.equals(prerequisites.get("mockReceiptAllowed"))
            && NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE.equals(text(currentTemplate.get("receiptType")))
            && Boolean.FALSE.equals(currentTemplate.get("receiptIssued"))
            && "NOT_ISSUED".equals(text(currentTemplate.get("receiptStatus")))
            && "NONE".equals(text(currentTemplate.get("storageMode")));
    }

    private static boolean digestChainRulesValid(Map<String, Object> auditContext,
                                                 Map<String, Object> schemaReport,
                                                 Map<String, Object> rules) {
        List<String> ruleTexts = stringList(rules.get("rules"));
        return !rules.isEmpty()
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(rules.get("digestAlgorithm")))
            && digestFor(auditContext).equals(text(rules.get("sourceAuditEventDigest")))
            && text(schemaReport.get("sourceInterfaceSpecDigest")).equals(text(rules.get("sourceInterfaceSpecDigest")))
            && Boolean.TRUE.equals(rules.get("canonicalizationRequired"))
            && ruleTexts.contains("StorageAvailabilityProbeReceipt.auditEventDigest must equal sourceAuditEventDigest")
            && ruleTexts.contains("PreWriteDurableAck.auditEventDigest must equal StorageAvailabilityProbeReceipt.auditEventDigest")
            && ruleTexts.contains("PostWriteDurableAck.auditEventDigest must equal PreWriteDurableAck.auditEventDigest")
            && ruleTexts.contains("DurableAuditReceipt cannot be assembled from test-double or caller-supplied ack instances");
    }

    private static boolean currentResponseTemplateValid(Map<String, Object> currentResponse) {
        return !currentResponse.isEmpty()
            && NimCreateDurableAuditReceiptSchemaSupport.HOLD_STATE.equals(text(currentResponse.get("status")))
            && Boolean.FALSE.equals(currentResponse.get("storageProbeReceiptIssued"))
            && Boolean.FALSE.equals(currentResponse.get("preWriteDurableAckIssued"))
            && Boolean.FALSE.equals(currentResponse.get("postWriteDurableAckIssued"))
            && Boolean.FALSE.equals(currentResponse.get("durableReceiptIssued"))
            && "NOT_ISSUED".equals(text(currentResponse.get("receiptStatus")))
            && "NONE".equals(text(currentResponse.get("storageMode")));
    }

    private static boolean failureContractValid(Map<String, Object> failureContract) {
        List<String> statuses = stringList(failureContract.get("failureStatuses"));
        return !failureContract.isEmpty()
            && Boolean.TRUE.equals(failureContract.get("failClosed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToMockReceiptAllowed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToSchemaOnlyAllowed"))
            && statuses.contains("IMPLEMENTATION_HOLD")
            && statuses.contains("STORAGE_PROBE_RECEIPT_MISSING")
            && statuses.contains("PRE_WRITE_DURABLE_ACK_MISSING")
            && statuses.contains("POST_WRITE_DURABLE_ACK_MISSING")
            && statuses.contains("DURABLE_RECEIPT_DIGEST_CHAIN_MISMATCH")
            && statuses.contains("ACK_OR_RECEIPT_FORGED");
    }

    private static boolean testDoubleRulesValid(Map<String, Object> schemaReport,
                                                Map<String, Object> testDoubleRules) {
        List<String> mustNotReturnTypes = stringList(testDoubleRules.get("mustNotReturnTypeInstances"));
        List<String> forbiddenClaims = stringList(testDoubleRules.get("forbiddenSuccessClaims"));
        return !testDoubleRules.isEmpty()
            && "UNIT_CONTRACT_ONLY".equals(text(testDoubleRules.get("testDoubleScope")))
            && text(schemaReport.get("sourceInterfaceSpecDigest")).equals(text(testDoubleRules.get("sourceInterfaceSpecDigest")))
            && NimCreateDurableAuditReceiptSchemaSupport.HOLD_STATE.equals(text(testDoubleRules.get("mayReturnStatus")))
            && "NOT_PERFORMED".equals(text(testDoubleRules.get("mustReturnNetworkAccess")))
            && "NONE".equals(text(testDoubleRules.get("mustReturnSideEffect")))
            && mustNotReturnTypes.contains(NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE)
            && mustNotReturnTypes.contains(NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE)
            && mustNotReturnTypes.contains(NimCreateDurableAuditReceiptSchemaSupport.POST_WRITE_ACK_TYPE)
            && mustNotReturnTypes.contains(NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE)
            && forbiddenClaims.contains("DurableAuditReceipt.receiptStatus=DURABLE_RECORDED")
            && forbiddenClaims.contains("DurableAuditReceipt.storageMode=DURABLE_AUDIT_LOG")
            && forbiddenClaims.contains("realStorageTouched=true");
    }

    private static Map<String, Object> validationPlan(Map<String, Object> auditContext,
                                                      Map<String, Object> principal,
                                                      Map<String, Object> schemaReport) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("validationBoundary", "SERVER_SIDE_DURABLE_RECEIPT_VALIDATION_GATE_REQUIRED");
        plan.put("futureValidator", FUTURE_VALIDATOR);
        plan.put("sourceReceiptSchemaDigest", text(schemaReport.get("schemaDigest")));
        plan.put("sourceInterfaceSpecDigest", text(schemaReport.get("sourceInterfaceSpecDigest")));
        plan.put("sourceBoundaryPlanDigest", text(schemaReport.get("sourceBoundaryPlanDigest")));
        plan.put("sourceWriterPlanDigest", text(schemaReport.get("sourceWriterPlanDigest")));
        plan.put("sourceAvailabilityPlanDigest", text(schemaReport.get("sourceAvailabilityPlanDigest")));
        plan.put("sourceAuditEventDigest", digestFor(auditContext));
        plan.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        plan.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        plan.put("validationSequence", validationSequence());
        plan.put("requiredEvidence", requiredEvidence(schemaReport));
        plan.put("releaseDecisionTemplate", releaseDecisionTemplate());
        plan.put("failureContract", validationFailureContract());
        plan.put("forbiddenShortcuts", forbiddenShortcuts());
        return plan;
    }

    private static List<Map<String, Object>> validationSequence() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(validationStep(
            "validate-schema-digest",
            "Verify the receipt schema report digest before accepting any typed evidence",
            "SCHEMA_DIGEST"
        ));
        steps.add(validationStep(
            "validate-storage-probe-receipt",
            "Verify storage probe receipt status, target storage and audit event digest",
            NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE
        ));
        steps.add(validationStep(
            "validate-pre-write-durable-ack",
            "Verify pre-write ack phase, status, record digest and previous probe receipt digest",
            NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE
        ));
        steps.add(validationStep(
            "validate-post-write-durable-ack",
            "Verify post-write ack phase, status, record digest and previous pre-write ack digest",
            NimCreateDurableAuditReceiptSchemaSupport.POST_WRITE_ACK_TYPE
        ));
        steps.add(validationStep(
            "validate-final-durable-receipt",
            "Verify final receipt status, storage mode, trusted principal digest and complete digest chain",
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

    private static Map<String, Object> requiredEvidence(Map<String, Object> schemaReport) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("sourceReceiptSchemaDigest", text(schemaReport.get("schemaDigest")));
        evidence.put("storageProbeReceipt", Map.of(
            "requiredType", NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE,
            "requiredStatus", "STORAGE_AVAILABLE_CONFIRMED",
            "mustBindAuditEventDigest", true,
            "mustBindInterfaceSpecDigest", true,
            "mustBeServerIssued", true
        ));
        evidence.put("preWriteDurableAck", Map.of(
            "requiredType", NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE,
            "requiredPhase", "PRE_WRITE_INTENT",
            "requiredStatus", "PRE_WRITE_DURABLY_RECORDED",
            "mustBindStorageProbeReceiptDigest", true,
            "mustBindRecordDigest", true,
            "mustBeServerIssued", true
        ));
        evidence.put("postWriteDurableAck", Map.of(
            "requiredType", NimCreateDurableAuditReceiptSchemaSupport.POST_WRITE_ACK_TYPE,
            "requiredPhase", "POST_WRITE_RESULT",
            "requiredStatus", "POST_WRITE_DURABLY_RECORDED",
            "mustBindPreWriteDurableAckDigest", true,
            "mustBindRecordDigest", true,
            "mustBeServerIssued", true
        ));
        evidence.put("durableReceipt", Map.of(
            "requiredType", NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE,
            "requiredReceiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS,
            "requiredStorageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE,
            "mustIncludeAllAckDigests", true,
            "mustBindTrustedPrincipalDigest", true,
            "mustBeServerIssued", true
        ));
        return evidence;
    }

    private static Map<String, Object> releaseDecisionTemplate() {
        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("validationStatus", VALIDATION_NOT_RUN);
        decision.put("storageProbeReceiptValidated", false);
        decision.put("preWriteDurableAckValidated", false);
        decision.put("postWriteDurableAckValidated", false);
        decision.put("digestChainValidated", false);
        decision.put("trustedPrincipalValidated", false);
        decision.put("durableReceiptValidated", false);
        decision.put("releaseEligible", false);
        decision.put("writeExecutionAllowed", false);
        decision.put("receiptStatus", "NOT_ISSUED");
        decision.put("storageMode", "NONE");
        return decision;
    }

    private static Map<String, Object> validationFailureContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("failClosed", true);
        contract.put("fallbackToMockReceiptAllowed", false);
        contract.put("fallbackToSchemaOnlyAllowed", false);
        contract.put("fallbackToCallerReceiptAllowed", false);
        contract.put("failureStatuses", List.of(
            "IMPLEMENTATION_HOLD",
            "RECEIPT_VALIDATION_NOT_IMPLEMENTED",
            "SCHEMA_DIGEST_MISMATCH",
            "STORAGE_PROBE_RECEIPT_INVALID",
            "PRE_WRITE_ACK_INVALID",
            "POST_WRITE_ACK_INVALID",
            "DIGEST_CHAIN_MISMATCH",
            "TRUSTED_PRINCIPAL_MISMATCH",
            "FORGED_VALIDATION_PASS_CLAIM",
            "SECRET_MATERIAL_REJECTED"
        ));
        return contract;
    }

    private static List<String> forbiddenShortcuts() {
        return List.of(
            "accepting schema report as validation pass",
            "accepting test double receipt or ack instance",
            "accepting caller-supplied validationStatus=PASS",
            "accepting releaseEligible=true from upstream input",
            "accepting receiptStatus=DURABLE_RECORDED without validating all ack digests",
            "allowing write execution before receipt validation gate passes"
        );
    }

    private static boolean hasOnlyExpectedSchemaHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_IMPLEMENTATION_HOLD".equals(text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static boolean hasForgedValidationOrSuccessClaim(Map<String, Object> map) {
        return Boolean.TRUE.equals(map.get("storageProbeExecuted"))
            || Boolean.TRUE.equals(map.get("storageAvailable"))
            || "AVAILABLE".equals(text(map.get("availabilityStatus")))
            || Boolean.TRUE.equals(map.get("storageProbeReceiptIssued"))
            || Boolean.TRUE.equals(map.get("storageProbeReceiptValidated"))
            || Boolean.TRUE.equals(map.get("preWritePersisted"))
            || Boolean.TRUE.equals(map.get("postWritePersisted"))
            || Boolean.TRUE.equals(map.get("preWriteDurable"))
            || Boolean.TRUE.equals(map.get("postWriteDurable"))
            || Boolean.TRUE.equals(map.get("preWriteDurableAckIssued"))
            || Boolean.TRUE.equals(map.get("postWriteDurableAckIssued"))
            || Boolean.TRUE.equals(map.get("preWriteDurableAckValidated"))
            || Boolean.TRUE.equals(map.get("postWriteDurableAckValidated"))
            || Boolean.TRUE.equals(map.get("digestChainValidated"))
            || Boolean.TRUE.equals(map.get("trustedPrincipalValidated"))
            || Boolean.TRUE.equals(map.get("durableReceiptValidated"))
            || "PASS".equals(text(map.get("validationStatus")))
            || "VALIDATED".equals(text(map.get("validationStatus")))
            || Boolean.TRUE.equals(map.get("writeExecutionAllowed"))
            || Boolean.TRUE.equals(map.get("durableReceiptCanBeIssued"))
            || Boolean.TRUE.equals(map.get("durableReceiptIssued"))
            || Boolean.TRUE.equals(map.get("releaseEligible"))
            || Boolean.TRUE.equals(map.get("realStorageTouched"))
            || Boolean.TRUE.equals(map.get("durable"))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(map.get("receiptStatus")))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(map.get("storageMode")))
            || containsTypedEvidenceOrValidationResult(map);
    }

    private static boolean containsTypedEvidenceOrValidationResult(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isTypedEvidenceOrValidationKey(key) && value != null) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && containsTypedEvidenceOrValidationResult(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && containsTypedEvidenceOrValidationResult(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isTypedEvidenceOrValidationKey(String key) {
        return "storageProbeReceipt".equals(key)
            || "preWriteDurableAck".equals(key)
            || "postWriteDurableAck".equals(key)
            || "durableAuditReceipt".equals(key)
            || "durableReceipt".equals(key)
            || "validationResult".equals(key)
            || "releaseDecision".equals(key);
    }

    private static Map<String, Object> forgedClaimBlocker(String source) {
        return blocker(
            "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_FORGED_PASS_CLAIM",
            source + " 不得自称 validation PASS、typed ack/receipt、releaseEligible、writeExecutionAllowed 或真实存储成功。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> schemaReport) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "storageProbeExecuted",
            "storageAvailable",
            "availabilityStatus",
            "storageProbeReceipt",
            "storageProbeReceiptIssued",
            "storageProbeReceiptValidated",
            "preWritePersisted",
            "postWritePersisted",
            "preWriteDurable",
            "postWriteDurable",
            "preWriteDurableAck",
            "postWriteDurableAck",
            "preWriteDurableAckIssued",
            "postWriteDurableAckIssued",
            "preWriteDurableAckValidated",
            "postWriteDurableAckValidated",
            "durableAuditReceipt",
            "durableReceipt",
            "durableReceiptValidated",
            "digestChainValidated",
            "trustedPrincipalValidated",
            "validationResult",
            "validationStatus",
            "releaseDecision",
            "durableReceiptCanBeIssued",
            "durableReceiptIssued",
            "releaseEligible",
            "writeExecutionAllowed",
            "realStorageTouched",
            "durable",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key) || principal.containsKey(key) || schemaReport.containsKey(key)) {
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

    record DurableAuditReceiptValidationGateInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditReceiptAckSchemaReport
    ) {
        DurableAuditReceiptValidationGateInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditReceiptAckSchemaReport = durableAuditReceiptAckSchemaReport == null ? Map.of() : objectMap(durableAuditReceiptAckSchemaReport);
        }

        static DurableAuditReceiptValidationGateInput empty() {
            return new DurableAuditReceiptValidationGateInput(Map.of(), Map.of(), Map.of());
        }
    }
}
