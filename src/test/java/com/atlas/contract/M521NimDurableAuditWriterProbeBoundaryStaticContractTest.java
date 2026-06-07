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
 * M5.21-78 source-level guard for the NIM durable audit writer/probe boundary.
 *
 * <p>This contract stays static on purpose. The dedicated writer boundary and storage probe
 * executor are the future places where real storage may eventually be wired, so this test forces
 * any such change to be reviewed explicitly instead of slipping into the HOLD-only shells.</p>
 */
class M521NimDurableAuditWriterProbeBoundaryStaticContractTest {

    private static final Path DEDICATED_WRITER_BOUNDARY = Path.of(
        "src/main/java/com/atlas/tool/impl/NimCreateDedicatedDurableAuditWriterBoundarySupport.java");
    private static final Path STORAGE_PROBE_EXECUTOR = Path.of(
        "src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeExecutorSupport.java");
    private static final List<Path> NIM_DURABLE_RELEASE_CHAIN = List.of(
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditWriterPlanSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageAvailabilityGateSupport.java"),
        DEDICATED_WRITER_BOUNDARY,
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditWriterInterfaceSpecSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptSchemaSupport.java"),
        STORAGE_PROBE_EXECUTOR,
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeResultSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditValidationResultProbeBindingMigrationSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReceiptValidationResultSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditReleaseDecisionContractSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchContractSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateDurableWriteExecutorSupport.java"),
        Path.of("src/main/java/com/atlas/tool/impl/NimCreateStateMachineSupport.java")
    );

    @Test
    void dedicatedWriterBoundary_shouldRemainHoldOnlyAndRecursiveAgainstForgedSuccess()
        throws IOException {
        String source = read(DEDICATED_WRITER_BOUNDARY);

        assertThat(source)
            .contains("DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_TEST_DOUBLE_CONTRACT_ONLY")
            .contains("DEDICATED_DURABLE_AUDIT_WRITER_BOUNDARY_IMPLEMENTATION_HOLD")
            .contains("writerBoundaryState\", inputAccepted ? HOLD_STATE : REJECTED_STATE")
            .contains("result.put(\"networkAccess\", \"NOT_PERFORMED\")")
            .contains("result.put(\"sideEffect\", \"NONE\")")
            .contains("result.put(\"realStorageTouched\", false)")
            .contains("result.put(\"storageProbeExecuted\", false)")
            .contains("result.put(\"storageAvailable\", false)")
            .contains("result.put(\"preWritePersisted\", false)")
            .contains("result.put(\"postWritePersisted\", false)")
            .contains("result.put(\"durableReceiptCanBeIssued\", false)")
            .contains("result.put(\"durableReceiptIssued\", false)")
            .contains("result.put(\"boundaryPlanDigest\", inputAccepted ? digestFor(writerBoundaryPlan) : \"\")")
            .contains("text(availabilityGateReport.get(\"sourceWriterPlanDigest\")).equals(text(writerPlanReport.get(\"writerPlanDigest\")))")
            .contains("text(writerPlanReport.get(\"writerPlanDigest\")).equals(text(availabilityPlan.get(\"sourceWriterPlanDigest\")))")
            .contains("text(availabilityGateReport.get(\"availabilityPlanDigest\")).equals(digestFor(availabilityPlan))")
            .contains("if (value instanceof Map<?, ?> nested && hasForgedSuccessClaim(objectMap(nested)))")
            .contains("if (item instanceof Map<?, ?> nestedItem && hasForgedSuccessClaim(objectMap(nestedItem)))")
            .contains("private static boolean isForgedSuccessClaim(String key, Object value)")
            .contains("case \"receiptStatus\" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(value))")
            .contains("case \"storageMode\" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(value))")
            .contains("validateNoSecretMaterial(\"auditContext\"")
            .contains("validateNoSecretMaterial(\"trustedPrincipalSnapshot\"")
            .contains("validateNoSecretMaterial(\"durableAuditWriterPlanReport\"")
            .contains("validateNoSecretMaterial(\"storageAvailabilityGateReport\"");

        assertThat(source)
            .doesNotContain("result.put(\"realStorageTouched\", true)")
            .doesNotContain("result.put(\"storageProbeExecuted\", true)")
            .doesNotContain("result.put(\"storageAvailable\", true)")
            .doesNotContain("result.put(\"preWritePersisted\", true)")
            .doesNotContain("result.put(\"postWritePersisted\", true)")
            .doesNotContain("result.put(\"durableReceiptCanBeIssued\", true)")
            .doesNotContain("result.put(\"durableReceiptIssued\", true)")
            .doesNotContain("result.put(\"releaseEligible\", true)")
            .doesNotContain("result.put(\"durable\", true)");
    }

