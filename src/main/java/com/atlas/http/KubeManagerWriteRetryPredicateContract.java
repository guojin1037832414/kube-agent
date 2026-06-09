package com.atlas.http;

import java.util.List;

/**
 * Bounded retry predicate contract for future kube-manager writes.
 */
public record KubeManagerWriteRetryPredicateContract(
    String contractId,
    boolean contractExists,
    boolean boundToHttpOutlet,
    boolean runtimePredicateExists,
    boolean callerOverrideAccepted,
    int maxAttempts,
    String backoffStrategy,
    boolean jitterRequired,
    boolean sameIdempotencyKeyRequired,
    boolean durablePrewriteReceiptRequired,
    boolean operationAllowlistRequired,
    boolean rbacEvidenceRequired,
    boolean postWriteReadbackRequiredBeforeSuccess,
    List<String> futureCandidateFailureClassIds,
    List<String> neverRetryFailureClassIds
) {
}
