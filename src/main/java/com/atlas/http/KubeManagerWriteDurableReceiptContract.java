package com.atlas.http;

import java.util.List;

/**
 * Review-only durable receipt contract for future generic kube-manager writes.
 */
public record KubeManagerWriteDurableReceiptContract(
    String contractId,
    boolean contractExists,
    boolean boundToHttpOutlet,
    boolean issuerExists,
    boolean issuedByReadinessEndpoint,
    boolean durableStorageMutationAllowed,
    String receiptPhase,
    String digestAlgorithm,
    List<String> requiredFields,
    List<String> rejectedCallerFields
) {
}
