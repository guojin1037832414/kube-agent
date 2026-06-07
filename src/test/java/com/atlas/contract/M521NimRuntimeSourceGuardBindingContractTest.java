package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.21-77 source-level guard for the M5.21-76 runtime source-guard binding.
 *
 * <p>This test is intentionally static: it does not start Spring, does not call kube-manager
 * `8100`, and does not exercise any write path. It protects the learning/safety contract that the
 * M5.21-75 source guard must remain a required input to both current shells without becoming a
 * release credential.</p>
 */
class M521NimRuntimeSourceGuardBindingContractTest {

    private static final Path STATE_MACHINE_SUPPORT = Path.of(
        "src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java");
    private static final Path DURABLE_EXECUTOR_SUPPORT = Path.of(
        "src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java");

    @Test
    void stateMachine_shouldKeepRuntimeSourceGuardReportAsRequiredHoldEvidence()
        throws IOException {
        String source = read(STATE_MACHINE_SUPPORT);

        assertThat(source)
            .contains("Map<String, Object> codeReleaseSwitchRuntimeSourceGuardReport")
            .contains("validateCodeReleaseSwitchRuntimeSourceGuardReport(")
            .contains("safeRequest.codeReleaseSwitchRuntimeSourceGuardReport()")
            .contains("result.put(\"codeReleaseSwitchRuntimeSourceGuardReportRequired\", true)")
            .contains("result.put(\"codeReleaseSwitchRuntimeSourceGuardAcceptedForRelease\", false)")
            .contains("result.put(\"sourceGuardInstalled\", false)")
            .contains("result.put(\"candidateSourceEvidenceAuthoritative\", false)")
            .contains("result.put(\"backendQuerySourceAllowedForRelease\", false)")
            .contains("result.put(\"sysLogBackfillSourceAllowed\", false)")
            .contains("text(codeReleaseSwitchRuntimeSourceGuardReport.get(\"sourceGuardMatrixDigest\")).equals(")
            .contains("text(durableWriteExecutorReport.get(\"sourceGuardMatrixDigest\"))")
            .contains("text(codeReleaseSwitchRuntimeSourceGuardReport.get(\"sourceRuntimeBindingContractDigest\")).equals(")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_READY")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_CONTRACT_INVALID")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_RELEASE_CLAIM_NOT_TRUSTED")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_CONTAINS_FORBIDDEN_SECRET")
            .contains("Boolean.TRUE.equals(report.get(\"llmJsonSourceAllowed\"))")
            .contains("Boolean.TRUE.equals(report.get(\"releaseDecisionContractReportSourceAllowed\"))")
            .contains("Boolean.TRUE.equals(report.get(\"validationResultContractReportSourceAllowed\"))");

        assertThat(source)
            .doesNotContain("result.put(\"writeExecutionAllowed\", true)")
            .doesNotContain("result.put(\"realHttpExecutionAllowed\", true)")
            .doesNotContain("result.put(\"writeAttempted\", true)")
            .doesNotContain("result.put(\"writeExecuted\", true)")
            .doesNotContain("sourceGuardInstalled\", true")
            .doesNotContain("backendQuerySourceAllowedForRelease\", true")
            .doesNotContain("sysLogBackfillSourceAllowed\", true");
    }

    @Test
    void durableExecutor_shouldRequireRuntimeSourceGuardReportBeforeAcceptingWriteShell()
        throws IOException {
        String source = read(DURABLE_EXECUTOR_SUPPORT);

        assertThat(source)
            .contains("Map<String, Object> codeReleaseSwitchRuntimeSourceGuardReport")
            .contains("safeInput.codeReleaseSwitchRuntimeSourceGuardReport()")
            .contains("validateCodeReleaseSwitchRuntimeSourceGuardReport(")
            .contains("validateNoSecretMaterial(\"codeReleaseSwitchRuntimeSourceGuardReport\"")
            .contains("result.put(\"codeReleaseSwitchRuntimeSourceGuardReportRequired\", true)")
            .contains("result.put(\"sourceGuardMatrixDigest\"")
            .contains("result.put(\"sourceRuntimeBindingContractDigest\"")
            .contains("result.put(\"sourceGuardInstalled\", false)")
            .contains("result.put(\"candidateSourceEvidenceAuthoritative\", false)")
            .contains("result.put(\"backendQuerySourceAllowedForRelease\", false)")
            .contains("result.put(\"sysLogBackfillSourceAllowed\", false)")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_READY_FOR_DURABLE_EXECUTOR")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REPORT_NOT_TRUSTED_FOR_DURABLE_EXECUTOR")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_RELEASE_CLAIM_NOT_TRUSTED_FOR_DURABLE_EXECUTOR")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD")
            .contains("text(codeSwitchReport.get(\"codeReleaseSwitchContractDigest\")).equals(")
            .contains("text(codeSwitchReport.get(\"sourceAuditEventDigest\")).equals(text(report.get(\"sourceAuditEventDigest\")))")
            .contains("text(report.get(\"sourceGuardMatrixDigest\")).equals(digestFor(contract))")
            .contains("WriteExecutionInput(Map<String, Object> writeExecutionHandoffReport,")
            .contains("Map<String, Object> codeReleaseSwitchRuntimeSourceGuardReport")
            .contains("new WriteExecutionInput(Map.of(), Map.of(), Map.of(), Map.of())");

        assertThat(source)
            .doesNotContain("result.put(\"writeExecutionAllowed\", true)")
            .doesNotContain("result.put(\"realHttpExecutionAllowed\", true)")
            .doesNotContain("result.put(\"writeAttempted\", true)")
            .doesNotContain("result.put(\"writeExecuted\", true)")
            .doesNotContain("sourceGuardInstalled\", true")
            .doesNotContain("backendQuerySourceAllowedForRelease\", true")
            .doesNotContain("sysLogBackfillSourceAllowed\", true");
    }

    @Test
    void bindingShells_shouldNotUseRuntimeConfigNetworkOrStorageToOpenSourceGuard()
        throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path path : List.of(STATE_MACHINE_SUPPORT, DURABLE_EXECUTOR_SUPPORT)) {
            String source = read(path);
            scanForForbiddenTokens(path, source, violations);
        }

        assertThat(violations)
            .as("M5.21-76 source-guard binding must stay static/HOLD-only:\n%s",
                String.join("\n", violations))
            .isEmpty();
    }

    private void scanForForbiddenTokens(Path path, String source, List<String> violations) {
        List<String> forbidden = List.of(
            "System.getenv(",
            "System.getProperty(",
            "@Value(",
            "@Component",
            "@Service",
            "@Controller",
            "@RestController",
            "@Autowired",
            "@Bean",
            "RestTemplate",
            "WebClient",
            "import java.net.http",
            "new KubeManagerHttpClient",
            "KubeManagerHttpClient ",
            "Elasticsearch",
            "ISysLogService",
            "sys_log",
            "8100",
            "result.put(\"writePermitted\", true)",
            "result.put(\"writeExecutionAllowed\", true)",
            "result.put(\"realHttpExecutionAllowed\", true)",
            "result.put(\"writeAttempted\", true)",
            "result.put(\"writeExecuted\", true)",
            "result.put(\"postWriteReadinessTriggered\", true)"
        );
        for (String line : source.lines().toList()) {
            if (allowedFutureRequirementLine(line)) {
                continue;
            }
            for (String token : forbidden) {
                if (line.contains(token)) {
                    violations.add(path + " contains forbidden token: " + token + " :: " + line.trim());
                }
            }
        }
    }

    private boolean allowedFutureRequirementLine(String line) {
        return line.contains("\"wire reviewed KubeManagerHttpClient only inside this executor boundary\"");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
