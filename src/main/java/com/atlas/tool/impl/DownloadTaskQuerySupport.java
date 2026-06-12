package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;
import java.util.Map;

/**
 * 下载任务类 Tool 的路径参数校验与 schema 片段。
 *
 * <p>中文说明：下载任务状态、进度、暂停、恢复、删除等接口都把任务 ID 放进 URL path。
 * 这些 Tool 的输入可能来自 LLM、Plan、前端表单或用户自然语言归一化后的参数 Map；输出会直接参与
 * kube-manager path 拼接，因此必须在进入 HTTP client 前收敛成成熟后端返回的正整数 ID。</p>
 *
 * <p>安全边界：任务 ID 只是目标资源定位符，不是授权凭证。这里不能接受 {@code ../42}、
 * {@code 42/extra}、{@code 1?x=y}、脚本片段、空白或负数，也不能把 sessionId、conversationId、
 * userId、orgId、token 等控制面字段当作任务 ID。真实读取仍必须继续依赖当前可信 token/orgId、
 * ToolPermission、HITL 敏感读取策略和 kube-manager 权限。</p>
 */
final class DownloadTaskQuerySupport {

    private DownloadTaskQuerySupport() {
    }

    /**
     * 下载任务 ID 的 ToolParameterSpec。
     *
     * <p>中文说明：schema 暴露给 LLM/ToolCallback/调试面板，用于告诉调用方只应提供数字 ID；
     * aliases 只是常见业务别名，不包含任何身份、租户、HITL、审计或 release 字段。</p>
     */
    static ToolParameterSpec taskIdSpec() {
        return new ToolParameterSpec(
            "id",
            "integer",
            "下载任务 ID，必须来自 download_task_list 返回的数字 ID。",
            true,
            List.of("taskId", "task_id", "downloadTaskId", "download_task_id")
        );
    }

    /**
     * 从参数 Map 中提取正整数任务 ID。
     *
     * <p>安全边界：返回值会进入 URL path，所以只能返回正整数文本；失败时抛出结构化校验异常，
     * 由 BaseTool 转成前端可澄清的安全失败，不触发 kube-manager 调用。</p>
     */
    static String positiveTaskId(Map<String, Object> params) {
        Object raw = params.get("id");
        if (raw == null || raw.toString().isBlank()) {
            throw new AtlasToolValidationException(
                "缺少必填参数: id",
                "MISSING_DOWNLOAD_TASK_ID",
                List.of("请先通过 download_task_list 查询下载任务，再使用返回的数字 ID 查询状态或进度")
            );
        }
        String value = raw.toString().trim();
        if (!value.matches("[1-9]\\d*")) {
            throw new AtlasToolValidationException(
                "下载任务 ID 仅支持正整数: " + value,
                "INVALID_DOWNLOAD_TASK_ID",
                List.of("id 会进入 URL path，必须使用成熟后端返回的数字 ID，不能包含路径、脚本或查询字符串")
            );
        }
        return value;
    }
}
