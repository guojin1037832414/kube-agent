package com.atlas.tool.core;

import java.util.List;

/**
 * Tool 参数声明元数据。
 *
 * <p>该 record 是 Atlas Tool 参数契约的第一阶段轻量模型，用于统一描述：
 * canonical 参数名、JSON Schema 类型、是否必填、给 LLM 看的说明，以及 LLM 常见别名。
 * 它不负责业务校验、不负责权限、不负责 HTTP API 绑定，只作为 Tool 参数知识的单一来源。</p>
 *
 * <p>后续可基于该模型继续演进：ToolParameterNormalizer 读取 aliases 做别名归一化，
 * AtlasToolCallback 读取 name/type/required/description 生成 inputSchema，
 * ReActPromptBuilder 读取 description/example 生成更准确的工具目录。</p>
 */
public record ToolParameterSpec(
    /** canonical 参数名，例如 podName、namespace。 */
    String name,
    /** JSON Schema 类型，例如 string、integer、boolean、object。 */
    String type,
    /** 参数说明，主要给 LLM / Prompt / 调试面板使用。 */
    String description,
    /** 是否必填。第一阶段不强制替代 BaseTool#getRequiredParams()，只用于 schema 暴露。 */
    boolean required,
    /** LLM 常见别名，例如 pod_name、pod、target_name、name。 */
    List<String> aliases
) {

    /**
     * 创建字符串参数声明。
     */
    public static ToolParameterSpec stringParam(String name,
                                                String description,
                                                boolean required,
                                                List<String> aliases) {
        return new ToolParameterSpec(name, "string", description, required,
            aliases == null ? List.of() : List.copyOf(aliases));
    }
}
