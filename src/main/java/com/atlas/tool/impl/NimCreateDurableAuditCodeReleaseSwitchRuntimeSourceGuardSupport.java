package com.atlas.tool.impl;

import com.atlas.tool.core.NimForbiddenSecretMaterialDetector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * NIM code release switch runtime source guard matrix.
 *
 * <p>This contract-only layer sits after the M5.21-73 runtime-binding report. It records which
 * evidence sources can be used only as planning/shape evidence and which sources must never become
 * a write-release credential. It does not install a real switch, bind clients, touch storage, or
 * execute any kube-manager write.</p>
 */
final class NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport {

    static final String SOURCE_GUARD_CONTRACT_NAME =
        "NIM_CREATE_CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_MATRIX";
    static final String EXECUTION_MODE =
        "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String SWITCH_LOCKED = "LOCKED_UNTIL_REVIEWED_CODE_RELEASE_SWITCH";
    private static final String REQUIRED_SWITCH_STATE = "OPEN_FOR_NIM_CREATE_WRITE_EXECUTION";
    private NimCreateDurableAuditCodeReleaseSwitchRuntimeSourceGuardSupport() {
    }

    static Map<String, Object> plan(RuntimeSourceGuardInput input) {
        RuntimeSourceGuardInput safeInput = input == null ? RuntimeSourceGuardInput.empty() : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> runtimeBindingReport = safeInput.codeReleaseSwitchRuntimeBindingReport();
        Map<String, Object> candidateSourceEvidence = safeInput.candidateSourceEvidence();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateRuntimeBindingReport(auditContext, principal, runtimeBindingReport, blockers);
        validateCandidateSourceEvidence(candidateSourceEvidence, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("codeReleaseSwitchRuntimeBindingReport", runtimeBindingReport, blockers);
        validateNoSecretMaterial("candidateSourceEvidence", candidateSourceEvidence, blockers);

        boolean inputAccepted = blockers.isEmpty();
        List<Map<String, Object>> sourceGuardMatrix = inputAccepted
            ? sourceGuardMatrix(runtimeBindingReport)
            : List.of();
        Map<String, Object> sourceGuardContract = inputAccepted
            ? sourceGuardContract(auditContext, principal, runtimeBindingReport, sourceGuardMatrix)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_IMPLEMENTATION_HOLD",
                "runtime switch source guard matrix is defined, but no reviewed server-owned open switch source exists yet.",
                "code-release-switch-runtime-source-guard"
            ))
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("codeReleaseSwitchRuntimeSourceGuard", SOURCE_GUARD_CONTRACT_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("guardState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("springBeanRegistered", false);
        result.put("httpClientBound", false);
        result.put("storageClientBound", false);
        result.put("toolRegistered", false);
        result.put("controllerRegistered", false);
        result.put("inputAccepted", inputAccepted);
        result.put("sourceGuardMatrixPrepared", inputAccepted);
        result.put("runtimeBindingReportRequired", true);
        result.put("runtimeBindingDigestRecomputed", inputAccepted);
        result.put("sourceGuardInstalled", false);
        result.put("candidateSourceEvidenceAuthoritative", false);
        result.put("callerParamSourceAllowed", false);
        result.put("llmJsonSourceAllowed", false);
        result.put("environmentVariableSourceAllowed", false);
        result.put("runtimeFlagSourceAllowed", false);
        result.put("stateMachineBooleanSourceAllowed", false);
        result.put("durableExecutorSuccessSourceAllowed", false);
        result.put("backendQuerySourceAllowedForRelease", false);
        result.put("sysLogBackfillSourceAllowed", false);
        result.put("releaseDecisionContractReportSourceAllowed", false);
        result.put("validationResultContractReportSourceAllowed", false);
        result.put("serverOwnedOpenSwitchRequired", true);
        result.put("reviewedCodeSwitchDigestRequired", true);
        result.put("stateMachineDigestRecheckRequired", true);
        result.put("durableExecutorDigestRecheckRequired", true);
        result.put("acceptedSourcesForCurrentRelease", List.of());
        result.put("contractShapeSourcesAcceptedForPlanning", contractShapeSourcesAcceptedForPlanning());
        result.put("forbiddenReleaseSources", forbiddenReleaseSources());
        result.put("dangerousReleaseCredentialFieldNames", dangerousReleaseCredentialFieldNames());
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("trustedPrincipalDigest", digestFor(principal));
        result.put("sourceRuntimeBindingContractDigest",
            text(runtimeBindingReport.get("runtimeBindingContractDigest")));
        result.put("sourceCodeReleaseSwitchContractDigest",
            text(runtimeBindingReport.get("sourceCodeReleaseSwitchContractDigest")));
        result.put("sourceGuardMatrixDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("sourceGuardMatrixDigest", inputAccepted ? digestFor(sourceGuardContract) : "");
        result.put("sourceGuardContract", sourceGuardContract);
        result.put("sourceGuardMatrix", sourceGuardMatrix);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCandidateClaims", ignoredCandidateClaims(candidateSourceEvidence));
        result.put("nextImplementationRequirements", List.of(
            "implement reviewed server-owned open switch source after code review, tests, security approval and change window exist",
            "make state machine accept only source-guard-approved switch evidence plus recomputed switch digest",
            "make durable executor re-check the same source-guard-approved switch digest immediately before POST",
            "keep caller params, LLM JSON, environment flags, runtime flags, state-machine booleans and executor success non-authoritative",
            "keep local backend query/read evidence out of nim_create write-release authorization"
        ));
        return result;
    }

    private static Map<String, Object> sourceGuardContract(Map<String, Object> auditContext,
                                                           Map<String, Object> principal,
                                                           Map<String, Object> runtimeBindingReport,
                                                           List<Map<String, Object>> sourceGuardMatrix) {
        Map<String, Object> contract = new LinkedHashMap<>();
        contract.put("contractBoundary", "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_REQUIRED");
        contract.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        contract.put("futureOnly", true);
        contract.put("instanceAllowedNow", false);
        contract.put("sourceRuntimeBindingContract",
            NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.BINDING_CONTRACT_NAME);
        contract.put("sourceRuntimeBindingContractDigest",
            text(runtimeBindingReport.get("runtimeBindingContractDigest")));
        contract.put("sourceCodeReleaseSwitchContractDigest",
            text(runtimeBindingReport.get("sourceCodeReleaseSwitchContractDigest")));
        contract.put("sourceAuditEventDigest", digestFor(auditContext));
        contract.put("trustedPrincipalDigest", digestFor(principal));
        contract.put("currentAcceptedSourceScope", "PLANNING_AND_GUARD_ONLY");
        contract.put("acceptedSourcesForCurrentRelease", List.of());
        contract.put("contractShapeSourcesAcceptedForPlanning", contractShapeSourcesAcceptedForPlanning());
        contract.put("dangerousReleaseCredentialFieldNames", dangerousReleaseCredentialFieldNames());
        contract.put("sourceGuardMatrix", sourceGuardMatrix);
        contract.put("acceptanceRules", acceptanceRules());
        contract.put("failureContract", failureContract());
        contract.put("forbiddenShortcuts", forbiddenShortcuts());
        return contract;
    }

    private static List<Map<String, Object>> sourceGuardMatrix(Map<String, Object> runtimeBindingReport) {
        List<Map<String, Object>> matrix = new ArrayList<>();
        matrix.add(sourceRow(
            "M5.21-72_CODE_RELEASE_SWITCH_CONTRACT_REPORT",
            "CONTRACT_SHAPE_ONLY",
            true,
            false,
            false,
            List.of("codeReleaseSwitchContractDigest", "sourceAuditEventDigest", "trustedPrincipalDigest"),
            "Can prove the future switch contract shape, but cannot open the switch."
        ));
        matrix.add(sourceRow(
            "M5.21-73_RUNTIME_BINDING_REPORT",
            "RUNTIME_BINDING_REQUIREMENT_ONLY",
            true,
            false,
            false,
            List.of("runtimeBindingContractDigest", "sourceCodeReleaseSwitchContractDigest"),
            "Can prove runtime binding requirements, but cannot act as an open switch."
        ));
        matrix.add(sourceRow(
            "REVIEWED_SERVER_OWNED_OPEN_SWITCH",
            "FUTURE_AUTHORITATIVE_SOURCE_REQUIRED",
            false,
            false,
            true,
            List.of(
                "codeReleaseSwitchDigest",
                "codeReviewDigest",
                "testEvidenceDigest",
                "securityApprovalDigest",
                "rollbackPlanDigest",
                "changeWindowDigest",
                "releaseDecisionDigest",
                "validationResultDigest",
                "bodyDigest",
                "requestSpecDigest",
                "handoffDigest",
                "auditReceiptId",
                "serverDerivedIdempotencyKey"
            ),
            "The only future source family that can become authoritative after real implementation and review."
        ));
        matrix.add(sourceRow(
            "CALLER_PARAMS_OR_LLM_JSON",
            "FORBIDDEN_RELEASE_SOURCE",
            false,
            false,
            false,
            List.of(),
            "Caller-visible JSON can be forged and must never become switch-open evidence."
        ));
        matrix.add(sourceRow(
            "ENVIRONMENT_VARIABLE_OR_RUNTIME_FLAG",
            "FORBIDDEN_RELEASE_SOURCE",
            false,
            false,
            false,
            List.of(),
            "Runtime toggles are not reviewed, digest-bound, or tied to the audit event."
        ));
        matrix.add(sourceRow(
            "STATE_MACHINE_WRITE_PERMITTED_BOOLEAN",
            "FORBIDDEN_RELEASE_SOURCE",
            false,
            false,
            false,
            List.of(),
            "The durable executor must re-check switch digest instead of trusting a state-machine boolean."
        ));
        matrix.add(sourceRow(
            "DURABLE_EXECUTOR_SUCCESS_OR_DEPLOYMENT_ID",
            "FORBIDDEN_RELEASE_SOURCE",
            false,
            false,
            false,
            List.of(),
            "Executor success cannot backfill release evidence after the write path."
        ));
        matrix.add(sourceRow(
            "BACKEND_QUERY_OR_READBACK_RESULT",
            "FORBIDDEN_RELEASE_SOURCE_FOR_NIM_CREATE",
            false,
            false,
            false,
            List.of(),
            "Read/query evidence may help diagnostics, but it is not a write-release source."
        ));
        matrix.add(sourceRow(
            "SYS_LOG_OR_ELASTICSEARCH_BACKFILL",
            "FORBIDDEN_RELEASE_SOURCE",
            false,
            false,
            false,
            List.of(),
            "Backfilled storage rows cannot replace the server-owned switch issuer."
        ));
        matrix.add(sourceRow(
            "RELEASE_DECISION_OR_VALIDATION_CONTRACT_REPORT_ONLY",
            "FORBIDDEN_RELEASE_SOURCE",
            false,
            false,
            false,
            List.of(),
            "Value contracts are not server-issued validation/release/open-switch facts."
        ));
        matrix.add(sourceRow(
            "M5.21-73_SOURCE_RUNTIME_BINDING_DIGEST",
            "BOUND_SOURCE_DIGEST",
            true,
            false,
            false,
            List.of(text(runtimeBindingReport.get("runtimeBindingContractDigest"))),
            "The matrix binds to the current runtime-binding digest without treating it as release."
        ));
        return List.copyOf(matrix);
    }

    private static Map<String, Object> sourceRow(String source,
                                                 String scope,
                                                 boolean acceptedForPlanningNow,
                                                 boolean authoritativeForReleaseNow,
                                                 boolean futureAuthoritativeCandidate,
                                                 List<String> requiredCompanionSignals,
                                                 String rationale) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", source);
        row.put("acceptedScope", scope);
        row.put("acceptedForPlanningNow", acceptedForPlanningNow);
        row.put("authoritativeForReleaseNow", authoritativeForReleaseNow);
        row.put("futureAuthoritativeCandidate", futureAuthoritativeCandidate);
        row.put("serverOwnedIssuerRequired", futureAuthoritativeCandidate);
        row.put("digestRecomputeRequired", futureAuthoritativeCandidate || acceptedForPlanningNow);
        row.put("stateMachineRecheckRequired", true);
        row.put("durableExecutorRecheckRequired", true);
        row.put("writePermittedAllowedNow", false);
        row.put("writeExecutionAllowedNow", false);
        row.put("requiredCompanionSignals", requiredCompanionSignals);
        row.put("rationale", rationale);
        return row;
    }

    private static Map<String, Object> acceptanceRules() {
        Map<String, Object> rules = new LinkedHashMap<>();
        rules.put("failClosed", true);
        rules.put("currentReleaseSourceCount", 0);
        rules.put("contractReportAcceptedForRelease", false);
        rules.put("runtimeBindingReportAcceptedForRelease", false);
        rules.put("legacyNimCreateReleasedBooleanAuthoritative", false);
        rules.put("stateMachineWritePermittedAuthoritativeForExecutor", false);
        rules.put("executorSuccessAuthoritativeForSwitch", false);
        rules.put("environmentOrRuntimeOverrideAllowed", false);
        rules.put("backendReadbackAllowedAsReleaseSource", false);
        rules.put("realOpenSwitchIssuerRequired", true);
        return rules;
    }

    private static Map<String, Object> failureContract() {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failClosed", true);
        failure.put("fallbackToCallerParamAllowed", false);
        failure.put("fallbackToLlmJsonAllowed", false);
        failure.put("fallbackToEnvironmentVariableAllowed", false);
        failure.put("fallbackToRuntimeFlagAllowed", false);
        failure.put("fallbackToStateMachineWritePermittedAllowed", false);
        failure.put("fallbackToDurableExecutorSuccessAllowed", false);
        failure.put("fallbackToBackendQueryResultAllowed", false);
        failure.put("fallbackToStorageBackfillAllowed", false);
        failure.put("fallbackToContractReportOnlyAllowed", false);
        return failure;
    }

    private static List<String> contractShapeSourcesAcceptedForPlanning() {
        return List.of(
            "M5.21-72_CODE_RELEASE_SWITCH_CONTRACT_REPORT",
            "M5.21-73_RUNTIME_BINDING_REPORT"
        );
    }

    private static List<String> forbiddenReleaseSources() {
        return List.of(
            "CALLER_PARAMS_OR_LLM_JSON",
            "ENVIRONMENT_VARIABLE_OR_RUNTIME_FLAG",
            "LEGACY_NIM_CREATE_RELEASED_BOOLEAN",
            "STATE_MACHINE_WRITE_PERMITTED_BOOLEAN",
            "DURABLE_EXECUTOR_SUCCESS_OR_DEPLOYMENT_ID",
            "BACKEND_QUERY_OR_READBACK_RESULT",
            "SYS_LOG_OR_ELASTICSEARCH_BACKFILL",
            "RELEASE_DECISION_OR_VALIDATION_CONTRACT_REPORT_ONLY"
        );
    }

    private static List<String> dangerousReleaseCredentialFieldNames() {
        return List.of(
            "nimCreateReleased",
            "codeReleaseSwitchContractReportAccepted",
            "codeReleaseSwitchContractReportAcceptedForRelease",
            "serverOwnedCodeReleaseSwitchAccepted",
            "realCodeReleaseSwitchOpened",
            "codeReleaseSwitchDigestVerified",
            "switchState",
            "codeReleaseSwitchStatus",
            "runtimeFlag",
            "runtimeReleaseFlag",
            "runtimeFlagOverrideAllowed",
            "runtimeToggleOverrideAllowed",
            "environmentOverride",
            "environmentReleaseOverride",
            "environmentVariableOverrideAllowed",
            "writePermitted",
            "writePermittedCanBeTrueNow",
            "writeExecutionAllowed",
            "writeExecutionAllowedNow",
            "releaseEligible",
            "releaseCredential",
            "releaseCredentialIssued",
            "releaseDecisionAccepted",
            "releaseDecisionDigestVerified",
            "validationResultDigestVerified",
            "stateMachineReleaseBound",
            "durableExecutorReleaseBound",
            "writeAttempted",
            "writeExecuted",
            "postWriteReadinessTriggered",
            "deploymentId",
            "deploymentUid",
            "writeResult"
        );
    }

    private static List<String> forbiddenShortcuts() {
        return List.of(
            "treating nimCreateReleased=true as an open code switch",
            "treating codeReleaseSwitchContractReport shape acceptance as release",
            "treating runtimeBindingReport shape acceptance as release",
            "treating state-machine writePermitted=true as durable executor authorization",
            "treating durable executor success as retroactive switch evidence",
            "treating backend readback or storage rows as switch-open evidence"
        );
    }

    private static void validateAuditContext(Map<String, Object> auditContext,
                                             List<Map<String, Object>> blockers) {
        if (auditContext.isEmpty()
            || !Boolean.TRUE.equals(auditContext.get("auditPrepared"))
            || !NimCreateAuditReadinessSupport.AUDIT_EVENT_TYPE.equals(text(auditContext.get("auditEventType")))
            || !NimCreateStateMachineSupport.TARGET_TOOL.equals(text(auditContext.get("targetTool")))
            || !NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE.equals(
                text(auditContext.get("writeBodyProvenance")))
            || !Boolean.TRUE.equals(auditContext.get("secretRedactionApplied"))
            || !NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(auditContext.get("apiKeyHandling")))
            || !hasText(auditContext.get("requestId"))
            || !hasText(auditContext.get("conversationId"))
            || !hasText(auditContext.get("userId"))
            || !integerOrgId(text(auditContext.get("organizationId")))
            || !hasText(auditContext.get("displayName"))
            || !hasText(auditContext.get("image"))
            || !hasText(auditContext.get("templateId"))) {
            blockers.add(blocker(
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD",
                "runtime source guard requires complete redacted NIM_CREATE_REQUEST audit context.",
                "audit-context"
            ));
        }
        if (hasForgedRuntimeSourceClaim(auditContext)) {
            blockers.add(forgedRuntimeSourceClaimBlocker("auditContext"));
        }
    }

