package com.atlas.tool.core;

import com.atlas.tool.annotation.WithDefaults;
import com.atlas.tool.exception.AtlasToolValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.*;
import java.util.function.Supplier;

/**
 * Atlas Tool 抽象基类 — Spring AI Function Calling v1.1.x 适配层。
 *
 * <p><b>核心设计：</b></p>
 * <ul>
 *   <li>子类实现 {@link #doExecute(Map)} 写业务逻辑，入口 {@link #execute(Map)} 已做 AOP 式包装</li>
 *   <li>{@link #execute(Map)} 标注 {@code @Tool}，被 Spring AI {@code MethodToolCallbackProvider}
 *       自动扫描注册为 {@code ToolCallback}</li>
 *   <li>参数校验失败抛出 {@link AtlasToolValidationException}，被 {@link #wrapCall}
 *       捕获转为 {@link AtlasToolResult#fail}，绝不中断 LLM 对话链</li>
 * </ul>
 *
 * <p><b>子类最小实现模板：</b></p>
 * <pre>{@code
 * @Component
 * public class NodeQueryTool extends BaseTool {
 *     public NodeQueryTool() {
 *         super("node_query", "查询集群节点列表及状态");
 *     }
 *     @Override
 *     protected Set<String> getRequiredParams() {
 *         return Set.of("clusterId");
 *     }
 *     @Override
 *     protected AtlasToolResult doExecute(Map<String, Object> params) {
 *         // 业务逻辑...
 *         return AtlasToolResult.ok("查询成功", data);
 *     }
 * }
 * }</pre>
 *
 * @author Atlas Team
 * @since 3.1.0
 */
public abstract class BaseTool implements AtlasTool {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /** Tool 名称（全局唯一，用于 LLM function calling 识别） */
    private final String toolName;

    /** Tool 人类可读描述（LLM 据此判断是否调用此工具） */
    private final String description;

