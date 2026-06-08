package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM 创建后 readiness HTTP adapter 契约测试。
 *
 * <p>本测试只验证 request spec 编译结果，不发 HTTP、不访问 kube-manager 8100、
 * 不轮询真实 NIM 服务。adapter 不能成为写入 release gate；它只是未来只读执行层的白名单请求规格。</p>
 */
class NimCreateReadinessHttpAdapterSupportTest {

    @Test
    void adapter_shouldCompileWhitelistRequestSpecsWithoutNetworkOrAuthHeaders() {
        Map<String, Object> report = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            readinessPlan(),
            "https://nim.example.com/nim/llama/",
            4
        ));

        assertEquals("NIM_CREATE_READINESS_HTTP_ADAPTER", report.get("readinessHttpAdapter"));
        assertEquals("REQUEST_SPEC_CONTRACT_ONLY", report.get("executionMode"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("readOnly"));
        assertEquals(true, report.get("pollOnly"));
        assertEquals(NimCreateStateMachineSupport.API_KEY_POLICY, report.get("apiKeyHandling"));
        assertEquals("DO_NOT_SEND_REAL_API_KEY", report.get("apiKeyHeaderPolicy"));
        assertEquals("READY_FOR_READ_ONLY_HTTP_GETS", report.get("state"));
        assertEquals(true, report.get("adapterPrepared"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> specs = (List<Map<String, Object>>) report.get("requestSpecs");
        assertEquals(3, specs.size());
        assertTargets(specs, List.of("deployment", "nim-health", "nim-models"));
        for (Map<String, Object> spec : specs) {
            assertEquals("GET", spec.get("method"));
            assertEquals(false, spec.get("bodyAllowed"));
            assertEquals(false, spec.get("headersAllowed"));
            assertEquals(false, spec.get("authorizationHeaderAllowed"));
            assertEquals(false, spec.get("realApiKeyAllowed"));
            assertEquals("NONE", spec.get("sideEffect"));
            assertFalse(spec.toString().contains("8100"));
            assertFalse(spec.toString().contains("Authorization"));
        }

        Map<String, Object> deployment = byTarget(specs, "deployment");
        assertEquals("KUBE_MANAGER_HTTP_GATEWAY", deployment.get("clientBoundary"));
        assertEquals("/api/{orgId}/deployment", deployment.get("endpoint"));
        assertEquals("100002", deployment.get("organizationId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> query = (Map<String, Object>) deployment.get("query");
        assertEquals(Map.of("page", "1", "limit", "100", "name", "llama-nim"), query);
        assertFalse(deployment.containsKey("body"));

        Map<String, Object> health = byTarget(specs, "nim-health");
        assertEquals("NIM_SERVICE_READINESS_PROBE", health.get("clientBoundary"));
        assertEquals("https://nim.example.com", health.get("serviceOrigin"));
        assertEquals("/nim/llama", health.get("basePath"));
        assertEquals("/nim/llama/v1/health/live", health.get("apiPath"));
        assertEquals("healthResponse", health.get("responseSlot"));

        Map<String, Object> models = byTarget(specs, "nim-models");
        assertEquals("/nim/llama/v1/models", models.get("apiPath"));
        assertEquals("modelsResponse", models.get("responseSlot"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> derivedSteps = (List<Map<String, Object>>) report.get("derivedSteps");
        assertEquals(1, derivedSteps.size());
        assertEquals("service", derivedSteps.get(0).get("target"));
    }

    @Test
    void adapter_shouldPrepareDeploymentOnlyWhenServiceUrlHasNotBeenDerivedYet() {
        Map<String, Object> report = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            readinessPlan(),
            "",
            0
        ));

        assertEquals("READY_FOR_DEPLOYMENT_POLL", report.get("state"));
        assertEquals(true, report.get("adapterPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> specs = (List<Map<String, Object>>) report.get("requestSpecs");
        assertEquals(1, specs.size());
        assertEquals("deployment", specs.get(0).get("target"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pending = (List<Map<String, Object>>) report.get("pendingBy");
        assertHasItem(pending, "SERVICE_URL_NOT_DERIVED");
    }

    @Test
    void adapter_shouldRejectPostOrUnapprovedGetEndpoint() {
        Map<String, Object> postPlan = mutablePlan();
        replaceStep(postPlan, "nim-health", Map.of(
            "target", "nim-health",
            "method", "POST",
            "endpoint", "{nimApiBasePath}/v1/health/live"
        ));

        Map<String, Object> rejectedPost = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            postPlan,
            "https://nim.example.com/nim",
            1
        ));

        assertEquals("REJECTED", rejectedPost.get("state"));
        assertEquals(false, rejectedPost.get("adapterPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> postBlockers = (List<Map<String, Object>>) rejectedPost.get("blockedBy");
        assertHasItem(postBlockers, "FORBIDDEN_READINESS_STEP");
        assertHasItem(postBlockers, "READINESS_STEP_NOT_APPROVED");
        assertEquals(false, rejectedPost.get("forbiddenActionsEnforced"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> postSpecs = (List<Map<String, Object>>) rejectedPost.get("requestSpecs");
        assertTrue(postSpecs.isEmpty());

        Map<String, Object> chatPlan = mutablePlan();
        replaceStep(chatPlan, "nim-models", Map.of(
            "target", "nim-models",
            "method", "GET",
            "endpoint", "{nimApiBasePath}/v1/chat/completions"
        ));

        Map<String, Object> rejectedEndpoint = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            chatPlan,
            "https://nim.example.com/nim",
            1
        ));

        assertEquals("REJECTED", rejectedEndpoint.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> endpointBlockers = (List<Map<String, Object>>) rejectedEndpoint.get("blockedBy");
        assertHasItem(endpointBlockers, "READINESS_STEP_NOT_APPROVED");

        Map<String, Object> unknownGetPlan = mutablePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unknownSteps = (List<Map<String, Object>>) unknownGetPlan.get("steps");
        unknownSteps.add(Map.of(
            "target", "nim-chat",
            "method", "GET",
            "endpoint", "{nimApiBasePath}/v1/chat/completions"
        ));

        Map<String, Object> rejectedUnknownGet = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            unknownGetPlan,
            "https://nim.example.com/nim",
            1
        ));

        assertEquals("REJECTED", rejectedUnknownGet.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unknownBlockers = (List<Map<String, Object>>) rejectedUnknownGet.get("blockedBy");
        assertHasItem(unknownBlockers, "UNKNOWN_READINESS_STEP");
    }

    @Test
    void adapter_shouldRejectUnpreparedPlanUnsafeQueryOrSecretMaterial() {
        Map<String, Object> unprepared = mutablePlan();
        unprepared.put("readinessPollingPrepared", false);

        Map<String, Object> rejectedUnprepared = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            unprepared,
            "https://nim.example.com/nim",
            1
        ));

        assertEquals("REJECTED", rejectedUnprepared.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unpreparedBlockers = (List<Map<String, Object>>) rejectedUnprepared.get("blockedBy");
        assertHasItem(unpreparedBlockers, "READINESS_PLAN_NOT_EXECUTABLE");

        Map<String, Object> unsafeQuery = mutablePlan();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) unsafeQuery.get("steps");
        Map<String, Object> deployment = new java.util.LinkedHashMap<>(steps.get(0));
        deployment.put("query", Map.of(
            "organizationId", "100002",
            "page", "1",
            "limit", "100",
            "name", "../admin"
        ));
        steps.set(0, deployment);

        Map<String, Object> rejectedQuery = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            unsafeQuery,
            "https://nim.example.com/nim",
            1
        ));

        assertEquals("REJECTED", rejectedQuery.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queryBlockers = (List<Map<String, Object>>) rejectedQuery.get("blockedBy");
        assertHasItem(queryBlockers, "DEPLOYMENT_QUERY_NOT_APPROVED");

        Map<String, Object> secretPlan = mutablePlan();
        secretPlan.put("Authorization", "Bearer real-key-material");
        Map<String, Object> rejectedSecret = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            secretPlan,
            "https://nim.example.com/nim",
            1
        ));

        assertEquals("REJECTED", rejectedSecret.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> secretBlockers = (List<Map<String, Object>>) rejectedSecret.get("blockedBy");
        assertHasItem(secretBlockers, "READINESS_ADAPTER_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void adapter_shouldRejectForgedReadinessTargetSupersetBeforeBuildingSpecs() {
        Map<String, Object> forgedPlan = mutablePlan();
        forgedPlan.put("targets", List.of("deployment", "service", "nim-health", "nim-models", "nim-chat"));

        Map<String, Object> rejected = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            forgedPlan,
            "https://nim.example.com/nim",
            1
        ));

        assertEquals("REJECTED", rejected.get("state"));
        assertEquals(false, rejected.get("adapterPrepared"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) rejected.get("blockedBy");
        assertHasItem(blockers, "READINESS_PLAN_NOT_EXECUTABLE");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> specs = (List<Map<String, Object>>) rejected.get("requestSpecs");
        assertTrue(specs.isEmpty());
    }

    @Test
    void adapter_shouldRejectUnsafeServiceUrlsBeforeBuildingNimSpecs() {
        List<String> unsafeUrls = List.of(
            "ftp://nim.example.com/nim",
            "https://key@nim.example.com/nim",
            "https://nim.example.com:70000/nim",
            "http://127.0.0.1:8100/nim",
            "http://localhost:8000/nim",
            "https://nim.example.com/nim/../admin",
            "https://nim.example.com/%2e%2e/admin",
            "https://nim.example.com/nim?Authorization=Bearer real",
            "https://nim.example.com/nim#fragment"
        );

        for (String unsafeUrl : unsafeUrls) {
            Map<String, Object> report = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
                readinessPlan(),
                unsafeUrl,
                1
            ));

            assertEquals("REJECTED", report.get("state"), "url=" + unsafeUrl);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
            assertTrue(blockers.stream().anyMatch(item -> List.of(
                "SERVICE_URL_INVALID",
                "READINESS_ADAPTER_CONTAINS_FORBIDDEN_SECRET"
            ).contains(item.get("code"))), "url=" + unsafeUrl + ", blockers=" + blockers);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> specs = (List<Map<String, Object>>) report.get("requestSpecs");
            assertTrue(specs.isEmpty(), "url=" + unsafeUrl);
        }
    }

    @Test
    void adapter_shouldAllowApiKeyPlaceholderOnlyOutsideForbiddenSecretKeys() {
        Map<String, Object> planWithDocumentedPlaceholder = mutablePlan();
        planWithDocumentedPlaceholder.put("operatorHint", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER);

        Map<String, Object> allowed = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            planWithDocumentedPlaceholder,
            "https://nim.example.com/nim",
            1
        ));

        assertEquals("READY_FOR_READ_ONLY_HTTP_GETS", allowed.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> allowedBlockers = (List<Map<String, Object>>) allowed.get("blockedBy");
        assertTrue(allowedBlockers.isEmpty());

        Map<String, Object> planWithForbiddenPlaceholder = mutablePlan();
        planWithForbiddenPlaceholder.put("token", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER);

        Map<String, Object> rejected = NimCreateReadinessHttpAdapterSupport.compile(new NimCreateReadinessHttpAdapterSupport.ReadinessHttpAdapterInput(
            planWithForbiddenPlaceholder,
            "https://nim.example.com/nim",
            1
        ));

        assertEquals("REJECTED", rejected.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rejectedBlockers = (List<Map<String, Object>>) rejected.get("blockedBy");
        assertHasItem(rejectedBlockers, "READINESS_ADAPTER_CONTAINS_FORBIDDEN_SECRET");
    }

    @Test
    void stateMachine_shouldRejectReadinessPlanWithoutModelsTargetAndStep() {
        Map<String, Object> readiness = mutablePlan();
        readiness.put("targets", List.of("deployment", "service", "nim-health"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) readiness.get("steps");
        steps.removeIf(step -> "nim-models".equals(step.get("target")));

        Map<String, Object> guard = NimCreateStateMachineSupport.evaluate(new NimCreateStateMachineSupport.ReadinessRequest(
            Map.of("name", "nim-missing-models"),
            openGate(),
            completePreview(),
            com.atlas.hitl.HitlConfirmation.human("thread-1", "nim_create"),
            completeAuditContext(),
            completeAuditReceipt(),
            readiness,
            completeReadinessExecutionReport(),
            NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE,
            true
        ));

        assertEquals("HELD", guard.get("state"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) guard.get("blockedBy");
        assertHasItem(blockers, "READINESS_PLAN_NOT_READY");
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
                Map.of(
                    "target", "deployment",
                    "method", "GET",
                    "endpoint", "/api/{orgId}/deployment",
                    "query", Map.of(
                        "organizationId", "100002",
                        "page", "1",
                        "limit", "100",
                        "name", "llama-nim"
                    )
                ),
                Map.of("target", "service", "method", "EXTRACT_FROM_DEPLOYMENT_RESPONSE", "endpoint", "deployment.entranceMap.http|http1"),
                Map.of("target", "nim-health", "method", "GET", "endpoint", "{nimApiBasePath}/v1/health/live"),
                Map.of("target", "nim-models", "method", "GET", "endpoint", "{nimApiBasePath}/v1/models")
            ))
        );
    }

    private Map<String, Object> mutablePlan() {
        Map<String, Object> copy = new java.util.LinkedHashMap<>(readinessPlan());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = new java.util.ArrayList<>((List<Map<String, Object>>) copy.get("steps"));
        copy.put("steps", steps);
        return copy;
    }

    private void replaceStep(Map<String, Object> plan, String target, Map<String, Object> replacement) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> steps = (List<Map<String, Object>>) plan.get("steps");
        for (int i = 0; i < steps.size(); i++) {
            if (target.equals(steps.get(i).get("target"))) {
                steps.set(i, replacement);
                return;
            }
        }
    }

    private Map<String, Object> openGate() {
        return Map.of(
            "gateState", NimCreateStateMachineSupport.READY_GATE_STATE,
            "allowedToCreateNow", true,
            "trustedPolicySnapshot", Map.of(
                "snapshotState", NimCreateStateMachineSupport.TRUSTED_POLICY_PASSED,
                "authoritative", true,
                "protectedFromCallerParams", true
            ),
            "futureWritePath", Map.of(
                "directUseOfPreviewAllowed", false,
                "fallbackAllowedFromPreflight", false
            )
        );
    }

    private Map<String, Object> completePreview() {
        return Map.of(
            "safeToPost", false,
            "previewOnly", true,
            "bodyComplete", true,
            "bodyDraft", Map.of(
                "displayName", "llama-nim",
                "image", "nvcr.io/nim/llama:1.0",
                "templateId", 88
            )
        );
    }

    private Map<String, Object> completeAuditContext() {
        return Map.ofEntries(
            entry("auditPrepared", true),
            entry("auditEventType", "NIM_CREATE_REQUEST"),
            entry("requestId", "req-1"),
            entry("conversationId", "conv-1"),
            entry("userId", "user-1"),
            entry("organizationId", "100002"),
            entry("targetTool", "nim_create"),
            entry("writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE),
            entry("secretRedactionApplied", true),
            entry("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY)
        );
    }

    private Map<String, Object> completeAuditReceipt() {
        return Map.ofEntries(
            entry("auditReceiptPrepared", true),
            entry("receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS),
            entry("storageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE),
            entry("durable", true),
            entry("realStorageTouched", true),
            entry("releaseEligible", true),
            entry("eventDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM),
            entry("eventDigest", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"),
            entry("receiptId", "nim-audit-durable-req-1"),
            entry("auditEventType", "NIM_CREATE_REQUEST"),
            entry("requestId", "req-1"),
            entry("conversationId", "conv-1"),
            entry("userId", "user-1"),
            entry("organizationId", "100002"),
            entry("targetTool", "nim_create"),
            entry("writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE)
        );
    }

    private Map<String, Object> completeReadinessExecutionReport() {
        return Map.ofEntries(
            entry("readinessExecutor", NimCreateReadinessExecutorSupport.EXECUTOR_NAME),
            entry("executionMode", "OFFLINE_CONTRACT_EVALUATION"),
            entry("sideEffect", "NONE"),
            entry("readOnly", true),
            entry("pollOnly", true),
            entry("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY),
            entry("apiKeyPlaceholderOnly", true),
            entry("apiKeyPlaceholder", NimCreateReadinessExecutorSupport.API_KEY_PLACEHOLDER),
            entry("state", "READY"),
            entry("ready", true),
            entry("deployment", Map.of("state", "MATCHED", "matched", true, "matchCount", 1)),
            entry("service", Map.of("state", "SERVICE_URL_READY", "serviceUrlReady", true, "entranceSource", "http", "nimApiBasePath", "/nim")),
            entry("health", Map.of("state", "LIVE", "live", true)),
            entry("models", Map.of("state", "MODEL_FOUND", "modelName", "llama")),
            entry("blockedBy", List.of()),
            entry("pendingBy", List.of()),
            entry("nextPoll", Map.of("prepared", false, "pollOnly", true, "afterSeconds", 0, "nextAttempt", 1, "maxAttempts", 120)),
            entry("forbiddenActionsEnforced", true)
        );
    }

    private void assertTargets(List<Map<String, Object>> specs, List<String> expectedTargets) {
        for (String target : expectedTargets) {
            assertTrue(specs.stream().anyMatch(spec -> target.equals(spec.get("target"))),
                "expected target: " + target + ", actual specs: " + specs);
        }
    }

    private Map<String, Object> byTarget(List<Map<String, Object>> specs, String target) {
        return specs.stream()
            .filter(spec -> target.equals(spec.get("target")))
            .findFirst()
            .orElseThrow();
    }

    private void assertHasItem(List<Map<String, Object>> items, String code) {
        assertTrue(items.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected code: " + code + ", actual items: " + items);
    }
}
