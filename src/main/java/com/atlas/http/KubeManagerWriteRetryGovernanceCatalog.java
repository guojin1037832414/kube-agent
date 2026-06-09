package com.atlas.http;

import java.util.List;

/**
 * Source-owned retry predicate and compensation catalog for future writes.
 *
 * <p>The catalog is review-only. It must not be wired into Resilience4j,
 * {@link KubeManagerHttpClient}, Tool execution, or runtime write paths until a
 * future release binds idempotency, durable receipts, allowlist/RBAC, readback,
 * and eval evidence together.</p>
 */
public final class KubeManagerWriteRetryGovernanceCatalog {

    public static final String SCHEMA_VERSION = "kube-manager-write-retry-governance-catalog.v1";
    public static final String PREDICATE_CONTRACT_ID = "generic-write-retry-predicate.v1";

    private KubeManagerWriteRetryGovernanceCatalog() {
    }

    public static KubeManagerWriteRetryPredicateContract predicateContract() {
        return new KubeManagerWriteRetryPredicateContract(
            PREDICATE_CONTRACT_ID,
            true,
            false,
            false,
            false,
            2,
            "bounded-jittered-exponential",
            true,
            true,
            true,
            true,
            true,
            true,
            List.of(
                "transport-timeout-before-acceptance",
                "transient-gateway-after-idempotent-request",
                "rate-limited-idempotent-request"
            ),
            List.of(
                "caller-validation-error",
                "authentication-or-authorization-denied",
                "tenant-or-ownership-mismatch",
                "conflict-or-duplicate-state",
                "unknown-acceptance-without-readback"
            )
        );
    }

    public static List<KubeManagerWriteRetryFailureClass> failureClasses() {
        return List.of(
            failureClass(
                "transport-timeout-before-acceptance",
                "TRANSIENT_TRANSPORT",
                true,
                List.of(),
                List.of("ConnectTimeoutException", "Connection reset before bytes written"),
                List.of("same-idempotency-key", "durable-prewrite-receipt", "request-not-accepted-evidence"),
                "future-candidate-only-if-request-was-not-accepted"
            ),
            failureClass(
                "transient-gateway-after-idempotent-request",
                "TRANSIENT_HTTP",
                true,
                List.of(502, 503, 504),
                List.of(),
                List.of("same-idempotency-key", "durable-prewrite-receipt", "post-write-readback-contract"),
                "future-candidate-only-with-readback-before-success"
            ),
            failureClass(
                "rate-limited-idempotent-request",
                "TRANSIENT_HTTP",
                true,
                List.of(429),
                List.of("Retry-After bounded by policy"),
                List.of("same-idempotency-key", "durable-prewrite-receipt", "bounded-backoff-window"),
                "future-candidate-only-when-server-policy-allows"
            ),
            failureClass(
                "caller-validation-error",
                "NON_RETRYABLE_HTTP",
                false,
                List.of(400, 422),
                List.of(),
                List.of("redacted-final-audit-outcome"),
                "never-retry-caller-must-correct-input"
            ),
            failureClass(
                "authentication-or-authorization-denied",
                "NON_RETRYABLE_HTTP",
                false,
                List.of(401, 403),
                List.of(),
                List.of("principal-fingerprint", "rbac-evidence-digest", "redacted-final-audit-outcome"),
                "never-retry-authz-denial"
            ),
            failureClass(
                "tenant-or-ownership-mismatch",
                "NON_RETRYABLE_HTTP",
                false,
                List.of(404),
                List.of("resource owner mismatch"),
                List.of("organization-fingerprint", "tenant-ownership-evidence-digest", "redacted-final-audit-outcome"),
                "never-retry-cross-tenant-or-unknown-resource"
            ),
            failureClass(
                "conflict-or-duplicate-state",
                "NON_RETRYABLE_HTTP",
                false,
                List.of(409),
                List.of(),
                List.of("post-write-readback-contract", "operator-review-required"),
                "never-auto-retry-conflict-without-dedicated-release-review"
            ),
            failureClass(
                "unknown-acceptance-without-readback",
                "UNKNOWN_ACCEPTANCE",
                false,
                List.of(),
                List.of("response timeout after request bytes written"),
                List.of("post-write-readback-contract", "operator-review-required", "compensation-policy"),
                "never-claim-success-or-retry-without-readback"
            )
        );
    }

    public static List<KubeManagerWriteCompensationPolicy> compensationPolicies() {
        return List.of(
            compensationPolicy(
                "create-unknown-acceptance-review",
                "CREATE",
                "UNKNOWN_ACCEPTANCE",
                List.of(
                    "run readback using the same organization fingerprint",
                    "if duplicate resource exists, mark outcome as needs operator reconciliation",
                    "do not issue a second create without release review"
                )
            ),
            compensationPolicy(
                "update-partial-state-review",
                "UPDATE",
                "PARTIAL_OR_UNKNOWN_STATE",
                List.of(
                    "read current resource state",
                    "compare canonical desired-state digest with observed-state digest",
                    "surface drift and rollback guidance to operator"
                )
            ),
            compensationPolicy(
                "delete-unknown-state-review",
                "DELETE",
                "UNKNOWN_DELETE_STATE",
                List.of(
                    "read target resource by stable identifier",
                    "treat expected not-found as delete confirmation only when ownership evidence matches",
                    "surface unexpected not-found as operator review"
                )
            ),
            compensationPolicy(
                "action-unknown-effect-review",
                "ACTION",
                "UNKNOWN_ACTION_EFFECT",
                List.of(
                    "read action-specific status endpoint when available",
                    "correlate audit receipt id with observed state",
                    "block repeated action until operator release review"
                )
            )
        );
    }

    public static long runtimeRetryableFailureClassCount() {
        return failureClasses().stream()
            .filter(KubeManagerWriteRetryFailureClass::runtimeRetryableNow)
            .count();
    }

    public static long automaticCompensationPolicyCount() {
        return compensationPolicies().stream()
            .filter(KubeManagerWriteCompensationPolicy::automaticCompensationAllowed)
            .count();
    }

    private static KubeManagerWriteRetryFailureClass failureClass(String failureClassId,
                                                                 String category,
                                                                 boolean futureRetryCandidate,
                                                                 List<Integer> httpStatuses,
                                                                 List<String> exceptionSignals,
                                                                 List<String> requiredEvidence,
                                                                 String decisionRule) {
        return new KubeManagerWriteRetryFailureClass(
            failureClassId,
            category,
            futureRetryCandidate,
            false,
            httpStatuses,
            exceptionSignals,
            requiredEvidence,
            decisionRule,
            "admin-only review required before any runtime retry binding"
        );
    }

    private static KubeManagerWriteCompensationPolicy compensationPolicy(String policyId,
                                                                        String operationType,
                                                                        String failureScope,
                                                                        List<String> guidance) {
        return new KubeManagerWriteCompensationPolicy(
            policyId,
            operationType,
            failureScope,
            false,
            true,
            false,
            false,
            List.of(
                "durable-prewrite-receipt",
                "server-derived-idempotency-key",
                "principal-fingerprint",
                "organization-fingerprint",
                "post-write-readback-result",
                "redacted-final-audit-outcome"
            ),
            guidance
        );
    }
}
