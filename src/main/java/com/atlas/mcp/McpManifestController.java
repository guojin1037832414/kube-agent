package com.atlas.mcp;

import com.atlas.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * MCP Manifest 控制器 — 暴露只读安全工具清单。
 *
 * <p>该接口用于 M5.20 阶段验证 MCP 适配安全门：外部系统可以先读取 Manifest，
 * 但不能直接通过本接口执行任何 Tool。真正 MCP Server 接入时必须复用
 * {@link McpToolManifestService#isExportableToMcp(com.atlas.tool.core.ToolRegistry.ToolMetadata)}。</p>
 *
 * @author Atlas Team
 * @since 3.1.0-M5.20
 */
@RestController
@RequestMapping("/api/agent/mcp")
public class McpManifestController {

    private final McpToolManifestService manifestService;
    private final McpGovernanceOverviewService governanceOverviewService;

    public McpManifestController(McpToolManifestService manifestService,
                                 McpGovernanceOverviewService governanceOverviewService) {
        this.manifestService = manifestService;
        this.governanceOverviewService = governanceOverviewService;
    }

    /**
     * 查询 MCP 安全 Manifest。
     */
    @GetMapping("/manifest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> manifest() {
        return ResponseEntity.ok(ApiResponse.ok(manifestService.buildSafeManifest()));
    }

    /**
     * Query MCP governance status without enabling MCP runtime calls.
     *
     * <p>中文说明：该端点只用于查询治理状态，不接受外部 Tool 调用参数，也不会打开 MCP 执行面。</p>
     */
    @GetMapping("/governance/overview")
    public ResponseEntity<ApiResponse<McpGovernanceOverviewResponse>> governanceOverview() {
        return ResponseEntity.ok(ApiResponse.ok(governanceOverviewService.overview()));
    }
}
