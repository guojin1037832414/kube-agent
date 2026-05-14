package com.atlas.tool.annotation;

import java.lang.annotation.*;

/**
 * Atlas 工具映射注解 — 声明 Tool 的身份信息与 Agent 归属。
 *
 * <p>每个实现 {@link com.atlas.tool.core.AtlasTool} 的 Spring Bean 必须标注此注解，
 * 注册中心通过扫描此注解完成自动发现和注册。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * @Component
 * @AtlasToolMapping(
 *     name        = "node_query",
 *     agent       = AtlasAgent.QUERY,
 *     description = "查询所有节点状态",
 *     intentId    = "node_query"
 * )
 * public class NodeQueryTool implements AtlasTool { ... }
 * }</pre>
 *
 * @version 3.1.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AtlasToolMapping {

    /**
     * Tool 全局唯一标识符（英文+下划线）。
     *
     * <p>作为 Tool 在注册中心的 key，需保证全局唯一。</p>
     */
    String name();

    /**
     * 所属 Agent 名称。
     *
     * <p>可选值：query / diag / deploy / rbac / storage / network</p>
     */
    String agent();

    /**
     * Tool 功能描述（给 LLM / 管理员看的）。
     */
    String description() default "";

    /**
     * 绑定的意图 ID（对应 intents.yml 中的 intentId）。
     *
     * <p>一个 Tool 可绑定多个意图（以英文逗号分隔），
     * 空字符串表示不绑定特定意图，由 Agent 手动触发。</p>
     */
    String intentId() default "";
}
