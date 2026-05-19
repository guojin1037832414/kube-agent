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
 * 根据名称查询存储详情 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "file_select_storage"}</p>
 * <p>Agent归属: storage | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/file/selectStorage</p>
 */
@Component
@AtlasToolMapping(
    name = "file_select_storage",
    agent = "storage",
    intentId = "file_select_storage",
    description = "根据名称查询存储详情"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class FileSelectStorageTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileSelectStorageTool(KubeManagerHttpClient httpClient) {
        super("file_select_storage", "根据名称查询存储详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/file/selectStorage".replace("{orgId}", orgId);

            Object nameParam = params.get("name");
            if (nameParam != null && !nameParam.toString().isBlank()) {
                path += "?name=" + nameParam;
            }
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("根据名称查询存储详情完成", data);
        } catch (Exception e) {
            log.error("[file_select_storage] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("根据名称查询存储详情失败: " + e.getMessage());
        }
    }
}
