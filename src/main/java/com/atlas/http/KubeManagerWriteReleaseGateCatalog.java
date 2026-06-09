package com.atlas.http;

import java.util.List;

/**
 * Source-owned release gate catalog for future generic kube-manager writes.
 *
 * <p>The catalog is a typed contract, not a writer. It must not be wired into
 * {@link KubeManagerHttpClient}, durable audit sinks, HITL controllers, or
 * runtime write/retry paths until a future reviewed release binds all required
 * evidence together.</p>
 */
public final class KubeManagerWriteReleaseGateCatalog {

    public static final String SCHEMA_VERSION = "kube-manager-write-release-gate-catalog.v1";
    public static final String DURABLE_RECEIPT_CONTRACT_ID = "generic-write-durable-prewrite-receipt.v1";
    public static final String RELEASE_EVIDENCE_CONTRACT_ID = "generic-write-hitl-release-evidence.v1";

    private KubeManagerWriteReleaseGateCatalog() {
    }

    public static KubeManagerWriteDurableReceiptContract durableReceiptContract() {
        return new KubeManagerWriteDurableReceiptContract(
            DURABLE_RECEIPT_CONTRACT_ID,
            true,
            false,
            false,
            false,
            false,
            "PRE_EXECUTION",
            "SHA-256",
            List.of(
                "receiptId",
                "auditEventDigest",
                "requestSpecDigest",
                "principalFingerprint",
                "organizationFingerprint",
                "operationType",
                "httpMethod",
                "pathTemplate",
                "requestBodyDigest",
                "idempotencyKeyDigest",
                "hitlConfirmationDigest",
                "releaseEvidenceDigest",
                "createdAt"
            ),
            List.of(
                "callerReceiptId",
                "callerAuditDigest",
                "callerIdempotencyKey",
                "callerReleaseApproved",
                "callerDurableReceipt"
            )
        );
    }

    public static KubeManagerWriteReleaseEvidenceContract releaseEvidenceContract() {
        return new KubeManagerWriteReleaseEvidenceContract(
            RELEASE_EVIDENCE_CONTRACT_ID,
            true,
            false,
            true,
            true,
            false,
            false,
            List.of(
                "serverHitlConfirmationDigest",
                "releaseReviewerFingerprint",
                "releaseDecisionDigest",
                "evalGateBundleDigest",
                "operationSafetyContractDigest",
                "retryGovernanceContractDigest",
                "operatorIntentDigest",
                "tenantOwnershipEvidenceDigest"
            ),
            List.of(
                "LLM-generated approval text",
                "caller request flag",
                "frontend checkbox alone",
                "durable executor success claim",
                "legacy migration report alone",
                "post-write success response"
            ),
            List.of(
                "release-evidence-contract-not-bound-to-http-outlet",
                "durable-receipt-issuer-missing",
                "server-hitl-confirmation-not-bound",
                "eval-gate-bundle-not-bound",
                "runtime-release-switch-intentionally-absent"
            )
        );
    }

    public static long runtimeReleaseGateOpenCount() {
        return 0L;
    }
}
