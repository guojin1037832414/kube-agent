package com.atlas.mcp;

import com.atlas.auth.UserPermissionContext;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.ToolRegistry;
import com.atlas.tool.impl.DeployDeleteTool;
import com.atlas.tool.impl.NodeQueryTool;
import com.atlas.tool.impl.UserQueryTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M5.20 MCP Manifest 安全契约测试。
 *
 * <p>验证 MCP 对外暴露前必须经过 fail-closed 风险门：普通 READ 可以进入 manifest；
 * SENSITIVE_READ、DELETE/ACTION/CREATE/UNKNOWN 等不得导出。</p>
 */
class M520McpManifestSafetyContractTest {

    @Test
    void buildSafeManifest_shouldOnlyExportDeclaredPlainReadTools() {
        ToolRegistry registry = new ToolRegistry(List.of(
            new NodeQueryTool(null),
            new UserQueryTool(null),
            new DeployDeleteTool(null)
        ), new UserPermissionContext());
        registry.init();

        McpToolManifestService service = new McpToolManifestService(registry);
        Map<String, Object> manifest = service.buildSafeManifest();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tools = (List<Map<String, Object>>) manifest.get("tools");
        assertThat(tools).extracting(t -> t.get("name"))
            .contains("node_query")
            .doesNotContain("user_query", "deploy_delete");
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.get("operationType")).isEqualTo(AtlasToolMapping.OperationType.READ.name());
            assertThat(tool.get("requiresConfirmation")).isEqualTo(false);
            assertThat(tool.get("endpointDeclared")).isEqualTo(true);
            assertThat(tool).doesNotContainKey("apiEndpoints");
        });

        @SuppressWarnings("unchecked")
        Map<String, Object> policy = (Map<String, Object>) manifest.get("policy");
        assertThat(policy.get("failClosed")).isEqualTo(true);
        assertThat(policy.get("blockedOperationTypes").toString())
            .contains("SENSITIVE_READ", "DELETE", "ACTION", "UNKNOWN");
    }
}
