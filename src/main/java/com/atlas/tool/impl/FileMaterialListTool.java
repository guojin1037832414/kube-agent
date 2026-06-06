package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.exception.AtlasToolValidationException;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 查询文件素材列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "file_material_list"}</p>
 * <p>Agent归属: storage | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/material/folders</p>
 */
@Component
@AtlasToolMapping(
    name = "file_material_list",
    agent = "storage",
    intentId = "file_material_list",
    description = "查询文件素材列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/material/folders"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class FileMaterialListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileMaterialListTool(KubeManagerHttpClient httpClient) {
        super("file_material_list", "查询文件素材列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明文件素材列表查询的分页与关键词参数契约。
     *
     * <p>ReAct 引擎会把该契约暴露给 LLM，促使模型使用 page、limit、keyword 结构化参数，
     * 后续再由 Tool 统一转成 HTTP query 参数。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("page", "页码，默认使用 1。", false, List.of("pageNo", "page_no", "current")),
            ToolParameterSpec.stringParam("limit", "每页数量，默认使用 100。", false, List.of("pageSize", "page_size", "size")),
            ToolParameterSpec.stringParam("keyword", "文件素材名称或关键词筛选条件。", false, List.of("name", "search", "kw"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/material/folders".replace("{orgId}", orgId);

            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询文件素材列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[file_material_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询文件素材列表失败: " + e.getMessage());
        }
    }
}
