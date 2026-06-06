package com.atlas.tool.impl;

import com.atlas.hitl.HitlConfirmation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NIM 创建审计上下文与 readiness 计划草案。
 *
 * <p>本类只构造可被 {@link NimCreateStateMachineSupport} 消费的纯数据结构，不写审计日志、
 * 不轮询服务、不调用 NIM API。它把 mature 前端的创建后行为沉淀为安全计划：先按名称只读回查
 * Deployment，再从返回的 entranceMap 取 NIM 服务入口，最后只读探测 {@code /v1/health/live}
 * 和 {@code /v1/models}。Agent 不生成、不保存、不展示真实 NGC/NIM API Key。</p>
 */
final class NimCreateAuditReadinessSupport {

    static final String AUDIT_EVENT_TYPE = "NIM_CREATE_REQUEST";
    static final String BACKEND_ENDPOINT = "POST /api/{orgId}/deployment";

    private NimCreateAuditReadinessSupport() {
    }

    static Map<String, Object> buildAuditContext(AuditReadinessInput input) {
        AuditReadinessInput safeInput = input == null ? AuditReadinessInput.empty() : input;
        Map<String, Object> bodyDraft = objectMap(safeInput.deploymentBodyPreview().get("bodyDraft"));
        Map<String, Object> policySnapshot = objectMap(safeInput.creationGate().get("trustedPolicySnapshot"));

        String displayName = firstText(bodyDraft.get("displayName"), bodyDraft.get("name"), safeInput.params().get("serviceName"));
        String image = text(bodyDraft.get("image"));
        String templateId = text(bodyDraft.get("templateId"));
        boolean hitlMatches = safeInput.hitlConfirmation() != null
            && safeInput.hitlConfirmation().allows(NimCreateStateMachineSupport.TARGET_TOOL);

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("auditPrepared", hasText(safeInput.requestId())
            && hasText(safeInput.conversationId())
            && hasText(safeInput.userId())
            && hasText(safeInput.organizationId())
            && hasText(displayName)
            && hasText(image)
            && hasText(templateId)
            && hitlMatches);
        audit.put("auditEventType", AUDIT_EVENT_TYPE);
        audit.put("requestId", safeInput.requestId());
        audit.put("conversationId", safeInput.conversationId());
        audit.put("userId", safeInput.userId());
        audit.put("organizationId", safeInput.organizationId());
        audit.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        audit.put("targetIntent", NimCreateStateMachineSupport.TARGET_TOOL);
        audit.put("operationType", "CREATE");
        audit.put("backendEndpoint", BACKEND_ENDPOINT);
        audit.put("writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE);
        audit.put("displayName", displayName);
        audit.put("image", image);
        audit.put("templateId", templateId);
        audit.put("trustedPolicyState", text(policySnapshot.get("snapshotState")));
        audit.put("trustedPolicyAuthoritative", policySnapshot.get("authoritative"));
        audit.put("protectedFromCallerParams", true);
        audit.put("hitlTarget", safeInput.hitlConfirmation() == null ? "" : safeInput.hitlConfirmation().target());
        audit.put("hitlAccepted", hitlMatches);
        audit.put("secretRedactionApplied", true);
        audit.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        audit.put("ignoredCallerClaimKeys", ignoredCallerClaimKeys(safeInput.params()));
        audit.put("evidence", List.of(
            "vue-kube-manager NIM handleCreate: createDeployment 后按 name 回查 deployment 列表",
            "vue-kube-manager request-nim: readiness 只读 GET /v1/health/live 与 GET /v1/models",
            "kube-manager DeploymentController: tenant 创建入口为 POST /api/{organizationId}/deployment"
        ));
        return audit;
    }

    static Map<String, Object> buildReadinessPlan(AuditReadinessInput input) {
        AuditReadinessInput safeInput = input == null ? AuditReadinessInput.empty() : input;
        Map<String, Object> bodyDraft = objectMap(safeInput.deploymentBodyPreview().get("bodyDraft"));
        String deploymentName = firstText(bodyDraft.get("name"), bodyDraft.get("displayName"), safeInput.params().get("serviceName"));

        boolean prepared = hasText(safeInput.organizationId()) && hasText(deploymentName);
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("readinessPollingPrepared", prepared);
        plan.put("pollOnly", true);
        plan.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        plan.put("apiKeyPlaceholderOnly", true);
        plan.put("apiKeyPlaceholder", "Bearer {input your NGC_API_KEY here}");
        plan.put("targets", List.of("deployment", "service", "nim-health", "nim-models"));
        plan.put("maxAttempts", 120);
        plan.put("intervalSeconds", 5);
        plan.put("steps", readinessSteps(safeInput.organizationId(), deploymentName));
        plan.put("successSignals", List.of(
            "Deployment list returns exactly one matching NIM deployment",
            "Deployment entranceMap contains http or http1 URL",
            "GET /v1/health/live returns message='Service is live.' or live/status=live",
            "GET /v1/models returns a model id or available model list"
        ));
        plan.put("forbiddenActions", List.of(
            "Do not call POST /v1/chat/completions during readiness",
            "Do not call POST /v1/embeddings during readiness",
            "Do not send Authorization headers with a real API Key",
            "Do not store or display real NGC/NIM API Key material"
        ));
        plan.put("matureEvidence", List.of(
            "vue-kube-manager getDeploymentList uses listDeployment({page:1, limit:100, name})",
            "vue-kube-manager checkApiStatus uses GET apiUrl + /v1/health/live",
            "vue-kube-manager getModelName uses GET apiUrl + /v1/models"
        ));
        return plan;
    }

    private static List<Map<String, Object>> readinessSteps(String organizationId, String deploymentName) {
        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(readinessStep(
            "deployment",
            "GET",
            "/api/{orgId}/deployment",
            Map.of(
                "organizationId", valueOrPlaceholder(organizationId, "{trustedOrgId}"),
                "page", "1",
                "limit", "100",
                "name", valueOrPlaceholder(deploymentName, "{deploymentName}")
            ),
            "按名称回查创建后的 NIM Deployment，并读取 entranceMap.http/http1。"
        ));
        steps.add(readinessStep(
            "service",
            "EXTRACT_FROM_DEPLOYMENT_RESPONSE",
            "deployment.entranceMap.http|http1",
            Map.of(),
            "从 Deployment 响应派生 NIM 服务 base URL；不调用写接口。"
        ));
        steps.add(readinessStep(
            "nim-health",
            "GET",
            "{nimApiBasePath}/v1/health/live",
            Map.of(),
            "只读探测 NIM 服务是否 live。"
        ));
        steps.add(readinessStep(
            "nim-models",
            "GET",
            "{nimApiBasePath}/v1/models",
            Map.of(),
            "只读回读模型 ID；失败时只返回 fetch failed，不生成 API Key。"
        ));
        return steps;
    }

    private static Map<String, Object> readinessStep(String target,
                                                     String method,
                                                     String endpoint,
                                                     Map<String, Object> query,
                                                     String purpose) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("target", target);
        step.put("method", method);
        step.put("endpoint", endpoint);
        step.put("query", query);
        step.put("purpose", purpose);
        return step;
    }

