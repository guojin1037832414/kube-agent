package com.atlas.tool.impl;

import com.atlas.tool.core.BaseTool;
import com.atlas.tool.core.ToolParameterSpec;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 敏感列表 Tool 暂缓保护测试。
 *
 * <p>M5 阶段开始，剩余列表类 Tool 已逐步进入账务、审批、RBAC、组织、身份源等高敏域。
 * 这些 Tool 不能再像普通资源列表一样机械套用 page / limit / keyword 标准三件套，
 * 否则会把“只读列表”放大成可翻页、可搜索、可枚举的管理面探测入口。</p>
 *
 * <p>本测试的目标不是证明这些能力永远不能开放，而是在权限模型、字段脱敏、
 * 审计日志和产品语义完成专项确认前，把 HOLD 决策测试化，防止后续批量脚本误开放。</p>
 */
class SensitiveListToolHoldContractTest {

    @Test
    void m51_shouldKeepOrderAndQuotaReceiveOnHoldUntilPermissionAuditCompletes() {
        assertNoStandardListQueryContract(new OrderListTool(null), "order_list", "账务订单敏感列表");
        assertNoStandardListQueryContract(new QuotaReceiveListTool(null), "quota_receive_list", "配额审批敏感列表");
    }

    @Test
    void m52_shouldKeepRbacManagementListsOnHoldUntilPermissionAuditCompletes() {
        assertNoStandardListQueryContract(new LdapConfigListTool(null), "ldap_config_list", "LDAP 身份源配置列表");
        assertNoStandardListQueryContract(new OrganizationListTool(null), "organization_list", "组织/租户结构列表");
        assertNoStandardListQueryContract(new PermissionMenuListTool(null), "permission_menu_list", "权限菜单/权限树列表");
        assertNoStandardListQueryContract(new RegisterAuditListTool(null), "register_audit_list", "组织注册审核列表");
        assertNoStandardListQueryContract(new RoleAssignableListTool(null), "role_assignable", "可分配角色边界列表");
        assertNoStandardListQueryContract(new RoleEditableListTool(null), "role_editable", "可编辑角色边界列表");
    }

    @Test
    void m53_shouldKeepGlobalGpuAndSysModelOnFullHoldUntilPublicBoundaryAuditCompletes() {
        assertNoStandardListQueryContract(new GpuGlobalListTool(null), "gpu_global_list", "全局 GPU 跨组织资源列表");
        assertNoStandardListQueryContract(new SysModelListTool(null), "sys_model_list", "全局模型/系统模型列表");
    }

    @Test
    void m54_shouldKeepPublicSysInfoMapOnNoParameterHoldUntilPublicConfigSchemaCompletes() {
        assertNoStandardListQueryContract(new SysInfoMapTool(null), "sys_info_map", "PUBLIC no-org 系统配置 Map");
    }

    /**
     * 敏感列表在专项审计完成前不得暴露标准列表查询参数。
     *
     * <p>这里同时禁止 page、limit、keyword：page/limit 会扩大翻页枚举能力，
     * keyword 会把敏感管理面升级为可搜索入口。后续如需开放受限分页，应另开权限专项，
     * 使用更细粒度的参数契约，而不是复用普通列表的标准三件套。</p>
     */
    private void assertNoStandardListQueryContract(BaseTool tool, String toolName, String sensitiveDomain) {
        Map<String, ToolParameterSpec> specs = tool.getParameterSpecs().stream()
            .collect(Collectors.toMap(ToolParameterSpec::name, Function.identity()));

        assertFalse(specs.containsKey("page"),
            toolName + " 属于" + sensitiveDomain + "，权限/审计专项完成前不得暴露 page 翻页枚举能力");
        assertFalse(specs.containsKey("limit"),
            toolName + " 属于" + sensitiveDomain + "，权限/审计专项完成前不得暴露 limit 批量枚举能力");
        assertFalse(specs.containsKey("keyword"),
            toolName + " 属于" + sensitiveDomain + "，权限/审计专项完成前不得暴露 keyword 搜索枚举能力");
    }
}
