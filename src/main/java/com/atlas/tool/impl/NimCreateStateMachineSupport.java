package com.atlas.tool.impl;

import com.atlas.hitl.HitlConfirmation;
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
 * NIM 创建写入状态机守卫。
 *
 * <p>本类只做纯判定，不调用 kube-manager，不写审计日志，也不生成 HITL marker。它的职责是把未来
 * {@code nim_create} 能否进入真实写入前的必要条件固化成代码契约，防止后续开发时从预检结果、
 * LLM 参数或 fallback Tool 直接跳到 {@code POST /api/{orgId}/deployment}。</p>
 */
final class NimCreateStateMachineSupport {

    static final String TARGET_TOOL = "nim_create";
    static final String READY_GATE_STATE = "READY_FOR_SERVER_CONFIRMED_WRITE";
    static final String TRUSTED_POLICY_PASSED = "TRUSTED_PASSED";
    static final String TRUSTED_BODY_PROVENANCE = "SERVER_REBUILT_FROM_AUDITED_NIM_STATE";
    static final String API_KEY_POLICY = "NEVER_GENERATE_STORE_OR_DISPLAY";
    static final String REQUIRED_AUDIT_STORAGE_MODE = "DURABLE_AUDIT_LOG";
    static final String REQUIRED_AUDIT_RECEIPT_STATUS = "DURABLE_RECORDED";

    private static final Set<String> REQUIRED_AUDIT_FIELDS = Set.of(
        "requestId",
        "conversationId",
        "userId",
        "organizationId",
        "targetTool",
        "writeBodyProvenance"
    );

    private static final Set<String> REQUIRED_READINESS_TARGETS = Set.of(
        "deployment",
        "service",
        "nim-health",
        "nim-models"
    );

    private static final NimForbiddenSecretMaterialDetector.DetectionPolicy SECRET_DETECTION_POLICY =
        NimForbiddenSecretMaterialDetector.textValuePolicyAllowing(
            Set.of(NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER)
        );
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
    private static final Set<String> EXECUTION_ATTEMPT_SPEC_KEYS = Set.of(
        "target",
        "method",
        "backendEndpoint",
        "pathTemplate",
        "resolvedPath",
        "requestSpecCopiedByValue",
        "requestSpecDigestAlgorithm",
        "requestSpecDigest",
        "requestSpec",
        "bodyCopiedByValue",
        "bodyDigestAlgorithm",
        "bodyDigest",
        "body",
        "executionHandoffPlanCopiedByValue",
        "handoffDigestAlgorithm",
        "handoffDigest",
        "executionHandoffPlan",
        "idempotencyKey",
        "idempotencyKeySource",
        "auditReceiptId",
        "auditEventDigest",
        "kubeManagerAuthBoundary",
        "callerHeadersAllowed",
        "authorizationHeaderFromCallerAllowed",
        "realApiKeyAllowed",
        "postWriteReadinessExecutor",
        "writeWillBeAttempted"
    );

    private NimCreateStateMachineSupport() {
    }

    static Map<String, Object> evaluateCurrentPlaceholderHold(Map<String, Object> params) {
        return evaluate(new ReadinessRequest(
            params,
            Map.of(),
            Map.of(),
            null,
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            "",
            false
        ));
    }

    static Map<String, Object> evaluate(ReadinessRequest request) {
        ReadinessRequest safeRequest = request == null ? ReadinessRequest.empty() : request;
        List<Map<String, Object>> blockers = new ArrayList<>();

        if (!safeRequest.nimCreateReleased()) {
            blockers.add(blocker(
                "NIM_CREATE_RELEASE_NOT_ENABLED",
                "nim_create 真实写入开关尚未由代码审计显式打开；当前只能返回状态机 HOLD。",
                "release"
            ));
        }
        validateCreationGate(safeRequest.creationGate(), blockers);
        validatePreview(safeRequest.deploymentBodyPreview(), blockers);
        validateHitlConfirmation(safeRequest.hitlConfirmation(), blockers);
        validateAuditContext(safeRequest.auditContext(), blockers);
        validateAuditReceipt(safeRequest.auditContext(), safeRequest.auditReceipt(), blockers);
        validateWriteBodyRebuildReport(
            safeRequest.auditContext(),
            safeRequest.auditReceipt(),
            safeRequest.writeBodyRebuildReport(),
            blockers
        );
        validateWriteRequestSpecReport(
            safeRequest.auditContext(),
            safeRequest.auditReceipt(),
            safeRequest.writeBodyRebuildReport(),
            safeRequest.writeRequestSpecReport(),
            blockers
        );
        validateWriteExecutionHandoffReport(
            safeRequest.auditContext(),
            safeRequest.auditReceipt(),
            safeRequest.writeBodyRebuildReport(),
            safeRequest.writeRequestSpecReport(),
            safeRequest.writeExecutionHandoffReport(),
            blockers
        );
        validateDurableWriteExecutorReport(
            safeRequest.auditContext(),
            safeRequest.auditReceipt(),
            safeRequest.writeBodyRebuildReport(),
            safeRequest.writeRequestSpecReport(),
            safeRequest.writeExecutionHandoffReport(),
            safeRequest.codeReleaseSwitchContractReport(),
            safeRequest.codeReleaseSwitchRuntimeSourceGuardReport(),
            safeRequest.durableWriteExecutorReport(),
            blockers
        );
        validateCodeReleaseSwitchContractReport(
            safeRequest.auditContext(),
            safeRequest.writeBodyRebuildReport(),
            safeRequest.writeRequestSpecReport(),
            safeRequest.writeExecutionHandoffReport(),
            safeRequest.codeReleaseSwitchContractReport(),
            blockers
        );
        validateCodeReleaseSwitchRuntimeSourceGuardReport(
            safeRequest.auditContext(),
            safeRequest.codeReleaseSwitchContractReport(),
            safeRequest.codeReleaseSwitchRuntimeSourceGuardReport(),
            blockers
        );
        validateReadinessPlan(safeRequest.readinessPlan(), blockers);
        validateReadinessExecutionReport(safeRequest.readinessExecutionReport(), blockers);
        validateWriteBodyProvenance(safeRequest.writeBodyProvenance(), blockers);
        validateNoFallbackWrite(safeRequest.params(), safeRequest.creationGate(), blockers);

        boolean permitted = blockers.isEmpty();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("stateMachine", "NIM_CREATE_WRITE_GUARD");
        result.put("targetTool", TARGET_TOOL);
        result.put("state", permitted ? "READY_FOR_CONTROLLED_WRITE" : "HELD");
        result.put("writePermitted", permitted);
        result.put("sideEffect", "NONE");
        result.put("nextSideEffectIfExecuted", "POST /api/{orgId}/deployment");
        result.put("blockedBy", blockers);
        result.put("ignoredCallerClaims", detectIgnoredCallerClaims(safeRequest.params()));
        result.put("requiredStages", requiredStages());
        result.put("directPreviewReuseAllowed", false);
        result.put("fallbackWriteAllowed", false);
        result.put("writeBodyRebuildRequired", true);
        result.put("writeRequestSpecRequired", true);
        result.put("writeExecutionHandoffRequired", true);
        result.put("durableWriteExecutorReportRequired", true);
        result.put("codeReleaseSwitchContractReportRequired", true);
        result.put("codeReleaseSwitchContractReportAcceptedForRelease", false);
        result.put("codeReleaseSwitchRuntimeBindingRequired", true);
        result.put("codeReleaseSwitchRuntimeBindingInstalled", false);
        result.put("codeReleaseSwitchRuntimeSourceGuardReportRequired", true);
        result.put("codeReleaseSwitchRuntimeSourceGuardAcceptedForRelease", false);
        result.put("sourceGuardInstalled", false);
        result.put("candidateSourceEvidenceAuthoritative", false);
        result.put("backendQuerySourceAllowedForRelease", false);
        result.put("sysLogBackfillSourceAllowed", false);
        result.put("codeReleaseSwitchDigestVerified", false);
        result.put("releaseDecisionDigestVerified", false);
        result.put("validationResultDigestVerified", false);
        result.put("sourceCodeReleaseSwitchContractDigest",
            text(safeRequest.codeReleaseSwitchContractReport().get("codeReleaseSwitchContractDigest")));
        result.put("sourceGuardMatrixDigest",
            text(safeRequest.codeReleaseSwitchRuntimeSourceGuardReport().get("sourceGuardMatrixDigest")));
        result.put("legacyNimCreateReleasedBooleanAuthoritative", false);
        result.put("readinessExecutionRequired", true);
        result.put("apiKeyPolicy", API_KEY_POLICY);
        return result;
    }

    private static void validateCreationGate(Map<String, Object> creationGate,
                                             List<Map<String, Object>> blockers) {
        if (creationGate.isEmpty()) {
            blockers.add(blocker(
                "CREATION_GATE_MISSING",
                "缺少服务端生成的 NIM creationGate；不能从 Tool 入参自报 gate 通过。",
                "creation-gate"
            ));
            blockers.add(blocker(
                "TRUSTED_POLICY_NOT_PASSED",
                "缺少可信策略快照，尚不能证明 NVAIE license、SYS_ADMIN 和 system org 检查已通过。",
                "backend-policy"
            ));
            return;
        }

        if (!READY_GATE_STATE.equals(text(creationGate.get("gateState")))
            || !Boolean.TRUE.equals(creationGate.get("allowedToCreateNow"))) {
            blockers.add(blocker(
                "CREATION_GATE_NOT_OPEN",
                "creationGate 尚未进入 READY_FOR_SERVER_CONFIRMED_WRITE，不能执行真实 NIM 创建。",
                "creation-gate"
            ));
        }

        Map<String, Object> trustedPolicySnapshot = objectMap(creationGate.get("trustedPolicySnapshot"));
        if (!TRUSTED_POLICY_PASSED.equals(text(trustedPolicySnapshot.get("snapshotState")))) {
            blockers.add(blocker(
                "TRUSTED_POLICY_NOT_PASSED",
                "NIM 创建必须先由后端可信策略确认 NVAIE license 有效，且调用方不是 SYS_ADMIN/system org。",
                "backend-policy"
            ));
        }
        if (!Boolean.TRUE.equals(trustedPolicySnapshot.get("authoritative"))
            || !Boolean.TRUE.equals(trustedPolicySnapshot.get("protectedFromCallerParams"))) {
            blockers.add(blocker(
                "TRUSTED_POLICY_SOURCE_NOT_PROTECTED",
                "可信策略快照必须声明 authoritative=true 且 protectedFromCallerParams=true。",
                "backend-policy"
            ));
        }

        Map<String, Object> futureWritePath = objectMap(creationGate.get("futureWritePath"));
        if (!Boolean.FALSE.equals(futureWritePath.get("directUseOfPreviewAllowed"))) {
            blockers.add(blocker(
                "PREVIEW_DIRECT_POST_BOUNDARY_NOT_LOCKED",
                "creationGate 必须明确 directUseOfPreviewAllowed=false，禁止预检 body 直接 POST。",
                "dto-preview"
            ));
        }
        if (!Boolean.FALSE.equals(futureWritePath.get("fallbackAllowedFromPreflight"))) {
            blockers.add(blocker(
                "FALLBACK_WRITE_BOUNDARY_NOT_LOCKED",
                "creationGate 必须明确 fallbackAllowedFromPreflight=false，禁止从预检跳到 fallback 写 Tool。",
                "agent-safety"
            ));
        }
    }

