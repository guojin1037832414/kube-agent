package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 创建分布式计算任务 Tool。
 *
 * <p>意图映射: {@code intentId = "distributed_create"}</p>
 * <p>Agent归属: deploy | 安全级别: P1</p>
 */
@Component
@AtlasToolMapping(
    name = "distributed_create",
    agent = "deploy",
    intentId = "distributed_create",
    description = "创建分布式计算任务",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/bcm/slurm-cluster"},
    operationType = AtlasToolMapping.OperationType.CREATE,
    requiresConfirmation = true
)

@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class DistributedCreateTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public DistributedCreateTool(KubeManagerHttpClient httpClient) {
        super("distributed_create", "创建分布式计算任务");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of("name");
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        log.info("[distributed_create] 执行创建分布式计算任务");
        String createdName = params.get("name") != null ? params.get("name").toString() : "unknown";

        try {
            String orgId = resolveOrganizationId(params);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/bcm/slurm-cluster",
                filterNullParams(params)
            );
            Object data = extractData(response);
            String summary = "创建任务 '" + createdName + "' 已提交";
            return AtlasToolResult.ok(summary, data);
        } catch (Exception e) {
            log.error("[distributed_create] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("分布式计算任务创建失败: " + e.getMessage());
        }
    }

    private Map<String, Object> filterNullParams(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        params.forEach((key, value) -> {
            if (value != null) {
                body.put(key, value);
            }
        });
        return body;
    }
}
