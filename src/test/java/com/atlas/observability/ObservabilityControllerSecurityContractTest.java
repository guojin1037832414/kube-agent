package com.atlas.observability;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Observability 诊断入口的源码级安全契约。
 *
 * <p>FilterChain 是第一道 URL 级防线，方法级授权是第二道防线。这个测试防止未来重构
 * Controller 或 Security matcher 时，审计快照只剩路由层保护。</p>
 */
class ObservabilityControllerSecurityContractTest {

    private static final Path SOURCE = Path.of(
        "src/main/java/com/atlas/observability/ObservabilityController.java"
    );

    @Test
    void snapshotShouldKeepMethodLevelAdminGuard() throws Exception {
        String source = Files.readString(SOURCE);

        assertThat(source).contains("import org.springframework.security.access.prepost.PreAuthorize;");
        assertThat(source).contains("@PreAuthorize(\"hasAnyRole('ADMIN', 'SYS_ADMIN')\")");
        assertThat(source).contains("public ResponseEntity<ApiResponse<Map<String, Object>>> snapshot()");
    }
}
