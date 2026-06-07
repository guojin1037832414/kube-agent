package com.atlas.tool.impl;

import com.atlas.tool.core.NimForbiddenSecretMaterialDetector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NIM 创建后 readiness HTTP adapter 契约。
 *
 * <p>本类仍然是纯数据编译层：它只把 {@link NimCreateAuditReadinessSupport} 生成的 readiness plan
 * 编译成未来执行层可以消费的只读请求规格，不发起 HTTP、不持有 {@code KubeManagerHttpClient}、
 * 不访问真实 kube-manager 8100，也不向 NIM 服务发送真实 API Key。真正响应判定仍由
 * {@link NimCreateReadinessExecutorSupport} 消费离线/未来执行层传回的响应快照完成。</p>
 */
final class NimCreateReadinessHttpAdapterSupport {

    static final String ADAPTER_NAME = "NIM_CREATE_READINESS_HTTP_ADAPTER";

    private static final Set<String> REQUIRED_TARGETS = Set.of(
        "deployment",
        "service",
        "nim-health",
        "nim-models"
    );
    private static final Set<String> READ_ONLY_METHODS = Set.of(
        "GET",
        "EXTRACT_FROM_DEPLOYMENT_RESPONSE"
    );
    private static final Set<String> DEPLOYMENT_QUERY_KEYS = Set.of(
        "organizationId",
        "page",
        "limit",
        "name"
    );
    private static final Pattern HTTP_URL_PATTERN = Pattern.compile(
        "^(https?)://([^/@?#:\\s]+|\\[[0-9A-Fa-f:.]+])(?::(\\d{1,5}))?([^?#\\s]*)?$"
    );

    private NimCreateReadinessHttpAdapterSupport() {
    }

