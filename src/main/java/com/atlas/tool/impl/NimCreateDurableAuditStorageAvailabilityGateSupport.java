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
 * NIM 创建 durable audit storage 可用性门禁计划契约。
 *
 * <p>本类仍然不做真实探测: 不连接 Elasticsearch，不调用 {@code ISysLogService}，不写 {@code sys_log}。
 * 它的职责是把 M5.21-52 的 writer plan 进一步收敛成未来真实存储探测必须满足的门禁计划，
 * 并明确当前计划不能证明 storage available，也不能签发 durable receipt。</p>
 */
final class NimCreateDurableAuditStorageAvailabilityGateSupport {

    static final String GATE_NAME = "NIM_CREATE_DURABLE_AUDIT_STORAGE_AVAILABILITY_GATE";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_STORAGE_AVAILABILITY_GATE_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String AVAILABILITY_STATUS_UNKNOWN = "UNKNOWN_UNTIL_REAL_PROBE";

    private NimCreateDurableAuditStorageAvailabilityGateSupport() {
    }

    static Map<String, Object> plan(StorageAvailabilityGateInput input) {
        StorageAvailabilityGateInput safeInput = input == null ? StorageAvailabilityGateInput.empty() : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = safeInput.durableAuditWriterPlanReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateWriterPlanReport(auditContext, principal, writerPlanReport, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditWriterPlanReport", writerPlanReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> availabilityPlan = inputAccepted
            ? availabilityPlan(auditContext, principal, writerPlanReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "STORAGE_AVAILABILITY_PROBE_IMPLEMENTATION_HOLD",
                "真实 durable audit storage 可用性探测尚未实现；当前计划不能证明 sys_log 可写，也不能签发 durable receipt。",
                "storage-availability"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditStorageAvailabilityGate", GATE_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("gateState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("availabilityPlanPrepared", inputAccepted);
        result.put("requiredBeforePreWrite", true);
        result.put("storageProbeExecuted", false);
        result.put("realStorageTouched", false);
        result.put("storageAvailable", false);
        result.put("availabilityStatus", AVAILABILITY_STATUS_UNKNOWN);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("durableReceiptCanBeIssued", false);
        result.put("candidateIndex", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        result.put("candidateSaveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceWriterPlanDigest", text(writerPlanReport.get("writerPlanDigest")));
        result.put("availabilityPlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("availabilityPlanDigest", inputAccepted ? digestFor(availabilityPlan) : "");
        result.put("availabilityPlan", availabilityPlan);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(auditContext, principal, writerPlanReport));
        result.put("nextImplementationRequirements", List.of(
            "implement a server-side storage availability probe inside the dedicated audit writer boundary",
            "verify Elasticsearch/sys_log persistence is enabled before pre-write intent",
            "verify sanitized pre-write intent can receive a durable acknowledgement",
            "fail closed when storage is disabled, ambiguous, timeout, or read-after-write verification fails",
            "allow durable receipt only after availability probe and both audit records succeed"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_STORAGE_AVAILABILITY_GATE",
                "storage availability gate 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedAvailabilityClaim(auditContext)) {
            blockers.add(forgedAvailabilityClaimBlocker("auditContext"));
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
                "storage availability gate 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedAvailabilityClaim(principal)) {
            blockers.add(forgedAvailabilityClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateWriterPlanReport(Map<String, Object> auditContext,
                                                 Map<String, Object> principal,
                                                 Map<String, Object> writerPlanReport,
                                                 List<Map<String, Object>> blockers) {
        if (writerPlanReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_WRITER_PLAN_REPORT_NOT_READY",
                "缺少 M5.21-52 durable audit writer plan 报告；不能规划 storage availability gate。",
                "durable-audit-writer-plan"
            ));
            return;
        }

        Map<String, Object> writerPlan = objectMap(writerPlanReport.get("writerPlan"));
        boolean valid = NimCreateDurableAuditWriterPlanSupport.WRITER_PLAN_NAME.equals(text(writerPlanReport.get("durableAuditWriterPlan")))
            && NimCreateDurableAuditWriterPlanSupport.EXECUTION_MODE.equals(text(writerPlanReport.get("executionMode")))
            && NimCreateDurableAuditWriterPlanSupport.HOLD_STATE.equals(text(writerPlanReport.get("writerState")))
            && "NOT_PERFORMED".equals(text(writerPlanReport.get("networkAccess")))
            && "NONE".equals(text(writerPlanReport.get("sideEffect")))
            && Boolean.TRUE.equals(writerPlanReport.get("inputAccepted"))
            && Boolean.TRUE.equals(writerPlanReport.get("writerPlanPrepared"))
            && Boolean.TRUE.equals(writerPlanReport.get("storageAvailabilityGateRequired"))
            && Boolean.FALSE.equals(writerPlanReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(writerPlanReport.get("durable"))
            && Boolean.FALSE.equals(writerPlanReport.get("releaseEligible"))
            && Boolean.FALSE.equals(writerPlanReport.get("durableReceiptCanBeIssued"))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(writerPlanReport.get("candidateIndex")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(writerPlanReport.get("candidateSaveService")))
            && text(writerPlanReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(writerPlanReport.get("writerPlanDigestAlgorithm")))
            && text(writerPlanReport.get("writerPlanDigest")).matches("[a-f0-9]{64}")
            && text(writerPlanReport.get("writerPlanDigest")).equals(digestFor(writerPlan))
            && hasExpectedWriterPlanHolds(writerPlanReport.get("blockedBy"))
            && writerPlanContractValid(auditContext, principal, writerPlan);

        if (!valid) {
            blockers.add(blocker(
                "DURABLE_AUDIT_WRITER_PLAN_REPORT_INVALID_FOR_STORAGE_AVAILABILITY_GATE",
                "storage availability gate 只能消费 M5.21-52 产生的、仍处于 HOLD 的两阶段 writer plan。",
                "durable-audit-writer-plan"
            ));
        }
        if (hasForgedAvailabilityClaim(writerPlanReport)) {
            blockers.add(forgedAvailabilityClaimBlocker("durableAuditWriterPlanReport"));
        }
    }

    private static boolean writerPlanContractValid(Map<String, Object> auditContext,
                                                   Map<String, Object> principal,
                                                   Map<String, Object> writerPlan) {
        Map<String, Object> storageGate = objectMap(writerPlan.get("storageAvailabilityGate"));
        Map<String, Object> identity = objectMap(writerPlan.get("trustedIdentityBinding"));
        Map<String, Object> preWrite = objectMap(writerPlan.get("preWriteRecordTemplate"));
        Map<String, Object> postWrite = objectMap(writerPlan.get("postWriteRecordTemplate"));
        Map<String, Object> receiptRule = objectMap(writerPlan.get("receiptIssuanceRule"));
        String eventDigest = digestFor(auditContext);

        return !writerPlan.isEmpty()
            && "DEDICATED_NIM_DURABLE_AUDIT_WRITER_REQUIRED".equals(text(writerPlan.get("writerBoundary")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(writerPlan.get("targetStorage")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(writerPlan.get("saveService")))
            && "PRE_WRITE_INTENT_AND_POST_WRITE_RESULT".equals(text(writerPlan.get("writeMode")))
            && Boolean.TRUE.equals(storageGate.get("required"))
            && Boolean.TRUE.equals(storageGate.get("probeBeforePreWrite"))
            && Boolean.TRUE.equals(storageGate.get("failClosedWhenStorageUnavailable"))
            && Boolean.TRUE.equals(storageGate.get("elasticsearchDisabledMustBlockReceipt"))
            && Boolean.FALSE.equals(storageGate.get("candidateReportAloneCanIssueReceipt"))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && recordTemplateContractValid(preWrite, NimCreateDurableAuditWriterPlanSupport.PRE_WRITE_RECORD_TYPE, "PRE_WRITE", auditContext, principal, eventDigest)
            && recordTemplateContractValid(postWrite, NimCreateDurableAuditWriterPlanSupport.POST_WRITE_RECORD_TYPE, "POST_WRITE", auditContext, principal, eventDigest)
            && Boolean.FALSE.equals(preWrite.get("realStorageTouched"))
            && Boolean.FALSE.equals(postWrite.get("realStorageTouched"))
            && Boolean.FALSE.equals(receiptRule.get("durableReceiptCanBeIssuedNow"))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(receiptRule.get("requiredFutureReceiptStatus")))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(receiptRule.get("requiredFutureStorageMode")))
            && Boolean.TRUE.equals(receiptRule.get("preWriteRecordRequired"))
            && Boolean.TRUE.equals(receiptRule.get("postWriteRecordRequired"))
            && Boolean.TRUE.equals(receiptRule.get("bothRecordsMustBeDurable"))
            && Boolean.TRUE.equals(receiptRule.get("recordDigestsMustBindSameAuditEvent"))
            && Boolean.TRUE.equals(receiptRule.get("candidateReportAloneIsInsufficient"));
    }

    private static boolean recordTemplateContractValid(Map<String, Object> record,
                                                       String expectedRecordType,
                                                       String expectedPhase,
                                                       Map<String, Object> auditContext,
                                                       Map<String, Object> principal,
                                                       String eventDigest) {
        return !record.isEmpty()
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(record.get("targetStorage")))
            && Integer.valueOf(text(auditContext.get("organizationId"))).equals(record.get("organizationId"))
            && text(principal.get("username")).equals(text(record.get("username")))
            && "NIM_CREATE_AUDIT".equals(text(record.get("module")))
            && ("/api/" + text(auditContext.get("organizationId")) + "/deployment").equals(text(record.get("uri")))
            && text(auditContext.get("requestId")).equals(text(record.get("requestId")))
            && text(auditContext.get("conversationId")).equals(text(record.get("conversationId")))
            && text(auditContext.get("userId")).equals(text(record.get("userId")))
            && expectedRecordType.equals(text(record.get("recordType")))
            && expectedPhase.equals(text(record.get("phase")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(record.get("eventDigestAlgorithm")))
            && eventDigest.equals(text(record.get("eventDigest")));
    }

    private static Map<String, Object> availabilityPlan(Map<String, Object> auditContext,
                                                        Map<String, Object> principal,
                                                        Map<String, Object> writerPlanReport) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("gateBoundary", "DEDICATED_NIM_STORAGE_AVAILABILITY_GATE_REQUIRED");
        plan.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        plan.put("targetEntity", NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY);
        plan.put("saveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        plan.put("probeMode", "FUTURE_SERVER_SIDE_PROBE_ONLY");
        plan.put("requiredBeforePreWrite", true);
        plan.put("sourceWriterPlanDigest", text(writerPlanReport.get("writerPlanDigest")));
        plan.put("sourceAuditEventDigest", digestFor(auditContext));
        plan.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        plan.put("probeSteps", probeSteps());
        plan.put("failurePolicy", failurePolicy());
        plan.put("receiptPrerequisites", receiptPrerequisites());
        return plan;
    }

    private static List<Map<String, Object>> probeSteps() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(probeStep(
            "verify-storage-client-enabled",
            "Confirm the server-side sys_log persistence client is configured and enabled",
            "SERVER_CONFIG",
            true
        ));
        steps.add(probeStep(
            "verify-sys-log-index-resolvable",
            "Confirm sys_log index or backing storage is reachable before pre-write intent",
            NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX,
            true
        ));
        steps.add(probeStep(
            "verify-dedicated-writer-sanitized-record-contract",
            "Confirm only sanitized pre-write/post-write record summaries can be persisted",
            "DEDICATED_NIM_AUDIT_WRITER",
            true
        ));
        steps.add(probeStep(
            "verify-durable-ack-or-read-after-write",
            "Confirm storage returns a durable acknowledgement or read-after-write evidence",
            "DURABLE_ACK",
            true
        ));
        return steps;
    }

    private static Map<String, Object> probeStep(String id,
                                                 String description,
                                                 String target,
                                                 boolean failClosed) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("description", description);
        step.put("target", target);
        step.put("required", true);
        step.put("failClosed", failClosed);
        step.put("sideEffectAllowedNow", false);
        return step;
    }

    private static Map<String, Object> failurePolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("failClosed", true);
        policy.put("blockReceiptWhenUnavailable", true);
        policy.put("blockReceiptWhenTimeout", true);
        policy.put("blockReceiptWhenAmbiguous", true);
        policy.put("blockReceiptWhenReadAfterWriteMissing", true);
        policy.put("fallbackToMockReceiptAllowed", false);
        policy.put("fallbackToCandidateReportAllowed", false);
        return policy;
    }

    private static Map<String, Object> receiptPrerequisites() {
        Map<String, Object> prerequisites = new LinkedHashMap<>();
        prerequisites.put("storageAvailableRequired", true);
        prerequisites.put("preWriteDurableAckRequired", true);
        prerequisites.put("postWriteDurableAckRequired", true);
        prerequisites.put("sameAuditEventDigestRequired", true);
        prerequisites.put("currentPlanSatisfiesPrerequisites", false);
        prerequisites.put("requiredFutureReceiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS);
        prerequisites.put("requiredFutureStorageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE);
        return prerequisites;
    }

    private static boolean hasExpectedWriterPlanHolds(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        if (blockers.size() != 2) {
            return false;
        }
        Set<String> codes = Set.of(
            text(blockers.get(0).get("code")),
            text(blockers.get(1).get("code"))
        );
        return codes.contains("DURABLE_AUDIT_STORAGE_CANDIDATE_IMPLEMENTATION_HOLD")
            && codes.contains("DURABLE_AUDIT_WRITER_IMPLEMENTATION_HOLD");
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "STORAGE_AVAILABILITY_GATE_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        return NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            map,
            NimForbiddenSecretMaterialDetector.textValuePolicy()
        );
    }

    private static boolean hasForgedAvailabilityClaim(Map<String, Object> map) {
        return Boolean.TRUE.equals(map.get("storageProbeExecuted"))
            || Boolean.TRUE.equals(map.get("storageAvailable"))
            || "AVAILABLE".equals(text(map.get("availabilityStatus")))
            || Boolean.TRUE.equals(map.get("durableReceiptCanBeIssued"))
            || Boolean.TRUE.equals(map.get("releaseEligible"))
            || Boolean.TRUE.equals(map.get("realStorageTouched"))
            || Boolean.TRUE.equals(map.get("durable"))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(map.get("receiptStatus")))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(map.get("storageMode")));
    }

    private static Map<String, Object> forgedAvailabilityClaimBlocker(String source) {
        return blocker(
            "STORAGE_AVAILABILITY_GATE_FORGED_SUCCESS_CLAIM",
            source + " 不得自称 storageAvailable、storageProbeExecuted、DURABLE_RECORDED 或可签发 receipt；这些只能来自未来真实探测。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> writerPlanReport) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "storageProbeExecuted",
            "storageAvailable",
            "availabilityStatus",
            "durableReceiptCanBeIssued",
            "releaseEligible",
            "realStorageTouched",
            "durable",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key) || principal.containsKey(key) || writerPlanReport.containsKey(key)) {
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

    record StorageAvailabilityGateInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditWriterPlanReport
    ) {
        StorageAvailabilityGateInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditWriterPlanReport = durableAuditWriterPlanReport == null ? Map.of() : objectMap(durableAuditWriterPlanReport);
        }

        static StorageAvailabilityGateInput empty() {
            return new StorageAvailabilityGateInput(Map.of(), Map.of(), Map.of());
        }
    }
}
