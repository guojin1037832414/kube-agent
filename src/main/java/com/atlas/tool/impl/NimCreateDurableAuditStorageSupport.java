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
 * NIM 创建 durable audit storage 候选契约。
 *
 * <p>mature kube-manager 已经存在系统日志能力: {@code SaveLogAspect -> ISysLogService.saveLog(SysLog) -> sys_log}。
 * 但该能力是通用请求日志，不等同于 NIM 创建前的专用 durable audit receipt。本类只把这个成熟证据
 * 固化成可复核的替换边界: 哪些字段可以映射、哪些语义缺口必须补齐、哪些 caller/secret 材料必须拒绝。
 * 当前实现不连接 Elasticsearch、不写 {@code sys_log}、不生成 release-eligible receipt。</p>
 */
final class NimCreateDurableAuditStorageSupport {

    static final String STORAGE_SUPPORT_NAME = "NIM_CREATE_DURABLE_AUDIT_STORAGE_CANDIDATE";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_STORAGE_CANDIDATE_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String CANDIDATE_INDEX = "sys_log";
    static final String CANDIDATE_ENTITY = "com.cgm.kube.system.entity.SysLog";
    static final String CANDIDATE_SAVE_SERVICE = "ISysLogService.saveLog(SysLog)";
    static final String CANDIDATE_WRITER = "SaveLogAspect";

    private NimCreateDurableAuditStorageSupport() {
    }

