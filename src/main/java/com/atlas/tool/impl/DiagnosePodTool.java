package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * 诊断Pod/服务故障 Tool。
 *
 * <p>意图映射: {@code intentId = "diagnose_pod"}</p>
 * <p>Agent归属: diag | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "diagnose_pod",
    agent = "diag",
    intentId = "diagnose_pod",
    description = "诊断Pod/服务故障"
)

@ToolPermission(ToolPermission.Policy.PUBLIC)
public class DiagnosePodTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DiagnosePodTool(KubeManagerHttpClient httpClient) {
        super("diagnose_pod", "诊断Pod/服务故障");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            log.info("[diagnose_pod] 执行诊断Pod/服务故障");
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/pod";
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);

            String podName = podName(params);
            if (podName != null) {
                Object matchedPod = findPod(data, podName);
                if (matchedPod != null) {
                    return AtlasToolResult.ok("Pod " + podName + " 诊断完成", Map.of("diagnosis", matchedPod));
                }
            }

            String summary = podName != null
                ? "未找到 Pod " + podName + "，返回 Pod 列表"
                : "Pod 列表诊断数据查询完成";
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[diagnose_pod] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Pod 诊断失败: " + e.getMessage());
        }
    }

    private String podName(Map<String, Object> params) {
        Object value = params.get("podName") != null ? params.get("podName") : params.get("targetName");
        return value != null && !value.toString().isBlank() ? value.toString() : null;
    }

    private Object findPod(Object data, String podName) {
        Object records = recordsOf(data);
        if (records instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (matchesPod(item, podName)) {
                    return item;
                }
            }
        }
        if (data instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (matchesPod(item, podName)) {
                    return item;
                }
            }
        }
        if (matchesPod(data, podName)) {
            return data;
        }
        return null;
    }

    private Object recordsOf(Object value) {
        if (value instanceof Map<?, ?> map) {
            if (map.get("records") != null) return map.get("records");
            if (map.get("list") != null) return map.get("list");
            if (map.get("rows") != null) return map.get("rows");
            if (map.get("content") != null) return map.get("content");
            if (map.get("data") != null) return recordsOf(map.get("data"));
        }
        return null;
    }

    private boolean matchesPod(Object item, String podName) {
        if (item instanceof Map<?, ?> map) {
            return valueMatches(map.get("podName"), podName)
                || valueMatches(map.get("name"), podName)
                || valueMatches(map.get("metadataName"), podName);
        }
        return false;
    }

    private boolean valueMatches(Object value, String podName) {
        return value != null && podName.equals(value.toString());
    }
}
