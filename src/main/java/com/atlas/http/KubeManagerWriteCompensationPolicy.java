package com.atlas.http;

import java.util.List;

/**
 * Review-only compensation policy for future kube-manager write failures.
 */
public record KubeManagerWriteCompensationPolicy(
    String policyId,
    String operationType,
    String failureScope,
    boolean automaticCompensationAllowed,
    boolean operatorReviewRequired,
    boolean runtimeBound,
    boolean canOpenReleaseSwitch,
    List<String> requiredEvidence,
    List<String> guidance
) {
}
