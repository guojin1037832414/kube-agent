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
 * 查询TensorBoard列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "tensorboard_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/tensorboard</p>
 */
@Component
@AtlasToolMapping(
    name = "tensorboard_list",
    agent = "query",
    intentId = "tensorboard_list",
    description = "查询TensorBoard列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class TensorBoardListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public TensorBoardListTool(KubeManagerHttpClient httpClient) {
        super("tensorboard_list", "查询TensorBoard列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }


    /**
     * 暴露列表查询的分页与关键词参数契约。
     *
     * <p>该契约与 doExecute 中的 buildListQuery(params) 成对出现，确保 LLM 传入的
     * page / limit / keyword 会真实进入 kube-manager query map。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("TensorBoard 名称、训练任务名称或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/tensorboard";

            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询TensorBoard列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[tensorboard_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询TensorBoard列表失败: " + e.getMessage());
        }
    }
}
