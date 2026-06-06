package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 删除用户 Tool。
 *
 * <p>成熟后端 UserController 暴露 {@code DELETE /api/{organizationId}/user/{id}}。删除账号是 P0
 * 高风险动作，必须经过 HITL，并且目标用户 ID 必须来自业务参数，不能从当前会话 userId 等上下文字段回退。</p>
 */
@Component
@AtlasToolMapping(
    name = "user_delete",
    agent = "rbac",
    intentId = "user_delete",
    description = "删除用户",
    httpMethod = "DELETE",
    apiEndpoints = {"/api/{orgId}/user/{id}"},
    operationType = AtlasToolMapping.OperationType.DELETE,
    requiresConfirmation = true
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
        // 兼容历史调用里的 userId，因此在 doExecute 中做 id/userId 归一化并返回结构化错误。
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "id",
                "要删除的目标用户 ID。必须是待删除账号的 ID，不能使用当前登录用户上下文中的 userId。",
                true,
                List.of("targetUserId", "targetId")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String targetId = resolveTargetId(params);
        if (targetId == null) {
            return AtlasToolResult.fail(
                "缺少必填参数: id（要删除的目标用户 ID）",
                "MISSING_USER_ID",
                List.of("请提供 id 或 userId，并确认这是待删除账号的用户 ID")
            );
        }

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.delete(
                "/api/" + orgId + "/user/" + targetId,
                Map.of()
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("用户删除请求已提交: " + targetId, data);
        } catch (Exception e) {
            log.error("[user_delete] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("用户删除失败: " + e.getMessage());
        }
    }

    private String resolveTargetId(Map<String, Object> params) {
        Object raw = params.get("id");
        if (raw == null) {
            raw = params.get("userId");
        }
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isBlank() ? null : value;
    }
}
