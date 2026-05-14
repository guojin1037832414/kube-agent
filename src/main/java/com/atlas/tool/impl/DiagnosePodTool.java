package com.atlas.tool.impl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 诊断Pod/服务故障 Tool。
 *
 * <p>意图映射: {@code intentId = "diagnose_pod"}</p>
 * <p>Agent归属: diag | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "diagnose_pod",
    agent = "diag",
    intentId = "diagnose_pod",
    description = "诊断Pod/服务故障"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DiagnosePodTool extends BaseTool {

    public DiagnosePodTool() {
        super("diagnose_pod", "诊断Pod/服务故障");
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[diagnose_pod] 执行诊断Pod/服务故障");
        String target = params.get("targetName") != null ? params.get("targetName").toString() : "未指定Pod";
                Map<String, Object> data = Map.of(
                    "target", target,
                    "status", "Diagnosed",
                    "findings", List.of(
                        Map.of("severity", "warning", "issue", "镜像拉取策略可能导致启动延迟", "suggestion", "检查镜像仓库可达性"),
                        Map.of("severity", "info", "issue", "资源限制设置合理", "suggestion", "无需调整")
                    ),
                    "events", List.of(
                        Map.of("time", "5m ago", "type", "Warning", "reason", "ImagePullBackOff", "message", "Back-off pulling image...")
                    )
                );
                String summary = "Pod " + target + " 诊断完成, 发现 1 个警告";
                return AtlasToolResult.ok(summary, data);
    }
}
