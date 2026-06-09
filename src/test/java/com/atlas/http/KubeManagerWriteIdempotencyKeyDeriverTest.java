package com.atlas.http;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Generic kube-manager write idempotency-key derivation contract tests.
 */
class KubeManagerWriteIdempotencyKeyDeriverTest {

    private final KubeManagerWriteIdempotencyKeyDeriver deriver = new KubeManagerWriteIdempotencyKeyDeriver();

    @Test
    void derive_shouldCreateDeterministicServerDerivedKeyFromBoundEvidence() {
        KubeManagerWriteIdempotencyKeyInput input = validInput();

        KubeManagerWriteIdempotencyKeyResult first = deriver.derive(input);
        KubeManagerWriteIdempotencyKeyResult second = deriver.derive(input);

        assertThat(first).isEqualTo(second);
        assertThat(first.schemaVersion()).isEqualTo("kube-manager-write-idempotency-key.v1");
        assertThat(first.keySource()).isEqualTo("server-derived-sha256-bound-evidence.v1");
        assertThat(first.algorithm()).isEqualTo("SHA-256");
        assertThat(first.key()).matches("km-write-v1-[a-f0-9]{64}");
        assertThat(first.inputDigest()).matches("[a-f0-9]{64}");
        assertThat(first.callerProvidedKeyAccepted()).isFalse();
        assertThat(first.retryAllowed()).isFalse();
        assertThat(first.retryAllowedOnlyWithSameEvidence()).isTrue();
        assertThat(first.toString())
            .doesNotContain(
                "raw-token",
                "Bearer",
                "password",
                "http://kube-manager.internal",
                "/api/100002"
            );
    }

    @Test
    void derive_shouldChangeWhenAnyBoundEvidenceChanges() {
        KubeManagerWriteIdempotencyKeyResult first = deriver.derive(validInput());
        KubeManagerWriteIdempotencyKeyResult changedBody = deriver.derive(new KubeManagerWriteIdempotencyKeyInput(
            "receipt-1",
            "audit-receipt-digest-1",
            "request-spec-digest-1",
            "principal-fingerprint-1",
            "organization-fingerprint-1",
            "CREATE",
            "POST",
            "/api/{organizationId}/deployment",
            "request-body-digest-2",
            "release-evidence-digest-1"
        ));

        assertThat(changedBody.key()).isNotEqualTo(first.key());
        assertThat(changedBody.inputDigest()).isNotEqualTo(first.inputDigest());
    }

    @Test
    void input_shouldRejectBlankEvidenceAndReadMethods() {
        assertThatThrownBy(() -> new KubeManagerWriteIdempotencyKeyInput(
            "",
            "audit-receipt-digest-1",
            "request-spec-digest-1",
            "principal-fingerprint-1",
            "organization-fingerprint-1",
            "CREATE",
            "POST",
            "/api/{organizationId}/deployment",
            "request-body-digest-1",
            "release-evidence-digest-1"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("auditReceiptId is required");

        assertThatThrownBy(() -> new KubeManagerWriteIdempotencyKeyInput(
            "receipt-1",
            "audit-receipt-digest-1",
            "request-spec-digest-1",
            "principal-fingerprint-1",
            "organization-fingerprint-1",
            "READ",
            "GET",
            "/api/{organizationId}/pod",
            "request-body-digest-1",
            "release-evidence-digest-1"
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("httpMethod must be one of");
    }

    @Test
    void input_shouldNormalizeMethodAndOperationWithoutAcceptingCallerKey() {
        KubeManagerWriteIdempotencyKeyInput input = new KubeManagerWriteIdempotencyKeyInput(
            " receipt-1 ",
            " audit-receipt-digest-1 ",
            " request-spec-digest-1 ",
            " principal-fingerprint-1 ",
            " organization-fingerprint-1 ",
            " create ",
            " post ",
            " /api/{organizationId}/deployment ",
            " request-body-digest-1 ",
            " release-evidence-digest-1 "
        );

        KubeManagerWriteIdempotencyKeyResult result = deriver.derive(input);

        assertThat(input.operationType()).isEqualTo("CREATE");
        assertThat(input.httpMethod()).isEqualTo("POST");
        assertThat(input.auditReceiptId()).isEqualTo("receipt-1");
        assertThat(result.callerProvidedKeyAccepted()).isFalse();
        assertThat(result.retryAllowed()).isFalse();
    }

    private KubeManagerWriteIdempotencyKeyInput validInput() {
        return new KubeManagerWriteIdempotencyKeyInput(
            "receipt-1",
            "audit-receipt-digest-1",
            "request-spec-digest-1",
            "principal-fingerprint-1",
            "organization-fingerprint-1",
            "CREATE",
            "POST",
            "/api/{organizationId}/deployment",
            "request-body-digest-1",
            "release-evidence-digest-1"
        );
    }
}
