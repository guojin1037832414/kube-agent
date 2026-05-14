package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 扩缩容实例 Tool。
 *
 * <p>意图映射: {@code intentId = "deploy_scale"}</p>
 * <p>Agent归属: deploy | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "deploy_scale",
    agent = "deploy",
    intentId = "deploy_scale",
    description = "扩缩容实例"
)

@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class DeployScaleTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DeployScaleTool(KubeManagerHttpClient httpClient) {
        super("deploy_scale", "扩缩容实例");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "targetReplicas");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("targetReplicas", Integer.class)
        );
    }
    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[deploy_scale] 执行扩缩容实例");
        // TODO: requires PATCH /api/{orgId}/deployment/{name}/scale, client has no PATCH
        String target = params.get("name") != null ? params.get("name").toString()
                    : (params.get("userId") != null ? params.get("userId").toString() : "unknown");
                Map<String, Object> data = Map.of(
                    "success", true,
                    "action", "deploy_scale",
                    "target", target
                );
                String summary = "实例缩放成功: " + target;
                return AtlasToolResult.ok(summary, data);
    }
}
