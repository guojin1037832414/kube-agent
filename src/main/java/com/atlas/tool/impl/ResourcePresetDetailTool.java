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
 * 查询资源预设详情 Tool，用于解释 EasyFlow 阶段 resourceCode 对应的资源配置。
 *
 * <p>意图映射: {@code intentId = "resource_preset_detail"}</p>
 * <p>Agent 归属: query | 安全级别: P3</p>
 * <p>API 路径: GET /api/{orgId}/resource-preset/{resourcePresetId}</p>
 */
@Component
@AtlasToolMapping(
    name = "resource_preset_detail",
    agent = "query",
    intentId = "resource_preset_detail",
    description = "查询资源预设详情",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/resource-preset/{resourcePresetId}"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class ResourcePresetDetailTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public ResourcePresetDetailTool(KubeManagerHttpClient httpClient) {
        super("resource_preset_detail", "查询资源预设详情");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("resourcePresetId");
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(new ToolParameterSpec(
            "resourcePresetId",
            "integer",
            "资源预设 ID，仅允许正整数，禁止传入路径片段或脚本内容",
            true,
            List.of()
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String resourcePresetId = requirePositiveInteger(params, "resourcePresetId");
            String path = "/api/" + orgId + "/resource-preset/" + resourcePresetId;

            Map<String, Object> response = httpClient.get(path, Map.of());
            Object data = extractData(response);
            return AtlasToolResult.ok("查询资源预设详情完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[resource_preset_detail] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询资源预设详情失败: " + e.getMessage());
        }
    }

    private String requirePositiveInteger(Map<String, Object> params, String name) {
        Object raw = params.get(name);
        if (raw == null) {
            throw new AtlasToolValidationException(name + " 不能为空");
        }

        String value = String.valueOf(raw).trim();
        if (!value.matches("[1-9][0-9]*")) {
            throw new AtlasToolValidationException(name + " 仅支持正整数");
        }
        return value;
    }
}
