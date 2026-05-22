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
 * 查询上传状态列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "upload_status_list"}</p>
 * <p>Agent归属: storage | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/download/status</p>
 */
@Component
@AtlasToolMapping(
    name = "upload_status_list",
    agent = "storage",
    intentId = "upload_status_list",
    description = "查询上传状态列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class UploadStatusListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public UploadStatusListTool(KubeManagerHttpClient httpClient) {
        super("upload_status_list", "查询上传状态列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 向 ReAct / Tool Schema 暴露标准列表查询参数，避免上传状态查询在工具目录中
     * 只显示模糊描述而执行层仍固定查询第一页。
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return listQueryParameterSpecs("上传任务名称、文件名或关键词筛选条件。");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/download/status";

            Map<String, Object> response = httpClient.get(path, buildListQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询上传状态列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[upload_status_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询上传状态列表失败: " + e.getMessage());
        }
    }
}
