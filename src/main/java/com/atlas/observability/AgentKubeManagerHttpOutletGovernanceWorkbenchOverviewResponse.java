package com.atlas.observability;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only Vue read model for kube-manager HTTP outlet governance.
 *
 * <p>The overview intentionally embeds only existing local read models. It is a
 * navigation and learning surface, not a release switch or write executor.</p>
 */
public record AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse(
    String schemaVersion,
    Instant generatedAt,
    String workbenchStatus,
    String frontendTarget,
    String httpOutletStatus,
    String writeReadinessVerdict,
    boolean releaseGateOpen,
    boolean writeRetryEnabled,
    boolean automaticWriteRetryAllowed,
    int governanceCardCount,
    int blockingCardCount,
    int boundRuntimeContractCount,
    long runtimeReleaseGateOpenCount,
    long runtimeRetryableFailureClassCount,
    long automaticCompensationPolicyCount,
    List<Map<String, Object>> governanceCards,
    List<String> recommendedWorkflow,
    List<String> nextActions,
    AgentKubeManagerHttpOutletHealthSummaryResponse healthSummary,
    AgentKubeManagerWriteRetryReadinessResponse writeRetryReadiness,
    AgentKubeManagerWriteIdempotencyContractResponse idempotencyContract,
    AgentKubeManagerWriteOperationSafetyContractResponse operationSafetyContract,
    AgentKubeManagerWriteRetryGovernanceContractResponse retryGovernanceContract,
    AgentKubeManagerWriteReleaseGateContractResponse releaseGateContract,
    Map<String, Object> workbenchPolicy,
    Map<String, Object> privacy
) {

    public static final String SCHEMA_VERSION =
        "agent-kube-manager-http-outlet-governance-workbench-overview.v1";

    public static AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse of(
        Instant generatedAt,
        AgentKubeManagerHttpOutletHealthSummaryResponse healthSummary,
        AgentKubeManagerWriteRetryReadinessResponse writeRetryReadiness,
        AgentKubeManagerWriteIdempotencyContractResponse idempotencyContract,
        AgentKubeManagerWriteOperationSafetyContractResponse operationSafetyContract,
        AgentKubeManagerWriteRetryGovernanceContractResponse retryGovernanceContract,
        AgentKubeManagerWriteReleaseGateContractResponse releaseGateContract
    ) {
        List<Map<String, Object>> cards = governanceCards(
            healthSummary,
            writeRetryReadiness,
            idempotencyContract,
            operationSafetyContract,
            retryGovernanceContract,
            releaseGateContract
        );
        int blockingCardCount = (int) cards.stream()
            .filter(card -> "BLOCKING".equals(card.get("severity")))
            .count();
        int boundRuntimeContractCount = boundRuntimeContractCount(
            idempotencyContract,
            operationSafetyContract,
            retryGovernanceContract,
            releaseGateContract
        );
        boolean writeRetryEnabled = bool(writeRetryReadiness != null ? writeRetryReadiness.writeRetryEnabled() : false)
            || bool(idempotencyContract != null ? idempotencyContract.writeRetryEnabled() : false)
            || bool(operationSafetyContract != null ? operationSafetyContract.writeRetryEnabled() : false)
            || bool(retryGovernanceContract != null ? retryGovernanceContract.writeRetryEnabled() : false)
            || bool(releaseGateContract != null ? releaseGateContract.writeRetryEnabled() : false);
        boolean automaticWriteRetryAllowed = writeRetryReadiness != null
            && writeRetryReadiness.automaticWriteRetryAllowed();
        boolean releaseGateOpen = releaseGateContract != null && releaseGateContract.releaseGateOpen();
        return new AgentKubeManagerHttpOutletGovernanceWorkbenchOverviewResponse(
            SCHEMA_VERSION,
            generatedAt,
            workbenchStatus(writeRetryReadiness, releaseGateContract, blockingCardCount),
            "vue-kube-manager kube-manager HTTP outlet governance workbench",
            healthSummary != null ? healthSummary.status() : "UNKNOWN",
            writeRetryReadiness != null ? writeRetryReadiness.readinessVerdict() : "UNKNOWN",
            releaseGateOpen,
            writeRetryEnabled,
            automaticWriteRetryAllowed,
            cards.size(),
            blockingCardCount,
            boundRuntimeContractCount,
            releaseGateContract != null ? releaseGateContract.runtimeReleaseGateOpenCount() : 0,
            retryGovernanceContract != null ? retryGovernanceContract.runtimeRetryableFailureClassCount() : 0,
            retryGovernanceContract != null ? retryGovernanceContract.automaticCompensationPolicyCount() : 0,
            cards,
            buildRecommendedWorkflow(),
            nextActions(blockingCardCount, boundRuntimeContractCount),
            healthSummary,
            writeRetryReadiness,
            idempotencyContract,
            operationSafetyContract,
            retryGovernanceContract,
            releaseGateContract,
            workbenchPolicy(cards, blockingCardCount, boundRuntimeContractCount, releaseGateOpen, writeRetryEnabled),
            privacyProof(
                healthSummary,
                writeRetryReadiness,
                idempotencyContract,
                operationSafetyContract,
                retryGovernanceContract,
                releaseGateContract
            )
        );
    }

    private static String workbenchStatus(AgentKubeManagerWriteRetryReadinessResponse readiness,
                                          AgentKubeManagerWriteReleaseGateContractResponse releaseGate,
                                          int blockingCardCount) {
        if (readiness == null || releaseGate == null) {
            return "INCOMPLETE_GOVERNANCE_EVIDENCE";
        }
        if (releaseGate.releaseGateOpen() || readiness.writeRetryEnabled() || readiness.automaticWriteRetryAllowed()) {
            return "UNEXPECTED_RUNTIME_WRITE_AUTHORITY";
        }
        if (blockingCardCount > 0 || !readiness.readyForControlledWriteRetry()) {
            return "WRITE_GOVERNANCE_NOT_READY";
        }
        return "READY_FOR_RELEASE_REVIEW";
    }

    private static List<Map<String, Object>> governanceCards(
        AgentKubeManagerHttpOutletHealthSummaryResponse healthSummary,
        AgentKubeManagerWriteRetryReadinessResponse writeRetryReadiness,
        AgentKubeManagerWriteIdempotencyContractResponse idempotencyContract,
        AgentKubeManagerWriteOperationSafetyContractResponse operationSafetyContract,
        AgentKubeManagerWriteRetryGovernanceContractResponse retryGovernanceContract,
        AgentKubeManagerWriteReleaseGateContractResponse releaseGateContract
    ) {
        List<Map<String, Object>> cards = new ArrayList<>();
        cards.add(card(
            "http-outlet-health",
            "Kube-manager HTTP outlet local health",
            healthSummary != null ? healthSummary.schemaVersion() : "missing",
            healthSummary != null ? healthSummary.status() : "UNKNOWN",
            healthSummary != null && "READY".equals(healthSummary.status()) ? "INFO" : "BLOCKING",
            "/api/agent/observability/kube-manager/http-outlet/health-summary",
            healthSummary != null ? healthSummary.statusReasons() : List.of("health-summary-missing"),
            Map.of(
                "readRetryEnabled", truthy(healthSummary != null ? healthSummary.readPolicy() : Map.of(), "automaticRetryEnabled"),
                "writeRetryEnabled", truthy(healthSummary != null ? healthSummary.writePolicy() : Map.of(), "automaticRetryEnabled")
            )
        ));
        cards.add(card(
            "write-retry-readiness",
            "Write retry release readiness",
            writeRetryReadiness != null ? writeRetryReadiness.schemaVersion() : "missing",
            writeRetryReadiness != null ? writeRetryReadiness.readinessVerdict() : "UNKNOWN",
            writeRetryReadiness != null && writeRetryReadiness.readyForControlledWriteRetry() ? "INFO" : "BLOCKING",
            "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness",
            writeRetryReadiness != null ? writeRetryReadiness.blockedReasons() : List.of("write-retry-readiness-missing"),
            Map.of(
                "writeRetryEnabled", writeRetryReadiness != null && writeRetryReadiness.writeRetryEnabled(),
                "automaticWriteRetryAllowed", writeRetryReadiness != null && writeRetryReadiness.automaticWriteRetryAllowed()
            )
        ));
        cards.add(card(
            "write-idempotency-contract",
            "Server-derived idempotency contract",
            idempotencyContract != null ? idempotencyContract.schemaVersion() : "missing",
            idempotencyContract != null ? idempotencyContract.contractStatus() : "UNKNOWN",
            idempotencyContract != null && idempotencyContract.boundToHttpOutlet() ? "INFO" : "BLOCKING",
            "/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract",
            idempotencyContract != null
                ? List.of("idempotency-contract-defined-not-bound-to-http-outlet")
                : List.of("idempotency-contract-missing"),
            Map.of(
                "contractExists", idempotencyContract != null && idempotencyContract.serverDerivedKeyContractExists(),
                "boundToHttpOutlet", idempotencyContract != null && idempotencyContract.boundToHttpOutlet()
            )
        ));
        cards.add(card(
            "write-operation-safety-contract",
            "Operation allowlist, RBAC, and readback contract",
            operationSafetyContract != null ? operationSafetyContract.schemaVersion() : "missing",
            operationSafetyContract != null ? operationSafetyContract.contractStatus() : "UNKNOWN",
            operationSafetyContract != null && operationSafetyContract.boundToHttpOutlet() ? "INFO" : "BLOCKING",
            "/api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract",
            operationSafetyContract != null
                ? List.of("operation-safety-contract-defined-not-bound-to-http-outlet")
                : List.of("operation-safety-contract-missing"),
            Map.of(
                "operationAllowlistContractExists",
                operationSafetyContract != null && operationSafetyContract.operationAllowlistContractExists(),
                "postWriteReadbackContractExists",
                operationSafetyContract != null && operationSafetyContract.postWriteReadbackContractExists(),
                "boundToHttpOutlet",
                operationSafetyContract != null && operationSafetyContract.boundToHttpOutlet()
            )
        ));
        cards.add(card(
            "write-retry-governance-contract",
            "Retry predicate and compensation governance",
            retryGovernanceContract != null ? retryGovernanceContract.schemaVersion() : "missing",
            retryGovernanceContract != null ? retryGovernanceContract.contractStatus() : "UNKNOWN",
            retryGovernanceContract != null && retryGovernanceContract.boundToHttpOutlet() ? "INFO" : "BLOCKING",
            "/api/agent/observability/kube-manager/http-outlet/write-retry-governance-contract",
            retryGovernanceContract != null
                ? List.of("retry-governance-contract-defined-not-bound-to-http-outlet")
                : List.of("retry-governance-contract-missing"),
            Map.of(
                "retryPredicateContractExists",
                retryGovernanceContract != null && retryGovernanceContract.retryPredicateContractExists(),
                "runtimeRetryableFailureClassCount",
                retryGovernanceContract != null ? retryGovernanceContract.runtimeRetryableFailureClassCount() : 0,
                "automaticCompensationPolicyCount",
                retryGovernanceContract != null ? retryGovernanceContract.automaticCompensationPolicyCount() : 0,
                "boundToHttpOutlet",
                retryGovernanceContract != null && retryGovernanceContract.boundToHttpOutlet()
            )
        ));
        cards.add(card(
            "write-release-gate-contract",
            "Durable receipt and HITL/release gate contract",
            releaseGateContract != null ? releaseGateContract.schemaVersion() : "missing",
            releaseGateContract != null ? releaseGateContract.contractStatus() : "UNKNOWN",
            releaseGateContract != null && releaseGateContract.releaseGateOpen() ? "INFO" : "BLOCKING",
            "/api/agent/observability/kube-manager/http-outlet/write-release-gate-contract",
            releaseGateContract != null
                ? List.of("release-gate-contract-defined-not-bound-to-http-outlet")
                : List.of("release-gate-contract-missing"),
            Map.of(
                "durableReceiptContractExists",
                releaseGateContract != null && releaseGateContract.durableReceiptContractExists(),
                "releaseEvidenceContractExists",
                releaseGateContract != null && releaseGateContract.releaseEvidenceContractExists(),
                "releaseGateOpen",
                releaseGateContract != null && releaseGateContract.releaseGateOpen(),
                "runtimeReleaseGateOpenCount",
                releaseGateContract != null ? releaseGateContract.runtimeReleaseGateOpenCount() : 0
            )
        ));
        return List.copyOf(cards);
    }

    private static Map<String, Object> card(String id,
                                            String title,
                                            String schemaVersion,
                                            String status,
                                            String severity,
                                            String endpoint,
                                            List<String> reasons,
                                            Map<String, Object> evidence) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", id);
        card.put("title", title);
        card.put("schemaVersion", schemaVersion);
        card.put("status", status);
        card.put("severity", severity);
        card.put("endpoint", endpoint);
        card.put("reasonCount", reasons != null ? reasons.size() : 0);
        card.put("reasons", reasons != null ? List.copyOf(reasons) : List.of());
        card.put("evidence", evidence != null ? Map.copyOf(evidence) : Map.of());
        card.put("frontendNavigationOnly", true);
        card.put("readOnly", true);
        card.put("runtimeMutationAllowed", false);
        card.put("kubeManagerCalls", false);
        card.put("toolExecution", false);
        card.put("llmUsed", false);
        return Map.copyOf(card);
    }

    private static int boundRuntimeContractCount(AgentKubeManagerWriteIdempotencyContractResponse idempotency,
                                                AgentKubeManagerWriteOperationSafetyContractResponse safety,
                                                AgentKubeManagerWriteRetryGovernanceContractResponse governance,
                                                AgentKubeManagerWriteReleaseGateContractResponse releaseGate) {
        int count = 0;
        if (idempotency != null && idempotency.boundToHttpOutlet()) {
            count++;
        }
        if (safety != null && safety.boundToHttpOutlet()) {
            count++;
        }
        if (governance != null && governance.boundToHttpOutlet()) {
            count++;
        }
        if (releaseGate != null && releaseGate.boundToHttpOutlet()) {
            count++;
        }
        return count;
    }

    private static List<String> buildRecommendedWorkflow() {
        return List.of(
            "governance-workbench-overview",
            "http-outlet-health-summary",
            "write-retry-readiness",
            "write-idempotency-contract",
            "write-operation-safety-contract",
            "write-retry-governance-contract",
            "write-release-gate-contract",
            "eval-workbench-gate-bundle-summary",
            "human-release-review-before-runtime-binding"
        );
    }

    private static List<String> nextActions(int blockingCardCount, int boundRuntimeContractCount) {
        List<String> actions = new ArrayList<>();
        if (blockingCardCount > 0) {
            actions.add("render-vue-governance-cards-with-blocking-reasons");
            actions.add("keep-kube-manager-write-retry-disabled");
        }
        if (boundRuntimeContractCount == 0) {
            actions.add("design-runtime-binding-only-after-durable-receipt-and-release-review");
        }
        actions.add("curate-real-redacted-eval-traces-before-any-release-gate");
        actions.add("keep-nim-hpc-slurm-bcm-paused-for-phase2");
        return List.copyOf(actions);
    }

    private static Map<String, Object> workbenchPolicy(List<Map<String, Object>> cards,
                                                       int blockingCardCount,
                                                       int boundRuntimeContractCount,
                                                       boolean releaseGateOpen,
                                                       boolean writeRetryEnabled) {
        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("schemaVersion", SCHEMA_VERSION);
        policy.put("adminOnly", true);
        policy.put("frontendTarget", "vue-kube-manager kube-manager HTTP outlet governance workbench");
        policy.put("overviewOnly", true);
        policy.put("readOnly", true);
        policy.put("localProcessOnly", true);
        policy.put("sourceReadModelsEmbedded", true);
        policy.put("governanceCardCount", cards.size());
        policy.put("blockingCardCount", blockingCardCount);
        policy.put("boundRuntimeContractCount", boundRuntimeContractCount);
        policy.put("runtimeWriteBindingAllowed", false);
        policy.put("runtimeReleaseGateSwitchPresent", false);
        policy.put("releaseGateOpen", releaseGateOpen);
        policy.put("writeRetryEnabled", writeRetryEnabled);
        policy.put("writeRetryEnablementAllowed", false);
        policy.put("kubeManagerCalls", false);
        policy.put("remoteProbeExecuted", false);
        policy.put("restClientUsed", false);
        policy.put("toolExecution", false);
        policy.put("llmUsed", false);
        policy.put("externalCalls", false);
        policy.put("auditWrite", false);
        policy.put("durableReceiptIssued", false);
        policy.put("durableStorageMutation", false);
        policy.put("resiliencePolicyMutation", false);
        policy.put("hitlInvocation", false);
        policy.put("phase2NimHpcSlurmBcmTouched", false);
        return Map.copyOf(policy);
    }

    private static Map<String, Object> privacyProof(AgentKubeManagerHttpOutletHealthSummaryResponse health,
                                                    AgentKubeManagerWriteRetryReadinessResponse readiness,
                                                    AgentKubeManagerWriteIdempotencyContractResponse idempotency,
                                                    AgentKubeManagerWriteOperationSafetyContractResponse safety,
                                                    AgentKubeManagerWriteRetryGovernanceContractResponse governance,
                                                    AgentKubeManagerWriteReleaseGateContractResponse releaseGate) {
        List<Map<String, Object>> privacyMaps = List.of(
            health != null ? health.privacy() : Map.of(),
            readiness != null ? readiness.privacy() : Map.of(),
            idempotency != null ? idempotency.privacy() : Map.of(),
            safety != null ? safety.privacy() : Map.of(),
            governance != null ? governance.privacy() : Map.of(),
            releaseGate != null ? releaseGate.privacy() : Map.of()
        );
        boolean containsRawBaseUrl = anyTruthy(privacyMaps, "containsRawBaseUrl");
        boolean containsRawBackendPath = anyTruthy(privacyMaps, "containsRawBackendPath");
        boolean containsRawEndpoint = anyTruthy(privacyMaps, "containsRawEndpoint");
        boolean containsAuthorizationHeader = anyTruthy(privacyMaps, "containsAuthorizationHeader");
        boolean containsToken = anyTruthy(privacyMaps, "containsToken");
        boolean containsLoginPassword = anyTruthy(privacyMaps, "containsLoginPassword");
        boolean containsRawPrincipal = anyTruthy(privacyMaps, "containsRawPrincipal");
        boolean containsRawOrganization = anyTruthy(privacyMaps, "containsRawOrganization");
        boolean containsRawRequestBody = anyTruthy(privacyMaps, "containsRawRequestBody");
        boolean containsRawResponseBody = anyTruthy(privacyMaps, "containsRawResponseBody");
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("redactedOnly", !containsRawBaseUrl
            && !containsRawBackendPath
            && !containsRawEndpoint
            && !containsAuthorizationHeader
            && !containsToken
            && !containsLoginPassword
            && !containsRawPrincipal
            && !containsRawOrganization
            && !containsRawRequestBody
            && !containsRawResponseBody);
        proof.put("containsRawBaseUrl", containsRawBaseUrl);
        proof.put("containsRawBackendPath", containsRawBackendPath);
        proof.put("containsRawEndpoint", containsRawEndpoint);
        proof.put("containsAuthorizationHeader", containsAuthorizationHeader);
        proof.put("containsToken", containsToken);
        proof.put("containsLoginPassword", containsLoginPassword);
        proof.put("containsRawPrincipal", containsRawPrincipal);
        proof.put("containsRawOrganization", containsRawOrganization);
        proof.put("containsRawRequestBody", containsRawRequestBody);
        proof.put("containsRawResponseBody", containsRawResponseBody);
        proof.put("llmUsed", false);
        proof.put("externalCalls", false);
        proof.put("toolExecution", false);
        proof.put("kubeManagerCalls", false);
        return Map.copyOf(proof);
    }

    private static boolean anyTruthy(List<Map<String, Object>> maps, String key) {
        return maps.stream().anyMatch(map -> truthy(map, key));
    }

    private static boolean truthy(Map<String, Object> map, String key) {
        return map != null && Boolean.TRUE.equals(map.get(key));
    }

    private static boolean bool(boolean value) {
        return value;
    }
}
