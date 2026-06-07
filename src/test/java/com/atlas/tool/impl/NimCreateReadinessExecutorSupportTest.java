package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 创建后 readiness 只读执行器契约测试。
 *
 * <p>这些用例只喂入离线响应快照，不发 HTTP、不访问 kube-manager `8100`、不访问真实 NIM 服务。
 * 目标是把 mature 前端创建后轮询的成功/等待/失败边界固化为可审计契约，方便未来真实执行层按同一规则接入。</p>
 */
class NimCreateReadinessExecutorSupportTest {

    @Test
    void executor_shouldReportReadyForSingleDeploymentHttpEntranceLiveHealthAndDataModel() {
        Map<String, Object> report = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            readinessPlan(),
            deploymentResponse(List.of(deployment("llama-nim", "http", "https://nim.example.com/nim/llama/"))),
            Map.of("message", "Service is live."),
            Map.of("data", List.of(Map.of("id", "meta/llama3-8b"))),
            3
        ));

        assertEquals("NIM_CREATE_READINESS_EXECUTOR", report.get("readinessExecutor"));
        assertEquals("OFFLINE_CONTRACT_EVALUATION", report.get("executionMode"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("readOnly"));
        assertEquals(true, report.get("pollOnly"));
        assertEquals(NimCreateStateMachineSupport.API_KEY_POLICY, report.get("apiKeyHandling"));
        assertEquals(true, report.get("apiKeyPlaceholderOnly"));
        assertEquals(NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER, report.get("apiKeyPlaceholder"));
        assertEquals("READY", report.get("state"));
        assertEquals(true, report.get("ready"));

        @SuppressWarnings("unchecked")
        Map<String, Object> deployment = (Map<String, Object>) report.get("deployment");
        assertEquals("MATCHED", deployment.get("state"));
        assertEquals(1, deployment.get("matchCount"));
        assertFalse(deployment.containsKey("selectedDeployment"));

        @SuppressWarnings("unchecked")
        Map<String, Object> service = (Map<String, Object>) report.get("service");
        assertEquals("SERVICE_URL_READY", service.get("state"));
        assertEquals("http", service.get("entranceSource"));
        assertEquals("/nim/llama", service.get("nimApiBasePath"));

        @SuppressWarnings("unchecked")
        Map<String, Object> health = (Map<String, Object>) report.get("health");
        assertEquals("LIVE", health.get("state"));
        assertEquals(true, health.get("live"));

        @SuppressWarnings("unchecked")
        Map<String, Object> models = (Map<String, Object>) report.get("models");
        assertEquals("MODEL_FOUND", models.get("state"));
        assertEquals("meta/llama3-8b", models.get("modelName"));
    }

    @Test
    void executor_shouldUseHttp1EntranceAndAvailableModelsShape() {
        Map<String, Object> report = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            readinessPlan(),
            deploymentResponse(List.of(deployment("mixtral-nim", "http1", "http://10.0.0.8:8000/service"))),
            Map.of("status", "live"),
            Map.of("available_models", List.of("mixtral-8x7b")),
            1
        ));

        assertEquals("READY", report.get("state"));
        assertEquals(true, report.get("ready"));
        @SuppressWarnings("unchecked")
        Map<String, Object> service = (Map<String, Object>) report.get("service");
        assertEquals("http1", service.get("entranceSource"));
        assertEquals("/service", service.get("nimApiBasePath"));
        @SuppressWarnings("unchecked")
        Map<String, Object> models = (Map<String, Object>) report.get("models");
        assertEquals("mixtral-8x7b", models.get("modelName"));
    }

    @Test
    void executor_shouldKeepModelFetchFailureNonFatalAfterHealthLive() {
        Map<String, Object> report = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            readinessPlan(),
            deploymentResponse(List.of(deployment("llama-nim", "http", "https://nim.example.com/nim"))),
            Map.of("live", true),
            Map.of("data", List.of(Map.of("name", "missing-id"))),
            5
        ));

        assertEquals("READY", report.get("state"));
        assertEquals(true, report.get("ready"));
        @SuppressWarnings("unchecked")
        Map<String, Object> models = (Map<String, Object>) report.get("models");
        assertEquals("UNAVAILABLE_NON_FATAL", models.get("state"));
        assertEquals("fetch failed", models.get("modelName"));
        assertEquals(false, models.get("fatalToReadiness"));
    }

    @Test
    void executor_shouldPrepareNextPollWhenDeploymentOrHealthIsNotReady() {
        Map<String, Object> noDeployment = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            readinessPlan(),
            deploymentResponse(List.of()),
            Map.of(),
            Map.of(),
            0
        ));

        assertEquals("PENDING", noDeployment.get("state"));
        assertEquals(false, noDeployment.get("ready"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nextPoll = (Map<String, Object>) noDeployment.get("nextPoll");
        assertEquals(true, nextPoll.get("prepared"));
        assertEquals(5, nextPoll.get("afterSeconds"));
        assertEquals(1, nextPoll.get("nextAttempt"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pending = (List<Map<String, Object>>) noDeployment.get("pendingBy");
        assertHasItem(pending, "DEPLOYMENT_NOT_FOUND");

        Map<String, Object> notLive = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            readinessPlan(),
            deploymentResponse(List.of(deployment("llama-nim", "http", "https://nim.example.com/nim"))),
            Map.of("status", "starting"),
            Map.of(),
            9
        ));

        assertEquals("PENDING", notLive.get("state"));
        assertEquals(false, notLive.get("ready"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> healthPending = (List<Map<String, Object>>) notLive.get("pendingBy");
        assertHasItem(healthPending, "NIM_HEALTH_NOT_LIVE");
    }

    @Test
    void executor_shouldBlockAmbiguousDeploymentOrInvalidServiceUrl() {
        Map<String, Object> ambiguous = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            readinessPlan(),
            deploymentResponse(List.of(
                deployment("llama-a", "http", "https://nim.example.com/a"),
                deployment("llama-b", "http", "https://nim.example.com/b")
            )),
            Map.of("live", true),
            Map.of(),
            1
        ));

        assertEquals("BLOCKED", ambiguous.get("state"));
        assertEquals(false, ambiguous.get("ready"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ambiguousBlockers = (List<Map<String, Object>>) ambiguous.get("blockedBy");
        assertHasItem(ambiguousBlockers, "DEPLOYMENT_MATCH_AMBIGUOUS");

        Map<String, Object> invalidUrl = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            readinessPlan(),
            deploymentResponse(List.of(deployment("bad", "http", "ftp://example.com/nim"))),
            Map.of("live", true),
            Map.of(),
            1
        ));

        assertEquals("BLOCKED", invalidUrl.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> urlBlockers = (List<Map<String, Object>>) invalidUrl.get("blockedBy");
        assertHasItem(urlBlockers, "SERVICE_URL_INVALID");
    }

    @Test
    void executor_shouldRejectPostStepOrRealSecretMaterial() {
        Map<String, Object> planWithPost = new java.util.LinkedHashMap<>(readinessPlan());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = new java.util.ArrayList<>((List<Map<String, Object>>) planWithPost.get("steps"));
        steps.add(Map.of(
            "target", "nim-chat",
            "method", "POST",
            "endpoint", "{nimApiBasePath}/v1/chat/completions"
        ));
        planWithPost.put("steps", steps);

        Map<String, Object> rejectedPost = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            planWithPost,
            deploymentResponse(List.of(deployment("llama-nim", "http", "https://nim.example.com/nim"))),
            Map.of("live", true),
            Map.of("data", List.of(Map.of("id", "llama"))),
            1
        ));

        assertEquals("REJECTED", rejectedPost.get("state"));
        assertEquals(false, rejectedPost.get("ready"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> postBlockers = (List<Map<String, Object>>) rejectedPost.get("blockedBy");
        assertHasItem(postBlockers, "FORBIDDEN_READINESS_STEP");
        assertEquals(false, rejectedPost.get("forbiddenActionsEnforced"));

        Map<String, Object> secretResponse = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            readinessPlan(),
            deploymentResponse(List.of(deployment("llama-nim", "http", "https://nim.example.com/nim"))),
            Map.of("Authorization", "Bearer real-key-material"),
            Map.of("data", List.of(Map.of("id", "llama"))),
            1
        ));

        assertEquals("REJECTED", secretResponse.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secretBlockers = (List<Map<String, Object>>) secretResponse.get("blockedBy");
        assertHasItem(secretBlockers, "READINESS_CONTAINS_FORBIDDEN_SECRET");
        assertEquals(false, secretResponse.get("forbiddenActionsEnforced"));
    }

    @Test
    void executor_shouldAllowApiKeyPlaceholderOnlyOutsideForbiddenSecretKeys() {
        Map<String, Object> planWithDocumentedPlaceholder = new java.util.LinkedHashMap<>(readinessPlan());
        planWithDocumentedPlaceholder.put("operatorHint", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER);

        Map<String, Object> allowed = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            planWithDocumentedPlaceholder,
            deploymentResponse(List.of(deployment("llama-nim", "http", "https://nim.example.com/nim"))),
            Map.of("live", true),
            Map.of("data", List.of(Map.of("id", "llama"))),
            1
        ));

        assertEquals("READY", allowed.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allowedBlockers = (List<Map<String, Object>>) allowed.get("blockedBy");
        assertTrue(allowedBlockers.isEmpty());

        Map<String, Object> planWithForbiddenPlaceholder = new java.util.LinkedHashMap<>(readinessPlan());
        planWithForbiddenPlaceholder.put("token", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER);

        Map<String, Object> rejected = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            planWithForbiddenPlaceholder,
            deploymentResponse(List.of(deployment("llama-nim", "http", "https://nim.example.com/nim"))),
            Map.of("live", true),
            Map.of("data", List.of(Map.of("id", "llama"))),
            1
        ));

        assertEquals("REJECTED", rejected.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rejectedBlockers = (List<Map<String, Object>>) rejected.get("blockedBy");
        assertHasItem(rejectedBlockers, "READINESS_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void executor_shouldTimeoutAtMaxAttemptWithoutLiveHealth() {
        Map<String, Object> report = NimCreateReadinessExecutorSupport.evaluate(new NimCreateReadinessExecutorSupport.ReadinessExecutionInput(
            readinessPlan(),
            deploymentResponse(List.of(deployment("llama-nim", "http", "https://nim.example.com/nim"))),
            Map.of("status", "starting"),
            Map.of(),
            120
        ));

        assertEquals("TIMEOUT", report.get("state"));
        assertEquals(false, report.get("ready"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nextPoll = (Map<String, Object>) report.get("nextPoll");
        assertEquals(false, nextPoll.get("prepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasItem(blockers, "READINESS_POLLING_TIMEOUT");
    }

    private Map<String, Object> readinessPlan() {
        return Map.ofEntries(
            entry("readinessPollingPrepared", true),
            entry("pollOnly", true),
            entry("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY),
            entry("apiKeyPlaceholderOnly", true),
            entry("apiKeyPlaceholder", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER),
            entry("targets", List.of("deployment", "service", "nim-health", "nim-models")),
            entry("maxAttempts", 120),
            entry("intervalSeconds", 5),
            entry("steps", List.of(
                Map.of("target", "deployment", "method", "GET", "endpoint", "/api/{orgId}/deployment"),
                Map.of("target", "service", "method", "EXTRACT_FROM_DEPLOYMENT_RESPONSE", "endpoint", "deployment.entranceMap.http|http1"),
                Map.of("target", "nim-health", "method", "GET", "endpoint", "{nimApiBasePath}/v1/health/live"),
                Map.of("target", "nim-models", "method", "GET", "endpoint", "{nimApiBasePath}/v1/models")
            ))
        );
    }

    private Map<String, Object> deploymentResponse(List<Map<String, Object>> deployments) {
        return Map.of("result", deployments);
    }

    private Map<String, Object> deployment(String name, String entranceKey, String entranceUrl) {
        return Map.of(
            "name", name,
            "displayName", name,
            "entranceMap", Map.of(entranceKey, entranceUrl)
        );
    }

    private void assertHasItem(List<Map<String, Object>> items, String code) {
        assertTrue(items.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected code: " + code + ", actual items: " + items);
    }
}
