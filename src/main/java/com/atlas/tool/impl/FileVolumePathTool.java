package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * 查询当前组织可用挂载路径。
 *
 * <p>意图映射: {@code intentId = "file_volume_path"}</p>
 * <p>文件挂载路径会暴露组织文件系统结构，并会影响后续部署、训练任务和课程环境的真实落点，因此按敏感只读处理。
 * 本 Tool 不暴露分页、搜索、path、namespace 等参数，避免把准备上下文接口扩大成文件路径枚举入口。</p>
 */
@Component
@AtlasToolMapping(
    name = "file_volume_path",
    agent = "storage",
    intentId = "file_volume_path",
    description = "查询当前组织可用挂载路径",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/file/volume-path"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class FileVolumePathTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public FileVolumePathTool(KubeManagerHttpClient httpClient) {
        super("file_volume_path", "查询当前组织可用挂载路径");
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
            Map<String, Object> response = httpClient.get("/api/" + orgId + "/file/volume-path", Map.of());
            return AtlasToolResult.ok("查询挂载路径完成", extractData(response));
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[file_volume_path] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询挂载路径失败: " + e.getMessage());
        }
    }
}
