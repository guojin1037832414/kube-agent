package com.atlas.http;

import java.util.List;
import java.util.Locale;

/**
 * Trusted evidence used to derive a future kube-manager write idempotency key.
 *
 * <p>This input intentionally has no caller-provided idempotency-key field.
 * A top-tier Agent must derive write idempotency from server-side evidence
 * instead of trusting request parameters that a prompt, browser, or user can
 * forge.</p>
 */
public record KubeManagerWriteIdempotencyKeyInput(
    String auditReceiptId,
    String auditReceiptDigest,
    String requestSpecDigest,
    String principalFingerprint,
    String organizationFingerprint,
    String operationType,
    String httpMethod,
    String pathTemplate,
    String requestBodyDigest,
    String releaseEvidenceDigest
) {

    private static final List<String> WRITE_METHODS = List.of("POST", "PATCH", "PUT", "DELETE");

    public KubeManagerWriteIdempotencyKeyInput {
        auditReceiptId = required("auditReceiptId", auditReceiptId);
        auditReceiptDigest = required("auditReceiptDigest", auditReceiptDigest);
        requestSpecDigest = required("requestSpecDigest", requestSpecDigest);
        principalFingerprint = required("principalFingerprint", principalFingerprint);
        organizationFingerprint = required("organizationFingerprint", organizationFingerprint);
        operationType = required("operationType", operationType).toUpperCase(Locale.ROOT);
        httpMethod = required("httpMethod", httpMethod).toUpperCase(Locale.ROOT);
        pathTemplate = required("pathTemplate", pathTemplate);
        requestBodyDigest = required("requestBodyDigest", requestBodyDigest);
        releaseEvidenceDigest = required("releaseEvidenceDigest", releaseEvidenceDigest);
        if (!WRITE_METHODS.contains(httpMethod)) {
            throw new IllegalArgumentException("httpMethod must be one of " + WRITE_METHODS);
        }
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
