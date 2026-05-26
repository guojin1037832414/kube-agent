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
 * 查询镜像仓库列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "image_repository"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/image/repository</p>
 */
@Component
@AtlasToolMapping(
    name = "image_repository",
    agent = "query",
    intentId = "image_repository",
    description = "查询镜像仓库列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/image/repository"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ImageRepositoryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ImageRepositoryTool(KubeManagerHttpClient httpClient) {
        super("image_repository", "查询镜像仓库列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 page/limit-only 参数契约。
     *
     * <p>本 Tool 只允许模型控制分页参数，不开放 keyword/name/search/kw 等搜索入口。
     * 这样既能让 ReAct/LLM 以结构化方式控制分页，又不会把低风险只读列表扩大为
     * 公开搜索或批量探测面。执行层同步使用 {@link #buildPageLimitOnlyQuery(Map, int)}，
     * 保证 Tool Schema 与真实 HTTP 请求参数保持一致。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return pageLimitOnlyParameterSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/{orgId}/image/repository".replace("{orgId}", orgId);
            Map<String, Object> response = httpClient.get(path, buildPageLimitOnlyQuery(params, 100));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询镜像仓库列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[image_repository] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询镜像仓库列表失败: " + e.getMessage());
        }
    }
}
