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
 * 删除用户申请的存储卷。
 *
 * <p>成熟 kube-manager 接口为 {@code DELETE /api/{organizationId}/file/deleteStorage?name=xxx}。
 * 旧实现曾把缺失的 name 回退为 userId，这是典型的上下文误用，已经改为严格 fail-closed。</p>
 */
@Component
@AtlasToolMapping(
    name = "storage_delete",
    agent = "storage",
    intentId = "storage_delete",
    description = "删除用户申请的存储卷",
    httpMethod = "DELETE",
    apiEndpoints = {"/api/{orgId}/file/deleteStorage?name={name}"},
    operationType = AtlasToolMapping.OperationType.DELETE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class StorageDeleteTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public StorageDeleteTool(KubeManagerHttpClient httpClient) {
        super("storage_delete", "删除存储卷");
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
                "要删除的存储申请名称。系统内置 user/org/pub 存储不能通过该工具删除。",
                true,
                List.of("storageName", "pvcName")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String name = params.get("name") != null ? params.get("name").toString().trim() : "";
        if (name.isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: name（存储申请名称）", "MISSING_STORAGE_NAME",
                List.of("请提供要删除的存储申请名称，不要传 userId 或模糊关键词"));
        }
        if (Set.of("user", "org", "pub").contains(name)) {
            return AtlasToolResult.fail("系统内置存储不能删除: " + name, "SYSTEM_STORAGE_PROTECTED",
                List.of("请只删除用户申请创建的额外存储"));
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.delete(
                "/api/" + orgId + "/file/deleteStorage",
                Map.of("name", name)
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("存储卷删除请求已发送: " + name, data);
        } catch (Exception e) {
            log.error("[storage_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("存储卷删除失败: " + e.getMessage());
        }
    }
}
