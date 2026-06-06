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
 * NIM 创建写执行交接契约。
 *
 * <p>本类位于 request spec adapter 和未来真实 durable write executor 之间，只生成可审计的
 * 执行交接计划，不持有 HTTP client，不访问 kube-manager 8100，不执行真实
 * {@code POST /api/{orgId}/deployment}。它的重点是把幂等键、pre-write audit handoff、
 * request spec digest 复核和写后 readiness handoff 固化为可测试契约。</p>
 */
final class NimCreateWriteExecutionHandoffSupport {

    static final String HANDOFF_NAME = "NIM_CREATE_WRITE_EXECUTION_HANDOFF";
    static final String EXECUTION_MODE = "WRITE_EXECUTION_HANDOFF_CONTRACT_ONLY";
    static final String FUTURE_EXECUTOR = "FUTURE_DURABLE_WRITE_EXECUTOR";
    static final String IDEMPOTENCY_KEY_SOURCE = "SERVER_DERIVED_FROM_AUDIT_AND_REQUEST_SPEC";
    static final String HANDOFF_DIGEST_ALGORITHM = "SHA-256";

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

    private NimCreateWriteExecutionHandoffSupport() {
    }

    static Map<String, Object> prepare(WriteExecutionHandoffInput input) {
        WriteExecutionHandoffInput safeInput = input == null ? WriteExecutionHandoffInput.empty() : input;
        Map<String, Object> creationGate = safeInput.creationGate();
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> auditReceipt = safeInput.auditReceipt();
        Map<String, Object> bodyReport = safeInput.writeBodyRebuildReport();
        Map<String, Object> requestSpecReport = safeInput.writeRequestSpecReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateCreationGate(creationGate, blockers);
        validateAuditContext(auditContext, blockers);
        validateAuditReceipt(auditContext, auditReceipt, blockers);
        validateWriteBodyReport(auditContext, auditReceipt, bodyReport, blockers);
        validateWriteRequestSpecReport(auditContext, auditReceipt, bodyReport, requestSpecReport, blockers);
        validateNoSecretMaterial("creationGate", creationGate, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("auditReceipt", auditReceipt, blockers);
        validateNoSecretMaterial("writeBodyRebuildReport", bodyReport, blockers);
        validateNoSecretMaterial("writeRequestSpecReport", requestSpecReport, blockers);

        String idempotencyKey = blockers.isEmpty()
            ? idempotencyKey(auditContext, auditReceipt, requestSpecReport)
            : "";
        Map<String, Object> handoffPlan = blockers.isEmpty()
            ? executionHandoffPlan(auditContext, auditReceipt, bodyReport, requestSpecReport, idempotencyKey)
            : Map.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("writeExecutionHandoff", HANDOFF_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("writeExecutionPrepared", blockers.isEmpty());
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("httpMethod", "POST");
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("organizationId", text(auditContext.get("organizationId")));
        result.put("futureExecutor", FUTURE_EXECUTOR);
        result.put("releaseCredential", false);
        result.put("realHttpExecutionAllowed", false);
        result.put("preWriteAuditRequired", true);
        result.put("idempotencyRequired", true);
        result.put("idempotencyKeySource", IDEMPOTENCY_KEY_SOURCE);
        result.put("idempotencyKey", idempotencyKey);
        result.put("callerIdempotencyKeyAllowed", false);
        result.put("callerHeadersAllowed", false);
        result.put("authorizationHeaderFromCallerAllowed", false);
        result.put("realApiKeyAllowed", false);
        result.put("sourceAuditReceiptId", text(auditReceipt.get("receiptId")));
        result.put("sourceAuditEventDigest", text(auditReceipt.get("eventDigest")));
        result.put("sourceRequestId", text(auditContext.get("requestId")));
        result.put("sourceConversationId", text(auditContext.get("conversationId")));
        result.put("sourceUserId", text(auditContext.get("userId")));
        result.put("sourceBodyDigest", text(bodyReport.get("bodyDigest")));
        result.put("sourceRequestSpecDigest", text(requestSpecReport.get("requestSpecDigest")));
        result.put("executionHandoffPlan", handoffPlan);
        result.put("handoffDigestAlgorithm", HANDOFF_DIGEST_ALGORITHM);
        result.put("handoffDigest", blockers.isEmpty() ? digestFor(handoffPlan) : "");
        result.put("blockedBy", blockers);
        result.put("evidence", List.of(
            "handoff is bound to durable audit receipt and request spec digest",
            "idempotency key is server-derived and not accepted from caller params",
            "future executor and post-write readiness handoff are declared but not executed"
        ));
        return result;
    }

    private static void validateCreationGate(Map<String, Object> creationGate,
                                             List<Map<String, Object>> blockers) {
        Map<String, Object> trustedPolicySnapshot = objectMap(creationGate.get("trustedPolicySnapshot"));
        Map<String, Object> futureWritePath = objectMap(creationGate.get("futureWritePath"));
        if (creationGate.isEmpty()
            || !NimCreateStateMachineSupport.READY_GATE_STATE.equals(text(creationGate.get("gateState")))
            || !Boolean.TRUE.equals(creationGate.get("allowedToCreateNow"))
            || !NimCreateStateMachineSupport.TRUSTED_POLICY_PASSED.equals(text(trustedPolicySnapshot.get("snapshotState")))
            || !Boolean.TRUE.equals(trustedPolicySnapshot.get("authoritative"))
            || !Boolean.TRUE.equals(trustedPolicySnapshot.get("protectedFromCallerParams"))
            || !Boolean.FALSE.equals(futureWritePath.get("directUseOfPreviewAllowed"))
            || !Boolean.FALSE.equals(futureWritePath.get("fallbackAllowedFromPreflight"))) {
            blockers.add(blocker(
                "CREATION_GATE_NOT_READY_FOR_WRITE_EXECUTION_HANDOFF",
                "写执行交接只能消费服务端已打开、可信策略已通过、且禁止 preview/fallback 直通的 creationGate。",
                "creation-gate"
            ));
        }
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
            || !safeIdentifier(auditContext.get("organizationId"))) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_NOT_READY_FOR_WRITE_EXECUTION_HANDOFF",
                "写执行交接必须绑定完整 NIM_CREATE_REQUEST 审计上下文、可信组织和密钥脱敏策略。",
                "audit"
            ));
        }
    }

    private static void validateAuditReceipt(Map<String, Object> auditContext,
                                             Map<String, Object> auditReceipt,
                                             List<Map<String, Object>> blockers) {
        if (auditReceipt.isEmpty()
            || !Boolean.TRUE.equals(auditReceipt.get("auditReceiptPrepared"))
            || !NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(auditReceipt.get("receiptStatus")))
            || !NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(auditReceipt.get("storageMode")))
            || !Boolean.TRUE.equals(auditReceipt.get("durable"))
            || !Boolean.TRUE.equals(auditReceipt.get("realStorageTouched"))
            || !Boolean.TRUE.equals(auditReceipt.get("releaseEligible"))
            || !NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(auditReceipt.get("eventDigestAlgorithm")))
            || !text(auditReceipt.get("eventDigest")).matches("[a-f0-9]{64}")
            || !hasText(auditReceipt.get("receiptId"))
            || !sameAuditIdentity(auditContext, auditReceipt)) {
            blockers.add(blocker(
                "AUDIT_RECEIPT_NOT_BOUND_FOR_WRITE_EXECUTION_HANDOFF",
                "写执行交接必须绑定真实 durable audit receipt；mock 或身份不匹配 receipt 不可用。",
                "audit"
            ));
        }
    }

    private static void validateWriteBodyReport(Map<String, Object> auditContext,
                                                Map<String, Object> auditReceipt,
                                                Map<String, Object> bodyReport,
                                                List<Map<String, Object>> blockers) {
        Map<String, Object> body = objectMap(bodyReport.get("body"));
        boolean contractValid = NimCreateWriteBodyRebuilderSupport.REBUILDER_NAME.equals(text(bodyReport.get("writeBodyRebuilder")))
            && NimCreateWriteBodyRebuilderSupport.EXECUTION_MODE.equals(text(bodyReport.get("executionMode")))
            && "NOT_PERFORMED".equals(text(bodyReport.get("networkAccess")))
            && "NONE".equals(text(bodyReport.get("sideEffect")))
            && Boolean.TRUE.equals(bodyReport.get("writeBodyPrepared"))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(bodyReport.get("targetTool")))
            && "POST".equals(text(bodyReport.get("httpMethod")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(bodyReport.get("backendEndpoint")))
            && NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE.equals(text(bodyReport.get("writeBodyProvenance")))
            && NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(bodyReport.get("apiKeyHandling")))
            && Boolean.FALSE.equals(bodyReport.get("releaseCredential"))
            && NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM.equals(text(bodyReport.get("bodyDigestAlgorithm")))
            && text(bodyReport.get("bodyDigest")).matches("[a-f0-9]{64}")
            && text(bodyReport.get("bodyDigest")).equals(digestFor(body))
            && text(auditReceipt.get("receiptId")).equals(text(bodyReport.get("sourceAuditReceiptId")))
            && text(auditReceipt.get("eventDigest")).equals(text(bodyReport.get("sourceAuditEventDigest")))
            && text(auditContext.get("requestId")).equals(text(bodyReport.get("sourceRequestId")))
            && text(auditContext.get("conversationId")).equals(text(bodyReport.get("sourceConversationId")))
            && text(auditContext.get("userId")).equals(text(bodyReport.get("sourceUserId")))
            && text(auditContext.get("organizationId")).equals(text(bodyReport.get("organizationId")))
            && listOfMaps(bodyReport.get("blockedBy")).isEmpty()
            && writeBodyContractValid(body);

        if (!contractValid) {
            blockers.add(blocker(
                "WRITE_BODY_REBUILD_REPORT_NOT_APPROVED_FOR_EXECUTION_HANDOFF",
                "写执行交接只能从已审计、receipt 绑定且 digest 可复算的 body 重建报告生成。",
                "write-body"
            ));
        }
    }

    private static void validateWriteRequestSpecReport(Map<String, Object> auditContext,
                                                       Map<String, Object> auditReceipt,
                                                       Map<String, Object> bodyReport,
                                                       Map<String, Object> requestSpecReport,
                                                       List<Map<String, Object>> blockers) {
        Map<String, Object> requestSpec = objectMap(requestSpecReport.get("requestSpec"));
        boolean contractValid = NimCreateWriteRequestSpecAdapterSupport.ADAPTER_NAME.equals(text(requestSpecReport.get("writeRequestSpecAdapter")))
            && NimCreateWriteRequestSpecAdapterSupport.EXECUTION_MODE.equals(text(requestSpecReport.get("executionMode")))
            && "NOT_PERFORMED".equals(text(requestSpecReport.get("networkAccess")))
            && "NONE".equals(text(requestSpecReport.get("sideEffect")))
            && Boolean.TRUE.equals(requestSpecReport.get("writeRequestPrepared"))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(requestSpecReport.get("targetTool")))
            && "POST".equals(text(requestSpecReport.get("httpMethod")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(requestSpecReport.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(requestSpecReport.get("pathTemplate")))
            && text(auditContext.get("organizationId")).equals(text(requestSpecReport.get("organizationId")))
            && NimCreateWriteRequestSpecAdapterSupport.CLIENT_BOUNDARY.equals(text(requestSpecReport.get("clientBoundary")))
            && NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(requestSpecReport.get("apiKeyHandling")))
            && Boolean.FALSE.equals(requestSpecReport.get("releaseCredential"))
            && Boolean.FALSE.equals(requestSpecReport.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(requestSpecReport.get("authorizationHeaderFromCallerAllowed"))
            && Boolean.FALSE.equals(requestSpecReport.get("realApiKeyAllowed"))
            && Boolean.TRUE.equals(requestSpecReport.get("bodyCopiedByValue"))
            && Boolean.FALSE.equals(requestSpecReport.get("bodyMutationAllowed"))
            && NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM.equals(text(requestSpecReport.get("bodyDigestAlgorithm")))
            && text(bodyReport.get("bodyDigest")).equals(text(requestSpecReport.get("bodyDigest")))
            && NimCreateWriteBodyRebuilderSupport.REBUILDER_NAME.equals(text(requestSpecReport.get("sourceWriteBodyRebuilder")))
            && text(auditReceipt.get("receiptId")).equals(text(requestSpecReport.get("sourceAuditReceiptId")))
            && text(auditReceipt.get("eventDigest")).equals(text(requestSpecReport.get("sourceAuditEventDigest")))
            && text(auditContext.get("requestId")).equals(text(requestSpecReport.get("sourceRequestId")))
            && text(auditContext.get("conversationId")).equals(text(requestSpecReport.get("sourceConversationId")))
            && text(auditContext.get("userId")).equals(text(requestSpecReport.get("sourceUserId")))
            && NimCreateWriteRequestSpecAdapterSupport.REQUEST_SPEC_DIGEST_ALGORITHM.equals(text(requestSpecReport.get("requestSpecDigestAlgorithm")))
            && text(requestSpecReport.get("requestSpecDigest")).matches("[a-f0-9]{64}")
            && text(requestSpecReport.get("requestSpecDigest")).equals(digestFor(requestSpec))
            && listOfMaps(requestSpecReport.get("blockedBy")).isEmpty()
            && requestSpecContractValid(auditContext, bodyReport, requestSpec);

        if (!contractValid) {
            blockers.add(blocker(
                "WRITE_REQUEST_SPEC_REPORT_NOT_APPROVED_FOR_EXECUTION_HANDOFF",
                "写执行交接只能从已审计、digest 可复算且禁止调用方 header/API Key 的 request spec 报告生成。",
                "write-request-spec"
            ));
        }
    }

    private static Map<String, Object> executionHandoffPlan(Map<String, Object> auditContext,
                                                            Map<String, Object> auditReceipt,
                                                            Map<String, Object> bodyReport,
                                                            Map<String, Object> requestSpecReport,
                                                            String idempotencyKey) {
        String organizationId = text(auditContext.get("organizationId"));
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("target", "deployment-create");
        plan.put("method", "POST");
        plan.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        plan.put("pathTemplate", PATH_TEMPLATE);
        plan.put("resolvedPath", "/api/" + organizationId + "/deployment");
        plan.put("futureExecutor", FUTURE_EXECUTOR);
        plan.put("networkAccess", "NOT_PERFORMED");
        plan.put("sideEffect", "NONE");
        plan.put("requestSpecDigest", text(requestSpecReport.get("requestSpecDigest")));
        plan.put("bodyDigest", text(bodyReport.get("bodyDigest")));
        plan.put("callerHeadersAllowed", false);
        plan.put("authorizationHeaderFromCallerAllowed", false);
        plan.put("realApiKeyAllowed", false);
        plan.put("kubeManagerAuthBoundary", "KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY");
        plan.put("idempotency", idempotency(idempotencyKey));
        plan.put("preWriteAuditHandoff", preWriteAuditHandoff(auditReceipt));
        plan.put("postWriteReadinessHandoff", postWriteReadinessHandoff());
        plan.put("retryPolicy", retryPolicy());
        return plan;
    }

    private static Map<String, Object> idempotency(String idempotencyKey) {
        Map<String, Object> idempotency = new LinkedHashMap<>();
        idempotency.put("required", true);
        idempotency.put("key", idempotencyKey);
        idempotency.put("keySource", IDEMPOTENCY_KEY_SOURCE);
        idempotency.put("callerKeyAllowed", false);
        idempotency.put("reuseAllowedOnlyForSameAuditReceiptAndRequestSpec", true);
        return idempotency;
    }

    private static Map<String, Object> preWriteAuditHandoff(Map<String, Object> auditReceipt) {
        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("required", true);
        handoff.put("receiptId", text(auditReceipt.get("receiptId")));
        handoff.put("eventDigest", text(auditReceipt.get("eventDigest")));
        handoff.put("storageMode", text(auditReceipt.get("storageMode")));
        handoff.put("receiptStatus", text(auditReceipt.get("receiptStatus")));
        handoff.put("durable", auditReceipt.get("durable"));
        handoff.put("realStorageTouched", auditReceipt.get("realStorageTouched"));
        return handoff;
    }

    private static Map<String, Object> postWriteReadinessHandoff() {
        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("requiredAfterWrite", true);
        handoff.put("nextExecutor", NimCreateReadinessExecutorSupport.EXECUTOR_NAME);
        handoff.put("pollOnly", true);
        handoff.put("readOnly", true);
        handoff.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        handoff.put("forbiddenBeforeWrite", true);
        return handoff;
    }

    private static Map<String, Object> retryPolicy() {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("retryAllowed", false);
        policy.put("retryAllowedOnlyWithSameIdempotencyKey", true);
        policy.put("maxAttemptsBeforeExecutorImplementation", 1);
        return policy;
    }

    private static String idempotencyKey(Map<String, Object> auditContext,
                                         Map<String, Object> auditReceipt,
                                         Map<String, Object> requestSpecReport) {
        String seed = String.join("\n", List.of(
            text(auditContext.get("requestId")),
            text(auditContext.get("conversationId")),
            text(auditContext.get("userId")),
            text(auditContext.get("organizationId")),
            text(auditReceipt.get("receiptId")),
            text(auditReceipt.get("eventDigest")),
            text(requestSpecReport.get("requestSpecDigest"))
        ));
        return "nim-create-" + digestFor(Map.of("seed", seed)).substring(0, 32);
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
                                                    Map<String, Object> bodyReport,
                                                    Map<String, Object> requestSpec) {
        String organizationId = text(auditContext.get("organizationId"));
        Map<String, Object> requestBody = objectMap(requestSpec.get("body"));
        return !requestSpec.isEmpty()
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
            && text(bodyReport.get("bodyDigest")).equals(text(requestSpec.get("bodyDigest")))
            && Boolean.FALSE.equals(requestSpec.get("callerHeadersAllowed"))
            && Boolean.FALSE.equals(requestSpec.get("authorizationHeaderFromCallerAllowed"))
            && "KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY".equals(text(requestSpec.get("kubeManagerAuthBoundary")))
            && Boolean.FALSE.equals(requestSpec.get("realApiKeyAllowed"))
            && NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(requestSpec.get("apiKeyHandling")))
            && Boolean.TRUE.equals(requestSpec.get("idempotencyKeyRequiredBeforeExecution"))
            && FUTURE_EXECUTOR.equals(text(requestSpec.get("executionAdapterRequired")))
            && "NONE".equals(text(requestSpec.get("sideEffect")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(requestSpec.get("futureSideEffectIfExecuted")))
            && requestBody.equals(objectMap(bodyReport.get("body")))
            && writeBodyContractValid(requestBody);
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "WRITE_EXECUTION_HANDOFF_INPUT_CONTAINS_FORBIDDEN_SECRET",
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
            MessageDigest digest = MessageDigest.getInstance(HANDOFF_DIGEST_ALGORITHM);
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
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }

    private static boolean safeIdentifier(Object value) {
        return text(value).matches("[A-Za-z0-9_-]{1,64}");
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

    record WriteExecutionHandoffInput(
        Map<String, Object> creationGate,
        Map<String, Object> auditContext,
        Map<String, Object> auditReceipt,
        Map<String, Object> writeBodyRebuildReport,
        Map<String, Object> writeRequestSpecReport
    ) {
        WriteExecutionHandoffInput {
            creationGate = creationGate == null ? Map.of() : objectMap(creationGate);
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            auditReceipt = auditReceipt == null ? Map.of() : objectMap(auditReceipt);
            writeBodyRebuildReport = writeBodyRebuildReport == null ? Map.of() : objectMap(writeBodyRebuildReport);
            writeRequestSpecReport = writeRequestSpecReport == null ? Map.of() : objectMap(writeRequestSpecReport);
        }

        static WriteExecutionHandoffInput empty() {
            return new WriteExecutionHandoffInput(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
            );
        }
    }
}
