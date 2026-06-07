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
 * NIM 创建 durable audit storage 探测执行器契约壳。
 *
 * <p>本类仍然是 contract-only 的纯数据边界：当前不做真实探测，不写真实存储，不签发 durable ack。
 * 它把 M5.21-53 availability gate 和 M5.21-54 dedicated writer boundary 绑定成未来探测执行器必须满足的
 * 输入契约、执行顺序和 fail-closed 规则，避免任何 mock 或调用方快照提前声称 storage available。</p>
 */
final class NimCreateDurableAuditStorageProbeExecutorSupport {

    static final String EXECUTOR_NAME = "NIM_CREATE_DURABLE_AUDIT_STORAGE_PROBE_EXECUTOR";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_STORAGE_PROBE_EXECUTOR_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";

    private NimCreateDurableAuditStorageProbeExecutorSupport() {
    }

    static Map<String, Object> plan(StorageProbeExecutorInput input) {
        StorageProbeExecutorInput safeInput = input == null ? StorageProbeExecutorInput.empty() : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> availabilityGateReport = safeInput.storageAvailabilityGateReport();
        Map<String, Object> writerBoundaryReport = safeInput.dedicatedAuditWriterBoundaryReport();
        Map<String, Object> probeExecutionSnapshot = safeInput.probeExecutionSnapshot();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateAvailabilityGateReport(auditContext, principal, availabilityGateReport, blockers);
        validateWriterBoundaryReport(auditContext, principal, availabilityGateReport, writerBoundaryReport, blockers);
        validateProbeExecutionSnapshot(probeExecutionSnapshot, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("storageAvailabilityGateReport", availabilityGateReport, blockers);
        validateNoSecretMaterial("dedicatedAuditWriterBoundaryReport", writerBoundaryReport, blockers);
        validateNoSecretMaterial("probeExecutionSnapshot", probeExecutionSnapshot, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> probeExecutorPlan = inputAccepted
            ? probeExecutorPlan(auditContext, principal, availabilityGateReport, writerBoundaryReport, probeExecutionSnapshot)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "STORAGE_PROBE_EXECUTOR_IMPLEMENTATION_HOLD",
                "真实 durable audit storage probe executor 尚未实现；当前契约不能探测真实存储，也不能允许 pre-write 或签发 receipt。",
                "storage-probe-executor"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditStorageProbeExecutor", EXECUTOR_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("probeExecutorState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("probeState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("springBeanRegistered", false);
        result.put("httpClientBound", false);
        result.put("storageClientBound", false);
        result.put("inputAccepted", inputAccepted);
        result.put("probeExecutorPlanPrepared", inputAccepted);
        result.put("probeAttemptSpecPrepared", inputAccepted);
        result.put("diagnosticProbeSnapshotObserved", !probeExecutionSnapshot.isEmpty());
        result.put("diagnosticProbeSnapshotAuthoritative", false);
        result.put("requiredInsideDedicatedWriterBoundary", true);
        result.put("requiredBeforePreWrite", true);
        result.put("storageProbeExecuted", false);
        result.put("probeAttempted", false);
        result.put("realStorageTouched", false);
        result.put("storageAvailable", false);
        result.put("availabilityStatus", NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN);
        result.put("durableAckVerified", false);
        result.put("durableAckReceived", false);
        result.put("durableAckObserved", false);
        result.put("readAfterWriteVerified", false);
        result.put("preWriteAllowed", false);
        result.put("preWritePersisted", false);
        result.put("postWritePersisted", false);
        result.put("writePermitted", false);
        result.put("writeExecutionAllowed", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("storageProbeReceiptIssued", false);
        result.put("durableReceiptCanBeIssued", false);
        result.put("durableReceiptIssued", false);
        result.put("candidateIndex", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        result.put("candidateSaveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceWriterPlanDigest", text(availabilityGateReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(availabilityGateReport.get("availabilityPlanDigest")));
        result.put("sourceBoundaryPlanDigest", text(writerBoundaryReport.get("boundaryPlanDigest")));
        result.put("probeExecutorPlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("probeExecutorPlanDigest", inputAccepted ? digestFor(probeExecutorPlan) : "");
        result.put("probeExecutorPlan", probeExecutorPlan);
        result.put("probeAttemptSpec", probeExecutorPlan);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(
            auditContext,
            principal,
            availabilityGateReport,
            writerBoundaryReport,
            probeExecutionSnapshot
        ));
        result.put("nextImplementationRequirements", List.of(
            "implement a reviewed server-side NimDurableAuditStorageProbeExecutor inside the dedicated audit writer boundary",
            "prove storage client readiness and target index reachability before any pre-write intent",
            "verify durable acknowledgement and read-after-write evidence with the same audit event digest",
            "fail closed on unavailable, timeout, ambiguous ack, missing read-after-write, digest mismatch, principal mismatch, or caller success claims",
            "allow pre-write and durable receipt only after a real server-issued probe result passes review"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_STORAGE_PROBE_EXECUTOR",
                "storage probe executor 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (containsForgedProbeSuccessClaim(auditContext)) {
            blockers.add(forgedProbeSuccessClaimBlocker("auditContext"));
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
                "storage probe executor 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (containsForgedProbeSuccessClaim(principal)) {
            blockers.add(forgedProbeSuccessClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateAvailabilityGateReport(Map<String, Object> auditContext,
                                                       Map<String, Object> principal,
                                                       Map<String, Object> availabilityGateReport,
                                                       List<Map<String, Object>> blockers) {
        if (availabilityGateReport.isEmpty()) {
            blockers.add(blocker(
                "STORAGE_AVAILABILITY_GATE_REPORT_NOT_READY",
                "缺少 M5.21-53 storage availability gate 报告；不能规划 storage probe executor。",
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
            && text(availabilityGateReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(availabilityGateReport.get("availabilityPlanDigestAlgorithm")))
            && text(availabilityGateReport.get("availabilityPlanDigest")).matches("[a-f0-9]{64}")
            && text(availabilityGateReport.get("availabilityPlanDigest")).equals(digestFor(availabilityPlan))
            && hasOnlyExpectedAvailabilityGateHold(availabilityGateReport.get("blockedBy"))
            && availabilityPlanContractValid(auditContext, principal, availabilityGateReport, availabilityPlan);

        if (!valid) {
            blockers.add(blocker(
                "STORAGE_AVAILABILITY_GATE_REPORT_INVALID_FOR_PROBE_EXECUTOR",
                "storage probe executor 只能消费 M5.21-53 产生的、仍为 HOLD 且未执行真实 probe 的 availability gate report。",
                "storage-availability-gate"
            ));
        }
        if (containsForgedProbeSuccessClaim(availabilityGateReport)) {
            blockers.add(forgedProbeSuccessClaimBlocker("storageAvailabilityGateReport"));
        }
    }

    private static void validateWriterBoundaryReport(Map<String, Object> auditContext,
                                                     Map<String, Object> principal,
                                                     Map<String, Object> availabilityGateReport,
                                                     Map<String, Object> writerBoundaryReport,
                                                     List<Map<String, Object>> blockers) {
        if (writerBoundaryReport.isEmpty()) {
            blockers.add(blocker(
                "DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_NOT_READY",
                "缺少 M5.21-54 dedicated audit writer boundary 报告；storage probe executor 不能脱离 writer 边界存在。",
                "dedicated-audit-writer-boundary"
            ));
            return;
        }

        Map<String, Object> writerBoundaryPlan = objectMap(writerBoundaryReport.get("writerBoundaryPlan"));
        Map<String, Object> testDoubleContract = objectMap(writerBoundaryReport.get("testDoubleContract"));
        boolean valid = NimCreateDedicatedDurableAuditWriterBoundarySupport.BOUNDARY_NAME.equals(text(writerBoundaryReport.get("dedicatedAuditWriterBoundary")))
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.EXECUTION_MODE.equals(text(writerBoundaryReport.get("executionMode")))
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.HOLD_STATE.equals(text(writerBoundaryReport.get("writerBoundaryState")))
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.TEST_DOUBLE_NAME.equals(text(writerBoundaryReport.get("testDoubleName")))
            && "NOT_PERFORMED".equals(text(writerBoundaryReport.get("networkAccess")))
            && "NONE".equals(text(writerBoundaryReport.get("sideEffect")))
            && Boolean.TRUE.equals(writerBoundaryReport.get("inputAccepted"))
            && Boolean.TRUE.equals(writerBoundaryReport.get("writerBoundaryPlanPrepared"))
            && Boolean.TRUE.equals(writerBoundaryReport.get("testDoubleContractPrepared"))
            && Boolean.FALSE.equals(writerBoundaryReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(writerBoundaryReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(writerBoundaryReport.get("storageAvailable"))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN.equals(text(writerBoundaryReport.get("availabilityStatus")))
            && Boolean.FALSE.equals(writerBoundaryReport.get("preWritePersisted"))
            && Boolean.FALSE.equals(writerBoundaryReport.get("postWritePersisted"))
            && Boolean.FALSE.equals(writerBoundaryReport.get("durable"))
            && Boolean.FALSE.equals(writerBoundaryReport.get("releaseEligible"))
            && Boolean.FALSE.equals(writerBoundaryReport.get("durableReceiptCanBeIssued"))
            && Boolean.FALSE.equals(writerBoundaryReport.get("durableReceiptIssued"))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(writerBoundaryReport.get("candidateIndex")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(writerBoundaryReport.get("candidateSaveService")))
            && text(writerBoundaryReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(writerBoundaryReport.get("sourceWriterPlanDigest")).equals(text(availabilityGateReport.get("sourceWriterPlanDigest")))
            && text(writerBoundaryReport.get("sourceAvailabilityPlanDigest")).equals(text(availabilityGateReport.get("availabilityPlanDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(writerBoundaryReport.get("boundaryPlanDigestAlgorithm")))
            && text(writerBoundaryReport.get("boundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(writerBoundaryReport.get("boundaryPlanDigest")).equals(digestFor(writerBoundaryPlan))
            && hasOnlyExpectedWriterBoundaryHold(writerBoundaryReport.get("blockedBy"))
            && writerBoundaryPlanContractValid(auditContext, principal, availabilityGateReport, writerBoundaryPlan)
            && testDoubleContractValid(availabilityGateReport, testDoubleContract);

        if (!valid) {
            blockers.add(blocker(
                "DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_INVALID_FOR_PROBE_EXECUTOR",
                "storage probe executor 只能消费 M5.21-54 产生的、仍为 HOLD 且不允许测试替身伪造成功的 writer boundary report。",
                "dedicated-audit-writer-boundary"
            ));
        }
        if (containsForgedProbeSuccessClaim(writerBoundaryReport)) {
            blockers.add(forgedProbeSuccessClaimBlocker("dedicatedAuditWriterBoundaryReport"));
        }
    }

    private static void validateProbeExecutionSnapshot(Map<String, Object> probeExecutionSnapshot,
                                                       List<Map<String, Object>> blockers) {
        if (containsForgedProbeSuccessClaim(probeExecutionSnapshot)) {
            blockers.add(forgedProbeSuccessClaimBlocker("probeExecutionSnapshot"));
        }
    }

    private static boolean availabilityPlanContractValid(Map<String, Object> auditContext,
                                                         Map<String, Object> principal,
                                                         Map<String, Object> availabilityGateReport,
                                                         Map<String, Object> availabilityPlan) {
        Map<String, Object> identity = objectMap(availabilityPlan.get("trustedIdentityBinding"));
        return !availabilityPlan.isEmpty()
            && "DEDICATED_NIM_STORAGE_AVAILABILITY_GATE_REQUIRED".equals(text(availabilityPlan.get("gateBoundary")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(availabilityPlan.get("targetStorage")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY.equals(text(availabilityPlan.get("targetEntity")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(availabilityPlan.get("saveService")))
            && "FUTURE_SERVER_SIDE_PROBE_ONLY".equals(text(availabilityPlan.get("probeMode")))
            && Boolean.TRUE.equals(availabilityPlan.get("requiredBeforePreWrite"))
            && text(availabilityPlan.get("sourceWriterPlanDigest")).equals(text(availabilityGateReport.get("sourceWriterPlanDigest")))
            && text(availabilityPlan.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && probeStepsContractValid(availabilityPlan.get("probeSteps"))
            && failurePolicyContractValid(objectMap(availabilityPlan.get("failurePolicy")))
            && receiptPrerequisitesContractValid(objectMap(availabilityPlan.get("receiptPrerequisites")));
    }

    private static boolean writerBoundaryPlanContractValid(Map<String, Object> auditContext,
                                                           Map<String, Object> principal,
                                                           Map<String, Object> availabilityGateReport,
                                                           Map<String, Object> writerBoundaryPlan) {
        Map<String, Object> evidence = objectMap(writerBoundaryPlan.get("evidenceBinding"));
        Map<String, Object> identity = objectMap(writerBoundaryPlan.get("trustedIdentityBinding"));
        Map<String, Object> currentState = objectMap(writerBoundaryPlan.get("currentImplementationState"));
        Map<String, Object> receiptReleaseRule = objectMap(writerBoundaryPlan.get("receiptReleaseRule"));

        return !writerBoundaryPlan.isEmpty()
            && "SERVER_SIDE_DEDICATED_DURABLE_AUDIT_WRITER_REQUIRED".equals(text(writerBoundaryPlan.get("boundaryRequirement")))
            && "NimDurableAuditWriter".equals(text(writerBoundaryPlan.get("futureInterface")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(writerBoundaryPlan.get("targetStorage")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY.equals(text(writerBoundaryPlan.get("targetEntity")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(writerBoundaryPlan.get("saveService")))
            && "PROBE_THEN_PRE_WRITE_THEN_POST_WRITE".equals(text(writerBoundaryPlan.get("writeMode")))
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.TEST_DOUBLE_NAME.equals(text(writerBoundaryPlan.get("testDoubleContractName")))
            && text(evidence.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(evidence.get("sourceWriterPlanDigest")).equals(text(availabilityGateReport.get("sourceWriterPlanDigest")))
            && text(evidence.get("sourceAvailabilityPlanDigest")).equals(text(availabilityGateReport.get("availabilityPlanDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(evidence.get("digestAlgorithm")))
            && NimCreateDurableAuditWriterPlanSupport.HOLD_STATE.equals(text(evidence.get("writerPlanRequiredState")))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.HOLD_STATE.equals(text(evidence.get("availabilityGateRequiredState")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && operationOrderContractValid(writerBoundaryPlan.get("operationOrder"))
            && Boolean.FALSE.equals(currentState.get("boundaryImplemented"))
            && Boolean.FALSE.equals(currentState.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(currentState.get("storageAvailable"))
            && Boolean.FALSE.equals(currentState.get("preWritePersisted"))
            && Boolean.FALSE.equals(currentState.get("postWritePersisted"))
            && Boolean.FALSE.equals(currentState.get("durableReceiptCanBeIssued"))
            && Boolean.FALSE.equals(receiptReleaseRule.get("currentBoundaryCanIssueReceipt"))
            && Boolean.TRUE.equals(receiptReleaseRule.get("storageAvailableRequired"))
            && Boolean.TRUE.equals(receiptReleaseRule.get("preWriteDurableAckRequired"))
            && Boolean.TRUE.equals(receiptReleaseRule.get("postWriteDurableAckRequired"))
            && Boolean.TRUE.equals(receiptReleaseRule.get("sameAuditEventDigestRequired"))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(receiptReleaseRule.get("requiredFutureReceiptStatus")))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(receiptReleaseRule.get("requiredFutureStorageMode")))
            && Boolean.FALSE.equals(receiptReleaseRule.get("mockReceiptAllowed"));
    }

    private static boolean testDoubleContractValid(Map<String, Object> availabilityGateReport,
                                                   Map<String, Object> testDoubleContract) {
        return !testDoubleContract.isEmpty()
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.TEST_DOUBLE_NAME.equals(text(testDoubleContract.get("testDoubleName")))
            && "UNIT_CONTRACT_ONLY".equals(text(testDoubleContract.get("scope")))
            && text(testDoubleContract.get("sourceWriterPlanDigest")).equals(text(availabilityGateReport.get("sourceWriterPlanDigest")))
            && text(testDoubleContract.get("sourceAvailabilityPlanDigest")).equals(text(availabilityGateReport.get("availabilityPlanDigest")))
            && "NOT_PERFORMED".equals(text(testDoubleContract.get("networkAccess")))
            && "NONE".equals(text(testDoubleContract.get("sideEffect")))
            && Boolean.FALSE.equals(testDoubleContract.get("realStorageTouched"))
            && Boolean.FALSE.equals(testDoubleContract.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(testDoubleContract.get("storageAvailable"))
            && Boolean.FALSE.equals(testDoubleContract.get("preWritePersisted"))
            && Boolean.FALSE.equals(testDoubleContract.get("postWritePersisted"))
            && Boolean.FALSE.equals(testDoubleContract.get("durableReceiptCanBeIssued"));
    }

    private static boolean probeStepsContractValid(Object value) {
        List<Map<String, Object>> steps = listOfMaps(value);
        if (steps.size() != 4) {
            return false;
        }
        List<String> ids = steps.stream().map(step -> text(step.get("id"))).toList();
        return ids.equals(List.of(
            "verify-storage-client-enabled",
            "verify-sys-log-index-resolvable",
            "verify-dedicated-writer-sanitized-record-contract",
            "verify-durable-ack-or-read-after-write"
        ))
            && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("required")))
            && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed")))
            && steps.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow")));
    }

    private static boolean operationOrderContractValid(Object value) {
        List<Map<String, Object>> steps = listOfMaps(value);
        if (steps.size() != 5) {
            return false;
        }
        List<String> ids = steps.stream().map(step -> text(step.get("id"))).toList();
        return ids.equals(List.of(
            "validate-boundary-inputs",
            "probe-storage-availability",
            "persist-pre-write-intent",
            "persist-post-write-result",
            "assemble-durable-receipt"
        ))
            && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly")))
            && steps.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow")))
            && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed")));
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

    private static Map<String, Object> probeExecutorPlan(Map<String, Object> auditContext,
                                                         Map<String, Object> principal,
                                                         Map<String, Object> availabilityGateReport,
                                                         Map<String, Object> writerBoundaryReport,
                                                         Map<String, Object> probeExecutionSnapshot) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("executorBoundary", "SERVER_SIDE_DURABLE_AUDIT_STORAGE_PROBE_EXECUTOR_REQUIRED");
        plan.put("futureInterface", "NimDurableAuditStorageProbeExecutor");
        plan.put("executionPlacement", "INSIDE_DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY");
        plan.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        plan.put("targetEntity", NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY);
        plan.put("saveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        plan.put("requiredBeforePreWrite", true);
        plan.put("sideEffectAllowedNow", false);
        plan.put("evidenceBinding", evidenceBinding(auditContext, availabilityGateReport, writerBoundaryReport));
        plan.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        plan.put("operationOrder", operationOrder());
        plan.put("currentImplementationState", currentImplementationState(!probeExecutionSnapshot.isEmpty()));
        plan.put("probeResultContract", probeResultContract());
        plan.put("failurePolicy", failurePolicy());
        plan.put("preWriteReleaseRule", preWriteReleaseRule());
        return plan;
    }

    private static Map<String, Object> evidenceBinding(Map<String, Object> auditContext,
                                                       Map<String, Object> availabilityGateReport,
                                                       Map<String, Object> writerBoundaryReport) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("sourceAuditEventDigest", digestFor(auditContext));
        binding.put("sourceWriterPlanDigest", text(availabilityGateReport.get("sourceWriterPlanDigest")));
        binding.put("sourceAvailabilityPlanDigest", text(availabilityGateReport.get("availabilityPlanDigest")));
        binding.put("sourceBoundaryPlanDigest", text(writerBoundaryReport.get("boundaryPlanDigest")));
        binding.put("digestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        binding.put("availabilityGateRequiredState", NimCreateDurableAuditStorageAvailabilityGateSupport.HOLD_STATE);
        binding.put("writerBoundaryRequiredState", NimCreateDedicatedDurableAuditWriterBoundarySupport.HOLD_STATE);
        binding.put("probeResultMustBeServerIssued", true);
        return binding;
    }

    private static List<Map<String, Object>> operationOrder() {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(operationStep(
            "validate-availability-gate-report",
            "Validate source gate digest, trusted identity binding and fail-closed probe plan"
        ));
        steps.add(operationStep(
            "validate-dedicated-writer-boundary",
            "Validate the probe executor is placed inside the dedicated writer boundary"
        ));
        steps.add(operationStep(
            "verify-storage-client-enabled",
            "Future executor must verify the server-side storage client is enabled"
        ));
        steps.add(operationStep(
            "verify-sys-log-index-resolvable",
            "Future executor must verify the target audit storage is reachable"
        ));
        steps.add(operationStep(
            "verify-sanitized-probe-record-contract",
            "Future executor must ensure probe evidence contains no caller secret material"
        ));
        steps.add(operationStep(
            "verify-durable-ack-or-read-after-write",
            "Future executor must verify durable acknowledgement or read-after-write evidence"
        ));
        steps.add(operationStep(
            "return-server-issued-probe-result",
            "Future executor may allow pre-write only after a reviewed server-issued probe result"
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

    private static Map<String, Object> currentImplementationState(boolean diagnosticSnapshotObserved) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("executorImplemented", false);
        state.put("diagnosticProbeSnapshotObserved", diagnosticSnapshotObserved);
        state.put("diagnosticProbeSnapshotAuthoritative", false);
        state.put("networkAccess", "NOT_PERFORMED");
        state.put("sideEffect", "NONE");
        state.put("storageProbeExecuted", false);
        state.put("realStorageTouched", false);
        state.put("storageAvailable", false);
        state.put("availabilityStatus", NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN);
        state.put("durableAckVerified", false);
        state.put("readAfterWriteVerified", false);
        state.put("preWriteAllowed", false);
        state.put("durableReceiptCanBeIssued", false);
        return state;
    }

    private static Map<String, Object> probeResultContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("futureResultName", "NimDurableAuditStorageProbeResult");
        contract.put("mustBeServerIssued", true);
        contract.put("bindSameAuditEventDigest", true);
        contract.put("bindSourceAvailabilityPlanDigest", true);
        contract.put("bindSourceBoundaryPlanDigest", true);
        contract.put("storageAvailableRequiredForPass", true);
        contract.put("durableAckRequiredForPass", true);
        contract.put("readAfterWriteRequiredForPass", true);
        contract.put("callerSnapshotCanIssuePass", false);
        contract.put("currentContractCanIssuePass", false);
        contract.put("forbiddenCurrentAssertions", List.of(
            "storageProbeExecuted=true",
            "realStorageTouched=true",
            "storageAvailable=true",
            "availabilityStatus=AVAILABLE",
            "durableAckVerified=true",
            "readAfterWriteVerified=true",
            "preWriteAllowed=true",
            "writePermitted=true",
            "writeExecutionAllowed=true",
            "realHttpExecutionAllowed=true",
            "receiptStatus=DURABLE_RECORDED",
            "storageMode=DURABLE_AUDIT_LOG"
        ));
        return contract;
    }

    private static Map<String, Object> failurePolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("failClosed", true);
        policy.put("blockPreWriteWhenUnavailable", true);
        policy.put("blockPreWriteWhenTimeout", true);
        policy.put("blockPreWriteWhenAmbiguous", true);
        policy.put("blockPreWriteWhenDurableAckMissing", true);
        policy.put("blockPreWriteWhenReadAfterWriteMissing", true);
        policy.put("blockPreWriteWhenDigestMismatch", true);
        policy.put("blockPreWriteWhenPrincipalMismatch", true);
        policy.put("fallbackToMockProbeAllowed", false);
        policy.put("fallbackToMockReceiptAllowed", false);
        policy.put("fallbackToCallerProbeSnapshotAllowed", false);
        policy.put("fallbackToAvailabilityPlanAllowed", false);
        policy.put("fallbackToWriterBoundaryTestDoubleAllowed", false);
        return policy;
    }

    private static Map<String, Object> preWriteReleaseRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("currentExecutorCanAllowPreWrite", false);
        rule.put("storageAvailableRequired", true);
        rule.put("durableAckVerifiedRequired", true);
        rule.put("readAfterWriteVerifiedRequired", true);
        rule.put("sameAuditEventDigestRequired", true);
        rule.put("serverIssuedProbeResultRequired", true);
        rule.put("durableReceiptCanBeIssuedNow", false);
        return rule;
    }

    private static boolean hasOnlyExpectedAvailabilityGateHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "STORAGE_AVAILABILITY_PROBE_IMPLEMENTATION_HOLD".equals(text(blockers.get(0).get("code")));
    }

    private static boolean hasOnlyExpectedWriterBoundaryHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_IMPLEMENTATION_HOLD".equals(text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "STORAGE_PROBE_EXECUTOR_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static boolean containsForgedProbeSuccessClaim(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForgedProbeSuccessClaim(entry.getKey(), value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && containsForgedProbeSuccessClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && containsForgedProbeSuccessClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isForgedProbeSuccessClaim(String key, Object value) {
        return switch (key) {
            case "storageProbeExecuted",
                "probeAttempted",
                "realStorageTouched",
                "storageAvailable",
                "durableAck",
                "durableAckReceived",
                "durableAckObserved",
                "durableAckVerified",
                "readAfterWrite",
                "readAfterWriteVerified",
                "preWriteAllowed",
                "preWritePersisted",
                "postWritePersisted",
                "preWriteDurable",
                "postWriteDurable",
                "storageProbeReceiptIssued",
                "durableReceiptCanBeIssued",
                "durableReceiptIssued",
                "probeReceiptIssued",
                "probeReceiptValidated",
                "preWriteDurableAckIssued",
                "preWriteDurableAckVerified",
                "postWriteDurableAckIssued",
                "postWriteDurableAckVerified",
                "writePermitted",
                "writeExecutionAllowed",
                "realHttpExecutionAllowed",
                "releaseEligible",
                "durable" -> Boolean.TRUE.equals(value);
            case "availabilityStatus" -> "AVAILABLE".equals(text(value));
            case "probeStatus" -> Set.of("SUCCESS", "STORAGE_AVAILABLE_CONFIRMED").contains(text(value));
            case "receiptStatus" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(value));
            case "storageMode" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(value));
            default -> false;
        };
    }

    private static Map<String, Object> forgedProbeSuccessClaimBlocker(String source) {
        return blocker(
            "STORAGE_PROBE_EXECUTOR_FORGED_SUCCESS_CLAIM",
            source + " 不得自称 storage probe 已执行、storageAvailable、durable ack/read-after-write 已验证或可放行 pre-write。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> availabilityGateReport,
                                                    Map<String, Object> writerBoundaryReport,
                                                    Map<String, Object> probeExecutionSnapshot) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "storageProbeExecuted",
            "probeAttempted",
            "realStorageTouched",
            "storageAvailable",
            "availabilityStatus",
            "durableAckVerified",
            "readAfterWriteVerified",
            "preWriteAllowed",
            "preWritePersisted",
            "postWritePersisted",
            "writePermitted",
            "writeExecutionAllowed",
            "realHttpExecutionAllowed",
            "durableReceiptCanBeIssued",
            "durableReceiptIssued",
            "releaseEligible",
            "durable",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key)
                || principal.containsKey(key)
                || availabilityGateReport.containsKey(key)
                || writerBoundaryReport.containsKey(key)
                || probeExecutionSnapshot.containsKey(key)) {
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

    record StorageProbeExecutorInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> storageAvailabilityGateReport,
        Map<String, Object> dedicatedAuditWriterBoundaryReport,
        Map<String, Object> probeExecutionSnapshot
    ) {
        StorageProbeExecutorInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            storageAvailabilityGateReport = storageAvailabilityGateReport == null ? Map.of() : objectMap(storageAvailabilityGateReport);
            dedicatedAuditWriterBoundaryReport = dedicatedAuditWriterBoundaryReport == null ? Map.of() : objectMap(dedicatedAuditWriterBoundaryReport);
            probeExecutionSnapshot = probeExecutionSnapshot == null ? Map.of() : objectMap(probeExecutionSnapshot);
        }

        static StorageProbeExecutorInput empty() {
            return new StorageProbeExecutorInput(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
