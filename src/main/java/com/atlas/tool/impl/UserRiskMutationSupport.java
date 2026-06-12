package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户/RBAC 高风险变更 Tool 的参数白名单与校验工具。
 *
 * <p>中文说明：用户启用、禁用、删除、充值都会改变账号状态或资金余额，属于高风险写操作。
 * 输入可能来自 LLM Action JSON、PlanStep、前端表单或人工补参；输出会进入 kube-manager path/body，
 * 因此必须集中做正整数校验与 body 白名单，避免每个 Tool 自行拼接产生漂移。</p>
 *
 * <p>安全边界：目标用户 ID 和充值 body 只是业务参数，不是写授权。这里不能把 organizationId、
 * orgId、token、sessionId、当前登录 userId、approved、auditReceipt、writeAllowed、releaseDecision
 * 等控制面字段透传给 kube-manager；真实写操作仍必须满足 ToolPermission、admin/RBAC、HITL、
 * durable audit、idempotency、release evidence 和 kube-manager 权限。</p>
 */
final class UserRiskMutationSupport {

    private UserRiskMutationSupport() {
    }

    /**
     * 提取目标用户正整数 ID。
     *
     * <p>中文说明：这里刻意支持 id/targetUserId/targetId 这些业务别名，但明确拒绝当前登录上下文
     * userId，避免“对自己操作”和“对目标用户操作”在高风险 Tool 中混淆。</p>
     */
    static String targetUserId(Map<String, Object> params) {
        Object raw = firstPresent(params, "id", "targetUserId", "targetId");
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            throw new AtlasToolValidationException(
                "缺少目标用户 ID，请提供 id 或 targetUserId",
                "MISSING_TARGET_USER_ID",
                List.of("不要使用当前登录上下文中的 userId；必须显式提供要操作的目标用户 ID")
            );
        }
        String value = String.valueOf(raw).trim();
        if (!value.matches("[1-9][0-9]*")) {
            throw new AtlasToolValidationException(
                "目标用户 ID 必须是正整数: " + value,
                "INVALID_TARGET_USER_ID",
                List.of("请先通过 user_query 确认目标用户，再传入该用户的数字 ID")
            );
        }
        return value;
    }

    /**
     * 构造用户充值请求体白名单。
     *
     * <p>安全边界：body 只允许 {@code userId}、{@code amount} 和可选 {@code remark}。
     * 即使调用方传入 approved、token、balance、organizationId、releaseDecision 等字段，也不会进入
     * kube-manager 写请求体；是否允许充值由外层安全链路决定。</p>
     */
    static Map<String, Object> rechargeBody(Map<String, Object> params) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", Integer.parseInt(targetUserId(params)));
        body.put("amount", positiveAmount(params.get("amount")));
        Object remark = params.get("remark");
        if (remark != null && !String.valueOf(remark).trim().isEmpty()) {
            body.put("remark", String.valueOf(remark).trim());
        }
        return body;
    }

    /**
     * 提取以“分”为单位的正整数充值金额。
     *
     * <p>中文说明：拒绝负数、小数、货币符号和超出 Integer 的金额，避免 LLM 把自然语言金额
     * 或异常大额数字直接送入真实资金变更接口。</p>
     */
    private static Integer positiveAmount(Object raw) {
        if (raw == null || String.valueOf(raw).trim().isEmpty()) {
            throw new AtlasToolValidationException(
                "充值金额 amount 不能为空",
                "MISSING_RECHARGE_AMOUNT",
                List.of("amount 单位是分，必须由管理员明确输入，例如 10000 表示 100 元")
            );
        }
        String value = String.valueOf(raw).trim();
        if (!value.matches("[1-9][0-9]*")) {
            throw new AtlasToolValidationException(
                "充值金额 amount 必须是正整数，单位为分: " + value,
                "INVALID_RECHARGE_AMOUNT",
                List.of("请使用正整数金额，避免小数、负数或包含货币符号的文本")
            );
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new AtlasToolValidationException(
                "充值金额 amount 超出整数范围: " + value,
                "INVALID_RECHARGE_AMOUNT",
                List.of("请拆分为后端可接受的较小金额，或先由管理员在 kube-manager 中确认额度")
            );
        }
    }

    /**
     * 按业务别名顺序读取第一个存在的参数。
     *
     * <p>安全边界：该方法只在受控 key 列表中查找，不会扫描整个 Map，也不会接受控制面字段。</p>
     */
    private static Object firstPresent(Map<String, Object> params, String... keys) {
        for (String key : keys) {
            Object value = params.get(key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
