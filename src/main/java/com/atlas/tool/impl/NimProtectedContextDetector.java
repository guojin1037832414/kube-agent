package com.atlas.tool.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared detector for NIM write-chain body structures that must not carry caller/context authority.
 *
 * <p>This is separate from ProtectedToolParameterFilter: Tool execution parameters and future
 * write DTO/request bodies are different boundaries, even when some protected key names overlap.</p>
 */
final class NimProtectedContextDetector {

    private static final Set<String> PROTECTED_CONTEXT_KEYS = Set.of(
        "orgid",
        "organizationid",
        "userid",
        "conversationid",
        "sessionid",
        "requestid",
        "auditcontext",
        "auditreceipt",
        "hitlconfirmation",
        "creationgate",
        "readinessplan",
        "readinessexecutionreport",
        "writerequestspecreport"
    );

    private NimProtectedContextDetector() {
    }

    static boolean containsProtectedContext(Map<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (isProtectedContextKey(String.valueOf(entry.getKey()))
                || containsProtectedContextValue(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    static boolean isProtectedContextKey(String key) {
        return PROTECTED_CONTEXT_KEYS.contains(normalizeKey(key));
    }

    private static boolean containsProtectedContextValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            return containsProtectedContext(nested);
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (containsProtectedContextValue(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String normalizeKey(String key) {
        return key == null
            ? ""
            : key.replace("_", "")
                .replace("-", "")
                .replace(".", "")
                .replace(" ", "")
                .toLowerCase(Locale.ROOT);
    }
}
