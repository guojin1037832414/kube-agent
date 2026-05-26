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
 * 查询裸金属配置模板列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "bare_metal_template"}</p>
 * <p>Agent归属: deploy | 安全级别: P3</p>
 * <p>API路径: GET /api/bare-metal-config-template</p>
 * <p>备注: </p>
 */
@Component
@AtlasToolMapping(
    name = "bare_metal_template",
    agent = "deploy",
    intentId = "bare_metal_template",
    description = "查询裸金属配置模板列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/bare-metal-config-template"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class BareMetalTemplateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public BareMetalTemplateTool(KubeManagerHttpClient httpClient) {
        super("bare_metal_template", "查询裸金属配置模板列表");
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
            String path = "/api/bare-metal-config-template";

            Map<String, Object> response = httpClient.get(path, buildPageLimitOnlyQuery(params, 100));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询裸金属配置模板列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[bare_metal_template] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询裸金属配置模板列表失败: " + e.getMessage());
        }
    }

}
