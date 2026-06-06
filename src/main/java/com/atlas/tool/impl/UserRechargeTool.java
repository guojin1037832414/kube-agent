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
 * 用户账户充值 Tool。
 *
 * <p>充值会直接改变账户余额，是财务高风险动作。Tool 仅透传成熟 RechargeDTO 的 userId、amount、
 * remark 三个字段，其中 userId 来自显式目标用户参数，amount 按“分”的正整数校验。</p>
 */
@Component
@AtlasToolMapping(
    name = "user_recharge",
    agent = "rbac",
    intentId = "user_recharge",
    description = "为用户账户充值",
    httpMethod = "PUT",
    apiEndpoints = {"/api/{orgId}/user/recharge"},
    operationType = AtlasToolMapping.OperationType.ACTION,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class UserRechargeTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public UserRechargeTool(KubeManagerHttpClient httpClient) {
        super("user_recharge", "为用户账户充值");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("amount");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "id",
                "要充值的目标用户 ID。必须显式确认目标账号，不能使用当前登录上下文中的 userId。",
                true,
                List.of("targetUserId", "targetId")
            ),
            new ToolParameterSpec(
                "amount",
                "integer",
                "充值金额，单位为分，必须是正整数。例如 10000 表示 100 元。",
                true,
                List.of("money", "cents")
            ),
            ToolParameterSpec.stringParam("remark", "充值备注，可选，会原样写入成熟后端账务记录。", false, List.of("reason"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/" + resolveOrganizationId(params) + "/user/recharge";
            Map<String, Object> response = httpClient.put(path, UserRiskMutationSupport.rechargeBody(params));
            return AtlasToolResult.ok("用户充值请求已提交", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[user_recharge] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("用户充值失败: " + e.getMessage());
        }
    }
}
