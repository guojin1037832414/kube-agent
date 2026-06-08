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
import java.util.TreeMap;

/**
 * NIM 专用 durable audit writer 的未来接口规格契约。
 *
 * <p>本类只定义未来 {@code NimDurableAuditWriter} 的请求、响应、失败语义和测试替身约束。
 * 它不创建真实 Java 接口、不注入 Spring Bean、不连接 Elasticsearch、不调用 {@code ISysLogService}，
 * 也不访问 kube-manager。这样可以先把接口边界审计清楚，再进入后续真实实现。</p>
 */
final class NimCreateDurableAuditWriterInterfaceSpecSupport {

    static final String INTERFACE_SPEC_NAME = "NIM_CREATE_DURABLE_AUDIT_WRITER_INTERFACE_SPEC";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_WRITER_INTERFACE_SPEC_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String FUTURE_INTERFACE = "NimDurableAuditWriter";
    static final String REQUEST_TYPE = "NimDurableAuditWriteRequest";
    static final String RESPONSE_TYPE = "NimDurableAuditWriteResult";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";

    private NimCreateDurableAuditWriterInterfaceSpecSupport() {
    }

    static List<String> requestRequiredFields() {
        return List.of(
            "auditContext",
            "trustedPrincipalSnapshot",
            "storageAvailabilityProbeRequest",
            "preWriteRecordTemplate",
            "postWriteRecordTemplate",
            "writeAttemptReference",
            "sanitizedWriteResultSummary"
        );
    }

    static List<String> responseRequiredFutureSuccessFields() {
        return List.of(
            "storageProbeReceipt",
            "preWriteDurableAck",
            "postWriteDurableAck",
            "durableReceipt"
        );
    }

    static List<String> failureStatuses() {
        return List.of(
            "IMPLEMENTATION_HOLD",
            "STORAGE_PROBE_NOT_IMPLEMENTED",
            "STORAGE_UNAVAILABLE",
            "STORAGE_PROBE_TIMEOUT",
            "PRE_WRITE_DURABLE_ACK_MISSING",
            "POST_WRITE_DURABLE_ACK_MISSING",
            "AUDIT_EVENT_DIGEST_MISMATCH",
            "SECRET_MATERIAL_REJECTED"
        );
    }

    static List<String> testDoubleForbiddenSuccessClaims() {
        return List.of(
            "storageAvailable=true",
            "preWritePersisted=true",
            "postWritePersisted=true",
            "receiptStatus=DURABLE_RECORDED",
            "storageMode=DURABLE_AUDIT_LOG",
            "realStorageTouched=true"
        );
    }

