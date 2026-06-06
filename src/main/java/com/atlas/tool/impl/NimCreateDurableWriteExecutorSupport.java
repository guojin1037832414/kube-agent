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

    private NimCreateDurableWriteExecutorSupport() {
    }

    static Map<String, Object> prepare(WriteExecutionInput input) {
        WriteExecutionInput safeInput = input == null ? WriteExecutionInput.empty() : input;
        Map<String, Object> handoffReport = safeInput.writeExecutionHandoffReport();
        Map<String, Object> requestSpecReport = safeInput.writeRequestSpecReport();
        Map<String, Object> requestSpec = objectMap(requestSpecReport.get("requestSpec"));
        Map<String, Object> handoffPlan = objectMap(handoffReport.get("executionHandoffPlan"));
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateRequestSpecReport(requestSpecReport, requestSpec, blockers);
        validateHandoffReport(handoffReport, handoffPlan, requestSpecReport, requestSpec, blockers);
        validateNoSecretMaterial("writeExecutionHandoffReport", handoffReport, blockers);
        validateNoSecretMaterial("writeRequestSpecReport", requestSpecReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> executionAttemptSpec = inputAccepted
            ? executionAttemptSpec(handoffReport, handoffPlan, requestSpecReport, requestSpec)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DURABLE_WRITE_EXECUTOR_IMPLEMENTATION_HOLD",
                "真实 durable write executor 尚未实现和审计；当前合同壳不得执行 POST。",
                "durable-write-executor"
            ))
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
        result.put("executionAttemptSpec", executionAttemptSpec);
        result.put("blockedBy", finalBlockers);
        result.put("nextImplementationRequirements", List.of(
            "wire reviewed KubeManagerHttpClient only inside this executor boundary",
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
            && EXECUTOR_NAME.equals(text(handoffReport.get("futureExecutor")))
            && Boolean.FALSE.equals(handoffReport.get("releaseCredential"))
            && Boolean.FALSE.equals(handoffReport.get("realHttpExecutionAllowed"))
            && Boolean.TRUE.equals(handoffReport.get("preWriteAuditRequired"))
            && Boolean.TRUE.equals(handoffReport.get("idempotencyRequired"))
            && NimCreateWriteExecutionHandoffSupport.IDEMPOTENCY_KEY_SOURCE.equals(text(handoffReport.get("idempotencyKeySource")))
            && text(handoffReport.get("idempotencyKey")).matches("nim-create-[a-f0-9]{32}")
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

    private static boolean handoffPlanContractValid(Map<String, Object> handoffReport,
                                                    Map<String, Object> handoffPlan,
                                                    Map<String, Object> requestSpecReport,
                                                    Map<String, Object> requestSpec,
                                                    Map<String, Object> idempotency,
                                                    Map<String, Object> preWriteAuditHandoff,
                                                    Map<String, Object> postWriteReadinessHandoff,
                                                    Map<String, Object> retryPolicy) {
        return !handoffPlan.isEmpty()
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

    private static Map<String, Object> executionAttemptSpec(Map<String, Object> handoffReport,
                                                            Map<String, Object> handoffPlan,
                                                            Map<String, Object> requestSpecReport,
                                                            Map<String, Object> requestSpec) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("target", "deployment-create");
        spec.put("method", "POST");
        spec.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        spec.put("pathTemplate", PATH_TEMPLATE);
        spec.put("resolvedPath", text(requestSpec.get("resolvedPath")));
        spec.put("requestSpecDigest", text(requestSpecReport.get("requestSpecDigest")));
        spec.put("bodyDigest", text(requestSpecReport.get("bodyDigest")));
        spec.put("handoffDigest", text(handoffReport.get("handoffDigest")));
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
            && !body.containsKey("secret");
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

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record WriteExecutionInput(
        Map<String, Object> writeExecutionHandoffReport,
        Map<String, Object> writeRequestSpecReport
    ) {
        WriteExecutionInput {
            writeExecutionHandoffReport = writeExecutionHandoffReport == null ? Map.of() : objectMap(writeExecutionHandoffReport);
            writeRequestSpecReport = writeRequestSpecReport == null ? Map.of() : objectMap(writeRequestSpecReport);
        }

        static WriteExecutionInput empty() {
            return new WriteExecutionInput(Map.of(), Map.of());
        }
    }
}
