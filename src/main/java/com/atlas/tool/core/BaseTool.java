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

    /**
     * Tool 参数声明元数据（可选重写）。
     *
     * <p>第一阶段用于给 LLM 暴露更精确的 inputSchema，并为
     * {@link ToolParameterNormalizer} 提供 schema-first 的 alias 归一化来源。
     * 默认返回空列表，保证所有既有 Tool 无需立即改造。</p>
     */
    public List<ToolParameterSpec> getParameterSpecs() {
        return List.of();
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
            // 调用方可能传入 Map.of(...) 或 Jackson/框架生成的不可变 Map。
            // 类型转换和默认值回填需要修改参数，因此入口先复制为本地可变副本，避免工具因参数容器不可变而失败。
            Map<String, Object> workingParams = params == null ? null : new LinkedHashMap<>(params);
            // 1. 参数校验
            validate(workingParams);
            // 2. 类型转换
            convertTypes(workingParams);
            // 3. 业务执行
            AtlasToolResult result = doExecute(workingParams);
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

    // ═══════════════════════════════════════════════════════════
    // P3.1+ 统一辅助方法：orgId 解析 + 响应数据提取
    // ═══════════════════════════════════════════════════════════

    /**
     * 从会话上下文中解析 organizationId。
     *
     * <p><b>M5.5 多租户安全治理：</b>organizationId 是权限边界，不是普通业务参数。
     * 因此这里不再读取 {@code params.organizationId} 或 {@code params.orgId}。
     * LLM Action、用户自然语言或外部调用方拼出的 params 只能携带业务查询条件，
     * 不能决定最终租户 path。Tool 执行层必须以 {@link com.atlas.auth.UserPermissionContext}
     * 中由认证链路绑定的 orgId 为唯一权威来源。</p>
     */
    protected String resolveOrganizationId(Map<String, Object> params) {
        String orgId = com.atlas.auth.UserPermissionContext.getCurrentOrgId();
        if (orgId != null && !orgId.isBlank()) {
            return orgId;
        }
        throw new AtlasToolValidationException(
            "无法确定可信 organizationId",
            "MISSING_TRUSTED_ORG_ID",
            List.of("请重新登录以建立可信租户上下文", "不要在工具参数中手工传递 organizationId")
        );
    }

    /**
     * 从 kube-manager 统一响应中提取数据。
     *
     * <p>处理两种常见返回格式：</p>
     * <ul>
     *   <li>{@code {"result": [...]}} — result 直接是数组</li>
     *   <li>{@code {"result": {"records": [...], "total": N}}} — 分页包装对象</li>
     * </ul>
     *
     * @return 若 result 是分页对象，返回 {@code records} 数组；否则返回 result 本身
     */
    @SuppressWarnings("unchecked")
    protected Object extractData(Map<String, Object> response) {
        Object result = response.get("result");
        if (result instanceof java.util.Map<?, ?> map) {
            Object records = map.get("records");
            if (records != null) {
                return records;
            }
            // 分页对象但无 records → 返回 map（如 "total":0 的空响应）
            return map;
        }
        return result;
    }

    /**
     * 构建列表查询结果文本（自动计数）。
     */
    protected String listMessage(String entityName, Object data) {
        int count = 0;
        if (data instanceof java.util.List<?> list) {
            count = list.size();
        }
        return String.format("查询到 %d 个%s", count, entityName);
    }

    /**
     * 构建标准列表查询参数契约。
     *
     * <p>所有接入 {@link #buildListQuery(Map)} 的列表类 Tool 都应复用该方法，
     * 让 ReAct Prompt、Tool JSON Schema、schema-first normalizer 和执行层保持同一套
     * page / limit / keyword 语义，避免不同 Tool 复制粘贴后 alias 漂移。</p>
     *
     * @param keywordDescription keyword 参数面向用户的业务描述
     * @return 标准列表查询参数契约
     */
    protected List<ToolParameterSpec> listQueryParameterSpecs(String keywordDescription) {
        return List.of(
            ToolParameterSpec.stringParam("page", "页码，默认使用 1。", false, List.of("pageNo", "page_no", "current")),
            ToolParameterSpec.stringParam("limit", "每页数量，默认使用 100。", false, List.of("pageSize", "page_size", "size")),
            ToolParameterSpec.stringParam("keyword", keywordDescription, false, List.of("name", "search", "kw"))
        );
    }

    /**
     * 构建 kube-manager 列表接口 query 参数。
     *
     * <p>列表类 Tool 常见参数为 {@code page / limit / keyword}。这些字段已经通过
     * {@link ToolParameterSpec} 暴露给 ReAct 和 schema-first normalizer，执行层必须真实消费，
     * 否则会形成“LLM 看到参数可用、HTTP 请求却忽略参数”的伪参数问题。</p>
     *
     * <p>安全约束：</p>
     * <ul>
     *   <li>只返回 query map，不拼接到 URL path，避免 {@code ?} 被二次编码或 query 注入。</li>
     *   <li>{@code page/limit} 支持数字和数字字符串；空白值回落默认值。</li>
     *   <li>{@code keyword} 仅做首尾 trim，非空才透传，保留中间空格和特殊字符给 HTTP 客户端编码。</li>
     * </ul>
     */
    protected Map<String, Object> buildListQuery(Map<String, Object> params) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", positiveIntOrDefault(params.get("page"), "page", 1));
        query.put("limit", positiveIntOrDefault(params.get("limit"), "limit", 100));

        String keyword = optionalTrimmedString(params.get("keyword"));
        if (keyword != null) {
            query.put("keyword", keyword);
        }
        return query;
    }

    /**
     * 构建仅包含 page / limit 的公共展示类参数契约。
     *
     * <p>M5.3 专项用于 {@code /api/public/home-info/*} 首页公共展示接口。
     * 这些接口允许用户翻页浏览公开展示内容，但不能复用 {@link #listQueryParameterSpecs(String)}，
     * 因为标准三件套会额外暴露 keyword/name/search/kw 搜索能力，导致 PUBLIC 场景从“展示”扩大为
     * “公共探测”。</p>
     *
     * @return 仅包含 page / limit 的参数契约
     */
    protected List<ToolParameterSpec> pageLimitOnlyParameterSpecs() {
        return List.of(
            ToolParameterSpec.stringParam("page", "页码，默认使用 1。", false, List.of("pageNo", "page_no", "current")),
            ToolParameterSpec.stringParam("limit", "每页数量，默认使用 100，最大不超过 100。", false, List.of("pageSize", "page_size", "size"))
        );
    }

    /**
     * 构建仅包含 page / limit 的 kube-manager query 参数，并对 limit 设置上限。
     *
     * <p>该方法刻意忽略 params 中可能出现的 keyword/name/search/kw，确保首页公共展示 Tool
     * 不会被 LLM 或用户手工参数升级为公开搜索入口。超过 maxLimit 时直接拒绝，而不是静默截断，
     * 避免 LLM 误以为大页请求已完整生效。</p>
     *
     * @param params   LLM 或调用方传入的参数
     * @param maxLimit limit 允许的最大值
     * @return 仅包含 page / limit 的 query map
     */
    protected Map<String, Object> buildPageLimitOnlyQuery(Map<String, Object> params, int maxLimit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", positiveIntOrDefault(params.get("page"), "page", 1));

        int limit = positiveIntValueOrDefault(params.get("limit"), "limit", 100);
        if (limit > maxLimit) {
            throw new AtlasToolValidationException(
                "参数 'limit' 不能超过 " + maxLimit + "，当前值: " + limit,
                "VALUE_OUT_OF_RANGE",
                List.of("请将 'limit' 调整为不超过 " + maxLimit + " 的正整数"));
        }
        query.put("limit", String.valueOf(limit));
        return query;
    }

    /**
     * 将可选分页参数归一为正整数字符串。
     *
     * <p>这里不复用 {@link #getParamTypes()}，因为列表查询允许空白 page/limit 使用默认值，
     * 而入口类型转换会在业务默认值逻辑前直接失败。特别注意：Number 类型必须严格校验为整数，
     * 不能使用 {@code intValue()} 截断小数，否则 LLM 传入 1.5 会被误当成 1。</p>
     */
    private String positiveIntOrDefault(Object raw, String key, int defaultValue) {
        return String.valueOf(positiveIntValueOrDefault(raw, key, defaultValue));
    }

    /**
     * 将可选分页参数归一为正整数，供需要额外上限判断的受限列表复用。
     */
    private int positiveIntValueOrDefault(Object raw, String key, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        if (raw instanceof String s && s.isBlank()) {
            return defaultValue;
        }
        int value = strictInt(raw, key);
        if (value <= 0) {
            throw new AtlasToolValidationException(
                "参数 '" + key + "' 必须大于 0，当前值: " + value,
                "VALUE_OUT_OF_RANGE",
                List.of("请将 '" + key + "' 改为正整数"));
        }
        return value;
    }

    /**
     * 严格解析整数，不允许小数 Number 被截断。
     *
     * <p>该方法专用于列表分页参数。普通 Tool 的 {@link #toInt(Object, String)} 保持原兼容行为，
     * 避免扩大本次 M4.3 修复范围影响既有 Tool。</p>
     */
    private int strictInt(Object raw, String key) {
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer) {
            return ((Number) raw).intValue();
        }
        if (raw instanceof Long value) {
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                throw new AtlasToolValidationException(
                    "参数 '" + key + "' 超出整数范围: " + raw,
                    "VALUE_OUT_OF_RANGE",
                    List.of("请将 '" + key + "' 改为有效的正整数"));
            }
            return value.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ex) {
            throw new AtlasToolValidationException(
                "参数 '" + key + "' 期望整数，但收到: " + raw,
                "TYPE_MISMATCH",
                List.of("请将 '" + key + "' 改为有效的整数值"));
        }
    }

    /**
     * 返回首尾 trim 后的可选字符串；空值或空白字符串返回 null，避免生成无效 query 参数。
     */
    private String optionalTrimmedString(Object raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isBlank() ? null : value;
    }

    // ═══════════════════════════════════════════
    // 访问器
    // ═══════════════════════════════════════════

    public String getToolName() { return toolName; }
    public String getDescription() { return description; }
}
