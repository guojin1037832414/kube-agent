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
 * 查询首页NIM服务列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "home_nim_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/public/home-info/nim</p>
 */
@Component
@AtlasToolMapping(
    name = "home_nim_list",
    agent = "query",
    intentId = "home_nim_list",
    description = "查询首页NIM服务列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class HomeNimListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public HomeNimListTool(KubeManagerHttpClient httpClient) {
        super("home_nim_list", "查询首页NIM服务列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }


    /**
     * 首页公共展示接口仅开放 page / limit 分页参数。
     *
     * <p>注意：这里不能复用普通列表的 page / limit / keyword 三件套，
     * 因为 PUBLIC 首页接口一旦开放 keyword，就会从展示入口扩大为公开搜索/探测入口。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return pageLimitOnlyParameterSpecs();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/public/home-info/nim";

            Map<String, Object> response = httpClient.get(path, buildPageLimitOnlyQuery(params, 100));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询首页NIM服务列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[home_nim_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询首页NIM服务列表失败: " + e.getMessage());
        }
    }

}
