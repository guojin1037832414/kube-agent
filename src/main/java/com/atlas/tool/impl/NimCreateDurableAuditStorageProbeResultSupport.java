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
 * NIM durable audit storage probe result 的未来服务端签发契约。
 *
 * <p>本类只定义未来 {@code NimDurableAuditStorageProbeResult} 必须如何绑定 probe executor、
 * typed receipt schema、审计事件和可信身份。当前不执行真实探测，不创建结果实例，也不允许 pre-write。</p>
 */
final class NimCreateDurableAuditStorageProbeResultSupport {

    static final String RESULT_CONTRACT_NAME = "NIM_CREATE_DURABLE_AUDIT_STORAGE_PROBE_RESULT_CONTRACT";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_STORAGE_PROBE_RESULT_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String FUTURE_RESULT_TYPE = "NimDurableAuditStorageProbeResult";
    static final String FUTURE_PROBE_RECEIPT_TYPE = NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE;
    static final String FUTURE_AVAILABLE_STATUS = "STORAGE_AVAILABLE_CONFIRMED";
    static final String CURRENT_STATUS = "NOT_ISSUED";

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

    private NimCreateDurableAuditStorageProbeResultSupport() {
    }

    static Map<String, Object> plan(StorageProbeResultInput input) {
        StorageProbeResultInput safeInput = input == null ? StorageProbeResultInput.empty() : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> probeExecutorReport = safeInput.storageProbeExecutorReport();
        Map<String, Object> receiptSchemaReport = safeInput.durableAuditReceiptAckSchemaReport();
        Map<String, Object> callerProbeResult = safeInput.callerProbeResult();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateProbeExecutorReport(auditContext, principal, probeExecutorReport, blockers);
        validateReceiptSchemaReport(auditContext, principal, receiptSchemaReport, blockers);
        validateCrossReportBinding(probeExecutorReport, receiptSchemaReport, blockers);
        validateCallerProbeResult(callerProbeResult, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("storageProbeExecutorReport", probeExecutorReport, blockers);
        validateNoSecretMaterial("durableAuditReceiptAckSchemaReport", receiptSchemaReport, blockers);
        validateNoSecretMaterial("callerProbeResult", callerProbeResult, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> resultContract = inputAccepted
            ? resultContract(auditContext, principal, probeExecutorReport, receiptSchemaReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "STORAGE_PROBE_RESULT_IMPLEMENTATION_HOLD",
                "server-issued storage probe result 尚未实现；当前不能签发 probe result、probe receipt 或 pre-write 放行。",
                "storage-probe-result"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditStorageProbeResultContract", RESULT_CONTRACT_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("probeResultState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureResultType", FUTURE_RESULT_TYPE);
        result.put("futureProbeReceiptType", FUTURE_PROBE_RECEIPT_TYPE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("springBeanRegistered", false);
        result.put("httpClientBound", false);
        result.put("storageClientBound", false);
        result.put("inputAccepted", inputAccepted);
        result.put("probeResultContractPrepared", inputAccepted);
        result.put("resultIssued", false);
        result.put("serverIssuedProbeResultAccepted", false);
        result.put("callerProbeResultAuthoritative", false);
        result.put("storageProbeExecuted", false);
        result.put("realStorageTouched", false);
        result.put("storageAvailable", false);
        result.put("availabilityStatus", NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN);
        result.put("probeStatus", CURRENT_STATUS);
        result.put("durableAckVerified", false);
        result.put("readAfterWriteVerified", false);
        result.put("storageProbeReceiptIssued", false);
        result.put("preWriteAllowed", false);
        result.put("writePermitted", false);
        result.put("writeExecutionAllowed", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("durableReceiptCanBeIssued", false);
        result.put("durableReceiptIssued", false);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("trustedPrincipalDigest", digestFor(principal));
        result.put("sourceProbeExecutorPlanDigest", text(probeExecutorReport.get("probeExecutorPlanDigest")));
        result.put("sourceReceiptSchemaDigest", text(receiptSchemaReport.get("schemaDigest")));
        result.put("sourceInterfaceSpecDigest", text(receiptSchemaReport.get("sourceInterfaceSpecDigest")));
        result.put("sourceBoundaryPlanDigest", text(probeExecutorReport.get("sourceBoundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(probeExecutorReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(probeExecutorReport.get("sourceAvailabilityPlanDigest")));
        result.put("probeResultContractDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("probeResultContractDigest", inputAccepted ? digestFor(resultContract) : "");
        result.put("probeResultContract", resultContract);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(
            auditContext,
            principal,
            probeExecutorReport,
            receiptSchemaReport,
            callerProbeResult
        ));
        result.put("nextImplementationRequirements", List.of(
            "implement a reviewed server-side NimDurableAuditStorageProbeResult issuer inside the probe executor",
            "issue StorageAvailabilityProbeReceipt only after real storage availability, durable ack and read-after-write are verified",
            "bind result digest to audit event, trusted principal, probe executor plan, receipt schema and writer boundary digests",
            "fail closed on unavailable, timeout, ambiguous ack, read-after-write missing, digest mismatch or principal mismatch",
            "keep caller supplied probe results non-authoritative until a reviewed server-issued result exists"
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
            || !integerOrgId(text(auditContext.get("organizationId")))) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_STORAGE_PROBE_RESULT",
                "storage probe result contract 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedProbeResultClaim(auditContext)) {
            blockers.add(forgedProbeResultClaimBlocker("auditContext"));
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
                "storage probe result contract 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedProbeResultClaim(principal)) {
            blockers.add(forgedProbeResultClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateProbeExecutorReport(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> probeExecutorReport,
                                                    List<Map<String, Object>> blockers) {
        if (probeExecutorReport.isEmpty()) {
            blockers.add(blocker(
                "STORAGE_PROBE_EXECUTOR_REPORT_NOT_READY",
                "缺少 M5.21-66 storage probe executor 报告；不能定义 server-issued probe result contract。",
                "storage-probe-executor"
            ));
            return;
        }

        Map<String, Object> probeAttemptSpec = objectMap(probeExecutorReport.get("probeAttemptSpec"));
        boolean valid = NimCreateDurableAuditStorageProbeExecutorSupport.EXECUTOR_NAME.equals(text(probeExecutorReport.get("durableAuditStorageProbeExecutor")))
            && NimCreateDurableAuditStorageProbeExecutorSupport.EXECUTION_MODE.equals(text(probeExecutorReport.get("executionMode")))
            && NimCreateDurableAuditStorageProbeExecutorSupport.HOLD_STATE.equals(text(probeExecutorReport.get("probeExecutorState")))
            && "NOT_PERFORMED".equals(text(probeExecutorReport.get("networkAccess")))
            && "NONE".equals(text(probeExecutorReport.get("sideEffect")))
            && Boolean.FALSE.equals(probeExecutorReport.get("springBeanRegistered"))
            && Boolean.FALSE.equals(probeExecutorReport.get("storageClientBound"))
            && Boolean.TRUE.equals(probeExecutorReport.get("inputAccepted"))
            && Boolean.TRUE.equals(probeExecutorReport.get("probeExecutorPlanPrepared"))
            && Boolean.FALSE.equals(probeExecutorReport.get("diagnosticProbeSnapshotAuthoritative"))
            && Boolean.TRUE.equals(probeExecutorReport.get("requiredInsideDedicatedWriterBoundary"))
            && Boolean.TRUE.equals(probeExecutorReport.get("requiredBeforePreWrite"))
            && Boolean.FALSE.equals(probeExecutorReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(probeExecutorReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(probeExecutorReport.get("storageAvailable"))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN.equals(text(probeExecutorReport.get("availabilityStatus")))
            && Boolean.FALSE.equals(probeExecutorReport.get("durableAckVerified"))
            && Boolean.FALSE.equals(probeExecutorReport.get("readAfterWriteVerified"))
            && Boolean.FALSE.equals(probeExecutorReport.get("preWriteAllowed"))
            && Boolean.FALSE.equals(probeExecutorReport.get("writePermitted"))
            && Boolean.FALSE.equals(probeExecutorReport.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(probeExecutorReport.get("realHttpExecutionAllowed"))
            && Boolean.FALSE.equals(probeExecutorReport.get("storageProbeReceiptIssued"))
            && Boolean.FALSE.equals(probeExecutorReport.get("durableReceiptCanBeIssued"))
            && text(probeExecutorReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(probeExecutorReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeExecutorReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeExecutorReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(probeExecutorReport.get("probeExecutorPlanDigestAlgorithm")))
            && text(probeExecutorReport.get("probeExecutorPlanDigest")).matches("[a-f0-9]{64}")
            && text(probeExecutorReport.get("probeExecutorPlanDigest")).equals(digestFor(probeAttemptSpec))
            && hasOnlyExpectedProbeExecutorHold(probeExecutorReport.get("blockedBy"))
            && probeAttemptSpecContractValid(auditContext, principal, probeExecutorReport, probeAttemptSpec);

        if (!valid) {
            blockers.add(blocker(
                "STORAGE_PROBE_EXECUTOR_REPORT_INVALID_FOR_PROBE_RESULT",
                "storage probe result contract 只能消费 M5.21-66 产生的、仍为 HOLD 且未签发真实 probe result 的 executor report。",
                "storage-probe-executor"
            ));
        }
        if (hasForgedProbeResultClaim(probeExecutorReport)) {
            blockers.add(forgedProbeResultClaimBlocker("storageProbeExecutorReport"));
        }
    }

    private static void validateReceiptSchemaReport(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> receiptSchemaReport,
                                                    List<Map<String, Object>> blockers) {
        if (receiptSchemaReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_NOT_READY",
                "缺少 M5.21-56 typed ack/receipt schema 报告；不能定义 probe result 的 receipt 形状。",
                "durable-audit-receipt-schema"
            ));
            return;
        }

        Map<String, Object> typedSchema = objectMap(receiptSchemaReport.get("typedSchema"));
        boolean valid = NimCreateDurableAuditReceiptSchemaSupport.SCHEMA_NAME.equals(text(receiptSchemaReport.get("durableAuditReceiptAckSchema")))
            && NimCreateDurableAuditReceiptSchemaSupport.EXECUTION_MODE.equals(text(receiptSchemaReport.get("executionMode")))
            && NimCreateDurableAuditReceiptSchemaSupport.HOLD_STATE.equals(text(receiptSchemaReport.get("schemaState")))
            && NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE.equals(text(receiptSchemaReport.get("storageProbeReceiptType")))
            && "NOT_PERFORMED".equals(text(receiptSchemaReport.get("networkAccess")))
            && "NONE".equals(text(receiptSchemaReport.get("sideEffect")))
            && Boolean.TRUE.equals(receiptSchemaReport.get("inputAccepted"))
            && Boolean.TRUE.equals(receiptSchemaReport.get("typedSchemaPrepared"))
            && Boolean.FALSE.equals(receiptSchemaReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(receiptSchemaReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(receiptSchemaReport.get("storageAvailable"))
            && Boolean.FALSE.equals(receiptSchemaReport.get("storageProbeReceiptIssued"))
            && Boolean.FALSE.equals(receiptSchemaReport.get("preWriteDurableAckIssued"))
            && Boolean.FALSE.equals(receiptSchemaReport.get("postWriteDurableAckIssued"))
            && Boolean.FALSE.equals(receiptSchemaReport.get("durableReceiptCanBeIssued"))
            && Boolean.FALSE.equals(receiptSchemaReport.get("durableReceiptIssued"))
            && text(receiptSchemaReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(receiptSchemaReport.get("sourceInterfaceSpecDigest")).matches("[a-f0-9]{64}")
            && text(receiptSchemaReport.get("sourceBoundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(receiptSchemaReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(receiptSchemaReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(receiptSchemaReport.get("schemaDigestAlgorithm")))
            && text(receiptSchemaReport.get("schemaDigest")).matches("[a-f0-9]{64}")
            && text(receiptSchemaReport.get("schemaDigest")).equals(digestFor(typedSchema))
            && hasOnlyExpectedSchemaHold(receiptSchemaReport.get("blockedBy"))
            && typedSchemaContractValid(auditContext, principal, receiptSchemaReport, typedSchema);

        if (!valid) {
            blockers.add(blocker(
                "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_INVALID_FOR_PROBE_RESULT",
                "storage probe result contract 只能消费 M5.21-56 产生的、仍为 HOLD 且未签发真实 ack/receipt 的 schema report。",
                "durable-audit-receipt-schema"
            ));
        }
        if (hasForgedProbeResultClaim(receiptSchemaReport)) {
            blockers.add(forgedProbeResultClaimBlocker("durableAuditReceiptAckSchemaReport"));
        }
    }

    private static void validateCallerProbeResult(Map<String, Object> callerProbeResult,
                                                  List<Map<String, Object>> blockers) {
        if (!callerProbeResult.isEmpty()) {
            blockers.add(blocker(
                "CALLER_PROBE_RESULT_NOT_AUTHORITATIVE",
                "调用方提供的 probe result 无权作为 server-issued storage probe result。",
                "caller-probe-result"
            ));
        }
        if (hasForgedProbeResultClaim(callerProbeResult)) {
            blockers.add(forgedProbeResultClaimBlocker("callerProbeResult"));
        }
    }

    private static void validateCrossReportBinding(Map<String, Object> probeExecutorReport,
                                                   Map<String, Object> receiptSchemaReport,
                                                   List<Map<String, Object>> blockers) {
        if (probeExecutorReport.isEmpty() || receiptSchemaReport.isEmpty()) {
            return;
        }
        boolean valid = text(probeExecutorReport.get("sourceAuditEventDigest")).equals(text(receiptSchemaReport.get("sourceAuditEventDigest")))
            && text(probeExecutorReport.get("sourceBoundaryPlanDigest")).equals(text(receiptSchemaReport.get("sourceBoundaryPlanDigest")))
            && text(probeExecutorReport.get("sourceWriterPlanDigest")).equals(text(receiptSchemaReport.get("sourceWriterPlanDigest")))
            && text(probeExecutorReport.get("sourceAvailabilityPlanDigest")).equals(text(receiptSchemaReport.get("sourceAvailabilityPlanDigest")));
        if (!valid) {
            blockers.add(blocker(
                "STORAGE_PROBE_RESULT_UPSTREAM_DIGEST_CHAIN_MISMATCH",
                "storage probe executor report 与 typed receipt schema report 必须绑定同一 audit event、writer plan、availability plan 和 writer boundary。",
                "upstream-digest-chain"
            ));
        }
    }

    private static boolean probeAttemptSpecContractValid(Map<String, Object> auditContext,
                                                         Map<String, Object> principal,
                                                         Map<String, Object> probeExecutorReport,
                                                         Map<String, Object> probeAttemptSpec) {
        Map<String, Object> evidence = objectMap(probeAttemptSpec.get("evidenceBinding"));
        Map<String, Object> identity = objectMap(probeAttemptSpec.get("trustedIdentityBinding"));
        Map<String, Object> state = objectMap(probeAttemptSpec.get("currentImplementationState"));
        Map<String, Object> resultContract = objectMap(probeAttemptSpec.get("probeResultContract"));
        Map<String, Object> failurePolicy = objectMap(probeAttemptSpec.get("failurePolicy"));

        return !probeAttemptSpec.isEmpty()
            && "SERVER_SIDE_DURABLE_AUDIT_STORAGE_PROBE_EXECUTOR_REQUIRED".equals(text(probeAttemptSpec.get("executorBoundary")))
            && "NimDurableAuditStorageProbeExecutor".equals(text(probeAttemptSpec.get("futureInterface")))
            && "INSIDE_DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY".equals(text(probeAttemptSpec.get("executionPlacement")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(probeAttemptSpec.get("targetStorage")))
            && Boolean.TRUE.equals(probeAttemptSpec.get("requiredBeforePreWrite"))
            && Boolean.FALSE.equals(probeAttemptSpec.get("sideEffectAllowedNow"))
            && text(evidence.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(evidence.get("sourceWriterPlanDigest")).equals(text(probeExecutorReport.get("sourceWriterPlanDigest")))
            && text(evidence.get("sourceAvailabilityPlanDigest")).equals(text(probeExecutorReport.get("sourceAvailabilityPlanDigest")))
            && text(evidence.get("sourceBoundaryPlanDigest")).equals(text(probeExecutorReport.get("sourceBoundaryPlanDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(evidence.get("digestAlgorithm")))
            && Boolean.TRUE.equals(evidence.get("probeResultMustBeServerIssued"))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && Boolean.FALSE.equals(state.get("executorImplemented"))
            && Boolean.FALSE.equals(state.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(state.get("storageAvailable"))
            && Boolean.FALSE.equals(state.get("durableAckVerified"))
            && Boolean.FALSE.equals(state.get("readAfterWriteVerified"))
            && Boolean.FALSE.equals(state.get("preWriteAllowed"))
            && Boolean.FALSE.equals(state.get("durableReceiptCanBeIssued"))
            && FUTURE_RESULT_TYPE.equals(text(resultContract.get("futureResultName")))
            && Boolean.TRUE.equals(resultContract.get("mustBeServerIssued"))
            && Boolean.TRUE.equals(resultContract.get("bindSameAuditEventDigest"))
            && Boolean.TRUE.equals(resultContract.get("bindSourceAvailabilityPlanDigest"))
            && Boolean.TRUE.equals(resultContract.get("bindSourceBoundaryPlanDigest"))
            && Boolean.FALSE.equals(resultContract.get("callerSnapshotCanIssuePass"))
            && Boolean.FALSE.equals(resultContract.get("currentContractCanIssuePass"))
            && Boolean.TRUE.equals(failurePolicy.get("failClosed"))
            && Boolean.TRUE.equals(failurePolicy.get("blockPreWriteWhenDigestMismatch"))
            && Boolean.TRUE.equals(failurePolicy.get("blockPreWriteWhenPrincipalMismatch"))
            && Boolean.FALSE.equals(failurePolicy.get("fallbackToMockProbeAllowed"))
            && Boolean.FALSE.equals(failurePolicy.get("fallbackToCallerProbeSnapshotAllowed"));
    }

    private static boolean typedSchemaContractValid(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> receiptSchemaReport,
                                                    Map<String, Object> typedSchema) {
        Map<String, Object> identity = objectMap(typedSchema.get("trustedIdentityBinding"));
        Map<String, Object> probeReceiptSchema = objectMap(typedSchema.get("storageAvailabilityProbeReceiptSchema"));
        return !typedSchema.isEmpty()
            && "FUTURE_TYPED_DURABLE_ACK_RECEIPT_ONLY".equals(text(typedSchema.get("schemaBoundary")))
            && NimCreateDurableAuditWriterInterfaceSpecSupport.FUTURE_INTERFACE.equals(text(typedSchema.get("futureInterface")))
            && text(receiptSchemaReport.get("sourceInterfaceSpecDigest")).equals(text(typedSchema.get("sourceInterfaceSpecDigest")))
            && text(receiptSchemaReport.get("sourceBoundaryPlanDigest")).equals(text(typedSchema.get("sourceBoundaryPlanDigest")))
            && text(receiptSchemaReport.get("sourceWriterPlanDigest")).equals(text(typedSchema.get("sourceWriterPlanDigest")))
            && text(receiptSchemaReport.get("sourceAvailabilityPlanDigest")).equals(text(typedSchema.get("sourceAvailabilityPlanDigest")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(typedSchema.get("targetStorage")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(typedSchema.get("digestAlgorithm")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE.equals(text(probeReceiptSchema.get("type")))
            && "PROBE_STORAGE".equals(text(probeReceiptSchema.get("phase")))
            && Boolean.TRUE.equals(probeReceiptSchema.get("futureOnly"))
            && Boolean.FALSE.equals(probeReceiptSchema.get("instanceAllowedNow"))
            && Boolean.FALSE.equals(probeReceiptSchema.get("sideEffectAllowedNow"))
            && FUTURE_AVAILABLE_STATUS.equals(text(probeReceiptSchema.get("requiredFutureStatus")));
    }

    private static Map<String, Object> resultContract(Map<String, Object> auditContext,
                                                      Map<String, Object> principal,
                                                      Map<String, Object> probeExecutorReport,
                                                      Map<String, Object> receiptSchemaReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contractBoundary", "SERVER_ISSUED_STORAGE_PROBE_RESULT_REQUIRED");
        contract.put("futureResultType", FUTURE_RESULT_TYPE);
        contract.put("futureProbeReceiptType", FUTURE_PROBE_RECEIPT_TYPE);
        contract.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        contract.put("targetEntity", NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY);
        contract.put("saveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        contract.put("currentInstanceAllowed", false);
        contract.put("currentProbeStatus", CURRENT_STATUS);
        contract.put("serverIssuedRequired", true);
        contract.put("callerProvidedResultAllowed", false);
        contract.put("evidenceBinding", resultEvidenceBinding(auditContext, probeExecutorReport, receiptSchemaReport));
        contract.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        contract.put("requiredFutureFields", List.of(
            "resultType",
            "probeReceiptType",
            "probeStatus",
            "storageAvailable",
            "storageProbeReceiptDigest",
            "auditEventDigest",
            "probeExecutorPlanDigest",
            "receiptSchemaDigest",
            "trustedPrincipalDigest",
            "durableAckEvidenceDigest",
            "readAfterWriteEvidenceDigest",
            "issuedAt",
            "issuedBy"
        ));
        contract.put("currentTemplate", currentTemplate());
        contract.put("passPrerequisites", passPrerequisites());
        contract.put("failureModel", failureModel());
        return contract;
    }

    private static Map<String, Object> resultEvidenceBinding(Map<String, Object> auditContext,
                                                             Map<String, Object> probeExecutorReport,
                                                             Map<String, Object> receiptSchemaReport) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("sourceAuditEventDigest", digestFor(auditContext));
        binding.put("sourceProbeExecutorPlanDigest", text(probeExecutorReport.get("probeExecutorPlanDigest")));
        binding.put("sourceReceiptSchemaDigest", text(receiptSchemaReport.get("schemaDigest")));
        binding.put("sourceInterfaceSpecDigest", text(receiptSchemaReport.get("sourceInterfaceSpecDigest")));
        binding.put("sourceBoundaryPlanDigest", text(probeExecutorReport.get("sourceBoundaryPlanDigest")));
        binding.put("sourceWriterPlanDigest", text(probeExecutorReport.get("sourceWriterPlanDigest")));
        binding.put("sourceAvailabilityPlanDigest", text(probeExecutorReport.get("sourceAvailabilityPlanDigest")));
        binding.put("trustedPrincipalDigestRequired", true);
        binding.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        binding.put("sameAuditEventRequired", true);
        binding.put("sameTrustedPrincipalRequired", true);
        binding.put("serverIssuedRequired", true);
        return binding;
    }

    private static Map<String, Object> currentTemplate() {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("resultIssued", false);
        template.put("probeStatus", CURRENT_STATUS);
        template.put("storageAvailable", false);
        template.put("storageProbeReceiptIssued", false);
        template.put("durableAckVerified", false);
        template.put("readAfterWriteVerified", false);
        template.put("preWriteAllowed", false);
        return template;
    }

    private static Map<String, Object> passPrerequisites() {
        Map<String, Object> prerequisites = new LinkedHashMap<>();
        prerequisites.put("realStorageProbeExecutedRequired", true);
        prerequisites.put("storageAvailableRequired", true);
        prerequisites.put("durableAckVerifiedRequired", true);
        prerequisites.put("readAfterWriteVerifiedRequired", true);
        prerequisites.put("sameAuditEventDigestRequired", true);
        prerequisites.put("sameTrustedPrincipalRequired", true);
        prerequisites.put("probeExecutorPlanDigestRequired", true);
        prerequisites.put("receiptSchemaDigestRequired", true);
        prerequisites.put("currentContractSatisfiesPrerequisites", false);
        return prerequisites;
    }

    private static Map<String, Object> failureModel() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("failClosed", true);
        model.put("fallbackToMockProbeAllowed", false);
        model.put("fallbackToCallerProbeResultAllowed", false);
        model.put("fallbackToSchemaOnlyAllowed", false);
        model.put("failureStatuses", List.of(
            "PROBE_RESULT_IMPLEMENTATION_HOLD",
            "STORAGE_UNAVAILABLE",
            "STORAGE_PROBE_TIMEOUT",
            "STORAGE_PROBE_AMBIGUOUS",
            "DURABLE_ACK_MISSING",
            "READ_AFTER_WRITE_MISSING",
            "AUDIT_EVENT_DIGEST_MISMATCH",
            "TRUSTED_PRINCIPAL_MISMATCH",
            "CALLER_PROBE_RESULT_REJECTED",
            "SECRET_MATERIAL_REJECTED"
        ));
        return model;
    }

    private static boolean hasOnlyExpectedProbeExecutorHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "STORAGE_PROBE_EXECUTOR_IMPLEMENTATION_HOLD".equals(text(blockers.get(0).get("code")));
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
                "STORAGE_PROBE_RESULT_INPUT_CONTAINS_FORBIDDEN_SECRET",
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
            if (value instanceof String textValue && looksLikeSecretValue(textValue)) {
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
                    if (item instanceof String textItem && looksLikeSecretValue(textItem)) {
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

    private static boolean hasForgedProbeResultClaim(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForgedProbeResultClaim(entry.getKey(), value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && hasForgedProbeResultClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && hasForgedProbeResultClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isForgedProbeResultClaim(String key, Object value) {
        return switch (key) {
            case "serverIssuedProbeResultAccepted",
                "storageProbeExecuted",
                "realStorageTouched",
                "storageAvailable",
                "durableAckVerified",
                "readAfterWriteVerified",
                "storageProbeReceiptIssued",
                "preWriteAllowed",
                "writePermitted",
                "writeExecutionAllowed",
                "realHttpExecutionAllowed",
                "durableReceiptCanBeIssued",
                "durableReceiptIssued",
                "releaseEligible",
                "durable" -> Boolean.TRUE.equals(value);
            case "probeStatus" -> Set.of("SUCCESS", FUTURE_AVAILABLE_STATUS).contains(text(value));
            case "availabilityStatus" -> "AVAILABLE".equals(text(value));
            case "receiptStatus" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(value));
            case "storageMode" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(value));
            case "probeResult",
                "storageProbeResult",
                "nimDurableAuditStorageProbeResult",
                "storageProbeReceipt" -> value != null;
            default -> false;
        };
    }

    private static Map<String, Object> forgedProbeResultClaimBlocker(String source) {
        return blocker(
            "STORAGE_PROBE_RESULT_FORGED_SUCCESS_CLAIM",
            source + " 不得自称 server-issued probe result、storage receipt、storageAvailable 或 pre-write 放行。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> probeExecutorReport,
                                                    Map<String, Object> receiptSchemaReport,
                                                    Map<String, Object> callerProbeResult) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "serverIssuedProbeResultAccepted",
            "probeResult",
            "storageProbeResult",
            "storageProbeReceipt",
            "storageProbeExecuted",
            "storageAvailable",
            "probeStatus",
            "durableAckVerified",
            "readAfterWriteVerified",
            "preWriteAllowed",
            "writePermitted",
            "writeExecutionAllowed",
            "realHttpExecutionAllowed",
            "durableReceiptCanBeIssued",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key)
                || principal.containsKey(key)
                || probeExecutorReport.containsKey(key)
                || receiptSchemaReport.containsKey(key)
                || callerProbeResult.containsKey(key)) {
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

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record StorageProbeResultInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> storageProbeExecutorReport,
        Map<String, Object> durableAuditReceiptAckSchemaReport,
        Map<String, Object> callerProbeResult
    ) {
        StorageProbeResultInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            storageProbeExecutorReport = storageProbeExecutorReport == null ? Map.of() : objectMap(storageProbeExecutorReport);
            durableAuditReceiptAckSchemaReport = durableAuditReceiptAckSchemaReport == null ? Map.of() : objectMap(durableAuditReceiptAckSchemaReport);
            callerProbeResult = callerProbeResult == null ? Map.of() : objectMap(callerProbeResult);
        }

        static StorageProbeResultInput empty() {
            return new StorageProbeResultInput(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
