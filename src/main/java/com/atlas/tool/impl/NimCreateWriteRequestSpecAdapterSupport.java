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
 * NIM 创建受控 POST 请求规格适配器契约。
 *
 * <p>本类只把 {@link NimCreateWriteBodyRebuilderSupport} 产出的已审计 DeploymentDTO body
 * 编译成未来写执行器可消费的请求规格；它不持有 HTTP client、不访问 kube-manager 8100、
 * 不执行 {@code POST /api/{orgId}/deployment}，也不接收调用方传入的 header 或密钥。</p>
 */
final class NimCreateWriteRequestSpecAdapterSupport {

    static final String ADAPTER_NAME = "NIM_CREATE_WRITE_REQUEST_SPEC_ADAPTER";
    static final String EXECUTION_MODE = "POST_REQUEST_SPEC_CONTRACT_ONLY";
    static final String CLIENT_BOUNDARY = "KUBE_MANAGER_HTTP_GATEWAY";
    static final String REQUEST_SPEC_DIGEST_ALGORITHM = "SHA-256";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String BODY_SOURCE = "CONTROLLED_REBUILDER_BODY_COPY";
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
    private static final Set<String> PROTECTED_BODY_KEYS = Set.of(
        "orgid",
        "organizationid",
        "userid",
        "conversationid",
        "sessionid",
        "requestid",
        "auditcontext",
        "auditreceipt",
        "hitlconfirmation",
        "creationgate",
        "readinessplan",
        "readinessexecutionreport",
        "writerequestspecreport"
    );

    private NimCreateWriteRequestSpecAdapterSupport() {
    }

