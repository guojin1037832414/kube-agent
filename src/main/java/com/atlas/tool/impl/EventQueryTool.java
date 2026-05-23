package com.atlas.tool.impl;

import com.atlas.http.KubeManagerHttpClient;
import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.annotation.ToolPermission;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pod Warning/异常事件摘要查询 Tool。
 *
 * <p><b>重要边界：</b>本工具当前不直接引入 Kubernetes Java Client，也不伪装成完整的
 * Kubernetes Event API。它只基于 kube-manager 已有 {@code GET /api/{orgId}/pod}
 * 能力，从 Pod 列表记录中的 {@code warning} 字段抽取异常摘要。</p>
 *
 * <p>因此，本工具适合回答“哪些 Pod 有异常事件/Warning”“某个 Pod 的 Warning 是什么”
 * 这类诊断前置问题；暂不支持 Kubernetes 原生 EventList 的
 * fieldSelector、labelSelector、since、type、involvedObjectKind 等能力。</p>
 */
@Component
@AtlasToolMapping(
    name = "event_query",
    agent = "diag",
    intentId = "event_query",
    description = "查询Pod Warning/异常事件摘要",
    httpMethod = "GET",
    apiEndpoints = {"/api/{orgId}/pod"},
    operationType = AtlasToolMapping.OperationType.READ
)
@ToolPermission(ToolPermission.Policy.PUBLIC)
public class EventQueryTool extends BaseTool {

    private static final String LIMITATION = "当前 event_query 基于 kube-manager Pod 列表 warning 字段生成摘要，"
        + "不是完整 Kubernetes EventList；暂不支持 fieldSelector、labelSelector、since、type、involvedObjectKind 等原生 Event 过滤。";

    private final KubeManagerHttpClient httpClient;

    public EventQueryTool(KubeManagerHttpClient httpClient) {
        super("event_query", "查询Pod Warning/异常事件摘要");
        this.httpClient = httpClient;
    }

    @Override
    protected Set<String> getRequiredParams() {
        return Set.of();
    }