    static Map<String, Object> compile(ReadinessHttpAdapterInput input) {
        ReadinessHttpAdapterInput safeInput = input == null ? ReadinessHttpAdapterInput.empty() : input;
        Map<String, Object> plan = safeInput.readinessPlan();
        List<Map<String, Object>> blockers = new ArrayList<>();
        List<Map<String, Object>> pending = new ArrayList<>();

        validateNoSecretMaterial("readinessPlan", plan, blockers);
        Map<String, Map<String, Object>> steps = validatePlan(plan, blockers);

        ServiceUrlParts serviceUrlParts = null;
        if (hasText(safeInput.serviceApiUrl())) {
            validateNoSecretValue("serviceApiUrl", safeInput.serviceApiUrl(), blockers);
            serviceUrlParts = parseHttpUrl(safeInput.serviceApiUrl());
            if (serviceUrlParts == null) {
                blockers.add(blocker(
                    "SERVICE_URL_INVALID",
                    "readiness HTTP adapter 只接受不带认证信息、query、fragment 和路径穿越的 http/https 服务 URL。",
                    "service"
                ));
            }
        } else {
            pending.add(pending(
                "SERVICE_URL_NOT_DERIVED",
                "尚未从 Deployment entranceMap.http/http1 派生 NIM 服务 URL；本轮只能准备 kube-manager Deployment 只读回查。",
                "service"
            ));
        }

        List<Map<String, Object>> requestSpecs = blockers.isEmpty()
            ? requestSpecs(steps, serviceUrlParts, blockers)
            : List.of();
        if (!blockers.isEmpty()) {
            requestSpecs = List.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readinessHttpAdapter", ADAPTER_NAME);
        result.put("executionMode", "REQUEST_SPEC_CONTRACT_ONLY");
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("readOnly", true);
        result.put("pollOnly", Boolean.TRUE.equals(plan.get("pollOnly")));
        result.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        result.put("apiKeyPlaceholderOnly", true);
        result.put("apiKeyHeaderPolicy", "DO_NOT_SEND_REAL_API_KEY");
        result.put("attempt", Math.max(0, safeInput.attempt()));
        result.put("state", stateFor(blockers, serviceUrlParts));
        result.put("adapterPrepared", blockers.isEmpty());
        result.put("requestSpecs", requestSpecs);
        result.put("derivedSteps", derivedSteps());
        result.put("executorHandoff", executorHandoff());
        result.put("pendingBy", pending);
        result.put("blockedBy", blockers);
        result.put("forbiddenActionsEnforced", forbiddenActionsEnforced(blockers));
        result.put("matureEvidence", List.of(
            "vue-kube-manager getDeploymentList: GET deployment list by name",
            "vue-kube-manager readiness: GET apiUrl + /v1/health/live",
            "vue-kube-manager model readback: GET apiUrl + /v1/models"
        ));
        return result;
    }

    private static Map<String, Map<String, Object>> validatePlan(Map<String, Object> plan,
                                                                 List<Map<String, Object>> blockers) {
        Map<String, Map<String, Object>> byTarget = new LinkedHashMap<>();
        if (plan.isEmpty()
            || !Boolean.TRUE.equals(plan.get("readinessPollingPrepared"))
            || !Boolean.TRUE.equals(plan.get("pollOnly"))
            || !NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(plan.get("apiKeyHandling")))
            || !Boolean.TRUE.equals(plan.get("apiKeyPlaceholderOnly"))
            || !NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER.equals(text(plan.get("apiKeyPlaceholder")))
            || !containsAllTargets(plan.get("targets"))) {
            blockers.add(blocker(
                "READINESS_PLAN_NOT_EXECUTABLE",
                "readiness HTTP adapter 只接受已准备好的 poll-only/placeholder-only 计划，且必须覆盖 deployment/service/nim-health/nim-models。",
                "readiness-plan"
            ));
        }

        if (!(plan.get("steps") instanceof List<?> rawSteps) || rawSteps.isEmpty()) {
            blockers.add(blocker(
                "READINESS_STEPS_MISSING",
                "readiness HTTP adapter 需要明确的只读步骤列表。",
                "readiness-plan"
            ));
            return byTarget;
        }

        for (Object item : rawSteps) {
            Map<String, Object> step = objectMap(item);
            String target = text(step.get("target"));
            String method = text(step.get("method"));
            if (!READ_ONLY_METHODS.contains(method)) {
                blockers.add(blocker(
                    "FORBIDDEN_READINESS_STEP",
                    "readiness HTTP adapter 只允许 GET 或 EXTRACT_FROM_DEPLOYMENT_RESPONSE，禁止 POST/chat/embedding。",
                    "readiness-plan"
                ));
                continue;
            }
            if (!REQUIRED_TARGETS.contains(target)) {
                blockers.add(blocker(
                    "UNKNOWN_READINESS_STEP",
                    "readiness HTTP adapter 只接受 deployment/service/nim-health/nim-models 四类已审计 step。",
                    "readiness-plan"
                ));
                continue;
            }
            if (byTarget.containsKey(target)) {
                blockers.add(blocker(
                    "DUPLICATE_READINESS_STEP",
                    "readiness step target 必须唯一，避免隐藏第二条未审计请求。",
                    "readiness-plan"
                ));
                continue;
            }
            byTarget.put(target, step);
        }

        validateExactStep(byTarget, blockers, "deployment", "GET", "/api/{orgId}/deployment");
        validateExactStep(byTarget, blockers, "service", "EXTRACT_FROM_DEPLOYMENT_RESPONSE", "deployment.entranceMap.http|http1");
        validateExactStep(byTarget, blockers, "nim-health", "GET", "{nimApiBasePath}/v1/health/live");
        validateExactStep(byTarget, blockers, "nim-models", "GET", "{nimApiBasePath}/v1/models");
        validateDeploymentQuery(byTarget.get("deployment"), blockers);
        return byTarget;
    }

    private static void validateExactStep(Map<String, Map<String, Object>> steps,
                                          List<Map<String, Object>> blockers,
                                          String target,
                                          String method,
                                          String endpoint) {
        Map<String, Object> step = steps.get(target);
        if (step == null
            || !method.equals(text(step.get("method")))
            || !endpoint.equals(text(step.get("endpoint")))) {
            blockers.add(blocker(
                "READINESS_STEP_NOT_APPROVED",
                "readiness step 必须匹配成熟前端已审计路径: " + target + " " + method + " " + endpoint,
                "readiness-plan"
            ));
        }
    }

