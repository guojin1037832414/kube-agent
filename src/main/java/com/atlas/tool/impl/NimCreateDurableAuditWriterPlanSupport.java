package com.atlas.tool.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * NIM 创建专用 durable audit writer 的两阶段计划契约。
 *
 * <p>本类仍然是纯数据、mock-first 的计划层: 不连接 Elasticsearch，不注入 {@code ISysLogService}，
 * 不写入 {@code sys_log}，也不签发 durable receipt。它只把 M5.21-51 找到的 mature
 * {@code sys_log} 候选证据进一步收敛为未来专用 writer 必须满足的两阶段写入边界:
 * pre-write intent 必须先于真实 POST 持久化，post-write result 必须在 POST 结果明确后再持久化。</p>
 */
final class NimCreateDurableAuditWriterPlanSupport {

    static final String WRITER_PLAN_NAME = "NIM_CREATE_DURABLE_AUDIT_WRITER_PLAN";
    static final String EXECUTION_MODE = "DURABLE_AUDIT_WRITER_PLAN_CONTRACT_ONLY";
    static final String HOLD_STATE = "IMPLEMENTATION_HOLD";
    static final String REJECTED_STATE = "REJECTED";
    static final String PRE_WRITE_RECORD_TYPE = "NIM_CREATE_PRE_WRITE_INTENT";
    static final String POST_WRITE_RECORD_TYPE = "NIM_CREATE_POST_WRITE_RESULT";

    private static final String PATH_TEMPLATE = "/api/{orgId}/deployment";
    private static final String SYS_LOG_MODULE = "NIM_CREATE_AUDIT";
    private static final Set<String> FORBIDDEN_SECRET_KEYS = Set.of(
        "apikey",
        "ngcapikey",
        "nvaieapikey",
        "token",
        "secret",
        "password",
        "authorization",
        "authheader",
        "bearertoken"
    );

    private NimCreateDurableAuditWriterPlanSupport() {
    }

