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
 * 创建 NIM 服务 Tool。
 *
 * <p>专家会诊结论：成熟前端 NIM 一键部署会先选择 NIM 仓库/tag，再合并 NIM 模板，最终调用
 * Deployment 创建接口；当前历史 Tool 直接调用 {@code /api/{orgId}/pod} 缺少成熟前端证据，且会绕过
 * 模板、GPU map 与部署默认值治理。因此本 Tool 暂时 fail-closed，等待后续接入正式 NIM 编排。</p>
 */
@Component
@AtlasToolMapping(
    name = "nim_create",
    agent = "deploy",
    intentId = "nim_create",
    description = "创建 NIM 服务（当前缺少已审计编排链路，默认拒绝执行）",
    httpMethod = "NONE",
    apiEndpoints = {},
    operationType = AtlasToolMapping.OperationType.PLACEHOLDER,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class NimCreateTool extends BaseTool {

    @SuppressWarnings("unused")
    private final KubeManagerHttpClient httpClient;

    public NimCreateTool(KubeManagerHttpClient httpClient) {
        super("nim_create", "创建 NIM 服务");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "name",
                "希望创建的 NIM 服务名称。当前工具只做占位说明，不会直接创建。",
                false,
                List.of("displayName", "serviceName")
            ),
            ToolParameterSpec.stringParam(
                "model",
                "希望部署的 NIM 模型或仓库标识。需要先完成 NIM repository/tag/template 编排后才能执行。",
                false,
                List.of("repository", "repoTag", "image")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String name = params.get("name") == null ? "" : params.get("name").toString().trim();
        Map<String, Object> stateMachine = NimCreateStateMachineSupport.evaluateCurrentPlaceholderHold(params);
        AtlasToolResult result = AtlasToolResult.fail(
            "当前 nim_create 尚未接入成熟前端的 NIM 模板编排链路，已拒绝直接创建: " + name,
            "UNSUPPORTED_BACKEND_OPERATION",
            List.of(
                "请先接入 NIM repository/tag 查询、NIM 模板合并和 deploy_create_instance 编排后再开放该 Tool",
                "必须补齐可信策略快照、服务端 HITL、审计上下文和创建后 readiness 计划后，才能考虑打开真实写入",
                "临时创建 NIM 服务请使用成熟前端的一键部署流程，或由管理员确认模板参数后使用 deployment 创建"
            )
        );
        result.put(AtlasToolResult.KEY_DATA, Map.of("stateMachine", stateMachine));
        return result;
    }
}
