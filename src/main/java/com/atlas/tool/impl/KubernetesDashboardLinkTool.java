package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询 Kubernetes Dashboard 外部链接 Tool。
 *
 * <p>成熟后端将该接口标记为 {@code SYS_ADMIN_ONLY}，说明它虽然是 GET，
 * 但会暴露运维入口地址。Agent 侧按敏感读取处理：管理员可见，且必须经过 HITL。</p>
 */
@Component
@AtlasToolMapping(
    name = "kubernetes_dashboard_link",
    agent = "query",
    intentId = "kubernetes_dashboard_link",
    description = "查询 Kubernetes Dashboard 外部链接，属于管理员敏感读取",
    httpMethod = "GET",
    apiEndpoints = {"/api/external-link/kubernetes/dashboard"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class KubernetesDashboardLinkTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public KubernetesDashboardLinkTool(KubeManagerHttpClient httpClient) {
        super("kubernetes_dashboard_link", "查询 Kubernetes Dashboard 外部链接");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            new ToolParameterSpec("reason", "string", "可选说明字段，用于审批时记录为什么需要打开 Kubernetes Dashboard。", false, List.of("purpose"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            Map<String, Object> response = httpClient.get("/api/external-link/kubernetes/dashboard");
            Object data = extractData(response);
            return AtlasToolResult.ok("Kubernetes Dashboard 外部链接查询完成", data);
        } catch (Exception e) {
            log.error("[kubernetes_dashboard_link] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询 Kubernetes Dashboard 外部链接失败: " + e.getMessage());
        }
    }
}
