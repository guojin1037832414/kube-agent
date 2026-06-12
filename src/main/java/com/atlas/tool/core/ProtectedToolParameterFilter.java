package com.atlas.tool.core;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Tool 执行边界的受保护参数过滤器。
 *
 * <p>中文说明：LLM、Plan 和前端传入的参数只能表达业务意图，不能表达控制平面事实。
 * 例如 token、organizationId、HITL confirmation、audit receipt、release decision、writeAllowed 等字段，
 * 都必须来自服务端可信链路，不能由 LLM 在 JSON 参数里“自己声明”。</p>
 *
 * <p>安全边界：本过滤器只识别并移除控制平面字段，不负责判断业务字段是否完整或合法；
 * 业务 schema 仍由 {@link ToolParameterSpec}、Tool 自身校验和 {@code SafeToolExecutor} 的来源策略处理。</p>
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
        // 中文说明：认证与会话字段只能来自 AuthTokenFilter / SessionStore / SecurityContext。
        "token",
        "accessToken",
        "authToken",
        "bearerToken",
        "authorization",
        "headers",
        "cookie",
        "session",
        "sessionId",
        // 中文说明：租户与用户上下文字段必须由服务端解析后最后覆盖，不能信任 LLM/前端传值。
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
        // 中文说明：trace 字段是可观测控制平面数据，不应被 Tool 当成业务筛选或写入授权。
        "traceId",
        "trace_id",
        "traceparent",
        "tracestate",
        // 中文说明：HITL/审批字段必须来自服务端确认 marker，不能由 Action.params 伪造。
        "confirmed",
        "confirmation",
        "hitlConfirmed",
        "hitlConfirmation",
        "hitlApproved",
        "approval",
        "approved",
        // 中文说明：审计、发布与写入放行字段都接近真实执行权限，必须由后端证据链生成。
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
     *
     * <p>中文说明：同时检查原始 key 和归一化 key，是为了防止调用方通过
     * {@code org_id}、{@code org-id}、{@code org.id} 等别名绕过过滤。</p>
     */
    public static boolean isProtected(String key) {
        if (key == null) {
            return false;
        }
        return EXACT_KEYS.contains(key) || NORMALIZED_KEYS.contains(normalize(key));
    }

    /**
     * 返回移除受保护字段后的参数副本，保留原始参数顺序与普通业务字段。
     *
     * <p>安全边界：这里不会补充服务端可信字段，只负责删除不可信控制字段。
     * SafeToolExecutor 会在后续把已确认的 userId、organizationId、conversationId 重新写回。</p>
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