    private static void validateDeploymentQuery(Map<String, Object> deploymentStep,
                                                List<Map<String, Object>> blockers) {
        if (deploymentStep == null) {
            return;
        }
        Map<String, Object> query = objectMap(deploymentStep.get("query"));
        if (!query.keySet().equals(DEPLOYMENT_QUERY_KEYS)
            || !"1".equals(text(query.get("page")))
            || !"100".equals(text(query.get("limit")))
            || !safeIdentifier(query.get("organizationId"))
            || !safeDeploymentName(query.get("name"))) {
            blockers.add(blocker(
                "DEPLOYMENT_QUERY_NOT_APPROVED",
                "Deployment 回查 query 只能包含 organizationId/page=1/limit=100/name，且不得使用占位符或不安全路径片段。",
                "deployment"
            ));
        }
    }

    private static List<Map<String, Object>> requestSpecs(Map<String, Map<String, Object>> steps,
                                                          ServiceUrlParts serviceUrlParts,
                                                          List<Map<String, Object>> blockers) {
        if (!blockers.isEmpty() || steps.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> specs = new ArrayList<>();
        specs.add(deploymentRequestSpec(steps.get("deployment")));
        if (serviceUrlParts != null) {
            specs.add(nimRequestSpec("nim-health", serviceUrlParts, "/v1/health/live"));
            specs.add(nimRequestSpec("nim-models", serviceUrlParts, "/v1/models"));
        }
        return specs;
    }

    private static Map<String, Object> deploymentRequestSpec(Map<String, Object> deploymentStep) {
        Map<String, Object> query = objectMap(deploymentStep.get("query"));
        Map<String, Object> spec = baseRequestSpec("deployment", "KUBE_MANAGER_HTTP_GATEWAY", "/api/{orgId}/deployment");
        spec.put("pathTemplate", "/api/{orgId}/deployment");
        spec.put("organizationId", text(query.get("organizationId")));
        spec.put("query", new LinkedHashMap<>(Map.of(
            "page", "1",
            "limit", "100",
            "name", text(query.get("name"))
        )));
        spec.put("responseSlot", "deploymentListResponse");
        return spec;
    }

    private static Map<String, Object> nimRequestSpec(String target,
                                                       ServiceUrlParts serviceUrlParts,
                                                       String suffix) {
        String path = joinPaths(serviceUrlParts.normalizedPath(), suffix);
        Map<String, Object> spec = baseRequestSpec(target, "NIM_SERVICE_READINESS_PROBE", path);
        spec.put("serviceOrigin", serviceUrlParts.origin());
        spec.put("basePath", serviceUrlParts.normalizedPath());
        spec.put("apiPath", path);
        spec.put("query", Map.of());
        spec.put("responseSlot", "nim-health".equals(target) ? "healthResponse" : "modelsResponse");
        return spec;
    }

    private static Map<String, Object> baseRequestSpec(String target, String clientBoundary, String endpoint) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("target", target);
        spec.put("method", "GET");
        spec.put("endpoint", endpoint);
        spec.put("clientBoundary", clientBoundary);
        spec.put("bodyAllowed", false);
        spec.put("headersAllowed", false);
        spec.put("authorizationHeaderAllowed", false);
        spec.put("realApiKeyAllowed", false);
        spec.put("sideEffect", "NONE");
        spec.put("readOnly", true);
        return spec;
    }

    private static List<Map<String, Object>> derivedSteps() {
        return List.of(Map.of(
            "target", "service",
            "method", "EXTRACT_FROM_DEPLOYMENT_RESPONSE",
            "source", "deploymentListResponse.result[0].entranceMap.http|http1",
            "sideEffect", "NONE"
        ));
    }

    private static Map<String, Object> executorHandoff() {
        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("nextExecutor", NimCreateReadinessExecutorSupport.EXECUTOR_NAME);
        handoff.put("requiredResponseSlots", List.of(
            "deploymentListResponse",
            "healthResponse",
            "modelsResponse"
        ));
        handoff.put("executionMode", "OFFLINE_CONTRACT_EVALUATION");
        return handoff;
    }

