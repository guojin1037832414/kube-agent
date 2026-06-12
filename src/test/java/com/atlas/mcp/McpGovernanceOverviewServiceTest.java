package com.atlas.mcp;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.impl.DeployDeleteTool;
import com.atlas.tool.impl.MigConfigListTool;
import com.atlas.tool.impl.NodeQueryTool;
import com.atlas.tool.impl.SlurmClusterListTool;
import com.atlas.tool.impl.UserQueryTool;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MCP governance overview contract tests.
 */
class McpGovernanceOverviewServiceTest {

    private static final Path SERVICE_SOURCE = Path.of("src/main/java/com/atlas/mcp/McpGovernanceOverviewService.java");
    private static final Path RESPONSE_SOURCE = Path.of("src/main/java/com/atlas/mcp/McpGovernanceOverviewResponse.java");

    @Test
    void overview_shouldDescribeManifestOnlyGovernanceWithoutRuntimeCalls() {
        McpGovernanceOverviewService service = newService();

        McpGovernanceOverviewResponse overview = service.overview();

        assertThat(overview.schemaVersion()).isEqualTo("agent-mcp-governance-overview.v1");
        assertThat(overview.generatedAt()).isEqualTo(Instant.parse("2026-06-09T00:00:00Z"));
        assertThat(overview.governanceStatus()).isEqualTo("MANIFEST_ONLY_NOT_CALLABLE");
        assertThat(overview.manifestMode()).isEqualTo("safe-readonly-manifest");
        assertThat(overview.manifestEndpointExists()).isTrue();
        assertThat(overview.toolSchemaAdapterExists()).isTrue();
        assertThat(overview.mcpServerRuntimeEnabled()).isFalse();
        assertThat(overview.toolsCallEnabled()).isFalse();
        assertThat(overview.externalToolExecutionEnabled()).isFalse();
        assertThat(overview.callerProvidedToolCallAccepted()).isFalse();
        assertThat(overview.totalToolCount()).isEqualTo(5);
        assertThat(overview.exportedToolCount()).isEqualTo(1);
        assertThat(overview.blockedToolCount()).isEqualTo(4);
        assertThat(overview.governanceCards()).extracting(card -> card.get("id"))
            .containsExactly(
                "manifest-export-policy",
                "tool-schema-adapter",
                "mcp-runtime-server",
                "safe-tool-executor-binding",
                "manifest-coverage"
            );
        assertThat(overview.governanceCards()).allSatisfy(card -> assertThat(card)
            .containsEntry("readOnly", true)
            .containsEntry("runtimeMutationAllowed", false)
            .containsEntry("toolExecution", false)
            .containsEntry("externalCalls", false)
            .containsEntry("mcpToolsCall", false));
        assertThat(overview.recommendedWorkflow()).containsExactly(
            "read-mcp-governance-overview",
            "read-safe-manifest",
            "review-exported-read-only-tools",
            "bind-future-tools-call-through-safe-tool-executor",
            "add-hitl-audit-eval-consent-before-runtime-mcp-server"
        );
        assertThat(overview.blockedCapabilities()).contains(
            "mcp-runtime-server",
            "mcp-tools-call",
            "external-agent-tool-execution",
            "write-tool-export",
            "sensitive-read-tool-export",
            "phase2-domain-tool-export"
        );
        assertThat(overview.futureEnablementProtocol())
            .containsEntry("enablementMode", "future-code-release-only")
            .containsEntry("runtimeToggleAllowed", false)
            .containsEntry("callerCanEnableToolsCall", false)
            .containsEntry("defaultIfAnyCheckMissing", "fail-closed-manifest-only");
        assertThat(overview.safety())
            .containsEntry("authenticatedEndpoint", true)
            .containsEntry("readOnly", true)
            .containsEntry("manifestOnly", true)
            .containsEntry("mcpServerRuntimeEnabled", false)
            .containsEntry("toolsCallRuntimeEnabled", false)
            .containsEntry("toolExecution", false)
            .containsEntry("safeToolExecutorInvoked", false)
            .containsEntry("hitlInvocation", false)
            .containsEntry("auditWrite", false)
            .containsEntry("externalCalls", false)
            .containsEntry("llmUsed", false)
            .containsEntry("writeToolExportAllowed", false)
            .containsEntry("sensitiveReadToolExportAllowed", false)
            .containsEntry("phase2DomainToolExportAllowed", false);
        assertThat(overview.privacy())
            .containsEntry("redactedOnly", true)
            .containsEntry("containsRawEndpoint", false)
            .containsEntry("containsAuthorizationHeader", false)
            .containsEntry("containsToken", false)
            .containsEntry("containsRawRequestBody", false);
        assertThat(overview.toString())
            .contains("MANIFEST_ONLY_NOT_CALLABLE", "safe-readonly-manifest")
            .doesNotContain("/api/100002", "Bearer", "secret-token-value", "password");
    }

    @Test
    void source_shouldNotCreateRuntimeMcpCallPlane() throws Exception {
        String serviceSource = Files.readString(SERVICE_SOURCE);
        String responseSource = Files.readString(RESPONSE_SOURCE);

        assertThat(serviceSource)
            .contains("does not start")
            .contains("accept {@code tools/call}")
            .contains("manifestService.buildSafeManifest()")
            .doesNotContain("SafeToolExecutor")
            .doesNotContain("BaseTool")
            .doesNotContain("execute(")
            .doesNotContain("@PostMapping")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient");
        assertThat(responseSource)
            .contains("MANIFEST_ONLY_NOT_CALLABLE")
            .contains("mcp-tools-call")
            .contains("SafeToolExecutor-only-runtime-binding")
            .contains("fail-closed-manifest-only")
            .doesNotContain("import com.atlas.tool.execution.SafeToolExecutor")
            .doesNotContain("SafeToolExecutor safeToolExecutor")
            .doesNotContain("safeToolExecutor.execute")
            .doesNotContain("BaseTool")
            .doesNotContain("execute(")
            .doesNotContain("RestClient")
            .doesNotContain("WebClient");
    }

    private McpGovernanceOverviewService newService() {
        ToolRegistry registry = new ToolRegistry(List.of(
            new NodeQueryTool(null),
            new SlurmClusterListTool(null),
            new MigConfigListTool(null),
            new UserQueryTool(null),
            new DeployDeleteTool(null)
        ), new UserPermissionContext());
        registry.init();
        return new McpGovernanceOverviewService(
            new McpToolManifestService(registry),
            Clock.fixed(Instant.parse("2026-06-09T00:00:00Z"), ZoneOffset.UTC)
        );
    }
}
