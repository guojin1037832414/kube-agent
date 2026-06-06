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
 * 申请创建存储卷 Tool。
 *
 * <p>对齐成熟 kube-manager 的 {@code StorageApplyDTO}：
 * {@code areaCode/scope/type/size/displayName/description/message}。这里刻意不透传
 * {@code token/orgId/organizationId/userId/conversationId/sessionId} 等服务端上下文字段，避免把权限边界混入业务 DTO。</p>
 */
@Component
@AtlasToolMapping(
    name = "storage_create",
    agent = "storage",
    intentId = "storage_create",
    description = "申请创建存储卷",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/file/storage"},
    operationType = AtlasToolMapping.OperationType.CREATE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class StorageCreateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public StorageCreateTool(KubeManagerHttpClient httpClient) {
        super("storage_create", "申请创建存储卷");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("size", "type", "scope");
    }

    @Override
    protected Map<String, Class<?>> getParamTypes() {
        return Map.of("size", Integer.class);
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            new ToolParameterSpec(
                "size",
                "integer",
                "申请的存储容量，单位 GB，必须大于 0。",
                true,
                List.of("capacityGb", "storageSize")
            ),
            ToolParameterSpec.stringParam(
                "type",
                "存储类型，必须来自 /file/storage/option 或成熟前端可选项，例如 fileset、yrfs 等。",
                true,
                List.of("storageType")
            ),
            ToolParameterSpec.stringParam(
                "scope",
                "存储归属范围，例如 user 或 org，必须来自成熟前端可选项。",
                true,
                List.of("storageScope")
            ),
            ToolParameterSpec.stringParam(
                "areaCode",
                "可选区域编码；如果用户传 location，会在执行前归一为 areaCode。",
                false,
                List.of("location")
            ),
            ToolParameterSpec.stringParam(
                "displayName",
                "可选展示名称，用于审批和列表展示。",
                false,
                List.of("name")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        int size = params.get("size") instanceof Number n ? n.intValue() : 0;
        String type = trimmed(params.get("type"));
        String scope = trimmed(params.get("scope"));
        String displayName = firstNonBlank(params, "displayName", "name");

        if (type == null) {
            return AtlasToolResult.fail("缺少必填参数: type（存储类型）", "MISSING_STORAGE_TYPE",
                List.of("请先查询存储选项，选择可用的存储类型"));
        }
        if (scope == null) {
            return AtlasToolResult.fail("缺少必填参数: scope（存储归属范围）", "MISSING_STORAGE_SCOPE",
                List.of("请明确申请个人存储还是组织存储"));
        }
        if (size <= 0) {
            return AtlasToolResult.fail("存储大小必须大于 0", "INVALID_SIZE",
                List.of("请提供有效的存储大小，单位 GB，例如 10"));
        }

        log.info("[storage_create] 创建存储申请 displayName={}, type={}, scope={}, size={}GB",
            displayName, type, scope, size);

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> body = buildCreateBody(params, displayName, size, type, scope);
            Map<String, Object> response = httpClient.post("/api/" + orgId + "/file/storage", body);
            Object data = extractData(response);
            return AtlasToolResult.ok("存储申请已提交: " + type + "/" + scope + "，容量 " + size + "GB", data);
        } catch (Exception e) {
            log.error("[storage_create] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("存储申请失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildCreateBody(Map<String, Object> params, String displayName, int size,
                                                String type, String scope) {
        Map<String, Object> body = new LinkedHashMap<>();
        putIfPresent(body, params, "areaCode");
        if (!body.containsKey("areaCode")) {
            putRenamedIfPresent(body, params, "location", "areaCode");
        }
        putIfPresent(body, params, "description");
        putIfPresent(body, params, "message");
        if (displayName != null) {
            body.put("displayName", displayName);
        }
        body.put("size", size);
        body.put("type", type);
        body.put("scope", scope);
        // approved 由后端根据系统资源条件决定，不能由 LLM 参数伪造。
        return body;
    }

    private void putIfPresent(Map<String, Object> body, Map<String, Object> params, String key) {
        Object value = params.get(key);
        if (value != null && !(value instanceof String s && s.isBlank())) {
            body.put(key, value);
        }
    }

    private void putRenamedIfPresent(Map<String, Object> body, Map<String, Object> params, String sourceKey, String targetKey) {
        Object value = params.get(sourceKey);
        if (value != null && !(value instanceof String s && s.isBlank())) {
            body.put(targetKey, value);
        }
    }

    private String firstNonBlank(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            String value = trimmed(params.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String trimmed(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isBlank() ? null : value;
    }
}
