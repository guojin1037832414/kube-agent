package com.atlas.memoryrag;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Memory/RAG source evidence digest derivation contract tests.
 */
class MemoryRagSourceEvidenceDigestDeriverTest {

    private static final Path DERIVER_SOURCE = Path.of(
        "src/main/java/com/atlas/memoryrag/MemoryRagSourceEvidenceDigestDeriver.java"
    );
    private static final Path INPUT_SOURCE = Path.of(
        "src/main/java/com/atlas/memoryrag/MemoryRagSourceEvidenceInput.java"
    );

    private final MemoryRagSourceEvidenceDigestDeriver deriver = new MemoryRagSourceEvidenceDigestDeriver();

    @Test
    void derive_shouldCreateDeterministicServerDerivedDigestsFromRedactedEvidence() {
        MemoryRagSourceEvidenceInput input = validInput();

        MemoryRagSourceEvidenceDigestResult first = deriver.derive(input);
        MemoryRagSourceEvidenceDigestResult second = deriver.derive(input);

        assertThat(first).isEqualTo(second);
        assertThat(first.schemaVersion()).isEqualTo("agent-memory-rag-source-evidence-digest.v1");
        assertThat(first.digestSource()).isEqualTo("server-derived-sha256-redacted-source-evidence.v1");
        assertThat(first.algorithm()).isEqualTo("SHA-256");
        assertThat(first.sourceDigest()).matches("sha256:[a-f0-9]{64}");
        assertThat(first.chunkDigest()).matches("sha256:[a-f0-9]{64}");
        assertThat(first.evidenceDigest()).matches("sha256:[a-f0-9]{64}");
        assertThat(first.citationSeed()).matches("rag-cite-v1-[a-f0-9]{64}");
        assertThat(first.rawSourceAccepted()).isFalse();
        assertThat(first.promptEvidenceAllowedNow()).isFalse();
        assertThat(first.boundToIngestionRuntime()).isFalse();
        assertThat(first.reusableAcrossTenantScope()).isFalse();
        assertThat(first.toString())
            .doesNotContain(
                "raw document",
                "Bearer",
                "password",
                "secret",
                "token=",
                "tenant-100002"
            );
    }

    @Test
    void derive_shouldChangeWhenSourceChunkOrTenantEvidenceChanges() {
        MemoryRagSourceEvidenceDigestResult baseline = deriver.derive(validInput());

        assertThat(deriver.derive(new MemoryRagSourceEvidenceInput(
            "doc-1",
            "runbook",
            "v1",
            sha("01"),
            sha("22"),
            sha("03"),
            "REDACTED",
            sha("04"),
            "EPHEMERAL_30D",
            sha("05"),
            sha("06"),
            sha("07"),
            sha("08")
        )).evidenceDigest()).isNotEqualTo(baseline.evidenceDigest());

        assertThat(deriver.derive(new MemoryRagSourceEvidenceInput(
            "doc-1",
            "runbook",
            "v1",
            sha("01"),
            sha("02"),
            sha("03"),
            "REDACTED",
            sha("04"),
            "EPHEMERAL_30D",
            sha("05"),
            sha("06"),
            sha("77"),
            sha("08")
        )).chunkDigest()).isNotEqualTo(baseline.chunkDigest());
    }

    @Test
    void input_shouldNormalizeDigestAndEnumFields() {
        MemoryRagSourceEvidenceInput input = new MemoryRagSourceEvidenceInput(
            " doc-1 ",
            " RUNBOOK ",
            " v1 ",
            shaWithoutPrefix("01"),
            shaWithoutPrefix("02"),
            shaWithoutPrefix("03"),
            " redacted ",
            shaWithoutPrefix("04"),
            " ephemeral_30d ",
            shaWithoutPrefix("05"),
            shaWithoutPrefix("06"),
            shaWithoutPrefix("07"),
            shaWithoutPrefix("08")
        );

        assertThat(input.sourceId()).isEqualTo("doc-1");
        assertThat(input.sourceType()).isEqualTo("runbook");
        assertThat(input.redactionStatus()).isEqualTo("REDACTED");
        assertThat(input.retentionPolicy()).isEqualTo("EPHEMERAL_30D");
        assertThat(input.sourceUriDigest()).isEqualTo(sha("01"));
    }

    @Test
    void input_shouldRejectBlankUnsupportedAndRawSecretOrDocumentEvidence() {
        assertThatThrownBy(() -> new MemoryRagSourceEvidenceInput(
            "",
            "runbook",
            "v1",
            sha("01"),
            sha("02"),
            sha("03"),
            "REDACTED",
            sha("04"),
            "EPHEMERAL_30D",
            sha("05"),
            sha("06"),
            sha("07"),
            sha("08")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sourceId is required");

        assertThatThrownBy(() -> new MemoryRagSourceEvidenceInput(
            "doc-1",
            "free-form",
            "v1",
            sha("01"),
            sha("02"),
            sha("03"),
            "REDACTED",
            sha("04"),
            "EPHEMERAL_30D",
            sha("05"),
            sha("06"),
            sha("07"),
            sha("08")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sourceType must be one of");

        assertThatThrownBy(() -> new MemoryRagSourceEvidenceInput(
            "doc-1",
            "runbook",
            "v1",
            "https://kube-manager.internal/raw-document?token=secret",
            sha("02"),
            sha("03"),
            "REDACTED",
            sha("04"),
            "EPHEMERAL_30D",
            sha("05"),
            sha("06"),
            sha("07"),
            sha("08")
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sourceUriDigest must not contain raw secret or document markers");
    }

    @Test
    void source_shouldStayPureJavaAndAvoidRuntimeCalls() throws Exception {
        String deriverSource = Files.readString(DERIVER_SOURCE);
        String inputSource = Files.readString(INPUT_SOURCE);

        assertThat(deriverSource)
            .contains("MessageDigest")
            .doesNotContain("VectorStore")
            .doesNotContain("Embedding")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("@PostMapping")
            .doesNotContain("execute(")
            .doesNotContain("append(")
            .doesNotContain("recent(");
        assertThat(inputSource)
            .contains("FORBIDDEN_RAW_MARKERS")
            .doesNotContain("org.springframework.ai")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient")
            .doesNotContain("execute(");
    }

    private MemoryRagSourceEvidenceInput validInput() {
        return new MemoryRagSourceEvidenceInput(
            "doc-1",
            "runbook",
            "v1",
            sha("01"),
            sha("02"),
            sha("03"),
            "REDACTED",
            sha("04"),
            "EPHEMERAL_30D",
            sha("05"),
            sha("06"),
            sha("07"),
            sha("08")
        );
    }

    private static String sha(String suffix) {
        return "sha256:" + shaWithoutPrefix(suffix);
    }

    private static String shaWithoutPrefix(String suffix) {
        return "0".repeat(62) + suffix;
    }
}
