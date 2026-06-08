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
 * NIM 创建 durable write executor 合同壳。
 *
 * <p>本类不是生产写执行器，也不持有 HTTP client。它只验证未来 executor 入场前必须同时拿到
 * 受控 request spec 和受控 handoff，并在真实实现完成前强制停在 {@code IMPLEMENTATION_HOLD}。
 * 这样可以先把幂等、审计交接、body/request digest 复核和写后 readiness 交接锁成契约，
 * 防止后续开发误把 handoff 直接当成已经执行的副作用结果。</p>
 */
final class NimCreateDurableWriteExecutorSupport {

    static final String EXECUTOR_NAME = NimCreateWriteExecutionHandoffSupport.FUTURE_EXECUTOR;
    static final String EXECUTION_MODE = "DURABLE_WRITE_EXECUTOR_CONTRACT_SHELL";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String EXECUTION_ATTEMPT_SPEC_DIGEST_ALGORITHM =
        NimCreateWriteExecutionHandoffSupport.HANDOFF_DIGEST_ALGORITHM;

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final Set<String> REQUEST_SPEC_KEYS = Set.of(
        "target",
        "method",
        "endpoint",
        "pathTemplate",
        "resolvedPath",
        "clientBoundary",
        "queryAllowed",
        "query",
        "bodyAllowed",
        "bodyRequired",
        "bodySource",
        "body",
        "bodyDigestAlgorithm",
        "bodyDigest",
        "callerHeadersAllowed",
        "authorizationHeaderFromCallerAllowed",
        "kubeManagerAuthBoundary",
        "realApiKeyAllowed",
        "apiKeyHandling",
        "idempotencyKeyRequiredBeforeExecution",
        "executionAdapterRequired",
        "sideEffect",
        "futureSideEffectIfExecuted"
    );
    private static final Set<String> HANDOFF_PLAN_KEYS = Set.of(
        "target",
        "method",
        "backendEndpoint",
        "pathTemplate",
        "resolvedPath",
        "futureExecutor",
        "networkAccess",
        "sideEffect",
        "requestSpecDigest",
        "bodyDigest",
        "callerHeadersAllowed",
        "authorizationHeaderFromCallerAllowed",
        "realApiKeyAllowed",
        "kubeManagerAuthBoundary",
        "idempotency",
        "preWriteAuditHandoff",
        "postWriteReadinessHandoff",
        "retryPolicy"
    );
    private static final Set<String> IDEMPOTENCY_KEYS = Set.of(
        "required",
        "key",
        "keySource",
        "callerKeyAllowed",
        "reuseAllowedOnlyForSameAuditReceiptAndRequestSpec"
    );
    private static final Set<String> PRE_WRITE_AUDIT_HANDOFF_KEYS = Set.of(
        "required",
        "receiptId",
        "eventDigest",
        "storageMode",
        "receiptStatus",
        "durable",
        "realStorageTouched"
    );
    private static final Set<String> POST_WRITE_READINESS_HANDOFF_KEYS = Set.of(
        "requiredAfterWrite",
        "nextExecutor",
        "pollOnly",
        "readOnly",
        "apiKeyHandling",
        "forbiddenBeforeWrite"
    );
    private static final Set<String> RETRY_POLICY_KEYS = Set.of(
        "retryAllowed",
        "retryAllowedOnlyWithSameIdempotencyKey",
        "maxAttemptsBeforeExecutorImplementation"
    );

    private NimCreateDurableWriteExecutorSupport() {
    }