    protected BaseTool(String toolName, String description) {
        this.toolName = Objects.requireNonNull(toolName, "toolName cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
    }

    // ═══════════════════════════════════════════
    // 子类重写区
    // ═══════════════════════════════════════════

    /**
     * 必填参数名集合。execute 入口会自动校验。
     *
     * @return 空集合表示无必填项
     */
    protected abstract Set<String> getRequiredParams();

    /**
     * 业务执行体。由子类实现，内部无需关心异常处理。
     */
    protected abstract AtlasToolResult doExecute(Map<String, Object> params);

    /**
     * 参数类型转换映射（可选重写）。
     * Key=参数名, Value=期望类型。execute 入口会自动做类型转换。
     */
    protected Map<String, Class<?>> getParamTypes() {
        return Map.of();
    }

    // ═══════════════════════════════════════════
    // Spring AI Tool 入口（ public，被 MethodToolCallback 反射调用）
    // ═══════════════════════════════════════════

    /**
     * Spring AI Function Calling 入口。
     *
     * <p>被 {@code MethodToolCallbackProvider} 自动扫描注册。
     * 内部完成：校验 → 默认值 AOP → 业务调用 → 异常兜底 → 结果转换。</p>
     *
     * @param params LLM 传入的参数（JSON → Map，由 Jackson 自动反序列化）
     * @return {@link AtlasToolResult} 强制结构的 Map（Spring AI 会转 JSON 给 LLM）
     */
    /**
     * Spring AI Tool Calling 入口。
     *
     * <p><b>重要</b>：此方法<b>不</b>直接标注 {@code @Tool}。
     * 因为 {@code @Tool.name()} 不支持 SpEL，无法动态绑定子类构造时传入的 toolName。
     * 因此由 {@link ToolRegistry} 在初始化时手动构建 {@link org.springframework.ai.tool.method.MethodToolCallback}，
     * 显式传入 name + description，再注册到 {@link org.springframework.ai.chat.client.ChatClient}。</p>
     *
     * @param params LLM 传入的参数（JSON 对象经 Jackson 反序列化为 Map）
     * @return {@link AtlasToolResult} 结构的 Map（Spring AI 自动转 JSON 返回给 LLM）
     */
    public final Map<String, Object> execute(Map<String, Object> params) {
        long startMs = System.currentTimeMillis();
        return wrapCall(() -> {
            // 1. 参数校验
            validate(params);
            // 2. 类型转换
            convertTypes(params);
            // 3. 业务执行
            AtlasToolResult result = doExecute(params);
            // 4. 附加元数据
            if (result != null) {
                result.withToolName(toolName)
                      .withExecutionTimeMs(System.currentTimeMillis() - startMs);
            }
            return result;
        });
    }

    // ═══════════════════════════════════════════
    // 供 Spring Expression Language 读取（用于 @Tool name/description）
    // ═══════════════════════════════════════════

    public String getToolNameForSpringAi() {
        return toolName;
    }
    public String getDescriptionForSpringAi() {
        return description;
    }

    // ═══════════════════════════════════════════
    // 参数校验
    // ═══════════════════════════════════════════

    /**
     * 校验必填字段。
     */
    protected void validate(Map<String, Object> params) {
        if (params == null) {
            throw new AtlasToolValidationException("参数不能为空", "PARAMS_NULL",
                List.of("请检查 LLM 是否正确传递了参数"));
        }
        Set<String> required = getRequiredParams();
        if (required == null || required.isEmpty()) return;

        List<String> missing = new ArrayList<>();
        for (String key : required) {
            Object v = params.get(key);
            if (v == null || (v instanceof String s && s.isBlank())) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            throw new AtlasToolValidationException(
                "缺少必填参数: " + missing,
                "MISSING_REQUIRED_PARAMS",
                List.of("请提供以下参数: " + String.join(", ", missing))
            );
        }
    }

    /**
     * 校验并转换常见类型。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected void convertTypes(Map<String, Object> params) {
        Map<String, Class<?>> types = getParamTypes();
        if (types == null || types.isEmpty()) return;

        for (Map.Entry<String, Class<?>> e : types.entrySet()) {
            String key = e.getKey();
            Class<?> targetType = e.getValue();
            Object raw = params.get(key);
            if (raw == null) continue;

            if (targetType == Integer.class || targetType == int.class) {
                params.put(key, toInt(raw, key));
            } else if (targetType == Long.class || targetType == long.class) {
                params.put(key, toLong(raw, key));
            } else if (targetType == Double.class || targetType == double.class) {
                params.put(key, toDouble(raw, key));
            } else if (targetType == Boolean.class || targetType == boolean.class) {
                params.put(key, toBoolean(raw, key));
            } else if (targetType.isEnum()) {
                params.put(key, toEnum(raw, (Class<? extends Enum>) targetType, key));
            }
            // String / Map / List 由 Jackson 已处理好，无需转换
        }
    }

    // ═══════════════════════════════════════════
    // 类型转换辅助（健壮，面向 LLM 不严谨的输出）
    // ═══════════════════════════════════════════

    protected int toInt(Object raw, String key) {
        if (raw instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new AtlasToolValidationException(
                "参数 '" + key + "' 期望整数，但收到: " + raw,
                "TYPE_MISMATCH",
                List.of("请将 '" + key + "' 改为有效的整数值"));
        }
    }

    protected long toLong(Object raw, String key) {
        if (raw instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new AtlasToolValidationException(
                "参数 '" + key + "' 期望长整数，但收到: " + raw,
                "TYPE_MISMATCH", List.of("请将 '" + key + "' 改为有效的整数值"));
        }
    }

    protected double toDouble(Object raw, String key) {
        if (raw instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new AtlasToolValidationException(
                "参数 '" + key + "' 期望小数，但收到: " + raw,
                "TYPE_MISMATCH", List.of("请将 '" + key + "' 改为有效的数值"));
        }
    }

    protected boolean toBoolean(Object raw, String key) {
        if (raw instanceof Boolean b) return b;
        String s = raw.toString().trim().toLowerCase();
        if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
        if ("false".equals(s) || "0".equals(s) || "no".equals(s)) return false;
        throw new AtlasToolValidationException(
            "参数 '" + key + "' 期望布尔值，但收到: " + raw,
            "TYPE_MISMATCH", List.of("请将 '" + key + "' 改为 true 或 false"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    protected <E extends Enum<E>> E toEnum(Object raw, Class<E> enumType, String key) {
        if (enumType.isInstance(raw)) return (E) raw;
        String s = raw.toString().trim();
        for (E e : enumType.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(s)) return e;
        }
        throw new AtlasToolValidationException(
            "参数 '" + key + "' 必须是 " + Arrays.toString(enumType.getEnumConstants()) + " 之一，但收到: " + raw,
            "TYPE_MISMATCH",
            List.of("请从以下值中选择: " +
                java.util.Arrays.stream(enumType.getEnumConstants())
                    .map(Enum::name).collect(java.util.stream.Collectors.joining(", "))));
    }

    // ═══════════════════════════════════════════
    // 异常处理核心：绝不抛出让 LLM 对话中断
    // ═══════════════════════════════════════════

    /**
     * AOP 式执行包装。
     *
     * <p>捕获所有异常并转为 {@link AtlasToolResult}：</p>
     * <ul>
     *   <li>{@link AtlasToolValidationException} → 友好提示 + suggestions</li>
     *   <li>其他 RuntimeException → 统一降级信息（建议重试）</li>
     *   <li>Error → 记录后仍降级（避免 JVM 错误拖垮服务）</li>
     * </ul>
     */
    protected Map<String, Object> wrapCall(Supplier<AtlasToolResult> action) {
        try {
            AtlasToolResult result = action.get();
            return result != null ? result : AtlasToolResult.fail("工具返回空结果");
        } catch (AtlasToolValidationException e) {
            log.warn("[{}] 参数校验失败: {}", toolName, e.getMessage());
            return AtlasToolResult.fail(e.getMessage(), e.getErrorCode(), e.getSuggestions());
        } catch (Exception e) {
            log.error("[{}] 执行异常: {}", toolName, e.getMessage(), e);
            return AtlasToolResult.fail(
                "工具执行异常: " + e.getMessage(),
                "TOOL_EXECUTION_ERROR",
                List.of("请稍后重试", "如果持续失败请联系管理员")
            );
        }
    }

    // ═══════════════════════════════════════════
    // 便捷 protected 方法（子类可用）
    // ═══════════════════════════════════════════

    /**
     * 断言参数在有效范围内（如 pageSize > 0）。
     */
    protected void assertPositive(Map<String, Object> params, String key) {
        Object v = params.get(key);
        if (v == null) return; // 非必填字段跳过
        int n = toInt(v, key);
        if (n <= 0) {
            throw new AtlasToolValidationException(
                "参数 '" + key + "' 必须大于 0，当前值: " + n,
                "VALUE_OUT_OF_RANGE",
                List.of("请将 '" + key + "' 改为正整数"));
        }
        params.put(key, n);
    }

    /**
     * 断言参数在集合内（白名单校验）。
     */
    protected void assertIn(Map<String, Object> params, String key, Set<String> allowed) {
        Object v = params.get(key);
        if (v == null) return;
        if (!allowed.contains(v.toString())) {
            throw new AtlasToolValidationException(
                "参数 '" + key + "' 的值 '" + v + "' 不在允许范围内",
                "VALUE_NOT_ALLOWED",
                List.of("请从以下值中选择: " + String.join(", ", allowed))
            );
        }
    }

    /**
     * 安全获取参数值（提供默认值）。
     */
    @SuppressWarnings("unchecked")
    protected <T> T getParam(Map<String, Object> params, String key, T defaultValue) {
        Object v = params.get(key);
        if (v == null) return defaultValue;
        try {
            return (T) v;
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    // ═══════════════════════════════════════════
    // 访问器
    // ═══════════════════════════════════════════

    public String getToolName() { return toolName; }
    public String getDescription() { return description; }
}
