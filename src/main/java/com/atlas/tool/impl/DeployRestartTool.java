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
 * 重启部署实例 Tool。
 *
 * <p>专家会诊结论：当前成熟 kube-manager 的 DeploymentController 尚未暴露 deployment restart 接口。
 * 因此本 Tool 暂时 fail-closed，不向线上后端发送不存在或未审计的写请求，避免 agent 误导用户。</p>
 */
@Component
@AtlasToolMapping(
    name = "deploy_restart",
    agent = "deploy",
    intentId = "deploy_restart",
    description = "重启实例（当前后端未提供已审计接口，默认拒绝执行）",
    httpMethod = "NONE",
    apiEndpoints = {},
    operationType = AtlasToolMapping.OperationType.PLACEHOLDER,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class DeployRestartTool extends BaseTool {

    @SuppressWarnings("unused")
    private final KubeManagerHttpClient httpClient;

    public DeployRestartTool(KubeManagerHttpClient httpClient) {
        super("deploy_restart", "重启实例");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "name",
                "希望重启的 Deployment/实例名称。当前 kube-manager 未提供已审计的重启接口，本工具会拒绝直接执行。",
                true,
                List.of("deploymentName", "instanceName", "targetName")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String name = params.get("name") == null ? "" : params.get("name").toString().trim();
        // fail-closed：没有真实后端契约时，宁可让用户看到明确限制，也不构造猜测路径。
        return AtlasToolResult.fail(
            "当前 kube-manager 未暴露已审计的实例重启接口，已拒绝执行 deploy_restart: " + name,
            "UNSUPPORTED_BACKEND_OPERATION",
            List.of(
                "请先在 kube-manager 增加并审计实例重启 API，再接入该 Tool",
                "临时处理请使用已审计的扩缩容、删除重建等流程，并逐步确认影响范围"
            )
        );
    }
}
