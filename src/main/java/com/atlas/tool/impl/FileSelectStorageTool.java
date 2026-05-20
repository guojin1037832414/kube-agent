package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * 存储详情查询参数契约。
     *
     * <p>当前执行逻辑读取的 canonical 字段是 {@code name}。这里的 name 严格表示
     * 存储卷/PVC 名称，不是普通文件名、目录名、镜像名或 StorageClass 名称。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "name",
                "要查询详情的存储卷/PVC 名称。这里的 name 不是文件名、目录名、镜像名称或 StorageClass 名称。",
                true,
                List.of("storageName", "storage_name", "storage", "pvc", "pvcName", "pvc_name", "volumeName", "volume_name", "targetName", "target_name")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/file/selectStorage".replace("{orgId}", orgId);

            Map<String, Object> query = new LinkedHashMap<>();
            query.put("page", "1");
            query.put("limit", "100");

            Object nameParam = params.get("name");
            if (nameParam != null && !nameParam.toString().isBlank()) {
                query.put("name", nameParam.toString());
            }
            Map<String, Object> response = httpClient.get(path, query);
            Object data = extractData(response);
            return AtlasToolResult.ok("根据名称查询存储详情完成", data);
        } catch (Exception e) {
            log.error("[file_select_storage] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("根据名称查询存储详情失败: " + e.getMessage());
        }
    }
}
