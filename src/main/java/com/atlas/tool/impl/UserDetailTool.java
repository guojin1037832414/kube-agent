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
 * 查询用户详情 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "user_detail"}</p>
 * <p>Agent归属: rbac | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/user</p>
 */
@Component
@AtlasToolMapping(
    name = "user_detail",
    agent = "rbac",
    intentId = "user_detail",
    description = "查询用户详情"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class UserDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public UserDetailTool(KubeManagerHttpClient httpClient) {
        super("user_detail", "查询用户详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("id");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = organizationId(params);
            String path = "/api/{orgId}/user".replace("{orgId}", orgId);

            Object idParam = params.get("id");
            if (idParam != null && !idParam.toString().isBlank()) {
                path += "/" + idParam;
            }
            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = response.containsKey("result") ? response.get("result") : response;
            return AtlasToolResult.ok("查询用户详情完成", data);
        } catch (Exception e) {
            log.error("[user_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询用户详情失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
