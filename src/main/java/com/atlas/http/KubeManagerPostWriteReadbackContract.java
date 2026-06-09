package com.atlas.http;

import java.util.List;

/**
 * GET-only post-write readback contract for future kube-manager writes.
 */
public record KubeManagerPostWriteReadbackContract(
    String contractId,
    String readMethod,
    String readEndpointTemplate,
    boolean samePrincipalRequired,
    boolean sameOrganizationFingerprintRequired,
    boolean requestSpecDigestRequired,
    boolean idempotencyDigestRequired,
    boolean successClaimRequiresReadback,
    boolean acceptsCallerSuccessClaim,
    boolean executorExists,
    boolean executedByReadinessEndpoint,
    boolean canOpenReleaseSwitch,
    List<String> statusFieldNames,
    List<String> expectedSuccessTerminalStates,
    List<String> expectedFailureTerminalStates
) {
}
