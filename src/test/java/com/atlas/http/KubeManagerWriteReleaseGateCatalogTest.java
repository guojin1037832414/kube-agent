package com.atlas.http;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KubeManagerWriteReleaseGateCatalogTest {

    private static final Path CATALOG_SOURCE = Path.of(
        "src/main/java/com/atlas/http/KubeManagerWriteReleaseGateCatalog.java"
    );

    @Test
    void catalog_shouldDefineDurableReceiptAndReleaseEvidenceWithoutRuntimeBinding() {
        KubeManagerWriteDurableReceiptContract receipt =
            KubeManagerWriteReleaseGateCatalog.durableReceiptContract();
        KubeManagerWriteReleaseEvidenceContract release =
            KubeManagerWriteReleaseGateCatalog.releaseEvidenceContract();

        assertThat(KubeManagerWriteReleaseGateCatalog.SCHEMA_VERSION)
            .isEqualTo("kube-manager-write-release-gate-catalog.v1");
        assertThat(receipt.contractId()).isEqualTo("generic-write-durable-prewrite-receipt.v1");
        assertThat(receipt.contractExists()).isTrue();
        assertThat(receipt.boundToHttpOutlet()).isFalse();
        assertThat(receipt.issuerExists()).isFalse();
        assertThat(receipt.issuedByReadinessEndpoint()).isFalse();
        assertThat(receipt.durableStorageMutationAllowed()).isFalse();
        assertThat(receipt.receiptPhase()).isEqualTo("PRE_EXECUTION");
        assertThat(receipt.digestAlgorithm()).isEqualTo("SHA-256");
        assertThat(receipt.requiredFields())
            .contains(
                "receiptId",
                "auditEventDigest",
                "requestSpecDigest",
                "principalFingerprint",
                "organizationFingerprint",
                "idempotencyKeyDigest",
                "hitlConfirmationDigest",
                "releaseEvidenceDigest"
            );
        assertThat(receipt.rejectedCallerFields())
            .contains("callerReceiptId", "callerIdempotencyKey", "callerReleaseApproved");

        assertThat(release.contractId()).isEqualTo("generic-write-hitl-release-evidence.v1");
        assertThat(release.contractExists()).isTrue();
        assertThat(release.boundToHttpOutlet()).isFalse();
        assertThat(release.hitlEvidenceRequired()).isTrue();
        assertThat(release.releaseReviewRequired()).isTrue();
        assertThat(release.callerProvidedReleaseEvidenceAccepted()).isFalse();
        assertThat(release.canOpenReleaseSwitch()).isFalse();
        assertThat(release.requiredEvidence())
            .contains(
                "serverHitlConfirmationDigest",
                "releaseReviewerFingerprint",
                "releaseDecisionDigest",
                "evalGateBundleDigest",
                "operationSafetyContractDigest",
                "retryGovernanceContractDigest"
            );
        assertThat(release.rejectedEvidenceSources())
            .contains("LLM-generated approval text", "caller request flag", "post-write success response");
        assertThat(release.releaseBlockers())
            .contains(
                "release-evidence-contract-not-bound-to-http-outlet",
                "durable-receipt-issuer-missing",
                "runtime-release-switch-intentionally-absent"
            );
        assertThat(KubeManagerWriteReleaseGateCatalog.runtimeReleaseGateOpenCount()).isZero();
    }

    @Test
    void source_shouldRemainPureCatalogWithoutRuntimeWriters() throws Exception {
        String source = Files.readString(CATALOG_SOURCE);

        assertThat(source)
            .doesNotContain("RetryRegistry")
            .doesNotContain("Retry.decorate")
            .doesNotContain("RestClient")
            .doesNotContain("prewriteHighRisk")
            .doesNotContain("HitlController")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("executeWrite(")
            .doesNotContain("executeRead(")
            .doesNotContain("transitionTo")
            .doesNotContain(".changeConfig")
            .doesNotContain(".reset()");
    }
}