    static Map<String, Object> plan(DurableAuditWriterInterfaceSpecInput input) {
        DurableAuditWriterInterfaceSpecInput safeInput = input == null
            ? DurableAuditWriterInterfaceSpecInput.empty()
            : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> boundaryReport = safeInput.dedicatedAuditWriterBoundaryReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateBoundaryReport(auditContext, principal, boundaryReport, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("dedicatedAuditWriterBoundaryReport", boundaryReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> interfaceSpec = inputAccepted
            ? interfaceSpec(auditContext, principal, boundaryReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DURABLE_AUDIT_WRITER_INTERFACE_IMPLEMENTATION_HOLD",
                "未来 NimDurableAuditWriter 的接口规格已定义，但真实服务端接口和存储实现尚未完成；当前规格不能写入 sys_log 或签发 durable receipt。",
                "durable-audit-writer-interface"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditWriterInterfaceSpec", INTERFACE_SPEC_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("interfaceSpecState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("futureInterface", FUTURE_INTERFACE);
        result.put("requestType", REQUEST_TYPE);
        result.put("responseType", RESPONSE_TYPE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("interfaceSpecPrepared", inputAccepted);
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
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceBoundaryPlanDigest", text(boundaryReport.get("boundaryPlanDigest")));
        result.put("sourceWriterPlanDigest", text(boundaryReport.get("sourceWriterPlanDigest")));
        result.put("sourceAvailabilityPlanDigest", text(boundaryReport.get("sourceAvailabilityPlanDigest")));
        result.put("interfaceSpecDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("interfaceSpecDigest", inputAccepted ? digestFor(interfaceSpec) : "");
        result.put("interfaceSpec", interfaceSpec);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(auditContext, principal, boundaryReport));
        result.put("nextImplementationRequirements", List.of(
            "turn this contract into a reviewed server-side Java interface without registering a Tool",
            "implement storage availability probe behind that interface",
            "persist pre-write and post-write records only through that interface",
            "return a typed result that distinguishes HOLD, storage unavailable, pre-write failure, post-write failure and durable success",
            "keep all test doubles unable to claim durable success until the real writer is implemented"
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_DURABLE_WRITER_INTERFACE_SPEC",
                "durable writer interface spec 只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
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
                "durable writer interface spec 必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedSuccessClaim(principal)) {
            blockers.add(forgedSuccessClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateBoundaryReport(Map<String, Object> auditContext,
                                               Map<String, Object> principal,
                                               Map<String, Object> boundaryReport,
                                               List<Map<String, Object>> blockers) {
        if (boundaryReport.isEmpty()) {
            blockers.add(blocker(
                "DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_NOT_READY",
                "缺少 M5.21-54 dedicated durable audit writer boundary 报告；不能规划未来 writer 接口规格。",
                "dedicated-audit-writer-boundary"
            ));
            return;
        }

        Map<String, Object> boundaryPlan = objectMap(boundaryReport.get("writerBoundaryPlan"));
        Map<String, Object> testDoubleContract = objectMap(boundaryReport.get("testDoubleContract"));
        boolean valid = NimCreateDedicatedDurableAuditWriterBoundarySupport.BOUNDARY_NAME.equals(text(boundaryReport.get("dedicatedAuditWriterBoundary")))
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.EXECUTION_MODE.equals(text(boundaryReport.get("executionMode")))
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.HOLD_STATE.equals(text(boundaryReport.get("writerBoundaryState")))
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.TEST_DOUBLE_NAME.equals(text(boundaryReport.get("testDoubleName")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(boundaryReport.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(boundaryReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(boundaryReport.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(boundaryReport.get("networkAccess")))
            && "NONE".equals(text(boundaryReport.get("sideEffect")))
            && Boolean.TRUE.equals(boundaryReport.get("inputAccepted"))
            && Boolean.TRUE.equals(boundaryReport.get("writerBoundaryPlanPrepared"))
            && Boolean.TRUE.equals(boundaryReport.get("testDoubleContractPrepared"))
            && Boolean.FALSE.equals(boundaryReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(boundaryReport.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(boundaryReport.get("storageAvailable"))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN.equals(text(boundaryReport.get("availabilityStatus")))
            && Boolean.FALSE.equals(boundaryReport.get("preWritePersisted"))
            && Boolean.FALSE.equals(boundaryReport.get("postWritePersisted"))
            && Boolean.FALSE.equals(boundaryReport.get("durable"))
            && Boolean.FALSE.equals(boundaryReport.get("releaseEligible"))
            && Boolean.FALSE.equals(boundaryReport.get("durableReceiptCanBeIssued"))
            && Boolean.FALSE.equals(boundaryReport.get("durableReceiptIssued"))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(boundaryReport.get("candidateIndex")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(boundaryReport.get("candidateSaveService")))
            && text(boundaryReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(boundaryReport.get("sourceWriterPlanDigest")).matches("[a-f0-9]{64}")
            && text(boundaryReport.get("sourceAvailabilityPlanDigest")).matches("[a-f0-9]{64}")
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(boundaryReport.get("boundaryPlanDigestAlgorithm")))
            && text(boundaryReport.get("boundaryPlanDigest")).matches("[a-f0-9]{64}")
            && text(boundaryReport.get("boundaryPlanDigest")).equals(digestFor(boundaryPlan))
            && hasOnlyExpectedBoundaryHold(boundaryReport.get("blockedBy"))
            && boundaryPlanContractValid(auditContext, principal, boundaryReport, boundaryPlan)
            && testDoubleContractValid(boundaryReport, testDoubleContract);

        if (!valid) {
            blockers.add(blocker(
                "DEDICATED_AUDIT_WRITER_BOUNDARY_REPORT_INVALID_FOR_INTERFACE_SPEC",
                "durable writer interface spec 只能消费 M5.21-54 产生的、仍处于 HOLD 且未声明真实存储成功的 boundary report。",
                "dedicated-audit-writer-boundary"
            ));
        }
        if (hasForgedSuccessClaim(boundaryReport)) {
            blockers.add(forgedSuccessClaimBlocker("dedicatedAuditWriterBoundaryReport"));
        }
    }

    private static boolean boundaryPlanContractValid(Map<String, Object> auditContext,
                                                     Map<String, Object> principal,
                                                     Map<String, Object> boundaryReport,
                                                     Map<String, Object> boundaryPlan) {
        Map<String, Object> evidence = objectMap(boundaryPlan.get("evidenceBinding"));
        Map<String, Object> identity = objectMap(boundaryPlan.get("trustedIdentityBinding"));
        Map<String, Object> state = objectMap(boundaryPlan.get("currentImplementationState"));
        Map<String, Object> releaseRule = objectMap(boundaryPlan.get("receiptReleaseRule"));

        return !boundaryPlan.isEmpty()
            && "SERVER_SIDE_DEDICATED_DURABLE_AUDIT_WRITER_REQUIRED".equals(text(boundaryPlan.get("boundaryRequirement")))
            && FUTURE_INTERFACE.equals(text(boundaryPlan.get("futureInterface")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(boundaryPlan.get("targetStorage")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY.equals(text(boundaryPlan.get("targetEntity")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(boundaryPlan.get("saveService")))
            && "PROBE_THEN_PRE_WRITE_THEN_POST_WRITE".equals(text(boundaryPlan.get("writeMode")))
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.TEST_DOUBLE_NAME.equals(text(boundaryPlan.get("testDoubleContractName")))
            && digestFor(auditContext).equals(text(evidence.get("sourceAuditEventDigest")))
            && text(boundaryReport.get("sourceWriterPlanDigest")).equals(text(evidence.get("sourceWriterPlanDigest")))
            && text(boundaryReport.get("sourceAvailabilityPlanDigest")).equals(text(evidence.get("sourceAvailabilityPlanDigest")))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(evidence.get("digestAlgorithm")))
            && NimCreateDurableAuditWriterPlanSupport.HOLD_STATE.equals(text(evidence.get("writerPlanRequiredState")))
            && NimCreateDurableAuditStorageAvailabilityGateSupport.HOLD_STATE.equals(text(evidence.get("availabilityGateRequiredState")))
            && text(auditContext.get("organizationId")).equals(text(identity.get("organizationId")))
            && text(auditContext.get("userId")).equals(text(identity.get("userId")))
            && text(principal.get("username")).equals(text(identity.get("username")))
            && "SERVER_SESSION_CONTEXT".equals(text(identity.get("source")))
            && Boolean.TRUE.equals(identity.get("protectedFromCallerParams"))
            && operationOrderContractValid(boundaryPlan.get("operationOrder"))
            && Boolean.FALSE.equals(state.get("boundaryImplemented"))
            && Boolean.FALSE.equals(state.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(state.get("storageAvailable"))
            && Boolean.FALSE.equals(state.get("preWritePersisted"))
            && Boolean.FALSE.equals(state.get("postWritePersisted"))
            && Boolean.FALSE.equals(state.get("durableReceiptCanBeIssued"))
            && receiptReleaseRuleContractValid(releaseRule);
    }

    private static boolean operationOrderContractValid(Object rawSteps) {
        List<Map<String, Object>> steps = listOfMaps(rawSteps);
        if (steps.size() != 5) {
            return false;
        }
        return "validate-boundary-inputs".equals(text(steps.get(0).get("id")))
            && "probe-storage-availability".equals(text(steps.get(1).get("id")))
            && "persist-pre-write-intent".equals(text(steps.get(2).get("id")))
            && "persist-post-write-result".equals(text(steps.get(3).get("id")))
            && "assemble-durable-receipt".equals(text(steps.get(4).get("id")))
            && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly")))
            && steps.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow")))
            && steps.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed")));
    }

    private static boolean receiptReleaseRuleContractValid(Map<String, Object> rule) {
        return !rule.isEmpty()
            && Boolean.FALSE.equals(rule.get("currentBoundaryCanIssueReceipt"))
            && Boolean.TRUE.equals(rule.get("storageAvailableRequired"))
            && Boolean.TRUE.equals(rule.get("preWriteDurableAckRequired"))
            && Boolean.TRUE.equals(rule.get("postWriteDurableAckRequired"))
            && Boolean.TRUE.equals(rule.get("sameAuditEventDigestRequired"))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(rule.get("requiredFutureReceiptStatus")))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(rule.get("requiredFutureStorageMode")))
            && Boolean.FALSE.equals(rule.get("mockReceiptAllowed"));
    }

    private static boolean testDoubleContractValid(Map<String, Object> boundaryReport,
                                                   Map<String, Object> testDoubleContract) {
        return !testDoubleContract.isEmpty()
            && NimCreateDedicatedDurableAuditWriterBoundarySupport.TEST_DOUBLE_NAME.equals(text(testDoubleContract.get("testDoubleName")))
            && "UNIT_CONTRACT_ONLY".equals(text(testDoubleContract.get("scope")))
            && text(boundaryReport.get("sourceWriterPlanDigest")).equals(text(testDoubleContract.get("sourceWriterPlanDigest")))
            && text(boundaryReport.get("sourceAvailabilityPlanDigest")).equals(text(testDoubleContract.get("sourceAvailabilityPlanDigest")))
            && "NOT_PERFORMED".equals(text(testDoubleContract.get("networkAccess")))
            && "NONE".equals(text(testDoubleContract.get("sideEffect")))
            && Boolean.FALSE.equals(testDoubleContract.get("realStorageTouched"))
            && Boolean.FALSE.equals(testDoubleContract.get("storageProbeExecuted"))
            && Boolean.FALSE.equals(testDoubleContract.get("storageAvailable"))
            && Boolean.FALSE.equals(testDoubleContract.get("preWritePersisted"))
            && Boolean.FALSE.equals(testDoubleContract.get("postWritePersisted"))
            && Boolean.FALSE.equals(testDoubleContract.get("durableReceiptCanBeIssued"))
            && stringList(testDoubleContract.get("forbiddenAssertions")).contains("storageAvailable=true")
            && stringList(testDoubleContract.get("forbiddenAssertions")).contains("preWritePersisted=true")
            && stringList(testDoubleContract.get("forbiddenAssertions")).contains("postWritePersisted=true")
            && stringList(testDoubleContract.get("forbiddenAssertions")).contains("receiptStatus=DURABLE_RECORDED")
            && stringList(testDoubleContract.get("forbiddenAssertions")).contains("storageMode=DURABLE_AUDIT_LOG");
    }

    private static Map<String, Object> interfaceSpec(Map<String, Object> auditContext,
                                                     Map<String, Object> principal,
                                                     Map<String, Object> boundaryReport) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("futureInterface", FUTURE_INTERFACE);
        spec.put("interfaceBoundary", "SERVER_SIDE_ONLY");
        spec.put("implementationMode", "FUTURE_REVIEWED_IMPLEMENTATION_REQUIRED");
        spec.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        spec.put("targetEntity", NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY);
        spec.put("saveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        spec.put("sourceBoundaryPlanDigest", text(boundaryReport.get("boundaryPlanDigest")));
        spec.put("sourceWriterPlanDigest", text(boundaryReport.get("sourceWriterPlanDigest")));
        spec.put("sourceAvailabilityPlanDigest", text(boundaryReport.get("sourceAvailabilityPlanDigest")));
        spec.put("trustedIdentityBinding", Map.of(
            "organizationId", text(auditContext.get("organizationId")),
            "userId", text(auditContext.get("userId")),
            "username", text(principal.get("username")),
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true
        ));
        spec.put("requestContract", requestContract(auditContext, boundaryReport));
        spec.put("responseContract", responseContract());
        spec.put("operationMethods", operationMethods());
        spec.put("failureContract", failureContract());
        spec.put("testDoubleRules", testDoubleRules(boundaryReport));
        return spec;
    }

    private static Map<String, Object> requestContract(Map<String, Object> auditContext,
                                                       Map<String, Object> boundaryReport) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("requestType", REQUEST_TYPE);
        contract.put("trustedInputsOnly", true);
        contract.put("callerSuppliedIdentityAllowed", false);
        contract.put("callerHeadersAllowed", false);
        contract.put("authorizationHeaderFromCallerAllowed", false);
        contract.put("realApiKeyAllowed", false);
        contract.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        contract.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        contract.put("pathTemplate", PATH_TEMPLATE);
        contract.put("sourceAuditEventDigest", digestFor(auditContext));
        contract.put("sourceBoundaryPlanDigest", text(boundaryReport.get("boundaryPlanDigest")));
        contract.put("requiredFields", requestRequiredFields());
        contract.put("forbiddenFields", List.of(
            "Authorization",
            "token",
            "apiKey",
            "ngcApiKey",
            "nvaieApiKey",
            "password",
            "secret",
            "callerProvidedUsername",
            "callerProvidedOrganizationId"
        ));
        contract.put("phaseContracts", phaseContracts());
        return contract;
    }

    private static List<Map<String, Object>> phaseContracts() {
        List<Map<String, Object>> phases = new ArrayList<>();
        phases.add(phaseContract(
            "PROBE_STORAGE",
            "must prove storage availability before pre-write",
            "PRE_WRITE_INTENT",
            false
        ));
        phases.add(phaseContract(
            "PRE_WRITE_INTENT",
            "must persist sanitized pre-write intent before POST",
            "POST_WRITE_RESULT",
            false
        ));
        phases.add(phaseContract(
            "POST_WRITE_RESULT",
            "must persist sanitized post-write result after POST outcome",
            "ASSEMBLE_RECEIPT",
            false
        ));
        phases.add(phaseContract(
            "ASSEMBLE_RECEIPT",
            "may issue durable receipt only after all durable acks are present",
            "NONE",
            false
        ));
        return phases;
    }

    private static Map<String, Object> phaseContract(String phase,
                                                     String requirement,
                                                     String nextPhase,
                                                     boolean sideEffectAllowedNow) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("phase", phase);
        contract.put("requirement", requirement);
        contract.put("nextPhase", nextPhase);
        contract.put("futureOnly", true);
        contract.put("sideEffectAllowedNow", sideEffectAllowedNow);
        contract.put("failClosed", true);
        return contract;
    }

    private static Map<String, Object> responseContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("responseType", RESPONSE_TYPE);
        contract.put("currentImplementationStatus", HOLD_STATE);
        contract.put("successAllowedNow", false);
        contract.put("durableReceiptAllowedNow", false);
        contract.put("requiredFutureSuccessFields", responseRequiredFutureSuccessFields());
        contract.put("currentResponseTemplate", currentResponseTemplate());
        return contract;
    }

    private static Map<String, Object> currentResponseTemplate() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", HOLD_STATE);
        response.put("storageProbeExecuted", false);
        response.put("storageAvailable", false);
        response.put("preWritePersisted", false);
        response.put("postWritePersisted", false);
        response.put("durableReceiptCanBeIssued", false);
        response.put("receiptStatus", "NOT_ISSUED");
        response.put("storageMode", "NONE");
        return response;
    }

    private static List<Map<String, Object>> operationMethods() {
        List<Map<String, Object>> methods = new ArrayList<>();
        methods.add(operationMethod(
            "probeStorageAvailability",
            "StorageAvailabilityProbeRequest",
            "StorageAvailabilityProbeResult",
            "READ_STORAGE_HEALTH"
        ));
        methods.add(operationMethod(
            "persistPreWriteIntent",
            "PreWriteAuditRecord",
            "DurableAuditAck",
            "WRITE_SYS_LOG_PRE_INTENT"
        ));
        methods.add(operationMethod(
            "persistPostWriteResult",
            "PostWriteAuditRecord",
            "DurableAuditAck",
            "WRITE_SYS_LOG_POST_RESULT"
        ));
        methods.add(operationMethod(
            "assembleDurableReceipt",
            "DurableAuditAckPair",
            RESPONSE_TYPE,
            "NO_ADDITIONAL_STORAGE_WRITE"
        ));
        return methods;
    }

    private static Map<String, Object> operationMethod(String name,
                                                       String inputType,
                                                       String outputType,
                                                       String futureSideEffect) {
        Map<String, Object> method = new LinkedHashMap<>();
        method.put("name", name);
        method.put("inputType", inputType);
        method.put("outputType", outputType);
        method.put("futureSideEffect", futureSideEffect);
        method.put("sideEffectAllowedNow", false);
        method.put("implementationRequiredBeforeRelease", true);
        method.put("failClosed", true);
        return method;
    }

    private static Map<String, Object> failureContract() {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("failClosed", true);
        contract.put("fallbackToMockReceiptAllowed", false);
        contract.put("fallbackToBoundaryPlanAllowed", false);
        contract.put("fallbackToCandidateStorageReportAllowed", false);
        contract.put("failureStatuses", failureStatuses());
        return contract;
    }

    private static Map<String, Object> testDoubleRules(Map<String, Object> boundaryReport) {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("testDoubleScope", "UNIT_CONTRACT_ONLY");
        rules.put("sourceBoundaryPlanDigest", text(boundaryReport.get("boundaryPlanDigest")));
        rules.put("mayReturnStatus", HOLD_STATE);
        rules.put("mustReturnNetworkAccess", "NOT_PERFORMED");
        rules.put("mustReturnSideEffect", "NONE");
        rules.put("mustKeepStorageAvailableFalse", true);
        rules.put("mustKeepPreWritePersistedFalse", true);
        rules.put("mustKeepPostWritePersistedFalse", true);
        rules.put("mustKeepDurableReceiptNotIssued", true);
        rules.put("forbiddenSuccessClaims", testDoubleForbiddenSuccessClaims());
        return rules;
    }

    private static boolean hasOnlyExpectedBoundaryHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_IMPLEMENTATION_HOLD".equals(text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_WRITER_INTERFACE_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static boolean hasForgedSuccessClaim(Map<String, Object> map) {
        return Boolean.TRUE.equals(map.get("storageProbeExecuted"))
            || Boolean.TRUE.equals(map.get("storageAvailable"))
            || "AVAILABLE".equals(text(map.get("availabilityStatus")))
            || Boolean.TRUE.equals(map.get("preWritePersisted"))
            || Boolean.TRUE.equals(map.get("postWritePersisted"))
            || Boolean.TRUE.equals(map.get("preWriteDurable"))
            || Boolean.TRUE.equals(map.get("postWriteDurable"))
            || Boolean.TRUE.equals(map.get("durableReceiptCanBeIssued"))
            || Boolean.TRUE.equals(map.get("durableReceiptIssued"))
            || Boolean.TRUE.equals(map.get("releaseEligible"))
            || Boolean.TRUE.equals(map.get("realStorageTouched"))
            || Boolean.TRUE.equals(map.get("durable"))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(map.get("receiptStatus")))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(map.get("storageMode")));
    }

    private static Map<String, Object> forgedSuccessClaimBlocker(String source) {
        return blocker(
            "DURABLE_AUDIT_WRITER_INTERFACE_FORGED_SUCCESS_CLAIM",
            source + " 不得自称 storageAvailable、preWritePersisted、postWritePersisted、DURABLE_RECORDED 或可签发 receipt；这些只能来自未来真实 writer。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> boundaryReport) {
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
            if (auditContext.containsKey(key) || principal.containsKey(key) || boundaryReport.containsKey(key)) {
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

    record DurableAuditWriterInterfaceSpecInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> dedicatedAuditWriterBoundaryReport
    ) {
        DurableAuditWriterInterfaceSpecInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            dedicatedAuditWriterBoundaryReport = dedicatedAuditWriterBoundaryReport == null ? Map.of() : objectMap(dedicatedAuditWriterBoundaryReport);
        }

        static DurableAuditWriterInterfaceSpecInput empty() {
            return new DurableAuditWriterInterfaceSpecInput(Map.of(), Map.of(), Map.of());
        }
    }
}
