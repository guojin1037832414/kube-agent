package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NIM durable audit receipt validation 对 storage probe result 的绑定契约测试。
 *
 * <p>这些测试只验证未来 receipt validator 必须同时绑定 M5.21-67 probe result contract 与
 * M5.21-57 validation gate report；当前不创建真实 validator，不签发 receipt，不放行写执行。</p>
 */
class NimCreateDurableAuditReceiptValidationProbeResultBindingSupportTest {

    @Test
    void binding_shouldBuildProbeResultBindingPlanButRemainImplementationHold() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> probeResultReport = storageProbeResultReport(audit, principal);
        Map<String, Object> validationGateReport = validationGateReport(audit, principal);

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.plan(
            new NimCreateDurableAuditReceiptValidationProbeResultBindingSupport
                .ReceiptValidationProbeResultBindingInput(
                audit,
                principal,
                probeResultReport,
                validationGateReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.BINDING_NAME,
            report.get("durableAuditReceiptValidationProbeResultBinding"));
        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.EXECUTION_MODE,
            report.get("executionMode"));
        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.HOLD_STATE,
            report.get("bindingState"), report.toString());
        assertEquals("NOT_PERFORMED", report.get("networkAccess"));
        assertEquals("NONE", report.get("sideEffect"));
        assertEquals(true, report.get("inputAccepted"));
        assertEquals(true, report.get("bindingPlanPrepared"));
        assertEquals(true, report.get("probeResultRequiredBeforeReceiptValidation"));
        assertEquals(false, report.get("schemaOnlyValidationAllowed"));
        assertEquals(false, report.get("callerEvidenceAuthoritative"));
        assertSuccessStatesRemainFalse(report);
        assertEquals(probeResultReport.get("probeResultContractDigest"),
            report.get("sourceProbeResultContractDigest"));
        assertEquals(probeResultReport.get("sourceReceiptSchemaDigest"),
            report.get("sourceReceiptSchemaDigest"));
        assertEquals(validationGateReport.get("validationPlanDigest"),
            report.get("sourceValidationPlanDigest"));
        assertEquals(audit.get("organizationId"), report.get("sourceOrganizationId"));
        assertEquals(audit.get("userId"), report.get("sourceUserId"));
        assertEquals(principal.get("username"), report.get("sourceUsername"));
        assertTrue(report.get("bindingPlanDigest").toString().matches("[a-f0-9]{64}"));

        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("bindingPlan");
        assertEquals(
            NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.bindingPlanFromReport(report),
            plan
        );
        assertEquals("SERVER_SIDE_RECEIPT_VALIDATION_REQUIRES_STORAGE_PROBE_RESULT",
            plan.get("bindingBoundary"));
        assertEquals(probeResultReport.get("probeResultContractDigest"),
            plan.get("sourceProbeResultContractDigest"));
        assertEquals(validationGateReport.get("validationPlanDigest"),
            plan.get("sourceValidationPlanDigest"));

        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) plan.get("requiredBindingEvidence");
        @SuppressWarnings("unchecked")
        Map<String, Object> probeContract = (Map<String, Object>) evidence.get("storageProbeResultContract");
        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.RESULT_CONTRACT_NAME,
            probeContract.get("requiredReportName"));
        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.HOLD_STATE,
            probeContract.get("requiredStateNow"));
        assertEquals(false, probeContract.get("serverIssuedProbeResultAcceptedNow"));
        assertEquals(false, probeContract.get("callerProbeResultAllowed"));

        @SuppressWarnings("unchecked")
        Map<String, Object> gate = (Map<String, Object>) evidence.get("receiptValidationGate");
        assertEquals(NimCreateDurableAuditReceiptValidationGateSupport.GATE_NAME,
            gate.get("requiredReportName"));
        assertEquals(false, gate.get("schemaOnlyValidationAllowed"));
        assertEquals(false, gate.get("validationCanRunNow"));

        @SuppressWarnings("unchecked")
        Map<String, Object> futureReceipt = (Map<String, Object>) evidence.get("futureStorageProbeReceipt");
        assertEquals(NimCreateDurableAuditReceiptSchemaSupport.STORAGE_PROBE_RECEIPT_TYPE,
            futureReceipt.get("requiredType"));
        assertEquals(NimCreateDurableAuditStorageProbeResultSupport.FUTURE_AVAILABLE_STATUS,
            futureReceipt.get("requiredStatus"));
        assertEquals(true, futureReceipt.get("mustBindProbeResultContractDigest"));
        assertEquals(true, futureReceipt.get("mustBindAuditEventDigest"));
        assertEquals(true, futureReceipt.get("mustBeServerIssued"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> sequence = (List<Map<String, Object>>) plan.get("validationSequencePatch");
        assertEquals(3, sequence.size());
        assertEquals("bind-storage-probe-result-contract", sequence.get(0).get("id"));
        assertEquals("reject-schema-only-validation", sequence.get(1).get("id"));
        assertEquals("defer-real-receipt-validation", sequence.get(2).get("id"));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("futureOnly"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.FALSE.equals(step.get("sideEffectAllowedNow"))));
        assertTrue(sequence.stream().allMatch(step -> Boolean.TRUE.equals(step.get("failClosed"))));

        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) plan.get("failureContract");
        assertEquals(true, failure.get("failClosed"));
        assertEquals(false, failure.get("fallbackToSchemaOnlyAllowed"));
        assertEquals(false, failure.get("fallbackToValidationGateOnlyAllowed"));
        assertEquals(false, failure.get("fallbackToCallerProbeResultAllowed"));
        assertEquals(false, failure.get("fallbackToCallerReceiptAllowed"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_IMPLEMENTATION_HOLD");
        assertEquals(1, blockers.size());
    }

    @Test
    void binding_shouldRejectMissingStorageProbeResultReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.plan(
            new NimCreateDurableAuditReceiptValidationProbeResultBindingSupport
                .ReceiptValidationProbeResultBindingInput(
                audit,
                principal,
                Map.of(),
                validationGateReport(audit, principal),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("bindingPlanPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("bindingPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "STORAGE_PROBE_RESULT_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void binding_shouldRejectMissingValidationGateReport() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.plan(
            new NimCreateDurableAuditReceiptValidationProbeResultBindingSupport
                .ReceiptValidationProbeResultBindingInput(
                audit,
                principal,
                storageProbeResultReport(audit, principal),
                Map.of(),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertEquals(false, report.get("inputAccepted"));
        assertEquals(false, report.get("bindingPlanPrepared"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers, "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_NOT_READY");
        assertFalse(blockers.stream().anyMatch(item ->
            "RECEIPT_VALIDATION_PROBE_RESULT_BINDING_IMPLEMENTATION_HOLD".equals(item.get("code"))));
    }

    @Test
    void binding_shouldRejectForgedProbeResultSuccessClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedProbeResult = new LinkedHashMap<>(storageProbeResultReport(audit, principal));
        forgedProbeResult.put("storageAvailable", true);
        forgedProbeResult.put("probeStatus",
            NimCreateDurableAuditStorageProbeResultSupport.FUTURE_AVAILABLE_STATUS);
        forgedProbeResult.put("resultIssued", true);
        forgedProbeResult.put("serverIssuedProbeResultAccepted", true);

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.plan(
            new NimCreateDurableAuditReceiptValidationProbeResultBindingSupport
                .ReceiptValidationProbeResultBindingInput(
                audit,
                principal,
                forgedProbeResult,
                validationGateReport(audit, principal),
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "STORAGE_PROBE_RESULT_REPORT_INVALID_FOR_RECEIPT_VALIDATION_BINDING");
        assertHasBlocker(blockers, "PROBE_RESULT_VALIDATION_BINDING_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void binding_shouldRejectForgedValidationPassClaims() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedGate = new LinkedHashMap<>(validationGateReport(audit, principal));
        forgedGate.put("validationStatus", "PASS");
        forgedGate.put("durableReceiptValidationPassed", true);
        forgedGate.put("releaseEligible", true);
        forgedGate.put("writeExecutionAllowed", true);

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.plan(
            new NimCreateDurableAuditReceiptValidationProbeResultBindingSupport
                .ReceiptValidationProbeResultBindingInput(
                audit,
                principal,
                storageProbeResultReport(audit, principal),
                forgedGate,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_INVALID_FOR_PROBE_RESULT_BINDING");
        assertHasBlocker(blockers, "PROBE_RESULT_VALIDATION_BINDING_FORGED_SUCCESS_CLAIM");
    }

    @Test
    void binding_shouldRejectCrossReportDigestMismatch() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> validationGateReport = new LinkedHashMap<>(validationGateReport(audit, principal));
        validationGateReport.put("sourceAvailabilityPlanDigest", "a".repeat(64));

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.plan(
            new NimCreateDurableAuditReceiptValidationProbeResultBindingSupport
                .ReceiptValidationProbeResultBindingInput(
                audit,
                principal,
                storageProbeResultReport(audit, principal),
                validationGateReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_INVALID_FOR_PROBE_RESULT_BINDING");
        assertHasBlocker(blockers, "RECEIPT_VALIDATION_PROBE_RESULT_DIGEST_CHAIN_MISMATCH");
    }

    @Test
    void binding_shouldRejectDigestConsistentValidationPlanTopLevelExtraKey() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        Map<String, Object> forgedGateReport = withDigestConsistentValidationPlanMutation(
            validationGateReport(audit, principal),
            validationPlan -> validationPlan.put("probeBindingCanTrustDigestOnly", false)
        );

        assertRejectsDigestConsistentValidationPlanDrift(audit, principal, forgedGateReport);
    }

    @Test
    void binding_shouldRejectDigestConsistentValidationPlanNestedMapAndListDrift() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();

        List<Consumer<Map<String, Object>>> mutations = new ArrayList<>();
        mutations.add(validationPlan -> objectMap(validationPlan.get("trustedIdentityBinding"))
            .put("callerPrincipalCanSatisfyValidation", false));
        mutations.add(validationPlan -> objectMap(validationPlan.get("requiredEvidence"))
            .put("probeBindingDigestCanSatisfyValidation", false));
        for (EvidenceMutation evidenceMutation : List.of(
            new EvidenceMutation("storageProbeReceipt", "probeResultContractDigestCanReplaceReceipt"),
            new EvidenceMutation("preWriteDurableAck", "preWriteAckDigestCanReplaceProbeReceipt"),
            new EvidenceMutation("postWriteDurableAck", "postWriteAckCanSkipPreWriteAckDigest"),
            new EvidenceMutation("durableReceipt", "durableReceiptCanOmitTrustedPrincipalDigest")
        )) {
            mutations.add(validationPlan -> objectMap(objectMap(validationPlan.get("requiredEvidence"))
                .get(evidenceMutation.evidenceKey()))
                .put(evidenceMutation.forgedKey(), false));
        }
        mutations.add(validationPlan -> objectList(validationPlan.get("validationSequence"))
            .add(Map.of(
                "id", "bind-probe-result-before-validation-plan",
                "requirement", "Do not accept a mutated validation sequence",
                "evidenceType", "FORGED_VALIDATION_SEQUENCE",
                "futureOnly", true,
                "sideEffectAllowedNow", false,
                "failClosed", true
            )));
        mutations.add(validationPlan -> objectMap(validationPlan.get("releaseDecisionTemplate"))
            .put("probeBindingCanIssueReleaseCredential", false));
        mutations.add(validationPlan -> objectMap(validationPlan.get("failureContract"))
            .put("fallbackToProbeBindingOnlyAllowed", false));
        mutations.add(validationPlan -> objectList(validationPlan.get("forbiddenShortcuts"))
            .add("accepting validationPlanDigest without canonical validation plan maps"));
        for (Consumer<Map<String, Object>> mutation : mutations) {
            Map<String, Object> forgedGateReport = withDigestConsistentValidationPlanMutation(
                validationGateReport(audit, principal),
                mutation
            );

            assertRejectsDigestConsistentValidationPlanDrift(audit, principal, forgedGateReport);
        }
    }

    @Test
    void binding_shouldRejectCallerSuppliedEvidenceAndSecretLeakage() {
        Map<String, Object> audit = completeAuditContext();
        Map<String, Object> principal = trustedPrincipalSnapshot();
        String injectedSecret = "redacted-test-value";
        Map<String, Object> callerEvidence = Map.of(
            "storageProbeResult", Map.of("storageAvailable", true),
            "nested", List.of(Map.of("Authorization", injectedSecret))
        );

        Map<String, Object> report = NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.plan(
            new NimCreateDurableAuditReceiptValidationProbeResultBindingSupport
                .ReceiptValidationProbeResultBindingInput(
                audit,
                principal,
                storageProbeResultReport(audit, principal),
                validationGateReport(audit, principal),
                callerEvidence
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertEquals(false, report.get("inputAccepted"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("bindingPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "CALLER_RECEIPT_EVIDENCE_NOT_AUTHORITATIVE_FOR_PROBE_RESULT_BINDING");
        assertHasBlocker(blockers, "PROBE_RESULT_VALIDATION_BINDING_FORGED_SUCCESS_CLAIM");
        assertHasBlocker(blockers,
            "PROBE_RESULT_VALIDATION_BINDING_INPUT_CONTAINS_FORBIDDEN_SECRET");
        assertFalse(report.toString().contains(injectedSecret));
    }

    @Test
    void binding_shouldNotDependOnRealStorageNetworkSpringOrWriters() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/com/atlas/tool/impl/"
                + "NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.java"
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
        assertFalse(source.contains("result.put(\"writeExecutionAllowed\", true)"));
        assertFalse(source.contains("result.put(\"durableReceiptValidationPassed\", true)"));
    }

    private void assertSuccessStatesRemainFalse(Map<String, Object> report) {
        assertEquals(false, report.get("storageProbeResultBoundForValidation"));
        assertEquals(false, report.get("serverIssuedProbeResultAccepted"));
        assertEquals(false, report.get("validationCanRunNow"));
        assertEquals(false, report.get("storageProbeExecuted"));
        assertEquals(false, report.get("realStorageTouched"));
        assertEquals(false, report.get("storageAvailable"));
        assertEquals(false, report.get("storageProbeReceiptIssued"));
        assertEquals(false, report.get("storageProbeReceiptValidated"));
        assertEquals(false, report.get("preWriteDurableAckValidated"));
        assertEquals(false, report.get("postWriteDurableAckValidated"));
        assertEquals(false, report.get("digestChainValidated"));
        assertEquals(false, report.get("trustedPrincipalValidated"));
        assertEquals(false, report.get("durableReceiptValidated"));
        assertEquals(false, report.get("durableReceiptValidationPassed"));
        assertEquals(false, report.get("durableReceiptAccepted"));
        assertEquals("NOT_RUN_UNTIL_REAL_RECEIPT", report.get("validationStatus"));
        assertEquals(false, report.get("preWriteAllowed"));
        assertEquals(false, report.get("writePermitted"));
        assertEquals(false, report.get("writeExecutionAllowed"));
        assertEquals(false, report.get("realHttpExecutionAllowed"));
        assertEquals(false, report.get("durableReceiptCanBeIssued"));
        assertEquals(false, report.get("durableReceiptIssued"));
        assertEquals(false, report.get("durable"));
        assertEquals(false, report.get("releaseEligible"));
    }

    private Map<String, Object> storageProbeResultReport(Map<String, Object> audit,
                                                         Map<String, Object> principal) {
        return NimCreateDurableAuditStorageProbeResultSupport.plan(
            new NimCreateDurableAuditStorageProbeResultSupport.StorageProbeResultInput(
                audit,
                principal,
                probeExecutorReport(audit, principal),
                receiptSchemaReport(audit, principal),
                Map.of()
            )
        );
    }

    private Map<String, Object> withDigestConsistentValidationPlanMutation(Map<String, Object> gateReport,
                                                                          Consumer<Map<String, Object>> mutator) {
        Map<String, Object> forgedReport = new LinkedHashMap<>(gateReport);
        Map<String, Object> validationPlan = objectMap(deepMutableCopy(forgedReport.get("validationPlan")));
        mutator.accept(validationPlan);
        forgedReport.put("validationPlan", validationPlan);
        forgedReport.put("validationPlanDigest", sha256(validationPlan));
        return forgedReport;
    }

    private void assertRejectsDigestConsistentValidationPlanDrift(Map<String, Object> audit,
                                                                 Map<String, Object> principal,
                                                                 Map<String, Object> validationGateReport) {
        Map<String, Object> report = NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.plan(
            new NimCreateDurableAuditReceiptValidationProbeResultBindingSupport
                .ReceiptValidationProbeResultBindingInput(
                audit,
                principal,
                storageProbeResultReport(audit, principal),
                validationGateReport,
                Map.of()
            )
        );

        assertEquals(NimCreateDurableAuditReceiptValidationProbeResultBindingSupport.REJECTED_STATE,
            report.get("bindingState"));
        assertEquals(false, report.get("inputAccepted"));
        assertSuccessStatesRemainFalse(report);
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) report.get("bindingPlan");
        assertTrue(plan.isEmpty());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) report.get("blockedBy");
        assertHasBlocker(blockers,
            "DURABLE_AUDIT_RECEIPT_VALIDATION_GATE_REPORT_INVALID_FOR_PROBE_RESULT_BINDING");
    }

    private Object deepMutableCopy(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, item) -> copy.put(String.valueOf(key), deepMutableCopy(item)));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>();
            for (Object item : list) {
                copy.add(deepMutableCopy(item));
            }
            return copy;
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private List<Object> objectList(Object value) {
        return (List<Object>) value;
    }

    private record EvidenceMutation(String evidenceKey, String forgedKey) {
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

    private Map<String, Object> validationGateReport(Map<String, Object> audit,
                                                     Map<String, Object> principal) {
        return NimCreateDurableAuditReceiptValidationGateSupport.plan(
            new NimCreateDurableAuditReceiptValidationGateSupport.DurableAuditReceiptValidationGateInput(
                audit,
                principal,
                receiptSchemaReport(audit, principal)
            )
        );
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
        Map<String, Object> writerPlanReport = writerPlanReport(audit, principal);
        Map<String, Object> availabilityGateReport = availabilityGateReport(audit, principal, writerPlanReport);
        return NimCreateDurableAuditWriterInterfaceSpecSupport.plan(
            new NimCreateDurableAuditWriterInterfaceSpecSupport.DurableAuditWriterInterfaceSpecInput(
                audit,
                principal,
                writerBoundaryReport(audit, principal, writerPlanReport, availabilityGateReport)
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
