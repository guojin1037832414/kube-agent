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
 * 查询存储选项配置 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "file_storage_option"}</p>
 * <p>Agent归属: storage | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/file/storage/option</p>
 */
@Component
@AtlasToolMapping(
    name = "file_storage_option",
    agent = "storage",
    intentId = "file_storage_option",
    description = "查询存储选项配置"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class FileStorageOptionTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileStorageOptionTool(KubeManagerHttpClient httpClient) {
        super("file_storage_option", "查询存储选项配置");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/{orgId}/file/storage/option".replace("{orgId}", orgId);

            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("查询存储选项配置完成", data);
        } catch (Exception e) {
            log.error("[file_storage_option] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询存储选项配置失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
