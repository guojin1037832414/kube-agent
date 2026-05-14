package com.atlas.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.atlas.tool.core.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * 查询 Agent — 节点/GPU/镜像/监控/概览。
 *
 * @author Atlas Team
 * @since 3.1.0
 * @deprecated P2 后由 {@link ReactAgent} 替代，保留仅作向后兼容。
 */
@Deprecated(since = "3.1.0-P2", forRemoval = false)
@Component
public class QueryAgent extends AtlasAgentBase {

    public QueryAgent(ToolRegistry toolRegistry) {
        super(toolRegistry);
    }

    @Override
    public String getAgentType() { return "query"; }

    @Override
    public String getAgentName() { return "QueryAgent (查询)"; }
}
