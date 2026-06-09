package com.atlas.mcp;

import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Builds a read-only governance overview for MCP interoperability.
 *
 * <p>中文说明：服务只组合已有的安全 manifest，并输出治理状态。
 * 未来真正接入 MCP {@code tools/call} 时，必须新开独立发布门禁，不能复用本服务偷偷执行工具。</p>
 *
 * <p>This service composes the existing safe manifest. It does not start an
 * MCP server, accept {@code tools/call}, execute Tools, call kube-manager, use
 * LLMs, invoke HITL, or write audit records.</p>
 */
@Service
public class McpGovernanceOverviewService {

    private final McpToolManifestService manifestService;
    private final Clock clock;

    public McpGovernanceOverviewService(McpToolManifestService manifestService) {
        this(manifestService, Clock.systemUTC());
    }

    McpGovernanceOverviewService(McpToolManifestService manifestService, Clock clock) {
        this.manifestService = manifestService;
        this.clock = clock;
    }

    public McpGovernanceOverviewResponse overview() {
        Map<String, Object> manifest = manifestService.buildSafeManifest();
        return McpGovernanceOverviewResponse.of(Instant.now(clock), manifest);
    }
}