    static Map<String, Object> compile(WriteRequestSpecInput input) {
        WriteRequestSpecInput safeInput = input == null ? WriteRequestSpecInput.empty() : input;
        Map<String, Object> creationGate = safeInput.creationGate();
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> auditReceipt = safeInput.auditReceipt();
        Map<String, Object> bodyReport = safeInput.writeBodyRebuildReport();
        Map<String, Object> body = objectMap(bodyReport.get("body"));
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateCreationGate(creationGate, blockers);
        validateAuditContext(auditContext, blockers);
        validateAuditReceipt(auditContext, auditReceipt, blockers);
        validateWriteBodyReport(auditContext, auditReceipt, bodyReport, blockers);
        validateNoSecretMaterial("creationGate", creationGate, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("auditReceipt", auditReceipt, blockers);
        validateNoSecretMaterial("writeBodyRebuildReport", bodyReport, blockers);
        validateBodyBoundary(body, blockers);

        Map<String, Object> requestSpec = blockers.isEmpty()
            ? requestSpec(auditContext, bodyReport, body)
            : Map.of();
        String requestSpecDigest = blockers.isEmpty() ? digestFor(requestSpec) : "";

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("writeRequestSpecAdapter", ADAPTER_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("writeRequestPrepared", blockers.isEmpty());
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("httpMethod", "POST");
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("organizationId", text(auditContext.get("organizationId")));
        result.put("clientBoundary", CLIENT_BOUNDARY);
        result.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        result.put("releaseCredential", false);
        result.put("callerHeadersAllowed", false);
        result.put("authorizationHeaderFromCallerAllowed", false);
        result.put("realApiKeyAllowed", false);
        result.put("bodySource", BODY_SOURCE);
        result.put("bodyCopiedByValue", blockers.isEmpty());
        result.put("bodyMutationAllowed", false);
        result.put("bodyDigestAlgorithm", NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM);
        result.put("bodyDigest", text(bodyReport.get("bodyDigest")));
        result.put("sourceWriteBodyRebuilder", text(bodyReport.get("writeBodyRebuilder")));
        result.put("sourceAuditReceiptId", text(auditReceipt.get("receiptId")));
        result.put("sourceAuditEventDigest", text(auditReceipt.get("eventDigest")));
        result.put("sourceRequestId", text(auditContext.get("requestId")));
        result.put("sourceConversationId", text(auditContext.get("conversationId")));
        result.put("sourceUserId", text(auditContext.get("userId")));
        result.put("requestSpec", requestSpec);
        result.put("requestSpecDigestAlgorithm", REQUEST_SPEC_DIGEST_ALGORITHM);
        result.put("requestSpecDigest", requestSpecDigest);
        result.put("blockedBy", blockers);
        result.put("evidence", List.of(
            "request spec is compiled from durable-audit-bound write body report",
            "no HTTP client is held and no network write is performed",
            "caller supplied Authorization/API Key headers are forbidden"
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
                "CREATION_GATE_NOT_READY_FOR_WRITE_REQUEST_SPEC",
                "POST request spec 只能消费服务端已打开、可信策略已通过、且禁止 preview/fallback 直通的 creationGate。",
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
                "AUDIT_CONTEXT_NOT_READY_FOR_WRITE_REQUEST_SPEC",
                "POST request spec 必须绑定完整 NIM_CREATE_REQUEST 审计上下文、可信 body 来源、可信组织和密钥脱敏策略。",
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
                "AUDIT_RECEIPT_NOT_BOUND_FOR_WRITE_REQUEST_SPEC",
                "POST request spec 必须绑定真实 durable audit receipt；mock 或身份不匹配 receipt 不可用于写请求规格。",
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
            && Boolean.FALSE.equals(bodyReport.get("directPreviewReuseAllowed"))
            && Boolean.FALSE.equals(bodyReport.get("previewBodyReferenceUsed"))
            && Boolean.TRUE.equals(bodyReport.get("fieldWhitelistApplied"))
            && Boolean.TRUE.equals(bodyReport.get("protectedContextStripped"))
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
                "WRITE_BODY_REBUILD_REPORT_NOT_APPROVED_FOR_REQUEST_SPEC",
                "POST request spec 只能从已审计、已脱敏、receipt 绑定且 digest 可复算的受控 body 重建报告生成。",
                "write-body"
            ));
        }
    }

    private static void validateBodyBoundary(Map<String, Object> body,
                                             List<Map<String, Object>> blockers) {
        if (!writeBodyContractValid(body)) {
            blockers.add(blocker(
                "WRITE_REQUEST_BODY_CONTRACT_INVALID",
                "POST request spec 的 body 必须是白名单 DeploymentDTO，且不得包含受保护上下文或密钥字段。",
                "request-spec"
            ));
            return;
        }
        if (containsProtectedBodyContext(body) || containsForbiddenSecretMaterial(body)) {
            blockers.add(blocker(
                "WRITE_REQUEST_SPEC_CONTAINS_FORBIDDEN_SECRET_OR_CONTEXT",
                "POST request spec 的 body 不得携带 organizationId/userId/conversationId/token/password/secret/API Key。",
                "request-spec"
            ));
        }
    }

    private static Map<String, Object> requestSpec(Map<String, Object> auditContext,
                                                   Map<String, Object> bodyReport,
                                                   Map<String, Object> body) {
        String organizationId = text(auditContext.get("organizationId"));
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("target", "deployment-create");
        spec.put("method", "POST");
        spec.put("endpoint", PATH_TEMPLATE);
        spec.put("pathTemplate", PATH_TEMPLATE);
        spec.put("resolvedPath", "/api/" + organizationId + "/deployment");
        spec.put("clientBoundary", CLIENT_BOUNDARY);
        spec.put("queryAllowed", false);
        spec.put("query", Map.of());
        spec.put("bodyAllowed", true);
        spec.put("bodyRequired", true);
        spec.put("bodySource", BODY_SOURCE);
        spec.put("body", deepCopy(body));
        spec.put("bodyDigestAlgorithm", NimCreateWriteBodyRebuilderSupport.BODY_DIGEST_ALGORITHM);
        spec.put("bodyDigest", text(bodyReport.get("bodyDigest")));
        spec.put("callerHeadersAllowed", false);
        spec.put("authorizationHeaderFromCallerAllowed", false);
        spec.put("kubeManagerAuthBoundary", "KUBE_MANAGER_HTTP_CLIENT_CONTEXT_ONLY");
        spec.put("realApiKeyAllowed", false);
        spec.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        spec.put("idempotencyKeyRequiredBeforeExecution", true);
        spec.put("executionAdapterRequired", "FUTURE_DURABLE_WRITE_EXECUTOR");
        spec.put("sideEffect", "NONE");
        spec.put("futureSideEffectIfExecuted", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        return spec;
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

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "WRITE_REQUEST_SPEC_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean containsProtectedBodyContext(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (PROTECTED_BODY_KEYS.contains(normalizeKey(entry.getKey()))) {
                return true;
            }
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested && containsProtectedBodyContext(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && containsProtectedBodyContext(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
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
            MessageDigest digest = MessageDigest.getInstance(REQUEST_SPEC_DIGEST_ALGORITHM);
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

    record WriteRequestSpecInput(
        Map<String, Object> creationGate,
        Map<String, Object> auditContext,
        Map<String, Object> auditReceipt,
        Map<String, Object> writeBodyRebuildReport
    ) {
        WriteRequestSpecInput {
            creationGate = creationGate == null ? Map.of() : objectMap(creationGate);
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            auditReceipt = auditReceipt == null ? Map.of() : objectMap(auditReceipt);
            writeBodyRebuildReport = writeBodyRebuildReport == null ? Map.of() : objectMap(writeBodyRebuildReport);
        }

        static WriteRequestSpecInput empty() {
            return new WriteRequestSpecInput(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
            );
        }
    }
}
