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
 * NIM durable audit ack/receipt 的未来类型化契约。
 *
 * <p>本类只描述未来 {@code NimDurableAuditWriter} 返回的 storage probe receipt、pre-write ack、
 * post-write ack 和 durable receipt 应该如何做 digest 绑定、状态约束和失败闭环。它不创建真实类型实例，
 * 不连接 Elasticsearch，不调用 {@code ISysLogService}，也不访问 kube-manager。</p>
 */
final class NimCreateDurableAuditReceiptSchemaSupport {

    static final String SCHEMA_NAME = "NIM_CREATE_DURABLE_AUDIT_RECEIPT_ACK_SCHEMA";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String STORAGE_PROBE_RECEIPT_TYPE = "StorageAvailabilityProbeReceipt";
    static final String PRE_WRITE_ACK_TYPE = "PreWriteDurableAck";
    static final String POST_WRITE_ACK_TYPE = "PostWriteDurableAck";
    static final String DURABLE_RECEIPT_TYPE = "DurableAuditReceipt";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String STORAGE_AVAILABLE_STATUS = "STORAGE_AVAILABLE_CONFIRMED";
    private static final String PRE_WRITE_ACK_STATUS = "PRE_WRITE_DURABLY_RECORDED";
    private static final String POST_WRITE_ACK_STATUS = "POST_WRITE_DURABLY_RECORDED";
    private NimCreateDurableAuditReceiptSchemaSupport() {
    }

    static List<String> storageProbeRequiredFields() {
        return List.of(
            "receiptType",
            "auditEventDigest",
            "interfaceSpecDigest",
            "storageTarget",
            "probeAttemptId",
            "probeStatus",
            "available",
            "observedAt",
            "issuedBy"
        );
    }

    static List<String> durableAckRequiredFields(String requiredPreviousDigestField) {
        return List.of(
            "ackType",
            "auditEventDigest",
            "interfaceSpecDigest",
            "recordDigest",
            requiredPreviousDigestField,
            "writeAttemptId",
            "ackStatus",
            "durable",
            "observedAt",
            "issuedBy"
        );
    }

    static List<String> durableReceiptRequiredFields() {
        return List.of(
            "receiptType",
            "auditEventDigest",
            "interfaceSpecDigest",
            "storageProbeReceiptDigest",
            "preWriteDurableAckDigest",
            "postWriteDurableAckDigest",
            "trustedPrincipalDigest",
            "receiptStatus",
            "storageMode",
            "issuedAt",
            "issuedBy"
        );
    }

