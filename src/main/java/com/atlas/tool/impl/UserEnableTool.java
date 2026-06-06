package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启用用户账号 Tool。
 *
 * <p>启用会恢复目标账号登录和资源使用能力，属于权限面高风险动作。Tool 只接受显式目标用户 ID，
 * 组织 ID 始终来自可信上下文，并通过 HITL 展示精确目标后才允许执行。</p>
 */
@Component
@AtlasToolMapping(
    name = "user_enable",
    agent = "rbac",
    intentId = "user_enable",
    description = "启用用户账号",
    httpMethod = "PUT",
    apiEndpoints = {"/api/{orgId}/user/enable/{id}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class UserEnableTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public UserEnableTool(KubeManagerHttpClient httpClient) {
        super("user_enable", "启用用户账号");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(ToolParameterSpec.stringParam(
            "id",
            "要启用的目标用户 ID。必须显式确认目标账号，不能使用当前登录上下文中的 userId。",
            true,
            List.of("targetUserId", "targetId")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String targetId = UserRiskMutationSupport.targetUserId(params);
        try {
            String path = "/api/" + resolveOrganizationId(params) + "/user/enable/" + targetId;
            Map<String, Object> response = httpClient.put(path, Map.of());
            return AtlasToolResult.ok("用户启用请求已提交: " + targetId, extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[user_enable] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("用户启用失败: " + e.getMessage());
        }
    }
}
