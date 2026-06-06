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
 * 查询课件详情 Tool。
 *
 * <p>对应 mature 后端 {@code GET /api/{orgId}/courseware/info/{coursewareId}}。
 * 本 Tool 只读取课件元数据和详情，不上传资料、不保存、不删除、不分配课件。</p>
 */
@Component
@AtlasToolMapping(
    name = "courseware_detail",
    agent = "query",
    intentId = "courseware_detail",
    description = "查询课件详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/courseware/info/{coursewareId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class CoursewareDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public CoursewareDetailTool(KubeManagerHttpClient httpClient) {
        super("courseware_detail", "查询课件详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("coursewareId");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(new ToolParameterSpec(
            "coursewareId",
            "integer",
            "课件 ID，来源应为 courseware_list 返回的数字 ID。",
            true,
            List.of("id", "courseware_id", "courseId", "course_id")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String coursewareId = CoursewareQuerySupport.positiveCoursewareId(params);
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/courseware/info/" + coursewareId, Map.of());
            return AtlasToolResult.ok("课件详情查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[courseware_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("课件详情查询失败: " + e.getMessage());
        }
    }
}
