package com.atlas.tool.impl;

import com.atlas.hitl.HitlConfirmation;

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
            && !body.containsKey("secret");
    }

    private static boolean requestSpecContractValid(Map<String, Object> auditContext,
                                                    Map<String, Object> writeBodyRebuildReport,
                                                    Map<String, Object> requestSpec,
                                                    Map<String, Object> requestBody) {
        String organizationId = text(auditContext.get("organizationId"));
        return !requestSpec.isEmpty()
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
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String normalizedKey = entry.getKey() == null
                ? ""
                : entry.getKey().replace("_", "").replace("-", "").toLowerCase(java.util.Locale.ROOT);
            if (FORBIDDEN_SECRET_KEYS.contains(normalizedKey) && hasText(entry.getValue())) {
                return true;
            }
            Object value = entry.getValue();
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

    private static boolean looksLikeSecretValue(String value) {
        String trimmed = value.trim();
        if (NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER.equals(trimmed)) {
            return false;
        }
        if (trimmed.startsWith("Bearer ") && trimmed.length() > "Bearer ".length()) {
            return true;
        }
        return trimmed.matches("sk-[A-Za-z0-9]{20,}")
            || trimmed.matches("AKIA[0-9A-Z]{16}")
            || trimmed.matches("AIza[0-9A-Za-z_-]{35}")
            || trimmed.matches("ghp_[A-Za-z0-9]{36}")
            || trimmed.matches("xox[baprs]-[A-Za-z0-9-]{10,}");
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
