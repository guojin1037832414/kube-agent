package com.atlas.tool.core;

import java.util.List;

/**
 * Tool 参数声明元数据。
 *
 * <p>中文说明：该 record 是 Atlas Tool 参数契约的第一阶段轻量模型，用于统一描述：
 * canonical 参数名、JSON Schema 类型、是否必填、给 LLM 看的说明，以及 LLM 常见别名。
 * 它是 Tool 参数知识的单一来源，输出给 ToolParameterNormalizer、AtlasToolCallback、
 * ToolRegistry 提示词目录和未来前端调试面板使用，帮助不同入口看到同一套参数形状。</p>
 *
 * <p>安全边界：这里的声明只描述“业务参数长什么样”，不负责身份、权限、HITL、审计、
 * release gate、kube-manager token/orgId 绑定或 HTTP API 授权。LLM 即使根据 description
 * 生成了看似完整的 JSON，也仍然只是候选业务输入，必须继续经过 SafeToolExecutor、
 * ProtectedToolParameterFilter、Tool 自身校验和 kube-manager 权限链路。</p>
 *
 * <p>后续可基于该模型继续演进：ToolParameterNormalizer 读取 aliases 做别名归一化，
 * AtlasToolCallback 读取 name/type/required/description 生成 inputSchema，
 * ReActPromptBuilder 读取 description/example 生成更准确的工具目录。</p>
 */
public record ToolParameterSpec(
    /** canonical 参数名，例如 podName、namespace；这是业务字段名，不是 userId/orgId/token 等受保护控制面字段。 */
    String name,
    /** JSON Schema 类型，例如 string、integer、boolean、object；只用于输入 schema 描述，不代表 Java 侧已经完成强类型校验。 */
    String type,
    /** 参数说明，主要给 LLM / Prompt / 调试面板使用；不能在这里嵌入密钥、token、内部接口或绕过 HITL 的暗示。 */
    String description,
    /** 是否必填。第一阶段不强制替代 BaseTool#getRequiredParams()，只用于 schema 暴露和学习提示。 */
    boolean required,
    /** LLM 常见别名，例如 pod_name、pod、target_name、name；别名归一化不能引入未声明或受保护字段。 */
    List<String> aliases
) {

    /**
     * 创建字符串参数声明。
     *
     * <p>中文说明：当前大多数 kube-manager 查询参数都以字符串进入 Tool，再由 BaseTool
     * 或具体 Tool 做业务转换。这里统一复制 aliases，避免外部可变 List 在运行期被悄悄修改，
     * 导致 Prompt、schema 和执行时校验看到不一致的参数契约。</p>
     */
    public static ToolParameterSpec stringParam(String name,
                                                String description,
                                                boolean required,
                                                List<String> aliases) {
        return new ToolParameterSpec(name, "string", description, required,
            aliases == null ? List.of() : List.copyOf(aliases));
    }
}
