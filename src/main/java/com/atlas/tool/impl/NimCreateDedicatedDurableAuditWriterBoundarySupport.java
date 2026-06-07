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
 * NIM 创建专用 durable audit writer 边界与测试替身契约。
 *
 * <p>本类仍然是纯数据、mock-first 的边界层: 不连接 Elasticsearch，不调用 {@code ISysLogService}，
 * 不写 {@code sys_log}，不访问 kube-manager，也不执行 {@code POST /api/{orgId}/deployment}。
 * 它把 M5.21-52 writer plan 与 M5.21-53 availability gate 收束成未来真实 writer 必须实现的
 * 服务端边界和测试替身约束，确保任何测试替身都不能伪造 durable receipt 或存储成功。</p>
 */
final class NimCreateDedicatedDurableAuditWriterBoundarySupport {

    static final String BOUNDARY_NAME = "NIM_CREATE_DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY";
    static final String TEST_DOUBLE_NAME = "NIM_CREATE_DEDICATED_DURABLE_AUDIT_WRITER_TEST_DOUBLE";
    static final String EXECUTION_MODE = "DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_TEST_DOUBLE_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
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

    private NimCreateDedicatedDurableAuditWriterBoundarySupport() {
    }

    static Map<String, Object> plan(DedicatedAuditWriterBoundaryInput input) {
        DedicatedAuditWriterBoundaryInput safeInput = input == null
            ? DedicatedAuditWriterBoundaryInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> writerPlanReport = safeInput.durableAuditWriterPlanReport();
        Map<String, Object> availabilityGateReport = safeInput.storageAvailabilityGateReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateWriterPlanReport(auditContext, principal, writerPlanReport, blockers);
        validateAvailabilityGateReport(auditContext, principal, writerPlanReport, availabilityGateReport, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditWriterPlanReport", writerPlanReport, blockers);
        validateNoSecretMaterial("storageAvailabilityGateReport", availabilityGateReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> writerBoundaryPlan = inputAccepted
            ? writerBoundaryPlan(auditContext, principal, writerPlanReport, availabilityGateReport)
            : Map.of();
        Map<String, Object> testDoubleContract = inputAccepted
            ? testDoubleContract(writerPlanReport, availabilityGateReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_IMPLEMENTATION_HOLD",
                "专用 durable audit writer 的真实服务端实现尚未完成；当前边界只允许测试替身验证契约，不得写入 sys_log 或签发 durable receipt。",
                "dedicated-audit-writer-boundary"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dedicatedAuditWriterBoundary", BOUNDARY_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("writerBoundaryState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("testDoubleName", TEST_DOUBLE_NAME);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("writerBoundaryPlanPrepared", inputAccepted);
        result.put("testDoubleContractPrepared", inputAccepted);
        result.put("realStorageTouched", false);
        result.put("storageProbeExecuted", false);
        result.put("storageAvailable", false);
        result.put("availabilityStatus", NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN);
        result.put("preWritePersisted", false);
        result.put("postWritePersisted", false);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("durableReceiptCanBeIssued", false);
        result.put("durableReceiptIssued", false);
        result.put("candidateIndex", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        result.put("candidateSaveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceWriterPlanDigest", text(writerPlanReport.get("writerPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(availabilityGateReport.get("availabilityPlanDigest")));
        result.put("boundaryPlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("boundaryPlanDigest", inputAccepted ? digestFor(writerBoundaryPlan) : "");
        result.put("writerBoundaryPlan", writerBoundaryPlan);
        result.put("testDoubleContract", testDoubleContract);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(
            auditContext,
            principal,
            writerPlanReport,
            availabilityGateReport
        ));
        result.put("nextImplementationRequirements", List.of(
            "create a reviewed server-side NimDurableAuditWriter boundary",
            "run storage availability probe inside that boundary before pre-write intent",
            "persist pre-write intent and post-write result through the dedicated writer only",
            "return durable receipt only after storage probe, pre-write durable ack and post-write durable ack",
            "keep unit test doubles fail-closed and unable to claim real storage success"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_DEDICATED_WRITER_BOUNDARY",
                "dedicated audit writer boundary 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
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
                "dedicated audit writer boundary 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedSuccessClaim(principal)) {
            blockers.add(forgedSuccessClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateWriterPlanReport(Map<String, Object> auditContext,
                                                 Map<String, Object> principal,
                                                 Map<String, Object> writerPlanReport,
                                                 List<Map<String, Object>> blockers) {
        if (writerPlanReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_WRITER_PLAN_REPORT_NOT_READY",
                "缺少 M5.21-52 durable audit writer plan 报告；不能规划专用 writer 边界。",
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
                "DURABLE_AUDIT_WRITER_PLAN_REPORT_INVALID_FOR_DEDICATED_BOUNDARY",
                "dedicated audit writer boundary 只能消费 M5.21-52 产生的、仍处于 HOLD 的两阶段 writer plan。",
                "durable-audit-writer-plan"
            ));
        }
        if (hasForgedSuccessClaim(writerPlanReport)) {
            blockers.add(forgedSuccessClaimBlocker("durableAuditWriterPlanReport"));
        }
    }

    private static void validateAvailabilityGateReport(Map<String, Object> auditContext,
                                                       Map<String, Object> principal,
                                                       Map<String, Object> writerPlanReport,
                                                       Map<String, Object> availabilityGateReport,
                                                       List<Map<String, Object>> blockers) {
        if (availabilityGateReport.isEmpty()) {
            blockers.add(blocker(
                "STORAGE_AVAILABILITY_GATE_REPORT_NOT_READY",
                "缺少 M5.21-53 storage availability gate 报告；不能规划专用 writer 边界。",
                "storage-availability-gate"
            ));
            return;
        }

        Map<String, Object> availabilityPlan = objectMap(availabilityGateReport.get("availabilityPlan"));
        boolean valid = NimCreateDurableAuditStorageAvailabilityGateSupport.GATE_NAME.equals(text(availabilityGateReport.get("durableAuditStorageAvailabilityGate")))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.EXECUTION_MODE.equals(text(availabilityGateReport.get("executionMode")))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.HOLD_STATE.equals(text(availabilityGateReport.get("gateState")))
            && "NOT_PERFORMED".equals(text(availabilityGateReport.get("networkAccess")))
            && "NONE".equals(text(availabilityGateReport.get("sideEffect")))
            && Boolean.TRUE.equals(availabilityGateReport.get("inputAccepted"))
            && Boolean.TRUE.equals(availabilityGateReport.get("availabilityPlanPrepared"))
            && Boolean.TRUE.equals(availabilityGateReport.get("requiredBeforePreWrite"))
            && Boolean.FALSE.equals(availabilityGateReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(availabilityGateReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(availabilityGateReport.get("storageAvailable"))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN.equals(text(availabilityGateReport.get("availabilityStatus")))
            && Boolean.FALSE.equals(availabilityGateReport.get("durable"))
            && Boolean.FALSE.equals(availabilityGateReport.get("releaseEligible"))
            && Boolean.FALSE.equals(availabilityGateReport.get("durableReceiptCanBeIssued"))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(availabilityGateReport.get("candidateIndex")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(availabilityGateReport.get("candidateSaveService")))
            && text(availabilityGateReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(availabilityGateReport.get("sourceWriterPlanDigest")).equals(text(writerPlanReport.get("writerPlanDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(availabilityGateReport.get("availabilityPlanDigestAlgorithm")))
            && text(availabilityGateReport.get("availabilityPlanDigest")).matches("[a-f0-9]{64}")
            && text(availabilityGateReport.get("availabilityPlanDigest")).equals(digestFor(availabilityPlan))
            && hasOnlyExpectedAvailabilityGateHold(availabilityGateReport.get("blockedBy"))
            && availabilityPlanContractValid(auditContext, principal, writerPlanReport, availabilityPlan);

        if (!valid) {
            blockers.add(blocker(
                "STORAGE_AVAILABILITY_GATE_REPORT_INVALID_FOR_DEDICATED_BOUNDARY",
                "dedicated audit writer boundary 只能消费 M5.21-53 产生的、仍未执行真实 probe 且 storageAvailable=false 的 gate report。",
                "storage-availability-gate"
            ));
        }
        if (hasForgedSuccessClaim(availabilityGateReport)) {
            blockers.add(forgedSuccessClaimBlocker("storageAvailabilityGateReport"));
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

    private static boolean availabilityPlanContractValid(Map<String, Object> auditContext,
                                                         Map<String, Object> principal,
                                                         Map<String, Object> writerPlanReport,
                                                         Map<String, Object> availabilityPlan) {
        Map<String, Object> identity = objectMap(availabilityPlan.get("trustedIdentityBinding"));
        Map<String, Object> failurePolicy = objectMap(availabilityPlan.get("failurePolicy"));
        Map<String, Object> prerequisites = objectMap(availabilityPlan.get("receiptPrerequisites"));

        return !availabilityPlan.isEmpty()
            && "DEDICATED_NIM_STORAGE_AVAILABILITY_GATE_REQUIRED".equals(text(availabilityPlan.get("gateBoundary")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(availabilityPlan.get("targetStorage")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY.equals(text(availabilityPlan.get("targetEntity")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(availabilityPlan.get("saveService")))
            && "FUTURE_SERVER_SIDE_PROBE_ONLY".equals(text(availabilityPlan.get("probeMode")))
            && Boolean.TRUE.equals(availabilityPlan.get("requiredBeforePreWrite"))
            && text(writerPlanReport.get("writerPlanDigest")).equals(text(availabilityPlan.get("sourceWriterPlanDigest")))
            && digestFor(auditContext).equals(text(availabilityPlan.get("sourceAuditEventDigest")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && probeStepsContractValid(availabilityPlan.get("probeSteps"))
            && failurePolicyContractValid(failurePolicy)
            && receiptPrerequisitesContractValid(prerequisites);
    }

    private static boolean probeStepsContractValid(Object rawSteps) {
        List<Map<String, Object>> steps = listOfMaps(rawSteps);
        if (steps.size() != 4) {
            return false;
        }
        Set<String> ids = Set.of(
            text(steps.get(0).get("id")),
            text(steps.get(1).get("id")),
            text(steps.get(2).get("id")),
            text(steps.get(3).get("id"))
        );
        return ids.contains("verify-storage-client-enabled")
            && ids.contains("verify-sys-log-index-resolvable")
            && ids.contains("verify-dedicated-writer-sanitized-record-contract")
            && ids.contains("verify-durable-ack-or-read-after-write")
            && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("required")))
            && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed")))
            && steps.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow")));
    }

    private static boolean failurePolicyContractValid(Map<String, Object> policy) {
        return !policy.isEmpty()
            && Boolean.TRUE.equals(policy.get("failClosed"))
            && Boolean.TRUE.equals(policy.get("blockReceiptWhenUnavailable"))
            && Boolean.TRUE.equals(policy.get("blockReceiptWhenTimeout"))
            && Boolean.TRUE.equals(policy.get("blockReceiptWhenAmbiguous"))
            && Boolean.TRUE.equals(policy.get("blockReceiptWhenReadAfterWriteMissing"))
            && Boolean.FALSE.equals(policy.get("fallbackToMockReceiptAllowed"))
            && Boolean.FALSE.equals(policy.get("fallbackToCandidateReportAllowed"));
    }

    private static boolean receiptPrerequisitesContractValid(Map<String, Object> prerequisites) {
        return !prerequisites.isEmpty()
            && Boolean.TRUE.equals(prerequisites.get("storageAvailableRequired"))
            && Boolean.TRUE.equals(prerequisites.get("preWriteDurableAckRequired"))
            && Boolean.TRUE.equals(prerequisites.get("postWriteDurableAckRequired"))
            && Boolean.TRUE.equals(prerequisites.get("sameAuditEventDigestRequired"))
            && Boolean.FALSE.equals(prerequisites.get("currentPlanSatisfiesPrerequisites"))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(prerequisites.get("requiredFutureReceiptStatus")))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(prerequisites.get("requiredFutureStorageMode")));
    }

    private static Map<String, Object> writerBoundaryPlan(Map<String, Object> auditContext,
                                                          Map<String, Object> principal,
                                                          Map<String, Object> writerPlanReport,
                                                          Map<String, Object> availabilityGateReport) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("boundaryRequirement", "SERVER_SIDE_DEDICATED_DURABLE_AUDIT_WRITER_REQUIRED");
        plan.put("futureInterface", "NimDurableAuditWriter");
        plan.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        plan.put("targetEntity", NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY);
        plan.put("saveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        plan.put("writeMode", "PROBE_THEN_PRE_WRITE_THEN_POST_WRITE");
        plan.put("evidenceBinding", evidenceBinding(auditContext, writerPlanReport, availabilityGateReport));
        plan.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        plan.put("operationOrder", operationOrder());
        plan.put("currentImplementationState", currentImplementationState());
        plan.put("receiptReleaseRule", receiptReleaseRule());
        plan.put("testDoubleContractName", TEST_DOUBLE_NAME);
        return plan;
    }

    private static Map<String, Object> evidenceBinding(Map<String, Object> auditContext,
                                                       Map<String, Object> writerPlanReport,
                                                       Map<String, Object> availabilityGateReport) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("sourceAuditEventDigest", digestFor(auditContext));
        binding.put("sourceWriterPlanDigest", text(writerPlanReport.get("writerPlanDigest")));
        binding.put("sourceAvailabilityPlanDigest", text(availabilityGateReport.get("availabilityPlanDigest")));
        binding.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        binding.put("writerPlanRequiredState", NimCreateDurableAuditWriterPlanSupport.HOLD_STATE);
        binding.put("availabilityGateRequiredState", NimCreateDurableAuditStorageAvailabilityGateSupport.HOLD_STATE);
        return binding;
    }

    private static List<Map<String, Object>> operationOrder() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(operationStep(
            "validate-boundary-inputs",
            "Validate trusted audit context, server principal, writer plan and storage availability gate digests"
        ));
        steps.add(operationStep(
            "probe-storage-availability",
            "Future server-side probe must prove sys_log persistence availability before any pre-write intent"
        ));
        steps.add(operationStep(
            "persist-pre-write-intent",
            "Future writer must durably persist sanitized pre-write intent before POST"
        ));
        steps.add(operationStep(
            "persist-post-write-result",
            "Future writer must durably persist sanitized post-write result after POST outcome"
        ));
        steps.add(operationStep(
            "assemble-durable-receipt",
            "Future receipt can be issued only after probe, pre-write ack and post-write ack are all durable"
        ));
        return steps;
    }

    private static Map<String, Object> operationStep(String id, String description) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("id", id);
        step.put("description", description);
        step.put("futureOnly", true);
        step.put("sideEffectAllowedNow", false);
        step.put("failClosed", true);
        return step;
    }

    private static Map<String, Object> currentImplementationState() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("boundaryImplemented", false);
        state.put("storageProbeExecuted", false);
        state.put("storageAvailable", false);
        state.put("preWritePersisted", false);
        state.put("postWritePersisted", false);
        state.put("durableReceiptCanBeIssued", false);
        return state;
    }

    private static Map<String, Object> receiptReleaseRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("currentBoundaryCanIssueReceipt", false);
        rule.put("storageAvailableRequired", true);
        rule.put("preWriteDurableAckRequired", true);
        rule.put("postWriteDurableAckRequired", true);
        rule.put("sameAuditEventDigestRequired", true);
        rule.put("requiredFutureReceiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS);
        rule.put("requiredFutureStorageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE);
        rule.put("mockReceiptAllowed", false);
        return rule;
    }

    private static Map<String, Object> testDoubleContract(Map<String, Object> writerPlanReport,
                                                          Map<String, Object> availabilityGateReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("testDoubleName", TEST_DOUBLE_NAME);
        contract.put("scope", "UNIT_CONTRACT_ONLY");
        contract.put("sourceWriterPlanDigest", text(writerPlanReport.get("writerPlanDigest")));
        contract.put("sourceAvailabilityPlanDigest", text(availabilityGateReport.get("availabilityPlanDigest")));
        contract.put("networkAccess", "NOT_PERFORMED");
        contract.put("sideEffect", "NONE");
        contract.put("realStorageTouched", false);
        contract.put("storageProbeExecuted", false);
        contract.put("storageAvailable", false);
        contract.put("preWritePersisted", false);
        contract.put("postWritePersisted", false);
        contract.put("durableReceiptCanBeIssued", false);
        contract.put("allowedAssertions", List.of(
            "input contract accepted or rejected",
            "future operation order",
            "digest and trusted identity binding",
            "fail-closed blockers"
        ));
        contract.put("forbiddenAssertions", List.of(
            "storageAvailable=true",
            "storageProbeExecuted=true",
            "preWritePersisted=true",
            "postWritePersisted=true",
            "receiptStatus=DURABLE_RECORDED",
            "storageMode=DURABLE_AUDIT_LOG",
            "realStorageTouched=true"
        ));
        contract.put("failureScenariosRequired", List.of(
            "missing writer plan report",
            "missing storage availability gate report",
            "forged storage/persistence/receipt success claim",
            "secret-shaped input material"
        ));
        return contract;
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

    private static boolean hasOnlyExpectedAvailabilityGateHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "STORAGE_AVAILABILITY_PROBE_IMPLEMENTATION_HOLD".equals(text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DEDICATED_AUDIT_WRITER_BOUNDARY_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForbiddenSecretKey(entry.getKey()) && hasText(value)) {
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

    private static boolean hasForgedSuccessClaim(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForgedSuccessClaim(entry.getKey(), value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && hasForgedSuccessClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && hasForgedSuccessClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isForgedSuccessClaim(String key, Object value) {
        return switch (key) {
            case "storageProbeExecuted",
                "storageAvailable",
                "preWritePersisted",
                "postWritePersisted",
                "preWriteDurable",
                "postWriteDurable",
                "durableReceiptCanBeIssued",
                "durableReceiptIssued",
                "releaseEligible",
                "realStorageTouched",
                "durable" -> Boolean.TRUE.equals(value);
            case "availabilityStatus" -> "AVAILABLE".equals(text(value));
            case "receiptStatus" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(value));
            case "storageMode" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(value));
            default -> false;
        };
    }

    private static Map<String, Object> forgedSuccessClaimBlocker(String source) {
        return blocker(
            "DEDICATED_AUDIT_WRITER_BOUNDARY_FORGED_SUCCESS_CLAIM",
            source + " 不得自称 storageAvailable、preWritePersisted、postWritePersisted、DURABLE_RECORDED 或可签发 receipt；这些只能来自未来真实 writer。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> writerPlanReport,
                                                    Map<String, Object> availabilityGateReport) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "storageProbeExecuted",
            "storageAvailable",
            "availabilityStatus",
            "preWritePersisted",
            "postWritePersisted",
            "preWriteDurable",
            "postWriteDurable",
            "durableReceiptCanBeIssued",
            "durableReceiptIssued",
            "releaseEligible",
            "realStorageTouched",
            "durable",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key)
                || principal.containsKey(key)
                || writerPlanReport.containsKey(key)
                || availabilityGateReport.containsKey(key)) {
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

    record DedicatedAuditWriterBoundaryInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditWriterPlanReport,
        Map<String, Object> storageAvailabilityGateReport
    ) {
        DedicatedAuditWriterBoundaryInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditWriterPlanReport = durableAuditWriterPlanReport == null ? Map.of() : objectMap(durableAuditWriterPlanReport);
            storageAvailabilityGateReport = storageAvailabilityGateReport == null ? Map.of() : objectMap(storageAvailabilityGateReport);
        }

        static DedicatedAuditWriterBoundaryInput empty() {
            return new DedicatedAuditWriterBoundaryInput(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
