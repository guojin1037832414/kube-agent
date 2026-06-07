package com.atlas.tool.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.21-86 NIM secret material detector usage contract.
 *
 * <p>This contract keeps the first shared-detector migration from drifting back into per-shell
 * copies of the same forbidden-key and secret-looking-value rules.</p>
 */
class NimForbiddenSecretMaterialDetectorUsageContractTest {

    private static final List<Path> MIGRATED_SUPPORTS = List.of(
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/"
            + "NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateWriteRequestSpecAdapterSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateWriteExecutionHandoffSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditWriterPlanSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditWriterInterfaceSpecSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageAvailabilityGateSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeExecutorSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDedicatedDurableAuditWriterBoundarySupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateReadinessExecutorSupport.java")
    );

    private static final List<Path> NON_BOOLEAN_NUMBER_POLICY_SUPPORTS = List.of(
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeResultSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/"
            + "NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationResultSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/"
            + "NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionContractSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchContractSupport.java")
    );

    private static final List<Path> STRICT_RECURSIVE_POLICY_SUPPORTS = List.of(
        Path.of("src/main/java/com/atlas/tool/impl/"
            + "NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/"
            + "NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.java")
    );

    private static final List<Path> RECEIPT_SCHEMA_POLICY_SUPPORTS = List.of(
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationGateSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultMigrationSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionGateSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/"
            + "NimCreateStateMachineReleaseDecisionRequirementSupport.java")
    );

    @Test
    void migratedNimSupports_shouldUseSharedDetectorWithoutLocalSecretMaterialLists()
        throws IOException {
        for (Path path : MIGRATED_SUPPORTS) {
            String source = read(path);

            assertThat(source)
                .contains("NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial")
                .doesNotContain("FORBIDDEN_SECRET_KEYS")
                .doesNotContain("looksLikeSecretValue(")
                .doesNotContain("isForbiddenSecretKey(")
                .doesNotContain("secretBearingValue(")
                .doesNotContain("isDocumentedForbiddenFieldName(");
        }
    }

    @Test
    void nonBooleanNumberPolicySupports_shouldUseSharedDetectorWithoutLocalSecretMaterialLists()
        throws IOException {
        for (Path path : NON_BOOLEAN_NUMBER_POLICY_SUPPORTS) {
            String source = read(path);

            assertThat(source)
                .contains("NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial")
                .contains("NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()")
                .doesNotContain("FORBIDDEN_SECRET_KEYS")
                .doesNotContain("looksLikeSecretValue(")
                .doesNotContain("isForbiddenSecretKey(")
                .doesNotContain("secretBearingValue(")
                .doesNotContain("isDocumentedForbiddenFieldName(");
        }
    }

    @Test
    void strictRecursivePolicySupports_shouldUseSharedDetectorWithoutLocalSecretMaterialLists()
        throws IOException {
        for (Path path : STRICT_RECURSIVE_POLICY_SUPPORTS) {
            String source = read(path);

            assertThat(source)
                .contains("NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial")
                .contains("NimForbiddenSecretMaterialDetector.strictRecursivePolicy()")
                .doesNotContain("FORBIDDEN_SECRET_KEYS")
                .doesNotContain("looksLikeSecretValue(")
                .doesNotContain("isForbiddenSecretKey(")
                .doesNotContain("secretBearingValue(")
                .doesNotContain("isDocumentedForbiddenFieldName(");
        }
    }

    @Test
    void receiptSchemaPolicySupports_shouldUseSharedDetectorWithoutLocalSecretMaterialLists()
        throws IOException {
        for (Path path : RECEIPT_SCHEMA_POLICY_SUPPORTS) {
            String source = read(path);

            assertThat(source)
                .contains("NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial")
                .contains("NimForbiddenSecretMaterialDetector.receiptSchemaPolicy()")
                .doesNotContain("FORBIDDEN_SECRET_KEYS")
                .doesNotContain("looksLikeSecretValue(")
                .doesNotContain("isForbiddenSecretKey(")
                .doesNotContain("secretBearingValue(")
                .doesNotContain("isDocumentedForbiddenFieldName(");
        }
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
