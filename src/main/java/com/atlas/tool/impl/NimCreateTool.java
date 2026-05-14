package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 创建NIM服务 Tool。
 *
 * <p>意图映射: {@code intentId = "nim_create"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 */
@Component
@AtlasToolMapping(
    name = "nim_create",
    agent = "deploy",
    intentId = "nim_create",
    description = "创建NIM服务"
)

@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class NimCreateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public NimCreateTool(KubeManagerHttpClient httpClient) {
        super("nim_create", "创建NIM服务");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name", "model");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.ofEntries(
            Map.entry("gpuPercentLimits", Integer.class)
        );
    }
    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[nim_create] 执行创建NIM服务");
        String createdName = params.get("name") != null ? params.get("name").toString() : "unknown";

        try {
            String orgId = organizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/pod",
                filterNullParams(params)
            );
            Object data = response.containsKey("result") ? response.get("result") : response;
            String summary = "创建任务 '" + createdName + "' 已提交";
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[nim_create] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("NIM服务创建失败: " + e.getMessage());
        }
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
