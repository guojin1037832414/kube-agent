package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 存储状态查询 Tool — 接入真实 kube-manager API。
 */
@Component
@AtlasToolMapping(
    name = "storage_status",
    agent = "storage",
    intentId = "storage_status",
    description = "查询 Kubernetes 存储卷/PVC 状态"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class StorageQueryTool extends BaseTool {

    private final KubeManagerHttpClient kubeManagerHttpClient;

    public StorageQueryTool(KubeManagerHttpClient kubeManagerHttpClient) {
        super("storage_status", "查询 Kubernetes 存储卷/PVC 状态");
        this.kubeManagerHttpClient = kubeManagerHttpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> query = new LinkedHashMap<>();
            putIfPresent(query, "clusterId", params.get("clusterId"));
            putIfPresent(query, "namespace", params.get("namespace"));
            query.put("current", 1);
            query.put("size", 100);
            query.put("page", "1");
            query.put("limit", "100");

            Map<String, Object> response;
            try {
                response = kubeManagerHttpClient.get("/api/storage/pageList", query);
            } catch (RestClientResponseException e) {
                if (!isNotFound(e)) {
                    throw e;
                }
                try {
                    response = kubeManagerHttpClient.get("/api/pvc/pageList", query);
                } catch (RestClientResponseException e2) {
                    if (!isNotFound(e2)) {
                        throw e2;
                    }
                    response = kubeManagerHttpClient.get("/api/" + orgId + "/file/storage/option", query);
                }
            }

            Object data = extractRecords(response);
            return AtlasToolResult.ok("存储状态查询完成 (from API)，共 " + count(data) + " 条记录", data);
        } catch (Exception e) {
            log.error("[storage_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("存储状态查询失败: " + e.getMessage());
        }
    }

    private void putIfPresent(Map<String, Object> query, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            query.put(key, value);
        }
    }

    private boolean isNotFound(RestClientResponseException e) {
        return e.getStatusCode().value() == 404;
    }

    private Object extractRecords(Map<String, Object> response) {
        Object data = response.get("data");
        Object records = recordsOf(data);
        if (records != null) return records;
        if (data != null) return data;

        Object result = response.get("result");
        records = recordsOf(result);
        if (records != null) return records;
        if (result != null) return result;

        records = recordsOf(response);
        return records != null ? records : response;
    }

    private Object recordsOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.get("records") != null) return map.get("records");
            if (map.get("list") != null) return map.get("list");
            if (map.get("rows") != null) return map.get("rows");
            if (map.get("content") != null) return map.get("content");
        }
        return null;
    }

    private int count(Object data) {
        if (data instanceof Collection<?> collection) return collection.size();
        if (data instanceof Map<?, ?> map) {
            Object total = map.get("total");
            if (total instanceof Number number) return number.intValue();
            Object list = recordsOf(map);
            if (list instanceof Collection<?> collection) return collection.size();
        }
        return data == null ? 0 : 1;
    }
}
