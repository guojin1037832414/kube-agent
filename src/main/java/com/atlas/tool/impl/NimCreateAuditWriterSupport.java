package com.atlas.tool.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NIM 创建审计写入器的 mock-first 契约支持。
 *
 * <p>本类不连接数据库、不写真实审计表，也不调用 kube-manager。它只把已经准备好的
 * {@code auditContext} 转换为可验证的审计 receipt 草案，用于提前锁定未来真实审计 writer
 * 必须满足的字段、脱敏和幂等边界。</p>
 */
final class NimCreateAuditWriterSupport {

    static final String STORAGE_MODE_MOCK_CONTRACT = "MOCK_CONTRACT_ONLY";
    static final String STORAGE_MODE_DURABLE_AUDIT_LOG = "DURABLE_AUDIT_LOG";
    static final String DIGEST_ALGORITHM = "SHA-256";

    private static final String RECEIPT_STATUS_MOCK_PREPARED = "MOCK_PREPARED";
    private static final String RECEIPT_STATUS_REJECTED = "REJECTED";

    private static final Set<String> FORBIDDEN_SECRET_KEYS = Set.of(
        "apikey",
        "ngcapikey",
        "nvaieapikey",
        "token",
        "secret",
        "password"
    );

    private NimCreateAuditWriterSupport() {
    }

    static Map<String, Object> buildMockReceipt(Map<String, Object> auditContext) {
        Map<String, Object> safeAudit = objectMap(auditContext);
        List<Map<String, Object>> blockers = validateAuditContext(safeAudit);

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("auditReceiptPrepared", blockers.isEmpty());
        receipt.put("receiptStatus", blockers.isEmpty() ? RECEIPT_STATUS_MOCK_PREPARED : RECEIPT_STATUS_REJECTED);
        receipt.put("sideEffect", "NONE");
        receipt.put("storageMode", STORAGE_MODE_MOCK_CONTRACT);
        receipt.put("durable", false);
        receipt.put("realStorageTouched", false);
        receipt.put("releaseEligible", false);
        receipt.put("requiredFutureStorage", STORAGE_MODE_DURABLE_AUDIT_LOG);
        receipt.put("blockedBy", blockers);

        putAuditIdentityFields(receipt, safeAudit);
        receipt.put("secretRedactionApplied", safeAudit.get("secretRedactionApplied"));
        receipt.put("apiKeyHandling", text(safeAudit.get("apiKeyHandling")));

        if (blockers.isEmpty()) {
            String digest = digestFor(safeAudit);
            receipt.put("eventDigestAlgorithm", DIGEST_ALGORITHM);
            receipt.put("eventDigest", digest);
            receipt.put("receiptId", "nim-audit-" + digest.substring(0, 16));
        } else {
            receipt.put("eventDigestAlgorithm", DIGEST_ALGORITHM);
            receipt.put("eventDigest", "");
            receipt.put("receiptId", "");
        }
        receipt.put("evidence", List.of(
            "mock-first audit writer contract: no persistent storage touched",
            "receipt digest only covers sanitized audit identity fields",
            "future nim_create release must replace this with a durable audit writer"
        ));
        return receipt;
    }

    private static List<Map<String, Object>> validateAuditContext(Map<String, Object> auditContext) {
        List<Map<String, Object>> blockers = new ArrayList<>();
        if (auditContext.isEmpty() || !Boolean.TRUE.equals(auditContext.get("auditPrepared"))) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_NOT_PREPARED",
                "审计上下文尚未准备完成，不能生成 NIM create audit receipt。",
                "audit-writer"
            ));
        }
        if (!NimCreateAuditReadinessSupport.AUDIT_EVENT_TYPE.equals(text(auditContext.get("auditEventType")))
            || !NimCreateStateMachineSupport.TARGET_TOOL.equals(text(auditContext.get("targetTool")))
            || !NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE.equals(text(auditContext.get("writeBodyProvenance")))
            || !Boolean.TRUE.equals(auditContext.get("secretRedactionApplied"))
            || !NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(auditContext.get("apiKeyHandling")))
            || !hasText(auditContext.get("requestId"))
            || !hasText(auditContext.get("conversationId"))
            || !hasText(auditContext.get("userId"))
            || !hasText(auditContext.get("organizationId"))) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_CONTRACT_INVALID",
                "审计上下文缺少 NIM_CREATE_REQUEST、targetTool、可信 body 来源、身份字段或密钥策略。",
                "audit-writer"
            ));
        }
        if (containsForbiddenSecretMaterial(auditContext)) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_CONTAINS_FORBIDDEN_SECRET",
                "审计上下文不得携带 token、password、secret 或真实 NGC/NIM API Key。",
                "audit-writer"
            ));
        }
        return blockers;
    }

    private static void putAuditIdentityFields(Map<String, Object> receipt,
                                               Map<String, Object> auditContext) {
        receipt.put("auditEventType", text(auditContext.get("auditEventType")));
        receipt.put("requestId", text(auditContext.get("requestId")));
        receipt.put("conversationId", text(auditContext.get("conversationId")));
        receipt.put("userId", text(auditContext.get("userId")));
        receipt.put("organizationId", text(auditContext.get("organizationId")));
        receipt.put("targetTool", text(auditContext.get("targetTool")));
        receipt.put("writeBodyProvenance", text(auditContext.get("writeBodyProvenance")));
    }

    private static String digestFor(Map<String, Object> auditContext) {
        String canonical = String.join("\n", List.of(
            "auditEventType=" + text(auditContext.get("auditEventType")),
            "requestId=" + text(auditContext.get("requestId")),
            "conversationId=" + text(auditContext.get("conversationId")),
            "userId=" + text(auditContext.get("userId")),
            "organizationId=" + text(auditContext.get("organizationId")),
            "targetTool=" + text(auditContext.get("targetTool")),
            "writeBodyProvenance=" + text(auditContext.get("writeBodyProvenance")),
            "displayName=" + text(auditContext.get("displayName")),
            "image=" + text(auditContext.get("image")),
            "templateId=" + text(auditContext.get("templateId")),
            "apiKeyHandling=" + text(auditContext.get("apiKeyHandling"))
        ));
        try {
            MessageDigest digest = MessageDigest.getInstance(DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", ex);
        }
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
            if (value instanceof Map<?, ?> nested && containsForbiddenSecretMaterial(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && containsForbiddenSecretMaterial(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
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
}