    static Map<String, Object> plan(DurableAuditReceiptSchemaInput input) {
        DurableAuditReceiptSchemaInput safeInput = input == null
            ? DurableAuditReceiptSchemaInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> interfaceSpecReport = safeInput.durableAuditWriterInterfaceSpecReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateInterfaceSpecReport(auditContext, principal, interfaceSpecReport, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditWriterInterfaceSpecReport", interfaceSpecReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> typedSchema = inputAccepted
            ? typedSchema(auditContext, principal, interfaceSpecReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_IMPLEMENTATION_HOLD",
                "typed ack/receipt schema 已定义，但真实 storage probe、pre-write ack、post-write ack 和 durable receipt 尚未实现；当前不能签发任何 receipt 或 ack 实例。",
                "durable-audit-receipt-schema"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditReceiptAckSchema", SCHEMA_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("schemaState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureInterface", NimCreateDurableAuditWriterInterfaceSpecSupport.FUTURE_INTERFACE);
        result.put("storageProbeReceiptType", STORAGE_PROBE_RECEIPT_TYPE);
        result.put("preWriteAckType", PRE_WRITE_ACK_TYPE);
        result.put("postWriteAckType", POST_WRITE_ACK_TYPE);
        result.put("durableReceiptType", DURABLE_RECEIPT_TYPE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("typedSchemaPrepared", inputAccepted);
        result.put("realStorageTouched", false);
        result.put("storageProbeExecuted", false);
        result.put("storageAvailable", false);
        result.put("availabilityStatus", NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN);
        result.put("storageProbeReceiptIssued", false);
        result.put("preWritePersisted", false);
        result.put("postWritePersisted", false);
        result.put("preWriteDurableAckIssued", false);
        result.put("postWriteDurableAckIssued", false);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("durableReceiptCanBeIssued", false);
        result.put("durableReceiptIssued", false);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceInterfaceSpecDigest", text(interfaceSpecReport.get("interfaceSpecDigest")));
        result.put("sourceBoundaryPlanDigest", text(interfaceSpecReport.get("sourceBoundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(interfaceSpecReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(interfaceSpecReport.get("sourceAvailabilityPlanDigest")));
        result.put("schemaDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("schemaDigest", inputAccepted ? digestFor(typedSchema) : "");
        result.put("typedSchema", typedSchema);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(auditContext, principal, interfaceSpecReport));
        result.put("nextImplementationRequirements", List.of(
            "turn these schema contracts into reviewed Java value types after the real writer boundary is approved",
            "issue StorageAvailabilityProbeReceipt only from a real server-side storage probe",
            "issue PreWriteDurableAck only after sanitized pre-write intent is durably persisted",
            "issue PostWriteDurableAck only after sanitized post-write result is durably persisted",
            "assemble DurableAuditReceipt only when all ack digests bind the same audit event and trusted principal"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_DURABLE_RECEIPT_SCHEMA",
                "typed receipt schema 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedSuccessClaim(auditContext)) {
            blockers.add(forgedSuccessClaimBlocker("auditContext"));
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
                "typed receipt schema 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedSuccessClaim(principal)) {
            blockers.add(forgedSuccessClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateInterfaceSpecReport(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> interfaceSpecReport,
                                                    List<Map<String, Object>> blockers) {
        if (interfaceSpecReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_WRITER_INTERFACE_SPEC_REPORT_NOT_READY",
                "缺少 M5.21-55 durable writer interface spec 报告；不能定义 typed ack/receipt schema。",
                "durable-audit-writer-interface-spec"
            ));
            return;
        }

        Map<String, Object> interfaceSpec = objectMap(interfaceSpecReport.get("interfaceSpec"));
        boolean valid = NimCreateDurableAuditWriterInterfaceSpecSupport.INTERFACE_SPEC_NAME.equals(text(interfaceSpecReport.get("durableAuditWriterInterfaceSpec")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.EXECUTION_MODE.equals(text(interfaceSpecReport.get("executionMode")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.HOLD_STATE.equals(text(interfaceSpecReport.get("interfaceSpecState")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.FUTURE_INTERFACE.equals(text(interfaceSpecReport.get("futureInterface")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.REQUEST_TYPE.equals(text(interfaceSpecReport.get("requestType")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.RESPONSE_TYPE.equals(text(interfaceSpecReport.get("responseType")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(interfaceSpecReport.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(interfaceSpecReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(interfaceSpecReport.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(interfaceSpecReport.get("networkAccess")))
            && "NONE".equals(text(interfaceSpecReport.get("sideEffect")))
            && Boolean.TRUE.equals(interfaceSpecReport.get("inputAccepted"))
            && Boolean.TRUE.equals(interfaceSpecReport.get("interfaceSpecPrepared"))
            && Boolean.FALSE.equals(interfaceSpecReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(interfaceSpecReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(interfaceSpecReport.get("storageAvailable"))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN.equals(text(interfaceSpecReport.get("availabilityStatus")))
            && Boolean.FALSE.equals(interfaceSpecReport.get("preWritePersisted"))
            && Boolean.FALSE.equals(interfaceSpecReport.get("postWritePersisted"))
            && Boolean.FALSE.equals(interfaceSpecReport.get("durable"))
            && Boolean.FALSE.equals(interfaceSpecReport.get("releaseEligible"))
            && Boolean.FALSE.equals(interfaceSpecReport.get("durableReceiptCanBeIssued"))
            && Boolean.FALSE.equals(interfaceSpecReport.get("durableReceiptIssued"))
            && text(interfaceSpecReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(interfaceSpecReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(interfaceSpecReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(interfaceSpecReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(interfaceSpecReport.get("interfaceSpecDigestAlgorithm")))
            && text(interfaceSpecReport.get("interfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(interfaceSpecReport.get("interfaceSpecDigest")).equals(digestFor(interfaceSpec))
            && hasOnlyExpectedInterfaceSpecHold(interfaceSpecReport.get("blockedBy"))
            && interfaceSpecContractValid(auditContext, principal, interfaceSpecReport, interfaceSpec);

        if (!valid) {
            blockers.add(blocker(
                "DURABLE_AUDIT_WRITER_INTERFACE_SPEC_REPORT_INVALID_FOR_RECEIPT_SCHEMA",
                "typed receipt schema 只能消费 M5.21-55 产生的、仍处于 HOLD 且未声明真实存储成功的 interface spec report。",
                "durable-audit-writer-interface-spec"
            ));
        }
        if (hasForgedSuccessClaim(interfaceSpecReport)) {
            blockers.add(forgedSuccessClaimBlocker("durableAuditWriterInterfaceSpecReport"));
        }
    }

    private static boolean interfaceSpecContractValid(Map<String, Object> auditContext,
                                                      Map<String, Object> principal,
                                                      Map<String, Object> interfaceSpecReport,
                                                      Map<String, Object> interfaceSpec) {
        Map<String, Object> identity = objectMap(interfaceSpec.get("trustedIdentityBinding"));
        return !interfaceSpec.isEmpty()
            && NimCreateDurableAuditWriterInterfaceSpecSupport.FUTURE_INTERFACE.equals(text(interfaceSpec.get("futureInterface")))
            && "SERVER_SIDE_ONLY".equals(text(interfaceSpec.get("interfaceBoundary")))
            && "FUTURE_REVIEWED_IMPLEMENTATION_REQUIRED".equals(text(interfaceSpec.get("implementationMode")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(interfaceSpec.get("targetStorage")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY.equals(text(interfaceSpec.get("targetEntity")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(interfaceSpec.get("saveService")))
            && text(interfaceSpecReport.get("sourceBoundaryPlanDigest")).equals(text(interfaceSpec.get("sourceBoundaryPlanDigest")))
            && text(interfaceSpecReport.get("sourceWriterPlanDigest")).equals(text(interfaceSpec.get("sourceWriterPlanDigest")))
            && text(interfaceSpecReport.get("sourceAvailabilityPlanDigest")).equals(text(interfaceSpec.get("sourceAvailabilityPlanDigest")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && requestContractValid(auditContext, interfaceSpecReport, objectMap(interfaceSpec.get("requestContract")))
            && responseContractValid(objectMap(interfaceSpec.get("responseContract")))
            && operationMethodsValid(interfaceSpec.get("operationMethods"))
            && failureContractValid(objectMap(interfaceSpec.get("failureContract")))
            && testDoubleRulesValid(interfaceSpecReport, objectMap(interfaceSpec.get("testDoubleRules")));
    }

    private static boolean requestContractValid(Map<String, Object> auditContext,
                                                Map<String, Object> interfaceSpecReport,
                                                Map<String, Object> requestContract) {
        List<String> requiredFields = stringList(requestContract.get("requiredFields"));
        List<String> forbiddenFields = stringList(requestContract.get("forbiddenFields"));
        return !requestContract.isEmpty()
            && NimCreateDurableAuditWriterInterfaceSpecSupport.REQUEST_TYPE.equals(text(requestContract.get("requestType")))
            && Boolean.TRUE.equals(requestContract.get("trustedInputsOnly"))
            && Boolean.FALSE.equals(requestContract.get("callerSuppliedIdentityAllowed"))
            && Boolean.FALSE.equals(requestContract.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(requestContract.get("authorizationHeaderFromCallerAllowed"))
            && Boolean.FALSE.equals(requestContract.get("realApiKeyAllowed"))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(requestContract.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(requestContract.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(requestContract.get("pathTemplate")))
            && digestFor(auditContext).equals(text(requestContract.get("sourceAuditEventDigest")))
            && text(interfaceSpecReport.get("sourceBoundaryPlanDigest")).equals(text(requestContract.get("sourceBoundaryPlanDigest")))
            && requiredFields.equals(NimCreateDurableAuditWriterInterfaceSpecSupport.requestRequiredFields())
            && forbiddenFields.contains("Authorization")
            && forbiddenFields.contains("apiKey")
            && phaseContractsValid(requestContract.get("phaseContracts"));
    }

    private static boolean phaseContractsValid(Object rawPhases) {
        List<Map<String, Object>> phases = listOfMaps(rawPhases);
        if (phases.size() != 4) {
            return false;
        }
        return "PROBE_STORAGE".equals(text(phases.get(0).get("phase")))
            && "PRE_WRITE_INTENT".equals(text(phases.get(1).get("phase")))
            && "POST_WRITE_RESULT".equals(text(phases.get(2).get("phase")))
            && "ASSEMBLE_RECEIPT".equals(text(phases.get(3).get("phase")))
            && phases.stream().allMatch(phase -> Boolean.TRUE.equals(phase.get("futureOnly")))
            && phases.stream().allMatch(phase -> Boolean.FALSE.equals(phase.get("sideEffectAllowedNow")))
            && phases.stream().allMatch(phase -> Boolean.TRUE.equals(phase.get("failClosed")));
    }

    private static boolean responseContractValid(Map<String, Object> responseContract) {
        Map<String, Object> currentResponse = objectMap(responseContract.get("currentResponseTemplate"));
        List<String> requiredFutureSuccessFields = stringList(responseContract.get("requiredFutureSuccessFields"));
        return !responseContract.isEmpty()
            && NimCreateDurableAuditWriterInterfaceSpecSupport.RESPONSE_TYPE.equals(text(responseContract.get("responseType")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.HOLD_STATE.equals(text(responseContract.get("currentImplementationStatus")))
            && Boolean.FALSE.equals(responseContract.get("successAllowedNow"))
            && Boolean.FALSE.equals(responseContract.get("durableReceiptAllowedNow"))
            && requiredFutureSuccessFields.equals(
                NimCreateDurableAuditWriterInterfaceSpecSupport.responseRequiredFutureSuccessFields()
            )
            && NimCreateDurableAuditWriterInterfaceSpecSupport.HOLD_STATE.equals(text(currentResponse.get("status")))
            && Boolean.FALSE.equals(currentResponse.get("storageAvailable"))
            && Boolean.FALSE.equals(currentResponse.get("preWritePersisted"))
            && Boolean.FALSE.equals(currentResponse.get("postWritePersisted"))
            && Boolean.FALSE.equals(currentResponse.get("durableReceiptCanBeIssued"))
            && "NOT_ISSUED".equals(text(currentResponse.get("receiptStatus")))
            && "NONE".equals(text(currentResponse.get("storageMode")));
    }

    private static boolean operationMethodsValid(Object rawMethods) {
        List<Map<String, Object>> methods = listOfMaps(rawMethods);
        if (methods.size() != 4) {
            return false;
        }
        return operationMethodValid(methods.get(0), "probeStorageAvailability", "StorageAvailabilityProbeRequest", "StorageAvailabilityProbeResult")
            && operationMethodValid(methods.get(1), "persistPreWriteIntent", "PreWriteAuditRecord", "DurableAuditAck")
            && operationMethodValid(methods.get(2), "persistPostWriteResult", "PostWriteAuditRecord", "DurableAuditAck")
            && operationMethodValid(methods.get(3), "assembleDurableReceipt", "DurableAuditAckPair", NimCreateDurableAuditWriterInterfaceSpecSupport.RESPONSE_TYPE)
            && methods.stream().allMatch(method -> Boolean.FALSE.equals(method.get("sideEffectAllowedNow")))
            && methods.stream().allMatch(method -> Boolean.TRUE.equals(method.get("implementationRequiredBeforeRelease")))
            && methods.stream().allMatch(method -> Boolean.TRUE.equals(method.get("failClosed")));
    }

    private static boolean operationMethodValid(Map<String, Object> method,
                                                String name,
                                                String inputType,
                                                String outputType) {
        return name.equals(text(method.get("name")))
            && inputType.equals(text(method.get("inputType")))
            && outputType.equals(text(method.get("outputType")));
    }

    private static boolean failureContractValid(Map<String, Object> failureContract) {
        List<String> statuses = stringList(failureContract.get("failureStatuses"));
        return !failureContract.isEmpty()
            && Boolean.TRUE.equals(failureContract.get("failClosed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToMockReceiptAllowed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToBoundaryPlanAllowed"))
            && Boolean.FALSE.equals(failureContract.get("fallbackToCandidateStorageReportAllowed"))
            && statuses.equals(NimCreateDurableAuditWriterInterfaceSpecSupport.failureStatuses());
    }

    private static boolean testDoubleRulesValid(Map<String, Object> interfaceSpecReport,
                                                Map<String, Object> testDoubleRules) {
        List<String> forbiddenSuccessClaims = stringList(testDoubleRules.get("forbiddenSuccessClaims"));
        return !testDoubleRules.isEmpty()
            && "UNIT_CONTRACT_ONLY".equals(text(testDoubleRules.get("testDoubleScope")))
            && text(interfaceSpecReport.get("sourceBoundaryPlanDigest")).equals(text(testDoubleRules.get("sourceBoundaryPlanDigest")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.HOLD_STATE.equals(text(testDoubleRules.get("mayReturnStatus")))
            && "NOT_PERFORMED".equals(text(testDoubleRules.get("mustReturnNetworkAccess")))
            && "NONE".equals(text(testDoubleRules.get("mustReturnSideEffect")))
            && Boolean.TRUE.equals(testDoubleRules.get("mustKeepStorageAvailableFalse"))
            && Boolean.TRUE.equals(testDoubleRules.get("mustKeepPreWritePersistedFalse"))
            && Boolean.TRUE.equals(testDoubleRules.get("mustKeepPostWritePersistedFalse"))
            && Boolean.TRUE.equals(testDoubleRules.get("mustKeepDurableReceiptNotIssued"))
            && forbiddenSuccessClaims.equals(
                NimCreateDurableAuditWriterInterfaceSpecSupport.testDoubleForbiddenSuccessClaims()
            );
    }

    private static Map<String, Object> typedSchema(Map<String, Object> auditContext,
                                                   Map<String, Object> principal,
                                                   Map<String, Object> interfaceSpecReport) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("schemaBoundary", "FUTURE_TYPED_DURABLE_ACK_RECEIPT_ONLY");
        schema.put("futureInterface", NimCreateDurableAuditWriterInterfaceSpecSupport.FUTURE_INTERFACE);
        schema.put("sourceInterfaceSpecDigest", text(interfaceSpecReport.get("interfaceSpecDigest")));
        schema.put("sourceBoundaryPlanDigest", text(interfaceSpecReport.get("sourceBoundaryPlanDigest")));
        schema.put("sourceWriterPlanDigest", text(interfaceSpecReport.get("sourceWriterPlanDigest")));
        schema.put("sourceAvailabilityPlanDigest", text(interfaceSpecReport.get("sourceAvailabilityPlanDigest")));
        schema.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        schema.put("targetEntity", NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY);
        schema.put("saveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        schema.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        schema.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        schema.put("storageAvailabilityProbeReceiptSchema", storageAvailabilityProbeReceiptSchema(auditContext, interfaceSpecReport));
        schema.put("preWriteDurableAckSchema", durableAckSchema(
            PRE_WRITE_ACK_TYPE,
            "PRE_WRITE_INTENT",
            NimCreateDurableAuditWriterPlanSupport.PRE_WRITE_RECORD_TYPE,
            PRE_WRITE_ACK_STATUS,
            "storageProbeReceiptDigest"
        ));
        schema.put("postWriteDurableAckSchema", durableAckSchema(
            POST_WRITE_ACK_TYPE,
            "POST_WRITE_RESULT",
            NimCreateDurableAuditWriterPlanSupport.POST_WRITE_RECORD_TYPE,
            POST_WRITE_ACK_STATUS,
            "preWriteDurableAckDigest"
        ));
        schema.put("durableAuditReceiptSchema", durableAuditReceiptSchema());
        schema.put("digestChainRules", digestChainRules(auditContext, interfaceSpecReport));
        schema.put("currentResponseTemplate", currentResponseTemplate());
        schema.put("failureContract", failureContract());
        schema.put("testDoubleRules", testDoubleRules(interfaceSpecReport));
        return schema;
    }

    private static Map<String, Object> storageAvailabilityProbeReceiptSchema(Map<String, Object> auditContext,
                                                                             Map<String, Object> interfaceSpecReport) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", STORAGE_PROBE_RECEIPT_TYPE);
        schema.put("phase", "PROBE_STORAGE");
        schema.put("futureOnly", true);
        schema.put("instanceAllowedNow", false);
        schema.put("sideEffectAllowedNow", false);
        schema.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        schema.put("sourceAuditEventDigest", digestFor(auditContext));
        schema.put("sourceInterfaceSpecDigest", text(interfaceSpecReport.get("interfaceSpecDigest")));
        schema.put("requiredFutureStatus", STORAGE_AVAILABLE_STATUS);
        schema.put("requiredFields", storageProbeRequiredFields());
        schema.put("currentTemplate", Map.of(
            "receiptType", STORAGE_PROBE_RECEIPT_TYPE,
            "probeExecuted", false,
            "available", false,
            "probeStatus", NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN,
            "receiptIssued", false
        ));
        return schema;
    }

    private static Map<String, Object> durableAckSchema(String type,
                                                        String phase,
                                                        String recordType,
                                                        String futureStatus,
                                                        String requiredPreviousDigestField) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", type);
        schema.put("phase", phase);
        schema.put("recordType", recordType);
        schema.put("futureOnly", true);
        schema.put("instanceAllowedNow", false);
        schema.put("sideEffectAllowedNow", false);
        schema.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        schema.put("requiredFutureAckStatus", futureStatus);
        schema.put("requiredPreviousDigestField", requiredPreviousDigestField);
        schema.put("requiredFields", durableAckRequiredFields(requiredPreviousDigestField));
        schema.put("currentTemplate", Map.of(
            "ackType", type,
            "ackIssued", false,
            "ackStatus", "NOT_ISSUED",
            "durable", false
        ));
        return schema;
    }

    private static Map<String, Object> durableAuditReceiptSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", DURABLE_RECEIPT_TYPE);
        schema.put("phase", "ASSEMBLE_RECEIPT");
        schema.put("futureOnly", true);
        schema.put("instanceAllowedNow", false);
        schema.put("sideEffectAllowedNow", false);
        schema.put("requiredFutureReceiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS);
        schema.put("requiredFutureStorageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE);
        schema.put("requiredFields", durableReceiptRequiredFields());
        schema.put("prerequisites", Map.of(
            "storageAvailableRequired", true,
            "preWriteDurableAckRequired", true,
            "postWriteDurableAckRequired", true,
            "sameAuditEventDigestRequired", true,
            "sameTrustedPrincipalRequired", true,
            "mockReceiptAllowed", false
        ));
        schema.put("currentTemplate", Map.of(
            "receiptType", DURABLE_RECEIPT_TYPE,
            "receiptIssued", false,
            "receiptStatus", "NOT_ISSUED",
            "storageMode", "NONE"
        ));
        return schema;
    }

    private static Map<String, Object> digestChainRules(Map<String, Object> auditContext,
                                                        Map<String, Object> interfaceSpecReport) {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        rules.put("sourceAuditEventDigest", digestFor(auditContext));
        rules.put("sourceInterfaceSpecDigest", text(interfaceSpecReport.get("interfaceSpecDigest")));
        rules.put("canonicalizationRequired", true);
        rules.put("rules", List.of(
            "StorageAvailabilityProbeReceipt.auditEventDigest must equal sourceAuditEventDigest",
            "PreWriteDurableAck.auditEventDigest must equal StorageAvailabilityProbeReceipt.auditEventDigest",
            "PostWriteDurableAck.auditEventDigest must equal PreWriteDurableAck.auditEventDigest",
            "DurableAuditReceipt must include all three upstream digests",
            "DurableAuditReceipt cannot be assembled from test-double or caller-supplied ack instances"
        ));
        return rules;
    }

    private static Map<String, Object> currentResponseTemplate() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HOLD_STATE);
        response.put("storageProbeReceiptIssued", false);
        response.put("preWriteDurableAckIssued", false);
        response.put("postWriteDurableAckIssued", false);
        response.put("durableReceiptIssued", false);
        response.put("receiptStatus", "NOT_ISSUED");
        response.put("storageMode", "NONE");
        return response;
    }

    private static Map<String, Object> failureContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("failClosed", true);
        contract.put("fallbackToMockReceiptAllowed", false);
        contract.put("fallbackToSchemaOnlyAllowed", false);
        contract.put("failureStatuses", List.of(
            "IMPLEMENTATION_HOLD",
            "STORAGE_PROBE_RECEIPT_MISSING",
            "STORAGE_PROBE_RECEIPT_NOT_AVAILABLE",
            "PRE_WRITE_DURABLE_ACK_MISSING",
            "PRE_WRITE_ACK_DIGEST_MISMATCH",
            "POST_WRITE_DURABLE_ACK_MISSING",
            "POST_WRITE_ACK_DIGEST_MISMATCH",
            "DURABLE_RECEIPT_DIGEST_CHAIN_MISMATCH",
            "ACK_OR_RECEIPT_FORGED",
            "SECRET_MATERIAL_REJECTED"
        ));
        return contract;
    }

    private static Map<String, Object> testDoubleRules(Map<String, Object> interfaceSpecReport) {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("testDoubleScope", "UNIT_CONTRACT_ONLY");
        rules.put("sourceInterfaceSpecDigest", text(interfaceSpecReport.get("interfaceSpecDigest")));
        rules.put("mayReturnStatus", HOLD_STATE);
        rules.put("mustReturnNetworkAccess", "NOT_PERFORMED");
        rules.put("mustReturnSideEffect", "NONE");
        rules.put("mustNotReturnTypeInstances", List.of(
            STORAGE_PROBE_RECEIPT_TYPE,
            PRE_WRITE_ACK_TYPE,
            POST_WRITE_ACK_TYPE,
            DURABLE_RECEIPT_TYPE
        ));
        rules.put("forbiddenSuccessClaims", List.of(
            "StorageAvailabilityProbeReceipt.available=true",
            "PreWriteDurableAck.ackStatus=PRE_WRITE_DURABLY_RECORDED",
            "PostWriteDurableAck.ackStatus=POST_WRITE_DURABLY_RECORDED",
            "DurableAuditReceipt.receiptStatus=DURABLE_RECORDED",
            "DurableAuditReceipt.storageMode=DURABLE_AUDIT_LOG",
            "realStorageTouched=true"
        ));
        return rules;
    }

    private static boolean hasOnlyExpectedInterfaceSpecHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DURABLE_AUDIT_WRITER_INTERFACE_IMPLEMENTATION_HOLD".equals(text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_SCHEMA_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static boolean hasForgedSuccessClaim(Map<String, Object> map) {
        return Boolean.TRUE.equals(map.get("storageProbeExecuted"))
            || Boolean.TRUE.equals(map.get("storageAvailable"))
            || "AVAILABLE".equals(text(map.get("availabilityStatus")))
            || STORAGE_AVAILABLE_STATUS.equals(text(map.get("probeStatus")))
            || Boolean.TRUE.equals(map.get("storageProbeReceiptIssued"))
            || Boolean.TRUE.equals(map.get("preWritePersisted"))
            || Boolean.TRUE.equals(map.get("postWritePersisted"))
            || Boolean.TRUE.equals(map.get("preWriteDurable"))
            || Boolean.TRUE.equals(map.get("postWriteDurable"))
            || Boolean.TRUE.equals(map.get("preWriteDurableAckIssued"))
            || Boolean.TRUE.equals(map.get("postWriteDurableAckIssued"))
            || PRE_WRITE_ACK_STATUS.equals(text(map.get("ackStatus")))
            || POST_WRITE_ACK_STATUS.equals(text(map.get("ackStatus")))
            || Boolean.TRUE.equals(map.get("durableReceiptCanBeIssued"))
            || Boolean.TRUE.equals(map.get("durableReceiptIssued"))
            || Boolean.TRUE.equals(map.get("releaseEligible"))
            || Boolean.TRUE.equals(map.get("realStorageTouched"))
            || Boolean.TRUE.equals(map.get("durable"))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(map.get("receiptStatus")))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(map.get("storageMode")))
            || containsForgedTypedInstance(map);
    }

    private static boolean containsForgedTypedInstance(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isTypedInstanceKey(key) && value != null) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && containsForgedTypedInstance(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && containsForgedTypedInstance(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isTypedInstanceKey(String key) {
        return "storageProbeReceipt".equals(key)
            || "preWriteDurableAck".equals(key)
            || "postWriteDurableAck".equals(key)
            || "durableAuditReceipt".equals(key)
            || "durableReceipt".equals(key);
    }

    private static Map<String, Object> forgedSuccessClaimBlocker(String source) {
        return blocker(
            "DURABLE_AUDIT_RECEIPT_SCHEMA_FORGED_SUCCESS_CLAIM",
            source + " 不得自称 typed ack/receipt、storageAvailable、preWritePersisted、postWritePersisted、DURABLE_RECORDED 或真实存储成功。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> interfaceSpecReport) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "storageProbeExecuted",
            "storageAvailable",
            "availabilityStatus",
            "storageProbeReceipt",
            "storageProbeReceiptIssued",
            "preWritePersisted",
            "postWritePersisted",
            "preWriteDurable",
            "postWriteDurable",
            "preWriteDurableAck",
            "postWriteDurableAck",
            "preWriteDurableAckIssued",
            "postWriteDurableAckIssued",
            "durableAuditReceipt",
            "durableReceipt",
            "durableReceiptCanBeIssued",
            "durableReceiptIssued",
            "releaseEligible",
            "realStorageTouched",
            "durable",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key) || principal.containsKey(key) || interfaceSpecReport.containsKey(key)) {
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

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record DurableAuditReceiptSchemaInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditWriterInterfaceSpecReport
    ) {
        DurableAuditReceiptSchemaInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditWriterInterfaceSpecReport = durableAuditWriterInterfaceSpecReport == null ? Map.of() : objectMap(durableAuditWriterInterfaceSpecReport);
        }

        static DurableAuditReceiptSchemaInput empty() {
            return new DurableAuditReceiptSchemaInput(Map.of(), Map.of(), Map.of());
        }
    }
}
