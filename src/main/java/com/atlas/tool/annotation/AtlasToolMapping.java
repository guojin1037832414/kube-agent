package com.atlas.tool.annotation;

import java.lang.annotation.*;

/**
 * Atlas 工具映射注解 — 声明 Tool 的身份信息、Agent 归属与后端 HTTP 契约。
 *
 * <p>每个实现 {@link com.atlas.tool.core.AtlasTool} 的 Spring Bean 必须标注此注解，
 * 注册中心通过扫描此注解完成自动发现和注册。</p>
 *
 * <p>M5.11 起，本注解开始承载 Tool 到 kube-manager 的静态 HTTP 契约。新增字段均保留
 * 向后兼容默认值，便于按“先小样本验证、再分批铺开”的方式逐步治理全部 Tool。</p>
 *
 * <p>用法示例：</p>
 * <pre>{@code
 * @Component
 * @AtlasToolMapping(
 *     name          = "node_query",
 *     agent         = "query",
 *     description   = "查询所有节点状态",
 *     intentId      = "node_query",
 *     httpMethod    = "GET",
 *     apiEndpoints  = {"/api/{orgId}/node"},
 *     operationType = AtlasToolMapping.OperationType.READ
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

    /**
     * Tool 实际访问 kube-manager 时使用的 HTTP 方法。
     *
     * <p>默认空字符串表示历史 Tool 尚未迁移到 M5.11 HTTP 契约。已声明该字段的 Tool
     * 会被 {@code M511AtlasToolHttpContractTest} 校验，确保注解声明与 {@code doExecute}
     * 方法中的真实 {@code KubeManagerHttpClient#get/post/delete/...} 调用一致。</p>
     */
    String httpMethod() default "";

    /**
     * Tool 可能访问的 kube-manager API 路径模板。
     *
     * <p>使用 {@code /api/{orgId}/...} 表达租户路径；动态资源 ID 可写成 {@code {var}}。
     * 多路径 fallback Tool 可以声明多个路径。历史未迁移 Tool 保持空数组。</p>
     */
    String[] apiEndpoints() default {};

    /**
     * Tool 的业务操作类型。
     *
     * <p>注意：HTTP POST 不一定只是普通写入，也可能表示删除、停止、重启等高危动作；
     * 因此风险语义必须独立于 HTTP 方法显式声明。</p>
     */
    OperationType operationType() default OperationType.UNKNOWN;

    /**
     * 当前 Tool 是否必须走 Human-in-the-loop 确认。
     *
     * <p>DELETE / HOLD 等高风险 Tool 应显式设置为 {@code true}。本字段后续会接入
     * ToolRegistry Prompt 与执行层强制拦截，M5.11 先通过源码契约建立基础元数据。</p>
     */
    boolean requiresConfirmation() default false;

    /**
     * Tool 业务操作类型枚举。
     */
    enum OperationType {
        /** 历史 Tool 尚未迁移，禁止作为生产安全判断依据。 */
        UNKNOWN,
        /** 纯查询类操作，不应改变 kube-manager/K8s 状态。 */
        READ,
        /** 创建类操作，会新增后端资源。 */
        CREATE,
        /** 修改类操作，会更新已有资源。 */
        UPDATE,
        /** 删除/销毁类操作，即使底层 HTTP 是 POST 也必须归类为 DELETE。 */
        DELETE,
        /** 停止、重启、提交、拉取镜像、扩缩容等非标准高影响动作。 */
        ACTION,
        /** 暂未真实接入后端的占位 Tool，不能返回“已执行成功”的生产语义。 */
        PLACEHOLDER
    }
}