    static Map<String, Object> prepare(DurableAuditStorageInput input) {
        DurableAuditStorageInput safeInput = input == null ? DurableAuditStorageInput.empty() : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> storagePlan = inputAccepted ? storagePlan(auditContext, principal) : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "DEDICATED_NIM_AUDIT_WRITER_NOT_IMPLEMENTED",
                "已识别 mature sys_log 持久化候选，但 NIM 专用 pre-write audit writer 尚未实现；不能签发 durable receipt。",
                "durable-audit-storage"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditStorage", STORAGE_SUPPORT_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("storageState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("storagePlanPrepared", inputAccepted);
        result.put("realStorageTouched", false);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("durableReceiptCanBeIssued", false);
        result.put("candidateFit", "PARTIAL_FIT_NEEDS_DEDICATED_NIM_AUDIT_WRITER");
        result.put("candidateIndex", CANDIDATE_INDEX);
        result.put("candidateEntity", CANDIDATE_ENTITY);
        result.put("candidateSaveService", CANDIDATE_SAVE_SERVICE);
        result.put("candidateWriter", CANDIDATE_WRITER);
        result.put("candidateSearchEndpoint", "GET /api/log");
        result.put("candidateSearchIsolation", "SYS_ADMIN_ONLY");
        result.put("candidateDeleteEndpoint", "DELETE /api/log/all");
        result.put("frontendRoute", "/system/log");
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("storagePlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("storagePlanDigest", inputAccepted ? digestFor(storagePlan) : "");
        result.put("storagePlan", storagePlan);
        result.put("blockedBy", finalBlockers);
        result.put("matureEvidence", matureEvidence());
        result.put("semanticGaps", semanticGaps());
        result.put("nextImplementationRequirements", List.of(
            "implement a dedicated server-side NIM audit writer instead of relying on caller-supplied params",
            "obtain username and organizationId from trusted session principal",
            "persist a pre-write intent before POST and a post-write result after POST",
            "store only sanitized params/body summaries and event digests",
            "return DURABLE_RECORDED + DURABLE_AUDIT_LOG receipt only after real storage succeeds"
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
            || !text(auditContext.get("organizationId")).matches("[0-9]{1,10}")
            || !hasText(auditContext.get("displayName"))
            || !hasText(auditContext.get("image"))
            || !hasText(auditContext.get("templateId"))) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_DURABLE_STORAGE",
                "durable audit storage 候选只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
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
                "mature SysLog 需要服务端当前用户 username/orgId；未来 writer 必须从可信 session principal 获取，不能相信 Tool 入参。",
                "trusted-principal"
            ));
        }
    }

    private static Map<String, Object> storagePlan(Map<String, Object> auditContext,
                                                   Map<String, Object> principal) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("targetStorage", CANDIDATE_INDEX);
        plan.put("targetEntity", CANDIDATE_ENTITY);
        plan.put("saveService", CANDIDATE_SAVE_SERVICE);
        plan.put("writerBoundary", "DEDICATED_NIM_AUDIT_WRITER_REQUIRED");
        plan.put("writeMode", "PRE_WRITE_INTENT_THEN_POST_WRITE_RESULT");
        plan.put("sysLogFieldMapping", sysLogFieldMapping(auditContext, principal));
        plan.put("redactionPolicy", List.of(
            "params store requestId/conversationId/targetTool/digest only",
            "body store sanitized NIM deployment summary only",
            "never store Authorization/token/password/secret/API key material",
            "never store caller-provided headers or raw Tool params"
        ));
        plan.put("receiptContract", Map.of(
            "receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS,
            "storageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE,
            "durable", true,
            "realStorageTouched", true,
            "releaseEligible", true,
            "eventDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM
        ));
        return plan;
    }

    private static Map<String, Object> sysLogFieldMapping(Map<String, Object> auditContext,
                                                          Map<String, Object> principal) {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("organizationId", Integer.parseInt(text(auditContext.get("organizationId"))));
        mapping.put("username", text(principal.get("username")));
        mapping.put("module", "NIM_CREATE_AUDIT");
        mapping.put("description", "nim_create pre-write audit intent");
        mapping.put("uri", "/api/" + text(auditContext.get("organizationId")) + "/deployment");
        mapping.put("params", sanitizedParams(auditContext));
        mapping.put("body", sanitizedBody(auditContext));
        mapping.put("ip", "{trustedRequestIp}");
        mapping.put("start", "{serverClockMillisBeforeWrite}");
        mapping.put("end", "{serverClockMillisAfterDurableAuditWrite}");
        mapping.put("success", "PRE_WRITE_INTENT_RECORDED_NOT_DEPLOYMENT_RESULT");
        mapping.put("trace", List.of());
        return mapping;
    }

    private static Map<String, Object> sanitizedParams(Map<String, Object> auditContext) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("requestId", text(auditContext.get("requestId")));
        params.put("conversationId", text(auditContext.get("conversationId")));
        params.put("userId", text(auditContext.get("userId")));
        params.put("targetTool", text(auditContext.get("targetTool")));
        params.put("auditEventType", text(auditContext.get("auditEventType")));
        params.put("backendEndpoint", text(auditContext.get("backendEndpoint")));
        params.put("eventDigest", digestFor(auditContext));
        return params;
    }

    private static Map<String, Object> sanitizedBody(Map<String, Object> auditContext) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", text(auditContext.get("displayName")));
        body.put("image", text(auditContext.get("image")));
        body.put("templateId", text(auditContext.get("templateId")));
        body.put("writeBodyProvenance", text(auditContext.get("writeBodyProvenance")));
        body.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        body.put("secretRedactionApplied", true);
        return body;
    }

    private static List<String> matureEvidence() {
        return List.of(
            "kube-manager SaveLogAspect surrounds @Operation APIs and builds SysLog entries for /api requests",
            "kube-manager ISysLogService.saveLog(SysLog) persists SysLog via ElasticsearchTemplate when enabled",
            "kube-manager SysLog is @Document(indexName = Constant.ES_SYS_LOG_INDEX_NAME)",
            "kube-manager Constant.ES_SYS_LOG_INDEX_NAME = sys_log",
            "kube-manager SysLogController exposes SYS_ADMIN_ONLY GET /api/log and DELETE /api/log/all",
            "vue-kube-manager /system/log calls listLog -> GET /api/log"
        );
    }

    private static List<String> semanticGaps() {
        return List.of(
            "SysLog is a generic request log, not a dedicated NIM pre-write audit receipt table",
            "SaveLogAspect captures raw request params/body by default; NIM audit writer must store sanitized summaries only",
            "SysLog success is an API response status, while NIM needs separate pre-write intent and post-write result records",
            "SysLog username comes from server session; kube-agent must not substitute caller-provided user fields",
            "Elasticsearch can be disabled in mature code; durable NIM release needs an explicit storage availability gate"
        );
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_STORAGE_INPUT_CONTAINS_FORBIDDEN_SECRET",
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

    record DurableAuditStorageInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot
    ) {
        DurableAuditStorageInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
        }

        static DurableAuditStorageInput empty() {
            return new DurableAuditStorageInput(Map.of(), Map.of());
        }
    }
}
