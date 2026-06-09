package com.atlas.observability;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the local readiness contract for future kube-manager write retries.
 *
 * <p>Safety boundary: this service never calls kube-manager, RestClient,
 * Tool execution, LLMs, audit writers, or release switches. It is an
 * admin-only read model over code-level invariants and Resilience4j registry
 * facts, so opening the endpoint cannot mutate runtime behavior.</p>
 */
@Service
public class AgentKubeManagerWriteRetryReadinessService {

    private static final String READ_RETRY = "kubeManagerRead";
    private static final String WRITE_RETRY = "kubeManagerWrite";
    private static final String KUBE_MANAGER = "kubeManager";

    private final RetryRegistry retryRegistry;
    private final Clock clock;

    public AgentKubeManagerWriteRetryReadinessService(RetryRegistry retryRegistry) {
        this(retryRegistry, Clock.systemUTC());
    }

    AgentKubeManagerWriteRetryReadinessService(RetryRegistry retryRegistry, Clock clock) {
        this.retryRegistry = retryRegistry;
        this.clock = clock;
    }

    public AgentKubeManagerWriteRetryReadinessResponse readiness() {
        Retry readRetry = retryRegistry.find(READ_RETRY).orElse(null);
        Retry configuredWriteRetry = retryRegistry.find(WRITE_RETRY).orElse(null);
        return new AgentKubeManagerWriteRetryReadinessResponse(
            AgentKubeManagerWriteRetryReadinessResponse.SCHEMA_VERSION,
            Instant.now(clock),
            "NOT_READY",
            false,
            false,
            false,
            effectivePolicy(configuredWriteRetry),
            requirements(),
            currentEvidence(readRetry, configuredWriteRetry),
            blockedReasons(),
            futureEnablementProtocol(),
            endpointTemplates(),
            safety(),
            privacy()
        );
    }

