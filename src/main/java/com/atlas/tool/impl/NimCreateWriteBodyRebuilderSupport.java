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
 * NIM 创建受控写入 body 重建契约。
 *
 * <p>本类只做纯数据重建，不调用 kube-manager，不执行 {@code POST /api/{orgId}/deployment}。
 * 它把已经完成预检、HITL、审计和 receipt 绑定的 NIM 状态重新组装成未来写链可消费的
 * DeploymentDTO 契约，避免把 {@code deploymentBodyPreview.bodyDraft} 或调用方参数直接透传给写接口。</p>
 */
final class NimCreateWriteBodyRebuilderSupport {

    static final String REBUILDER_NAME = "NIM_CREATE_WRITE_BODY_REBUILDER";
    static final String EXECUTION_MODE = "CONTROLLED_BODY_CONTRACT_ONLY";
    static final String BODY_DIGEST_ALGORITHM = "SHA-256";

    private static final Set<String> BODY_ALLOWLIST = Set.of(
        "uid",
        "name",
        "namespace",
        "displayName",
        "image",
        "templateId",
        "gpuSpec",
        "gpuModel",
        "migConfig",
        "cpuLimits",
        "cpuRequests",
        "memLimits",
        "memRequests",
        "gpuPercentLimits",
        "gpuMemLimits",
        "replicas",
        "acceptQueue",
        "enableWebSsh",
        "mainEntrance",
        "webPort",
        "tcpPort",
        "exposeType",
        "commands",
        "runAsRoot",
        "bandwidth",
        "ingressBandwidth",
        "egressBandwidth",
        "model",
        "autoScaleSwitch",
        "autoScaleConfig",
        "enableSecondNetwork"
    );
    private static final Set<String> REQUIRED_BODY_FIELDS = Set.of(
        "name",
        "displayName",
        "image",
        "templateId"
    );
    private static final Set<String> NON_NEGATIVE_NUMBER_FIELDS = Set.of(
        "cpuLimits",
        "cpuRequests",
        "memLimits",
        "memRequests",
        "gpuPercentLimits",
        "gpuMemLimits",
        "replicas",
        "bandwidth",
        "webPort",
        "tcpPort"
    );

    private NimCreateWriteBodyRebuilderSupport() {
    }

