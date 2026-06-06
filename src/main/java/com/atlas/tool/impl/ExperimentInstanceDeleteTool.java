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
 * 删除实验实例 Tool。
 *
 * <p>专家会诊结论：当前成熟前端未暴露实验实例删除调用，成熟后端源码也未检索到可审计的
 * experiment instance delete controller。删除属于 P0 高风险动作，因此本 Tool 采用 fail-closed：
 * 保留意图入口和审批可见性，但在后端 API 明确前不发送任何 HTTP 请求。</p>
 */
@Component
@AtlasToolMapping(
    name = "experiment_instance_delete",
    agent = "deploy",
    intentId = "experiment_instance_delete",
    description = "删除实验实例（当前后端未提供已审计接口，默认拒绝执行）",
    httpMethod = "NONE",
    apiEndpoints = {},
    operationType = AtlasToolMapping.OperationType.PLACEHOLDER,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class ExperimentInstanceDeleteTool extends BaseTool {

    @SuppressWarnings("unused")
    private final KubeManagerHttpClient httpClient;

    public ExperimentInstanceDeleteTool(KubeManagerHttpClient httpClient) {
        super("experiment_instance_delete", "删除实验实例");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "id",
                "希望删除的实验实例 ID。当前 kube-manager 未提供已审计删除接口，本工具会拒绝直接执行。",
                true,
                List.of("instanceId", "experimentInstanceId", "targetId")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String id = params.get("id").toString().trim();
        return AtlasToolResult.fail(
            "当前 kube-manager 未暴露已审计的实验实例删除接口，已拒绝执行 experiment_instance_delete: " + id,
            "UNSUPPORTED_BACKEND_OPERATION",
            List.of(
                "请先在 kube-manager 增加并审计实验实例删除 API，再接入该 Tool",
                "临时处理请使用成熟前端已支持的启动/关闭流程，并人工确认资源回收状态"
            )
        );
    }
}