    @Test
    void storageProbeExecutor_shouldRemainHoldOnlyAndBindDedicatedBoundary()
        throws IOException {
        String source = read(STORAGE_PROBE_EXECUTOR);

        assertThat(source)
            .contains("DURABLE_AUDIT_STORAGE_PROBE_EXECUTOR_CONTRACT_ONLY")
            .contains("STORAGE_PROBE_EXECUTOR_IMPLEMENTATION_HOLD")
            .contains("probeExecutorState\", inputAccepted ? HOLD_STATE : REJECTED_STATE")
            .contains("result.put(\"networkAccess\", \"NOT_PERFORMED\")")
            .contains("result.put(\"sideEffect\", \"NONE\")")
            .contains("result.put(\"springBeanRegistered\", false)")
            .contains("result.put(\"httpClientBound\", false)")
            .contains("result.put(\"storageClientBound\", false)")
            .contains("result.put(\"requiredInsideDedicatedWriterBoundary\", true)")
            .contains("result.put(\"storageProbeExecuted\", false)")
            .contains("result.put(\"probeAttempted\", false)")
            .contains("result.put(\"realStorageTouched\", false)")
            .contains("result.put(\"storageAvailable\", false)")
            .contains("result.put(\"preWriteAllowed\", false)")
            .contains("result.put(\"preWritePersisted\", false)")
            .contains("result.put(\"postWritePersisted\", false)")
            .contains("result.put(\"writeExecutionAllowed\", false)")
            .contains("result.put(\"realHttpExecutionAllowed\", false)")
            .contains("result.put(\"storageProbeReceiptIssued\", false)")
            .contains("result.put(\"durableReceiptCanBeIssued\", false)")
            .contains("result.put(\"durableReceiptIssued\", false)")
            .contains("text(writerBoundaryReport.get(\"sourceAvailabilityPlanDigest\")).equals(text(availabilityGateReport.get(\"availabilityPlanDigest\")))")
            .contains("text(writerBoundaryReport.get(\"boundaryPlanDigest\")).equals(digestFor(writerBoundaryPlan))")
            .contains("probeExecutorPlanDigest\", inputAccepted ? digestFor(probeExecutorPlan) : \"\"")
            .contains("if (value instanceof Map<?, ?> nested && containsForgedProbeSuccessClaim(objectMap(nested)))")
            .contains("if (item instanceof Map<?, ?> nestedItem && containsForgedProbeSuccessClaim(objectMap(nestedItem)))")
            .contains("case \"probeStatus\" -> Set.of(\"SUCCESS\", \"STORAGE_AVAILABLE_CONFIRMED\").contains(text(value))")
            .contains("case \"receiptStatus\" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(value))")
            .contains("case \"storageMode\" -> NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(value))")
            .contains("validateNoSecretMaterial(\"probeExecutionSnapshot\"");

        assertThat(source)
            .doesNotContain("result.put(\"storageProbeExecuted\", true)")
            .doesNotContain("result.put(\"probeAttempted\", true)")
            .doesNotContain("result.put(\"realStorageTouched\", true)")
            .doesNotContain("result.put(\"storageAvailable\", true)")
            .doesNotContain("result.put(\"preWriteAllowed\", true)")
            .doesNotContain("result.put(\"preWritePersisted\", true)")
            .doesNotContain("result.put(\"postWritePersisted\", true)")
            .doesNotContain("result.put(\"writeExecutionAllowed\", true)")
            .doesNotContain("result.put(\"realHttpExecutionAllowed\", true)")
            .doesNotContain("result.put(\"storageProbeReceiptIssued\", true)")
            .doesNotContain("result.put(\"durableReceiptCanBeIssued\", true)")
            .doesNotContain("result.put(\"durableReceiptIssued\", true)")
            .doesNotContain("result.put(\"releaseEligible\", true)")
            .doesNotContain("result.put(\"durable\", true)");
    }

