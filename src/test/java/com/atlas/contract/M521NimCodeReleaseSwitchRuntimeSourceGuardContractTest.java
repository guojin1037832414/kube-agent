package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Source-level guard for NIM code release switch runtime sources.
 *
 * <p>The contract catches future shortcuts that try to open {@code nim_create} through environment
 * variables, runtime flags, legacy booleans, backend readback, or executor-success evidence instead
 * of the reviewed server-owned switch source guarded by M5.21-75.</p>
 */
class M521NimCodeReleaseSwitchRuntimeSourceGuardContractTest {

    private static final Path MAIN_JAVA = Path.of("src/main/java");
    private static final Path SOURCE_GUARD_SUPPORT = Path.of(
        "src/main/java/com/atlas/tool/impl/"
            + "NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.java");
    private static final Path STATE_MACHINE_SUPPORT = Path.of(
        "src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java");

    @Test
    void productionCode_shouldNotAddEnvironmentOrRuntimeFlagReleaseSwitchShortcut()
        throws IOException {
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(MAIN_JAVA)) {
            paths
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> scanForReleaseShortcut(path, violations));
        }

        assertThat(violations)
            .as("nim_create code release switch must not be opened by environment/property/@Value shortcuts: %s",
                violations)
            .isEmpty();
    }

    @Test
    void sourceGuardSupport_shouldDeclareAllDangerousSourceFamiliesAsNonAuthoritative()
        throws IOException {
        String source = read(SOURCE_GUARD_SUPPORT);

        assertThat(source)
            .contains("CALLER_PARAMS_OR_LLM_JSON")
            .contains("ENVIRONMENT_VARIABLE_OR_RUNTIME_FLAG")
            .contains("LEGACY_NIM_CREATE_RELEASED_BOOLEAN")
            .contains("STATE_MACHINE_WRITE_PERMITTED_BOOLEAN")
            .contains("DURABLE_EXECUTOR_SUCCESS_OR_DEPLOYMENT_ID")
            .contains("BACKEND_QUERY_OR_READBACK_RESULT")
            .contains("SYS_LOG_OR_ELASTICSEARCH_BACKFILL")
            .contains("RELEASE_DECISION_OR_VALIDATION_CONTRACT_REPORT_ONLY")
            .contains("result.put(\"acceptedSourcesForCurrentRelease\", List.of())")
            .contains("result.put(\"dangerousReleaseCredentialFieldNames\", dangerousReleaseCredentialFieldNames())")
            .contains("\"codeReleaseSwitchContractReportAcceptedForRelease\"")
            .contains("\"writeExecuted\"")
            .contains("\"deploymentId\"")
            .contains("result.put(\"candidateSourceEvidenceAuthoritative\", false)")
            .contains("result.put(\"environmentVariableSourceAllowed\", false)")
            .contains("result.put(\"runtimeFlagSourceAllowed\", false)")
            .contains("result.put(\"stateMachineBooleanSourceAllowed\", false)")
            .contains("result.put(\"durableExecutorSuccessSourceAllowed\", false)")
            .contains("result.put(\"backendQuerySourceAllowedForRelease\", false)")
            .contains("fallbackToBackendQueryResultAllowed")
            .contains("fallbackToStorageBackfillAllowed")
            .contains("treating backend readback or storage rows as switch-open evidence");
    }

    @Test
    void stateMachine_shouldKeepLegacyNimCreateReleasedBooleanNonAuthoritative()
        throws IOException {
        String source = read(STATE_MACHINE_SUPPORT);

        assertThat(source)
            .contains("if (!safeRequest.nimCreateReleased())")
            .contains("result.put(\"codeReleaseSwitchContractReportRequired\", true)")
            .contains("result.put(\"codeReleaseSwitchRuntimeBindingRequired\", true)")
            .contains("result.put(\"codeReleaseSwitchDigestVerified\", false)")
            .contains("result.put(\"releaseDecisionDigestVerified\", false)")
            .contains("result.put(\"validationResultDigestVerified\", false)")
            .contains("result.put(\"legacyNimCreateReleasedBooleanAuthoritative\", false)")
            .contains("Missing code release switch contract report; nimCreateReleased=true alone cannot authorize the state machine.")
            .contains("code release switch runtime binding 必须由状态机复算 switch digest，旧 nimCreateReleased 布尔值不能单独授权");
    }

    private void scanForReleaseShortcut(Path path, List<String> violations) {
        try {
            String source = read(path);
            int lineNumber = 0;
            for (String line : source.lines().toList()) {
                lineNumber++;
                String normalized = line.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
                boolean shortcutApi = line.contains("System.getenv(")
                    || line.contains("System.getProperty(")
                    || line.contains("@Value(");
                boolean releaseToken = normalized.contains("nimcreatereleased")
                    || normalized.contains("codereleaseswitch")
                    || normalized.contains("nimcreatecoderelease")
                    || normalized.contains("nimreleaseswitch");
                if (shortcutApi && releaseToken) {
                    violations.add(path + ":" + lineNumber + " :: " + line.trim());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to scan source file: " + path, ex);
        }
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