    static Map<String, Object> rebuild(WriteBodyRebuildInput input) {
        WriteBodyRebuildInput safeInput = input == null ? WriteBodyRebuildInput.empty() : input;
        Map<String, Object> creationGate = safeInput.creationGate();
        Map<String, Object> preview = safeInput.deploymentBodyPreview();
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> auditReceipt = safeInput.auditReceipt();
        Map<String, Object> bodyDraft = objectMap(preview.get("bodyDraft"));
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateCreationGate(creationGate, blockers);
        validatePreview(preview, blockers);
        validateAuditContext(auditContext, blockers);
        validateAuditReceipt(auditContext, auditReceipt, blockers);
        validateNoSecretMaterial("creationGate", creationGate, blockers);
        validateNoSecretMaterial("deploymentBodyPreview", preview, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("auditReceipt", auditReceipt, blockers);

        Map<String, Object> body = blockers.isEmpty() ? rebuildBody(bodyDraft, auditContext, blockers) : Map.of();
        if (!blockers.isEmpty()) {
            body = Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("writeBodyRebuilder", REBUILDER_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("writeBodyPrepared", blockers.isEmpty());
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("httpMethod", "POST");
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("organizationId", text(auditContext.get("organizationId")));
        result.put("writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE);
        result.put("directPreviewReuseAllowed", false);
        result.put("previewBodyReferenceUsed", false);
        result.put("fieldWhitelistApplied", true);
        result.put("protectedContextStripped", true);
        result.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        result.put("sourceAuditReceiptId", text(auditReceipt.get("receiptId")));
        result.put("sourceAuditEventDigest", text(auditReceipt.get("eventDigest")));
        result.put("sourceRequestId", text(auditContext.get("requestId")));
        result.put("sourceConversationId", text(auditContext.get("conversationId")));
        result.put("sourceUserId", text(auditContext.get("userId")));
        result.put("body", body);
        result.put("bodyDigestAlgorithm", BODY_DIGEST_ALGORITHM);
        result.put("bodyDigest", blockers.isEmpty() ? digestFor(body) : "");
        result.put("releaseCredential", false);
        result.put("blockedBy", blockers);
        List<String> allowedBodyKeys = new ArrayList<>(BODY_ALLOWLIST);
        allowedBodyKeys.sort(String::compareTo);
        result.put("allowedBodyKeys", allowedBodyKeys);
        result.put("evidence", List.of(
            "body is rebuilt from whitelisted audited NIM state, not reused by reference from preview",
            "audit receipt identity is bound before future POST body can be considered",
            "protected context and API Key material are excluded from DeploymentDTO body"
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
                "CREATION_GATE_NOT_READY_FOR_BODY_REBUILD",
                "受控 body 重建只能消费服务端已打开、可信策略已通过、且禁止 preview/fallback 直通的 creationGate。",
                "creation-gate"
            ));
        }
    }

    private static void validatePreview(Map<String, Object> preview,
                                        List<Map<String, Object>> blockers) {
        if (preview.isEmpty()
            || !Boolean.TRUE.equals(preview.get("bodyComplete"))
            || !Boolean.FALSE.equals(preview.get("safeToPost"))
            || objectMap(preview.get("bodyDraft")).isEmpty()) {
            blockers.add(blocker(
                "DEPLOYMENT_BODY_PREVIEW_NOT_REBUILDABLE",
                "受控 body 重建需要完整预检草案，且 preview.safeToPost 必须保持 false。",
                "dto-preview"
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
            || !hasText(auditContext.get("organizationId"))) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_NOT_READY_FOR_BODY_REBUILD",
                "受控 body 重建必须绑定完整 NIM_CREATE_REQUEST 审计上下文和密钥脱敏策略。",
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
            || !BODY_DIGEST_ALGORITHM.equals(text(auditReceipt.get("eventDigestAlgorithm")))
            || !text(auditReceipt.get("eventDigest")).matches("[a-f0-9]{64}")
            || !hasText(auditReceipt.get("receiptId"))
            || !sameAuditIdentity(auditContext, auditReceipt)) {
            blockers.add(blocker(
                "AUDIT_RECEIPT_NOT_BOUND_FOR_BODY_REBUILD",
                "受控 body 重建必须绑定真实 durable audit receipt；mock 或身份不匹配 receipt 不可用。",
                "audit"
            ));
        }
    }

    private static Map<String, Object> rebuildBody(Map<String, Object> bodyDraft,
                                                   Map<String, Object> auditContext,
                                                   List<Map<String, Object>> blockers) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : bodyDraft.entrySet()) {
            String key = entry.getKey();
            if (!BODY_ALLOWLIST.contains(key)) {
                continue;
            }
            if (NimProtectedContextDetector.isProtectedContextKey(key)
                || NimForbiddenSecretMaterialDetector.isForbiddenSecretKey(key)) {
                blockers.add(blocker(
                    "WRITE_BODY_CONTAINS_FORBIDDEN_FIELD",
                    "DeploymentDTO 写入 body 不得包含受保护上下文、token、password、secret 或 API Key 字段: " + key,
                    "write-body"
                ));
                continue;
            }
            Object value = entry.getValue();
            if (value != null) {
                body.put(key, deepCopy(value));
            }
        }

        if (!hasText(body.get("name")) && hasText(body.get("displayName"))) {
            body.put("name", text(body.get("displayName")));
        }
        body.put("organizationId", text(auditContext.get("organizationId")));
        body.remove("organizationId");

        validateBodyShape(body, blockers);
        return blockers.isEmpty() ? body : Map.of();
    }

    private static void validateBodyShape(Map<String, Object> body,
                                          List<Map<String, Object>> blockers) {
        List<String> missing = new ArrayList<>();
        for (String key : REQUIRED_BODY_FIELDS) {
            if (!hasText(body.get(key))) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            blockers.add(blocker(
                "WRITE_BODY_REQUIRED_FIELDS_MISSING",
                "受控写入 body 缺少 DeploymentDTO 关键字段: " + missing,
                "write-body"
            ));
        }
        if (!safeDeploymentName(body.get("name"))) {
            blockers.add(blocker(
                "WRITE_BODY_NAME_UNSAFE",
                "DeploymentDTO name 必须是安全名称，不能包含路径穿越、斜杠或控制字符。",
                "write-body"
            ));
        }
        if (!safeDisplayName(body.get("displayName")) || !safeImage(body.get("image")) || !safeIdentifier(body.get("templateId"))) {
            blockers.add(blocker(
                "WRITE_BODY_IDENTITY_FIELDS_UNSAFE",
                "DeploymentDTO displayName/image/templateId 必须来自已审计 NIM 状态，且不能包含 query、路径穿越或控制字符。",
                "write-body"
            ));
        }
        for (String field : NON_NEGATIVE_NUMBER_FIELDS) {
            if (body.containsKey(field) && !nonNegativeNumber(body.get(field))) {
                blockers.add(blocker(
                    "WRITE_BODY_NUMERIC_FIELD_INVALID",
                    "DeploymentDTO 数值字段必须是非负数: " + field,
                    "write-body"
                ));
            }
        }
        if (NimProtectedContextDetector.containsProtectedContext(body)) {
            blockers.add(blocker(
                "WRITE_BODY_CONTAINS_FORBIDDEN_CONTEXT",
                "Controlled write body must not carry protected context inside allowlisted fields.",
                "write-body"
            ));
        }
        if (containsForbiddenSecretMaterial(body)) {
            blockers.add(blocker(
                "WRITE_BODY_CONTAINS_FORBIDDEN_SECRET",
                "受控写入 body 不得包含 token、Authorization、password、secret、真实 NGC/NIM API Key 或相关字段。",
                "write-body"
            ));
        }
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

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "WRITE_BODY_REBUILD_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 token、Authorization、password、secret 或真实 NGC/NIM API Key。",
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

    private static boolean safeDeploymentName(Object value) {
        String name = text(value);
        return name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
            && !name.contains("..")
            && !name.contains("/")
            && !name.contains("\\")
            && name.chars().noneMatch(Character::isISOControl);
    }

    private static boolean safeDisplayName(Object value) {
        String displayName = text(value);
        return hasText(displayName)
            && displayName.length() <= 128
            && !displayName.contains("..")
            && !displayName.contains("/")
            && !displayName.contains("\\")
            && displayName.chars().noneMatch(Character::isISOControl);
    }

    private static boolean safeImage(Object value) {
        String image = text(value);
        return image.length() <= 240
            && image.matches("[A-Za-z0-9][A-Za-z0-9._:/@-]{0,239}")
            && !image.contains("..")
            && !image.contains("?")
            && !image.contains("&")
            && !image.contains("\\")
            && image.chars().noneMatch(Character::isWhitespace)
            && image.chars().noneMatch(Character::isISOControl);
    }

    private static boolean safeIdentifier(Object value) {
        return text(value).matches("[A-Za-z0-9_-]{1,64}");
    }

    private static boolean nonNegativeNumber(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue() >= 0;
        }
        try {
            return Double.parseDouble(text(value)) >= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String digestFor(Map<String, Object> body) {
        try {
            MessageDigest digest = MessageDigest.getInstance(BODY_DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical(body).getBytes(StandardCharsets.UTF_8)));
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

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record WriteBodyRebuildInput(
        Map<String, Object> creationGate,
        Map<String, Object> deploymentBodyPreview,
        Map<String, Object> auditContext,
        Map<String, Object> auditReceipt
    ) {
        WriteBodyRebuildInput {
            creationGate = creationGate == null ? Map.of() : objectMap(creationGate);
            deploymentBodyPreview = deploymentBodyPreview == null ? Map.of() : objectMap(deploymentBodyPreview);
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            auditReceipt = auditReceipt == null ? Map.of() : objectMap(auditReceipt);
        }

        static WriteBodyRebuildInput empty() {
            return new WriteBodyRebuildInput(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
            );
        }
    }
}
