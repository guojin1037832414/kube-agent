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
     */
    public Decision verifyByIntentId(ToolRegistry toolRegistry,
                                     String intentId,
                                     HitlConfirmation confirmation) {
        Optional<ToolRegistry.ToolMetadata> metadata = toolRegistry.resolveByIntentId(intentId);
        return verify(intentId, metadata.orElse(null), confirmation);
    }

    /**
     * 构造结构化 Tool 失败结果，供 ReAct/ToolCallback 这类 Map/JSON 路径复用。
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
