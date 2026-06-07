package com.atlas.contract;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.21-79 source-level guard for the public {@code nim_create} Tool entry.
 *
 * <p>The inner NIM write chain is heavily guarded. This contract protects the outer Tool entry so
 * it cannot quietly grow a real HTTP/storage dependency or endpoint while it is still documented as
 * PLACEHOLDER/HOLD.</p>
 */
class M521NimCreateToolEntryStaticContractTest {

    private static final Path NIM_CREATE_TOOL = Path.of(
        "src/main/java/com/atlas/tool/impl/NimCreateTool.java");

    @Test
    void nimCreateToolEntry_shouldRemainPlaceholderWithoutRuntimeIoDependency()
        throws IOException {
        String source = read(NIM_CREATE_TOOL);

        assertThat(source)
            .contains("name = \"nim_create\"")
            .contains("intentId = \"nim_create\"")
            .contains("httpMethod = \"NONE\"")
            .contains("apiEndpoints = {}")
            .contains("operationType = AtlasToolMapping.OperationType.PLACEHOLDER")
            .contains("requiresConfirmation = true")
            .contains("@ToolPermission(ToolPermission.Policy.AUTHENTICATED)")
            .contains("public NimCreateTool()")
            .contains("NimCreateStateMachineSupport.evaluateCurrentPlaceholderHold(params)")
            .contains("AtlasToolResult.fail(")
            .contains("\"UNSUPPORTED_BACKEND_OPERATION\"")
            .contains("result.put(AtlasToolResult.KEY_DATA, Map.of(\"stateMachine\", stateMachine))");

        assertThat(source)
            .doesNotContain("KubeManagerHttpClient")
            .doesNotContain("httpClient")
            .doesNotContain("httpClient.post(")
            .doesNotContain("httpClient.put(")
            .doesNotContain("httpClient.patch(")
            .doesNotContain("httpClient.delete(")
            .doesNotContain("/api/{orgId}/deployment\"")
            .doesNotContain("/api/\" +")
            .doesNotContain("AtlasToolResult.success(");
    }

    @Test
    void nimCreateToolEntry_shouldNotBindRuntimeShortcuts()
        throws IOException {
        String source = read(NIM_CREATE_TOOL);
        List<String> violations = new ArrayList<>();
        scanForForbiddenTokens(source, violations);

        assertThat(violations)
            .as("nim_create public Tool entry must remain a no-I/O placeholder:\n%s",
                String.join("\n", violations))
            .isEmpty();
    }

    private void scanForForbiddenTokens(String source, List<String> violations) {
        List<String> forbidden = List.of(
            "System.getenv(",
            "System.getProperty(",
            "@Value(",
            "@Autowired",
            "@Bean",
            "RestTemplate",
            "WebClient",
            "import java.net.http",
            "new KubeManagerHttpClient",
            "KubeManagerHttpClient ",
            "Elasticsearch",
            "ISysLogService",
            "saveLog(",
            "saveSysLog(",
            "sys_log",
            "8100",
            "POST /api/{orgId}/deployment",
            "result.put(\"writePermitted\", true)",
            "result.put(\"writeExecutionAllowed\", true)",
            "result.put(\"realHttpExecutionAllowed\", true)",
            "result.put(\"writeAttempted\", true)",
            "result.put(\"writeExecuted\", true)",
            "result.put(\"durableReceiptIssued\", true)",
            "result.put(\"releaseEligible\", true)"
        );
        for (String line : source.lines().toList()) {
            if (allowedDocumentationLine(line)) {
                continue;
            }
            for (String token : forbidden) {
                if (line.contains(token)) {
                    violations.add("NimCreateTool.java contains forbidden token: " + token + " :: " + line.trim());
                }
            }
        }
    }

    private boolean allowedDocumentationLine(String line) {
        return line.contains("deployment 创建")
            || line.contains("部署创建")
            || line.contains("Deployment 创建接口");
    }

    private String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