    private static void validatePreview(Map<String, Object> deploymentBodyPreview,
                                        List<Map<String, Object>> blockers) {
        if (deploymentBodyPreview.isEmpty()) {
            blockers.add(blocker(
                "DEPLOYMENT_BODY_PREVIEW_MISSING",
                "缺少离线 DeploymentDTO 预览；不能构造 NIM 创建写入。",
                "dto-preview"
            ));
            return;
        }
        if (!Boolean.TRUE.equals(deploymentBodyPreview.get("bodyComplete"))) {
            blockers.add(blocker(
                "DEPLOYMENT_BODY_PREVIEW_INCOMPLETE",
                "DeploymentDTO 预览尚不完整，必须先补齐 displayName、image、templateId 与 GPU 解析。",
                "dto-preview"
            ));
        }
        if (!Boolean.FALSE.equals(deploymentBodyPreview.get("safeToPost"))) {
            blockers.add(blocker(
                "PREVIEW_SAFE_TO_POST_MUST_REMAIN_FALSE",
                "预检对象必须保持 safeToPost=false；可提交 body 只能由未来受控写链重新构建。",
                "dto-preview"
            ));
        }

        Map<String, Object> bodyDraft = objectMap(deploymentBodyPreview.get("bodyDraft"));
        List<String> missingFields = new ArrayList<>();
        for (String key : List.of("displayName", "image", "templateId")) {
            if (!hasText(bodyDraft.get(key))) {
                missingFields.add(key);
            }
        }
        if (!missingFields.isEmpty()) {
            blockers.add(blocker(
                "DEPLOYMENT_DRAFT_REQUIRED_FIELDS_MISSING",
                "DeploymentDTO 草案缺少关键字段: " + missingFields,
                "dto-preview"
            ));
        }
    }

    private static void validateHitlConfirmation(HitlConfirmation hitlConfirmation,
                                                 List<Map<String, Object>> blockers) {
        if (hitlConfirmation == null || !hitlConfirmation.allows(TARGET_TOOL)) {
            blockers.add(blocker(
                "HITL_CONFIRMATION_NOT_TRUSTED",
                "必须由 HITLController 注入 target=nim_create 的服务端 HitlConfirmation；Tool 参数中的 confirmed 不可信。",
                "hitl"
            ));
        }
    }

