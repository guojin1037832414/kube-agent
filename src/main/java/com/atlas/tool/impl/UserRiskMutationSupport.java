package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户/RBAC 高风险变更 Tool 的参数白名单与校验工具。
 *
 * <p>用户启用、禁用、充值都会改变账号状态或资金余额，不能让 LLM 自由拼接 path/body。
 * 这里集中做正整数校验与 body 白名单，避免把 organizationId、token、sessionId、当前登录
 * userId 等上下文字段误透传给 kube-manager。</p>
 */
final class UserRiskMutationSupport {

    private UserRiskMutationSupport() {
    }

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
