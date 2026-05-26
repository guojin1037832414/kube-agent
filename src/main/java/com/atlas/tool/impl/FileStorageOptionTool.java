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
 * 查询存储选项配置 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "file_storage_option"}</p>
 * <p>Agent归属: storage | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/file/storage/option</p>
 */
@Component
@AtlasToolMapping(
    name = "file_storage_option",
    agent = "storage",
    intentId = "file_storage_option",
    description = "查询存储选项配置"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class FileStorageOptionTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileStorageOptionTool(KubeManagerHttpClient httpClient) {
        super("file_storage_option", "查询存储选项配置");
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
            String path = "/api/{orgId}/file/storage/option".replace("{orgId}", orgId);

            Map<String, Object> response = httpClient.get(path, buildPageLimitOnlyQuery(params, 100));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询存储选项配置完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[file_storage_option] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询存储选项配置失败: " + e.getMessage());
        }
    }
}
