package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 创建用户 Tool。
 *
 * <p>意图映射: {@code intentId = "user_create"}</p>
 * <p>Agent归属: rbac | 安全级别: P0</p>
 */
@Component
@AtlasToolMapping(
    name = "user_create",
    agent = "rbac",
    intentId = "user_create",
    description = "创建用户"
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class UserCreateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public UserCreateTool(KubeManagerHttpClient httpClient) {
        super("user_create", "创建用户");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("username", "password");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[user_create] 执行创建用户");
        String createdName = params.get("username") != null ? params.get("username").toString() : "unknown";

        try {
            String orgId = organizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/user",
                filterNullParams(params)
            );
            Object data = response.containsKey("result") ? response.get("result") : response;
            String summary = "创建任务 '" + createdName + "' 已提交";
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[user_create] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("用户创建失败: " + e.getMessage());
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
