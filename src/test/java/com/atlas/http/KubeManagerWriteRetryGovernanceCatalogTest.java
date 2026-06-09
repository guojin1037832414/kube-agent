package com.atlas.http;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-owned retry governance catalog tests.
 */
class KubeManagerWriteRetryGovernanceCatalogTest {

    private static final Path CATALOG_SOURCE = Path.of(
        "src/main/java/com/atlas/http/KubeManagerWriteRetryGovernanceCatalog.java"
    );

    @Test
    void catalog_shouldDefineReviewOnlyRetryPredicateAndCompensationPolicies() {
        KubeManagerWriteRetryPredicateContract predicate =
            KubeManagerWriteRetryGovernanceCatalog.predicateContract();

        assertThat(KubeManagerWriteRetryGovernanceCatalog.SCHEMA_VERSION)
            .isEqualTo("kube-manager-write-retry-governance-catalog.v1");
        assertThat(predicate.contractId()).isEqualTo("generic-write-retry-predicate.v1");
        assertThat(predicate.contractExists()).isTrue();
        assertThat(predicate.boundToHttpOutlet()).isFalse();
        assertThat(predicate.runtimePredicateExists()).isFalse();
        assertThat(predicate.callerOverrideAccepted()).isFalse();
        assertThat(predicate.maxAttempts()).isEqualTo(2);
        assertThat(predicate.backoffStrategy()).isEqualTo("bounded-jittered-exponential");
        assertThat(predicate.jitterRequired()).isTrue();
        assertThat(predicate.sameIdempotencyKeyRequired()).isTrue();
        assertThat(predicate.postWriteReadbackRequiredBeforeSuccess()).isTrue();
        assertThat(predicate.futureCandidateFailureClassIds())
            .containsExactly(
                "transport-timeout-before-acceptance",
                "transient-gateway-after-idempotent-request",
                "rate-limited-idempotent-request"
            );
        assertThat(predicate.neverRetryFailureClassIds())
            .contains("caller-validation-error", "authentication-or-authorization-denied", "unknown-acceptance-without-readback");

        assertThat(KubeManagerWriteRetryGovernanceCatalog.failureClasses())
            .extracting(KubeManagerWriteRetryFailureClass::failureClassId)
            .containsExactly(
                "transport-timeout-before-acceptance",
                "transient-gateway-after-idempotent-request",
                "rate-limited-idempotent-request",
                "caller-validation-error",
                "authentication-or-authorization-denied",
                "tenant-or-ownership-mismatch",
                "conflict-or-duplicate-state",
                "unknown-acceptance-without-readback"
            );
        assertThat(KubeManagerWriteRetryGovernanceCatalog.failureClasses())
            .allSatisfy(failureClass -> assertThat(failureClass.runtimeRetryableNow()).isFalse());
        assertThat(KubeManagerWriteRetryGovernanceCatalog.runtimeRetryableFailureClassCount()).isZero();

        assertThat(KubeManagerWriteRetryGovernanceCatalog.compensationPolicies())
            .extracting(KubeManagerWriteCompensationPolicy::policyId)
            .containsExactly(
                "create-unknown-acceptance-review",
                "update-partial-state-review",
                "delete-unknown-state-review",
                "action-unknown-effect-review"
            );
        assertThat(KubeManagerWriteRetryGovernanceCatalog.compensationPolicies())
            .allSatisfy(policy -> {
                assertThat(policy.automaticCompensationAllowed()).isFalse();
                assertThat(policy.operatorReviewRequired()).isTrue();
                assertThat(policy.runtimeBound()).isFalse();
                assertThat(policy.canOpenReleaseSwitch()).isFalse();
            });
        assertThat(KubeManagerWriteRetryGovernanceCatalog.automaticCompensationPolicyCount()).isZero();
    }

    @Test
    void source_shouldNotBindRuntimeRetryOrWriteExecution() throws Exception {
        String source = Files.readString(CATALOG_SOURCE);

        assertThat(source)
            .doesNotContain("Retry.decorate")
            .doesNotContain("RetryRegistry")
            .doesNotContain("CircuitBreaker")
            .doesNotContain("Bulkhead")
            .doesNotContain("RestClient")
            .doesNotContain("executeRead(")
            .doesNotContain("executeWrite(")
            .doesNotContain("prewriteHighRisk")
            .doesNotContain("ToolRegistry.")
            .doesNotContain("com.atlas.tool.impl");
    }
}
