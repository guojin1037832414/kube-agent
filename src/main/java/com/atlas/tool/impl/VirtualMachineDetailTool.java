package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 按名称查询虚拟机详情 Tool。
 *
 * <p>VM 名称进入 URL path，执行前通过 {@link VirtualMachineQuerySupport}
 * 统一做路径片段安全校验和编码，防止模型输出 "../" 之类内容穿透目标接口。</p>
 */
@Component
@AtlasToolMapping(
    name = "virtual_machine_detail",
    agent = "query",
    intentId = "virtual_machine_detail",
    description = "按名称查询虚拟机详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/virtual-machine/{name}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class VirtualMachineDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public VirtualMachineDetailTool(KubeManagerHttpClient httpClient) {
        super("virtual_machine_detail", "按名称查询虚拟机详情");
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
                "要查询详情的虚拟机名称，仅表示 VM 名称，不是 Deployment、Pod、Node 或用户名。",
                true,
                List.of("vmName", "vm_name", "virtualMachineName", "virtual_machine_name", "targetName", "target_name")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String encodedName = VirtualMachineQuerySupport.encodedVmName(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/virtual-machine/" + encodedName, Map.of());
            Object data = extractData(response);
            return AtlasToolResult.ok("虚拟机详情查询完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[virtual_machine_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("虚拟机详情查询失败: " + e.getMessage());
        }
    }
}
