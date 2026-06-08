package com.atlas.tool.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tool 执行边界的受保护参数过滤器。
 *
 * <p>LLM、Plan、前端兼容入口传入的 Tool 参数都属于不可信业务输入。认证/租户上下文、
 * HITL、审计、发布与真实写入控制字段只能来自服务端可信链路，不能由参数伪造。本组件把这些
 * 字段的识别规则集中到一个位置，避免 ReAct、SafeToolExecutor 等入口维护相互漂移的黑名单。</p>
 *
 * <p>注意：本过滤器聚焦 Tool 执行参数边界，不替代 defaults.yml 的配置级清洗；默认值注册仍可使用
 * 更宽的配置安全策略。</p>
 */
public final class ProtectedToolParameterFilter {

    private static final Set<String> EXACT_KEYS = Set.of(
        "token",
        "accessToken",
        "authToken",
        "bearerToken",
        "authorization",
        "headers",
        "cookie",
        "session",
        "sessionId",
        "organizationId",
        "organization_id",
        "orgId",
        "org_id",
        "tenant",
        "tenantId",
        "tenant_id",
        "conversationId",
        "conversation_id",
        "userId",
        "user_id",
        "traceId",
        "trace_id",
        "traceparent",
        "tracestate",
        "confirmed",
        "confirmation",
        "hitlConfirmed",
        "hitlConfirmation",
        "hitlApproved",
        "approval",
        "approved",
        "auditReceipt",
        "auditAck",
        "releaseReceipt",
        "releaseDecision",
        "releaseApproved",
        "releaseCredential",
        "writePermitted",
        "writeAllowed",
        "writeEnabled",
        "writeAuthorized",
        "writeExecutionAllowed",
        "realHttpExecutionAllowed",
        "releaseEligible",
        "requiresConfirmation",
        "httpMethod",
        "operationType",
        "apiEndpoint",
        "apiEndpoints",
        "nimCreateReleased",
        "codeReleaseSwitch",
        "codeReleaseSwitchDigestVerified"
    );

    private static final Set<String> NORMALIZED_KEYS = Set.of(
        "token",
        "accesstoken",
        "authtoken",
        "bearertoken",
        "authorization",
        "headers",
        "cookie",
        "session",
        "sessionid",
        "organizationid",
        "orgid",
        "tenant",
        "tenantid",
        "conversationid",
        "userid",
        "traceid",
        "traceparent",
        "tracestate",
        "confirmed",
        "confirmation",
        "hitlconfirmed",
        "hitlconfirmation",
        "hitlapproved",
        "approval",
        "approved",
        "auditreceipt",
        "auditack",
        "releasereceipt",
        "releasedecision",
        "releaseapproved",
        "releasecredential",
        "writepermitted",
        "writeallowed",
        "writeenabled",
        "writeauthorized",
        "writeexecutionallowed",
        "realhttpexecutionallowed",
        "releaseeligible",
        "requiresconfirmation",
        "httpmethod",
        "operationtype",
        "apiendpoint",
        "apiendpoints",
        "nimcreatereleased",
        "codereleaseswitch",
        "codereleaseswitchdigestverified"
    );

    private ProtectedToolParameterFilter() {
    }

    /**
     * 判断参数名是否属于服务端可信控制平面字段。
     */
    public static boolean isProtected(String key) {
        if (key == null) {
            return false;
        }
        return EXACT_KEYS.contains(key) || NORMALIZED_KEYS.contains(normalize(key));
    }

    /**
     * 返回移除受保护字段后的参数副本，保留原始参数顺序与普通业务字段。
     */
    public static Map<String, Object> copyWithoutProtected(Map<String, Object> params) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        if (params == null || params.isEmpty()) {
            return filtered;
        }
        params.forEach((key, value) -> {
            if (!isProtected(key)) {
                filtered.put(key, value);
            }
        });
        return filtered;
    }

    private static String normalize(String key) {
        return key.replace("_", "")
            .replace("-", "")
            .replace(".", "")
            .replace(" ", "")
            .toLowerCase(Locale.ROOT);
    }
}
