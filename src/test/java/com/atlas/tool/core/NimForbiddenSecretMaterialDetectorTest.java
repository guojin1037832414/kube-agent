package com.atlas.tool.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5.21-86 NIM forbidden secret material 共享检测契约。
 *
 * <p>这些断言锁定共享检测器的公共语义，同时保留不同 NIM contract shell 的兼容策略差异，
 * 避免后续抽取时把“文档里的禁用字段名”和“真实 secret material”混为一谈。</p>
 */
class NimForbiddenSecretMaterialDetectorTest {

    @Test
    void detector_shouldRecognizeSecretKeysAndSecretLikeValuesRecursively() {
        Map<String, Object> payload = Map.of(
            "metadata", Map.of(
                "items", List.of(
                    Map.of("safe", "value"),
                    Map.of("header", "Authorization=Bearer abcdefghijklmnop")
                )
            )
        );

        assertTrue(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            payload,
            NimForbiddenSecretMaterialDetector.textValuePolicy()
        ));
    }

    @Test
    void textValuePolicy_shouldTreatNonBlankForbiddenKeyValuesAsSecretMaterial() {
        assertTrue(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("ngcApiKey", "redacted-but-present"),
            NimForbiddenSecretMaterialDetector.textValuePolicy()
        ));
        assertTrue(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("token", 123),
            NimForbiddenSecretMaterialDetector.textValuePolicy()
        ));
        assertFalse(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("password", ""),
            NimForbiddenSecretMaterialDetector.textValuePolicy()
        ));
    }

    @Test
    void receiptSchemaPolicy_shouldAllowDocumentedForbiddenFieldNamesButRejectRealSecrets() {
        Map<String, Object> documentedFieldNames = Map.of(
            "requestContract", Map.of(
                "forbiddenFields", List.of("Authorization", "apiKey", "ngcApiKey", "callerProvidedUsername")
            )
        );

        assertFalse(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            documentedFieldNames,
            NimForbiddenSecretMaterialDetector.receiptSchemaPolicy()
        ));
        assertFalse(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("documentedHeader", "Authorization"),
            NimForbiddenSecretMaterialDetector.receiptSchemaPolicy()
        ));
        assertFalse(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("documentedKey", "ngcApiKey"),
            NimForbiddenSecretMaterialDetector.receiptSchemaPolicy()
        ));
        assertFalse(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("token", true, "password", 0),
            NimForbiddenSecretMaterialDetector.receiptSchemaPolicy()
        ));
        assertTrue(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("forbiddenFields", List.of("Authorization=Bearer abcdefghijklmnop")),
            NimForbiddenSecretMaterialDetector.receiptSchemaPolicy()
        ));
    }

    @Test
    void nonBooleanNumberValuePolicy_shouldAllowStateScalarsButRejectSecretObjects() {
        assertFalse(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("token", false, "apiKey", 0),
            NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()
        ));
        assertTrue(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("token", List.of(Map.of("nested", "present"))),
            NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()
        ));
        assertTrue(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("note", List.of("Authorization=Bearer abcdefghijklmnop")),
            NimForbiddenSecretMaterialDetector.nonBooleanNumberValuePolicy()
        ));
    }

    @Test
    void strictRecursivePolicy_shouldRejectAnyNonNullForbiddenKeyValueIncludingNestedClaims() {
        assertTrue(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("token", false),
            NimForbiddenSecretMaterialDetector.strictRecursivePolicy()
        ));
        assertTrue(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("apiKey", List.of(Map.of("nested", "present"))),
            NimForbiddenSecretMaterialDetector.strictRecursivePolicy()
        ));
        assertFalse(NimForbiddenSecretMaterialDetector.containsForbiddenSecretMaterial(
            Map.of("note", List.of("Authorization", "apiKey")),
            NimForbiddenSecretMaterialDetector.strictRecursivePolicy()
        ));
    }

    @Test
    void detector_shouldRecognizeNormalizedSecretKeyVariants() {
        for (String key : new String[] {
            "ngc_api_key",
            "NVAIE-API-Key",
            "bearerToken",
            "release_secret",
            "registry.password",
            "customAuthorization"
        }) {
            assertTrue(NimForbiddenSecretMaterialDetector.isForbiddenSecretKey(key), key);
        }
    }
}
