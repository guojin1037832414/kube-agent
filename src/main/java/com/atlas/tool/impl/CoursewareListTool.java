package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询课件列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "courseware_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/{orgId}/courseware/list</p>
 */
@Component
@AtlasToolMapping(
    name = "courseware_list",
    agent = "query",
    intentId = "courseware_list",
    description = "查询课件列表"
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class CoursewareListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public CoursewareListTool(KubeManagerHttpClient httpClient) {
        super("courseware_list", "查询课件列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/courseware/list";

            Map<String, Object> response = httpClient.get(path, Map.of("page", "1", "limit", "100"));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询课件列表完成", data);
        } catch (Exception e) {
            log.error("[courseware_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询课件列表失败: " + e.getMessage());
        }
    }
}
