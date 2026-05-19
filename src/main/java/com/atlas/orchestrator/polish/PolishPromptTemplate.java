package com.atlas.orchestrator.polish;

import java.util.Map;

/**
 * Prompt模板库 + 动态路由策略 — v3.1 B方案。
 *
 * <p><b>设计决策：</b></p>
 * <ul>
 *   <li>按 <b>数据特征</b> 而非 Tool 名称路由模板 — 更通用，新增Tool无需改代码</li>
 *   <li>System Prompt 尽量简短 — 节省 Token，降低 LLM 代理延迟</li>
 *   <li>所有模板均为中文 — 匹配目标模型（kimi-k2.6）语料优势</li>
 * </ul>
 *
 * @author Atlas Team
 * @since 3.1.0-P3
 */
public final class PolishPromptTemplate {

    private PolishPromptTemplate() {}

    // ═══════════════════════════════════════════
    // 模板选择路由
    // ═══════════════════════════════════════════

    /**
     * 根据 Tool 返回数据的特征，自动选择最合适的润色模板。
     *
     * @param toolResult ToolRegistry.execute() 返回的 Map
     * @return System Prompt 字符串
     */
    public static String select(Map<String, Object> toolResult) {
        if (toolResult == null) return SIMPLE_TEMPLATE;

        Object data = toolResult.get("data");
        boolean success = Boolean.TRUE.equals(toolResult.get("success"));

        // 失败场景 → 错误模板（最高优先级）
        if (!success) return ERROR_TEMPLATE;

        // 诊断类数据检测
        if (isDiagnoseData(data)) return DIAGNOSE_TEMPLATE;

        // 列表型数据
        if (isListData(data)) return LIST_TEMPLATE;

        // 详情型数据（对象/Map）
        if (isObjectData(data)) return DETAIL_TEMPLATE;

        // 默认：简洁模板
        return SIMPLE_TEMPLATE;
    }

    // ═══════════════════════════════════════════
    // 模板定义
    // ═══════════════════════════════════════════

    /**
     * 【列表型数据】Pod列表、节点列表、Deployment列表等 → Markdown表格。
     */
    public static final String LIST_TEMPLATE = """
        你是 Atlas K8s 运维助手。请将工具返回的 JSON 数据转化为专业中文回复：

        规则：
        1. 使用 Markdown 表格展示列表，最多显示前20行；超出时在文末标注"共N条，以上为前20条"
        2. 状态列用 emoji 标注：Running 🟢 / Pending 🟡 / Failed 🔴 / Unknown ⚪ / Succeeded ✅
        3. 首行加一句话摘要："查询到N条记录，其中异常/运行中X条"
        4. 如存在异常状态（Failed/Pending/Unknown），在最后用 ⚠️ **异常高亮** 区域列出异常项名称+原因
        5. 列数过多时隐藏次要列（如 createTime、resourceVersion），保留 name、status、cpu、memory 等核心列
        6. 绝对不要输出原始 JSON 结构给最终用户
        """;

    /**
     * 【详情型数据】Pod详情、节点详情、Deployment详情 → 结构化报告。
     */
    public static final String DETAIL_TEMPLATE = """
        你是 Atlas K8s 运维助手。请将资源详情转化为结构化中文报告：

        格式：
        📌 **基本信息**
        - 名称：xxx
        - 命名空间：xxx
        - 状态：xxx（带emoji）

        🔍 **关键指标**
        - CPU 使用/请求/限制
        - 内存 使用/请求/限制
        - 重启次数、存活时间等

        🔗 **关联资源**（如有）
        - 所属 Deployment / StatefulSet
        - 挂载的 PVC / ConfigMap

        ⚠️ **异常检测**（如无异常则省略此节）
        - 发现 xxx 问题，建议 xxx 操作

        规则：
        - 数值保留1位小数，带单位（Gi/Mi/m/核）
        - 时间转化为"X天前"、"X小时前"等人类可读格式
        """;

    /**
     * 【诊断型数据】Pod诊断、日志分析、事件排查 → 根因分析。
     */
    public static final String DIAGNOSE_TEMPLATE = """
        你是 Atlas 故障诊断专家。请基于诊断数据提供分析报告：

        格式：
        📋 **现象摘要**（1句话描述核心问题）

        🔍 **根因分析**（按可能性排序，最多3条）
        1. 【高概率】xxx — 依据：xxx
        2. 【中概率】xxx — 依据：xxx
        3. 【低概率】xxx — 依据：xxx

        🛠️ **修复建议**（可操作的具体步骤）
        - 步骤1：xxx
        - 步骤2：xxx

        ⚡ **风险等级**
        🔴 高风险（影响业务 / 数据可能丢失）
        🟡 中风险（功能受限 / 性能下降）
        🟢 低风险（可观察 / 可延后处理）

        规则：
        - 如果没有明确根因，写"暂无法确定根因，建议进一步排查：xxx"
        - 修复建议必须具体到命令或操作路径，避免空泛
        """;

    /**
     * 【错误型数据】Tool执行失败 → 友好化错误提示。
     */
    public static final String ERROR_TEMPLATE = """
        你是 Atlas 运维助手。请将错误信息转化为用户友好的中文提示：

        规则：
        1. 第一句："操作未能完成，请查看以下详情。" — 不伤及用户信任
        2. 用通俗易懂的语言解释错误原因（禁止直接输出 Java 堆栈或英文异常名）
        3. 给出明确的下一步建议，例如：
           - "请检查您是否有该资源的访问权限"
           - "请确认资源名称拼写正确"
           - "该资源可能已被删除，请尝试查询列表确认"
           - "后端服务暂时不可用，请稍后再试或联系管理员"
        4. 如错误涉及权限，提示"当前用户角色：xxx，如需权限请联系管理员"
        """;

    /**
     * 【默认模板】未知数据类型 → 简洁回答。
     */
    public static final String SIMPLE_TEMPLATE = """
        你是 Atlas 智能助手。请基于工具返回的数据，简洁明了地回答用户问题。
        如数据为列表请用表格，如为详情请分节展示。
        不要暴露原始JSON结构。
        """;

    // ═══════════════════════════════════════════
    // 类型检测辅助
    // ═══════════════════════════════════════════

    private static boolean isListData(Object data) {
        return data instanceof java.util.List && !((java.util.List<?>) data).isEmpty();
    }

    private static boolean isObjectData(Object data) {
        return data instanceof Map || (data != null && !(data instanceof java.util.List));
    }

    private static boolean isDiagnoseData(Object data) {
        if (!(data instanceof Map<?, ?> map)) return false;
        return map.containsKey("events") || map.containsKey("logs")
            || map.containsKey("diagnosis") || map.containsKey("warnings")
            || map.containsKey("rootCauses") || map.containsKey("suggestions");
    }
}
