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
 * 查询我的配额申请列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "quota_my_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/quota/my</p>
 */
@Component
@AtlasToolMapping(
    name = "quota_my_list",
    agent = "query",
    intentId = "quota_my_list",
    description = "查询我的配额申请列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/quota/my"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class QuotaMyListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public QuotaMyListTool(KubeManagerHttpClient httpClient) {
        super("quota_my_list", "查询我的配额申请列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 暴露“我的配额申请”列表的标准分页与关键词参数契约。
     *
     * <p>该接口用于查询当前组织内当前用户相关的配额申请记录。这里仅做 Tool 层 query 参数透传，
     * 不改变后端权限语义；keyword 的实际匹配字段以后端 kube-manager 接口实现为准。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("配额申请资源、申请状态、项目/命名空间或关键词筛选条件，具体匹配字段以后端接口为准。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/quota/my";

            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询我的配额申请列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[quota_my_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询我的配额申请列表失败: " + e.getMessage());
        }
    }
}
