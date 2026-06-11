package com.atlas.hitl;

import com.atlas.tool.annotation.AtlasToolMapping;
import com.atlas.tool.core.AtlasToolResult;
import com.atlas.tool.core.ToolRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * HITL 执行层统一守卫。
 *
 * <p>M5.13 安全边界下沉：所有可能触发 {@code BaseTool#execute(Map)} 的入口，
 * 都必须在真正执行前调用本守卫。这样无论请求来自 Graph tool_call、手写 ReAct、
 * legacy IntentRouter fallback，还是 Spring AI ToolCallback 桥接层，高风险 Tool 都会遵循
 * “无服务端确认则 fail-closed”的统一规则。</p>
 *
 * <p>守卫只信任 {@link HitlConfirmation} 这种后端 marker，不读取 LLM/前端参数中的
 * confirmed/hitlConfirmed 字段，防止自然语言或 JSON 参数伪造确认。</p>
 *
 * <p>中文说明：HitlGuard 是执行前最后一道人工确认闸门。
 * 它不负责展示弹窗，也不负责保存确认 token；它只回答一个问题：
 * “当前 Tool 风险元数据 + 服务端确认 marker 是否允许继续进入 BaseTool.execute”。</p>
 *
 * <p>安全边界：这里必须 fail-closed。新增 Tool 类型、缺失 Tool 元数据、UNKNOWN/PLACEHOLDER
 * 或任何非普通 READ 操作，都应该先要求人工确认，而不是默认放行。</p>
 */
@Component
public class HitlGuard {

    /** 结构化拦截结果中的错误码。 */
    public static final String HITL_REQUIRED_CODE = "HITL_CONFIRMATION_REQUIRED";

    /**
     * 判断 Tool 是否必须经过 HITL 人工确认。
     *
     * <p>fail-closed 策略：只有明确声明为普通 READ 且未要求确认的 Tool 可以直接执行。
     * SENSITIVE_READ/CREATE/UPDATE/DELETE/ACTION/PLACEHOLDER/UNKNOWN 以及元数据缺失，
     * 都视为需要确认。这样新增风险类型时会天然默认拦截，而不是默认放行。</p>
     */
    public boolean requiresConfirmation(ToolRegistry.ToolMetadata metadata) {
        if (metadata == null) {
            return true;
        }
        AtlasToolMapping.OperationType operationType = metadata.operationType();
        return metadata.requiresConfirmation()
            || operationType == null
            || operationType != AtlasToolMapping.OperationType.READ;
    }

    /**
     * 校验服务端确认凭证是否允许执行当前 Tool。
     *
     * @param target 待执行 Tool 名称或 intentId
     * @param metadata Tool 风险元数据
     * @param confirmation 后端 confirm 接口注入的服务端 marker；可以为空
     * @return 允许/拒绝结果
     */
    public Decision verify(String target,
                           ToolRegistry.ToolMetadata metadata,
                           HitlConfirmation confirmation) {
        if (!requiresConfirmation(metadata)) {
            return Decision.permit();
        }
        if (confirmation != null && confirmation.allows(target)) {
            return Decision.permit();
        }
        return Decision.blocked(target, formatToolRisk(metadata));
    }

    /**
     * 根据 Tool 名称从注册表解析元数据并校验。
     *
     * <p>中文说明：解析失败不能放行。ToolRegistry 查不到元数据时，verify 会把 metadata=null
     * 当成高风险处理，这样新入口或注册缺陷不会绕过 HITL。</p>
     */
    public Decision verifyByToolName(ToolRegistry toolRegistry,
                                     String toolName,
                                     HitlConfirmation confirmation) {
        ToolRegistry.ToolMetadata metadata = null;
        try {
            metadata = toolRegistry.resolve(toolName);
        } catch (Exception ignored) {
            // 元数据缺失时按 fail-closed 处理，由 verify 生成风险提示。
        }
        return verify(toolName, metadata, confirmation);
    }

    /**
     * 根据 intentId 从注册表解析元数据并校验。
     *
     * <p>中文说明：Graph/ReAct 更多使用 intentId，因此这里保持与 Tool 名称路径同样的安全语义。</p>
     */
    public Decision verifyByIntentId(ToolRegistry toolRegistry,
                                     String intentId,
                                     HitlConfirmation confirmation) {
        Optional<ToolRegistry.ToolMetadata> metadata = toolRegistry.resolveByIntentId(intentId);
        return verify(intentId, metadata.orElse(null), confirmation);
    }

    /**
     * 构造结构化 Tool 失败结果，供 ReAct/ToolCallback 这类 Map/JSON 路径复用。
     *
     * <p>中文说明：返回结构化错误而不是抛异常，是为了让前端能展示 HITL 所需信息，
     * 同时让审计/观测系统看到“被拦截”而不是“系统崩溃”。</p>
     */
    public Map<String, Object> toBlockedToolResult(Decision decision) {
        return AtlasToolResult.fail(
            decision.message(),
            HITL_REQUIRED_CODE,
            java.util.List.of("请先通过前端高危操作确认弹窗完成服务端确认，再重新执行。")
        );
    }

    /**
     * 格式化 Tool 风险信息，供拦截提示和审计日志阅读。
     *
     * <p>中文说明：这里输出的是风险解释，不是权限证明。
     * 前端不能因为看到 operationType=READ 就自行决定执行；最终仍以 Decision.allowed 为准。</p>
     */
    public String formatToolRisk(ToolRegistry.ToolMetadata metadata) {
        if (metadata == null) {
            return "[风险元数据缺失]";
        }
        String httpMethod = metadata.httpMethod() == null || metadata.httpMethod().isBlank()
            ? "未声明" : metadata.httpMethod();
        return "[operationType=" + metadata.operationType()
            + ", httpMethod=" + httpMethod
            + ", requiresConfirmation=" + metadata.requiresConfirmation() + "]";
    }

    /**
     * HITL 守卫判定结果。
     *
     * <p>中文说明：allowed=true 只表示 HITL 这一道门通过了；
     * 它不代表权限、租户、审计、参数保护、kube-manager 写安全都已经通过。</p>
     */
    public record Decision(boolean allowed, String message) {
        public static Decision permit() {
            return new Decision(true, "");
        }

        public static Decision blocked(String target, String risk) {
            return new Decision(false,
                "⛔ 已阻止高风险操作：'" + target + "' " + risk
                    + "。该操作必须先经过人工确认，未确认时系统按 fail-closed 策略拒绝执行。");
        }
    }
}
