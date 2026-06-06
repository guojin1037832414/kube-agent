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
 * 查询镜像注册处列表 Tool — 接入真实 kube-manager API。
 *
 * <p>意图映射: {@code intentId = "registry_list"}</p>
 * <p>Agent归属: query | 安全级别: P3</p>
 * <p>API路径: GET /api/registry</p>
 */
@Component
@AtlasToolMapping(
    name = "registry_list",
    agent = "query",
    intentId = "registry_list",
    description = "查询镜像注册处列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/registry"},
    operationType = AtlasToolMapping.OperationType.SENSITIVE_READ,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class RegistryListTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public RegistryListTool(KubeManagerHttpClient httpClient) {
        super("registry_list", "查询镜像注册处列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }


    /**
     * Registry 注册处列表是站点级配置查询，不是组织内 repository 分页列表。
     *
     * <p>成熟后端只接收 {@code keyWord}，用于按镜像注册处名称/地址筛选；产品镜像目录由
     * {@code /api/{orgId}/repository} 和 {@code image_repository} 等其它 Tool 承担。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(ToolParameterSpec.stringParam(
            "keyWord",
            "镜像注册处名称、地址或关键字筛选条件。",
            false,
            List.of("keyword", "key_word", "name", "search", "registry")
        ));
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String path = "/api/registry";

            Map<String, Object> response = httpClient.get(path, buildRegistryQuery(params));
            Object data = extractData(response);
            return AtlasToolResult.ok("查询镜像注册处列表完成", data);
        } catch (AtlasToolValidationException e) {
            throw e;
        } catch (Exception e) {
            log.error("[registry_list] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("查询镜像注册处列表失败: " + e.getMessage());
        }
    }

    private Map<String, Object> buildRegistryQuery(Map<String, Object> params) {
        Object raw = params.get("keyWord");
        if (raw == null || raw.toString().isBlank()) {
            raw = params.get("keyword");
        }
        if (raw == null || raw.toString().isBlank()) {
            return Map.of();
        }
        return Map.of("keyWord", raw.toString().trim());
    }
}
