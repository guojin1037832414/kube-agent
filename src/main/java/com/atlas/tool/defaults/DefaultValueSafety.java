package com.atlas.tool.defaults;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 默认值安全边界。
 *
 * <p>defaults.yml 只能表达前端表单草稿值，不能生成认证、租户、HITL、审计、发布或真实写入控制字段。
 * 这里在 {@link IntentDefaults} 封装层统一过滤，保证 YAML、测试注入和未来注册路径都走同一条护栏。</p>
 */
final class DefaultValueSafety {

    private static final Set<String> PROTECTED_KEYS = Set.of(
        "apikey",
        "ngcapikey",
        "nvaieapikey",
        "accesstoken",
        "refreshtoken",
        "idtoken",
        "authtoken",
        "token",
        "bearertoken",
        "authorization",
        "authheader",
        "headers",
        "cookie",
        "session",
        "sessionid",
        "conversationid",
        "requestid",
        "userid",
        "orgid",
        "organizationid",
        "targetorgid",
        "sourceorgid",
        "currentorgid",
        "requestedorgid",
        "trustedorgid",
        "tenant",
        "tenantid",
        "password",
        "secret",
        "secretkey",
        "clientsecret",
        "privatekey",
        "credential",
        "credentials",
        "approval",
        "approved",
        "approvedby",
        "confirmed",
        "confirmation",
        "humanconfirmed",
        "humanapproved",
        "hitlapproved",
        "hitlconfirmed",
        "hitlconfirmation",
        "requiresconfirmation",
        "httpmethod",
        "apiendpoint",
        "apiendpoints",
        "operationtype",
        "sideeffect",
        "nextsideeffectifexecuted",
        "safetopost",
        "writeallowed",
        "writeenabled",
        "writeauthorized",
        "writepermitted",
        "writeexecutionallowed",
        "realhttpexecutionallowed",
        "writeattempted",
        "writeexecuted",
        "writeresult",
        "writebodyrebuildreport",
        "writerequestspecreport",
        "writeexecutionhandoffreport",
        "releaseeligible",
        "releaseapproved",
        "releaseaccepted",
        "releaseauthorized",
        "releasegranted",
        "releasedecision",
        "releasecredential",
        "releasecredentialissued",
        "validationresult",
        "creationgate",
        "trustedpolicysnapshot",
        "licensevalid",
        "nvaielicensevalid",
        "nvaielicenseverified",
        "sysadmin",
        "issysorg",
        "auditcontext",
        "auditcontextprepared",
        "auditprepared",
        "auditreceipt",
        "auditreceiptprepared",
        "receiptstatus",
        "receiptid",
        "durablewriteexecutorreport",
        "readinessexecutionreport",
        "readinessready",
        "nimcreatereleased",
        "codereleaseswitch",
        "codereleaseswitchopened",
        "codereleaseswitchdigest",
        "sourceguardinstalled",
        "backendquerysourceallowedforrelease",
        "syslogbackfillsourceallowed",
        "fallbacktool",
        "usefallback",
        "deploymentid",
        "deploymentuid",
        "postwritereadinesstriggered",
        "success",
        "succeeded",
        "executed",
        "authoritative",
        "trustedpolicysource",
        "trustedpolicypassed",
        "trustedpolicyverified",
        "trustedpolicyreport"
    );

    private DefaultValueSafety() {
    }

    static Map<String, Object> sanitizeParameters(String intentId, Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            if (isProtectedDefaultKey(key)) {
                continue;
            }
            sanitized.put(key, sanitizeValue(entry.getValue()));
        }
        return sanitized;
    }

    static boolean isProtectedDefaultKey(String key) {
        return PROTECTED_KEYS.contains(normalize(key));
    }

    @SuppressWarnings("unchecked")
    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> rawMap) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            rawMap.forEach((key, item) -> {
                String textKey = String.valueOf(key);
                if (!isProtectedDefaultKey(textKey)) {
                    sanitized.put(textKey, sanitizeValue(item));
                }
            });
            return Collections.unmodifiableMap(sanitized);
        }
        if (value instanceof List<?> rawList) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : rawList) {
                sanitized.add(sanitizeValue(item));
            }
            return Collections.unmodifiableList(sanitized);
        }
        return value;
    }

    private static String normalize(String key) {
        if (key == null) {
            return "";
        }
        return key.replace("_", "")
            .replace("-", "")
            .replace(".", "")
            .replace(" ", "")
            .toLowerCase(Locale.ROOT);
    }
}
