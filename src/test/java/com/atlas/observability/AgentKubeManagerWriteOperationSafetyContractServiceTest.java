package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kube-manager write operation allowlist/RBAC/readback contract tests.
 */
class AgentKubeManagerWriteOperationSafetyContractServiceTest {

    private static final Path SERVICE_SOURCE = Path.of(
        "src/main/java/com/atlas/observability/AgentKubeManagerWriteOperationSafetyContractService.java"
    );

    @Test
    void contract_shouldDescribeOperationSafetyWithoutBindingRuntimeWrites() {
        AgentKubeManagerWriteOperationSafetyContractService service =
            new AgentKubeManagerWriteOperationSafetyContractService(
                Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
            );

        AgentKubeManagerWriteOperationSafetyContractResponse contract = service.contract();

        assertThat(contract.schemaVersion()).isEqualTo("agent-kube-manager-write-operation-safety-contract.v1");
        assertThat(contract.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(contract.contractStatus()).isEqualTo("CONTRACT_DEFINED_NOT_BOUND");
        assertThat(contract.operationAllowlistContractExists()).isTrue();
        assertThat(contract.postWriteReadbackContractExists()).isTrue();
        assertThat(contract.boundToHttpOutlet()).isFalse();
        assertThat(contract.writeRetryEnabled()).isFalse();
        assertThat(contract.allowedOperationClasses())
            .extracting(operation -> operation.get("id"))
            .containsExactly(
                "generic-tenant-create",
                "generic-tenant-update-patch",
                "generic-tenant-update-put",
                "generic-tenant-delete",
                "generic-tenant-action"
            );
        assertThat(contract.allowedOperationClasses())
            .allSatisfy(operation -> assertThat(operation)
                .containsEntry("tenantScoped", true)
                .containsEntry("highRisk", true)
                .containsEntry("requiresDurablePrewriteReceipt", true)
                .containsEntry("requiresServerDerivedIdempotencyKey", true)
                .containsEntry("requiresRbacEvidence", true)
                .containsEntry("requiresHumanReleaseEvidence", true)
                .containsEntry("requiresPostWriteReadback", true)
                .containsEntry("eligibleForRuntimeExecutionNow", false)
                .containsEntry("eligibleForAutomaticRetryNow", false)
                .containsEntry("boundToHttpOutlet", false)
                .containsEntry("phase2ExcludedDomain", false));
        assertThat(contract.requiredRbacEvidence())
            .extracting(evidence -> evidence.get("field"))
            .containsExactly(
                "principalFingerprint",
                "organizationFingerprint",
                "rolePermissionDigest",
                "tenantOwnershipEvidenceDigest",
                "toolRiskMetadataDigest",
                "hitlConfirmationDigest",
                "releaseEvidenceDigest",
                "requestSpecDigest"
            );
        assertThat(contract.requiredRbacEvidence())
            .allSatisfy(evidence -> assertThat(evidence)
                .containsEntry("required", true)
                .containsEntry("rawValueAllowed", false));
        assertThat(contract.readbackContract())
            .containsEntry("contractId", "generic-tenant-resource-readback.v1")
            .containsEntry("contractExists", true)
            .containsEntry("boundToHttpOutlet", false)
            .containsEntry("readEndpointTemplate", "/api/{organizationId}/{resourceType}/{resourceId}")
            .containsEntry("readMethod", "GET")
            .containsEntry("readbackIsWriteFree", true)
            .containsEntry("successClaimRequiresReadback", true)
            .containsEntry("acceptsCallerSuccessClaim", false)
            .containsEntry("executorExists", false)
            .containsEntry("executedByReadinessEndpoint", false)
            .containsEntry("canOpenReleaseSwitch", false);
        assertThat(contract.readbackContract().get("expectedSuccessTerminalStates"))
            .isEqualTo(java.util.List.of("READY", "ACTIVE", "SUCCEEDED", "DELETED_CONFIRMED"));
        assertThat(contract.retryEligibilityGates())
            .containsEntry("serverDerivedIdempotencyKeyRequired", true)
            .containsEntry("durablePrewriteReceiptRequired", true)
            .containsEntry("operationAllowlistRequired", true)
            .containsEntry("rbacEvidenceRequired", true)
            .containsEntry("postWriteReadbackRequired", true)
            .containsEntry("runtimeRetryEligibleWriteOperationCount", 0L)
            .containsEntry("runtimeBindingAllowedNow", false)
            .containsEntry("callerOverrideAllowed", false)
            .containsEntry("defaultIfAnyGateMissing", "fail-closed-no-write-retry");
        assertThat(contract.retryEligibilityGates().get("phase2ExcludedDomains"))
            .isEqualTo(java.util.List.of("NIM", "HPC", "Slurm", "BCM"));
        assertThat(contract.blockedRuntimeBindings())
            .containsEntry("kubeManagerHttpClientBinding", "blocked-until-release-review")
            .containsEntry("executeWriteBinding", "blocked-until-release-review")
            .containsEntry("httpHeaderInjection", "blocked-until-idempotency-bound")
            .containsEntry("realWriteExecution", "blocked-by-contract")
            .containsEntry("runtimeEnableSwitch", "not-exposed");
        assertThat(contract.endpointTemplates())
            .containsEntry("operationSafetyContract", "/api/agent/observability/kube-manager/http-outlet/write-operation-safety-contract")
            .containsEntry("writeIdempotencyContract", "/api/agent/observability/kube-manager/http-outlet/write-idempotency-contract")
            .containsEntry("writeRetryReadiness", "/api/agent/observability/kube-manager/http-outlet/write-retry-readiness")
            .containsEntry("runtimeAllowlistMutation", "not-exposed")
            .containsEntry("runtimeEnableWriteRetry", "not-exposed");
        assertThat(contract.safety())
            .containsEntry("adminOnly", true)
            .containsEntry("readOnly", true)
            .containsEntry("localProcessOnly", true)
            .containsEntry("summaryOnly", true)
            .containsEntry("kubeManagerCalls", false)
            .containsEntry("remoteProbeExecuted", false)
            .containsEntry("restClientUsed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("auditWrite", false)
            .containsEntry("durableReceiptIssued", false)
            .containsEntry("httpHeaderInjection", false)
            .containsEntry("writeRetryEnablement", false)
            .containsEntry("allowlistMutation", false)
            .containsEntry("readbackExecuted", false)
            .containsEntry("callerInputAccepted", false)
            .containsEntry("phase2NimHpcSlurmBcmTouched", false);
        assertThat(contract.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawBaseUrl", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsLoginPassword", false)
            .containsEntry("containsRawRequestBody", false);
        assertThat(contract.toString())
            .contains("agent-kube-manager-write-operation-safety-contract.v1", "CONTRACT_DEFINED_NOT_BOUND")
            .doesNotContain(
                "kube-manager.internal",
                "secret-password",
                "sysadmin",
                "Bearer",
                "user-token",
                "/api/login",
                "/api/100002"
            );
    }

    @Test
    void source_shouldRemainLocalOnlyAndAvoidHiddenRuntimeBinding() throws Exception {
        String source = Files.readString(SERVICE_SOURCE);

        assertThat(source)
            .doesNotContain("import com.atlas.http.KubeManagerHttpClient")
            .doesNotContain("import org.springframework.web.client.RestClient")
            .doesNotContain("prewriteHighRisk")
            .doesNotContain("resolveToken")
            .doesNotContain("refreshFallbackToken")
            .doesNotContain("doFallbackLogin")
            .doesNotContain("/api/login")
            .doesNotContain("restClient.")
            .doesNotContain("executeRead(")
            .doesNotContain("executeWrite(")
            .doesNotContain("transitionTo")
            .doesNotContain(".changeConfig")
            .doesNotContain(".reset()");
    }
}
