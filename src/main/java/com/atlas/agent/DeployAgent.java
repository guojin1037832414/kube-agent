package com.atlas.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.atlas.tool.core.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * 部署Agent — 负责 部署 相关 Tool 的执行。
 *
 * <p>Agent类型: deploy</p>
 *
 * @deprecated P2 后由 {@link ReactAgent} 替代，保留仅作向后兼容。
 */
@Deprecated(since = "3.1.0-P2", forRemoval = false)
@Component
public class DeployAgent extends AtlasAgentBase {

    public DeployAgent(ToolRegistry toolRegistry) {
        super(toolRegistry);
    }

    @Override
    public String getAgentType() { return "deploy"; }

    @Override
    public String getAgentName() { return "DeployAgent (部署)"; }
}
