package com.atlas.tool.impl;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NimProtectedContextDetectorTest {

    @Test
    void detector_shouldNormalizeProtectedContextKeyVariants() {
        assertTrue(NimProtectedContextDetector.isProtectedContextKey("organization_id"));
        assertTrue(NimProtectedContextDetector.isProtectedContextKey("org-id"));
        assertTrue(NimProtectedContextDetector.isProtectedContextKey("audit.receipt"));
        assertTrue(NimProtectedContextDetector.isProtectedContextKey("hitl-confirmation"));
        assertTrue(NimProtectedContextDetector.isProtectedContextKey("write_request_spec_report"));
    }

    @Test
    void detector_shouldFindNestedProtectedContextInMapsAndLists() {
        assertTrue(NimProtectedContextDetector.containsProtectedContext(Map.of(
            "autoScaleConfig", Map.of("organization_id", "100002")
        )));
        assertTrue(NimProtectedContextDetector.containsProtectedContext(Map.of(
            "commands", List.of(
                Map.of("name", "start"),
                Map.of("auditReceipt", Map.of("receiptId", "r1"))
            )
        )));
    }

    @Test
    void detector_shouldNotFlagOrdinaryBusinessFields() {
        assertFalse(NimProtectedContextDetector.containsProtectedContext(Map.of(
            "name", "llama-nim",
            "namespace", "default",
            "displayName", "llama-nim",
            "replicas", 1,
            "autoScaleConfig", Map.of("minReplicas", 1, "maxReplicas", 2),
            "commands", List.of("python", "server.py")
        )));
    }
}
