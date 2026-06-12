package com.atlas.tool.impl;

import com.atlas.tool.exception.AtlasToolValidationException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 虚拟机查询类 Tool 的参数安全辅助。
 *
 * <p>中文说明：成熟 kube-manager 的虚拟机详情接口把 VM 名称放在 URL path 中。
 * 输入来自 LLM/Plan/前端参数 Map，输出会进入真实 kube-manager HTTP path，因此 Agent 侧必须先做
 * 路径片段级校验，再交给 {@link UriUtils} 编码，避免把自然语言或恶意路径片段直接拼接到 URL。</p>
 *
 * <p>安全边界：VM 名称只是资源定位符，不是读取或操作授权。这里不接入启动、停止、删除等写操作；
 * 那些动作后续必须走高风险 ToolPermission、HITL、durable audit、idempotency、release evidence
 * 和 kube-manager 权限。这里也不能接受 token、orgId、sessionId、currentUserId 等控制面字段作为目标 VM。</p>
 */
final class VirtualMachineQuerySupport {

    private static final int MAX_VM_NAME_LENGTH = 253;
    private static final String SAFE_VM_NAME_PATTERN = "[A-Za-z0-9][A-Za-z0-9_.-]{0,252}";

    private VirtualMachineQuerySupport() {
    }

    /**
     * 提取、校验并编码 VM 名称 path 片段。
     *
     * <p>中文说明：只允许字母、数字、点、下划线和短横线，拒绝斜杠、反斜杠、query、fragment、
     * 空白和超长名称。返回值仅用于 path segment，不代表资源归属已经通过校验。</p>
     */
    static String encodedVmName(Map<String, Object> params) {
        Object raw = params.get("name");
        if (raw == null || raw.toString().isBlank()) {
            throw new AtlasToolValidationException(
                "缺少必填参数: name",
                "MISSING_VM_NAME",
                List.of("请提供要查询的虚拟机名称，例如 vm-training-01"));
        }

        String name = raw.toString().trim();
        if (name.length() > MAX_VM_NAME_LENGTH || !name.matches(SAFE_VM_NAME_PATTERN)) {
            throw new AtlasToolValidationException(
                "虚拟机名称不合法: " + name,
                "INVALID_VM_NAME",
                List.of("虚拟机名称只能包含字母、数字、点、下划线和短横线，且不能包含 /、\\、?、# 等路径或查询字符"));
        }
        return UriUtils.encodePathSegment(name, StandardCharsets.UTF_8);
    }
}
