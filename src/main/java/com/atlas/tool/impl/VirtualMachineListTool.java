package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询组织下虚拟机列表 Tool。
 *
 * <p>对应成熟后端 {@code VirtualMachineController#listOrgVirtualMachine}。
 * 该接口为只读 GET，不允许 LLM 通过参数覆盖 organizationId，也不暴露 VM 启动/停止/删除能力。</p>
 */
@Component
@AtlasToolMapping(
    name = "virtual_machine_list",
    agent = "query",
    intentId = "virtual_machine_list",
    description = "查询虚拟机列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/virtual-machine"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class VirtualMachineListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public VirtualMachineListTool(KubeManagerHttpClient httpClient) {
        super("virtual_machine_list", "查询虚拟机列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/virtual-machine", Map.of());
            Object data = extractData(response);
            return AtlasToolResult.ok(listMessage("虚拟机", data), data);
        } catch (Exception e) {
            log.error("[virtual_machine_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("虚拟机列表查询失败: " + e.getMessage());
        }
    }
}
