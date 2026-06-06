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
 * 按存储名称查询已申请存储详情。
 *
 * <p>意图映射: {@code intentId = "file_select_storage"}</p>
 * <p>该接口用于存储扩容前反显原申请信息。Tool 只允许透传成熟前端也使用的 {@code name} 参数，不接受
 * organizationId、scope、userId、page、limit 等会改变权限边界或扩大枚举面的字段。</p>
 */
@Component
@AtlasToolMapping(
    name = "file_select_storage",
    agent = "storage",
    intentId = "file_select_storage",
    description = "按存储名称查询已申请存储详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/file/selectStorage"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class FileSelectStorageTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileSelectStorageTool(KubeManagerHttpClient httpClient) {
        super("file_select_storage", "按存储名称查询已申请存储详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(ToolParameterSpec.stringParam(
            "name",
            "存储名称，必须来自当前组织的存储列表或 kube-manager 前端展示值。",
            true,
            List.of("storageName", "storage_name", "storage", "pvc", "pvcName", "pvc_name", "volumeName", "volume_name", "targetName", "target_name")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String name = FileStorageQuerySupport.requiredTrimmedString(params.get("name"), "name", "存储名称");
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/file/selectStorage", Map.of("name", name));
            return AtlasToolResult.ok("查询存储详情完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[file_select_storage] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询存储详情失败: " + e.getMessage());
        }
    }
}
