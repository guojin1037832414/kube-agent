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
 * 查询课程学习状态 Tool。
 *
 * <p>该接口只读取课程实例/学习环境状态，不创建、暂停、恢复、重置或删除课程环境。
 * 由于返回内容可能包含个人学习环境状态，Agent 侧按敏感读取处理并要求 HITL 确认。</p>
 */
@Component
@AtlasToolMapping(
    name = "courseware_learning_status",
    agent = "query",
    intentId = "courseware_learning_status",
    description = "查询课程学习状态，属于敏感读取",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/learn/deployment/status/{coursewareId}"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class CoursewareLearningStatusTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public CoursewareLearningStatusTool(KubeManagerHttpClient httpClient) {
        super("courseware_learning_status", "查询课程学习状态");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/learn/deployment/status/" + coursewareId, Map.of());
            return AtlasToolResult.ok("课程学习状态查询完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[courseware_learning_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("课程学习状态查询失败: " + e.getMessage());
        }
    }
}
