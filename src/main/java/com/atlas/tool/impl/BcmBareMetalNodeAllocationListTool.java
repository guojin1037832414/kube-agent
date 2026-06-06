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
 * 查询当前组织已分配的 BareMetal 节点。
 *
 * <p>BareMetal 节点属于组织内部算力资产。Agent 只读取 mature 后端固定接口，
 * 不接入创建 BareMetal 实例或站点管理员跨组织接口。</p>
 */
@Component
@AtlasToolMapping(
    name = "bcm_bare_metal_node_allocation_list",
    agent = "deploy",
    intentId = "bcm_bare_metal_node_allocation_list",
    description = "查询当前组织已分配的 BareMetal 节点",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/bcm/all-bare-metal-nodes"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class BcmBareMetalNodeAllocationListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public BcmBareMetalNodeAllocationListTool(KubeManagerHttpClient httpClient) {
        super("bcm_bare_metal_node_allocation_list", "查询当前组织已分配的 BareMetal 节点");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/bcm/all-bare-metal-nodes", Map.of());
            return AtlasToolResult.ok("BCM BareMetal 节点分配查询完成", extractData(response));
        } catch (Exception e) {
            log.error("[bcm_bare_metal_node_allocation_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("BCM BareMetal 节点分配查询失败: " + e.getMessage());
        }
    }
}
