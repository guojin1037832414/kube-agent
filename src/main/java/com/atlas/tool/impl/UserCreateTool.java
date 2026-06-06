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
 * 创建用户 Tool。
 *
 * <p>成熟后端 UserController 接收 {@code UserDetailDTO}。账号创建属于 P0 级权限动作，本 Tool
 * 只透传 DTO 明确允许的业务字段，拒绝把 organizationId、userId、token、sessionId 等服务端上下文字段
 * 混入 body，避免 LLM 参数伪造租户或审批上下文。</p>
 */
@Component
@AtlasToolMapping(
    name = "user_create",
    agent = "rbac",
    intentId = "user_create",
    description = "创建用户",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/user"},
    operationType = AtlasToolMapping.OperationType.CREATE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.ADMIN_ONLY)
public class UserCreateTool extends BaseTool {

    private static final List<String> USER_DETAIL_BODY_FIELDS = List.of(
        "username",
        "password",
        "name",
        "introduction",
        "roles",
        "avatar",
        "balance",
        "cpuLimits",
        "memLimits",
        "gpuPerformanceLimits",
        "gpuMemLimits",
        "enabled",
        "accessKey",
        "secretKey"
    );

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
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("username", "新用户登录名。创建账号前必须经管理员确认。", true,
                List.of("loginName", "account", "userName")),
            ToolParameterSpec.stringParam("password", "新用户初始密码。该字段敏感，只能来自明确的管理员输入。", true,
                List.of("initialPassword")),
            ToolParameterSpec.stringParam("name", "新用户展示姓名，可选。", false, List.of("displayName", "realName")),
            ToolParameterSpec.stringParam("roles", "角色编码数组，可选；后端默认会授予基础用户角色。", false,
                List.of("roleCodes", "roleList")),
            new ToolParameterSpec("cpuLimits", "integer", "CPU 配额上限，单位为毫核 m，可选。", false, List.of("cpu")),
            new ToolParameterSpec("memLimits", "integer", "内存配额上限，单位为 MiB，可选。", false, List.of("memory", "mem")),
            new ToolParameterSpec("gpuPerformanceLimits", "integer", "GPU 性能配额上限，可选。", false, List.of("gpuPerformance")),
            new ToolParameterSpec("gpuMemLimits", "integer", "GPU 显存配额上限，单位为 MiB，可选。", false, List.of("gpuMemory"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        String createdName = params.get("username").toString().trim();

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/user",
                buildUserDetailBody(params)
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("用户创建请求已提交: " + createdName, data);
        } catch (Exception e) {
            log.error("[user_create] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("用户创建失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildUserDetailBody(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String key : USER_DETAIL_BODY_FIELDS) {
            Object value = params.get(key);
            if (value != null) {
                body.put(key, value);
            }
        }
        return body;
    }
}
