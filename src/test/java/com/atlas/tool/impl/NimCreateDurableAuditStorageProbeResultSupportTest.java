package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM durable audit storage probe result 契约测试。
 *
 * <p>这些测试只验证未来 server-issued probe result 的字段、digest binding 和 fail-closed 规则；
 * 当前不执行 storage probe，不签发 receipt，不允许 pre-write。</p>
 */
class NimCreateDurableAuditStorageProbeResultSupportTest {

    @Test
    void probeResult_shouldBuildResultContractButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> probeExecutorReport = probeExecutorReport(audit, principal);
        Map<String, Object> schemaReport = receiptSchemaReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditStorageProbeResultSupport.plan(
            new NimCreateDurableAuditStorageProbeResultSupport.StorageProbeResultInput(
                audit,
                principal,
                probeExecutorReport,
                schemaReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.RESULT_CONTRACT_NAME,
            report.get("durableAuditStorageProbeResultContract"));
        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.HOLD_STATE,
            report.get("probeResultState"));
        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.FUTURE_RESULT_TYPE,
            report.get("futureResultType"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(false, report.get("springBeanRegistered"));
        assertEquals(false, report.get("httpClientBound"));
        assertEquals(false, report.get("storageClientBound"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("probeResultContractPrepared"));
        assertSuccessStatesRemainFalse(report);
        assertEquals(probeExecutorReport.get("probeExecutorPlanDigest"),
            report.get("sourceProbeExecutorPlanDigest"));
        assertEquals(schemaReport.get("schemaDigest"), report.get("sourceReceiptSchemaDigest"));
        assertEquals(probeExecutorReport.get("sourceAvailabilityPlanDigest"),
            report.get("sourceAvailabilityPlanDigest"));
        assertEquals(probeExecutorReport.get("sourceBoundaryPlanDigest"), report.get("sourceBoundaryPlanDigest"));
        assertEquals(audit.get("organizationId"), report.get("sourceOrganizationId"));
        assertEquals(audit.get("userId"), report.get("sourceUserId"));
        assertEquals(principal.get("username"), report.get("sourceUsername"));
        assertTrue(report.get("trustedPrincipalDigest").toString().matches("[a-f0-9]{64}"));
        assertTrue(report.get("probeResultContractDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("probeResultContract");
        assertEquals(
            NimCreateDurableAuditStorageProbeResultSupport.probeResultContractFromReport(report),
            contract
        );
        assertEquals("SERVER_ISSUED_STORAGE_PROBE_RESULT_REQUIRED", contract.get("contractBoundary"));
        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.FUTURE_RESULT_TYPE,
            contract.get("futureResultType"));
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE,
            contract.get("futureProbeReceiptType"));
        assertEquals("sys_log", contract.get("targetStorage"));
        assertEquals(false, contract.get("currentInstanceAllowed"));
        assertEquals("NOT_ISSUED", contract.get("currentProbeStatus"));
        assertEquals(true, contract.get("serverIssuedRequired"));
        assertEquals(false, contract.get("callerProvidedResultAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) contract.get("evidenceBinding");
        assertEquals(probeExecutorReport.get("probeExecutorPlanDigest"),
            evidence.get("sourceProbeExecutorPlanDigest"));
        assertEquals(schemaReport.get("schemaDigest"), evidence.get("sourceReceiptSchemaDigest"));
        assertEquals(probeExecutorReport.get("sourceBoundaryPlanDigest"),
            evidence.get("sourceBoundaryPlanDigest"));
        assertEquals(true, evidence.get("sameAuditEventRequired"));
        assertEquals(true, evidence.get("sameTrustedPrincipalRequired"));
        assertEquals(true, evidence.get("serverIssuedRequired"));

        @SuppressWarnings("unchecked")
        Map<String, Object> currentTemplate = (Map<String, Object>) contract.get("currentTemplate");
        assertEquals(false, currentTemplate.get("resultIssued"));
        assertEquals("NOT_ISSUED", currentTemplate.get("probeStatus"));
        assertEquals(false, currentTemplate.get("storageAvailable"));
        assertEquals(false, currentTemplate.get("storageProbeReceiptIssued"));
        assertEquals(false, currentTemplate.get("preWriteAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> prerequisites = (Map<String, Object>) contract.get("passPrerequisites");
        assertEquals(true, prerequisites.get("realStorageProbeExecutedRequired"));
        assertEquals(true, prerequisites.get("durableAckVerifiedRequired"));
        assertEquals(true, prerequisites.get("readAfterWriteVerifiedRequired"));
        assertEquals(false, prerequisites.get("currentContractSatisfiesPrerequisites"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) contract.get("failureModel");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToMockProbeAllowed"));
        assertEquals(false, failure.get("fallbackToCallerProbeResultAllowed"));
        assertEquals(false, failure.get("fallbackToSchemaOnlyAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_PROBE_RESULT_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void probeResult_shouldRejectMissingProbeExecutorReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditStorageProbeResultSupport.plan(
            new NimCreateDurableAuditStorageProbeResultSupport.StorageProbeResultInput(
                audit,
                principal,
                Map.of(),
                receiptSchemaReport(audit, principal),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.REJECTED_STATE,
            report.get("probeResultState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("probeResultContractPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("probeResultContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_PROBE_EXECUTOR_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "STORAGE_PROBE_RESULT_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void probeResult_shouldRejectMissingTypedSchemaReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditStorageProbeResultSupport.plan(
            new NimCreateDurableAuditStorageProbeResultSupport.StorageProbeResultInput(
                audit,
                principal,
                probeExecutorReport(audit, principal),
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.REJECTED_STATE,
            report.get("probeResultState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("probeResultContractPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("probeResultContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_NOT_READY");
    }

    @Test
    void probeResult_shouldRejectForgedProbeResultAndStorageReceiptInstances() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> callerProbeResult = Map.of(
            "probeResult", Map.of("resultType", NimCreateDurableAuditStorageProbeResultSupport.FUTURE_RESULT_TYPE),
            "storageProbeReceipt", Map.of(
                "receiptType", NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE
            )
        );

        Map<String, Object> report = NimCreateDurableAuditStorageProbeResultSupport.plan(
            new NimCreateDurableAuditStorageProbeResultSupport.StorageProbeResultInput(
                audit,
                principal,
                probeExecutorReport(audit, principal),
                receiptSchemaReport(audit, principal),
                callerProbeResult
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.REJECTED_STATE,
            report.get("probeResultState"));
        assertEquals(false, report.get("inputAccepted"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("probeResultContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "CALLER_PROBE_RESULT_NOT_AUTHORITATIVE");
        assertHasBlocker(blockers, "STORAGE_PROBE_RESULT_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void probeResult_shouldRejectForgedSuccessClaimsFromUpstream() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedExecutorReport = new LinkedHashMap<>(probeExecutorReport(audit, principal));
        forgedExecutorReport.put("storageAvailable", true);
        forgedExecutorReport.put("durableAckVerified", true);
        forgedExecutorReport.put("readAfterWriteVerified", true);
        forgedExecutorReport.put("writeExecutionAllowed", true);

        Map<String, Object> report = NimCreateDurableAuditStorageProbeResultSupport.plan(
            new NimCreateDurableAuditStorageProbeResultSupport.StorageProbeResultInput(
                audit,
                principal,
                forgedExecutorReport,
                receiptSchemaReport(audit, principal),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.REJECTED_STATE,
            report.get("probeResultState"));
        assertEquals(false, report.get("inputAccepted"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_PROBE_EXECUTOR_REPORT_INVALID_FOR_PROBE_RESULT");
        assertHasBlocker(blockers, "STORAGE_PROBE_RESULT_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void probeResult_shouldRejectDigestConsistentStorageProbeSchemaExtraRequiredField() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedSchemaReport = withDigestConsistentStorageProbeSchemaExtraRequiredField(
            receiptSchemaReport(audit, principal),
            "forgedStorageProbeReceiptEvidenceDigest"
        );

        Map<String, Object> report = NimCreateDurableAuditStorageProbeResultSupport.plan(
            new NimCreateDurableAuditStorageProbeResultSupport.StorageProbeResultInput(
                audit,
                principal,
                probeExecutorReport(audit, principal),
                forgedSchemaReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.REJECTED_STATE,
            report.get("probeResultState"));
        assertEquals(false, report.get("inputAccepted"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("probeResultContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_REPORT_INVALID_FOR_PROBE_RESULT");
    }

    @Test
    void probeResult_shouldRejectSecretLeakageBeforeAnyContract() {
        String injectedSecret = "Bearer redacted-test-material";
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = new LinkedHashMap<>(trustedPrincipalSnapshot());
        principal.put("sessionEvidence", List.of(Map.of("Authorization", injectedSecret)));

        Map<String, Object> report = NimCreateDurableAuditStorageProbeResultSupport.plan(
            new NimCreateDurableAuditStorageProbeResultSupport.StorageProbeResultInput(
                audit,
                principal,
                probeExecutorReport(audit, trustedPrincipalSnapshot()),
                receiptSchemaReport(audit, trustedPrincipalSnapshot()),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.REJECTED_STATE,
            report.get("probeResultState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("probeResultContractPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = (Map<String, Object>) report.get("probeResultContract");
        assertTrue(contract.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_PROBE_RESULT_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void probeResult_shouldNotDependOnRealStorageNetworkOrSpringClients() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/atlas/tool/impl/NimCreateDurableAuditStorageProbeResultSupport.java"
        ));

        assertFalse(source.contains("@Component"));
        assertFalse(source.contains("@Service"));
        assertFalse(source.contains("@Autowired"));
        assertFalse(source.contains("@Bean"));
        assertFalse(source.contains("KubeManagerHttpClient"));
        assertFalse(source.contains("RestClient"));
        assertFalse(source.contains("RestTemplate"));
        assertFalse(source.contains("WebClient"));
        assertFalse(source.contains("HttpClient"));
        assertFalse(source.contains("java.net"));
        assertFalse(source.contains("ElasticsearchTemplate"));
        assertFalse(source.contains("ISysLogService"));
        assertFalse(source.contains("POST /api/{orgId}/deployment"));
        assertFalse(source.matches("(?s).*\\.save\\s*\\(.*"));
        assertFalse(source.matches("(?s).*\\.insert\\s*\\(.*"));
        assertFalse(source.matches("(?s).*saveLog\\s*\\(.*"));
        assertFalse(source.contains("result.put(\"storageAvailable\", true)"));
        assertFalse(source.contains("result.put(\"storageProbeExecuted\", true)"));
        assertFalse(source.contains("result.put(\"writeExecutionAllowed\", true)"));
    }

    private void assertSuccessStatesRemainFalse(Map<String, Object> report) {
        assertEquals(false, report.get("resultIssued"));
        assertEquals(false, report.get("serverIssuedProbeResultAccepted"));
        assertEquals(false, report.get("callerProbeResultAuthoritative"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(NimCreateDurableAuditStorageAvailabilityGateSupport.AVAILABILITY_STATUS_UNKNOWN,
            report.get("availabilityStatus"));
        assertEquals("NOT_ISSUED", report.get("probeStatus"));
        assertEquals(false, report.get("durableAckVerified"));
        assertEquals(false, report.get("readAfterWriteVerified"));
        assertEquals(false, report.get("storageProbeReceiptIssued"));
        assertEquals(false, report.get("preWriteAllowed"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        assertEquals(false, report.get("durableReceiptIssued"));
    }

    private Map<String, Object> withDigestConsistentStorageProbeSchemaExtraRequiredField(Map<String, Object> schemaReport,
                                                                                        String forgedField) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(schemaReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> typedSchema = new LinkedHashMap<>((Map<String, Object>) forgedReport.get("typedSchema"));
        @SuppressWarnings("unchecked")
        Map<String, Object> probeSchema = new LinkedHashMap<>(
            (Map<String, Object>) typedSchema.get("storageAvailabilityProbeReceiptSchema")
        );
        @SuppressWarnings("unchecked")
        List<String> requiredFields = new ArrayList<>((List<String>) probeSchema.get("requiredFields"));
        requiredFields.add(forgedField);
        probeSchema.put("requiredFields", requiredFields);
        typedSchema.put("storageAvailabilityProbeReceiptSchema", probeSchema);
        forgedReport.put("typedSchema", typedSchema);
        forgedReport.put("schemaDigest", sha256(typedSchema));
        return forgedReport;
    }

    private String sha256(Map<String, Object> value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String canonical(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put(String.valueOf(key), item));
            StringBuilder builder = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<String, Object> entry : sorted.entrySet()) {
                if (!first) {
                    builder.append(",");
                }
                first = false;
                builder.append(escape(entry.getKey())).append("=").append(canonical(entry.getValue()));
            }
            return builder.append("}").toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    builder.append(",");
                }
                builder.append(canonical(list.get(i)));
            }
            return builder.append("]").toString();
        }
        return escape(value.toString());
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private Map<String, Object> probeExecutorReport(Map<String, Object> audit,
                                                    Map<String, Object> principal) {
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
        Map<String, Object> boundaryReport = writerBoundaryReport(
            audit,
            principal,
            writerPlanReport,
            availabilityGateReport
        );
        return NimCreateDurableAuditStorageProbeExecutorSupport.plan(
            new NimCreateDurableAuditStorageProbeExecutorSupport.StorageProbeExecutorInput(
                audit,
                principal,
                availabilityGateReport,
                boundaryReport,
                Map.of()
            )
        );
    }

    private Map<String, Object> receiptSchemaReport(Map<String, Object> audit,
                                                    Map<String, Object> principal) {
        return NimCreateDurableAuditReceiptSchemaSupport.plan(
            new NimCreateDurableAuditReceiptSchemaSupport.DurableAuditReceiptSchemaInput(
                audit,
                principal,
                interfaceSpecReport(audit, principal)
            )
        );
    }

    private Map<String, Object> interfaceSpecReport(Map<String, Object> audit,
                                                    Map<String, Object> principal) {
        return NimCreateDurableAuditWriterInterfaceSpecSupport.plan(
            new NimCreateDurableAuditWriterInterfaceSpecSupport.DurableAuditWriterInterfaceSpecInput(
                audit,
                principal,
                writerBoundaryReport(
                    audit,
                    principal,
                    writerPlanReport(audit, principal),
                    availabilityGateReport(audit, principal, writerPlanReport(audit, principal))
                )
            )
        );
    }

    private Map<String, Object> writerBoundaryReport(Map<String, Object> audit,
                                                     Map<String, Object> principal,
                                                     Map<String, Object> writerPlanReport,
                                                     Map<String, Object> availabilityGateReport) {
        return NimCreateDedicatedDurableAuditWriterBoundarySupport.plan(
            new NimCreateDedicatedDurableAuditWriterBoundarySupport.DedicatedAuditWriterBoundaryInput(
                audit,
                principal,
                writerPlanReport,
                availabilityGateReport
            )
        );
    }

    private Map<String, Object> availabilityGateReport(Map<String, Object> audit,
                                                       Map<String, Object> principal,
                                                       Map<String, Object> writerPlanReport) {
        return NimCreateDurableAuditStorageAvailabilityGateSupport.plan(
            new NimCreateDurableAuditStorageAvailabilityGateSupport.StorageAvailabilityGateInput(
                audit,
                principal,
                writerPlanReport
            )
        );
    }

    private Map<String, Object> writerPlanReport(Map<String, Object> audit,
                                                 Map<String, Object> principal) {
        return NimCreateDurableAuditWriterPlanSupport.plan(
            new NimCreateDurableAuditWriterPlanSupport.DurableAuditWriterPlanInput(
                audit,
                principal,
                storageCandidateReport(audit, principal)
            )
        );
    }

    private Map<String, Object> storageCandidateReport(Map<String, Object> audit,
                                                       Map<String, Object> principal) {
        return NimCreateDurableAuditStorageSupport.prepare(
            new NimCreateDurableAuditStorageSupport.DurableAuditStorageInput(
                audit,
                principal
            )
        );
    }

    private Map<String, Object> completeAuditContext() {
        return Map.ofEntries(
            entry("auditPrepared", true),
            entry("auditEventType", NimCreateAuditReadinessSupport.AUDIT_EVENT_TYPE),
            entry("requestId", "req-1"),
            entry("conversationId", "conv-1"),
            entry("userId", "user-1"),
            entry("organizationId", "100002"),
            entry("targetTool", "nim_create"),
            entry("targetIntent", "nim_create"),
            entry("operationType", "CREATE"),
            entry("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT),
            entry("writeBodyProvenance", NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE),
            entry("displayName", "llama-nim"),
            entry("image", "nvcr.io/nim/llama:1.0"),
            entry("templateId", "88"),
            entry("secretRedactionApplied", true),
            entry("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY)
        );
    }

    private Map<String, Object> trustedPrincipalSnapshot() {
        return Map.of(
            "authoritative", true,
            "source", "SERVER_SESSION_CONTEXT",
            "protectedFromCallerParams", true,
            "organizationId", "100002",
            "userId", "user-1",
            "username", "alice"
        );
    }

    private void assertHasBlocker(List<Map<String, Object>> blockers, String code) {
        assertTrue(blockers.stream().anyMatch(item -> code.equals(item.get("code"))),
            "expected blocker code: " + code + ", actual blockers: " + blockers);
    }
}
