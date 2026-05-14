package com.atlas.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.atlas.tool.core.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * 权限Agent — 负责 权限 相关 Tool 的执行。
 *
 * <p>Agent类型: rbac</p>
 *
 * @deprecated P2 后由 {@link ReactAgent} 替代，保留仅作向后兼容。
 */
@Deprecated(since = "3.1.0-P2", forRemoval = false)
@Component
public class RbacAgent extends AtlasAgentBase {

    public RbacAgent(ToolRegistry toolRegistry) {
        super(toolRegistry);
    }

    @Override
    public String getAgentType() { return "rbac"; }

    @Override
    public String getAgentName() { return "RbacAgent (权限)"; }
}
