package com.atlas.agent;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.atlas.tool.core.ToolRegistry;
import org.springframework.stereotype.Component;

/**
 * 诊断Agent — 负责 诊断 相关 Tool 的执行。
 *
 * <p>Agent类型: diag</p>
 *
 * @deprecated P2 后由 {@link ReactAgent} 替代，保留仅作向后兼容。
 */
@Deprecated(since = "3.1.0-P2", forRemoval = false)
@Component
public class DiagAgent extends AtlasAgentBase {

    public DiagAgent(ToolRegistry toolRegistry) {
        super(toolRegistry);
    }

    @Override
    public String getAgentType() { return "diag"; }

    @Override
    public String getAgentName() { return "DiagAgent (诊断)"; }
}
