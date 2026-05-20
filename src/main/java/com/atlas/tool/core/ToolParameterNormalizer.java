package com.atlas.tool.core;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tool 参数归一化器。
 *
 * <p>LLM 在生成工具调用参数时，经常混用 snake_case、camelCase 或口语化字段名，
 * 例如 {@code pod_name}、{@code target_name}、{@code ns}。但现有 Tool 实现通常读取
 * Java/前端约定的 canonical 参数名，例如 {@code podName}、{@code namespace}。
 * 本组件位于 Agent/ReAct 与 Tool 执行之间，负责把常见别名补齐为 Tool 可识别的规范字段。</p>
 *
 * <p><b>设计边界：</b>当前只做“别名补齐”，不做类型转换、不删除字段、不覆盖 canonical 值、
 * 不做 required 校验，也不处理权限。类型转换和必填校验仍由 {@link BaseTool} 负责。
 * 这样可以避免归一化器变成万能中间层，也降低对既有 Tool 行为的误伤风险。</p>
 *
 * <p><b>风险控制：</b>{@code name} 这类高歧义字段必须按 toolName 做 tool-aware 归一化，
 * 禁止全局把 {@code name} 映射到 {@code podName}。例如 {@code diagnose_pod} 中
 * {@code name} 可以表示 Pod 名，但后续其它 Tool 中可能表示用户、镜像、仓库或任务名。</p>
 */
@Component
public class ToolParameterNormalizer {

    /** Pod 相关工具：允许把高歧义 name 解释为 podName。 */
    private static final Set<String> POD_NAME_TOOLS = Set.of(
        "diagnose_pod",
        "pod_status",
        "pod_query",
        "log_query"
    );

    /** Node 相关工具：允许把高歧义 name 解释为 nodeName。 */
    private static final Set<String> NODE_NAME_TOOLS = Set.of(
        "node_query",
        "node_detail",
        "node_status",
        "node_health"
    );

    /** Deployment/实例相关工具：允许把高歧义 name 解释为 deploymentName。 */
    private static final Set<String> DEPLOYMENT_NAME_TOOLS = Set.of(
        "deployment_query",
        "deployment_detail",
        "deployment_status",
        "deployment_restart",
        "deployment_scale",
        "deploy_restart",
        "deploy_scale"
    );

    /**
     * 对工具参数进行轻量归一化。
     *
     * @param toolName 当前将要调用的工具名，用于处理 {@code name} 这类高歧义字段
     * @param params   原始参数；允许为空
     * @return 新的参数 Map，不修改调用方传入的原始对象
     */
    public Map<String, Object> normalize(String toolName, Map<String, Object> params) {
        Map<String, Object> normalized = new HashMap<>();
        if (params != null && !params.isEmpty()) {
            normalized.putAll(params);
        }
        if (normalized.isEmpty()) {
            return normalized;
        }

        normalizeGlobalAliases(normalized);
        normalizeToolSpecificAliases(toolName, normalized);
        return normalized;
    }

    /**
     * 全局低歧义别名归一化。
     *
     * <p>这里只处理在绝大多数 K8s/API 场景下语义稳定的字段，避免跨工具误伤。</p>
     */
    private void normalizeGlobalAliases(Map<String, Object> params) {
        copyFirstPresentAlias(params, "namespace", "name_space", "ns");
    }

    /**
     * 按工具名归一化高歧义字段。
     *
     * <p>{@code name} 的语义依赖具体工具：Pod 工具中是 Pod 名，Node 工具中是节点名，
     * Deployment 工具中是实例/Deployment 名。后续引入 Tool Schema 后，这里可以改为读取
     * schema 中声明的 aliases，而不是硬编码集合。</p>
     */
    private void normalizeToolSpecificAliases(String toolName, Map<String, Object> params) {
        String normalizedToolName = toolName == null ? "" : toolName.trim();
        if (POD_NAME_TOOLS.contains(normalizedToolName)) {
            copyFirstPresentAlias(params, "podName", "pod_name", "pod", "targetName", "target_name", "name");
            return;
        }
        if (NODE_NAME_TOOLS.contains(normalizedToolName)) {
            copyFirstPresentAlias(params, "nodeName", "node_name", "node", "targetName", "target_name", "name");
            return;
        }
        if (DEPLOYMENT_NAME_TOOLS.contains(normalizedToolName)) {
            copyFirstPresentAlias(params, "deploymentName", "deployment_name", "deployment", "instanceName", "instance_name", "targetName", "target_name", "name");
            return;
        }

        // 未知工具只处理低歧义别名，不把 name 猜成任何具体资源名。
        copyFirstPresentAlias(params, "podName", "pod_name", "pod", "targetName", "target_name");
        copyFirstPresentAlias(params, "nodeName", "node_name", "node");
        copyFirstPresentAlias(params, "deploymentName", "deployment_name", "deployment", "instanceName", "instance_name");
    }

    /**
     * 从别名列表中复制第一个非空值到 canonical 参数名。
     *
     * <p>如果 canonical 参数已经存在且非 null，则绝不覆盖；如果别名值为空字符串，
     * 也不会把它当成有效值补齐。其它原始字段会被保留，方便日志审计和后续兼容。</p>
     */
    private void copyFirstPresentAlias(Map<String, Object> params, String canonicalKey, String... aliases) {
        if (params.containsKey(canonicalKey) && params.get(canonicalKey) != null) {
            return;
        }
        for (String alias : aliases) {
            Object value = params.get(alias);
            if (value != null && !value.toString().isBlank()) {
                params.put(canonicalKey, value);
                return;
            }
        }
    }
}