    private Map<String, Object> effectivePolicy(Retry configuredWriteRetry) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("writeRetryName", WRITE_RETRY);
        policy.put("configuredRetryInstancePresent", configuredWriteRetry != null);
        policy.put("configuredButInactive", configuredWriteRetry != null);
        policy.put("automaticRetryEnabled", false);
        policy.put("effectiveForHttpMethods", List.of("POST", "PATCH", "PUT", "DELETE"));
        policy.put("currentGuards", List.of("CircuitBreaker:" + KUBE_MANAGER, "Bulkhead:" + KUBE_MANAGER));
        policy.put("readRetryName", READ_RETRY);
        policy.put("readRetryScope", List.of("GET"));
        policy.put("runtimeEnableEndpointPresent", false);
        policy.put("configurationMutationAllowed", false);
        policy.put("reason", "Write retries remain disabled until idempotency, audit, HITL, verification, and eval evidence are all bound.");
        if (configuredWriteRetry != null) {
            policy.put("configuredMaxAttempts", configuredWriteRetry.getRetryConfig().getMaxAttempts());
        }
        return Map.copyOf(policy);
    }

    private List<Map<String, Object>> requirements() {
        return List.of(
            requirement(
                "server-derived-idempotency-key",
                "BLOCKING",
                "Each retried write must use a server-derived key bound to audit receipt, request spec, principal, organization, and operation.",
                false
            ),
            requirement(
                "durable-prewrite-receipt",
                "PARTIAL",
                "High-risk Tool execution has durable prewrite support, but generic kube-manager write retry still needs the receipt bound into the HTTP outlet.",
                false
            ),
            requirement(
                "human-release-and-hitl-evidence",
                "BLOCKING",
                "High-risk state-changing operations need explicit human approval and release evidence before retry can amplify them.",
                false
            ),
            requirement(
                "read-after-write-verification",
                "BLOCKING",
                "Every retryable write needs deterministic post-write readback or equivalent verification before success can be claimed.",
                false
            ),
            requirement(
                "bounded-retry-predicate",
                "BLOCKING",
                "Retry must be limited by operation allowlist, idempotent failure classes, bounded attempts, and jittered backoff.",
                false
            ),
            requirement(
                "operation-allowlist-and-rbac",
                "BLOCKING",
                "Only explicitly modeled operations with tenant/RBAC evidence may be eligible for controlled retry.",
                false
            ),
            requirement(
                "compensation-and-replay-evidence",
                "BLOCKING",
                "Rollback or compensation guidance plus replay/eval regression evidence must be available before release.",
                false
            ),
            requirement(
                "ci-gate-and-operator-observability",
                "BLOCKING",
                "Release must be guarded by deterministic eval bundles and visible in admin-only redacted observability surfaces.",
                false
            )
        );
    }

    private Map<String, Object> requirement(String id, String status, String description, boolean satisfied) {
        Map<String, Object> requirement = new LinkedHashMap<>();
        requirement.put("id", id);
        requirement.put("status", status);
        requirement.put("satisfied", satisfied);
        requirement.put("description", description);
        return Map.copyOf(requirement);
    }

    private Map<String, Object> currentEvidence(Retry readRetry, Retry configuredWriteRetry) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("readRetryRegistryInstancePresent", readRetry != null);
        evidence.put("writeRetryRegistryInstancePresent", configuredWriteRetry != null);
        evidence.put("writeRetryConfiguredButInactive", configuredWriteRetry != null);
        evidence.put("writeRetryBoundIntoExecutionPath", false);
        evidence.put("writePathCircuitBreakerAndBulkheadOnly", true);
        evidence.put("highRiskDurablePrewriteGateExists", true);
        evidence.put("adminAuditQueryExists", true);
        evidence.put("replayTimelineExists", true);
        evidence.put("evalGateBundleExists", true);
        evidence.put("genericKubeManagerIdempotencyBoundaryExists", false);
        evidence.put("genericWriteOperationAllowlistExists", false);
        evidence.put("retryPredicateBoundToWriteFailureClasses", false);
        evidence.put("postWriteReadbackContractExists", false);
        evidence.put("runtimeWriteRetryEnablementSwitchExists", false);
        evidence.put("nimHpcSlurmBcmPhase2Paused", true);
        return Map.copyOf(evidence);
    }

    private List<String> blockedReasons() {
        return List.of(
            "generic-kube-manager-idempotency-boundary-missing",
            "write-operation-allowlist-missing",
            "write-retry-predicate-not-bound",
            "post-write-readback-contract-missing",
            "release-and-hitl-evidence-not-bound-to-http-outlet",
            "compensation-policy-missing",
            "runtime-enable-switch-intentionally-absent"
        );
    }

    private Map<String, Object> futureEnablementProtocol() {
        Map<String, Object> protocol = new LinkedHashMap<>();
        protocol.put("enablementMode", "future-code-release-only");
        protocol.put("runtimeToggleAllowed", false);
        protocol.put("callerCanRequestRetry", false);
        protocol.put("minimumRequiredChecks", List.of(
            "idempotency-key-bound-to-request-spec-and-durable-receipt",
            "durable-prewrite-receipt-before-first-attempt",
            "operation-allowlist-and-rbac-evidence",
            "retry-predicate-for-idempotent-failure-classes-only",
            "bounded-attempts-with-jittered-backoff",
            "read-after-write-verification-or-compensation",
            "redacted-replay-and-eval-gate-pass",
            "admin-observability-and-audit-query-visible"
        ));
        protocol.put("firstEligibleScope", "narrow allowlisted write operation after dedicated release review");
        protocol.put("defaultIfAnyCheckMissing", "fail-closed-no-auto-retry");
        return Map.copyOf(protocol);
    }

    private Map<String, Object> endpointTemplates() {
        Map<String, Object> endpoints = new LinkedHashMap<>();
        endpoints.put("healthSummary", "/api/agent/observability/kube-manager/http-outlet/health-summary");
        endpoints.put("writeRetryReadiness", "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness");
        endpoints.put("auditByTrace", "/api/agent/observability/audit/trace/{traceId}");
        endpoints.put("replayByTrace", "/api/agent/observability/replay/trace/{traceId}");
        endpoints.put("evalTraceSetGateBundle", "/api/agent/observability/eval/trace-sets/gate-bundle");
        endpoints.put("runtimeEnableWriteRetry", "not-exposed");
        return Map.copyOf(endpoints);
    }

    private Map<String, Object> safety() {
        Map<String, Object> safety = new LinkedHashMap<>();
        safety.put("adminOnly", true);
        safety.put("readOnly", true);
        safety.put("localProcessOnly", true);
        safety.put("summaryOnly", true);
        safety.put("kubeManagerCalls", false);
        safety.put("remoteProbeExecuted", false);
        safety.put("toolExecution", false);
        safety.put("llmUsed", false);
        safety.put("externalCalls", false);
        safety.put("auditWrite", false);
        safety.put("durableStorageMutation", false);
        safety.put("resiliencePolicyMutation", false);
        safety.put("writeRetryEnablement", false);
        safety.put("callerInputAccepted", false);
        safety.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(safety);
    }

    private Map<String, Object> privacy() {
        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("redactedOnly", true);
        privacy.put("containsRawBaseUrl", false);
        privacy.put("containsRawBackendPath", false);
        privacy.put("containsRawEndpoint", false);
        privacy.put("containsAuthorizationHeader", false);
        privacy.put("containsToken", false);
        privacy.put("containsTokenPrefix", false);
        privacy.put("containsLoginUsername", false);
        privacy.put("containsLoginPassword", false);
        privacy.put("containsRawPrincipal", false);
        privacy.put("containsRawOrganization", false);
        privacy.put("containsRawConversation", false);
        privacy.put("containsRawRequestBody", false);
        privacy.put("containsRawResponseBody", false);
        privacy.put("containsRawExceptionBody", false);
        return Map.copyOf(privacy);
    }
}
