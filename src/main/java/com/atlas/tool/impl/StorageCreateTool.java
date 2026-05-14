package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 创建存储卷(PVC) Tool。
 *
 * <p>Agent归属: storage | 安全级别: P1</p>
 */
@Component
@AtlasToolMapping(
    name = "storage_create",
    agent = "storage",
    intentId = "storage_create",
    description = "创建存储卷(PVC)"
)
// P1 PVC 创建会占用存储资源；允许已认证用户提交，配额、命名空间等细粒度限制交由 kube-manager 后端校验。
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class StorageCreateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public StorageCreateTool(KubeManagerHttpClient httpClient) {
        super("storage_create", "创建存储卷(PVC)");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "size");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("size", Integer.class)
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String name = params.get("name") != null ? params.get("name").toString().trim() : "";
        int size = params.get("size") instanceof Number n ? n.intValue() : 0;
        String storageClass = params.get("storageClass") != null ? params.get("storageClass").toString() : "default";

        if (name.isBlank()) {
            return AtlasToolResult.fail("缺少必填参数: name（PVC名称）", "MISSING_NAME",
                List.of("请提供PVC名称，例如: pvc-data-1"));
        }
        if (size <= 0) {
            return AtlasToolResult.fail("存储大小必须大于0", "INVALID_SIZE",
                List.of("请提供有效的存储大小(GB)，例如: 10"));
        }

        log.info("[storage_create] 创建PVC name={}, size={}Gi, class={}", name, size, storageClass);

        try {
            String orgId = organizationId(params);
            Map<String, Object> body = buildCreateBody(params, name, size, storageClass);
            Map<String, Object> response = httpClient.post("/api/" + orgId + "/file/storage", body);
            Object data = response.containsKey("result") ? response.get("result") : response;

            String summary = "存储卷 '" + name + "' (" + size + "Gi, class: " + storageClass + ") 创建任务已提交";
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[storage_create] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("存储卷创建失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildCreateBody(Map<String, Object> params, String name, int size, String storageClass) {
        Map<String, Object> body = filterNullParams(params);
        body.put("name", name);
        body.put("size", size);
        body.put("storageClass", storageClass);
        return body;
    }

    private Map<String, Object> filterNullParams(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (value != null) {
                body.put(key, value);
            }
        });
        return body;
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
