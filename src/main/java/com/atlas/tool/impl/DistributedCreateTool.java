package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 创建 Slurm 分布式集群 Tool。
 *
 * <p>成熟后端 {@code BCMController#createSlurmCluster} 接收 {@code SlurmNodeParamDTO}。该 DTO 中虽然
 * 有 {@code organizationId} 字段，但租户边界必须来自 path 中的可信 orgId，不能由 LLM 参数覆盖；
 * 因此本 Tool 只透传 Slurm 创建所需业务字段。</p>
 */
@Component
@AtlasToolMapping(
    name = "distributed_create",
    agent = "deploy",
    intentId = "distributed_create",
    description = "创建 Slurm 分布式集群",
    httpMethod = "POST",
    apiEndpoints = {"/api/{orgId}/bcm/slurm-cluster"},
    operationType = AtlasToolMapping.OperationType.CREATE,
    requiresConfirmation = true
)
@ToolPermission(ToolPermission.Policy.AUTHENTICATED)
public class DistributedCreateTool extends BaseTool {

    private static final List<String> SLURM_BODY_FIELDS = List.of(
        "displayName",
        "clusterName",
        "slurmConfigTemplateId",
        "queues",
        "publicIp",
        "publicPort",
        "privateIp",
        "privatePort",
        "loginNode",
        "workNode"
    );

    private final KubeManagerHttpClient httpClient;

    public DistributedCreateTool(KubeManagerHttpClient httpClient) {
        super("distributed_create", "创建 Slurm 分布式集群");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        // 在 doExecute 中做业务级校验，支持 name -> displayName/clusterName 的历史兼容。
        return Set.of();
    }

    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("displayName", "Slurm 集群展示名称。", true, List.of("name")),
            ToolParameterSpec.stringParam("clusterName", "Slurm 集群内部名称，后端会追加组织前缀。", true, List.of("internalName")),
            new ToolParameterSpec("slurmConfigTemplateId", "integer", "Slurm 配置模板 ID。", true, List.of("templateId")),
            new ToolParameterSpec("queues", "array", "队列名称列表，例如 [\"defq\"]。", true, List.of("queueList")),
            new ToolParameterSpec("assignedUserIds", "array", "分配给该 Slurm 集群的目标用户 ID 列表。", true, List.of("memberIds")),
            new ToolParameterSpec("loginNode", "object", "登录节点对象，来源应为 bcm/all-slurm-nodes 返回的节点。", true, List.of()),
            new ToolParameterSpec("workNode", "array", "工作节点对象列表，来源应为 bcm/all-slurm-nodes 返回的节点。", true, List.of("workNodes"))
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        Map<String, Object> normalized = new LinkedHashMap<>(params);
        normalizeLegacyName(normalized);

        List<String> missing = requiredMissing(normalized,
            "displayName", "clusterName", "slurmConfigTemplateId", "queues", "assignedUserIds", "loginNode", "workNode");
        if (!missing.isEmpty()) {
            return AtlasToolResult.fail(
                "缺少创建 Slurm 集群所需参数: " + missing,
                "MISSING_SLURM_CREATE_PARAMS",
                List.of("请先查询 Slurm 节点和模板，再提供 displayName/clusterName/template/users/queues/loginNode/workNode")
            );
        }

        String createdName = normalized.get("displayName").toString().trim();
        try {
            String orgId = resolveOrganizationId(normalized);
            Map<String, Object> response = httpClient.post(
                "/api/" + orgId + "/bcm/slurm-cluster",
                buildSlurmBody(normalized)
            );
            Object data = extractData(response);
            return AtlasToolResult.ok("Slurm 分布式集群创建请求已提交: " + createdName, data);
        } catch (Exception e) {
            log.error("[distributed_create] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Slurm 分布式集群创建失败: " + e.getMessage());
        }
    }

    private void normalizeLegacyName(Map<String, Object> params) {
        Object name = params.get("name");
        if (name == null || name.toString().isBlank()) {
            return;
        }
        params.putIfAbsent("displayName", name);
        params.putIfAbsent("clusterName", name);
    }

    private List<String> requiredMissing(Map<String, Object> params, String... keys) {
        return java.util.Arrays.stream(keys)
            .filter(key -> {
                Object value = params.get(key);
                if (value == null) {
                    return true;
                }
                if (value instanceof String s) {
                    return s.isBlank();
                }
                if (value instanceof java.util.Collection<?> collection) {
                    return collection.isEmpty();
                }
                return false;
            })
            .toList();
    }

    private Map<String, Object> buildSlurmBody(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (String key : SLURM_BODY_FIELDS) {
            Object value = params.get(key);
            if (value != null) {
                body.put(key, value);
            }
        }
        // 后端 SlurmNodeParamDTO 的字段名是 userId，但 Tool schema 中避免暴露受保护上下文字段名。
        body.put("userId", params.get("assignedUserIds"));
        return body;
    }
}
