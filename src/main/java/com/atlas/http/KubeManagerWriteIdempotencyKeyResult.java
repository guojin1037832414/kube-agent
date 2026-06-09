package com.atlas.http;

/**
 * Server-derived idempotency key result for future controlled kube-manager writes.
 */
public record KubeManagerWriteIdempotencyKeyResult(
    String schemaVersion,
    String key,
    String keySource,
    String algorithm,
    String inputDigest,
    boolean callerProvidedKeyAccepted,
    boolean retryAllowed,
    boolean retryAllowedOnlyWithSameEvidence
) {
}
