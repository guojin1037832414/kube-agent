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
 * 查询MPI分布式计算任务列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "mpi_job_list"}</p>
 * <p>Agent归属: deploy | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/mpi-job</p>
 */
@Component
@AtlasToolMapping(
    name = "mpi_job_list",
    agent = "deploy",
    intentId = "mpi_job_list",
    description = "查询MPI分布式计算任务列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class MpiJobListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public MpiJobListTool(KubeManagerHttpClient httpClient) {
        super("mpi_job_list", "查询MPI分布式计算任务列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 MPI 任务列表查询的分页与关键词参数契约。
     *
     * <p>这些字段与 kube-manager 列表接口的 query 参数保持一致，供 ReAct 工具目录和
     * ToolParameterNormalizer 使用，避免 LLM 把 page/limit/keyword 手工拼进 URL。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("page", "页码，默认使用 1。", false, List.of("pageNo", "page_no", "current")),
            ToolParameterSpec.stringParam("limit", "每页数量，默认使用 100。", false, List.of("pageSize", "page_size", "size")),
            ToolParameterSpec.stringParam("keyword", "MPI 任务名称或关键词筛选条件。", false, List.of("name", "search", "kw"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/mpi-job".replace("{orgId}", orgId);
            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询MPI分布式计算任务列表完成", data);
        } catch (Exception e) {
            log.error("[mpi_job_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询MPI分布式计算任务列表失败: " + e.getMessage());
        }
    }
}
