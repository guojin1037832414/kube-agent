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
 * NIM 创建后 readiness 只读执行器契约。
 *
 * <p>本类仍然是纯函数支持层：不发 HTTP、不访问 kube-manager、不访问真实 NIM 服务，也不读取或保存
 * API Key。它消费 {@link NimCreateAuditReadinessSupport} 生成的 readiness 计划和测试/未来执行层传入的
 * 离线响应快照，判断 mature 前端那条创建后链路是否已经满足：
 * Deployment 回查唯一命中 -> entranceMap 派生服务入口 -> health live -> models 回读。</p>
 */
final class NimCreateReadinessExecutorSupport {

    static final String EXECUTOR_NAME = "NIM_CREATE_READINESS_EXECUTOR";
    static final String API_KEY_PLACEHOLDER = "Bearer {input your NGC_API_KEY here}";

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
    private static final Pattern HTTP_URL_PATTERN = Pattern.compile(
        "^(https?)://([^/@?#:\\s]+|\\[[0-9A-Fa-f:.]+])(?::\\d{1,5})?([^?#\\s]*)?(?:\\?[^#\\s]*)?(?:#\\S*)?$"
    );

    private NimCreateReadinessExecutorSupport() {
    }

    static Map<String, Object> evaluate(ReadinessExecutionInput input) {
        ReadinessExecutionInput safeInput = input == null ? ReadinessExecutionInput.empty() : input;
        Map<String, Object> plan = safeInput.readinessPlan();
        List<Map<String, Object>> blockers = new ArrayList<>();
        List<Map<String, Object>> pending = new ArrayList<>();

        validatePlan(plan, blockers);
        validateNoSecretMaterial("readinessPlan", plan, blockers);
        validateNoSecretMaterial("deploymentListResponse", safeInput.deploymentListResponse(), blockers);
        validateNoSecretMaterial("healthResponse", safeInput.healthResponse(), blockers);
        validateNoSecretMaterial("modelsResponse", safeInput.modelsResponse(), blockers);

        int maxAttempts = positiveInt(plan.get("maxAttempts"), 120);
        int intervalSeconds = positiveInt(plan.get("intervalSeconds"), 5);
        int currentAttempt = Math.max(0, safeInput.attempt());

        List<Map<String, Object>> deployments = deploymentsFrom(safeInput.deploymentListResponse());
        Map<String, Object> deploymentReport = deploymentReport(deployments, blockers, pending);
        Map<String, Object> selectedDeployment = objectMap(deploymentReport.get("selectedDeployment"));
        Map<String, Object> serviceReport = serviceReport(selectedDeployment, blockers, pending);
        Map<String, Object> healthReport = healthReport(safeInput.healthResponse(), serviceReport, pending);
        Map<String, Object> modelReport = modelReport(safeInput.modelsResponse(), healthReport);

        boolean timeout = blockers.isEmpty()
            && !Boolean.TRUE.equals(healthReport.get("live"))
            && currentAttempt >= maxAttempts;
        if (timeout) {
            blockers.add(blocker(
                "READINESS_POLLING_TIMEOUT",
                "NIM readiness 只读轮询已达到计划最大次数，仍未看到 health live 信号。",
                "readiness"
            ));
        }

        boolean ready = blockers.isEmpty()
            && Boolean.TRUE.equals(deploymentReport.get("matched"))
            && Boolean.TRUE.equals(serviceReport.get("serviceUrlReady"))
            && Boolean.TRUE.equals(healthReport.get("live"));
        String state = stateFor(ready, blockers, currentAttempt, maxAttempts);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("readinessExecutor", EXECUTOR_NAME);
        result.put("executionMode", "OFFLINE_CONTRACT_EVALUATION");
        result.put("sideEffect", "NONE");
        result.put("readOnly", true);
        result.put("pollOnly", Boolean.TRUE.equals(plan.get("pollOnly")));
        result.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        result.put("apiKeyPlaceholderOnly", true);
        result.put("apiKeyPlaceholder", API_KEY_PLACEHOLDER);
        result.put("attempt", currentAttempt);
        result.put("maxAttempts", maxAttempts);
        result.put("intervalSeconds", intervalSeconds);
        result.put("state", state);
        result.put("ready", ready);
        result.put("deployment", withoutSelectedDeployment(deploymentReport));
        result.put("service", serviceReport);
        result.put("health", healthReport);
        result.put("models", modelReport);
        result.put("pendingBy", pending);
        result.put("blockedBy", blockers);
        result.put("nextPoll", nextPoll(blockers, ready, currentAttempt, maxAttempts, intervalSeconds));
        result.put("forbiddenActionsEnforced", forbiddenActionsEnforced(blockers));
        result.put("matureEvidence", List.of(
            "vue-kube-manager getDeploymentList: listDeployment({page:1, limit:100, name})",
            "vue-kube-manager readiness: GET /v1/health/live, accepted by message/live/status",
            "vue-kube-manager model readback: GET /v1/models, model failure becomes fetch failed"
        ));
        return result;
    }

