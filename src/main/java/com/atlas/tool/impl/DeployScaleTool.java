package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
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
    description = "扩缩容实例",
    httpMethod = "PATCH",
    apiEndpoints = {"/api/{orgId}/deployment/scale"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
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
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "name",
                "要扩缩容的 Deployment/实例名称，必须是 kube-manager 中存在的实例名。",
                true,
                List.of("deploymentName", "instanceName", "targetName")
            ),
            new ToolParameterSpec(
                "targetReplicas",
                "integer",
                "目标副本数，非负整数。示例：targetReplicas=0 表示缩容到 0，targetReplicas=3 表示扩容到 3 个副本。",
                true,
                List.of("replicas", "replicaCount", "targetReplicaCount")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[deploy_scale] 执行扩缩容实例");
        String target = params.get("name") != null ? params.get("name").toString().trim() : "";
        if (target.isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: name（部署实例名称）", "MISSING_NAME",
                List.of("请提供要扩缩容的部署实例名称"));
        }

        int replicas = getIntParam(params, "targetReplicas", -1);
        if (replicas < 0) {
            return AtlasToolResult.fail("缺少或非法参数: targetReplicas（目标副本数）", "INVALID_REPLICAS",
                List.of("请提供非负整数副本数，例如 targetReplicas=1"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> body = new LinkedHashMap<>();
            // kube-manager DeploymentDTO 缩放接口读取 name + replicas；namespace 由后端按 orgId 写入。
            body.put("name", target);
            body.put("replicas", replicas);

            Map<String, Object> response = httpClient.patch("/api/" + orgId + "/deployment/scale", body);
            Object data = extractData(response);
            String summary = "实例缩放请求已提交: " + target + " -> " + replicas + " 副本";
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[deploy_scale] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("实例缩放失败: " + e.getMessage());
        }
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultVal) {
        Object v = params.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v != null) {
            try { return Integer.parseInt(v.toString().trim()); }
            catch (NumberFormatException e) { return defaultVal; }
        }
        return defaultVal;
    }
}
