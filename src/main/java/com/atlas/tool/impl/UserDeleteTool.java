package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 删除用户 Tool。
 * <p><b>⚠️ 危险操作</b>: P0级, 执行前需用户确认</p>
 *
 * <p>意图映射: {@code intentId = "user_delete"}</p>
 * <p>Agent归属: rbac | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "user_delete",
    agent = "rbac",
    intentId = "user_delete",
    description = "删除用户"
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class UserDeleteTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public UserDeleteTool(KubeManagerHttpClient httpClient) {
        super("user_delete", "删除用户");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("userId");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[user_delete] 执行删除用户");
        String target = params.get("userId") != null ? params.get("userId").toString() : "unknown";

        try {
            String orgId = organizationId(params);
            Map<String, Object> response = httpClient.delete(
                "/api/" + orgId + "/user/" + target,
                Map.of()
            );
            Object data = response.containsKey("result") ? response.get("result") : response;
            String summary = "用户删除成功: " + target;
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[user_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("用户删除失败: " + e.getMessage());
        }
    }

    private String organizationId(Map<String, Object> params) {
        Object value = params.get("organizationId") != null ? params.get("organizationId") : params.get("orgId");
        return value != null && !value.toString().isBlank() ? value.toString() : "100001";
    }
}
