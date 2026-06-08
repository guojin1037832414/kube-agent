package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NimProtectedContextDetectorUsageContractTest {

    private static final List<Path> MIGRATED_WRITE_SUPPORTS = List.of(
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateWriteBodyRebuilderSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateWriteRequestSpecAdapterSupport.java")
    );
    private static final List<Path> DOWNSTREAM_BODY_CONTRACT_SUPPORTS = List.of(
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateWriteExecutionHandoffSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java")
    );

    @Test
    void migratedNimWriteSupports_shouldUseSharedProtectedContextDetectorWithoutLocalContextLists()
        throws IOException {
        for (Path path : MIGRATED_WRITE_SUPPORTS) {
            String source = read(path);

            assertThat(source)
                .as(path.toString())
                .contains("NimProtectedContextDetector.")
                .doesNotContain("PROTECTED_CONTEXT_KEYS")
                .doesNotContain("PROTECTED_BODY_KEYS")
                .doesNotContain("containsProtectedBodyContext(")
                .doesNotContain("private static boolean isProtectedContextKey(")
                .doesNotContain("private static String normalizeKey(");
        }
    }

    @Test
    void protectedContextKeyList_shouldLiveOnlyInSharedDetector() throws IOException {
        String detector = read(Path.of("src/main/java/com/atlas/tool/impl/NimProtectedContextDetector.java"));

        assertThat(detector)
            .contains("PROTECTED_CONTEXT_KEYS")
            .contains("organizationid")
            .contains("writerequestspecreport")
            .contains("replace(\".\", \"\")")
            .contains("replace(\" \", \"\")");
    }

    @Test
    void downstreamWriteBodyContracts_shouldRejectSharedProtectedContextDetector() throws IOException {
        for (Path path : DOWNSTREAM_BODY_CONTRACT_SUPPORTS) {
            String source = read(path);

            assertThat(source)
                .as(path.toString())
                .contains("NimProtectedContextDetector.containsProtectedContext(body)")
                .doesNotContain("PROTECTED_CONTEXT_KEYS")
                .doesNotContain("PROTECTED_BODY_KEYS")
                .doesNotContain("containsProtectedBodyContext(")
                .doesNotContain("private static boolean isProtectedContextKey(");
        }
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