    private static List<String> ignoredCallerClaimKeys(Map<String, Object> params) {
        List<String> riskyKeys = List.of(
            "approved",
            "confirmed",
            "hitlConfirmed",
            "safeToPost",
            "licenseValid",
            "nvaieLicenseValid",
            "sysAdmin",
            "isSysOrg",
            "role",
            "fallbackTool",
            "useFallback",
            "token",
            "password",
            "secret",
            "apiKey",
            "ngcApiKey"
        );
        List<String> keys = new ArrayList<>();
        for (String key : riskyKeys) {
            if (params.containsKey(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private static String valueOrPlaceholder(String value, String placeholder) {
        return hasText(value) ? value : placeholder;
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record AuditReadinessInput(
        String requestId,
        String conversationId,
        String userId,
        String organizationId,
        Map<String, Object> params,
        Map<String, Object> creationGate,
        Map<String, Object> deploymentBodyPreview,
        HitlConfirmation hitlConfirmation
    ) {
        AuditReadinessInput {
            requestId = requestId == null ? "" : requestId.trim();
            conversationId = conversationId == null ? "" : conversationId.trim();
            userId = userId == null ? "" : userId.trim();
            organizationId = organizationId == null ? "" : organizationId.trim();
            params = params == null ? Map.of() : objectMap(params);
            creationGate = creationGate == null ? Map.of() : objectMap(creationGate);
            deploymentBodyPreview = deploymentBodyPreview == null ? Map.of() : objectMap(deploymentBodyPreview);
        }

        static AuditReadinessInput empty() {
            return new AuditReadinessInput(
                "",
                "",
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(),
                null
            );
        }
    }
}
