package com.atlas.http;

import java.util.List;

/**
 * Review-only failure class for future kube-manager write retry decisions.
 */
public record KubeManagerWriteRetryFailureClass(
    String failureClassId,
    String category,
    boolean futureRetryCandidate,
    boolean runtimeRetryableNow,
    List<Integer> httpStatuses,
    List<String> exceptionSignals,
    List<String> requiredEvidence,
    String decisionRule,
    String operatorGuidance
) {
}
