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
 * 禁用用户账号 Tool。
 *
 * <p>禁用会影响目标用户登录、任务提交和资源访问，必须作为高风险动作处理。这里不支持模糊用户名
 * 直接禁用，要求先查清目标 ID，再经人工确认执行。</p>
 */
@Component
@AtlasToolMapping(
    name = "user_disable",
    agent = "rbac",
    intentId = "user_disable",
    description = "禁用用户账号",
    httpMethod = "PUT",
    apiEndpoints = {"/api/{orgId}/user/disable/{id}"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class UserDisableTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public UserDisableTool(KubeManagerHttpClient httpClient) {
        super("user_disable", "禁用用户账号");
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
            "要禁用的目标用户 ID。该操作会影响账号可用性，必须经管理员确认。",
            true,
            List.of("targetUserId", "targetId")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String targetId = UserRiskMutationSupport.targetUserId(params);
        try {
            String path = "/api/" + resolveOrganizationId(params) + "/user/disable/" + targetId;
            Map<String, Object> response = httpClient.put(path, Map.of());
            return AtlasToolResult.ok("用户禁用请求已提交: " + targetId, extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[user_disable] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("用户禁用失败: " + e.getMessage());
        }
    }
}
