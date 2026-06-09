package com.atlas.http;

import java.util.List;

/**
 * Review-only HITL/release evidence contract for future generic writes.
 */
public record KubeManagerWriteReleaseEvidenceContract(
    String contractId,
    boolean contractExists,
    boolean boundToHttpOutlet,
    boolean hitlEvidenceRequired,
    boolean releaseReviewRequired,
    boolean callerProvidedReleaseEvidenceAccepted,
    boolean canOpenReleaseSwitch,
    List<String> requiredEvidence,
    List<String> rejectedEvidenceSources,
    List<String> releaseBlockers
) {
}