    private static void validatePlan(Map<String, Object> plan, List<Map<String, Object>> blockers) {
        if (plan.isEmpty()
            || !Boolean.TRUE.equals(plan.get("readinessPollingPrepared"))
            || !Boolean.TRUE.equals(plan.get("pollOnly"))
            || !NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(plan.get("apiKeyHandling")))
            || !Boolean.TRUE.equals(plan.get("apiKeyPlaceholderOnly"))
            || !API_KEY_PLACEHOLDER.equals(text(plan.get("apiKeyPlaceholder")))
            || !targetsExactlyMatch(plan.get("targets"))) {
            blockers.add(blocker(
                "READINESS_PLAN_NOT_EXECUTABLE",
                "readiness 执行器只接受已准备好的只读计划，且必须覆盖 deployment/service/nim-health/nim-models。",
                "readiness-plan"
            ));
        }

        if (!readinessStepsAreReadOnly(plan.get("steps"))) {
            blockers.add(blocker(
                "FORBIDDEN_READINESS_STEP",
                "readiness 执行器只能消费 GET 或从 Deployment 响应派生的步骤；POST/chat/embedding 一律拒绝。",
                "readiness-plan"
            ));
        }
    }

    private static Map<String, Object> deploymentReport(List<Map<String, Object>> deployments,
                                                        List<Map<String, Object>> blockers,
                                                        List<Map<String, Object>> pending) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("matchCount", deployments.size());
        if (deployments.isEmpty()) {
            report.put("state", "PENDING_DEPLOYMENT_NOT_FOUND");
            report.put("matched", false);
            pending.add(pending(
                "DEPLOYMENT_NOT_FOUND",
                "尚未按名称回查到创建后的 NIM Deployment；下一轮继续只读查询 deployment 列表。",
                "deployment"
            ));
            return report;
        }
        if (deployments.size() > 1) {
            report.put("state", "BLOCKED_AMBIGUOUS_DEPLOYMENT");
            report.put("matched", false);
            blockers.add(blocker(
                "DEPLOYMENT_MATCH_AMBIGUOUS",
                "按名称回查得到多个 Deployment，无法安全派生唯一 NIM 服务入口。",
                "deployment"
            ));
            return report;
        }

        Map<String, Object> selected = deployments.get(0);
        report.put("state", "MATCHED");
        report.put("matched", true);
        report.put("name", firstText(selected.get("name"), selected.get("displayName")));
        report.put("displayName", firstText(selected.get("displayName"), selected.get("name")));
        report.put("selectedDeployment", selected);
        return report;
    }

    private static Map<String, Object> serviceReport(Map<String, Object> deployment,
                                                     List<Map<String, Object>> blockers,
                                                     List<Map<String, Object>> pending) {
        Map<String, Object> report = new LinkedHashMap<>();
        if (deployment.isEmpty()) {
            report.put("state", "WAITING_FOR_DEPLOYMENT");
            report.put("serviceUrlReady", false);
            return report;
        }

        Map<String, Object> entranceMap = objectMap(deployment.get("entranceMap"));
        String entranceSource = hasText(entranceMap.get("http")) ? "http" : (hasText(entranceMap.get("http1")) ? "http1" : "");
        String serviceUrl = firstText(entranceMap.get("http"), entranceMap.get("http1"));
        if (!hasText(serviceUrl)) {
            report.put("state", "PENDING_SERVICE_ENTRANCE_NOT_FOUND");
            report.put("serviceUrlReady", false);
            pending.add(pending(
                "SERVICE_ENTRANCE_NOT_FOUND",
                "Deployment 尚未返回 entranceMap.http/http1，无法派生 NIM API base path。",
                "service"
            ));
            return report;
        }

        ServiceUrlParts serviceUrlParts = parseHttpUrl(serviceUrl);
        if (serviceUrlParts == null) {
            report.put("state", "BLOCKED_INVALID_SERVICE_URL");
            report.put("serviceUrlReady", false);
            blockers.add(blocker(
                "SERVICE_URL_INVALID",
                "entranceMap.http/http1 必须是可解析的 http/https URL，才能安全派生 readiness 路径。",
                "service"
            ));
            return report;
        }

        report.put("state", "SERVICE_URL_READY");
        report.put("serviceUrlReady", true);
        report.put("entranceSource", entranceSource);
        report.put("serviceApiUrl", serviceUrl);
        report.put("nimApiBasePath", serviceUrlParts.normalizedPath());
        return report;
    }

    private static Map<String, Object> healthReport(Map<String, Object> healthResponse,
                                                    Map<String, Object> serviceReport,
                                                    List<Map<String, Object>> pending) {
        Map<String, Object> report = new LinkedHashMap<>();
        if (!Boolean.TRUE.equals(serviceReport.get("serviceUrlReady"))) {
            report.put("state", "WAITING_FOR_SERVICE_URL");
            report.put("live", false);
            return report;
        }

        Map<String, Object> payload = responsePayload(healthResponse);
        boolean live = "Service is live.".equals(text(payload.get("message")))
            || Boolean.TRUE.equals(payload.get("live"))
            || "live".equals(text(payload.get("status")));
        report.put("state", live ? "LIVE" : "PENDING_NOT_LIVE");
        report.put("live", live);
        report.put("acceptedSignals", List.of("message=Service is live.", "live=true", "status=live"));
        if (!live) {
            pending.add(pending(
                "NIM_HEALTH_NOT_LIVE",
                "NIM /v1/health/live 尚未返回 mature 前端认可的 live 信号。",
                "nim-health"
            ));
        }
        return report;
    }

    private static Map<String, Object> modelReport(Map<String, Object> modelsResponse,
                                                   Map<String, Object> healthReport) {
        Map<String, Object> report = new LinkedHashMap<>();
        if (!Boolean.TRUE.equals(healthReport.get("live"))) {
            report.put("state", "WAITING_FOR_HEALTH");
            report.put("modelName", "");
            report.put("fatalToReadiness", false);
            return report;
        }

        String modelName = extractModelName(modelsResponse);
        if (hasText(modelName)) {
            report.put("state", "MODEL_FOUND");
            report.put("modelName", modelName);
        } else {
            report.put("state", "UNAVAILABLE_NON_FATAL");
            report.put("modelName", "fetch failed");
        }
        report.put("fatalToReadiness", false);
        report.put("acceptedShapes", List.of("data[0].id", "available_models[0]"));
        return report;
    }

    private static List<Map<String, Object>> deploymentsFrom(Map<String, Object> response) {
        Object raw = response.get("result");
        if (!(raw instanceof List<?>)) {
            Map<String, Object> result = objectMap(raw);
            raw = result.get("records");
        }
        if (!(raw instanceof List<?>)) {
            raw = response.get("data");
        }
        List<Map<String, Object>> deployments = new ArrayList<>();
        if (raw instanceof List<?> items) {
            for (Object item : items) {
                Map<String, Object> deployment = objectMap(item);
                if (!deployment.isEmpty()) {
                    deployments.add(deployment);
                }
            }
        }
        return deployments;
    }

    private static Map<String, Object> responsePayload(Map<String, Object> response) {
        Map<String, Object> nested = objectMap(response.get("data"));
        return nested.isEmpty() ? response : nested;
    }

    private static String extractModelName(Map<String, Object> response) {
        Map<String, Object> payload = modelPayload(response);
        Object rawData = payload.get("data");
        if (rawData instanceof List<?> items && !items.isEmpty()) {
            Map<String, Object> first = objectMap(items.get(0));
            String id = text(first.get("id"));
            if (hasText(id)) {
                return id;
            }
        }
        Object rawAvailable = payload.get("available_models");
        if (rawAvailable instanceof List<?> available && !available.isEmpty()) {
            return text(available.get(0));
        }
        return "";
    }

    private static Map<String, Object> modelPayload(Map<String, Object> response) {
        Map<String, Object> nested = objectMap(response.get("data"));
        if (!nested.isEmpty() && (nested.containsKey("data") || nested.containsKey("available_models"))) {
            return nested;
        }
        return response;
    }

    private static ServiceUrlParts parseHttpUrl(String serviceUrl) {
        String candidate = text(serviceUrl);
        Matcher matcher = HTTP_URL_PATTERN.matcher(candidate);
        if (!matcher.matches()) {
            return null;
        }
        String scheme = text(matcher.group(1)).toLowerCase(Locale.ROOT);
        String host = text(matcher.group(2));
        String path = text(matcher.group(3));
        if (!List.of("http", "https").contains(scheme) || !hasText(host)) {
            return null;
        }
        return new ServiceUrlParts(scheme, host, normalizePath(path));
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

    private static Map<String, Object> withoutSelectedDeployment(Map<String, Object> report) {
        Map<String, Object> copy = new LinkedHashMap<>(report);
        copy.remove("selectedDeployment");
        return copy;
    }

    private static Map<String, Object> nextPoll(List<Map<String, Object>> blockers,
                                                boolean ready,
                                                int currentAttempt,
                                                int maxAttempts,
                                                int intervalSeconds) {
        Map<String, Object> next = new LinkedHashMap<>();
        boolean prepared = blockers.isEmpty() && !ready && currentAttempt < maxAttempts;
        next.put("prepared", prepared);
        next.put("pollOnly", true);
        next.put("afterSeconds", prepared ? intervalSeconds : 0);
        next.put("nextAttempt", prepared ? currentAttempt + 1 : currentAttempt);
        next.put("maxAttempts", maxAttempts);
        return next;
    }

    private static String stateFor(boolean ready,
                                   List<Map<String, Object>> blockers,
                                   int currentAttempt,
                                   int maxAttempts) {
        if (ready) {
            return "READY";
        }
        if (blockers.stream().anyMatch(item -> "READINESS_POLLING_TIMEOUT".equals(item.get("code")))) {
            return "TIMEOUT";
        }
        if (blockers.stream().anyMatch(item -> List.of(
            "READINESS_PLAN_NOT_EXECUTABLE",
            "FORBIDDEN_READINESS_STEP",
            "READINESS_CONTAINS_FORBIDDEN_SECRET"
        ).contains(item.get("code")))) {
            return "REJECTED";
        }
        if (!blockers.isEmpty()) {
            return "BLOCKED";
        }
        return currentAttempt >= maxAttempts ? "TIMEOUT" : "PENDING";
    }

    private static boolean forbiddenActionsEnforced(List<Map<String, Object>> blockers) {
        return blockers.stream().noneMatch(item -> List.of(
            "FORBIDDEN_READINESS_STEP",
            "READINESS_CONTAINS_FORBIDDEN_SECRET"
        ).contains(item.get("code")));
    }

    private static boolean targetsExactlyMatch(Object rawTargets) {
        if (!(rawTargets instanceof List<?> targets)) {
            return false;
        }
        Set<String> actualTargets = new java.util.HashSet<>();
        for (Object target : targets) {
            actualTargets.add(text(target));
        }
        return actualTargets.equals(REQUIRED_TARGETS);
    }

    private static boolean readinessStepsAreReadOnly(Object rawSteps) {
        if (!(rawSteps instanceof List<?> steps) || steps.isEmpty()) {
            return false;
        }
        boolean hasDeploymentRead = false;
        boolean hasServiceExtraction = false;
        boolean hasHealthRead = false;
        boolean hasModelsRead = false;
        for (Object item : steps) {
            Map<String, Object> step = objectMap(item);
            String target = text(step.get("target"));
            String method = text(step.get("method"));
            if (!READ_ONLY_METHODS.contains(method)) {
                return false;
            }
            if ("deployment".equals(target) && "GET".equals(method)) {
                hasDeploymentRead = true;
            }
            if ("service".equals(target) && "EXTRACT_FROM_DEPLOYMENT_RESPONSE".equals(method)) {
                hasServiceExtraction = true;
            }
            if ("nim-health".equals(target) && "GET".equals(method)) {
                hasHealthRead = true;
            }
            if ("nim-models".equals(target) && "GET".equals(method)) {
                hasModelsRead = true;
            }
        }
        return hasDeploymentRead && hasServiceExtraction && hasHealthRead && hasModelsRead;
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "READINESS_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 token、Authorization、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        return NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            map,
            NimForbiddenSecretMaterialDetector.textValuePolicyAllowing(Set.of(API_KEY_PLACEHOLDER))
        );
    }

    private static int positiveInt(Object value, int fallback) {
        if (value instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        try {
            int parsed = Integer.parseInt(text(value));
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
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

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record ReadinessExecutionInput(
        Map<String, Object> readinessPlan,
        Map<String, Object> deploymentListResponse,
        Map<String, Object> healthResponse,
        Map<String, Object> modelsResponse,
        int attempt
    ) {
        ReadinessExecutionInput {
            readinessPlan = readinessPlan == null ? Map.of() : objectMap(readinessPlan);
            deploymentListResponse = deploymentListResponse == null ? Map.of() : objectMap(deploymentListResponse);
            healthResponse = healthResponse == null ? Map.of() : objectMap(healthResponse);
            modelsResponse = modelsResponse == null ? Map.of() : objectMap(modelsResponse);
        }

        static ReadinessExecutionInput empty() {
            return new ReadinessExecutionInput(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                0
            );
        }
    }

    private record ServiceUrlParts(String scheme, String host, String normalizedPath) {
    }
}