    private static void validateAuditContext(Map<String, Object> auditContext,
                                             List<Map<String, Object>> blockers) {
        if (auditContext.isEmpty()
            || !Boolean.TRUE.equals(auditContext.get("auditPrepared"))
            || !"NIM_CREATE_REQUEST".equals(text(auditContext.get("auditEventType")))
            || !TARGET_TOOL.equals(text(auditContext.get("targetTool")))
            || !TRUSTED_BODY_PROVENANCE.equals(text(auditContext.get("writeBodyProvenance")))
            || !Boolean.TRUE.equals(auditContext.get("secretRedactionApplied"))
            || !API_KEY_POLICY.equals(text(auditContext.get("apiKeyHandling")))
            || missingRequiredFields(auditContext, REQUIRED_AUDIT_FIELDS)) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_NOT_READY",
                "缺少完整审计上下文，必须包含 requestId、conversationId、userId、organizationId、targetTool、可信 body 来源和密钥脱敏策略。",
                "audit"
            ));
        }
        if (containsForbiddenSecretMaterial(auditContext)) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET",
                "审计上下文不得携带 token、password、secret 或真实 NGC/NIM API Key。",
                "audit"
            ));
        }
    }

    private static void validateAuditReceipt(Map<String, Object> auditContext,
                                             Map<String, Object> auditReceipt,
                                             List<Map<String, Object>> blockers) {
        if (auditReceipt.isEmpty()) {
            blockers.add(blocker(
                "AUDIT_RECEIPT_NOT_READY",
                "缺少可信审计 writer 返回的 durable audit receipt；不能只凭 auditContext 进入真实写入。",
                "audit"
            ));
            return;
        }

        boolean contractValid = Boolean.TRUE.equals(auditReceipt.get("auditReceiptPrepared"))
            && REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(auditReceipt.get("receiptStatus")))
            && REQUIRED_AUDIT_STORAGE_MODE.equals(text(auditReceipt.get("storageMode")))
            && Boolean.TRUE.equals(auditReceipt.get("durable"))
            && Boolean.TRUE.equals(auditReceipt.get("realStorageTouched"))
            && Boolean.TRUE.equals(auditReceipt.get("releaseEligible"))
            && auditDigestAlgorithmValid(text(auditReceipt.get("eventDigestAlgorithm")))
            && text(auditReceipt.get("eventDigest")).matches("[a-f0-9]{64}")
            && hasText(auditReceipt.get("receiptId"))
            && TARGET_TOOL.equals(text(auditReceipt.get("targetTool")))
            && NimCreateAuditReadinessSupport.AUDIT_EVENT_TYPE.equals(text(auditReceipt.get("auditEventType")))
            && TRUSTED_BODY_PROVENANCE.equals(text(auditReceipt.get("writeBodyProvenance")))
            && sameAuditIdentity(auditContext, auditReceipt);

        if (!contractValid) {
            blockers.add(blocker(
                "AUDIT_RECEIPT_NOT_DURABLE",
                "NIM 创建必须拿到真实持久化审计 receipt；mock receipt 或身份字段不匹配不能放行。",
                "audit"
            ));
        }
        if (containsForbiddenSecretMaterial(auditReceipt)) {
            blockers.add(blocker(
                "AUDIT_RECEIPT_CONTAINS_FORBIDDEN_SECRET",
                "审计 receipt 不得携带 token、password、secret 或真实 NGC/NIM API Key。",
                "audit"
            ));
        }
    }

    private static void validateReadinessPlan(Map<String, Object> readinessPlan,
                                              List<Map<String, Object>> blockers) {
        if (readinessPlan.isEmpty()
            || !Boolean.TRUE.equals(readinessPlan.get("readinessPollingPrepared"))
            || !Boolean.TRUE.equals(readinessPlan.get("pollOnly"))
            || !API_KEY_POLICY.equals(text(readinessPlan.get("apiKeyHandling")))
            || !Boolean.TRUE.equals(readinessPlan.get("apiKeyPlaceholderOnly"))
            || !containsAllRequiredTargets(readinessPlan.get("targets"), REQUIRED_READINESS_TARGETS)
            || !readinessStepsAreReadOnly(readinessPlan.get("steps"))) {
            blockers.add(blocker(
                "READINESS_PLAN_NOT_READY",
                "缺少创建后只读 readiness 轮询计划，必须覆盖 deployment/service/nim-health，且只能使用只读/派生步骤。",
                "readiness"
            ));
        }
        if (containsForbiddenSecretMaterial(readinessPlan)) {
            blockers.add(blocker(
                "READINESS_PLAN_CONTAINS_FORBIDDEN_SECRET",
                "readiness 计划只能描述轮询目标，不得携带 token、password、secret 或真实 API Key。",
                "readiness"
            ));
        }
    }

    private static void validateWriteBodyRebuildReport(Map<String, Object> auditContext,
                                                       Map<String, Object> auditReceipt,
                                                       Map<String, Object> writeBodyRebuildReport,
                                                       List<Map<String, Object>> blockers) {
        if (writeBodyRebuildReport.isEmpty()) {
            blockers.add(blocker(
                "WRITE_BODY_REBUILD_REPORT_NOT_READY",
                "缺少受控 NIM 写入 body 重建报告；未来真实写入不能只凭 preview bodyDraft 或 provenance 字符串放行。",
                "write-body"
            ));
            return;
        }

        Map<String, Object> body = objectMap(writeBodyRebuildReport.get("body"));
        boolean contractValid = NimCreateWriteBodyRebuilderSupport.REBUILDER_NAME.equals(text(writeBodyRebuildReport.get("writeBodyRebuilder")))
            && NimCreateWriteBodyRebuilderSupport.EXECUTION_MODE.equals(text(writeBodyRebuildReport.get("executionMode")))
            && "NOT_PERFORMED".equals(text(writeBodyRebuildReport.get("networkAccess")))
            && "NONE".equals(text(writeBodyRebuildReport.get("sideEffect")))
            && Boolean.TRUE.equals(writeBodyRebuildReport.get("writeBodyPrepared"))
            && TARGET_TOOL.equals(text(writeBodyRebuildReport.get("targetTool")))
            && "POST".equals(text(writeBodyRebuildReport.get("httpMethod")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(writeBodyRebuildReport.get("backendEndpoint")))
            && TRUSTED_BODY_PROVENANCE.equals(text(writeBodyRebuildReport.get("writeBodyProvenance")))
            && Boolean.FALSE.equals(writeBodyRebuildReport.get("directPreviewReuseAllowed"))
            && Boolean.FALSE.equals(writeBodyRebuildReport.get("previewBodyReferenceUsed"))
            && Boolean.TRUE.equals(writeBodyRebuildReport.get("fieldWhitelistApplied"))
            && Boolean.TRUE.equals(writeBodyRebuildReport.get("protectedContextStripped"))
            && API_KEY_POLICY.equals(text(writeBodyRebuildReport.get("apiKeyHandling")))
            && Boolean.FALSE.equals(writeBodyRebuildReport.get("releaseCredential"))
            && NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM.equals(text(writeBodyRebuildReport.get("bodyDigestAlgorithm")))
            && text(writeBodyRebuildReport.get("bodyDigest")).matches("[a-f0-9]{64}")
            && hasText(writeBodyRebuildReport.get("sourceAuditReceiptId"))
            && text(auditReceipt.get("receiptId")).equals(text(writeBodyRebuildReport.get("sourceAuditReceiptId")))
            && text(auditReceipt.get("eventDigest")).equals(text(writeBodyRebuildReport.get("sourceAuditEventDigest")))
            && text(auditContext.get("requestId")).equals(text(writeBodyRebuildReport.get("sourceRequestId")))
            && text(auditContext.get("conversationId")).equals(text(writeBodyRebuildReport.get("sourceConversationId")))
            && text(auditContext.get("userId")).equals(text(writeBodyRebuildReport.get("sourceUserId")))
            && text(auditContext.get("organizationId")).equals(text(writeBodyRebuildReport.get("organizationId")))
            && listOfMaps(writeBodyRebuildReport.get("blockedBy")).isEmpty()
            && writeBodyContractValid(body);

        if (!contractValid) {
            blockers.add(blocker(
                "WRITE_BODY_REBUILD_REPORT_CONTRACT_INVALID",
                "受控 body 重建报告必须来自 NIM_CREATE_WRITE_BODY_REBUILDER，绑定 audit receipt，并输出已脱敏白名单 DeploymentDTO。",
                "write-body"
            ));
        }
        if (containsForbiddenSecretMaterial(writeBodyRebuildReport)) {
            blockers.add(blocker(
                "WRITE_BODY_REBUILD_REPORT_CONTAINS_FORBIDDEN_SECRET",
                "受控 body 重建报告不得携带 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                "write-body"
            ));
        }
    }

    private static void validateReadinessExecutionReport(Map<String, Object> readinessExecutionReport,
                                                         List<Map<String, Object>> blockers) {
        if (readinessExecutionReport.isEmpty()) {
            blockers.add(blocker(
                "READINESS_EXECUTION_REPORT_NOT_READY",
                "缺少创建后 readiness 只读执行器报告；未来真实写入不能只凭 readiness 计划放行。",
                "readiness"
            ));
            return;
        }

        Map<String, Object> deployment = objectMap(readinessExecutionReport.get("deployment"));
        Map<String, Object> service = objectMap(readinessExecutionReport.get("service"));
        Map<String, Object> health = objectMap(readinessExecutionReport.get("health"));
        Map<String, Object> nextPoll = objectMap(readinessExecutionReport.get("nextPoll"));

        boolean contractValid = NimCreateReadinessExecutorSupport.EXECUTOR_NAME.equals(text(readinessExecutionReport.get("readinessExecutor")))
            && "NONE".equals(text(readinessExecutionReport.get("sideEffect")))
            && Boolean.TRUE.equals(readinessExecutionReport.get("readOnly"))
            && Boolean.TRUE.equals(readinessExecutionReport.get("pollOnly"))
            && API_KEY_POLICY.equals(text(readinessExecutionReport.get("apiKeyHandling")))
            && Boolean.TRUE.equals(readinessExecutionReport.get("apiKeyPlaceholderOnly"))
            && Boolean.TRUE.equals(readinessExecutionReport.get("forbiddenActionsEnforced"))
            && Boolean.TRUE.equals(deployment.get("matched"))
            && Boolean.TRUE.equals(service.get("serviceUrlReady"))
            && Boolean.TRUE.equals(health.get("live"))
            && Boolean.FALSE.equals(nextPoll.get("prepared"));

        if (!contractValid) {
            blockers.add(blocker(
                "READINESS_EXECUTION_REPORT_CONTRACT_INVALID",
                "readiness 执行器报告必须来自受控只读执行器，且声明 readOnly/pollOnly/sideEffect=NONE/API Key 策略和 deployment/service/health 已就绪。",
                "readiness"
            ));
        }

        boolean ready = Boolean.TRUE.equals(readinessExecutionReport.get("ready"))
            && "READY".equals(text(readinessExecutionReport.get("state")))
            && listOfMaps(readinessExecutionReport.get("blockedBy")).isEmpty();
        if (!ready) {
            blockers.add(blocker(
                "READINESS_EXECUTION_REPORT_NOT_READY",
                "readiness 执行器报告尚未进入 READY，不能把 PENDING/TIMEOUT/BLOCKED/REJECTED 结果用于真实写入放行。",
                "readiness"
            ));
        }

        if (reportHasBlockingState(readinessExecutionReport)) {
            blockers.add(blocker(
                "READINESS_EXECUTION_REPORT_BLOCKED",
                "readiness 执行器报告包含阻断态或阻断原因，必须先解决后才能考虑真实 NIM 创建写入。",
                "readiness"
            ));
        }

        if (containsForbiddenSecretMaterial(readinessExecutionReport)) {
            blockers.add(blocker(
                "READINESS_EXECUTION_REPORT_CONTAINS_FORBIDDEN_SECRET",
                "readiness 执行器报告不得携带 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                "readiness"
            ));
        }
    }

    private static void validateWriteRequestSpecReport(Map<String, Object> auditContext,
                                                       Map<String, Object> auditReceipt,
                                                       Map<String, Object> writeBodyRebuildReport,
                                                       Map<String, Object> writeRequestSpecReport,
                                                       List<Map<String, Object>> blockers) {
        if (writeRequestSpecReport.isEmpty()) {
            blockers.add(blocker(
                "WRITE_REQUEST_SPEC_REPORT_NOT_READY",
                "缺少受控 POST request spec 报告；未来真实写入不能从 body 重建报告直接跳到 HTTP client。",
                "write-request-spec"
            ));
            return;
        }

        Map<String, Object> requestSpec = objectMap(writeRequestSpecReport.get("requestSpec"));
        Map<String, Object> requestBody = objectMap(requestSpec.get("body"));
        boolean contractValid = NimCreateWriteRequestSpecAdapterSupport.ADAPTER_NAME.equals(text(writeRequestSpecReport.get("writeRequestSpecAdapter")))
            && NimCreateWriteRequestSpecAdapterSupport.EXECUTION_MODE.equals(text(writeRequestSpecReport.get("executionMode")))
            && "NOT_PERFORMED".equals(text(writeRequestSpecReport.get("networkAccess")))
            && "NONE".equals(text(writeRequestSpecReport.get("sideEffect")))
            && Boolean.TRUE.equals(writeRequestSpecReport.get("writeRequestPrepared"))
            && TARGET_TOOL.equals(text(writeRequestSpecReport.get("targetTool")))
            && "POST".equals(text(writeRequestSpecReport.get("httpMethod")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(writeRequestSpecReport.get("backendEndpoint")))
            && "/api/{orgId}/deployment".equals(text(writeRequestSpecReport.get("pathTemplate")))
            && text(auditContext.get("organizationId")).equals(text(writeRequestSpecReport.get("organizationId")))
            && NimCreateWriteRequestSpecAdapterSupport.CLIENT_BOUNDARY.equals(text(writeRequestSpecReport.get("clientBoundary")))
            && API_KEY_POLICY.equals(text(writeRequestSpecReport.get("apiKeyHandling")))
            && Boolean.FALSE.equals(writeRequestSpecReport.get("releaseCredential"))
            && Boolean.FALSE.equals(writeRequestSpecReport.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(writeRequestSpecReport.get("authorizationHeaderFromCallerAllowed"))
            && Boolean.FALSE.equals(writeRequestSpecReport.get("realApiKeyAllowed"))
            && Boolean.TRUE.equals(writeRequestSpecReport.get("bodyCopiedByValue"))
            && Boolean.FALSE.equals(writeRequestSpecReport.get("bodyMutationAllowed"))
            && NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM.equals(text(writeRequestSpecReport.get("bodyDigestAlgorithm")))
            && text(writeBodyRebuildReport.get("bodyDigest")).equals(text(writeRequestSpecReport.get("bodyDigest")))
            && NimCreateWriteBodyRebuilderSupport.REBUILDER_NAME.equals(text(writeRequestSpecReport.get("sourceWriteBodyRebuilder")))
            && text(auditReceipt.get("receiptId")).equals(text(writeRequestSpecReport.get("sourceAuditReceiptId")))
            && text(auditReceipt.get("eventDigest")).equals(text(writeRequestSpecReport.get("sourceAuditEventDigest")))
            && text(auditContext.get("requestId")).equals(text(writeRequestSpecReport.get("sourceRequestId")))
            && text(auditContext.get("conversationId")).equals(text(writeRequestSpecReport.get("sourceConversationId")))
            && text(auditContext.get("userId")).equals(text(writeRequestSpecReport.get("sourceUserId")))
            && NimCreateWriteRequestSpecAdapterSupport.REQUEST_SPEC_DIGEST_ALGORITHM.equals(text(writeRequestSpecReport.get("requestSpecDigestAlgorithm")))
            && text(writeRequestSpecReport.get("requestSpecDigest")).matches("[a-f0-9]{64}")
            && text(writeRequestSpecReport.get("requestSpecDigest")).equals(digestFor(requestSpec))
            && listOfMaps(writeRequestSpecReport.get("blockedBy")).isEmpty()
            && requestSpecContractValid(auditContext, writeBodyRebuildReport, requestSpec, requestBody);

        if (!contractValid) {
            blockers.add(blocker(
                "WRITE_REQUEST_SPEC_REPORT_CONTRACT_INVALID",
                "受控 POST request spec 报告必须来自 NIM_CREATE_WRITE_REQUEST_SPEC_ADAPTER，绑定 audit receipt/body digest，并禁止调用方 header 和密钥。",
                "write-request-spec"
            ));
        }
        if (containsForbiddenSecretMaterial(writeRequestSpecReport)) {
            blockers.add(blocker(
                "WRITE_REQUEST_SPEC_REPORT_CONTAINS_FORBIDDEN_SECRET",
                "受控 POST request spec 报告不得携带 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                "write-request-spec"
            ));
        }
    }

    private static void validateWriteExecutionHandoffReport(Map<String, Object> auditContext,
                                                            Map<String, Object> auditReceipt,
                                                            Map<String, Object> writeBodyRebuildReport,
                                                            Map<String, Object> writeRequestSpecReport,
                                                            Map<String, Object> writeExecutionHandoffReport,
                                                            List<Map<String, Object>> blockers) {
        if (writeExecutionHandoffReport.isEmpty()) {
            blockers.add(blocker(
                "WRITE_EXECUTION_HANDOFF_REPORT_NOT_READY",
                "缺少受控写执行交接报告；未来真实写入不能从 request spec 直接跳到 durable write executor。",
                "write-execution-handoff"
            ));
            return;
        }

        Map<String, Object> handoffPlan = objectMap(writeExecutionHandoffReport.get("executionHandoffPlan"));
        Map<String, Object> idempotency = objectMap(handoffPlan.get("idempotency"));
        Map<String, Object> preWriteAuditHandoff = objectMap(handoffPlan.get("preWriteAuditHandoff"));
        Map<String, Object> postWriteReadinessHandoff = objectMap(handoffPlan.get("postWriteReadinessHandoff"));
        boolean contractValid = NimCreateWriteExecutionHandoffSupport.HANDOFF_NAME.equals(text(writeExecutionHandoffReport.get("writeExecutionHandoff")))
            && NimCreateWriteExecutionHandoffSupport.EXECUTION_MODE.equals(text(writeExecutionHandoffReport.get("executionMode")))
            && "NOT_PERFORMED".equals(text(writeExecutionHandoffReport.get("networkAccess")))
            && "NONE".equals(text(writeExecutionHandoffReport.get("sideEffect")))
            && Boolean.TRUE.equals(writeExecutionHandoffReport.get("writeExecutionPrepared"))
            && TARGET_TOOL.equals(text(writeExecutionHandoffReport.get("targetTool")))
            && "POST".equals(text(writeExecutionHandoffReport.get("httpMethod")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(writeExecutionHandoffReport.get("backendEndpoint")))
            && "/api/{orgId}/deployment".equals(text(writeExecutionHandoffReport.get("pathTemplate")))
            && text(auditContext.get("organizationId")).equals(text(writeExecutionHandoffReport.get("organizationId")))
            && NimCreateWriteExecutionHandoffSupport.FUTURE_EXECUTOR.equals(text(writeExecutionHandoffReport.get("futureExecutor")))
            && Boolean.FALSE.equals(writeExecutionHandoffReport.get("releaseCredential"))
            && Boolean.FALSE.equals(writeExecutionHandoffReport.get("realHttpExecutionAllowed"))
            && Boolean.TRUE.equals(writeExecutionHandoffReport.get("preWriteAuditRequired"))
            && Boolean.TRUE.equals(writeExecutionHandoffReport.get("idempotencyRequired"))
            && NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE.equals(text(writeExecutionHandoffReport.get("idempotencyKeySource")))
            && text(writeExecutionHandoffReport.get("idempotencyKey")).matches("nim-create-[a-f0-9]{32}")
            && text(writeExecutionHandoffReport.get("idempotencyKey")).equals(
                NimCreateWriteExecutionHandoffSupport.serverDerivedIdempotencyKey(
                    auditContext,
                    auditReceipt,
                    writeRequestSpecReport
                ))
            && Boolean.FALSE.equals(writeExecutionHandoffReport.get("callerIdempotencyKeyAllowed"))
            && Boolean.FALSE.equals(writeExecutionHandoffReport.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(writeExecutionHandoffReport.get("authorizationHeaderFromCallerAllowed"))
            && Boolean.FALSE.equals(writeExecutionHandoffReport.get("realApiKeyAllowed"))
            && text(auditReceipt.get("receiptId")).equals(text(writeExecutionHandoffReport.get("sourceAuditReceiptId")))
            && text(auditReceipt.get("eventDigest")).equals(text(writeExecutionHandoffReport.get("sourceAuditEventDigest")))
            && text(auditContext.get("requestId")).equals(text(writeExecutionHandoffReport.get("sourceRequestId")))
            && text(auditContext.get("conversationId")).equals(text(writeExecutionHandoffReport.get("sourceConversationId")))
            && text(auditContext.get("userId")).equals(text(writeExecutionHandoffReport.get("sourceUserId")))
            && text(writeBodyRebuildReport.get("bodyDigest")).equals(text(writeExecutionHandoffReport.get("sourceBodyDigest")))
            && text(writeRequestSpecReport.get("requestSpecDigest")).equals(text(writeExecutionHandoffReport.get("sourceRequestSpecDigest")))
            && NimCreateWriteExecutionHandoffSupport.HANDOFF_DIGEST_ALGORITHM.equals(text(writeExecutionHandoffReport.get("handoffDigestAlgorithm")))
            && text(writeExecutionHandoffReport.get("handoffDigest")).matches("[a-f0-9]{64}")
            && text(writeExecutionHandoffReport.get("handoffDigest")).equals(digestFor(handoffPlan))
            && listOfMaps(writeExecutionHandoffReport.get("blockedBy")).isEmpty()
            && handoffPlanContractValid(auditContext, auditReceipt, writeBodyRebuildReport, writeRequestSpecReport, handoffPlan, idempotency, preWriteAuditHandoff, postWriteReadinessHandoff);

        if (!contractValid) {
            blockers.add(blocker(
                "WRITE_EXECUTION_HANDOFF_REPORT_CONTRACT_INVALID",
                "受控写执行交接报告必须来自 NIM_CREATE_WRITE_EXECUTION_HANDOFF，绑定 audit receipt/request spec digest，并声明服务端幂等键与写后 readiness handoff。",
                "write-execution-handoff"
            ));
        }
        if (containsForbiddenSecretMaterial(writeExecutionHandoffReport)) {
            blockers.add(blocker(
                "WRITE_EXECUTION_HANDOFF_REPORT_CONTAINS_FORBIDDEN_SECRET",
                "受控写执行交接报告不得携带 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                "write-execution-handoff"
            ));
        }
    }

    private static void validateDurableWriteExecutorReport(Map<String, Object> auditContext,
                                                           Map<String, Object> auditReceipt,
                                                           Map<String, Object> writeBodyRebuildReport,
                                                           Map<String, Object> writeRequestSpecReport,
                                                           Map<String, Object> writeExecutionHandoffReport,
                                                           Map<String, Object> codeReleaseSwitchContractReport,
                                                           Map<String, Object> codeReleaseSwitchRuntimeSourceGuardReport,
                                                           Map<String, Object> durableWriteExecutorReport,
                                                           List<Map<String, Object>> blockers) {
        if (durableWriteExecutorReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_WRITE_EXECUTOR_REPORT_NOT_READY",
                "缺少 durable write executor 报告；未来真实写入不能只凭 handoff 或调用方自报执行结果放行。",
                "durable-write-executor"
            ));
            return;
        }

        Map<String, Object> executionAttemptSpec = objectMap(durableWriteExecutorReport.get("executionAttemptSpec"));
        boolean shellContractValid = NimCreateDurableWriteExecutorSupport.EXECUTOR_NAME.equals(text(durableWriteExecutorReport.get("durableWriteExecutor")))
            && NimCreateDurableWriteExecutorSupport.EXECUTION_MODE.equals(text(durableWriteExecutorReport.get("executionMode")))
            && NimCreateDurableWriteExecutorSupport.HOLD_STATE.equals(text(durableWriteExecutorReport.get("executionState")))
            && TARGET_TOOL.equals(text(durableWriteExecutorReport.get("targetTool")))
            && "POST".equals(text(durableWriteExecutorReport.get("httpMethod")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(durableWriteExecutorReport.get("backendEndpoint")))
            && "/api/{orgId}/deployment".equals(text(durableWriteExecutorReport.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(durableWriteExecutorReport.get("networkAccess")))
            && "NONE".equals(text(durableWriteExecutorReport.get("sideEffect")))
            && Boolean.TRUE.equals(durableWriteExecutorReport.get("inputAccepted"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("executorImplementationAvailable"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("releaseCredential"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("realHttpExecutionAllowed"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("writeAttempted"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("writeExecuted"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("postWriteReadinessTriggered"))
            && Boolean.TRUE.equals(durableWriteExecutorReport.get("codeReleaseSwitchRuntimeSourceGuardReportRequired"))
            && text(codeReleaseSwitchRuntimeSourceGuardReport.get("sourceGuardMatrixDigest")).equals(
                text(durableWriteExecutorReport.get("sourceGuardMatrixDigest")))
            && text(codeReleaseSwitchRuntimeSourceGuardReport.get("sourceRuntimeBindingContractDigest")).equals(
                text(durableWriteExecutorReport.get("sourceRuntimeBindingContractDigest")))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("sourceGuardInstalled"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("candidateSourceEvidenceAuthoritative"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("backendQuerySourceAllowedForRelease"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("sysLogBackfillSourceAllowed"))
            && text(writeExecutionHandoffReport.get("handoffDigest")).equals(text(durableWriteExecutorReport.get("sourceHandoffDigest")))
            && text(writeRequestSpecReport.get("requestSpecDigest")).equals(text(durableWriteExecutorReport.get("sourceRequestSpecDigest")))
            && text(writeBodyRebuildReport.get("bodyDigest")).equals(text(durableWriteExecutorReport.get("sourceBodyDigest")))
            && text(writeExecutionHandoffReport.get("idempotencyKey")).equals(text(durableWriteExecutorReport.get("idempotencyKey")))
            && NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE.equals(text(durableWriteExecutorReport.get("idempotencyKeySource")))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("callerIdempotencyKeyAllowed"))
            && Boolean.TRUE.equals(durableWriteExecutorReport.get("codeReleaseSwitchRuntimeBindingRequired"))
            && text(codeReleaseSwitchContractReport.get("codeReleaseSwitchContractDigest")).equals(
                text(durableWriteExecutorReport.get("sourceCodeReleaseSwitchContractDigest")))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("codeReleaseSwitchDigestVerified"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("releaseDecisionDigestVerified"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("validationResultDigestVerified"))
            && Boolean.FALSE.equals(durableWriteExecutorReport.get("fallbackToStateMachineWritePermittedAllowed"))
            && NimCreateDurableWriteExecutorSupport.EXECUTION_ATTEMPT_SPEC_DIGEST_ALGORITHM.equals(
                text(durableWriteExecutorReport.get("executionAttemptSpecDigestAlgorithm")))
            && text(durableWriteExecutorReport.get("executionAttemptSpecDigest")).matches("[a-f0-9]{64}")
            && text(durableWriteExecutorReport.get("executionAttemptSpecDigest")).equals(
                digestFor(executionAttemptSpec))
            && hasOnlyBlockerCodes(durableWriteExecutorReport.get("blockedBy"),
                List.of(
                    "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD",
                    "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD"
                ))
            && executionAttemptSpecContractValid(auditContext, auditReceipt, writeBodyRebuildReport, writeRequestSpecReport, writeExecutionHandoffReport, executionAttemptSpec);

        if (!shellContractValid) {
            blockers.add(blocker(
                "DURABLE_WRITE_EXECUTOR_REPORT_CONTRACT_INVALID",
                "durable write executor 报告必须来自当前受控合同壳，绑定 handoff/request/body/audit digest，并保持未实现、未联网、未写入。",
                "durable-write-executor"
            ));
        } else {
            blockers.add(blocker(
                "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD",
                "当前 durable write executor 仍是合同壳 IMPLEMENTATION_HOLD，writeExecuted=false；真实 POST 必须等待实现、审计与发布门禁。",
                "durable-write-executor"
            ));
        }

        if (durableExecutorClaimsWriteSuccess(durableWriteExecutorReport)) {
            blockers.add(blocker(
                "DURABLE_WRITE_EXECUTOR_SUCCESS_NOT_TRUSTED",
                "当前版本没有已审计的真实 durable write executor；任何 writeExecuted、deploymentId 或写后 readiness 触发声明都不能作为放行依据。",
                "durable-write-executor"
            ));
        }

        if (containsForbiddenSecretMaterial(durableWriteExecutorReport)) {
            blockers.add(blocker(
                "DURABLE_WRITE_EXECUTOR_REPORT_CONTAINS_FORBIDDEN_SECRET",
                "durable write executor 报告不得携带 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                "durable-write-executor"
            ));
        }
    }

    private static void validateCodeReleaseSwitchContractReport(Map<String, Object> auditContext,
                                                                Map<String, Object> writeBodyRebuildReport,
                                                                Map<String, Object> writeRequestSpecReport,
                                                                Map<String, Object> writeExecutionHandoffReport,
                                                                Map<String, Object> report,
                                                                List<Map<String, Object>> blockers) {
        if (report.isEmpty()) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_NOT_READY",
                "Missing code release switch contract report; nimCreateReleased=true alone cannot authorize the state machine.",
                "code-release-switch-contract"
            ));
            return;
        }

        Map<String, Object> contract = objectMap(report.get("codeReleaseSwitchContract"));
        Map<String, Object> stateMachineBinding = objectMap(contract.get("stateMachineBinding"));
        Map<String, Object> durableExecutorBinding = objectMap(contract.get("durableExecutorBinding"));
        Map<String, Object> template = objectMap(contract.get("currentTemplate"));
        Map<String, Object> prerequisites = objectMap(contract.get("openPrerequisites"));
        Map<String, Object> failure = objectMap(contract.get("failureContract"));
        boolean contractValid = NimCreateDurableAuditCodeReleaseSwitchContractSupport.SWITCH_CONTRACT_NAME.equals(
                text(report.get("durableAuditCodeReleaseSwitchContract")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.EXECUTION_MODE.equals(
                text(report.get("executionMode")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.HOLD_STATE.equals(
                text(report.get("switchState")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.FUTURE_CODE_RELEASE_SWITCH.equals(
                text(report.get("futureCodeReleaseSwitch")))
            && TARGET_TOOL.equals(text(report.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(report.get("backendEndpoint")))
            && "/api/{orgId}/deployment".equals(text(report.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(report.get("networkAccess")))
            && "NONE".equals(text(report.get("sideEffect")))
            && Boolean.TRUE.equals(report.get("inputAccepted"))
            && Boolean.TRUE.equals(report.get("codeReleaseSwitchContractPrepared"))
            && Boolean.TRUE.equals(report.get("serverOwnedCodeReleaseSwitchRequired"))
            && Boolean.TRUE.equals(report.get("reviewedCodeSwitchDigestRequired"))
            && Boolean.TRUE.equals(report.get("serverIssuedReleaseDecisionDigestRequired"))
            && Boolean.TRUE.equals(report.get("serverIssuedValidationResultDigestRequired"))
            && Boolean.FALSE.equals(report.get("callerSwitchEvidenceAuthoritative"))
            && Boolean.FALSE.equals(report.get("legacyConfigFlagAllowed"))
            && Boolean.FALSE.equals(report.get("environmentVariableOverrideAllowed"))
            && Boolean.FALSE.equals(report.get("runtimeToggleOverrideAllowed"))
            && codeReleaseSwitchStatesRemainFalse(report)
            && text(report.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(report.get("codeReleaseSwitchContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("codeReleaseSwitchContractDigest")).equals(digestFor(contract))
            && hasOnlyBlockerCode(report.get("blockedBy"),
                "DURABLE_AUDIT_CODE_RELEASE_SWITCH_CONTRACT_IMPLEMENTATION_HOLD")
            && "REVIEWED_SERVER_OWNED_CODE_RELEASE_SWITCH_REQUIRED".equals(text(contract.get("contractBoundary")))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.FUTURE_CODE_RELEASE_SWITCH.equals(
                text(contract.get("type")))
            && TARGET_TOOL.equals(text(contract.get("targetTool")))
            && Boolean.TRUE.equals(contract.get("futureOnly"))
            && Boolean.FALSE.equals(contract.get("instanceAllowedNow"))
            && "LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH".equals(text(contract.get("currentSwitchState")))
            && "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION".equals(text(contract.get("requiredSwitchState")))
            && Boolean.TRUE.equals(contract.get("serverOwnedRequired"))
            && Boolean.FALSE.equals(contract.get("callerProvidedSwitchAllowed"))
            && Boolean.FALSE.equals(contract.get("environmentOverrideAllowed"))
            && Boolean.FALSE.equals(contract.get("runtimeFlagFallbackAllowed"))
            && stateMachineSwitchBindingValid(stateMachineBinding)
            && durableExecutorSwitchBindingValid(durableExecutorBinding)
            && "LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH".equals(text(template.get("switchState")))
            && Boolean.FALSE.equals(template.get("codeReleaseSwitchDigestVerified"))
            && Boolean.FALSE.equals(template.get("codeReviewDigestVerified"))
            && Boolean.FALSE.equals(template.get("testEvidenceDigestVerified"))
            && Boolean.FALSE.equals(template.get("releaseDecisionDigestVerified"))
            && Boolean.FALSE.equals(template.get("validationResultDigestVerified"))
            && Boolean.FALSE.equals(template.get("stateMachineReleaseBound"))
            && Boolean.FALSE.equals(template.get("durableExecutorReleaseBound"))
            && Boolean.FALSE.equals(template.get("writePermitted"))
            && Boolean.FALSE.equals(template.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(template.get("realHttpExecutionAllowed"))
            && Boolean.TRUE.equals(prerequisites.get("stateMachineRecheckRequired"))
            && Boolean.TRUE.equals(prerequisites.get("durableExecutorRecheckRequired"))
            && Boolean.FALSE.equals(prerequisites.get("currentContractSatisfiesPrerequisites"))
            && Boolean.TRUE.equals(failure.get("failClosed"))
            && Boolean.FALSE.equals(failure.get("fallbackToCallerSwitchAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToEnvironmentVariableAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToRuntimeFlagAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToReleaseDecisionContractAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToStateMachineBooleanAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToExecutorSuccessAllowed"));

        if (!contractValid) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_CONTRACT_INVALID",
                "Code release switch contract report must come from the M5.21-72 HOLD contract with recomputable digest and all release states false.",
                "code-release-switch-contract"
            ));
        } else {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_IMPLEMENTATION_HOLD",
                "Code release switch contract report is accepted as shape evidence only; the real runtime switch binding is still not installed.",
                "code-release-switch-contract"
            ));
        }

        if (!codeReleaseSwitchContractDigestsMatchWriteChain(report, writeBodyRebuildReport, writeRequestSpecReport,
            writeExecutionHandoffReport)) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_WRITE_CHAIN_DIGEST_MISMATCH",
                "Code release switch contract report must advertise future binding of the same controlled body, request and handoff digests.",
                "code-release-switch-contract"
            ));
        }

        if (codeReleaseSwitchContractClaimsRelease(report)) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_RELEASE_CLAIM_NOT_TRUSTED",
                "Current code release switch report is a HOLD contract; switch/open/write-success claims are not trusted release evidence.",
                "code-release-switch-contract"
            ));
        }

        if (containsForbiddenSecretMaterial(report)) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_CONTRACT_REPORT_CONTAINS_FORBIDDEN_SECRET",
                "Code release switch contract report must not contain Authorization, token, password, secret, or real NGC/NIM API key material.",
                "code-release-switch-contract"
            ));
        }
    }

    private static void validateCodeReleaseSwitchRuntimeSourceGuardReport(Map<String, Object> auditContext,
                                                                          Map<String, Object> codeSwitchReport,
                                                                          Map<String, Object> report,
                                                                          List<Map<String, Object>> blockers) {
        if (report.isEmpty()) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_READY",
                "Missing M5.21-75 runtime source guard matrix; state machine cannot distinguish planning evidence from release source.",
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

        boolean contractValid = NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.SOURCE_GUARD_CONTRACT_NAME.equals(
                text(report.get("codeReleaseSwitchRuntimeSourceGuard")))
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.EXECUTION_MODE.equals(
                text(report.get("executionMode")))
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.HOLD_STATE.equals(
                text(report.get("guardState")))
            && TARGET_TOOL.equals(text(report.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(report.get("backendEndpoint")))
            && "/api/{orgId}/deployment".equals(text(report.get("pathTemplate")))
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
                "codeReleaseSwitchDigestVerified",
                "writeExecuted",
                "deploymentId",
                "writeResult"
            ))
            && text(codeSwitchReport.get("codeReleaseSwitchContractDigest")).equals(
                text(report.get("sourceCodeReleaseSwitchContractDigest")))
            && text(codeSwitchReport.get("trustedPrincipalDigest")).equals(text(report.get("trustedPrincipalDigest")))
            && text(report.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(report.get("sourceRuntimeBindingContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceGuardMatrixDigest")).matches("[a-f0-9]{64}")
            && text(report.get("sourceGuardMatrixDigest")).equals(digestFor(contract))
            && hasOnlyBlockerCode(report.get("blockedBy"),
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD")
            && "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REQUIRED".equals(text(contract.get("contractBoundary")))
            && TARGET_TOOL.equals(text(contract.get("targetTool")))
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
            && Boolean.FALSE.equals(failure.get("fallbackToCallerParamAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToLlmJsonAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToEnvironmentVariableAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToRuntimeFlagAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToStateMachineWritePermittedAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToDurableExecutorSuccessAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToBackendQueryResultAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToStorageBackfillAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToContractReportOnlyAllowed"))
            && matrixContainsSource(matrix, "REVIEWED_SERVER_OWNED_OPEN_SWITCH", true)
            && matrixContainsSource(matrix, "STATE_MACHINE_WRITE_PERMITTED_BOOLEAN", false)
            && matrixContainsSource(matrix, "DURABLE_EXECUTOR_SUCCESS_OR_DEPLOYMENT_ID", false)
            && matrixContainsSource(matrix, "BACKEND_QUERY_OR_READBACK_RESULT", false);

        if (!contractValid) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_CONTRACT_INVALID",
                "Runtime source guard report must come from the M5.21-75 HOLD matrix and bind the same switch/audit digest.",
                "code-release-switch-runtime-source-guard"
            ));
        } else {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD",
                "Runtime source guard matrix is accepted as required guard evidence only; no reviewed open switch source exists yet.",
                "code-release-switch-runtime-source-guard"
            ));
        }

        if (runtimeSourceGuardClaimsRelease(report)) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_RELEASE_CLAIM_NOT_TRUSTED",
                "Source guard matrix cannot claim installed guard, accepted release source, write permission, executor success, readback release, or storage backfill release.",
                "code-release-switch-runtime-source-guard"
            ));
        }

        if (containsForbiddenSecretMaterial(report)) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_CONTAINS_FORBIDDEN_SECRET",
                "Runtime source guard report must not contain Authorization, token, password, secret, or real NGC/NIM API key material.",
                "code-release-switch-runtime-source-guard"
            ));
        }
    }

    private static void validateWriteBodyProvenance(String writeBodyProvenance,
                                                    List<Map<String, Object>> blockers) {
        String provenance = text(writeBodyProvenance);
        if ("PREVIEW_BODY_DIRECT_REUSE".equals(provenance)) {
            blockers.add(blocker(
                "PREVIEW_DIRECT_REUSE_BLOCKED",
                "禁止把 deploymentBodyPreview.bodyDraft 直接当成 POST body；未来写链必须从已审计状态重新构建。",
                "dto-preview"
            ));
            return;
        }
        if (!TRUSTED_BODY_PROVENANCE.equals(provenance)) {
            blockers.add(blocker(
                "WRITE_BODY_PROVENANCE_NOT_TRUSTED",
                "缺少可信写入 body 来源标记，不能证明 POST body 来自受控 NIM 状态机。",
                "dto-preview"
            ));
        }
    }

    private static void validateNoFallbackWrite(Map<String, Object> params,
                                                Map<String, Object> creationGate,
                                                List<Map<String, Object>> blockers) {
        if (Boolean.TRUE.equals(params.get("useFallback"))
            || "deploy_create_instance".equals(text(params.get("fallbackTool")))) {
            blockers.add(blocker(
                "FALLBACK_WRITE_FORBIDDEN",
                "NIM preflight 不能降级调用 deploy_create_instance；必须等待已审计 nim_create 写链。",
                "agent-safety"
            ));
        }

        Map<String, Object> futureWritePath = objectMap(creationGate.get("futureWritePath"));
        if (Boolean.TRUE.equals(futureWritePath.get("fallbackAllowedFromPreflight"))) {
            blockers.add(blocker(
                "FALLBACK_WRITE_FORBIDDEN",
                "creationGate 中的 fallbackAllowedFromPreflight 不能为 true。",
                "agent-safety"
            ));
        }
    }

    private static List<Map<String, Object>> detectIgnoredCallerClaims(Map<String, Object> params) {
        List<String> riskyKeys = List.of(
            "approved",
            "confirmed",
            "hitlConfirmed",
            "hitlConfirmation",
            "safeToPost",
            "writePermitted",
            "writeExecutionAllowed",
            "realHttpExecutionAllowed",
            "releaseEligible",
            "releaseDecision",
            "releaseCredential",
            "releaseCredentialIssued",
            "validationResult",
            "writeBodyRebuildReport",
            "writeBodyRebuilder",
            "writeBodyPrepared",
            "bodyDigest",
            "rebuiltBody",
            "writeRequestSpecReport",
            "writeRequestSpecAdapter",
            "writeRequestPrepared",
            "requestSpec",
            "requestSpecDigest",
            "writeExecutionHandoffReport",
            "writeExecutionHandoff",
            "writeExecutionPrepared",
            "executionHandoffPlan",
            "handoffDigest",
            "idempotencyKey",
            "idempotency",
            "durableWriteExecutorReport",
            "durableWriteExecutor",
            "executorImplementationAvailable",
            "nimCreateReleased",
            "codeReleaseSwitch",
            "codeReleaseSwitchOpened",
            "codeReleaseSwitchDigest",
            "sourceGuardInstalled",
            "backendQuerySourceAllowedForRelease",
            "sysLogBackfillSourceAllowed",
            "writeAttempted",
            "writeExecuted",
            "writeResult",
            "deploymentId",
            "deploymentUid",
            "postWriteReadinessTriggered",
            "Authorization",
            "headers",
            "creationGate",
            "trustedPolicySnapshot",
            "auditPrepared",
            "auditReceipt",
            "auditReceiptPrepared",
            "receiptStatus",
            "receiptId",
            "readinessExecutionReport",
            "readinessExecutor",
            "readinessReady",
            "readinessState",
            "licenseValid",
            "nvaieLicenseValid",
            "nvaieLicenseVerified",
            "isSysOrg",
            "sysAdmin",
            "role",
            "roles",
            "organizationId",
            "orgId",
            "trustedPolicySource",
            "authoritative",
            "fallbackTool",
            "useFallback"
        );
        List<Map<String, Object>> ignored = new ArrayList<>();
        for (String key : riskyKeys) {
            if (params.containsKey(key)) {
                Map<String, Object> claim = new LinkedHashMap<>();
                claim.put("key", key);
                claim.put("ignored", true);
                claim.put("reason", "该字段来自 Tool 入参，不能作为 NIM 创建状态机的授权、审计、HITL 或 fallback 依据。");
                ignored.add(claim);
            }
        }
        return ignored;
    }

    private static List<String> requiredStages() {
        return List.of(
            "public preflight 只能生成 safeToPost=false 的 DeploymentDTO 草案",
            "后端可信策略快照必须为 TRUSTED_PASSED",
            "creationGate 必须由后端状态机进入 READY_FOR_SERVER_CONFIRMED_WRITE",
            "HITLController 必须注入 target=nim_create 的服务端 HitlConfirmation",
            "写入前必须准备完整审计上下文",
            "审计上下文必须先被持久化审计 writer 接收，并返回 durable audit receipt",
            "POST body 必须由受控重建器输出白名单 DeploymentDTO，并绑定 durable audit receipt",
            "POST body 必须由受控 NIM 状态机重新构建，不能直接复用 preview bodyDraft",
            "受控写执行交接之后必须产出 durable write executor 报告，不能只凭 handoff 放行",
            "当前 durable write executor shell 仍为 IMPLEMENTATION_HOLD/writeExecuted=false，真实写入必须等待实现审计",
            "code release switch runtime binding 必须由状态机复算 switch digest，旧 nimCreateReleased 布尔值不能单独授权",
            "创建后 readiness 只能只读轮询，且必须由受控 readiness executor 返回 READY 报告",
            "readiness executor 报告不得生成、保存、展示或携带真实 API Key",
            "nim_create 代码级 release 开关必须显式打开"
        );
    }

    private static boolean missingRequiredFields(Map<String, Object> map, Set<String> requiredFields) {
        for (String field : requiredFields) {
            if (!hasText(map.get(field))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAllRequiredTargets(Object rawTargets, Set<String> requiredTargets) {
        if (!(rawTargets instanceof List<?> targets)) {
            return false;
        }
        Set<String> actualTargets = new java.util.HashSet<>();
        for (Object target : targets) {
            actualTargets.add(text(target));
        }
        return actualTargets.containsAll(requiredTargets);
    }

    private static boolean sameAuditIdentity(Map<String, Object> auditContext,
                                             Map<String, Object> auditReceipt) {
        for (String key : List.of(
            "auditEventType",
            "requestId",
            "conversationId",
            "userId",
            "organizationId",
            "targetTool",
            "writeBodyProvenance"
        )) {
            if (!text(auditContext.get(key)).equals(text(auditReceipt.get(key)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean auditDigestAlgorithmValid(String algorithm) {
        return NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(algorithm);
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

    private static boolean requestSpecContractValid(Map<String, Object> auditContext,
                                                    Map<String, Object> writeBodyRebuildReport,
                                                    Map<String, Object> requestSpec,
                                                    Map<String, Object> requestBody) {
        String organizationId = text(auditContext.get("organizationId"));
        return !requestSpec.isEmpty()
            && hasOnlyKeys(requestSpec, REQUEST_SPEC_KEYS)
            && "deployment-create".equals(text(requestSpec.get("target")))
            && "POST".equals(text(requestSpec.get("method")))
            && "/api/{orgId}/deployment".equals(text(requestSpec.get("endpoint")))
            && "/api/{orgId}/deployment".equals(text(requestSpec.get("pathTemplate")))
            && ("/api/" + organizationId + "/deployment").equals(text(requestSpec.get("resolvedPath")))
            && NimCreateWriteRequestSpecAdapterSupport.CLIENT_BOUNDARY.equals(text(requestSpec.get("clientBoundary")))
            && Boolean.FALSE.equals(requestSpec.get("queryAllowed"))
            && objectMap(requestSpec.get("query")).isEmpty()
            && Boolean.TRUE.equals(requestSpec.get("bodyAllowed"))
            && Boolean.TRUE.equals(requestSpec.get("bodyRequired"))
            && "CONTROLLED_REBUILDER_BODY_COPY".equals(text(requestSpec.get("bodySource")))
            && NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM.equals(text(requestSpec.get("bodyDigestAlgorithm")))
            && text(writeBodyRebuildReport.get("bodyDigest")).equals(text(requestSpec.get("bodyDigest")))
            && Boolean.FALSE.equals(requestSpec.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(requestSpec.get("authorizationHeaderFromCallerAllowed"))
            && "KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY".equals(text(requestSpec.get("kubeManagerAuthBoundary")))
            && Boolean.FALSE.equals(requestSpec.get("realApiKeyAllowed"))
            && API_KEY_POLICY.equals(text(requestSpec.get("apiKeyHandling")))
            && Boolean.TRUE.equals(requestSpec.get("idempotencyKeyRequiredBeforeExecution"))
            && "FUTURE_DURABLE_WRITE_EXECUTOR".equals(text(requestSpec.get("executionAdapterRequired")))
            && "NONE".equals(text(requestSpec.get("sideEffect")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(requestSpec.get("futureSideEffectIfExecuted")))
            && requestBody.equals(objectMap(writeBodyRebuildReport.get("body")))
            && writeBodyContractValid(requestBody);
    }

    private static boolean handoffPlanContractValid(Map<String, Object> auditContext,
                                                    Map<String, Object> auditReceipt,
                                                    Map<String, Object> writeBodyRebuildReport,
                                                    Map<String, Object> writeRequestSpecReport,
                                                    Map<String, Object> handoffPlan,
                                                    Map<String, Object> idempotency,
                                                    Map<String, Object> preWriteAuditHandoff,
                                                    Map<String, Object> postWriteReadinessHandoff) {
        String organizationId = text(auditContext.get("organizationId"));
        Map<String, Object> retryPolicy = objectMap(handoffPlan.get("retryPolicy"));
        return !handoffPlan.isEmpty()
            && hasOnlyKeys(handoffPlan, HANDOFF_PLAN_KEYS)
            && hasOnlyKeys(idempotency, IDEMPOTENCY_KEYS)
            && hasOnlyKeys(preWriteAuditHandoff, PRE_WRITE_AUDIT_HANDOFF_KEYS)
            && hasOnlyKeys(postWriteReadinessHandoff, POST_WRITE_READINESS_HANDOFF_KEYS)
            && hasOnlyKeys(retryPolicy, RETRY_POLICY_KEYS)
            && "deployment-create".equals(text(handoffPlan.get("target")))
            && "POST".equals(text(handoffPlan.get("method")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(handoffPlan.get("backendEndpoint")))
            && "/api/{orgId}/deployment".equals(text(handoffPlan.get("pathTemplate")))
            && ("/api/" + organizationId + "/deployment").equals(text(handoffPlan.get("resolvedPath")))
            && NimCreateWriteExecutionHandoffSupport.FUTURE_EXECUTOR.equals(text(handoffPlan.get("futureExecutor")))
            && "NOT_PERFORMED".equals(text(handoffPlan.get("networkAccess")))
            && "NONE".equals(text(handoffPlan.get("sideEffect")))
            && text(writeRequestSpecReport.get("requestSpecDigest")).equals(text(handoffPlan.get("requestSpecDigest")))
            && text(writeBodyRebuildReport.get("bodyDigest")).equals(text(handoffPlan.get("bodyDigest")))
            && Boolean.FALSE.equals(handoffPlan.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(handoffPlan.get("authorizationHeaderFromCallerAllowed"))
            && Boolean.FALSE.equals(handoffPlan.get("realApiKeyAllowed"))
            && "KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY".equals(text(handoffPlan.get("kubeManagerAuthBoundary")))
            && Boolean.TRUE.equals(idempotency.get("required"))
            && text(idempotency.get("key")).equals(
                NimCreateWriteExecutionHandoffSupport.serverDerivedIdempotencyKey(
                    auditContext,
                    auditReceipt,
                    writeRequestSpecReport
                ))
            && NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE.equals(text(idempotency.get("keySource")))
            && Boolean.FALSE.equals(idempotency.get("callerKeyAllowed"))
            && Boolean.TRUE.equals(idempotency.get("reuseAllowedOnlyForSameAuditReceiptAndRequestSpec"))
            && Boolean.TRUE.equals(preWriteAuditHandoff.get("required"))
            && text(auditReceipt.get("receiptId")).equals(text(preWriteAuditHandoff.get("receiptId")))
            && text(auditReceipt.get("eventDigest")).equals(text(preWriteAuditHandoff.get("eventDigest")))
            && REQUIRED_AUDIT_STORAGE_MODE.equals(text(preWriteAuditHandoff.get("storageMode")))
            && REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(preWriteAuditHandoff.get("receiptStatus")))
            && Boolean.TRUE.equals(preWriteAuditHandoff.get("durable"))
            && Boolean.TRUE.equals(preWriteAuditHandoff.get("realStorageTouched"))
            && Boolean.TRUE.equals(postWriteReadinessHandoff.get("requiredAfterWrite"))
            && NimCreateReadinessExecutorSupport.EXECUTOR_NAME.equals(text(postWriteReadinessHandoff.get("nextExecutor")))
            && Boolean.TRUE.equals(postWriteReadinessHandoff.get("pollOnly"))
            && Boolean.TRUE.equals(postWriteReadinessHandoff.get("readOnly"))
            && API_KEY_POLICY.equals(text(postWriteReadinessHandoff.get("apiKeyHandling")))
            && Boolean.TRUE.equals(postWriteReadinessHandoff.get("forbiddenBeforeWrite"))
            && Boolean.FALSE.equals(retryPolicy.get("retryAllowed"))
            && Boolean.TRUE.equals(retryPolicy.get("retryAllowedOnlyWithSameIdempotencyKey"))
            && "1".equals(text(retryPolicy.get("maxAttemptsBeforeExecutorImplementation")));
    }

    private static boolean executionAttemptSpecContractValid(Map<String, Object> auditContext,
                                                             Map<String, Object> auditReceipt,
                                                             Map<String, Object> writeBodyRebuildReport,
                                                             Map<String, Object> writeRequestSpecReport,
                                                             Map<String, Object> writeExecutionHandoffReport,
                                                             Map<String, Object> executionAttemptSpec) {
        String organizationId = text(auditContext.get("organizationId"));
        Map<String, Object> sourceRequestSpec = objectMap(writeRequestSpecReport.get("requestSpec"));
        Map<String, Object> sourceBody = objectMap(writeBodyRebuildReport.get("body"));
        Map<String, Object> sourceHandoffPlan = objectMap(writeExecutionHandoffReport.get("executionHandoffPlan"));
        Map<String, Object> attemptRequestSpec = objectMap(executionAttemptSpec.get("requestSpec"));
        Map<String, Object> attemptBody = objectMap(executionAttemptSpec.get("body"));
        Map<String, Object> attemptRequestBody = objectMap(attemptRequestSpec.get("body"));
        Map<String, Object> attemptHandoffPlan = objectMap(executionAttemptSpec.get("executionHandoffPlan"));
        return !executionAttemptSpec.isEmpty()
            && hasOnlyKeys(executionAttemptSpec, EXECUTION_ATTEMPT_SPEC_KEYS)
            && "deployment-create".equals(text(executionAttemptSpec.get("target")))
            && "POST".equals(text(executionAttemptSpec.get("method")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(executionAttemptSpec.get("backendEndpoint")))
            && "/api/{orgId}/deployment".equals(text(executionAttemptSpec.get("pathTemplate")))
            && ("/api/" + organizationId + "/deployment").equals(text(executionAttemptSpec.get("resolvedPath")))
            && Boolean.TRUE.equals(executionAttemptSpec.get("requestSpecCopiedByValue"))
            && NimCreateWriteRequestSpecAdapterSupport.REQUEST_SPEC_DIGEST_ALGORITHM.equals(
                text(executionAttemptSpec.get("requestSpecDigestAlgorithm")))
            && text(writeRequestSpecReport.get("requestSpecDigest")).equals(text(executionAttemptSpec.get("requestSpecDigest")))
            && text(writeRequestSpecReport.get("requestSpecDigest")).equals(digestFor(attemptRequestSpec))
            && sourceRequestSpec.equals(attemptRequestSpec)
            && Boolean.TRUE.equals(executionAttemptSpec.get("bodyCopiedByValue"))
            && NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM.equals(
                text(executionAttemptSpec.get("bodyDigestAlgorithm")))
            && text(writeBodyRebuildReport.get("bodyDigest")).equals(text(executionAttemptSpec.get("bodyDigest")))
            && text(writeBodyRebuildReport.get("bodyDigest")).equals(digestFor(attemptBody))
            && sourceBody.equals(attemptBody)
            && attemptBody.equals(attemptRequestBody)
            && writeBodyContractValid(attemptBody)
            && Boolean.TRUE.equals(executionAttemptSpec.get("executionHandoffPlanCopiedByValue"))
            && NimCreateWriteExecutionHandoffSupport.HANDOFF_DIGEST_ALGORITHM.equals(
                text(executionAttemptSpec.get("handoffDigestAlgorithm")))
            && text(writeExecutionHandoffReport.get("handoffDigest")).equals(text(executionAttemptSpec.get("handoffDigest")))
            && text(writeExecutionHandoffReport.get("handoffDigest")).equals(digestFor(attemptHandoffPlan))
            && sourceHandoffPlan.equals(attemptHandoffPlan)
            && text(writeExecutionHandoffReport.get("idempotencyKey")).equals(text(executionAttemptSpec.get("idempotencyKey")))
            && text(executionAttemptSpec.get("idempotencyKey")).equals(
                NimCreateWriteExecutionHandoffSupport.serverDerivedIdempotencyKey(
                    auditContext,
                    auditReceipt,
                    writeRequestSpecReport
                ))
            && NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE.equals(text(executionAttemptSpec.get("idempotencyKeySource")))
            && text(auditReceipt.get("receiptId")).equals(text(executionAttemptSpec.get("auditReceiptId")))
            && text(auditReceipt.get("eventDigest")).equals(text(executionAttemptSpec.get("auditEventDigest")))
            && "KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY".equals(text(executionAttemptSpec.get("kubeManagerAuthBoundary")))
            && Boolean.FALSE.equals(executionAttemptSpec.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(executionAttemptSpec.get("authorizationHeaderFromCallerAllowed"))
            && Boolean.FALSE.equals(executionAttemptSpec.get("realApiKeyAllowed"))
            && NimCreateReadinessExecutorSupport.EXECUTOR_NAME.equals(text(executionAttemptSpec.get("postWriteReadinessExecutor")))
            && Boolean.FALSE.equals(executionAttemptSpec.get("writeWillBeAttempted"));
    }

    private static boolean durableExecutorClaimsWriteSuccess(Map<String, Object> durableWriteExecutorReport) {
        return Boolean.TRUE.equals(durableWriteExecutorReport.get("executorImplementationAvailable"))
            || Boolean.TRUE.equals(durableWriteExecutorReport.get("writeAttempted"))
            || Boolean.TRUE.equals(durableWriteExecutorReport.get("writeExecuted"))
            || Boolean.TRUE.equals(durableWriteExecutorReport.get("postWriteReadinessTriggered"))
            || hasText(durableWriteExecutorReport.get("deploymentId"))
            || hasText(durableWriteExecutorReport.get("deploymentUid"))
            || !objectMap(durableWriteExecutorReport.get("writeResult")).isEmpty();
    }

    private static boolean matrixContainsSource(List<Map<String, Object>> matrix,
                                                String source,
                                                boolean futureAuthoritativeCandidate) {
        for (Map<String, Object> row : matrix) {
            if (source.equals(text(row.get("source")))
                && Boolean.valueOf(futureAuthoritativeCandidate).equals(row.get("futureAuthoritativeCandidate"))
                && Boolean.FALSE.equals(row.get("authoritativeForReleaseNow"))
                && Boolean.FALSE.equals(row.get("writePermittedAllowedNow"))
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
            || Boolean.TRUE.equals(report.get("writePermitted"))
            || Boolean.TRUE.equals(report.get("writeExecutionAllowed"))
            || Boolean.TRUE.equals(report.get("writeExecuted"))
            || hasText(report.get("deploymentId"))
            || hasText(report.get("deploymentUid"))
            || !objectMap(report.get("writeResult")).isEmpty();
    }

    private static boolean codeReleaseSwitchStatesRemainFalse(Map<String, Object> report) {
        return Boolean.FALSE.equals(report.get("realCodeReleaseSwitchCreated"))
            && Boolean.FALSE.equals(report.get("realCodeReleaseSwitchOpened"))
            && Boolean.FALSE.equals(report.get("serverOwnedCodeReleaseSwitchAccepted"))
            && Boolean.FALSE.equals(report.get("codeReleaseSwitchDigestVerified"))
            && Boolean.FALSE.equals(report.get("codeReviewDigestVerified"))
            && Boolean.FALSE.equals(report.get("testEvidenceDigestVerified"))
            && Boolean.FALSE.equals(report.get("releaseDecisionDigestVerified"))
            && Boolean.FALSE.equals(report.get("validationResultDigestVerified"))
            && Boolean.FALSE.equals(report.get("trustedPrincipalValidated"))
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

    private static boolean stateMachineSwitchBindingValid(Map<String, Object> binding) {
        return "NimCreateStateMachineSupport".equals(text(binding.get("target")))
            && Boolean.TRUE.equals(binding.get("futureCodeReleaseSwitchDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureReleaseDecisionDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureValidationResultDigestRequired"))
            && Boolean.TRUE.equals(binding.get("mustRecomputeSwitchDigestBeforeWritePermitted"))
            && Boolean.FALSE.equals(binding.get("fallbackToRuntimeFlagAllowed"))
            && Boolean.FALSE.equals(binding.get("fallbackToEnvironmentVariableAllowed"))
            && Boolean.FALSE.equals(binding.get("writePermittedCanBeTrueNow"));
    }

    private static boolean durableExecutorSwitchBindingValid(Map<String, Object> binding) {
        return NimCreateDurableWriteExecutorSupport.EXECUTOR_NAME.equals(text(binding.get("target")))
            && Boolean.TRUE.equals(binding.get("futureCodeReleaseSwitchDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureReleaseDecisionDigestRequired"))
            && Boolean.TRUE.equals(binding.get("futureValidationResultDigestRequired"))
            && Boolean.TRUE.equals(binding.get("mustRecheckImmediatelyBeforePost"))
            && Boolean.FALSE.equals(binding.get("fallbackToStateMachineFlagOnlyAllowed"))
            && Boolean.FALSE.equals(binding.get("writeExecutionAllowedNow"));
    }

    private static boolean codeReleaseSwitchContractDigestsMatchWriteChain(Map<String, Object> report,
                                                                           Map<String, Object> writeBodyRebuildReport,
                                                                           Map<String, Object> writeRequestSpecReport,
                                                                           Map<String, Object> writeExecutionHandoffReport) {
        Map<String, Object> contract = objectMap(report.get("codeReleaseSwitchContract"));
        List<String> fields = stringList(contract.get("requiredFutureEvidenceDigestFields"));
        return fields.containsAll(List.of(
            "bodyDigest",
            "requestSpecDigest",
            "handoffDigest",
            "auditReceiptId",
            "serverDerivedIdempotencyKey"
        ))
            && hasText(writeBodyRebuildReport.get("bodyDigest"))
            && hasText(writeRequestSpecReport.get("requestSpecDigest"))
            && hasText(writeExecutionHandoffReport.get("handoffDigest"));
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

    private static boolean hasOnlyBlockerCode(Object rawBlockers, String code) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1 && code.equals(text(blockers.get(0).get("code")));
    }

    private static boolean hasOnlyBlockerCodes(Object rawBlockers, List<String> expectedCodes) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        if (blockers.size() != expectedCodes.size()) {
            return false;
        }
        List<String> actualCodes = new ArrayList<>();
        for (Map<String, Object> blocker : blockers) {
            actualCodes.add(text(blocker.get("code")));
        }
        return actualCodes.containsAll(expectedCodes);
    }

    private static boolean listIsEmpty(Object value) {
        return value instanceof List<?> list && list.isEmpty();
    }

    private static boolean hasOnlyKeys(Map<String, Object> map, Set<String> allowedKeys) {
        return map.keySet().equals(allowedKeys);
    }

    private static String digestFor(Map<String, Object> value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(NimCreateWriteRequestSpecAdapterSupport.REQUEST_SPEC_DIGEST_ALGORITHM);
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

    private static boolean readinessStepsAreReadOnly(Object rawSteps) {
        if (!(rawSteps instanceof List<?> steps) || steps.isEmpty()) {
            return false;
        }
        boolean hasDeploymentRead = false;
        boolean hasNimHealthRead = false;
        boolean hasNimModelsRead = false;
        for (Object item : steps) {
            Map<String, Object> step = objectMap(item);
            String target = text(step.get("target"));
            String method = text(step.get("method"));
            if (!List.of("GET", "EXTRACT_FROM_DEPLOYMENT_RESPONSE").contains(method)) {
                return false;
            }
            if ("deployment".equals(target) && "GET".equals(method)) {
                hasDeploymentRead = true;
            }
            if ("nim-health".equals(target) && "GET".equals(method)) {
                hasNimHealthRead = true;
            }
            if ("nim-models".equals(target) && "GET".equals(method)) {
                hasNimModelsRead = true;
            }
        }
        return hasDeploymentRead && hasNimHealthRead && hasNimModelsRead;
    }

    private static boolean reportHasBlockingState(Map<String, Object> report) {
        if (!listOfMaps(report.get("blockedBy")).isEmpty()) {
            return true;
        }
        return List.of("BLOCKED", "REJECTED", "TIMEOUT").contains(text(report.get("state")));
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

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        return NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(map, SECRET_DETECTION_POLICY);
    }

    private static Map<String, Object> blocker(String code, String message, String source) {
        Map<String, Object> blocker = new LinkedHashMap<>();
        blocker.put("code", code);
        blocker.put("message", message);
        blocker.put("source", source);
        return blocker;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return objectMap(map);
    }

    private static Map<String, Object> objectMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(String.valueOf(key), value));
        return copy;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record ReadinessRequest(
        Map<String, Object> params,
        Map<String, Object> creationGate,
        Map<String, Object> deploymentBodyPreview,
        HitlConfirmation hitlConfirmation,
        Map<String, Object> auditContext,
        Map<String, Object> auditReceipt,
        Map<String, Object> writeBodyRebuildReport,
        Map<String, Object> writeRequestSpecReport,
        Map<String, Object> writeExecutionHandoffReport,
        Map<String, Object> codeReleaseSwitchContractReport,
        Map<String, Object> codeReleaseSwitchRuntimeSourceGuardReport,
        Map<String, Object> durableWriteExecutorReport,
        Map<String, Object> readinessPlan,
        Map<String, Object> readinessExecutionReport,
        String writeBodyProvenance,
        boolean nimCreateReleased
    ) {
        ReadinessRequest(Map<String, Object> params,
                         Map<String, Object> creationGate,
                         Map<String, Object> deploymentBodyPreview,
                         HitlConfirmation hitlConfirmation,
                         Map<String, Object> auditContext,
                         Map<String, Object> auditReceipt,
                         Map<String, Object> writeBodyRebuildReport,
                         Map<String, Object> writeRequestSpecReport,
                         Map<String, Object> writeExecutionHandoffReport,
                         Map<String, Object> readinessPlan,
                         Map<String, Object> readinessExecutionReport,
                         String writeBodyProvenance,
                         boolean nimCreateReleased) {
            this(
                params,
                creationGate,
                deploymentBodyPreview,
                hitlConfirmation,
                auditContext,
                auditReceipt,
                writeBodyRebuildReport,
                writeRequestSpecReport,
                writeExecutionHandoffReport,
                Map.of(),
                Map.of(),
                Map.of(),
                readinessPlan,
                readinessExecutionReport,
                writeBodyProvenance,
                nimCreateReleased
            );
        }

        ReadinessRequest(Map<String, Object> params,
                         Map<String, Object> creationGate,
                         Map<String, Object> deploymentBodyPreview,
                         HitlConfirmation hitlConfirmation,
                         Map<String, Object> auditContext,
                         Map<String, Object> auditReceipt,
                         Map<String, Object> writeBodyRebuildReport,
                         Map<String, Object> writeRequestSpecReport,
                         Map<String, Object> writeExecutionHandoffReport,
                         Map<String, Object> codeReleaseSwitchContractReport,
                         Map<String, Object> durableWriteExecutorReport,
                         Map<String, Object> readinessPlan,
                         Map<String, Object> readinessExecutionReport,
                         String writeBodyProvenance,
                         boolean nimCreateReleased) {
            this(
                params,
                creationGate,
                deploymentBodyPreview,
                hitlConfirmation,
                auditContext,
                auditReceipt,
                writeBodyRebuildReport,
                writeRequestSpecReport,
                writeExecutionHandoffReport,
                codeReleaseSwitchContractReport,
                Map.of(),
                durableWriteExecutorReport,
                readinessPlan,
                readinessExecutionReport,
                writeBodyProvenance,
                nimCreateReleased
            );
        }

        ReadinessRequest(Map<String, Object> params,
                         Map<String, Object> creationGate,
                         Map<String, Object> deploymentBodyPreview,
                         HitlConfirmation hitlConfirmation,
                         Map<String, Object> auditContext,
                         Map<String, Object> auditReceipt,
                         Map<String, Object> writeBodyRebuildReport,
                         Map<String, Object> writeRequestSpecReport,
                         Map<String, Object> writeExecutionHandoffReport,
                         Map<String, Object> durableWriteExecutorReport,
                         Map<String, Object> readinessPlan,
                         Map<String, Object> readinessExecutionReport,
                         String writeBodyProvenance,
                         boolean nimCreateReleased) {
            this(
                params,
                creationGate,
                deploymentBodyPreview,
                hitlConfirmation,
                auditContext,
                auditReceipt,
                writeBodyRebuildReport,
                writeRequestSpecReport,
                writeExecutionHandoffReport,
                Map.of(),
                Map.of(),
                durableWriteExecutorReport,
                readinessPlan,
                readinessExecutionReport,
                writeBodyProvenance,
                nimCreateReleased
            );
        }

        ReadinessRequest(Map<String, Object> params,
                         Map<String, Object> creationGate,
                         Map<String, Object> deploymentBodyPreview,
                         HitlConfirmation hitlConfirmation,
                         Map<String, Object> auditContext,
                         Map<String, Object> auditReceipt,
                         Map<String, Object> writeBodyRebuildReport,
                         Map<String, Object> writeRequestSpecReport,
                         Map<String, Object> readinessPlan,
                         Map<String, Object> readinessExecutionReport,
                         String writeBodyProvenance,
                         boolean nimCreateReleased) {
            this(
                params,
                creationGate,
                deploymentBodyPreview,
                hitlConfirmation,
                auditContext,
                auditReceipt,
                writeBodyRebuildReport,
                writeRequestSpecReport,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                readinessPlan,
                readinessExecutionReport,
                writeBodyProvenance,
                nimCreateReleased
            );
        }

        ReadinessRequest(Map<String, Object> params,
                         Map<String, Object> creationGate,
                         Map<String, Object> deploymentBodyPreview,
                         HitlConfirmation hitlConfirmation,
                         Map<String, Object> auditContext,
                         Map<String, Object> auditReceipt,
                         Map<String, Object> writeBodyRebuildReport,
                         Map<String, Object> readinessPlan,
                         Map<String, Object> readinessExecutionReport,
                         String writeBodyProvenance,
                         boolean nimCreateReleased) {
            this(
                params,
                creationGate,
                deploymentBodyPreview,
                hitlConfirmation,
                auditContext,
                auditReceipt,
                writeBodyRebuildReport,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                readinessPlan,
                readinessExecutionReport,
                writeBodyProvenance,
                nimCreateReleased
            );
        }

        ReadinessRequest(Map<String, Object> params,
                         Map<String, Object> creationGate,
                         Map<String, Object> deploymentBodyPreview,
                         HitlConfirmation hitlConfirmation,
                         Map<String, Object> auditContext,
                         Map<String, Object> auditReceipt,
                         Map<String, Object> readinessPlan,
                         Map<String, Object> readinessExecutionReport,
                         String writeBodyProvenance,
                         boolean nimCreateReleased) {
            this(
                params,
                creationGate,
                deploymentBodyPreview,
                hitlConfirmation,
                auditContext,
                auditReceipt,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                readinessPlan,
                readinessExecutionReport,
                writeBodyProvenance,
                nimCreateReleased
            );
        }

        ReadinessRequest(Map<String, Object> params,
                         Map<String, Object> creationGate,
                         Map<String, Object> deploymentBodyPreview,
                         HitlConfirmation hitlConfirmation,
                         Map<String, Object> auditContext,
                         Map<String, Object> auditReceipt,
                         Map<String, Object> readinessPlan,
                         String writeBodyProvenance,
                         boolean nimCreateReleased) {
            this(
                params,
                creationGate,
                deploymentBodyPreview,
                hitlConfirmation,
                auditContext,
                auditReceipt,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                readinessPlan,
                Map.of(),
                writeBodyProvenance,
                nimCreateReleased
            );
        }

        ReadinessRequest {
            params = params == null ? Map.of() : objectMap(params);
            creationGate = creationGate == null ? Map.of() : objectMap(creationGate);
            deploymentBodyPreview = deploymentBodyPreview == null ? Map.of() : objectMap(deploymentBodyPreview);
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            auditReceipt = auditReceipt == null ? Map.of() : objectMap(auditReceipt);
            writeBodyRebuildReport = writeBodyRebuildReport == null ? Map.of() : objectMap(writeBodyRebuildReport);
            writeRequestSpecReport = writeRequestSpecReport == null ? Map.of() : objectMap(writeRequestSpecReport);
            writeExecutionHandoffReport = writeExecutionHandoffReport == null ? Map.of() : objectMap(writeExecutionHandoffReport);
            codeReleaseSwitchContractReport = codeReleaseSwitchContractReport == null ? Map.of() : objectMap(codeReleaseSwitchContractReport);
            codeReleaseSwitchRuntimeSourceGuardReport = codeReleaseSwitchRuntimeSourceGuardReport == null
                ? Map.of()
                : objectMap(codeReleaseSwitchRuntimeSourceGuardReport);
            durableWriteExecutorReport = durableWriteExecutorReport == null ? Map.of() : objectMap(durableWriteExecutorReport);
            readinessPlan = readinessPlan == null ? Map.of() : objectMap(readinessPlan);
            readinessExecutionReport = readinessExecutionReport == null ? Map.of() : objectMap(readinessExecutionReport);
            writeBodyProvenance = writeBodyProvenance == null ? "" : writeBodyProvenance.trim();
        }

        static ReadinessRequest empty() {
            return new ReadinessRequest(
                Map.of(),
                Map.of(),
                Map.of(),
                null,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                "",
                false
            );
        }
    }
}
