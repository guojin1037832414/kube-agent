package com.atlas.http;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-owned kube-manager write safety catalog tests.
 */
class KubeManagerWriteSafetyContractCatalogTest {

    private static final Path CATALOG_SOURCE = Path.of(
        "src/main/java/com/atlas/http/KubeManagerWriteSafetyContractCatalog.java"
    );

    @Test
    void catalog_shouldDefineReviewOnlyAllowlistAndGetOnlyReadbackContract() {
        assertThat(KubeManagerWriteSafetyContractCatalog.SCHEMA_VERSION)
            .isEqualTo("kube-manager-write-safety-catalog.v1");

        assertThat(KubeManagerWriteSafetyContractCatalog.reviewOnlyAllowlistEntries())
            .extracting(KubeManagerWriteOperationAllowlistEntry::operationId)
            .containsExactly(
                "generic-tenant-create",
                "generic-tenant-update-patch",
                "generic-tenant-update-put",
                "generic-tenant-delete",
                "generic-tenant-action"
            );
        assertThat(KubeManagerWriteSafetyContractCatalog.reviewOnlyAllowlistEntries())
            .allSatisfy(entry -> {
                assertThat(entry.retryEligible()).isFalse();
                assertThat(entry.runtimeEligible()).isFalse();
                assertThat(entry.phase2Excluded()).isFalse();
                assertThat(entry.tenantBinding()).isEqualTo("same-organization-fingerprint");
                assertThat(entry.readbackContractId())
                    .isEqualTo(KubeManagerWriteSafetyContractCatalog.READBACK_CONTRACT_ID);
            });
        assertThat(KubeManagerWriteSafetyContractCatalog.runtimeRetryEligibleOperationCount()).isZero();
        assertThat(KubeManagerWriteSafetyContractCatalog.phase2ExcludedDomains())
            .containsExactly("NIM", "HPC", "Slurm", "BCM");

        KubeManagerPostWriteReadbackContract readback =
            KubeManagerWriteSafetyContractCatalog.genericReadbackContract();

        assertThat(readback.contractId()).isEqualTo("generic-tenant-resource-readback.v1");
        assertThat(readback.readMethod()).isEqualTo("GET");
        assertThat(readback.readEndpointTemplate()).isEqualTo("/api/{organizationId}/{resourceType}/{resourceId}");
        assertThat(readback.samePrincipalRequired()).isTrue();
        assertThat(readback.sameOrganizationFingerprintRequired()).isTrue();
        assertThat(readback.requestSpecDigestRequired()).isTrue();
        assertThat(readback.idempotencyDigestRequired()).isTrue();
        assertThat(readback.successClaimRequiresReadback()).isTrue();
        assertThat(readback.acceptsCallerSuccessClaim()).isFalse();
        assertThat(readback.executorExists()).isFalse();
        assertThat(readback.executedByReadinessEndpoint()).isFalse();
        assertThat(readback.canOpenReleaseSwitch()).isFalse();
        assertThat(readback.expectedSuccessTerminalStates())
            .containsExactly("READY", "ACTIVE", "SUCCEEDED", "DELETED_CONFIRMED");
    }

    @Test
    void source_shouldNotScanToolsOrBindHttpExecution() throws Exception {
        String source = Files.readString(CATALOG_SOURCE);

        assertThat(source)
            .doesNotContain("import com.atlas.tool.core.ToolRegistry")
            .doesNotContain("ToolRegistry.")
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("RestClient")
            .doesNotContain("executeRead(")
            .doesNotContain("executeWrite(")
            .doesNotContain("prewriteHighRisk")
            .doesNotContain("Retry.decorate")
            .doesNotContain("NimCreate")
            .doesNotContain("HpcJob")
            .doesNotContain("SlurmNode")
            .doesNotContain("BcmAllocation")
            .doesNotContain("com.atlas.tool.impl");
    }
}
