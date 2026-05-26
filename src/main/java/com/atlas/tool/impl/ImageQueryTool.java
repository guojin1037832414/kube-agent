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
 * 查询镜像资源列表 Tool。
 *
 * <p>意图映射: {@code intentId = "image_query"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 */
@Component
@AtlasToolMapping(
    name = "image_query",
    agent = "query",
    intentId = "image_query",
    description = "查询镜像资源列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class ImageQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ImageQueryTool(KubeManagerHttpClient httpClient) {
        super("image_query", "查询镜像资源列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 image_query 的标准列表查询参数契约。
     *
     * <p>镜像资源查询允许用户按镜像名称、仓库地址或标签关键词筛选。
     * schema 只暴露 page/limit/keyword，具体编码交给 HTTP client 统一处理。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("镜像名称、仓库地址、镜像标签或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            // M5.5 多租户安全治理：orgId 必须来自可信 ThreadLocal，禁止使用 params.organizationId。
            String orgId = resolveOrganizationId(params);

            String path = "/api/" + orgId + "/image";
            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);

            return AtlasToolResult.ok("镜像查询完成", data);
        } catch (Exception e) {
            log.error("[image_query] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("镜像查询失败: " + e.getMessage());
        }
    }
}
