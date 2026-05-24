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
 * Pod列表查询 Tool — 接入真实 kube-manager API。
 *
 * <p>本工具用于查询当前组织下的 Pod 列表，并允许 LLM 按命名空间、Pod名称、
 * 用户名或状态进行轻量筛选。所有筛选参数都通过 query map 传给
 * {@link KubeManagerHttpClient}，避免手工拼接 URL 造成编码或参数污染问题。</p>
 */
@Component
@AtlasToolMapping(
    name = "pod_status",
    agent = "query",
    intentId = "pod_status",
    description = "查询Pod列表",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/pod"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class PodQueryTool extends BaseTool {

    private final KubeManagerHttpClient httpClient;

    public PodQueryTool(KubeManagerHttpClient httpClient) {
        super("pod_status", "查询Pod列表");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 pod_status 的参数契约。
     *
     * <p>这里全部保持可选，原因是“查看 Pod 列表”本身不需要用户提供筛选条件；
     * 当用户表达“查看 ns100002 下的 Pod”“查看 aaaa 相关 Pod”“查看异常 Pod”时，
     * LLM 可以按 schema 生成 canonical 参数名，Normalizer 也能通过 aliases 兼容历史字段。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "namespace",
                "Pod 所在命名空间。用户提到 namespace、ns、命名空间时填写该字段。",
                false,
                List.of("name_space", "ns")
            ),
            ToolParameterSpec.stringParam(
                "podName",
                "Pod 名称或名称片段。用户明确提到某个 Pod 或实例运行单元时填写该字段。",
                false,
                List.of("pod_name", "pod", "name", "targetName", "target_name")
            ),
            ToolParameterSpec.stringParam(
                "username",
                "创建或归属用户名称。用户要求查看某个用户的 Pod 时填写该字段。",
                false,
                List.of("user", "userName", "user_name", "creator", "owner")
            ),
            ToolParameterSpec.stringParam(
                "status",
                "Pod 状态筛选条件，例如 Running、Pending、Failed、Succeeded、Unknown，或用户口语中的异常/失败/运行中。",
                false,
                List.of("phase", "podStatus", "pod_status", "state")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/pod";

            // 使用有序可变 Map 构造 query 参数：基础分页参数固定在前，筛选参数按需追加，便于日志审计。
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("page", "1");
            query.put("limit", "100");
            putIfPresent(query, "namespace", params.get("namespace"));
            putIfPresent(query, "name", firstNonBlank(params.get("podName"), params.get("name")));
            putIfPresent(query, "username", params.get("username"));
            putIfPresent(query, "status", params.get("status"));

            Map<String, Object> response = httpClient.get(path, query);
            Object data = extractData(response);
            return AtlasToolResult.ok(listMessage("Pod", data), data);
        } catch (Exception e) {
            log.error("[pod_status] 调用 kube-manager API 失败", e);
            return AtlasToolResult.fail("Pod列表查询失败: " + e.getMessage());
        }
    }

    /**
     * 只有值存在且非空白时才写入 query，避免把空字符串传给后端造成误筛选。
     */
    private void putIfPresent(Map<String, Object> query, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            query.put(key, value.toString());
        }
    }

    /**
     * 从多个候选字段中取第一个非空值，用于兼容 LLM 可能输出的 canonical/历史字段。
     */
    private Object firstNonBlank(Object... values) {
        for (Object value : values) {
            if (value != null && !value.toString().isBlank()) {
                return value;
            }
        }
        return null;
    }
}