    private static ServiceUrlParts parseHttpUrl(String serviceUrl) {
        Matcher matcher = HTTP_URL_PATTERN.matcher(text(serviceUrl));
        if (!matcher.matches()) {
            return null;
        }
        String scheme = text(matcher.group(1)).toLowerCase(Locale.ROOT);
        String host = text(matcher.group(2));
        String port = text(matcher.group(3));
        String path = normalizePath(matcher.group(4));
        if (!List.of("http", "https").contains(scheme)
            || !hasText(host)
            || !hostSafe(host, port)
            || !portValid(port)
            || !pathSafe(path)) {
            return null;
        }
        String origin = scheme + "://" + host + (hasText(port) ? ":" + port : "");
        return new ServiceUrlParts(origin, path);
    }

    private static boolean hostSafe(String host, String port) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return !"localhost".equals(normalized)
            && !"0.0.0.0".equals(normalized)
            && !normalized.startsWith("127.")
            && !"::1".equals(normalized)
            && !"[::1]".equals(normalized)
            && !"8100".equals(text(port));
    }

    private static boolean portValid(String port) {
        if (!hasText(port)) {
            return true;
        }
        try {
            int parsed = Integer.parseInt(port);
            return parsed > 0 && parsed <= 65535;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static String normalizePath(String rawPath) {
        String path = text(rawPath);
        if (!hasText(path)) {
            return "/";
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static boolean pathSafe(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return path.startsWith("/")
            && !path.contains("\\")
            && !path.contains("..")
            && !lower.contains("%2e")
            && path.chars().noneMatch(Character::isISOControl);
    }

    private static String joinPaths(String basePath, String suffix) {
        String base = "/".equals(basePath) ? "" : basePath;
        return base + suffix;
    }

    private static String stateFor(List<Map<String, Object>> blockers, ServiceUrlParts serviceUrlParts) {
        if (!blockers.isEmpty()) {
            return "REJECTED";
        }
        if (serviceUrlParts == null) {
            return "READY_FOR_DEPLOYMENT_POLL";
        }
        return "READY_FOR_READ_ONLY_HTTP_GETS";
    }

    private static boolean forbiddenActionsEnforced(List<Map<String, Object>> blockers) {
        return blockers.stream().noneMatch(item -> List.of(
            "FORBIDDEN_READINESS_STEP",
            "READINESS_ADAPTER_CONTAINS_FORBIDDEN_SECRET"
        ).contains(item.get("code")));
    }

    private static boolean containsAllTargets(Object rawTargets) {
        if (!(rawTargets instanceof List<?> targets)) {
            return false;
        }
        Set<String> actualTargets = new java.util.HashSet<>();
        for (Object target : targets) {
            actualTargets.add(text(target));
        }
        return actualTargets.containsAll(REQUIRED_TARGETS);
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "READINESS_ADAPTER_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static void validateNoSecretValue(String source,
                                              String value,
                                              List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(Map.of("value", value))) {
            blockers.add(blocker(
                "READINESS_ADAPTER_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Bearer token 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        return NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            map,
            NimForbiddenSecretMaterialDetector.textValuePolicyAllowing(
                Set.of(NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER)
            )
        );
    }

    private static boolean safeIdentifier(Object value) {
        return text(value).matches("[A-Za-z0-9_-]{1,64}");
    }

    private static boolean safeDeploymentName(Object value) {
        String name = text(value);
        return name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
            && !name.contains("..")
            && !name.contains("\\")
            && !name.contains("/");
    }

    private static Map<String, Object> blocker(String code, String message, String source) {
        Map<String, Object> blocker = new LinkedHashMap<>();
        blocker.put("code", code);
        blocker.put("message", message);
        blocker.put("source", source);
        return blocker;
    }

    private static Map<String, Object> pending(String code, String message, String source) {
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("code", code);
        pending.put("message", message);
        pending.put("source", source);
        return pending;
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

    record ReadinessHttpAdapterInput(
        Map<String, Object> readinessPlan,
        String serviceApiUrl,
        int attempt
    ) {
        ReadinessHttpAdapterInput {
            readinessPlan = readinessPlan == null ? Map.of() : objectMap(readinessPlan);
            serviceApiUrl = serviceApiUrl == null ? "" : serviceApiUrl.trim();
        }

        static ReadinessHttpAdapterInput empty() {
            return new ReadinessHttpAdapterInput(
                Map.of(),
                "",
                0
            );
        }
    }

    private record ServiceUrlParts(String origin, String normalizedPath) {
    }
}
