package com.atlas.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Pure Java contract for deriving kube-manager write idempotency keys.
 *
 * <p>M5.51 only defines and tests the contract. It does not bind this key to
 * {@link KubeManagerHttpClient}, does not inject HTTP headers, and does not
 * enable automatic write retries.</p>
 */
public class KubeManagerWriteIdempotencyKeyDeriver {

    public static final String SCHEMA_VERSION = "kube-manager-write-idempotency-key.v1";
    public static final String KEY_SOURCE = "server-derived-sha256-bound-evidence.v1";
    public static final String KEY_PREFIX = "km-write-v1-";
    public static final String ALGORITHM = "SHA-256";

    public KubeManagerWriteIdempotencyKeyResult derive(KubeManagerWriteIdempotencyKeyInput input) {
        String canonicalEvidence = canonicalEvidence(input);
        String digest = sha256Hex(canonicalEvidence);
        return new KubeManagerWriteIdempotencyKeyResult(
            SCHEMA_VERSION,
            KEY_PREFIX + digest,
            KEY_SOURCE,
            ALGORITHM,
            digest,
            false,
            false,
            true
        );
    }

    private String canonicalEvidence(KubeManagerWriteIdempotencyKeyInput input) {
        return String.join("\n",
            "schema=" + SCHEMA_VERSION,
            "auditReceiptId=" + input.auditReceiptId(),
            "auditReceiptDigest=" + input.auditReceiptDigest(),
            "requestSpecDigest=" + input.requestSpecDigest(),
            "principalFingerprint=" + input.principalFingerprint(),
            "organizationFingerprint=" + input.organizationFingerprint(),
            "operationType=" + input.operationType(),
            "httpMethod=" + input.httpMethod(),
            "pathTemplate=" + input.pathTemplate(),
            "requestBodyDigest=" + input.requestBodyDigest(),
            "releaseEvidenceDigest=" + input.releaseEvidenceDigest()
        );
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " is not available", e);
        }
    }
}
