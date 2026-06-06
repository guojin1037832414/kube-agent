package com.atlas.tool.impl;

import com.atlas.tool.core.ToolParameterSpec;
import com.atlas.tool.exception.AtlasToolValidationException;

import java.util.List;
import java.util.Map;

/**
 * 下载任务类 Tool 的路径参数校验与 schema 片段。
 *
 * <p>下载任务状态、进度、暂停、恢复、删除等接口都把任务 ID 放进 URL path。
 * Agent 侧必须只接受成熟后端返回的正整数 ID，避免 LLM 输出 "../42"、"42/extra" 等路径片段。</p>
 */
final class DownloadTaskQuerySupport {

    private DownloadTaskQuerySupport() {
    }

    static ToolParameterSpec taskIdSpec() {
        return new ToolParameterSpec(
            "id",
            "integer",
            "下载任务 ID，必须来自 download_task_list 返回的数字 ID。",
            true,
            List.of("taskId", "task_id", "downloadTaskId", "download_task_id")
        );
    }

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
