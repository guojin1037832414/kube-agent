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
 * 查询GPU详情列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "gpu_detail_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/gpu-detail</p>
 */
@Component
@AtlasToolMapping(
    name = "gpu_detail_list",
    agent = "query",
    intentId = "gpu_detail_list",
    description = "查询GPU详情列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class GpuDetailListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public GpuDetailListTool(KubeManagerHttpClient httpClient) {
        super("gpu_detail_list", "查询GPU详情列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 GPU 详情列表查询的分页与关键词参数契约。
     *
     * <p>用于锁定 ReAct Action.params 的 canonical 字段，兼容用户说“按名称/关键词查 GPU”。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("page", "页码，默认使用 1。", false, List.of("pageNo", "page_no", "current")),
            ToolParameterSpec.stringParam("limit", "每页数量，默认使用 100。", false, List.of("pageSize", "page_size", "size")),
            ToolParameterSpec.stringParam("keyword", "GPU 型号、节点名称或关键词筛选条件。", false, List.of("name", "search", "kw"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/gpu-detail".replace("{orgId}", orgId);

            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询GPU详情列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[gpu_detail_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询GPU详情列表失败: " + e.getMessage());
        }
    }
}
