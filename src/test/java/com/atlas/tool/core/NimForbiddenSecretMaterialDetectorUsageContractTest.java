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
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateWriteExecutionHandoffSupport.java")
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

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
