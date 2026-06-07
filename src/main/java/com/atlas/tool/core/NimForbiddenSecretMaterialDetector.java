package com.atlas.tool.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Shared detector for NIM caller-visible structures that must not carry real secret material.
 *
 * <p>NIM support shells have slightly different compatibility rules: some contract reports allow
 * documented forbidden field names such as {@code Authorization} inside an interface specification,
 * while runtime-source guards treat non-null scalar values under secret-bearing keys as unsafe. This
 * helper centralizes the common key/value pattern matching and keeps those policy differences
 * explicit at each call site.</p>
 */
public final class NimForbiddenSecretMaterialDetector {

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

    private NimForbiddenSecretMaterialDetector() {
    }

    public static DetectionPolicy textValuePolicy() {
        return new DetectionPolicy(ForbiddenKeyValuePolicy.NON_BLANK_VALUE, Set.of());
    }

    public static DetectionPolicy nonBooleanNumberValuePolicy() {
        return new DetectionPolicy(ForbiddenKeyValuePolicy.NON_BOOLEAN_NUMBER_VALUE, Set.of());
    }

    public static DetectionPolicy receiptSchemaPolicy() {
        return new DetectionPolicy(
            ForbiddenKeyValuePolicy.NON_BOOLEAN_NUMBER_VALUE,
            Set.of(
                "authorization",
                "token",
                "apikey",
                "ngcapikey",
                "nvaieapikey",
                "password",
                "secret",
                "callerprovidedusername",
                "callerprovidedorganizationid"
            )
        );
    }

    public static DetectionPolicy strictRecursivePolicy() {
        return new DetectionPolicy(ForbiddenKeyValuePolicy.RECURSIVE_NON_NULL, Set.of());
    }

    public static boolean containsForbiddenSecretMaterial(Map<?, ?> map, DetectionPolicy policy) {
        DetectionPolicy safePolicy = policy == null ? textValuePolicy() : policy;
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (isForbiddenSecretKey(key) && secretBearingValue(value, safePolicy)) {
                return true;
            }
            if (value instanceof String textValue && looksLikeSecretValue(textValue)) {
                return true;
            }
            if (value instanceof Map<?, ?> nested && containsForbiddenSecretMaterial(nested, safePolicy)) {
                return true;
            }
            if (value instanceof List<?> list && listContainsSecretMaterial(list, safePolicy)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isForbiddenSecretKey(String key) {
        String normalized = normalizeKey(key);
        return FORBIDDEN_SECRET_KEYS.contains(normalized)
            || normalized.endsWith("apikey")
            || normalized.endsWith("token")
            || normalized.endsWith("secret")
            || normalized.endsWith("password")
            || normalized.endsWith("authorization");
    }

    public static boolean looksLikeSecretValue(String value) {
        String trimmed = value == null ? "" : value.trim();
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

    private static boolean listContainsSecretMaterial(List<?> list, DetectionPolicy policy) {
        for (Object item : list) {
            if (item instanceof Map<?, ?> nestedItem && containsForbiddenSecretMaterial(nestedItem, policy)) {
                return true;
            }
            if (item instanceof String textItem
                && looksLikeSecretValue(textItem)
                && !policy.allowsSecretLikeListValue(textItem)) {
                return true;
            }
        }
        return false;
    }

    private static boolean secretBearingValue(Object value, DetectionPolicy policy) {
        return switch (policy.forbiddenKeyValuePolicy()) {
            case NON_BLANK_VALUE -> hasText(value);
            case NON_BOOLEAN_NUMBER_VALUE -> {
                if (value instanceof Boolean || value instanceof Number) {
                    yield false;
                }
                yield hasText(value);
            }
            case RECURSIVE_NON_NULL -> recursiveSecretBearingValue(value, policy);
        };
    }

    private static boolean recursiveSecretBearingValue(Object value, DetectionPolicy policy) {
        if (value instanceof String textValue) {
            return hasText(textValue);
        }
        if (value instanceof Map<?, ?> nested) {
            return containsForbiddenSecretMaterial(nested, policy);
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (recursiveSecretBearingValue(item, policy)) {
                    return true;
                }
            }
        }
        return value != null;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(Object value) {
        return value != null && !value.toString().isBlank();
    }

    public enum ForbiddenKeyValuePolicy {
        NON_BLANK_VALUE,
        NON_BOOLEAN_NUMBER_VALUE,
        RECURSIVE_NON_NULL
    }

    public record DetectionPolicy(
        ForbiddenKeyValuePolicy forbiddenKeyValuePolicy,
        Set<String> allowedSecretLikeListValues
    ) {
        public DetectionPolicy {
            forbiddenKeyValuePolicy = forbiddenKeyValuePolicy == null
                ? ForbiddenKeyValuePolicy.NON_BLANK_VALUE
                : forbiddenKeyValuePolicy;
            allowedSecretLikeListValues = allowedSecretLikeListValues == null
                ? Set.of()
                : normalizedCopy(allowedSecretLikeListValues);
        }

        private boolean allowsSecretLikeListValue(String value) {
            return allowedSecretLikeListValues.contains(normalizeKey(value));
        }

        private static Set<String> normalizedCopy(Set<String> values) {
            Map<String, Boolean> normalized = new LinkedHashMap<>();
            for (String value : values) {
                normalized.put(normalizeKey(value), true);
            }
            return Set.copyOf(normalized.keySet());
        }
    }
}