    private static void validateTrustedPrincipal(Map<String, Object> auditContext,
                                                 Map<String, Object> principal,
                                                 List<Map<String, Object>> blockers) {
        if (principal.isEmpty()
            || !Boolean.TRUE.equals(principal.get("authoritative"))
            || !"SERVER_SESSION_CONTEXT".equals(text(principal.get("source")))
            || !Boolean.TRUE.equals(principal.get("protectedFromCallerParams"))
            || !text(auditContext.get("organizationId")).equals(text(principal.get("organizationId")))
            || !text(auditContext.get("userId")).equals(text(principal.get("userId")))
            || !hasText(principal.get("username"))) {
            blockers.add(blocker(
                "TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY_FOR_CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD",
                "runtime source guard must bind to a server-session principal, not caller identity claims.",
                "trusted-principal"
            ));
        }
        if (hasForgedRuntimeSourceClaim(principal)) {
            blockers.add(forgedRuntimeSourceClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateRuntimeBindingReport(Map<String, Object> auditContext,
                                                     Map<String, Object> principal,
                                                     Map<String, Object> report,
                                                     List<Map<String, Object>> blockers) {
        if (report.isEmpty()) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_BINDING_REPORT_NOT_READY_FOR_SOURCE_GUARD",
                "Missing M5.21-73 runtime binding report; source guard matrix cannot be defined.",
                "code-release-switch-runtime-binding"
            ));
            return;
        }

        Map<String, Object> contract = objectMap(report.get("runtimeBindingContract"));
        Map<String, Object> stateMachineBinding = objectMap(contract.get("stateMachineRuntimeBinding"));
        Map<String, Object> durableExecutorBinding = objectMap(contract.get("durableExecutorRuntimeBinding"));
        Map<String, Object> template = objectMap(contract.get("currentRuntimeTemplate"));
        Map<String, Object> failure = objectMap(contract.get("failureContract"));
        List<String> requiredFields = stringList(contract.get("requiredFutureRuntimeEvidenceDigestFields"));
        boolean valid = NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.BINDING_CONTRACT_NAME.equals(
                text(report.get("codeReleaseSwitchRuntimeBindingContract")))
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.EXECUTION_MODE.equals(
                text(report.get("executionMode")))
            && NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.HOLD_STATE.equals(
                text(report.get("bindingState")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(report.get("targetTool")))
            && NimCreateAuditReadinessSupport.BACKEND_ENDPOINT.equals(text(report.get("backendEndpoint")))
            && PATH_TEMPLATE.equals(text(report.get("pathTemplate")))
            && "NOT_PERFORMED".equals(text(report.get("networkAccess")))
            && "NONE".equals(text(report.get("sideEffect")))
            && Boolean.TRUE.equals(report.get("inputAccepted"))
            && Boolean.TRUE.equals(report.get("runtimeBindingContractPrepared"))
            && Boolean.TRUE.equals(report.get("codeReleaseSwitchContractReportRequired"))
            && Boolean.TRUE.equals(report.get("stateMachineRuntimeBindingRequired"))
            && Boolean.TRUE.equals(report.get("durableExecutorRuntimeBindingRequired"))
            && Boolean.FALSE.equals(report.get("stateMachineReleaseEvidenceAuthoritative"))
            && Boolean.FALSE.equals(report.get("durableExecutorReleaseEvidenceAuthoritative"))
            && Boolean.TRUE.equals(report.get("serverOwnedOpenSwitchRequired"))
            && Boolean.TRUE.equals(report.get("reviewedCodeSwitchDigestRequired"))
            && Boolean.FALSE.equals(report.get("legacyNimCreateReleasedBooleanAuthoritative"))
            && Boolean.FALSE.equals(report.get("runtimeFlagOverrideAllowed"))
            && Boolean.FALSE.equals(report.get("environmentVariableOverrideAllowed"))
            && runtimeStatesRemainFalse(report)
            && text(report.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && text(report.get("trustedPrincipalDigest")).equals(digestFor(principal))
            && text(report.get("sourceCodeReleaseSwitchContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("runtimeBindingContractDigest")).matches("[a-f0-9]{64}")
            && text(report.get("runtimeBindingContractDigest")).equals(digestFor(contract))
            && hasOnlyBlockerCode(report.get("blockedBy"),
                "CODE_RELEASE_SWITCH_RUNTIME_BINDING_IMPLEMENTATION_HOLD")
            && "CODE_RELEASE_SWITCH_RUNTIME_BINDING_REQUIRED".equals(text(contract.get("contractBoundary")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(contract.get("targetTool")))
            && Boolean.TRUE.equals(contract.get("futureOnly"))
            && Boolean.FALSE.equals(contract.get("instanceAllowedNow"))
            && NimCreateDurableAuditCodeReleaseSwitchContractSupport.SWITCH_CONTRACT_NAME.equals(
                text(contract.get("sourceCodeReleaseSwitchContract")))
            && text(report.get("sourceCodeReleaseSwitchContractDigest")).equals(
                text(contract.get("sourceCodeReleaseSwitchContractDigest")))
            && digestFor(auditContext).equals(text(contract.get("sourceAuditEventDigest")))
            && digestFor(principal).equals(text(contract.get("trustedPrincipalDigest")))
            && stateMachineRuntimeBindingValid(report, stateMachineBinding)
            && durableExecutorRuntimeBindingValid(report, durableExecutorBinding)
            && requiredFields.containsAll(requiredRuntimeEvidenceFields())
            && SWITCH_LOCKED.equals(text(template.get("switchState")))
            && Boolean.FALSE.equals(template.get("codeReleaseSwitchContractReportAccepted"))
            && Boolean.FALSE.equals(template.get("codeReleaseSwitchDigestVerified"))
            && Boolean.FALSE.equals(template.get("stateMachineRuntimeBindingInstalled"))
            && Boolean.FALSE.equals(template.get("durableExecutorRuntimeBindingInstalled"))
            && Boolean.FALSE.equals(template.get("writePermitted"))
            && Boolean.FALSE.equals(template.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(template.get("realHttpExecutionAllowed"))
            && Boolean.TRUE.equals(failure.get("failClosed"))
            && Boolean.FALSE.equals(failure.get("fallbackToCodeReleaseSwitchContractReportAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToCallerRuntimeEvidenceAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToEnvironmentVariableAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToRuntimeFlagAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToNimCreateReleasedBooleanAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToStateMachineWritePermittedAllowed"))
            && Boolean.FALSE.equals(failure.get("fallbackToDurableExecutorSuccessAllowed"));

        if (!valid) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_BINDING_REPORT_INVALID_FOR_SOURCE_GUARD",
                "runtime source guard can only consume the M5.21-73 HOLD report with recomputable digest and no release states.",
                "code-release-switch-runtime-binding"
            ));
        }
        if (hasForgedRuntimeSourceClaim(report)) {
            blockers.add(forgedRuntimeSourceClaimBlocker("codeReleaseSwitchRuntimeBindingReport"));
        }
    }

    private static boolean stateMachineRuntimeBindingValid(Map<String, Object> report,
                                                           Map<String, Object> binding) {
        return NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.TARGET_STATE_MACHINE.equals(
                text(binding.get("target")))
            && "codeReleaseSwitchContractReport".equals(text(binding.get("futureReadinessRequestField")))
            && text(report.get("sourceCodeReleaseSwitchContractDigest")).equals(
                text(binding.get("sourceCodeReleaseSwitchContractDigest")))
            && Boolean.TRUE.equals(binding.get("codeReleaseSwitchContractReportRequired"))
            && Boolean.TRUE.equals(binding.get("codeReleaseSwitchContractDigestRequired"))
            && Boolean.TRUE.equals(binding.get("mustRecomputeCodeReleaseSwitchContractDigest"))
            && Boolean.TRUE.equals(binding.get("mustRequireServerOwnedOpenSwitch"))
            && Boolean.TRUE.equals(binding.get("mustBindServerIssuedReleaseDecisionDigest"))
            && Boolean.TRUE.equals(binding.get("mustBindServerIssuedValidationResultDigest"))
            && Boolean.TRUE.equals(binding.get("mustBindWriteChainDigests"))
            && Boolean.FALSE.equals(binding.get("legacyNimCreateReleasedBooleanAuthoritative"))
            && Boolean.FALSE.equals(binding.get("fallbackToRuntimeFlagAllowed"))
            && Boolean.FALSE.equals(binding.get("fallbackToEnvironmentVariableAllowed"))
            && Boolean.FALSE.equals(binding.get("fallbackToReleaseDecisionContractAllowed"))
            && Boolean.FALSE.equals(binding.get("writePermittedCanBeTrueNow"));
    }

    private static boolean durableExecutorRuntimeBindingValid(Map<String, Object> report,
                                                              Map<String, Object> binding) {
        return NimCreateDurableAuditCodeReleaseSwitchRuntimeBindingSupport.TARGET_DURABLE_EXECUTOR.equals(
                text(binding.get("target")))
            && text(report.get("sourceCodeReleaseSwitchContractDigest")).equals(
                text(binding.get("sourceCodeReleaseSwitchContractDigest")))
            && Boolean.TRUE.equals(binding.get("codeReleaseSwitchDigestRequired"))
            && Boolean.TRUE.equals(binding.get("mustRecheckImmediatelyBeforePost"))
            && Boolean.TRUE.equals(binding.get("mustBindSameHandoffDigest"))
            && Boolean.TRUE.equals(binding.get("mustBindSameRequestSpecDigest"))
            && Boolean.TRUE.equals(binding.get("mustBindSameBodyDigest"))
            && Boolean.TRUE.equals(binding.get("mustBindServerDerivedIdempotencyKey"))
            && Boolean.FALSE.equals(binding.get("fallbackToStateMachineWritePermittedAllowed"))
            && Boolean.FALSE.equals(binding.get("fallbackToStateMachineFlagOnlyAllowed"))
            && Boolean.FALSE.equals(binding.get("fallbackToExecutorSuccessAllowed"))
            && Boolean.FALSE.equals(binding.get("writeExecutionAllowedNow"));
    }

    private static boolean runtimeStatesRemainFalse(Map<String, Object> report) {
        return Boolean.FALSE.equals(report.get("codeReleaseSwitchDigestVerified"))
            && Boolean.FALSE.equals(report.get("codeReviewDigestVerified"))
            && Boolean.FALSE.equals(report.get("testEvidenceDigestVerified"))
            && Boolean.FALSE.equals(report.get("releaseDecisionDigestVerified"))
            && Boolean.FALSE.equals(report.get("validationResultDigestVerified"))
            && Boolean.FALSE.equals(report.get("trustedPrincipalValidated"))
            && Boolean.FALSE.equals(report.get("runtimeBindingInstalled"))
            && Boolean.FALSE.equals(report.get("stateMachineReleaseBound"))
            && Boolean.FALSE.equals(report.get("durableExecutorReleaseBound"))
            && Boolean.FALSE.equals(report.get("releaseDecisionAccepted"))
            && Boolean.FALSE.equals(report.get("releaseCredentialIssued"))
            && Boolean.FALSE.equals(report.get("releaseEligible"))
            && Boolean.FALSE.equals(report.get("writePermitted"))
            && Boolean.FALSE.equals(report.get("writeExecutionAllowed"))
            && Boolean.FALSE.equals(report.get("realHttpExecutionAllowed"))
            && Boolean.FALSE.equals(report.get("realStorageTouched"))
            && SWITCH_LOCKED.equals(text(report.get("codeReleaseSwitchStatus")))
            && REQUIRED_SWITCH_STATE.equals(text(report.get("requiredSwitchState")));
    }

    private static List<String> requiredRuntimeEvidenceFields() {
        return List.of(
            "codeReleaseSwitchContractDigest",
            "codeReleaseSwitchDigest",
            "codeReviewDigest",
            "testEvidenceDigest",
            "securityApprovalDigest",
            "rollbackPlanDigest",
            "changeWindowDigest",
            "releaseDecisionDigest",
            "validationResultDigest",
            "bodyDigest",
            "requestSpecDigest",
            "handoffDigest",
            "auditReceiptId",
            "serverDerivedIdempotencyKey",
            "sourceAuditEventDigest",
            "trustedPrincipalDigest"
        );
    }

    private static void validateCandidateSourceEvidence(Map<String, Object> evidence,
                                                        List<Map<String, Object>> blockers) {
        if (!evidence.isEmpty()) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_CANDIDATE_NOT_AUTHORIZED",
                "candidate source evidence cannot replace a reviewed server-owned open switch source.",
                "candidate-source-evidence"
            ));
        }
        if (hasForgedRuntimeSourceClaim(evidence)) {
            blockers.add(forgedRuntimeSourceClaimBlocker("candidateSourceEvidence"));
        }
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_GUARD_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " must not contain Authorization, token, password, secret, or real API key material.",
                source
            ));
        }
    }

    private static boolean hasForgedRuntimeSourceClaim(Map<String, Object> map) {
        if (map.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (forgedBooleanClaim(key, value) || forgedTextClaim(key, value)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && hasForgedRuntimeSourceClaim(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem
                        && hasForgedRuntimeSourceClaim(objectMap(nestedItem))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean forgedBooleanClaim(String key, Object value) {
        if (!Boolean.TRUE.equals(value)) {
            return false;
        }
        String normalized = normalizeKey(key);
        return List.of(
            "nimcreatereleased",
            "codereleaseswitchcontractreportaccepted",
            "codereleaseswitchcontractreportacceptedforrelease",
            "serverownedcodereleaseswitchaccepted",
            "realcodereleaseswitchopened",
            "codereleaseswitchdigestverified",
            "codereviewdigestverified",
            "testevidencedigestverified",
            "releasedecisiondigestverified",
            "validationresultdigestverified",
            "trustedprincipalvalidated",
            "runtimeflag",
            "runtimereleaseflag",
            "runtimeflagoverrideallowed",
            "runtimetoggleoverrideallowed",
            "environmentoverride",
            "environmentreleaseoverride",
            "environmentvariableoverrideallowed",
            "runtimebindinginstalled",
            "sourceguardinstalled",
            "statemachinereleasebound",
            "durableexecutorreleasebound",
            "releasedecisionaccepted",
            "releasecredential",
            "releasecredentialissued",
            "releaseeligible",
            "writepermitted",
            "writepermittedcanbetruenow",
            "writeexecutionallowed",
            "writeexecutionallowednow",
            "realhttpexecutionallowed",
            "writeattempted",
            "writeexecuted",
            "postwritereadinesstriggered",
            "deploymentid",
            "deploymentuid",
            "writeresult"
        ).contains(normalized);
    }

    private static boolean forgedTextClaim(String key, Object value) {
        String normalized = normalizeKey(key);
        String textValue = text(value);
        return ("switchstate".equals(normalized) || "codereleaseswitchstatus".equals(normalized))
            && REQUIRED_SWITCH_STATE.equals(textValue);
    }

    private static Map<String, Object> forgedRuntimeSourceClaimBlocker(String source) {
        return blocker(
            "CODE_RELEASE_SWITCH_RUNTIME_SOURCE_FORGED_RELEASE_CLAIM",
            source + " claims an open switch, release credential, write permission, or executor success.",
            source
        );
    }

    private static List<Map<String, Object>> ignoredCandidateClaims(Map<String, Object> candidateSourceEvidence) {
        List<Map<String, Object>> ignored = new ArrayList<>();
        collectIgnored("candidateSourceEvidence", candidateSourceEvidence, ignored);
        return ignored;
    }

    private static void collectIgnored(String source,
                                       Map<String, Object> map,
                                       List<Map<String, Object>> ignored) {
        for (String key : List.of(
            "nimCreateReleased",
            "codeReleaseSwitch",
            "codeReleaseSwitchDigest",
            "codeReleaseSwitchDigestVerified",
            "codeReleaseSwitchContractReportAccepted",
            "codeReleaseSwitchContractReportAcceptedForRelease",
            "codeReleaseSwitchStatus",
            "switchState",
            "runtimeFlag",
            "runtimeReleaseFlag",
            "environmentOverride",
            "environmentReleaseOverride",
            "writePermitted",
            "writePermittedCanBeTrueNow",
            "writeExecutionAllowed",
            "writeExecutionAllowedNow",
            "writeExecuted",
            "deploymentId",
            "deploymentUid",
            "writeResult",
            "releaseEligible",
            "releaseCredential",
            "releaseCredentialIssued"
        )) {
            if (map.containsKey(key)) {
                Map<String, Object> claim = new LinkedHashMap<>();
                claim.put("source", source);
                claim.put("key", key);
                claim.put("ignored", true);
                claim.put("reason", "candidate source evidence cannot replace the reviewed server-owned open switch.");
                ignored.add(claim);
            }
        }
    }

    private static boolean hasOnlyBlockerCode(Object rawBlockers, String code) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1 && code.equals(text(blockers.get(0).get("code")));
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        return NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            map,
            NimForbiddenSecretMaterialDetector.strictRecursivePolicy()
        );
    }

    private static String digestFor(Object value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK is missing SHA-256 digest algorithm", ex);
        }
    }

    private static String canonical(Object value) {
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

    private static String escape(String value) {
        return value.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    private static List<Map<String, Object>> listOfMaps(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> map = objectMap(item);
            if (!map.isEmpty()) {
                items.add(map);
            }
        }
        return items;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (Object item : list) {
            items.add(text(item));
        }
        return items;
    }

    private static Map<String, Object> objectMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        map.forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }

    private static Map<String, Object> blocker(String code, String message, String source) {
        Map<String, Object> blocker = new LinkedHashMap<>();
        blocker.put("code", code);
        blocker.put("message", message);
        blocker.put("source", source);
        return blocker;
    }

    private static boolean integerOrgId(String value) {
        return value.matches("[0-9]{1,18}");
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    record RuntimeSourceGuardInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> codeReleaseSwitchRuntimeBindingReport,
        Map<String, Object> candidateSourceEvidence
    ) {
        RuntimeSourceGuardInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null
                ? Map.of()
                : objectMap(trustedPrincipalSnapshot);
            codeReleaseSwitchRuntimeBindingReport = codeReleaseSwitchRuntimeBindingReport == null
                ? Map.of()
                : objectMap(codeReleaseSwitchRuntimeBindingReport);
            candidateSourceEvidence = candidateSourceEvidence == null
                ? Map.of()
                : objectMap(candidateSourceEvidence);
        }

        static RuntimeSourceGuardInput empty() {
            return new RuntimeSourceGuardInput(
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
            );
        }
    }
}
