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
 * 查询课件关联班级 Tool。
 *
 * <p>班级/教学组织信息可能涉及教学管理关系，虽然后端接口是 GET，
 * Agent 侧仍按敏感读取处理，需要用户确认后再执行。</p>
 */
@Component
@AtlasToolMapping(
    name = "courseware_grade_list",
    agent = "query",
    intentId = "courseware_grade_list",
    description = "查询课件关联班级，属于敏感读取",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/courseware/grade/{coursewareId}"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class CoursewareGradeListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public CoursewareGradeListTool(KubeManagerHttpClient httpClient) {
        super("courseware_grade_list", "查询课件关联班级");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/courseware/grade/" + coursewareId, Map.of());
            return AtlasToolResult.ok("课件关联班级查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[courseware_grade_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("课件关联班级查询失败: " + e.getMessage());
        }
    }
}
