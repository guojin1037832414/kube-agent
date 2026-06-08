package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
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
 * NIM durable audit typed ack/receipt schema 契约测试。
 *
 * <p>这些测试只验证未来 ack/receipt 类型的字段、digest chain 和 fail-closed 规则；
 * 不创建真实 writer，不连接 Elasticsearch，不写 sys_log，也不把 schema 当成 durable receipt。</p>
 */
class NimCreateDurableAuditReceiptSchemaSupportTest {

    @Test
    void receiptSchema_shouldBuildTypedAckReceiptContractButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> interfaceSpecReport = interfaceSpecReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditReceiptSchemaSupport.plan(
            new NimCreateDurableAuditReceiptSchemaSupport.DurableAuditReceiptSchemaInput(
                audit,
                principal,
                interfaceSpecReport
            )
        );

        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.SCHEMA_NAME,
            report.get("durableAuditReceiptAckSchema"));
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.EXECUTION_MODE, report.get("executionMode"));
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.HOLD_STATE, report.get("schemaState"));
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE,
            report.get("storageProbeReceiptType"));
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE, report.get("preWriteAckType"));
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.POST_WRITE_ACK_TYPE, report.get("postWriteAckType"));
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE,
            report.get("durableReceiptType"));
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("typedSchemaPrepared"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(false, report.get("storageProbeReceiptIssued"));
        assertEquals(false, report.get("preWritePersisted"));
        assertEquals(false, report.get("postWritePersisted"));
        assertEquals(false, report.get("preWriteDurableAckIssued"));
        assertEquals(false, report.get("postWriteDurableAckIssued"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        assertEquals(false, report.get("durableReceiptIssued"));
        assertEquals(interfaceSpecReport.get("interfaceSpecDigest"), report.get("sourceInterfaceSpecDigest"));
        assertTrue(report.get("schemaDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) report.get("typedSchema");
        assertEquals("FUTURE_TYPED_DURABLE_ACK_RECEIPT_ONLY", schema.get("schemaBoundary"));
        assertEquals("NimDurableAuditWriter", schema.get("futureInterface"));
        assertEquals(interfaceSpecReport.get("interfaceSpecDigest"), schema.get("sourceInterfaceSpecDigest"));
        assertEquals("sys_log", schema.get("targetStorage"));

        @SuppressWarnings("unchecked")
        Map<String, Object> storageProbe =
            (Map<String, Object>) schema.get("storageAvailabilityProbeReceiptSchema");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE,
            storageProbe.get("type"));
        assertEquals("PROBE_STORAGE", storageProbe.get("phase"));
        assertEquals(true, storageProbe.get("futureOnly"));
        assertEquals(false, storageProbe.get("instanceAllowedNow"));
        assertEquals(false, storageProbe.get("sideEffectAllowedNow"));
        assertEquals("STORAGE_AVAILABLE_CONFIRMED", storageProbe.get("requiredFutureStatus"));

        @SuppressWarnings("unchecked")
        Map<String, Object> preAck = (Map<String, Object>) schema.get("preWriteDurableAckSchema");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE, preAck.get("type"));
        assertEquals("PRE_WRITE_INTENT", preAck.get("phase"));
        assertEquals(NimCreateDurableAuditWriterPlanSupport.PRE_WRITE_RECORD_TYPE, preAck.get("recordType"));
        assertEquals("PRE_WRITE_DURABLY_RECORDED", preAck.get("requiredFutureAckStatus"));
        assertEquals("storageProbeReceiptDigest", preAck.get("requiredPreviousDigestField"));
        assertEquals(false, preAck.get("instanceAllowedNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> postAck = (Map<String, Object>) schema.get("postWriteDurableAckSchema");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.POST_WRITE_ACK_TYPE, postAck.get("type"));
        assertEquals("POST_WRITE_RESULT", postAck.get("phase"));
        assertEquals(NimCreateDurableAuditWriterPlanSupport.POST_WRITE_RECORD_TYPE, postAck.get("recordType"));
        assertEquals("POST_WRITE_DURABLY_RECORDED", postAck.get("requiredFutureAckStatus"));
        assertEquals("preWriteDurableAckDigest", postAck.get("requiredPreviousDigestField"));
        assertEquals(false, postAck.get("instanceAllowedNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> receipt = (Map<String, Object>) schema.get("durableAuditReceiptSchema");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.DURABLE_RECEIPT_TYPE, receipt.get("type"));
        assertEquals("ASSEMBLE_RECEIPT", receipt.get("phase"));
        assertEquals(false, receipt.get("instanceAllowedNow"));
        assertEquals(NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS,
            receipt.get("requiredFutureReceiptStatus"));
        assertEquals(NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE,
            receipt.get("requiredFutureStorageMode"));

        @SuppressWarnings("unchecked")
        Map<String, Object> prerequisites = (Map<String, Object>) receipt.get("prerequisites");
        assertEquals(true, prerequisites.get("storageAvailableRequired"));
        assertEquals(true, prerequisites.get("preWriteDurableAckRequired"));
        assertEquals(true, prerequisites.get("postWriteDurableAckRequired"));
        assertEquals(true, prerequisites.get("sameAuditEventDigestRequired"));
        assertEquals(true, prerequisites.get("sameTrustedPrincipalRequired"));
        assertEquals(false, prerequisites.get("mockReceiptAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> digestRules = (Map<String, Object>) schema.get("digestChainRules");
        assertEquals(NimCreateAuditWriterSupport.DIGEST_ALGORITHM, digestRules.get("digestAlgorithm"));
        assertEquals(interfaceSpecReport.get("interfaceSpecDigest"), digestRules.get("sourceInterfaceSpecDigest"));
        assertEquals(true, digestRules.get("canonicalizationRequired"));

        @SuppressWarnings("unchecked")
        Map<String, Object> currentResponse = (Map<String, Object>) schema.get("currentResponseTemplate");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.HOLD_STATE, currentResponse.get("status"));
        assertEquals(false, currentResponse.get("storageProbeReceiptIssued"));
        assertEquals(false, currentResponse.get("preWriteDurableAckIssued"));
        assertEquals(false, currentResponse.get("postWriteDurableAckIssued"));
        assertEquals(false, currentResponse.get("durableReceiptIssued"));
        assertEquals("NOT_ISSUED", currentResponse.get("receiptStatus"));
        assertEquals("NONE", currentResponse.get("storageMode"));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) schema.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToMockReceiptAllowed"));
        assertEquals(false, failure.get("fallbackToSchemaOnlyAllowed"));
        @SuppressWarnings("unchecked")
        List<String> failureStatuses = (List<String>) failure.get("failureStatuses");
        assertTrue(failureStatuses.contains("STORAGE_PROBE_RECEIPT_MISSING"));
        assertTrue(failureStatuses.contains("PRE_WRITE_ACK_DIGEST_MISMATCH"));
        assertTrue(failureStatuses.contains("POST_WRITE_ACK_DIGEST_MISMATCH"));
        assertTrue(failureStatuses.contains("DURABLE_RECEIPT_DIGEST_CHAIN_MISMATCH"));

        @SuppressWarnings("unchecked")
        Map<String, Object> testDoubleRules = (Map<String, Object>) schema.get("testDoubleRules");
        assertEquals("UNIT_CONTRACT_ONLY", testDoubleRules.get("testDoubleScope"));
        assertEquals(interfaceSpecReport.get("interfaceSpecDigest"),
            testDoubleRules.get("sourceInterfaceSpecDigest"));
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.HOLD_STATE,
            testDoubleRules.get("mayReturnStatus"));
        @SuppressWarnings("unchecked")
        List<String> forbiddenSuccessClaims = (List<String>) testDoubleRules.get("forbiddenSuccessClaims");
        assertTrue(forbiddenSuccessClaims.contains("DurableAuditReceipt.receiptStatus=DURABLE_RECORDED"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void receiptSchema_shouldRejectMissingInterfaceSpecReport() {
        Map<String, Object> report = NimCreateDurableAuditReceiptSchemaSupport.plan(
            new NimCreateDurableAuditReceiptSchemaSupport.DurableAuditReceiptSchemaInput(
                completeAuditContext(),
                trustedPrincipalSnapshot(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.REJECTED_STATE, report.get("schemaState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("typedSchemaPrepared"));
        assertEquals(false, report.get("durableReceiptIssued"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) report.get("typedSchema");
        assertTrue(schema.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_INTERFACE_SPEC_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "DURABLE_AUDIT_RECEIPT_ACK_SCHEMA_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void receiptSchema_shouldRejectForgedTypedAckAndReceiptClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedInterfaceSpecReport = new LinkedHashMap<>(interfaceSpecReport(audit, principal));
        forgedInterfaceSpecReport.put("preWriteDurableAckIssued", true);
        forgedInterfaceSpecReport.put("postWriteDurableAckIssued", true);
        forgedInterfaceSpecReport.put("durableReceiptIssued", true);
        forgedInterfaceSpecReport.put("durableReceipt", Map.of(
            "receiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS,
            "storageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE
        ));

        Map<String, Object> report = NimCreateDurableAuditReceiptSchemaSupport.plan(
            new NimCreateDurableAuditReceiptSchemaSupport.DurableAuditReceiptSchemaInput(
                audit,
                principal,
                forgedInterfaceSpecReport
            )
        );

        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.REJECTED_STATE, report.get("schemaState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("preWriteDurableAckIssued"));
        assertEquals(false, report.get("postWriteDurableAckIssued"));
        assertEquals(false, report.get("durableReceiptIssued"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) report.get("typedSchema");
        assertTrue(schema.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_INTERFACE_SPEC_REPORT_INVALID_FOR_RECEIPT_SCHEMA");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_SCHEMA_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void receiptSchema_shouldRejectDigestConsistentInterfaceSpecExtraRequiredLists() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        for (String contractKey : List.of("requestContract", "responseContract")) {
            Map<String, Object> forgedInterfaceSpecReport = withDigestConsistentExtraInterfaceSpecRequiredField(
                interfaceSpecReport(audit, principal),
                contractKey,
                contractKey.equals("requestContract") ? "futureCallerProofEnvelope" : "futureReceiptSignerEvidence"
            );

            Map<String, Object> report = NimCreateDurableAuditReceiptSchemaSupport.plan(
                new NimCreateDurableAuditReceiptSchemaSupport.DurableAuditReceiptSchemaInput(
                    audit,
                    principal,
                    forgedInterfaceSpecReport
                )
            );

            assertEquals(NimCreateDurableAuditReceiptSchemaSupport.REJECTED_STATE,
                report.get("schemaState"), "contractKey=" + contractKey);
            assertEquals(false, report.get("inputAccepted"), "contractKey=" + contractKey);
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) report.get("typedSchema");
            assertTrue(schema.isEmpty(), "contractKey=" + contractKey);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
            assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_INTERFACE_SPEC_REPORT_INVALID_FOR_RECEIPT_SCHEMA");
        }
    }

    @Test
    void receiptSchema_shouldRejectDigestConsistentInterfaceSpecExtraFailureOrTestDoubleLists() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        for (String contractKey : List.of("failureContract", "testDoubleRules")) {
            Map<String, Object> forgedInterfaceSpecReport = withDigestConsistentExtraInterfaceSpecListField(
                interfaceSpecReport(audit, principal),
                contractKey,
                contractKey.equals("failureContract") ? "failureStatuses" : "forbiddenSuccessClaims",
                contractKey.equals("failureContract") ? "FUTURE_SIGNER_NOT_READY" : "releaseEligible=true"
            );

            Map<String, Object> report = NimCreateDurableAuditReceiptSchemaSupport.plan(
                new NimCreateDurableAuditReceiptSchemaSupport.DurableAuditReceiptSchemaInput(
                    audit,
                    principal,
                    forgedInterfaceSpecReport
                )
            );

            assertEquals(NimCreateDurableAuditReceiptSchemaSupport.REJECTED_STATE,
                report.get("schemaState"), "contractKey=" + contractKey);
            assertEquals(false, report.get("inputAccepted"), "contractKey=" + contractKey);
            @SuppressWarnings("unchecked")
            Map<String, Object> schema = (Map<String, Object>) report.get("typedSchema");
            assertTrue(schema.isEmpty(), "contractKey=" + contractKey);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
            assertHasBlocker(blockers, "DURABLE_AUDIT_WRITER_INTERFACE_SPEC_REPORT_INVALID_FOR_RECEIPT_SCHEMA");
        }
    }

    @Test
    void receiptSchema_shouldRejectEvenEmptyCallerSuppliedTypedAckInstance() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedInterfaceSpecReport = new LinkedHashMap<>(interfaceSpecReport(audit, principal));
        forgedInterfaceSpecReport.put("preWriteDurableAck", Map.of(
            "ackType", NimCreateDurableAuditReceiptSchemaSupport.PRE_WRITE_ACK_TYPE
        ));

        Map<String, Object> report = NimCreateDurableAuditReceiptSchemaSupport.plan(
            new NimCreateDurableAuditReceiptSchemaSupport.DurableAuditReceiptSchemaInput(
                audit,
                principal,
                forgedInterfaceSpecReport
            )
        );

        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.REJECTED_STATE, report.get("schemaState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("preWriteDurableAckIssued"));
        assertEquals(false, report.get("durableReceiptIssued"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_SCHEMA_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void receiptSchema_shouldRejectSecretLeakageBeforeAnySchema() {
        Map<String, Object> audit = new LinkedHashMap<>(completeAuditContext());
        audit.put("Authorization", "redacted-test-value");
        Map<String, Object> cleanAudit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditReceiptSchemaSupport.plan(
            new NimCreateDurableAuditReceiptSchemaSupport.DurableAuditReceiptSchemaInput(
                audit,
                principal,
                interfaceSpecReport(cleanAudit, principal)
            )
        );

        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.REJECTED_STATE, report.get("schemaState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageProbeReceiptIssued"));
        assertEquals(false, report.get("preWriteDurableAckIssued"));
        assertEquals(false, report.get("postWriteDurableAckIssued"));
        assertEquals(false, report.get("durableReceiptIssued"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) report.get("typedSchema");
        assertTrue(schema.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_SCHEMA_INPUT_CONTAINS_FORBIDDEN_SECRET");
    }

    private Map<String, Object> interfaceSpecReport(Map<String, Object> audit,
                                                    Map<String, Object> principal) {
        return NimCreateDurableAuditWriterInterfaceSpecSupport.plan(
            new NimCreateDurableAuditWriterInterfaceSpecSupport.DurableAuditWriterInterfaceSpecInput(
                audit,
                principal,
                boundaryReport(audit, principal)
            )
        );
    }

    private Map<String, Object> withDigestConsistentExtraInterfaceSpecRequiredField(Map<String, Object> interfaceSpecReport,
                                                                                   String contractKey,
                                                                                   String forgedField) {
        String listKey = contractKey.equals("requestContract")
            ? "requiredFields"
            : "requiredFutureSuccessFields";
        return withDigestConsistentExtraInterfaceSpecListField(
            interfaceSpecReport,
            contractKey,
            listKey,
            forgedField
        );
    }

    private Map<String, Object> withDigestConsistentExtraInterfaceSpecListField(Map<String, Object> interfaceSpecReport,
                                                                               String contractKey,
                                                                               String listKey,
                                                                               String forgedField) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(interfaceSpecReport);
        @SuppressWarnings("unchecked")
        Map<String, Object> interfaceSpec = new LinkedHashMap<>(
            (Map<String, Object>) forgedReport.get("interfaceSpec")
        );
        @SuppressWarnings("unchecked")
        Map<String, Object> contract = new LinkedHashMap<>((Map<String, Object>) interfaceSpec.get(contractKey));
        @SuppressWarnings("unchecked")
        List<String> fields = new ArrayList<>((List<String>) contract.get(listKey));
        fields.add(forgedField);
        contract.put(listKey, fields);
        interfaceSpec.put(contractKey, contract);
        forgedReport.put("interfaceSpec", interfaceSpec);
        forgedReport.put("interfaceSpecDigest", sha256(interfaceSpec));
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

    private Map<String, Object> boundaryReport(Map<String, Object> audit,
                                               Map<String, Object> principal) {
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
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