    @Test
    void writerAndProbeBoundary_shouldNotBindRuntimeIoOrRegistrationShortcuts()
        throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path path : NIM_DURABLE_RELEASE_CHAIN) {
            String source = read(path);
            scanForForbiddenTokens(path, source, violations);
        }

        assertThat(violations)
            .as("M5.21-78 writer/probe boundary must remain explicit-review HOLD-only:\n%s",
                String.join("\n", violations))
            .isEmpty();
    }

    @Test
    void durableReleaseChain_shouldKeepDigestAndForgedClaimGuardNames()
        throws IOException {
        String joined = readJoined(NIM_DURABLE_RELEASE_CHAIN);

        assertThat(joined)
            .contains("storagePlanDigest")
            .contains("writerPlanDigest")
            .contains("availabilityPlanDigest")
            .contains("boundaryPlanDigest")
            .contains("interfaceSpecDigest")
            .contains("schemaDigest")
            .contains("validationPlanDigest")
            .contains("probeExecutorPlanDigest")
            .contains("probeResultContractDigest")
            .contains("bindingPlanDigest")
            .contains("enhancedMigrationPlanDigest")
            .contains("validationResultContractDigest")
            .contains("releaseDecisionContractDigest")
            .contains("codeReleaseSwitchContractDigest")
            .contains("sourceGuardMatrixDigest")
            .contains("STORAGE_AVAILABILITY_GATE_FORGED_SUCCESS_CLAIM")
            .contains("DEDICATED_AUDIT_WRITER_BOUNDARY_FORGED_SUCCESS_CLAIM")
            .contains("STORAGE_PROBE_EXECUTOR_FORGED_SUCCESS_CLAIM")
            .contains("STORAGE_PROBE_RESULT_FORGED_SUCCESS_CLAIM")
            .contains("PROBE_RESULT_VALIDATION_BINDING_FORGED_SUCCESS_CLAIM")
            .contains("DURABLE_AUDIT_RECEIPT_VALIDATION_RESULT_FORGED_PASS_CLAIM")
            .contains("DURABLE_AUDIT_RELEASE_DECISION_CONTRACT_FORGED_RELEASE_CLAIM")
            .contains("DURABLE_AUDIT_CODE_RELEASE_SWITCH_FORGED_OPEN_CLAIM")
            .contains("CODE_RELEASE_SWITCH_RUNTIME_SOURCE_FORGED_RELEASE_CLAIM");
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
            "ElasticsearchRestTemplate",
            "ElasticsearchOperations",
            "ISysLogService ",
            "ISysLogService;",
            ".saveSysLog(",
            ".save(",
            "8100",
            "result.put(\"realStorageTouched\", true)",
            "result.put(\"serverIssuedProbeResultAccepted\", true)",
            "result.put(\"durableAckVerified\", true)",
            "result.put(\"readAfterWriteVerified\", true)",
            "result.put(\"storageProbeExecuted\", true)",
            "result.put(\"storageAvailable\", true)",
            "result.put(\"preWriteAllowed\", true)",
            "result.put(\"preWritePersisted\", true)",
            "result.put(\"postWritePersisted\", true)",
            "result.put(\"storageProbeReceiptIssued\", true)",
            "result.put(\"durableReceiptValidationPassed\", true)",
            "result.put(\"validationPassed\", true)",
            "result.put(\"writePermitted\", true)",
            "result.put(\"writeExecutionAllowed\", true)",
            "result.put(\"realHttpExecutionAllowed\", true)",
            "result.put(\"durableReceiptCanBeIssued\", true)",
            "result.put(\"durableReceiptIssued\", true)",
            "result.put(\"releaseEligible\", true)"
        );
        for (String line : source.lines().toList()) {
            if (allowedDocumentationLine(line)) {
                continue;
            }
            for (String token : forbidden) {
                if (line.contains(token)) {
                    violations.add(path + " contains forbidden token: " + token + " :: " + line.trim());
                }
            }
        }
    }

    private boolean allowedDocumentationLine(String line) {
        return line.contains("Elasticsearch")
            || line.contains("ISysLogService")
            || line.contains("sys_log")
            || line.contains("Future server-side probe must prove sys_log persistence availability")
            || line.contains("\"wire reviewed KubeManagerHttpClient only inside this executor boundary\"");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String readJoined(List<Path> paths) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (Path path : paths) {
            builder.append("\n// ").append(path).append("\n");
            builder.append(read(path));
        }
        return builder.toString();
    }
}
