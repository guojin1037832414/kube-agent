package com.atlas.tool.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M5.21-85 共享 Tool 参数保护契约。
 *
 * <p>该测试锁定执行边界的控制平面字段识别规则，防止 ReAct、SafeToolExecutor、
 * execute_node 分别维护不同名单后再次发生安全语义漂移。</p>
 */
class ProtectedToolParameterFilterTest {

    @Test
    void isProtected_shouldRecognizeContextHitlReleaseAndWriteControlVariants() {
        for (String key : new String[] {
            "token",
            "accessToken",
            "auth_token",
            "Authorization",
            "headers",
            "cookie",
            "organizationId",
            "organization_id",
            "orgId",
            "tenant_id",
            "conversation_id",
            "userId",
            "confirmed",
            "hitl_approved",
            "release-approved",
            "releaseCredential",
            "write_allowed",
            "writeExecutionAllowed",
            "real-http-execution-allowed",
            "requiresConfirmation",
            "operation_type",
            "api.endpoints",
            "nimCreateReleased",
            "code-release-switch-digest-verified"
        }) {
            assertTrue(ProtectedToolParameterFilter.isProtected(key), key + " must be protected");
        }
    }

    @Test
    void isProtected_shouldNotBlockOrdinaryBusinessParameters() {
        for (String key : new String[] {
            "keyword",
            "namespace",
            "name",
            "displayName",
            "password",
            "role",
            "status",
            "replicas",
            "storageClass"
        }) {
            assertFalse(ProtectedToolParameterFilter.isProtected(key), key + " must remain a business parameter");
        }
    }

    @Test
    void copyWithoutProtected_shouldPreserveOnlyBusinessParameters() {
        Map<String, Object> filtered = ProtectedToolParameterFilter.copyWithoutProtected(Map.of(
            "keyword", "gpu",
            "organizationId", "evil-org",
            "release-approved", true,
            "write_allowed", true,
            "password", "business-password"
        ));

        assertTrue(filtered.containsKey("keyword"));
        assertTrue(filtered.containsKey("password"));
        assertFalse(filtered.containsKey("organizationId"));
        assertFalse(filtered.containsKey("release-approved"));
        assertFalse(filtered.containsKey("write_allowed"));
    }
}