    static Map<String, Object> prepare(WriteExecutionInput input) {
        WriteExecutionInput safeInput = input == null ? WriteExecutionInput.empty() : input;
        Map<String, Object> handoffReport = safeInput.writeExecutionHandoffReport();
        Map<String, Object> requestSpecReport = safeInput.writeRequestSpecReport();
        Map<String, Object> codeReleaseSwitchContractReport = safeInput.codeReleaseSwitchContractReport();
        Map<String, Object> codeReleaseSwitchRuntimeSourceGuardReport =
            safeInput.codeReleaseSwitchRuntimeSourceGuardReport();
        Map<String, Object> requestSpec = objectMap(requestSpecReport.get("requestSpec"));
        Map<String, Object> handoffPlan = objectMap(handoffReport.get("executionHandoffPlan"));
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateRequestSpecReport(requestSpecReport, requestSpec, blockers);
        validateHandoffReport(handoffReport, handoffPlan, requestSpecReport, requestSpec, blockers);
        validateCodeReleaseSwitchContractReport(
            codeReleaseSwitchContractReport,
            handoffReport,
            requestSpecReport,
            blockers
        );
        validateCodeReleaseSwitchRuntimeSourceGuardReport(
            codeReleaseSwitchRuntimeSourceGuardReport,
            codeReleaseSwitchContractReport,
            handoffReport,
            blockers
        );
        validateNoSecretMaterial("writeExecutionHandoffReport", handoffReport, blockers);
        validateNoSecretMaterial("writeRequestSpecReport", requestSpecReport, blockers);
        validateNoSecretMaterial("codeReleaseSwitchContractReport", codeReleaseSwitchContractReport, blockers);
        validateNoSecretMaterial("codeReleaseSwitchRuntimeSourceGuardReport",
            codeReleaseSwitchRuntimeSourceGuardReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> executionAttemptSpec = inputAccepted
            ? executionAttemptSpec(handoffReport, handoffPlan, requestSpecReport, requestSpec)
            : Map.of();
        String executionAttemptSpecDigest = inputAccepted ? digestFor(executionAttemptSpec) : "";
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(
                blocker(
                    "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD",
                    "真实 durable write executor 尚未实现和审计；当前合同壳不得执行 POST。",
                    "durable-write-executor"
                ),
                blocker(
                    "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD",
                    "M5.21-75 source guard matrix is accepted as required guard evidence only; no reviewed open switch source exists yet.",
                    "code-release-switch-runtime-source-guard"
                )
            )
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableWriteExecutor", EXECUTOR_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("executionState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("httpMethod", "POST");
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("executorImplementationAvailable", false);
        result.put("releaseCredential", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("writeAttempted", false);
        result.put("writeExecuted", false);
        result.put("postWriteReadinessTriggered", false);
        result.put("sourceHandoffDigest", text(handoffReport.get("handoffDigest")));
        result.put("sourceRequestSpecDigest", text(requestSpecReport.get("requestSpecDigest")));
        result.put("sourceBodyDigest", text(requestSpecReport.get("bodyDigest")));
        result.put("idempotencyKey", text(handoffReport.get("idempotencyKey")));
        result.put("idempotencyKeySource", NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE);
        result.put("callerIdempotencyKeyAllowed", false);
        result.put("codeReleaseSwitchContractReportRequired", true);
        result.put("sourceCodeReleaseSwitchContractDigest",
            text(codeReleaseSwitchContractReport.get("codeReleaseSwitchContractDigest")));
        result.put("codeReleaseSwitchRuntimeBindingRequired", true);
        result.put("codeReleaseSwitchRuntimeSourceGuardReportRequired", true);
        result.put("sourceGuardMatrixDigest",
            text(codeReleaseSwitchRuntimeSourceGuardReport.get("sourceGuardMatrixDigest")));
        result.put("sourceRuntimeBindingContractDigest",
            text(codeReleaseSwitchRuntimeSourceGuardReport.get("sourceRuntimeBindingContractDigest")));
        result.put("sourceGuardInstalled", false);
        result.put("candidateSourceEvidenceAuthoritative", false);
        result.put("backendQuerySourceAllowedForRelease", false);
        result.put("sysLogBackfillSourceAllowed", false);
        result.put("codeReleaseSwitchDigestVerified", false);
        result.put("releaseDecisionDigestVerified", false);
        result.put("validationResultDigestVerified", false);
        result.put("fallbackToStateMachineWritePermittedAllowed", false);
        result.put("executionAttemptSpecDigestAlgorithm", EXECUTION_ATTEMPT_SPEC_DIGEST_ALGORITHM);
        result.put("executionAttemptSpecDigest", executionAttemptSpecDigest);
        result.put("executionAttemptSpec", executionAttemptSpec);
        result.put("blockedBy", finalBlockers);
        result.put("nextImplementationRequirements", List.of(
            "wire reviewed KubeManagerHttpClient only inside this executor boundary",
            "re-check reviewed code release switch digest immediately before real POST",
            "require the M5.21-75 runtime source guard matrix before trusting any switch source",
            "bind release decision digest and validation result digest to the same switch runtime binding",
            "persist write attempt/result audit before and after POST",
            "reuse only the server-derived idempotency key from handoff",
            "trigger post-write readiness executor only after a confirmed write response"
        ));
        return result;
    }

    private static void validateRequestSpecReport(Map<String, Object> requestSpecReport,
                                                  Map<String, Object> requestSpec,
                                                  List<Map<String, Object>> blockers) {
        Map<String, Object> requestBody = objectMap(requestSpec.get("body"));
        String organizationId = text(requestSpecReport.get("organizationId"));
        boolean contractValid = NimCreateWriteRequestSpecAdapterSupport.ADAPTER_NAME.equals(text(requestSpecReport.get("writeRequestSpecAdapter")))
            && NimCreateWriteRequestSpecAdapterSupport.EXECUTION_MODE.equals(text(requestSpecReport.get("executionMode")))
            && "NOT_PERFORMED".equals(text(requestSpecReport.get("networkAccess")))
            && "NONE".equals(text(requestSpecReport.get("sideEffect")))
            && Boolean.TRUE.equals(requestSpecReport.get("writeRequestPrepared"))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(requestSpecReport.get("targetTool")))
            && "POST".equals(text(requestSpecReport.get("httpMethod")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(requestSpecReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(requestSpecReport.get("pathTemplate")))
            && safeIdentifier(organizationId)
            && NimCreateWriteRequestSpecAdapterSupport.CLIENT_BOUNDARY.equals(text(requestSpecReport.get("clientBoundary")))
            && NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(requestSpecReport.get("apiKeyHandling")))
            && Boolean.FALSE.equals(requestSpecReport.get("releaseCredential"))
            && Boolean.FALSE.equals(requestSpecReport.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(requestSpecReport.get("authorizationHeaderFromCallerAllowed"))
            && Boolean.FALSE.equals(requestSpecReport.get("realApiKeyAllowed"))
            && Boolean.TRUE.equals(requestSpecReport.get("bodyCopiedByValue"))
            && Boolean.FALSE.equals(requestSpecReport.get("bodyMutationAllowed"))
            && NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM.equals(text(requestSpecReport.get("bodyDigestAlgorithm")))
            && text(requestSpecReport.get("bodyDigest")).matches("[a-f0-9]{64}")
            && NimCreateWriteRequestSpecAdapterSupport.REQUEST_SPEC_DIGEST_ALGORITHM.equals(text(requestSpecReport.get("requestSpecDigestAlgorithm")))
            && text(requestSpecReport.get("requestSpecDigest")).matches("[a-f0-9]{64}")
            && text(requestSpecReport.get("requestSpecDigest")).equals(digestFor(requestSpec))
            && listOfMaps(requestSpecReport.get("blockedBy")).isEmpty()
            && requestSpecContractValid(organizationId, requestSpecReport, requestSpec, requestBody);

        if (!contractValid) {
            blockers.add(blocker(
                "WRITE_REQUEST_SPEC_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR",
                "durable write executor 合同壳只能消费受控 request spec adapter 产出的、digest 可复算且无副作用的 POST request spec。",
                "write-request-spec"
            ));
        }
    }

    private static boolean requestSpecContractValid(String organizationId,
                                                    Map<String, Object> requestSpecReport,
                                                    Map<String, Object> requestSpec,
                                                    Map<String, Object> requestBody) {
        return !requestSpec.isEmpty()
            && hasOnlyKeys(requestSpec, REQUEST_SPEC_KEYS)
            && "deployment-create".equals(text(requestSpec.get("target")))
            && "POST".equals(text(requestSpec.get("method")))
            && PATH_TEMPLATE.equals(text(requestSpec.get("endpoint")))
            && PATH_TEMPLATE.equals(text(requestSpec.get("pathTemplate")))
            && ("/api/" + organizationId + "/deployment").equals(text(requestSpec.get("resolvedPath")))
            && NimCreateWriteRequestSpecAdapterSupport.CLIENT_BOUNDARY.equals(text(requestSpec.get("clientBoundary")))
            && Boolean.FALSE.equals(requestSpec.get("queryAllowed"))
            && objectMap(requestSpec.get("query")).isEmpty()
            && Boolean.TRUE.equals(requestSpec.get("bodyAllowed"))
            && Boolean.TRUE.equals(requestSpec.get("bodyRequired"))
            && "CONTROLLED_REBUILDER_BODY_COPY".equals(text(requestSpec.get("bodySource")))
            && NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM.equals(text(requestSpec.get("bodyDigestAlgorithm")))
            && text(requestSpecReport.get("bodyDigest")).equals(text(requestSpec.get("bodyDigest")))
            && text(requestSpec.get("bodyDigest")).equals(digestFor(requestBody))
            && Boolean.FALSE.equals(requestSpec.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(requestSpec.get("authorizationHeaderFromCallerAllowed"))
            && "KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY".equals(text(requestSpec.get("kubeManagerAuthBoundary")))
            && Boolean.FALSE.equals(requestSpec.get("realApiKeyAllowed"))
            && NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(requestSpec.get("apiKeyHandling")))
            && Boolean.TRUE.equals(requestSpec.get("idempotencyKeyRequiredBeforeExecution"))
            && EXECUTOR_NAME.equals(text(requestSpec.get("executionAdapterRequired")))
            && "NONE".equals(text(requestSpec.get("sideEffect")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(requestSpec.get("futureSideEffectIfExecuted")))
            && writeBodyContractValid(requestBody);
    }

    private static void validateHandoffReport(Map<String, Object> handoffReport,
                                              Map<String, Object> handoffPlan,
                                              Map<String, Object> requestSpecReport,
                                              Map<String, Object> requestSpec,
                                              List<Map<String, Object>> blockers) {
        Map<String, Object> idempotency = objectMap(handoffPlan.get("idempotency"));
        Map<String, Object> preWriteAuditHandoff = objectMap(handoffPlan.get("preWriteAuditHandoff"));
        Map<String, Object> postWriteReadinessHandoff = objectMap(handoffPlan.get("postWriteReadinessHandoff"));
        Map<String, Object> retryPolicy = objectMap(handoffPlan.get("retryPolicy"));
        boolean contractValid = NimCreateWriteExecutionHandoffSupport.HANDOFF_NAME.equals(text(handoffReport.get("writeExecutionHandoff")))
            && NimCreateWriteExecutionHandoffSupport.EXECUTION_MODE.equals(text(handoffReport.get("executionMode")))
            && "NOT_PERFORMED".equals(text(handoffReport.get("networkAccess")))
            && "NONE".equals(text(handoffReport.get("sideEffect")))
            && Boolean.TRUE.equals(handoffReport.get("writeExecutionPrepared"))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(handoffReport.get("targetTool")))
            && "POST".equals(text(handoffReport.get("httpMethod")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(handoffReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(handoffReport.get("pathTemplate")))
            && text(requestSpecReport.get("organizationId")).equals(text(handoffReport.get("organizationId")))
            && handoffSourceEvidenceMatchesRequestSpecReport(handoffReport, requestSpecReport)
            && EXECUTOR_NAME.equals(text(handoffReport.get("futureExecutor")))
            && Boolean.FALSE.equals(handoffReport.get("releaseCredential"))
            && Boolean.FALSE.equals(handoffReport.get("realHttpExecutionAllowed"))
            && Boolean.TRUE.equals(handoffReport.get("preWriteAuditRequired"))
            && Boolean.TRUE.equals(handoffReport.get("idempotencyRequired"))
            && NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE.equals(text(handoffReport.get("idempotencyKeySource")))
            && text(handoffReport.get("idempotencyKey")).matches("nim-create-[a-f0-9]{32}")
            && text(handoffReport.get("idempotencyKey")).equals(
                NimCreateWriteExecutionHandoffSupport.serverDerivedIdempotencyKeyFromHandoffEvidence(
                    handoffReport,
                    requestSpecReport
                ))
            && Boolean.FALSE.equals(handoffReport.get("callerIdempotencyKeyAllowed"))
            && Boolean.FALSE.equals(handoffReport.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(handoffReport.get("authorizationHeaderFromCallerAllowed"))
            && Boolean.FALSE.equals(handoffReport.get("realApiKeyAllowed"))
            && text(requestSpecReport.get("bodyDigest")).equals(text(handoffReport.get("sourceBodyDigest")))
            && text(requestSpecReport.get("requestSpecDigest")).equals(text(handoffReport.get("sourceRequestSpecDigest")))
            && NimCreateWriteExecutionHandoffSupport.HANDOFF_DIGEST_ALGORITHM.equals(text(handoffReport.get("handoffDigestAlgorithm")))
            && text(handoffReport.get("handoffDigest")).matches("[a-f0-9]{64}")
            && text(handoffReport.get("handoffDigest")).equals(digestFor(handoffPlan))
            && listOfMaps(handoffReport.get("blockedBy")).isEmpty()
            && handoffPlanContractValid(handoffReport, handoffPlan, requestSpecReport, requestSpec, idempotency, preWriteAuditHandoff, postWriteReadinessHandoff, retryPolicy);

        if (!contractValid) {
            blockers.add(blocker(
                "WRITE_EXECUTION_HANDOFF_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR",
                "durable write executor 合同壳只能消费绑定 request spec/body/audit receipt/idempotency 的受控 handoff。",
                "write-execution-handoff"
            ));
        }
    }

    private static boolean handoffSourceEvidenceMatchesRequestSpecReport(Map<String, Object> handoffReport,
                                                                         Map<String, Object> requestSpecReport) {
        return text(requestSpecReport.get("sourceAuditReceiptId")).equals(text(handoffReport.get("sourceAuditReceiptId")))
            && text(requestSpecReport.get("sourceAuditEventDigest")).equals(text(handoffReport.get("sourceAuditEventDigest")))
            && text(requestSpecReport.get("sourceRequestId")).equals(text(handoffReport.get("sourceRequestId")))
            && text(requestSpecReport.get("sourceConversationId")).equals(text(handoffReport.get("sourceConversationId")))
            && text(requestSpecReport.get("sourceUserId")).equals(text(handoffReport.get("sourceUserId")))
            && text(requestSpecReport.get("organizationId")).equals(text(handoffReport.get("organizationId")));
    }

    private static boolean handoffPlanContractValid(Map<String, Object> handoffReport,
                                                    Map<String, Object> handoffPlan,
                                                    Map<String, Object> requestSpecReport,
                                                    Map<String, Object> requestSpec,
                                                    Map<String, Object> idempotency,
                                                    Map<String, Object> preWriteAuditHandoff,
                                                    Map<String, Object> postWriteReadinessHandoff,
                                                    Map<String, Object> retryPolicy) {
        return !handoffPlan.isEmpty()
            && hasOnlyKeys(handoffPlan, HANDOFF_PLAN_KEYS)
            && hasOnlyKeys(idempotency, IDEMPOTENCY_KEYS)
            && hasOnlyKeys(preWriteAuditHandoff, PRE_WRITE_AUDIT_HANDOFF_KEYS)
            && hasOnlyKeys(postWriteReadinessHandoff, POST_WRITE_READINESS_HANDOFF_KEYS)
            && hasOnlyKeys(retryPolicy, RETRY_POLICY_KEYS)
            && "deployment-create".equals(text(handoffPlan.get("target")))
            && "POST".equals(text(handoffPlan.get("method")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(handoffPlan.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(handoffPlan.get("pathTemplate")))
            && text(requestSpec.get("resolvedPath")).equals(text(handoffPlan.get("resolvedPath")))
            && EXECUTOR_NAME.equals(text(handoffPlan.get("futureExecutor")))
            && "NOT_PERFORMED".equals(text(handoffPlan.get("networkAccess")))
            && "NONE".equals(text(handoffPlan.get("sideEffect")))
            && text(requestSpecReport.get("requestSpecDigest")).equals(text(handoffPlan.get("requestSpecDigest")))
            && text(requestSpecReport.get("bodyDigest")).equals(text(handoffPlan.get("bodyDigest")))
            && Boolean.FALSE.equals(handoffPlan.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(handoffPlan.get("authorizationHeaderFromCallerAllowed"))
            && Boolean.FALSE.equals(handoffPlan.get("realApiKeyAllowed"))
            && "KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY".equals(text(handoffPlan.get("kubeManagerAuthBoundary")))
            && Boolean.TRUE.equals(idempotency.get("required"))
            && text(handoffReport.get("idempotencyKey")).equals(text(idempotency.get("key")))
            && text(idempotency.get("key")).equals(
                NimCreateWriteExecutionHandoffSupport.serverDerivedIdempotencyKeyFromHandoffEvidence(
                    handoffReport,
                    requestSpecReport
                ))
            && NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE.equals(text(idempotency.get("keySource")))
            && Boolean.FALSE.equals(idempotency.get("callerKeyAllowed"))
            && Boolean.TRUE.equals(idempotency.get("reuseAllowedOnlyForSameAuditReceiptAndRequestSpec"))
            && Boolean.TRUE.equals(preWriteAuditHandoff.get("required"))
            && text(handoffReport.get("sourceAuditReceiptId")).equals(text(preWriteAuditHandoff.get("receiptId")))
            && text(handoffReport.get("sourceAuditEventDigest")).equals(text(preWriteAuditHandoff.get("eventDigest")))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(preWriteAuditHandoff.get("storageMode")))
            && NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(preWriteAuditHandoff.get("receiptStatus")))
            && Boolean.TRUE.equals(preWriteAuditHandoff.get("durable"))
            && Boolean.TRUE.equals(preWriteAuditHandoff.get("realStorageTouched"))
            && Boolean.TRUE.equals(postWriteReadinessHandoff.get("requiredAfterWrite"))
            && NimCreateReadinessExecutorSupport.EXECUTOR_NAME.equals(text(postWriteReadinessHandoff.get("nextExecutor")))
            && Boolean.TRUE.equals(postWriteReadinessHandoff.get("pollOnly"))
            && Boolean.TRUE.equals(postWriteReadinessHandoff.get("readOnly"))
            && NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(postWriteReadinessHandoff.get("apiKeyHandling")))
            && Boolean.TRUE.equals(postWriteReadinessHandoff.get("forbiddenBeforeWrite"))
            && Boolean.FALSE.equals(retryPolicy.get("retryAllowed"))
            && Boolean.TRUE.equals(retryPolicy.get("retryAllowedOnlyWithSameIdempotencyKey"))
            && "1".equals(text(retryPolicy.get("maxAttemptsBeforeExecutorImplementation")));
    }

    private static void validateCodeReleaseSwitchContractReport(Map<String, Object> report,
                                                                Map<String, Object> handoffReport,
                                                                Map<String, Object> requestSpecReport,
                                                                List<Map<String, Object>> blockers) {
        if (report.isEmpty()) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_NOT_READY_FOR_DURABLE_EXECUTOR",
                "Missing code release switch contract report; durable executor cannot rely on state-machine flags or handoff alone.",
                "code-release-switch-contract"
            ));
            return;
        }

        Map<String, Object> contract = objectMap(report.get("codeReleaseSwitchContract"));
        Map<String, Object> durableExecutorBinding = objectMap(contract.get("durableExecutorBinding"));
        Map<String, Object> template = objectMap(contract.get("currentTemplate"));
        Map<String, Object> prerequisites = objectMap(contract.get("openPrerequisites"));
        Map<String, Object> failure = objectMap(contract.get("failureContract"));
        List<String> fields = stringList(contract.get("requiredFutureEvidenceDigestFields"));
        boolean valid = NimCreateDurableAuditCodeReleaseSwitchContractSupport.SWITCH_CONTRACT_NAME.equals(
                text(report.get("durableAuditCodeReleaseSwitchContract")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.EXECUTION_MODE.equals(
                text(report.get("executionMode")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.HOLD_STATE.equals(
                text(report.get("switchState")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.FUTURE_CODE_RELEASE_SWITCH.equals(
                text(report.get("futureCodeReleaseSwitch")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(report.get("targetTool")))
            && PATH_TEMPLATE.equals(text(report.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(report.get("networkAccess")))
            && "NONE".equals(text(report.get("sideEffect")))
            && Boolean.TRUE.equals(report.get("inputAccepted"))
            && Boolean.TRUE.equals(report.get("codeReleaseSwitchContractPrepared"))
            && Boolean.TRUE.equals(report.get("serverOwnedCodeReleaseSwitchRequired"))
            && Boolean.TRUE.equals(report.get("reviewedCodeSwitchDigestRequired"))
            && Boolean.FALSE.equals(report.get("callerSwitchEvidenceAuthoritative"))
            && codeReleaseSwitchStatesRemainFalse(report)
            && text(report.get("codeReleaseSwitchContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("codeReleaseSwitchContractDigest")).equals(digestFor(contract))
            && hasOnlyBlockerCode(report.get("blockedBy"),
                "DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT_IMPLEMENTATION_HOLD")
            && "REVIEWED_SERVER_OWNED_CODE_RELEASE_SWITCH_REQUIRED".equals(text(contract.get("contractBoundary")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.FUTURE_CODE_RELEASE_SWITCH.equals(
                text(contract.get("type")))
            && Boolean.TRUE.equals(contract.get("futureOnly"))
            && Boolean.FALSE.equals(contract.get("instanceAllowedNow"))
            && durableExecutorSwitchBindingValid(durableExecutorBinding)
            && fields.equals(requiredCodeReleaseSwitchDigestFields())
            && Boolean.FALSE.equals(template.get("codeReleaseSwitchDigestVerified"))
            && Boolean.FALSE.equals(template.get("durableExecutorReleaseBound"))
            && Boolean.FALSE.equals(template.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(template.get("realHttpExecutionAllowed"))
            && Boolean.TRUE.equals(prerequisites.get("durableExecutorRecheckRequired"))
            && Boolean.FALSE.equals(prerequisites.get("currentContractSatisfiesPrerequisites"))
            && Boolean.TRUE.equals(failure.get("failClosed"))
            && Boolean.FALSE.equals(failure.get("fallbackToStateMachineBooleanAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToExecutorSuccessAllowed"))
            && hasText(handoffReport.get("handoffDigest"))
            && hasText(requestSpecReport.get("requestSpecDigest"));

        if (!valid) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR",
                "Durable executor only accepts the M5.21-72 HOLD code release switch contract report with recomputable digest.",
                "code-release-switch-contract"
            ));
        }

        if (codeReleaseSwitchContractClaimsRelease(report)) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_RELEASE_CLAIM_NOT_TRUSTED_FOR_DURABLE_EXECUTOR",
                "Durable executor cannot trust forged open-switch or write-success claims from the contract report.",
                "code-release-switch-contract"
            ));
        }
    }

    private static List<String> requiredCodeReleaseSwitchDigestFields() {
        return List.of(
            "releaseDecisionContractDigest",
            "validationResultContractDigest",
            "validationResultDigest",
            "releaseDecisionDigest",
            "codeReleaseSwitchDigest",
            "codeReviewDigest",
            "testEvidenceDigest",
            "securityApprovalDigest",
            "rollbackPlanDigest",
            "changeWindowDigest",
            "bodyDigest",
            "requestSpecDigest",
            "handoffDigest",
            "auditReceiptId",
            "sourceAuditEventDigest",
            "trustedPrincipalDigest",
            "serverDerivedIdempotencyKey"
        );
    }

    private static void validateCodeReleaseSwitchRuntimeSourceGuardReport(Map<String, Object> report,
                                                                          Map<String, Object> codeSwitchReport,
                                                                          Map<String, Object> handoffReport,
                                                                          List<Map<String, Object>> blockers) {
        if (report.isEmpty()) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_READY_FOR_DURABLE_EXECUTOR",
                "Missing M5.21-75 runtime source guard matrix; durable executor cannot treat switch contract shape or readback as release source.",
                "code-release-switch-runtime-source-guard"
            ));
            return;
        }

        Map<String, Object> contract = objectMap(report.get("sourceGuardContract"));
        Map<String, Object> acceptanceRules = objectMap(contract.get("acceptanceRules"));
        Map<String, Object> failure = objectMap(contract.get("failureContract"));
        List<Map<String, Object>> matrix = listOfMaps(report.get("sourceGuardMatrix"));
        List<Map<String, Object>> contractMatrix = listOfMaps(contract.get("sourceGuardMatrix"));
        List<String> planningSources = stringList(report.get("contractShapeSourcesAcceptedForPlanning"));
        List<String> contractPlanningSources = stringList(contract.get("contractShapeSourcesAcceptedForPlanning"));
        List<String> forbiddenSources = stringList(report.get("forbiddenReleaseSources"));
        List<String> dangerousFields = stringList(report.get("dangerousReleaseCredentialFieldNames"));
        List<String> contractDangerousFields = stringList(contract.get("dangerousReleaseCredentialFieldNames"));

        boolean valid = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.SOURCE_GUARD_CONTRACT_NAME.equals(
                text(report.get("codeReleaseSwitchRuntimeSourceGuard")))
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.EXECUTION_MODE.equals(
                text(report.get("executionMode")))
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.HOLD_STATE.equals(
                text(report.get("guardState")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(report.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(report.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(report.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(report.get("networkAccess")))
            && "NONE".equals(text(report.get("sideEffect")))
            && Boolean.TRUE.equals(report.get("inputAccepted"))
            && Boolean.TRUE.equals(report.get("sourceGuardMatrixPrepared"))
            && Boolean.TRUE.equals(report.get("runtimeBindingReportRequired"))
            && Boolean.TRUE.equals(report.get("runtimeBindingDigestRecomputed"))
            && Boolean.FALSE.equals(report.get("sourceGuardInstalled"))
            && Boolean.FALSE.equals(report.get("candidateSourceEvidenceAuthoritative"))
            && Boolean.FALSE.equals(report.get("callerParamSourceAllowed"))
            && Boolean.FALSE.equals(report.get("llmJsonSourceAllowed"))
            && Boolean.FALSE.equals(report.get("environmentVariableSourceAllowed"))
            && Boolean.FALSE.equals(report.get("runtimeFlagSourceAllowed"))
            && Boolean.FALSE.equals(report.get("stateMachineBooleanSourceAllowed"))
            && Boolean.FALSE.equals(report.get("durableExecutorSuccessSourceAllowed"))
            && Boolean.FALSE.equals(report.get("backendQuerySourceAllowedForRelease"))
            && Boolean.FALSE.equals(report.get("sysLogBackfillSourceAllowed"))
            && Boolean.TRUE.equals(report.get("serverOwnedOpenSwitchRequired"))
            && Boolean.TRUE.equals(report.get("reviewedCodeSwitchDigestRequired"))
            && Boolean.TRUE.equals(report.get("stateMachineDigestRecheckRequired"))
            && Boolean.TRUE.equals(report.get("durableExecutorDigestRecheckRequired"))
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.closedSourceGuardReportListsValid(report)
            && listIsEmpty(report.get("acceptedSourcesForCurrentRelease"))
            && planningSources.containsAll(List.of(
                "M5.21-72_CODE_RELEASE_SWITCH_CONTRACT_REPORT",
                "M5.21-73_RUNTIME_BINDING_REPORT"
            ))
            && forbiddenSources.containsAll(List.of(
                "CALLER_PARAMS_OR_LLM_JSON",
                "ENVIRONMENT_VARIABLE_OR_RUNTIME_FLAG",
                "LEGACY_NIM_CREATE_RELEASED_BOOLEAN",
                "STATE_MACHINE_WRITE_PERMITTED_BOOLEAN",
                "DURABLE_EXECUTOR_SUCCESS_OR_DEPLOYMENT_ID",
                "BACKEND_QUERY_OR_READBACK_RESULT",
                "SYS_LOG_OR_ELASTICSEARCH_BACKFILL",
                "RELEASE_DECISION_OR_VALIDATION_CONTRACT_REPORT_ONLY"
            ))
            && dangerousFields.containsAll(List.of(
                "codeReleaseSwitchContractReportAcceptedForRelease",
                "writeExecuted",
                "deploymentId",
                "writeResult"
            ))
            && text(codeSwitchReport.get("codeReleaseSwitchContractDigest")).equals(
                text(report.get("sourceCodeReleaseSwitchContractDigest")))
            && text(codeSwitchReport.get("sourceAuditEventDigest")).equals(text(report.get("sourceAuditEventDigest")))
            && text(codeSwitchReport.get("trustedPrincipalDigest")).equals(text(report.get("trustedPrincipalDigest")))
            && text(report.get("sourceRuntimeBindingContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceGuardMatrixDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceGuardMatrixDigest")).equals(digestFor(contract))
            && hasOnlyBlockerCode(report.get("blockedBy"),
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD")
            && "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REQUIRED".equals(text(contract.get("contractBoundary")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(contract.get("targetTool")))
            && Boolean.TRUE.equals(contract.get("futureOnly"))
            && Boolean.FALSE.equals(contract.get("instanceAllowedNow"))
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.BINDING_CONTRACT_NAME.equals(
                text(contract.get("sourceRuntimeBindingContract")))
            && text(report.get("sourceRuntimeBindingContractDigest")).equals(
                text(contract.get("sourceRuntimeBindingContractDigest")))
            && text(report.get("sourceCodeReleaseSwitchContractDigest")).equals(
                text(contract.get("sourceCodeReleaseSwitchContractDigest")))
            && text(report.get("sourceAuditEventDigest")).equals(text(contract.get("sourceAuditEventDigest")))
            && text(report.get("trustedPrincipalDigest")).equals(text(contract.get("trustedPrincipalDigest")))
            && "PLANNING_AND_GUARD_ONLY".equals(text(contract.get("currentAcceptedSourceScope")))
            && listIsEmpty(contract.get("acceptedSourcesForCurrentRelease"))
            && matrix.equals(contractMatrix)
            && planningSources.equals(contractPlanningSources)
            && dangerousFields.equals(contractDangerousFields)
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.closedSourceGuardMatrixValid(
                matrix,
                text(report.get("sourceRuntimeBindingContractDigest")))
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.closedSourceGuardContractValid(
                contract,
                matrix,
                text(report.get("sourceRuntimeBindingContractDigest")),
                text(report.get("sourceCodeReleaseSwitchContractDigest")),
                text(report.get("sourceAuditEventDigest")),
                text(report.get("trustedPrincipalDigest")))
            && Boolean.TRUE.equals(acceptanceRules.get("failClosed"))
            && Integer.valueOf(0).equals(acceptanceRules.get("currentReleaseSourceCount"))
            && Boolean.FALSE.equals(acceptanceRules.get("contractReportAcceptedForRelease"))
            && Boolean.FALSE.equals(acceptanceRules.get("runtimeBindingReportAcceptedForRelease"))
            && Boolean.FALSE.equals(acceptanceRules.get("legacyNimCreateReleasedBooleanAuthoritative"))
            && Boolean.FALSE.equals(acceptanceRules.get("stateMachineWritePermittedAuthoritativeForExecutor"))
            && Boolean.FALSE.equals(acceptanceRules.get("executorSuccessAuthoritativeForSwitch"))
            && Boolean.FALSE.equals(acceptanceRules.get("backendReadbackAllowedAsReleaseSource"))
            && Boolean.TRUE.equals(acceptanceRules.get("realOpenSwitchIssuerRequired"))
            && Boolean.TRUE.equals(failure.get("failClosed"))
            && Boolean.FALSE.equals(failure.get("fallbackToStateMachineWritePermittedAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToDurableExecutorSuccessAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToBackendQueryResultAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToStorageBackfillAllowed"))
            && matrixContainsSource(matrix, "REVIEWED_SERVER_OWNED_OPEN_SWITCH", true)
            && matrixContainsSource(matrix, "DURABLE_EXECUTOR_SUCCESS_OR_DEPLOYMENT_ID", false)
            && matrixContainsSource(matrix, "BACKEND_QUERY_OR_READBACK_RESULT", false)
            && hasText(handoffReport.get("handoffDigest"));

        if (!valid) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR",
                "Durable executor only accepts the M5.21-75 HOLD source guard matrix bound to the same switch digest.",
                "code-release-switch-runtime-source-guard"
            ));
        }

        if (runtimeSourceGuardClaimsRelease(report)) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_RELEASE_CLAIM_NOT_TRUSTED_FOR_DURABLE_EXECUTOR",
                "Source guard matrix cannot claim installed guard, accepted release source, write permission, executor success, readback release, or storage backfill release.",
                "code-release-switch-runtime-source-guard"
            ));
        }
    }

    private static boolean matrixContainsSource(List<Map<String, Object>> matrix,
                                                String source,
                                                boolean futureAuthoritativeCandidate) {
        for (Map<String, Object> row : matrix) {
            if (source.equals(text(row.get("source")))
                && Boolean.valueOf(futureAuthoritativeCandidate).equals(row.get("futureAuthoritativeCandidate"))
                && Boolean.FALSE.equals(row.get("authoritativeForReleaseNow"))
                && Boolean.FALSE.equals(row.get("writeExecutionAllowedNow"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean runtimeSourceGuardClaimsRelease(Map<String, Object> report) {
        return Boolean.TRUE.equals(report.get("sourceGuardInstalled"))
            || Boolean.TRUE.equals(report.get("candidateSourceEvidenceAuthoritative"))
            || Boolean.TRUE.equals(report.get("callerParamSourceAllowed"))
            || Boolean.TRUE.equals(report.get("llmJsonSourceAllowed"))
            || Boolean.TRUE.equals(report.get("environmentVariableSourceAllowed"))
            || Boolean.TRUE.equals(report.get("runtimeFlagSourceAllowed"))
            || Boolean.TRUE.equals(report.get("stateMachineBooleanSourceAllowed"))
            || Boolean.TRUE.equals(report.get("durableExecutorSuccessSourceAllowed"))
            || Boolean.TRUE.equals(report.get("backendQuerySourceAllowedForRelease"))
            || Boolean.TRUE.equals(report.get("sysLogBackfillSourceAllowed"))
            || Boolean.TRUE.equals(report.get("releaseDecisionContractReportSourceAllowed"))
            || Boolean.TRUE.equals(report.get("validationResultContractReportSourceAllowed"))
            || !listIsEmpty(report.get("acceptedSourcesForCurrentRelease"))
            || Boolean.TRUE.equals(report.get("writeExecutionAllowed"))
            || Boolean.TRUE.equals(report.get("writeExecuted"))
            || hasText(report.get("deploymentId"))
            || hasText(report.get("deploymentUid"))
            || !objectMap(report.get("writeResult")).isEmpty();
    }

    private static Map<String, Object> executionAttemptSpec(Map<String, Object> handoffReport,
                                                            Map<String, Object> handoffPlan,
                                                            Map<String, Object> requestSpecReport,
                                                            Map<String, Object> requestSpec) {
        Map<String, Object> requestBody = objectMap(requestSpec.get("body"));
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("target", "deployment-create");
        spec.put("method", "POST");
        spec.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        spec.put("pathTemplate", PATH_TEMPLATE);
        spec.put("resolvedPath", text(requestSpec.get("resolvedPath")));
        spec.put("requestSpecCopiedByValue", true);
        spec.put("requestSpecDigestAlgorithm", NimCreateWriteRequestSpecAdapterSupport.REQUEST_SPEC_DIGEST_ALGORITHM);
        spec.put("requestSpecDigest", text(requestSpecReport.get("requestSpecDigest")));
        spec.put("requestSpec", deepObjectMap(requestSpec));
        spec.put("bodyCopiedByValue", true);
        spec.put("bodyDigestAlgorithm", NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM);
        spec.put("bodyDigest", text(requestSpecReport.get("bodyDigest")));
        spec.put("body", deepObjectMap(requestBody));
        spec.put("executionHandoffPlanCopiedByValue", true);
        spec.put("handoffDigestAlgorithm", NimCreateWriteExecutionHandoffSupport.HANDOFF_DIGEST_ALGORITHM);
        spec.put("handoffDigest", text(handoffReport.get("handoffDigest")));
        spec.put("executionHandoffPlan", deepObjectMap(handoffPlan));
        spec.put("idempotencyKey", text(handoffReport.get("idempotencyKey")));
        spec.put("idempotencyKeySource", NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE);
        spec.put("auditReceiptId", text(handoffReport.get("sourceAuditReceiptId")));
        spec.put("auditEventDigest", text(handoffReport.get("sourceAuditEventDigest")));
        spec.put("kubeManagerAuthBoundary", text(handoffPlan.get("kubeManagerAuthBoundary")));
        spec.put("callerHeadersAllowed", false);
        spec.put("authorizationHeaderFromCallerAllowed", false);
        spec.put("realApiKeyAllowed", false);
        spec.put("postWriteReadinessExecutor", NimCreateReadinessExecutorSupport.EXECUTOR_NAME);
        spec.put("writeWillBeAttempted", false);
        return spec;
    }

    private static boolean writeBodyContractValid(Map<String, Object> body) {
        return !body.isEmpty()
            && hasText(body.get("name"))
            && hasText(body.get("displayName"))
            && hasText(body.get("image"))
            && hasText(body.get("templateId"))
            && !body.containsKey("organizationId")
            && !body.containsKey("orgId")
            && !body.containsKey("userId")
            && !body.containsKey("conversationId")
            && !body.containsKey("token")
            && !body.containsKey("apiKey")
            && !body.containsKey("ngcApiKey")
            && !body.containsKey("nvaieApiKey")
            && !body.containsKey("Authorization")
            && !body.containsKey("password")
            && !body.containsKey("secret")
            && !NimProtectedContextDetector.containsProtectedContext(body);
    }

    private static boolean codeReleaseSwitchStatesRemainFalse(Map<String, Object> report) {
        return Boolean.FALSE.equals(report.get("realCodeReleaseSwitchCreated"))
            && Boolean.FALSE.equals(report.get("realCodeReleaseSwitchOpened"))
            && Boolean.FALSE.equals(report.get("serverOwnedCodeReleaseSwitchAccepted"))
            && Boolean.FALSE.equals(report.get("codeReleaseSwitchDigestVerified"))
            && Boolean.FALSE.equals(report.get("releaseDecisionDigestVerified"))
            && Boolean.FALSE.equals(report.get("validationResultDigestVerified"))
            && Boolean.FALSE.equals(report.get("stateMachineReleaseBound"))
            && Boolean.FALSE.equals(report.get("durableExecutorReleaseBound"))
            && Boolean.FALSE.equals(report.get("releaseDecisionAccepted"))
            && Boolean.FALSE.equals(report.get("releaseCredentialIssued"))
            && Boolean.FALSE.equals(report.get("releaseEligible"))
            && Boolean.FALSE.equals(report.get("writePermitted"))
            && Boolean.FALSE.equals(report.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(report.get("realHttpExecutionAllowed"))
            && Boolean.FALSE.equals(report.get("realStorageTouched"));
    }

    private static boolean durableExecutorSwitchBindingValid(Map<String, Object> binding) {
        return EXECUTOR_NAME.equals(text(binding.get("target")))
            && Boolean.TRUE.equals(binding.get("futureCodeReleaseSwitchDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureReleaseDecisionDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureValidationResultDigestRequired"))
            && Boolean.TRUE.equals(binding.get("mustRecheckImmediatelyBeforePost"))
            && Boolean.FALSE.equals(binding.get("fallbackToStateMachineFlagOnlyAllowed"))
            && Boolean.FALSE.equals(binding.get("writeExecutionAllowedNow"));
    }

    private static boolean codeReleaseSwitchContractClaimsRelease(Map<String, Object> report) {
        return Boolean.TRUE.equals(report.get("realCodeReleaseSwitchCreated"))
            || Boolean.TRUE.equals(report.get("realCodeReleaseSwitchOpened"))
            || Boolean.TRUE.equals(report.get("serverOwnedCodeReleaseSwitchAccepted"))
            || Boolean.TRUE.equals(report.get("codeReleaseSwitchDigestVerified"))
            || Boolean.TRUE.equals(report.get("releaseDecisionDigestVerified"))
            || Boolean.TRUE.equals(report.get("validationResultDigestVerified"))
            || Boolean.TRUE.equals(report.get("releaseEligible"))
            || Boolean.TRUE.equals(report.get("writePermitted"))
            || Boolean.TRUE.equals(report.get("writeExecutionAllowed"))
            || Boolean.TRUE.equals(report.get("realHttpExecutionAllowed"))
            || "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION".equals(text(report.get("switchState")))
            || "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION".equals(text(report.get("codeReleaseSwitchStatus")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_WRITE_EXECUTOR_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    private static Map<String, Object> deepObjectMap(Map<String, Object> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, item) -> copy.put(String.valueOf(key), deepCopy(item)));
        return copy;
    }

    private static Object deepCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), deepCopy(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(deepCopy(item));
            }
            return copy;
        }
        return value;
    }

    private static String digestFor(Map<String, Object> value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(NimCreateWriteExecutionHandoffSupport.HANDOFF_DIGEST_ALGORITHM);
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

    private static boolean hasOnlyBlockerCode(Object rawBlockers, String code) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1 && code.equals(text(blockers.get(0).get("code")));
    }

    private static boolean listIsEmpty(Object value) {
        return value instanceof List<?> list && list.isEmpty();
    }

    private static boolean hasOnlyKeys(Map<String, Object> map, Set<String> allowedKeys) {
        return map.keySet().equals(allowedKeys);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            result.add(text(item));
        }
        return result;
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

    private static boolean safeIdentifier(Object value) {
        return text(value).matches("[A-Za-z0-9_-]{1,64}");
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record WriteExecutionInput(
        Map<String, Object> writeExecutionHandoffReport,
        Map<String, Object> writeRequestSpecReport,
        Map<String, Object> codeReleaseSwitchContractReport,
        Map<String, Object> codeReleaseSwitchRuntimeSourceGuardReport
    ) {
        WriteExecutionInput(Map<String, Object> writeExecutionHandoffReport,
                            Map<String, Object> writeRequestSpecReport,
                            Map<String, Object> codeReleaseSwitchContractReport) {
            this(
                writeExecutionHandoffReport,
                writeRequestSpecReport,
                codeReleaseSwitchContractReport,
                Map.of()
            );
        }

        WriteExecutionInput(Map<String, Object> writeExecutionHandoffReport,
                            Map<String, Object> writeRequestSpecReport) {
            this(writeExecutionHandoffReport, writeRequestSpecReport, Map.of(), Map.of());
        }

        WriteExecutionInput {
            writeExecutionHandoffReport = writeExecutionHandoffReport == null ? Map.of() : objectMap(writeExecutionHandoffReport);
            writeRequestSpecReport = writeRequestSpecReport == null ? Map.of() : objectMap(writeRequestSpecReport);
            codeReleaseSwitchContractReport = codeReleaseSwitchContractReport == null ? Map.of() : objectMap(codeReleaseSwitchContractReport);
            codeReleaseSwitchRuntimeSourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport == null
                ? Map.of()
                : objectMap(codeReleaseSwitchRuntimeSourceGuardReport);
        }

        static WriteExecutionInput empty() {
            return new WriteExecutionInput(Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
