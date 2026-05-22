package com.atlas.tool.impl;

import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * M5.1 敏感列表 Tool 暂缓保护测试。
 *
 * <p>订单列表和配额审批列表虽然都是 GET 列表接口，但它们分别涉及账务订单
 * 与 RBAC/审批记录。未完成权限、字段脱敏和审计专项前，不允许因为批量脚本
 * 误操作而暴露 keyword 搜索能力，避免把敏感列表变成可枚举的搜索入口。</p>
 */
class SensitiveListToolHoldContractTest {

    @Test
    void m51_shouldKeepOrderAndQuotaReceiveOnHoldUntilPermissionAuditCompletes() {
        assertNoKeywordContract(new OrderListTool(null), "order_list");
        assertNoKeywordContract(new QuotaReceiveListTool(null), "quota_receive_list");
    }

    /**
     * 敏感列表在专项审计完成前不得暴露 keyword 参数。
     */
    private void assertNoKeywordContract(BaseTool tool, String toolName) {
        Map<String, ToolParameterSpec> specs = tool.getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertFalse(specs.containsKey("keyword"),
            toolName + " 属于账务/审批敏感列表，未完成权限/审计专项前不得暴露 keyword 枚举能力");
    }
}
