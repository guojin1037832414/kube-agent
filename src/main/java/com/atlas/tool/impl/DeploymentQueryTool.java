package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deployment列表查询 Tool — 接入真实 kube-manager API。
 *
 * <p>在哥哥的平台语义里，“实例”对应 Deployment，而不是 Pod。本工具专门负责
 * Deployment/实例列表查询，并允许按实例名称、命名空间、用户名和状态筛选。
 * 参数统一放入 query map，禁止手工拼接 URL 查询串。</p>
 */
@Component
@AtlasToolMapping(
    name = "deployment_status",
    agent = "query",
    intentId = "deployment_status",
    description = "查询Deployment列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DeploymentQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DeploymentQueryTool(KubeManagerHttpClient httpClient) {
        super("deployment_status", "查询Deployment列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 deployment_status 的参数契约。
     *
     * <p>全部筛选项保持可选，保证“查看所有实例/Deployment”这种零参数查询仍然可直接执行。
     * 同时通过 aliases 兼容 LLM 常见输出：deploymentName、instanceName、deploy 等都会被
     * schema-first normalizer 识别为实例名称语义。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "name",
                "Deployment/实例名称或名称片段。注意：本系统中的“实例”指 Deployment，不是 Pod。",
                false,
                List.of("deploymentName", "deployment_name", "deployment", "deploy", "deployName", "instanceName", "instance_name", "targetName", "target_name")
            ),
            ToolParameterSpec.stringParam(
                "namespace",
                "Deployment 所在命名空间。用户提到 namespace、ns、命名空间时填写该字段。",
                false,
                List.of("name_space", "ns")
            ),
            ToolParameterSpec.stringParam(
                "username",
                "创建或归属用户名称。用户要求查看某个用户创建的实例/Deployment 时填写该字段。",
                false,
                List.of("user", "userName", "user_name", "creator", "owner")
            ),
            ToolParameterSpec.stringParam(
                "status",
                "Deployment/实例状态筛选条件，例如 Running、Stopped、Failed、异常、运行中等。",
                false,
                List.of("deploymentStatus", "deployment_status", "instanceStatus", "instance_status", "state")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/deployment";

            // 使用 LinkedHashMap 保持日志中参数顺序稳定，便于排查后端真实收到的筛选条件。
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("page", "1");
            query.put("limit", "100");
            putIfPresent(query, "name", params.get("name"));
            putIfPresent(query, "namespace", params.get("namespace"));
            putIfPresent(query, "username", params.get("username"));
            putIfPresent(query, "status", params.get("status"));

            Map<String, Object> response = httpClient.get(path, query);
            Object data = extractData(response);
            return AtlasToolResult.ok(listMessage("Deployment", data), data);
        } catch (Exception e) {
            log.error("[deployment_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Deployment列表查询失败: " + e.getMessage());
        }
    }

    /**
     * 只有值存在且非空白时才写入 query，避免空字符串污染后端筛选条件。
     */
    private void putIfPresent(Map<String, Object> query, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            query.put(key, value.toString());
        }
    }
}