    /**
     * 声明 event_query 的参数契约。
     *
     * <p>只声明真实生效的参数：namespace、username、status 会透传给 kube-manager；
     * podName、reason、keyword 会在 kube-agent 本地基于 Pod 名称和 warning 文本过滤。
     * 不声明 kube-manager 当前无法提供的 Kubernetes 原生 Event 参数，避免形成“伪参数”。</p>
     */
    @Override
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam(
                "namespace",
                "Pod 所在命名空间。用户提到 namespace、ns、命名空间时填写该字段；该字段会透传给 kube-manager Pod 查询接口。",
                false,
                List.of("name_space", "ns")
            ),
            ToolParameterSpec.stringParam(
                "podName",
                "Pod 名称或名称片段。用于在 kube-agent 本地筛选 warning 摘要所属 Pod。",
                false,
                List.of("pod_name", "pod", "targetName", "target_name")
            ),
            ToolParameterSpec.stringParam(
                "username",
                "创建或归属用户名称。该字段会透传给 kube-manager Pod 查询接口。",
                false,
                List.of("user", "userName", "user_name", "creator", "owner")
            ),
            ToolParameterSpec.stringParam(
                "status",
                "Pod 状态筛选条件，例如 Pending、Failed、Running、Unknown；该字段会透传给 kube-manager Pod 查询接口。",
                false,
                List.of("phase", "podStatus", "pod_status", "state")
            ),
            ToolParameterSpec.stringParam(
                "reason",
                "Warning/异常原因关键词，例如 FailedScheduling、BackOff、OOMKilled。用于本地匹配 warning 文本。",
                false,
                List.of("eventReason", "event_reason", "warningReason", "warning_reason")
            ),
            ToolParameterSpec.stringParam(
                "keyword",
                "Warning 文本关键词。用户只描述报错片段或异常关键字时填写该字段，用于本地匹配 warning 文本。",
                false,
                List.of("message", "eventMessage", "event_message", "warning", "text")
            )
        );
    }

    @Override
    protected AtlasToolResult doExecute(Map<String, Object> params) {
        try {
            String orgId = resolveOrganizationId(params);
            String path = "/api/" + orgId + "/pod";

            // 只把 kube-manager Pod 接口真实支持/已验证的查询条件放进 query map，禁止手拼 URL。
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("page", "1");
            query.put("limit", "100");
            putIfPresent(query, "namespace", params.get("namespace"));
            putIfPresent(query, "username", params.get("username"));
            putIfPresent(query, "status", params.get("status"));

            Map<String, Object> response = httpClient.get(path, query);
            Object data = extractData(response);
            List<Map<String, Object>> summaries = buildWarningSummaries(data, params);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dataKind", "podWarningSummaries");
            result.put("podWarningSummaries", summaries);
            result.put("count", summaries.size());
            result.put("source", "kube-manager GET " + path + " (PodDTO.warning)");
            result.put("query", query);
            result.put("limitations", LIMITATION);

            return AtlasToolResult.ok("查询到 " + summaries.size() + " 条Pod Warning摘要", result);
        } catch (Exception e) {
            log.error("[event_query] 调用 kube-manager Pod warning 摘要查询失败", e);
            return AtlasToolResult.fail("Pod Warning摘要查询失败，请稍后重试或联系管理员查看后端日志");
        }
    }

    /**
     * 从 kube-manager Pod 列表数据中抽取 warning 摘要。
     *
     * <p>kube-manager 返回结构在不同版本中可能是 result.records，也可能直接是数组；
     * 上游 {@link BaseTool#extractData(Map)} 已经把分页包装尽量展开。这里仍然只接受
     * List 作为可遍历数据，非 List 时返回空摘要，并在 limitations 中说明能力边界。</p>
     */
    private List<Map<String, Object>> buildWarningSummaries(Object data, Map<String, Object> params) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        if (!(data instanceof List<?> records)) {
            return summaries;
        }

        String podNameFilter = normalizeText(params.get("podName"));
        String reasonFilter = normalizeText(params.get("reason"));
        String keywordFilter = normalizeText(params.get("keyword"));

        for (Object item : records) {
            if (!(item instanceof Map<?, ?> record)) {
                continue;
            }
            String podName = firstNonBlank(record, "name", "podName", "pod_name");
            String namespace = firstNonBlank(record, "namespace", "nameSpace", "name_space");
            String status = firstNonBlank(record, "status", "phase");
            String username = firstNonBlank(record, "username", "userName", "creator", "owner");
            String warning = firstNonBlank(record, "warning", "warnings", "eventWarning", "message");

            // 只输出真正带 warning 的 Pod，避免把正常 Pod 包装成“无事件”。
            if (warning.isBlank()) {
                continue;
            }
            if (!containsIgnoreCase(podName, podNameFilter)) {
                continue;
            }
            if (!containsIgnoreCase(warning, reasonFilter)) {
                continue;
            }
            if (!containsIgnoreCase(warning, keywordFilter)) {
                continue;
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("podName", podName);
            summary.put("namespace", namespace);
            summary.put("status", status);
            summary.put("username", username);
            summary.put("warning", warning);
            summaries.add(summary);
        }
        return summaries;
    }

    /**
     * 只有值存在且非空白时才写入 query，避免空字符串污染后端筛选条件。
     */
    private void putIfPresent(Map<String, Object> query, String key, Object value) {
        if (value != null && !value.toString().isBlank()) {
            query.put(key, value.toString());
        }
    }

    /**
     * 从后端记录中按多个候选字段取第一个非空字符串，兼容不同 DTO/Map 命名。
     */
    private String firstNonBlank(Map<?, ?> record, String... keys) {
        for (String key : keys) {
            Object value = record.get(key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return "";
    }

    /**
     * 归一化本地过滤文本，空白值视为“不启用该过滤条件”。
     */
    private String normalizeText(Object value) {
        if (value == null || value.toString().isBlank()) {
            return "";
        }
        return value.toString().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 大小写不敏感包含判断；filter 为空时代表该条件放行。
     */
    private boolean containsIgnoreCase(String text, String normalizedFilter) {
        if (normalizedFilter == null || normalizedFilter.isBlank()) {
            return true;
        }
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.toLowerCase(Locale.ROOT).contains(normalizedFilter);
    }
}
