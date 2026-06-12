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
 * <p>中文说明：本 Controller 只提供 MCP 治理读模型，帮助前端、审计人员和未来外部 Agent 理解
 * kube-agent 当前“哪些能力理论上可导出”。它不会执行 Tool，也不会把外部 MCP 请求桥接到 kube-manager。</p>
 *
 * <p>安全边界：两个端点都是只读查询；不接收 Tool 参数、不接收 HITL marker、不接收 release/audit/write
 * 控制字段，也不会打开 MCP 执行面。真正 MCP tools/call 必须作为单独受审计的运行时能力再引入。</p>
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
     *
     * <p>中文说明：返回的是只读能力清单，里面不会包含内部 kube-manager endpoint，
     * 也不会因为某个 Tool 出现在清单里就授予外部系统执行权限。</p>
     */
    @GetMapping("/manifest")
    public ResponseEntity<ApiResponse<Map<String, Object>>> manifest() {
        return ResponseEntity.ok(ApiResponse.ok(manifestService.buildSafeManifest()));
    }

    /**
     * Query MCP governance status without enabling MCP runtime calls.
     *
     * <p>中文说明：该端点只用于查询治理状态，不接受外部 Tool 调用参数，也不会打开 MCP 执行面。</p>
     *
     * <p>安全边界：响应中的 blockedCapabilities/disabled runtime controls 是治理证据，
     * 不是前端可点击的放行按钮；UI 只能渲染只读状态。</p>
     */
    @GetMapping("/governance/overview")
    public ResponseEntity<ApiResponse<McpGovernanceOverviewResponse>> governanceOverview() {
        return ResponseEntity.ok(ApiResponse.ok(governanceOverviewService.overview()));
    }
}