    static Map<String, Object> plan(DurableAuditWriterPlanInput input) {
        DurableAuditWriterPlanInput safeInput = input == null ? DurableAuditWriterPlanInput.empty() : input;
        Map<String, Object> auditContext = safeInput.auditContext();
        Map<String, Object> principal = safeInput.trustedPrincipalSnapshot();
        Map<String, Object> storageReport = safeInput.durableAuditStorageReport();
        Map<String, Object> requestSpecReport = safeInput.writeRequestSpecReport();
        Map<String, Object> handoffReport = safeInput.writeExecutionHandoffReport();
        List<Map<String, Object>> blockers = new ArrayList<>();

        validateAuditContext(auditContext, blockers);
        validateTrustedPrincipal(auditContext, principal, blockers);
        validateStorageCandidateReport(auditContext, principal, storageReport, blockers);
        validateOptionalWriteRequestSpecReport(requestSpecReport, blockers);
        validateOptionalWriteExecutionHandoffReport(handoffReport, blockers);
        validateNoSecretMaterial("auditContext", auditContext, blockers);
        validateNoSecretMaterial("trustedPrincipalSnapshot", principal, blockers);
        validateNoSecretMaterial("durableAuditStorageReport", storageReport, blockers);
        validateNoSecretMaterial("writeRequestSpecReport", requestSpecReport, blockers);
        validateNoSecretMaterial("writeExecutionHandoffReport", handoffReport, blockers);

        boolean inputAccepted = blockers.isEmpty();
        Map<String, Object> writerPlan = inputAccepted
            ? writerPlan(auditContext, principal, storageReport, requestSpecReport, handoffReport)
            : Map.of();
        List<Map<String, Object>> finalBlockers = inputAccepted
            ? List.of(
                blocker(
                    "DURABLE_AUDIT_STORAGE_CANDIDATE_IMPLEMENTATION_HOLD",
                    "sys_log 仍只是候选持久化证据；真实存储可用性门禁和专用 writer 尚未实现。",
                    "durable-audit-storage"
                ),
                blocker(
                    "DURABLE_AUDIT_WRITER_IMPLEMENTATION_HOLD",
                    "NIM 专用两阶段 durable audit writer 尚未实现；当前计划不能签发 durable receipt。",
                    "durable-audit-writer"
                )
            )
            : blockers;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("durableAuditWriterPlan", WRITER_PLAN_NAME);
        result.put("executionMode", EXECUTION_MODE);
        result.put("writerState", inputAccepted ? HOLD_STATE : REJECTED_STATE);
        result.put("targetTool", NimCreateStateMachineSupport.TARGET_TOOL);
        result.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        result.put("pathTemplate", PATH_TEMPLATE);
        result.put("networkAccess", "NOT_PERFORMED");
        result.put("sideEffect", "NONE");
        result.put("inputAccepted", inputAccepted);
        result.put("writerPlanPrepared", inputAccepted);
        result.put("preWriteRecordRequired", true);
        result.put("postWriteRecordRequired", true);
        result.put("storageAvailabilityGateRequired", true);
        result.put("dedicatedWriterRequired", true);
        result.put("candidateStorageValidated", inputAccepted);
        result.put("candidateIndex", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        result.put("candidateSaveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        result.put("realStorageTouched", false);
        result.put("durable", false);
        result.put("releaseEligible", false);
        result.put("durableReceiptCanBeIssued", false);
        result.put("sourceAuditEventDigest", digestFor(auditContext));
        result.put("sourceStoragePlanDigest", text(storageReport.get("storagePlanDigest")));
        result.put("writerPlanDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        result.put("writerPlanDigest", inputAccepted ? digestFor(writerPlan) : "");
        result.put("writerPlan", writerPlan);
        result.put("blockedBy", finalBlockers);
        result.put("ignoredCallerClaims", ignoredCallerClaims(auditContext, principal, storageReport));
        result.put("nextImplementationRequirements", List.of(
            "implement a dedicated server-side NimDurableAuditWriter boundary",
            "probe storage availability before recording pre-write intent",
            "persist pre-write intent before POST /api/{orgId}/deployment",
            "persist post-write result after the durable write executor returns",
            "issue DURABLE_RECORDED receipt only when both records are durably stored and bound by digest"
        ));
        return result;
    }

    private static void validateAuditContext(Map<String, Object> auditContext,
                                             List<Map<String, Object>> blockers) {
        if (auditContext.isEmpty()
            || !Boolean.TRUE.equals(auditContext.get("auditPrepared"))
            || !NimCreateAuditReadinessSupport.AUDIT_EVENT_TYPE.equals(text(auditContext.get("auditEventType")))
            || !NimCreateStateMachineSupport.TARGET_TOOL.equals(text(auditContext.get("targetTool")))
            || !NimCreateStateMachineSupport.TRUSTED_BODY_PROVENANCE.equals(text(auditContext.get("writeBodyProvenance")))
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
                "AUDIT_CONTEXT_NOT_TRUSTED_FOR_DURABLE_WRITER_PLAN",
                "durable audit writer 计划只能消费完整、已脱敏、绑定 NIM_CREATE_REQUEST 的审计上下文。",
                "audit-context"
            ));
        }
        if (hasForgedReleaseClaim(auditContext)) {
            blockers.add(forgedReleaseClaimBlocker("auditContext"));
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
                "TRUSTED_PRINCIPAL_SNAPSHOT_NOT_READY",
                "writer 计划必须绑定服务端可信 session principal，不能信任 Tool 入参自报身份。",
                "trusted-principal"
            ));
        }
        if (hasForgedReleaseClaim(principal)) {
            blockers.add(forgedReleaseClaimBlocker("trustedPrincipalSnapshot"));
        }
    }

    private static void validateStorageCandidateReport(Map<String, Object> auditContext,
                                                       Map<String, Object> principal,
                                                       Map<String, Object> storageReport,
                                                       List<Map<String, Object>> blockers) {
        if (storageReport.isEmpty()) {
            blockers.add(blocker(
                "DURABLE_AUDIT_STORAGE_CANDIDATE_REPORT_NOT_READY",
                "缺少 M5.21-51 durable audit storage 候选报告；不能凭空规划 writer。",
                "durable-audit-storage"
            ));
            return;
        }

        Map<String, Object> storagePlan = objectMap(storageReport.get("storagePlan"));
        boolean valid = NimCreateDurableAuditStorageSupport.STORAGE_SUPPORT_NAME.equals(text(storageReport.get("durableAuditStorage")))
            && NimCreateDurableAuditStorageSupport.EXECUTION_MODE.equals(text(storageReport.get("executionMode")))
            && NimCreateDurableAuditStorageSupport.HOLD_STATE.equals(text(storageReport.get("storageState")))
            && "NOT_PERFORMED".equals(text(storageReport.get("networkAccess")))
            && "NONE".equals(text(storageReport.get("sideEffect")))
            && Boolean.TRUE.equals(storageReport.get("inputAccepted"))
            && Boolean.TRUE.equals(storageReport.get("storagePlanPrepared"))
            && Boolean.FALSE.equals(storageReport.get("realStorageTouched"))
            && Boolean.FALSE.equals(storageReport.get("durable"))
            && Boolean.FALSE.equals(storageReport.get("releaseEligible"))
            && Boolean.FALSE.equals(storageReport.get("durableReceiptCanBeIssued"))
            && "PARTIAL_FIT_NEEDS_DEDICATED_NIM_AUDIT_WRITER".equals(text(storageReport.get("candidateFit")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(storageReport.get("candidateIndex")))
            && NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE.equals(text(storageReport.get("candidateSaveService")))
            && text(storageReport.get("sourceAuditEventDigest")).equals(digestFor(auditContext))
            && NimCreateAuditWriterSupport.DIGEST_ALGORITHM.equals(text(storageReport.get("storagePlanDigestAlgorithm")))
            && text(storageReport.get("storagePlanDigest")).matches("[a-f0-9]{64}")
            && text(storageReport.get("storagePlanDigest")).equals(digestFor(storagePlan))
            && hasOnlyExpectedStorageCandidateHold(storageReport.get("blockedBy"))
            && storagePlanMatchesIdentity(storagePlan, auditContext, principal);

        if (!valid) {
            blockers.add(blocker(
                "DURABLE_AUDIT_STORAGE_CANDIDATE_REPORT_INVALID",
                "durable audit writer 计划只能消费 M5.21-51 产生的、未触碰真实存储且仍处于 HOLD 的 sys_log 候选报告。",
                "durable-audit-storage"
            ));
        }
        if (hasForgedReleaseClaim(storageReport)) {
            blockers.add(forgedReleaseClaimBlocker("durableAuditStorageReport"));
        }
    }

    private static boolean storagePlanMatchesIdentity(Map<String, Object> storagePlan,
                                                      Map<String, Object> auditContext,
                                                      Map<String, Object> principal) {
        Map<String, Object> mapping = objectMap(storagePlan.get("sysLogFieldMapping"));
        Map<String, Object> params = objectMap(mapping.get("params"));
        Map<String, Object> body = objectMap(mapping.get("body"));
        return !storagePlan.isEmpty()
            && NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX.equals(text(storagePlan.get("targetStorage")))
            && "DEDICATED_NIM_AUDIT_WRITER_REQUIRED".equals(text(storagePlan.get("writerBoundary")))
            && "PRE_WRITE_INTENT_THEN_POST_WRITE_RESULT".equals(text(storagePlan.get("writeMode")))
            && Integer.valueOf(text(auditContext.get("organizationId"))).equals(mapping.get("organizationId"))
            && text(principal.get("username")).equals(text(mapping.get("username")))
            && SYS_LOG_MODULE.equals(text(mapping.get("module")))
            && ("/api/" + text(auditContext.get("organizationId")) + "/deployment").equals(text(mapping.get("uri")))
            && text(auditContext.get("requestId")).equals(text(params.get("requestId")))
            && text(auditContext.get("conversationId")).equals(text(params.get("conversationId")))
            && text(auditContext.get("userId")).equals(text(params.get("userId")))
            && NimCreateStateMachineSupport.TARGET_TOOL.equals(text(params.get("targetTool")))
            && digestFor(auditContext).equals(text(params.get("eventDigest")))
            && text(auditContext.get("displayName")).equals(text(body.get("displayName")))
            && text(auditContext.get("image")).equals(text(body.get("image")))
            && text(auditContext.get("templateId")).equals(text(body.get("templateId")))
            && NimCreateStateMachineSupport.API_KEY_POLICY.equals(text(body.get("apiKeyHandling")));
    }

    private static void validateOptionalWriteRequestSpecReport(Map<String, Object> requestSpecReport,
                                                               List<Map<String, Object>> blockers) {
        if (requestSpecReport.isEmpty()) {
            return;
        }
        if (!NimCreateWriteRequestSpecAdapterSupport.ADAPTER_NAME.equals(text(requestSpecReport.get("writeRequestSpecAdapter")))
            || !Boolean.TRUE.equals(requestSpecReport.get("writeRequestPrepared"))
            || !text(requestSpecReport.get("requestSpecDigest")).matches("[a-f0-9]{64}")
            || !text(requestSpecReport.get("bodyDigest")).matches("[a-f0-9]{64}")
            || !"POST".equals(text(requestSpecReport.get("httpMethod")))
            || !PATH_TEMPLATE.equals(text(requestSpecReport.get("pathTemplate")))) {
            blockers.add(blocker(
                "WRITE_REQUEST_SPEC_REPORT_NOT_READY_FOR_DURABLE_AUDIT_WRITER_PLAN",
                "如果提供 write request spec，它必须是受控 adapter 生成的 POST request spec 报告。",
                "write-request-spec"
            ));
        }
    }

    private static void validateOptionalWriteExecutionHandoffReport(Map<String, Object> handoffReport,
                                                                    List<Map<String, Object>> blockers) {
        if (handoffReport.isEmpty()) {
            return;
        }
        if (!NimCreateWriteExecutionHandoffSupport.HANDOFF_NAME.equals(text(handoffReport.get("writeExecutionHandoff")))
            || !Boolean.TRUE.equals(handoffReport.get("writeExecutionPrepared"))
            || !text(handoffReport.get("handoffDigest")).matches("[a-f0-9]{64}")
            || !text(handoffReport.get("idempotencyKey")).matches("nim-create-[a-f0-9]{32}")
            || !"POST".equals(text(handoffReport.get("httpMethod")))
            || !PATH_TEMPLATE.equals(text(handoffReport.get("pathTemplate")))) {
            blockers.add(blocker(
                "WRITE_EXECUTION_HANDOFF_REPORT_NOT_READY_FOR_DURABLE_AUDIT_WRITER_PLAN",
                "如果提供 write execution handoff，它必须绑定服务端幂等键和受控 POST handoff digest。",
                "write-execution-handoff"
            ));
        }
    }

    private static Map<String, Object> writerPlan(Map<String, Object> auditContext,
                                                  Map<String, Object> principal,
                                                  Map<String, Object> storageReport,
                                                  Map<String, Object> requestSpecReport,
                                                  Map<String, Object> handoffReport) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("writerBoundary", "DEDICATED_NIM_DURABLE_AUDIT_WRITER_REQUIRED");
        plan.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        plan.put("targetEntity", NimCreateDurableAuditStorageSupport.CANDIDATE_ENTITY);
        plan.put("saveService", NimCreateDurableAuditStorageSupport.CANDIDATE_SAVE_SERVICE);
        plan.put("writeMode", "PRE_WRITE_INTENT_AND_POST_WRITE_RESULT");
        plan.put("storageCandidateDigest", text(storageReport.get("storagePlanDigest")));
        plan.put("storageAvailabilityGate", storageAvailabilityGate());
        plan.put("trustedIdentityBinding", trustedIdentityBinding(auditContext, principal));
        plan.put("preWriteRecordTemplate", preWriteRecordTemplate(auditContext, principal, requestSpecReport));
        plan.put("postWriteRecordTemplate", postWriteRecordTemplate(auditContext, principal, requestSpecReport, handoffReport));
        plan.put("receiptIssuanceRule", receiptIssuanceRule());
        plan.put("redactionPolicy", List.of(
            "write params/body summaries only, never raw Tool params",
            "bind records with requestId, conversationId, audit event digest and server-derived idempotency key",
            "never persist Authorization/token/password/secret/API key material",
            "issue durable receipt only after both pre-write and post-write records are confirmed durable"
        ));
        return plan;
    }

    private static Map<String, Object> storageAvailabilityGate() {
        Map<String, Object> gate = new LinkedHashMap<>();
        gate.put("required", true);
        gate.put("probeBeforePreWrite", true);
        gate.put("failClosedWhenStorageUnavailable", true);
        gate.put("elasticsearchDisabledMustBlockReceipt", true);
        gate.put("candidateReportAloneCanIssueReceipt", false);
        return gate;
    }

    private static Map<String, Object> trustedIdentityBinding(Map<String, Object> auditContext,
                                                              Map<String, Object> principal) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("organizationId", text(auditContext.get("organizationId")));
        binding.put("userId", text(auditContext.get("userId")));
        binding.put("username", text(principal.get("username")));
        binding.put("source", "SERVER_SESSION_CONTEXT");
        binding.put("protectedFromCallerParams", true);
        return binding;
    }

    private static Map<String, Object> preWriteRecordTemplate(Map<String, Object> auditContext,
                                                              Map<String, Object> principal,
                                                              Map<String, Object> requestSpecReport) {
        Map<String, Object> record = baseSysLogRecord(auditContext, principal);
        record.put("recordType", PRE_WRITE_RECORD_TYPE);
        record.put("phase", "PRE_WRITE");
        record.put("description", "nim_create pre-write durable audit intent");
        record.put("recordedBeforeWrite", true);
        record.put("recordedAfterWrite", false);
        record.put("requestSpecDigest", optionalDigest(requestSpecReport, "requestSpecDigest", "{futureRequestSpecDigest}"));
        record.put("bodyDigest", optionalDigest(requestSpecReport, "bodyDigest", "{futureWriteBodyDigest}"));
        record.put("params", sanitizedParams(auditContext));
        record.put("body", sanitizedBody(auditContext));
        record.put("success", "PRE_WRITE_INTENT_PLANNED_NOT_RECORDED");
        record.put("trace", List.of());
        record.put("futureRecordId", "{futurePreWriteSysLogId}");
        record.put("realStorageTouched", false);
        return record;
    }

    private static Map<String, Object> postWriteRecordTemplate(Map<String, Object> auditContext,
                                                               Map<String, Object> principal,
                                                               Map<String, Object> requestSpecReport,
                                                               Map<String, Object> handoffReport) {
        Map<String, Object> record = baseSysLogRecord(auditContext, principal);
        record.put("recordType", POST_WRITE_RECORD_TYPE);
        record.put("phase", "POST_WRITE");
        record.put("description", "nim_create post-write durable audit result");
        record.put("recordedBeforeWrite", false);
        record.put("recordedAfterWrite", true);
        record.put("preWriteRecordId", "{futurePreWriteSysLogId}");
        record.put("requestSpecDigest", optionalDigest(requestSpecReport, "requestSpecDigest", "{futureRequestSpecDigest}"));
        record.put("bodyDigest", optionalDigest(requestSpecReport, "bodyDigest", "{futureWriteBodyDigest}"));
        record.put("handoffDigest", optionalDigest(handoffReport, "handoffDigest", "{futureHandoffDigest}"));
        record.put("idempotencyKey", hasText(handoffReport.get("idempotencyKey"))
            ? text(handoffReport.get("idempotencyKey"))
            : "{futureServerDerivedIdempotencyKey}");
        record.put("params", sanitizedParams(auditContext));
        record.put("body", Map.of(
            "writeResult", "{futureSanitizedWriteResultSummary}",
            "deploymentId", "{futureDeploymentId}",
            "deploymentUid", "{futureDeploymentUid}",
            "readinessHandoff", NimCreateReadinessExecutorSupport.EXECUTOR_NAME
        ));
        record.put("success", "{futureWriteResultSuccess}");
        record.put("trace", List.of());
        record.put("futureRecordId", "{futurePostWriteSysLogId}");
        record.put("realStorageTouched", false);
        return record;
    }

    private static Map<String, Object> baseSysLogRecord(Map<String, Object> auditContext,
                                                        Map<String, Object> principal) {
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("targetStorage", NimCreateDurableAuditStorageSupport.CANDIDATE_INDEX);
        record.put("organizationId", Integer.valueOf(text(auditContext.get("organizationId"))));
        record.put("username", text(principal.get("username")));
        record.put("module", SYS_LOG_MODULE);
        record.put("uri", "/api/" + text(auditContext.get("organizationId")) + "/deployment");
        record.put("requestId", text(auditContext.get("requestId")));
        record.put("conversationId", text(auditContext.get("conversationId")));
        record.put("userId", text(auditContext.get("userId")));
        record.put("eventDigestAlgorithm", NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
        record.put("eventDigest", digestFor(auditContext));
        record.put("ip", "{trustedRequestIp}");
        record.put("start", "{serverClockMillis}");
        record.put("end", "{serverClockMillisAfterDurableRecord}");
        return record;
    }

    private static Map<String, Object> receiptIssuanceRule() {
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("durableReceiptCanBeIssuedNow", false);
        rule.put("requiredFutureReceiptStatus", NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS);
        rule.put("requiredFutureStorageMode", NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE);
        rule.put("preWriteRecordRequired", true);
        rule.put("postWriteRecordRequired", true);
        rule.put("bothRecordsMustBeDurable", true);
        rule.put("recordDigestsMustBindSameAuditEvent", true);
        rule.put("candidateReportAloneIsInsufficient", true);
        return rule;
    }

    private static Map<String, Object> sanitizedParams(Map<String, Object> auditContext) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("requestId", text(auditContext.get("requestId")));
        params.put("conversationId", text(auditContext.get("conversationId")));
        params.put("userId", text(auditContext.get("userId")));
        params.put("targetTool", text(auditContext.get("targetTool")));
        params.put("auditEventType", text(auditContext.get("auditEventType")));
        params.put("backendEndpoint", NimCreateAuditReadinessSupport.BACKEND_ENDPOINT);
        params.put("eventDigest", digestFor(auditContext));
        return params;
    }

    private static Map<String, Object> sanitizedBody(Map<String, Object> auditContext) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("displayName", text(auditContext.get("displayName")));
        body.put("image", text(auditContext.get("image")));
        body.put("templateId", text(auditContext.get("templateId")));
        body.put("writeBodyProvenance", text(auditContext.get("writeBodyProvenance")));
        body.put("apiKeyHandling", NimCreateStateMachineSupport.API_KEY_POLICY);
        body.put("secretRedactionApplied", true);
        return body;
    }

    private static String optionalDigest(Map<String, Object> report, String key, String placeholder) {
        String digest = text(report.get(key));
        return digest.matches("[a-f0-9]{64}") ? digest : placeholder;
    }

    private static boolean hasOnlyExpectedStorageCandidateHold(Object rawBlockers) {
        List<Map<String, Object>> blockers = listOfMaps(rawBlockers);
        return blockers.size() == 1
            && "DEDICATED_NIM_AUDIT_WRITER_NOT_IMPLEMENTED".equals(text(blockers.get(0).get("code")));
    }

    private static void validateNoSecretMaterial(String source,
                                                 Map<String, Object> map,
                                                 List<Map<String, Object>> blockers) {
        if (containsForbiddenSecretMaterial(map)) {
            blockers.add(blocker(
                "DURABLE_AUDIT_WRITER_INPUT_CONTAINS_FORBIDDEN_SECRET",
                source + " 不得包含 Authorization、token、password、secret 或真实 NGC/NIM API Key。",
                source
            ));
        }
    }

    private static boolean containsForbiddenSecretMaterial(Map<String, Object> map) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (isForbiddenSecretKey(entry.getKey()) && hasText(value)) {
                return true;
            }
            if (value instanceof String textValue && looksLikeSecretValue(textValue)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && containsForbiddenSecretMaterial(objectMap(nested))) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof Map<?, ?> nestedItem && containsForbiddenSecretMaterial(objectMap(nestedItem))) {
                        return true;
                    }
                    if (item instanceof String textItem && looksLikeSecretValue(textItem)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasForgedReleaseClaim(Map<String, Object> map) {
        return Boolean.TRUE.equals(map.get("durableReceiptCanBeIssued"))
            || Boolean.TRUE.equals(map.get("releaseEligible"))
            || Boolean.TRUE.equals(map.get("realStorageTouched"))
            || Boolean.TRUE.equals(map.get("durable"))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_RECEIPT_STATUS.equals(text(map.get("receiptStatus")))
            || NimCreateStateMachineSupport.REQUIRED_AUDIT_STORAGE_MODE.equals(text(map.get("storageMode")));
    }

    private static Map<String, Object> forgedReleaseClaimBlocker(String source) {
        return blocker(
            "DURABLE_AUDIT_WRITER_FORGED_RELEASE_CLAIM",
            source + " 不得自称 durable、releaseEligible、DURABLE_RECORDED 或可签发 receipt；这些只能来自未来真实 writer。",
            source
        );
    }

    private static List<String> ignoredCallerClaims(Map<String, Object> auditContext,
                                                    Map<String, Object> principal,
                                                    Map<String, Object> storageReport) {
        List<String> ignored = new ArrayList<>();
        for (String key : List.of(
            "durableReceiptCanBeIssued",
            "releaseEligible",
            "realStorageTouched",
            "durable",
            "receiptStatus",
            "storageMode"
        )) {
            if (auditContext.containsKey(key) || principal.containsKey(key) || storageReport.containsKey(key)) {
                ignored.add(key);
            }
        }
        return ignored;
    }

    private static boolean isForbiddenSecretKey(String key) {
        String normalized = normalizeKey(key);
        return FORBIDDEN_SECRET_KEYS.contains(normalized)
            || normalized.endsWith("apikey")
            || normalized.endsWith("token")
            || normalized.endsWith("secret")
            || normalized.endsWith("password")
            || normalized.endsWith("authorization");
    }

    private static boolean looksLikeSecretValue(String value) {
        String trimmed = value.trim();
        String normalized = normalizeKey(trimmed);
        if (trimmed.startsWith("Bearer ") && trimmed.length() > "Bearer ".length()) {
            return true;
        }
        return normalized.contains("ngcapikey")
            || normalized.contains("nvaieapikey")
            || normalized.contains("authorizationbearer")
            || normalized.contains("apikey=")
            || normalized.contains("token=")
            || normalized.contains("secret=")
            || normalized.contains("password=")
            || normalized.contains("authorization=")
            || trimmed.matches("sk-[A-Za-z0-9]{20,}")
            || trimmed.matches("AKIA[0-9A-Z]{16}")
            || trimmed.matches("AIza[0-9A-Za-z_-]{35}")
            || trimmed.matches("ghp_[A-Za-z0-9]{36}")
            || trimmed.matches("xox[baprs]-[A-Za-z0-9-]{10,}");
    }

    private static String digestFor(Map<String, Object> value) {
        try {
            MessageDigest digest = MessageDigest.getInstance(NimCreateAuditWriterSupport.DIGEST_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JDK 缺少 SHA-256 摘要算法", ex);
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
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0;
        } catch (NumberFormatException ex) {
            return false;
        }
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

    record DurableAuditWriterPlanInput(
        Map<String, Object> auditContext,
        Map<String, Object> trustedPrincipalSnapshot,
        Map<String, Object> durableAuditStorageReport,
        Map<String, Object> writeRequestSpecReport,
        Map<String, Object> writeExecutionHandoffReport
    ) {
        DurableAuditWriterPlanInput {
            auditContext = auditContext == null ? Map.of() : objectMap(auditContext);
            trustedPrincipalSnapshot = trustedPrincipalSnapshot == null ? Map.of() : objectMap(trustedPrincipalSnapshot);
            durableAuditStorageReport = durableAuditStorageReport == null ? Map.of() : objectMap(durableAuditStorageReport);
            writeRequestSpecReport = writeRequestSpecReport == null ? Map.of() : objectMap(writeRequestSpecReport);
            writeExecutionHandoffReport = writeExecutionHandoffReport == null ? Map.of() : objectMap(writeExecutionHandoffReport);
        }

        DurableAuditWriterPlanInput(Map<String, Object> auditContext,
                                    Map<String, Object> trustedPrincipalSnapshot,
                                    Map<String, Object> durableAuditStorageReport) {
            this(auditContext, trustedPrincipalSnapshot, durableAuditStorageReport, Map.of(), Map.of());
        }

        static DurableAuditWriterPlanInput empty() {
            return new DurableAuditWriterPlanInput(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
    }
}
